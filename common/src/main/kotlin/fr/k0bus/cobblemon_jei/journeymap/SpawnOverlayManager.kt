package fr.k0bus.cobblemon_jei.journeymap

import fr.k0bus.cobblemon_jei.recipe.PokemonSpawnRecipe
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import journeymap.api.v2.client.IClientAPI
import journeymap.api.v2.client.display.Context
import journeymap.api.v2.client.display.DisplayType
import journeymap.api.v2.client.display.PolygonOverlay
import journeymap.api.v2.client.model.TextProperties
import journeymap.api.v2.client.util.UIState
import net.minecraft.client.Minecraft
import net.minecraft.core.Holder
import net.minecraft.world.level.ChunkPos
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Coordinateur principal de l'affichage persistant des zones de spawn Cobblemon sur JourneyMap.
 * Gère le cycle de vie, l'exécution 100% asynchrone (zéro lag GUI/carte),
 * la fusion géométrique et la synchronisation avec l'API JourneyMap (v2).
 */
@Suppress("DEPRECATION")
object SpawnOverlayManager {

    const val MOD_ID = "cobblemon_jei"
    const val OVERLAY_GROUP_NAME = "Cobblemon Spawns"

    private val logger = LoggerFactory.getLogger("CobblemonJM/SpawnOverlayManager")
    private var clientApi: IClientAPI? = null

    // Thread pool dédié hors du thread principal pour les calculs lourds (I/O disque + union 2D)
    private val workerExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "CobblemonJM-Worker").apply { isDaemon = true }
    }

    data class ScanProgress(
        val isScanning: Boolean = false,
        val completedRegions: Int = 0,
        val totalRegions: Int = 0,
        val progressPercent: Int = 0,
        val scannedChunks: Int = 0,
        val eligibleChunks: Int = 0
    )

    var isOverlayActive: Boolean = false
        private set

    var activeSpecies: Species? = null
        private set

    var activeForm: FormData? = null
        private set

    var activeRarityCounts: Map<SpawnRarity, Int> = emptyMap()
        private set

    var scanProgress: ScanProgress = ScanProgress()
        private set

    private val generationCounter = AtomicInteger(0)
    private var lastDimension: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>? = null

    fun initialize(api: IClientAPI) {
        this.clientApi = api
        logger.info("Cobblemon JourneyMap SpawnOverlayManager lié à l'API v2.")
    }

    /**
     * Active l'overlay cartographique pour l'espèce sélectionnée.
     * Déclenche un pipeline progressif en deux étapes :
     * Étape 1 : Rendu IMMÉDIAT (< 15ms) des chunks en RAM et du cache mémoire.
     * Étape 2 : Scan asynchrone des régions disque en arrière-plan avec mise à jour progressive.
     */
    fun setOverlay(species: Species?, form: FormData? = null) {
        val api = clientApi ?: return

        if (species == null) {
            clearOverlay()
            return
        }

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val clientLevel = mc.level ?: return
        val currentDim = clientLevel.dimension()
        val dimString = currentDim.location().toString()

        if (lastDimension != currentDim) {
            JourneyMapDiskScanner.clearCache()
            lastDimension = currentDim
        }

        // 1. Validation préalable des règles de spawn pour cette dimension
        val targetForm = form ?: species.standardForm
        val validRecipes = CobblemonSpawnMatcher.getValidRecipes(species, targetForm, currentDim)

        if (validRecipes.isEmpty()) {
            logger.info("Cobblemon JM: Aucun spawn naturel pour ${species.name} dans la dimension $dimString.")
            clearOverlay()
            return
        }

        val currentGen = generationCounter.incrementAndGet()
        activeSpecies = species
        activeForm = targetForm
        isOverlayActive = true
        scanProgress = ScanProgress(isScanning = true, progressPercent = 0)

        mc.execute {
            CobblemonJourneyMapPlugin.updateButton()
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("chat.cobblemon_jm.scan_start", species.translatedName),
                true
            )
        }

        val playerY = player.blockPosition().y

        // ==========================================
        // ÉTAPE 1 : Rendu immédiat (< 15ms) depuis le cache mémoire persistant
        // ==========================================
        val immediateChunkBiomes = mutableMapOf<ChunkPos, Holder<net.minecraft.world.level.biome.Biome>>()
        immediateChunkBiomes.putAll(JourneyMapDiskScanner.getCachedChunksForDimension(currentDim))

        if (immediateChunkBiomes.isNotEmpty()) {
            val immediateChunksByRarity = CobblemonSpawnMatcher.groupChunksByRarity(immediateChunkBiomes, validRecipes)
            if (immediateChunksByRarity.isNotEmpty()) {
                activeRarityCounts = immediateChunksByRarity.mapValues { it.value.size }
                val initialOverlays = buildOverlays(species, currentDim, playerY, immediateChunksByRarity, validRecipes)
                if (initialOverlays.isNotEmpty()) {
                    try {
                        api.removeAll(MOD_ID, DisplayType.Polygon)
                        for (overlay in initialOverlays) {
                            api.show(overlay)
                        }
                        logger.info("Cobblemon JM: [Stage 1] ${initialOverlays.size} overlays immédiats affichés depuis le cache (${immediateChunkBiomes.size} chunks).")
                    } catch (e: Exception) {
                        logger.error("Cobblemon JM: Erreur affichage initial : {}", e.message)
                    }
                }
            }
        }

        // ==========================================
        // ÉTAPE 2 : Scan asynchrone des régions disque en arrière-plan
        // ==========================================
        logger.info("Cobblemon JM: [Stage 2] Lancement de l'analyse persistante pour ${species.name} (Génération $currentGen)...")

        CompletableFuture.runAsync({
            try {
                fun isCancelled() = (currentGen != generationCounter.get() || !isOverlayActive)
                if (isCancelled()) return@runAsync

                val scannedChunkBiomes = JourneyMapDiskScanner.scanExploredChunks(
                    currentDim = currentDim,
                    mc = mc,
                    playerY = playerY,
                    isCancelled = { isCancelled() },
                    clientApi = api,
                    onProgress = { completedRegions, totalRegions, progressBiomes ->
                        if (isCancelled() || progressBiomes.isEmpty()) return@scanExploredChunks
                        val progressChunksByRarity = CobblemonSpawnMatcher.groupChunksByRarity(progressBiomes, validRecipes)
                        val pct = if (totalRegions > 0) (completedRegions * 100 / totalRegions).coerceIn(0, 99) else 0
                        val eligibleCount = progressChunksByRarity.values.sumOf { it.size }

                        scanProgress = ScanProgress(
                            isScanning = true,
                            completedRegions = completedRegions,
                            totalRegions = totalRegions,
                            progressPercent = pct,
                            scannedChunks = progressBiomes.size,
                            eligibleChunks = eligibleCount
                        )

                        if (progressChunksByRarity.isNotEmpty()) {
                            activeRarityCounts = progressChunksByRarity.mapValues { it.value.size }
                            val progressOverlays = buildOverlays(species, currentDim, playerY, progressChunksByRarity, validRecipes)
                            mc.execute {
                                if (isCancelled()) return@execute
                                try {
                                    api.removeAll(MOD_ID, DisplayType.Polygon)
                                    for (overlay in progressOverlays) {
                                        api.show(overlay)
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        // Notification dynamique dans l'action bar
                        mc.execute {
                            if (isCancelled()) return@execute
                            val p = mc.player ?: return@execute
                            p.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable(
                                    "chat.cobblemon_jm.scan_progress",
                                    completedRegions,
                                    totalRegions,
                                    eligibleCount
                                ),
                                true
                            )
                        }
                    }
                )

                if (isCancelled()) return@runAsync

                // Consolidation globale finale
                val allChunkBiomes = mutableMapOf<ChunkPos, Holder<net.minecraft.world.level.biome.Biome>>()
                allChunkBiomes.putAll(JourneyMapDiskScanner.getCachedChunksForDimension(currentDim))
                allChunkBiomes.putAll(scannedChunkBiomes)

                val chunksByRarity = if (allChunkBiomes.isNotEmpty()) {
                    CobblemonSpawnMatcher.groupChunksByRarity(allChunkBiomes, validRecipes)
                } else emptyMap()

                if (isCancelled()) return@runAsync

                val finalEligibleCount = chunksByRarity.values.sumOf { it.size }
                activeRarityCounts = chunksByRarity.mapValues { it.value.size }
                val totalReg = scanProgress.totalRegions.coerceAtLeast(scanProgress.completedRegions).coerceAtLeast(1)
                scanProgress = ScanProgress(
                    isScanning = false,
                    completedRegions = totalReg,
                    totalRegions = totalReg,
                    progressPercent = 100,
                    scannedChunks = allChunkBiomes.size,
                    eligibleChunks = finalEligibleCount
                )

                val finalOverlays = if (chunksByRarity.isNotEmpty()) {
                    buildOverlays(species, currentDim, playerY, chunksByRarity, validRecipes)
                } else emptyList()

                mc.execute {
                    if (isCancelled()) return@execute
                    try {
                        api.removeAll(MOD_ID, DisplayType.Polygon)
                        for (overlay in finalOverlays) {
                            api.show(overlay)
                        }
                        logger.info("Cobblemon JM: [Stage 2] ${finalOverlays.size} overlays de rareté affichés avec succès (${allChunkBiomes.size} chunks cartographiés).")

                        val p = mc.player
                        if (p != null) {
                            p.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable(
                                    "chat.cobblemon_jm.scan_complete",
                                    species.translatedName,
                                    finalEligibleCount
                                ),
                                true
                            )
                        }
                    } catch (e: Exception) {
                        logger.error("Cobblemon JM: Erreur affichage final JourneyMap : {}", e.message)
                    }
                }
            } catch (e: Exception) {
                logger.error("Cobblemon JM: Erreur lors du calcul asynchrone des overlays : {}", e.message, e)
            }
        }, workerExecutor)
    }

    /**
     * Construit les objets [PolygonOverlay] avec configuration graphique, infobulles riches et support Minimap.
     */
    private fun buildOverlays(
        species: Species,
        currentDim: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
        playerY: Int,
        chunksByRarity: Map<SpawnRarity, Set<ChunkPos>>,
        validRecipes: List<PokemonSpawnRecipe>
    ): List<PolygonOverlay> {
        val generatedOverlays = mutableListOf<PolygonOverlay>()

        for ((rarity, chunkSet) in chunksByRarity) {
            val mergedPolygons = ChunkPolygonMerger.mergeChunks(chunkSet, playerY)
            val matchingRecipesForRarity = validRecipes.filter { recipe ->
                SpawnRarity.fromBucketOrWeight(recipe.bucket, recipe.weight) == rarity
            }.ifEmpty { validRecipes }

            val shapeProps = RarityColorPalette.createShapeProperties(rarity)
            val tooltip = RarityColorPalette.buildTooltip(
                species = species,
                rarity = rarity,
                matchingRecipes = matchingRecipesForRarity,
                chunkCount = chunkSet.size
            )

            for (poly in mergedPolygons) {
                val overlay = PolygonOverlay(MOD_ID, currentDim, shapeProps, poly)
                overlay.overlayGroupName = OVERLAY_GROUP_NAME
                overlay.title = tooltip
                overlay.setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap)
                overlay.minZoom = UIState.FULLSCREEN_ZOOM_MIN
                overlay.maxZoom = UIState.ZOOM_IN_MAX

                val textProps = TextProperties()
                    .setScale(1.3f)
                    .setColor(0xFFFFFF)
                    .setBackgroundColor(0x181920)
                    .setBackgroundOpacity(0.85f)
                    .setFontShadow(true)
                    .setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap)
                overlay.setTextProperties(textProps)

                generatedOverlays.add(overlay)
            }
        }
        return generatedOverlays
    }

    /**
     * Supprime immédiatement tous les overlays actifs et interrompt tout calcul asynchrone en cours.
     */
    fun clearOverlay() {
        generationCounter.incrementAndGet()
        val api = clientApi
        if (api != null) {
            try {
                api.removeAll(MOD_ID, DisplayType.Polygon)
            } catch (e: Exception) {
                logger.debug("Cobblemon JM: Erreur lors du nettoyage des overlays : {}", e.message)
            }
        }
        scanProgress = ScanProgress()
        activeSpecies = null
        activeForm = null
        activeRarityCounts = emptyMap()
        isOverlayActive = false
        try {
            PokemonRadarService.clearTrackedOnly()
        } catch (_: Throwable) {}
        try {
            Minecraft.getInstance().execute {
                CobblemonJourneyMapPlugin.updateButton()
                Minecraft.getInstance().player?.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("chat.cobblemon_jm.overlay_disabled"),
                    true
                )
            }
        } catch (_: Throwable) {}
    }

    /**
     * Couleurs élémentaires de référence pour les types de Pokémon.
     */
    fun getElementColor(typeName: String): Int {
        return when (typeName.lowercase()) {
            "fire" -> 0xFFEB4D4B.toInt()
            "water" -> 0xFF3867D6.toInt()
            "grass" -> 0xFF20BF6B.toInt()
            "electric" -> 0xFFF7B731.toInt()
            "ice" -> 0xFF45AAF2.toInt()
            "fighting" -> 0xFFB71540.toInt()
            "poison" -> 0xFF8854D0.toInt()
            "ground" -> 0xFFE1B12C.toInt()
            "flying" -> 0xFF9980FA.toInt()
            "psychic" -> 0xFFED4C67.toInt()
            "bug" -> 0xFF26DE81.toInt()
            "rock" -> 0xFF778CA3.toInt()
            "ghost" -> 0xFF57606F.toInt()
            "dragon" -> 0xFF3B3B98.toInt()
            "dark" -> 0xFF2F3542.toInt()
            "steel" -> 0xFF718093.toInt()
            "fairy" -> 0xFFFDA7DF.toInt()
            else -> 0xFF747D8C.toInt()
        }
    }
}
