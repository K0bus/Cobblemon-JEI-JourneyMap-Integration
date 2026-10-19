package fr.k0bus.cobblemon_jei.journeymap

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty
import journeymap.api.v2.client.IClientAPI
import journeymap.api.v2.client.display.Context
import journeymap.api.v2.client.display.MarkerOverlay
import journeymap.api.v2.client.model.MapImage
import journeymap.api.v2.client.model.TextProperties
import journeymap.api.v2.client.util.UIState
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service de détection et de traçage en temps réel des Pokémon vivants dans le monde client.
 *
 * Fonctionnalités clés :
 * 1. **Sentinelle Shiny globale** : Écoute constante de TOUS les Pokémon Shiny sauvages à proximité,
 *    même si aucun Pokémon n'est traqué ou si un Pokémon différent est sélectionné.
 * 2. **Radar de cible traquée** : Suivi des entités correspondant à l'espèce sélectionnée par l'utilisateur.
 * 3. **Marqueurs dynamiques JourneyMap** : Affichage et mise à jour des positions sur Minimap et Plein écran.
 * 4. **Alertes sonores et visuelles** : Fanfare et actionbar lors de la première découverte d'un Shiny.
 */
object PokemonRadarService {

    private const val MOD_ID = "cobblemon_jei"
    private const val OVERLAY_GROUP_RADAR = "Cobblemon Radar"
    private val logger = LoggerFactory.getLogger("CobblemonJM/Radar")

    // Textures des marqueurs JourneyMap pour le radar (Normal & Shiny)
    private val ICON_NORMAL_LOC = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/radar_marker.png")
    private val ICON_SHINY_LOC = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/radar_marker_shiny.png")

    private val mapImageNormal by lazy {
        MapImage(ICON_NORMAL_LOC, 24, 24).centerAnchors()
    }
    private val mapImageShiny by lazy {
        MapImage(ICON_SHINY_LOC, 26, 26).centerAnchors()
    }

    private var clientApi: IClientAPI? = null
    private var isRegistered = false

    // Compteur de ticks pour cadencer le scan (toutes les 20 ticks = 1 seconde)
    private var tickCounter = 0

    // Rayon de scan horizontal et vertical en blocs autour du joueur
    private const val SCAN_RADIUS_XZ = 160.0
    private const val SCAN_RADIUS_Y = 64.0

    /**
     * Données d'un Pokémon détecté en temps réel par le radar.
     */
    data class DetectedPokemon(
        val entityId: Int,
        val uuid: UUID,
        val species: Species,
        val form: FormData,
        val isShiny: Boolean,
        val level: Int,
        val isWild: Boolean,
        var blockPos: BlockPos,
        var marker: MarkerOverlay? = null
    )

    // Table de toutes les entités actuellement affichées sur JourneyMap (UUID -> DetectedPokemon)
    private val trackedEntities = ConcurrentHashMap<UUID, DetectedPokemon>()

    // Mémorise les UUIDs déjà signalés sonores pour ne pas rejouer le son à chaque seconde
    private val alertedEntities = ConcurrentHashMap.newKeySet<UUID>()

    // Nombre d'entités de l'espèce traquée actuellement détectées
    var detectedCount: Int = 0
        private set

    // Nombre de Shiny globaux (tous Pokémon confondus) actuellement détectés à proximité
    var shinyNearbyCount: Int = 0
        private set

    val hasShinyNearby: Boolean
        get() = shinyNearbyCount > 0

    private var lastDimension: ResourceKey<Level>? = null

    /**
     * Initialise la liaison avec l'API JourneyMap v2.
     */
    fun initialize(api: IClientAPI) {
        this.clientApi = api
        registerEvents()
        logger.info("Cobblemon JourneyMap PokemonRadarService (Sentinelle Shiny Globale) lié à l'API v2.")
    }

    /**
     * Enregistre l'écouteur ClientTickEvent.Post sur le bus NeoForge.
     */
    fun registerEvents() {
        if (isRegistered) return
        isRegistered = true
        try {
            NeoForge.EVENT_BUS.addListener(::onClientTick)
            logger.info("PokemonRadarService: Écouteur ClientTickEvent enregistré.")
        } catch (t: Throwable) {
            logger.error("PokemonRadarService: Erreur enregistrement ClientTickEvent: {}", t.message)
        }
    }

    /**
     * Événement de tick client : cadencé toutes les 20 ticks (1 seconde).
     */
    fun onClientTick(event: ClientTickEvent.Post) {
        tickCounter++
        if (tickCounter % 20 != 0) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: run {
            clearRadar()
            return
        }
        val level = mc.level ?: run {
            clearRadar()
            return
        }

        // Nettoyage automatique et chargement en cas de changement de dimension ou de monde
        val currentDim = level.dimension()
        if (lastDimension != currentDim) {
            clearRadar()
            lastDimension = currentDim
            try {
                fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager.onWorldLoad(currentDim)
            } catch (_: Throwable) {}
        }

        // Détection en direct des piédestaux et blocs suspects dans les chunks actifs autour du joueur
        if (tickCounter % 40 == 0) {
            try {
                val pPos = player.blockPosition()
                val pcx = pPos.x shr 4
                val pcz = pPos.z shr 4
                for (dx in -1..1) {
                    for (dz in -1..1) {
                        val chunk = level.getChunk(pcx + dx, pcz + dz)
                        if (chunk is net.minecraft.world.level.chunk.LevelChunk) {
                            fr.k0bus.cobblemon_jei.journeymap.structure.StructureRegionScanner.onChunkLoaded(chunk)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        scanWorld(mc, level, player)
    }

    /**
     * Vérifie scrupuleusement si une entité [PokemonEntity] est sauvage, capturable et n'appartient
     * à aucun joueur ou système (exclut les Pokémon de pâturage, de suivi, portés sur l'épaule, montures ou apprivoisés).
     */
    fun isWildAndCatchable(entity: PokemonEntity): Boolean {
        if (!entity.isAlive || entity.isRemoved) return false

        val pokemon = entity.pokemon

        // 1. Doit être un Pokémon sauvage (non stocké dans une Party/PC)
        if (!pokemon.isWild()) return false
        if (pokemon.isPlayerOwned()) return false

        // 2. Ne doit pas appartenir à un dresseur / joueur (suivi, sorti de ball, apprivoisé)
        if (entity.ownerUUID != null || pokemon.getOwnerUUID() != null) return false
        if (entity.isTame) return false
        if (pokemon.getOwnerPlayer() != null) return false

        // 3. Ne doit pas être un Pokémon de pâturage (Pasture block)
        if (entity.tethering != null || pokemon.tetheringId != null) return false

        // 4. Ne doit pas être monté, conduit ou porté sur l'épaule (passager ou véhicule)
        if (entity.isPassenger || entity.vehicle != null || entity.passengers.isNotEmpty()) return false

        // 5. Doit être capturable selon Cobblemon
        if (entity.isUncatchable() || pokemon.isUncatchable()) return false
        try {
            if (!UncatchableProperty.isCatchable(entity)) return false
        } catch (_: Throwable) {}

        // 6. Ne doit pas être inattaquable / incapturable (ex: PNJ, cinématiques)
        try {
            if (entity.entityData.get(PokemonEntity.UNBATTLEABLE)) return false
        } catch (_: Throwable) {}

        return true
    }

    /**
     * Effectue le scan des entités `PokemonEntity` dans le monde client et synchronise les marqueurs.
     * Détecte uniquement les Pokémon sauvages et capturables :
     * 1. TOUS les Pokémon Shiny sauvages et capturables dans le périmètre (Sentinelle globale).
     * 2. Les Pokémon de l'espèce traquée sauvages et capturables (si un overlay est actif).
     */
    private fun scanWorld(
        mc: Minecraft,
        level: net.minecraft.client.multiplayer.ClientLevel,
        player: net.minecraft.world.entity.player.Player
    ) {
        val api = clientApi ?: return

        val playerPos = player.position()
        val boundingBox = AABB(
            playerPos.x - SCAN_RADIUS_XZ, playerPos.y - SCAN_RADIUS_Y, playerPos.z - SCAN_RADIUS_XZ,
            playerPos.x + SCAN_RADIUS_XZ, playerPos.y + SCAN_RADIUS_Y, playerPos.z + SCAN_RADIUS_XZ
        )

        val foundPokemons = mutableMapOf<UUID, DetectedPokemon>()
        var foundNewShinySpecies: Species? = null
        var foundNewTargetSpecies: Species? = null
        var currentShinyCount = 0
        var currentTargetCount = 0

        try {
            val entities = level.getEntitiesOfClass(PokemonEntity::class.java, boundingBox)
            for (entity in entities) {
                // Filtrer rigoureusement : UNIQUEMENT les Pokémon sauvages et capturables
                // (exclut pâturages, Pokémon de suivi, dresseurs, uncatchable, etc.)
                if (!isWildAndCatchable(entity)) continue

                val pokemon = entity.pokemon
                val species = pokemon.species
                val form = pokemon.form
                val isShiny = pokemon.shiny

                // Vérifier si cette entité doit être suivie par le radar :
                // Cas A : C'est un Shiny sauvage capturable -> Sentinelle permanente activée !
                // Cas B : C'est une cible valide selon le mode de tracking actif (non capturé, cible unique, lignée, liste)
                val matchesTarget = fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.isTarget(species, form)
                val isGlobalShiny = isShiny

                if (!isGlobalShiny && !matchesTarget) {
                    continue
                }

                if (isGlobalShiny) {
                    currentShinyCount++
                }
                if (matchesTarget) {
                    currentTargetCount++
                }

                val uuid = entity.uuid
                val currentBlockPos = entity.blockPosition()
                val detected = DetectedPokemon(
                    entityId = entity.id,
                    uuid = uuid,
                    species = species,
                    form = form,
                    isShiny = isShiny,
                    level = pokemon.level,
                    isWild = pokemon.isWild(),
                    blockPos = currentBlockPos
                )
                foundPokemons[uuid] = detected

                if (!alertedEntities.contains(uuid)) {
                    if (isShiny) {
                        foundNewShinySpecies = species
                    } else if (matchesTarget) {
                        foundNewTargetSpecies = species
                    }
                    alertedEntities.add(uuid)
                }
            }
        } catch (e: Throwable) {
            logger.debug("PokemonRadarService: Erreur lors de l'extraction des entités: {}", e.message)
            return
        }

        // 1. Suppression des marqueurs pour les Pokémon ayant disparu (capturés, despawnés ou hors de portée)
        val toRemove = trackedEntities.keys.filter { !foundPokemons.containsKey(it) }
        for (deadUuid in toRemove) {
            val removed = trackedEntities.remove(deadUuid)
            removed?.marker?.let { marker ->
                try {
                    api.remove(marker)
                } catch (_: Throwable) {}
            }
            alertedEntities.remove(deadUuid)
        }

        val dimKey = level.dimension()

        // 2. Création ou mise à jour de la position des marqueurs actifs
        for ((uuid, detected) in foundPokemons) {
            val existing = trackedEntities[uuid]
            if (existing != null && existing.marker != null) {
                val marker = existing.marker!!
                if (marker.point != detected.blockPos) {
                    marker.point = detected.blockPos
                    try {
                        api.show(marker)
                    } catch (_: Throwable) {}
                }
                existing.blockPos = detected.blockPos
            } else {
                val marker = createMarkerOverlay(detected, dimKey)
                detected.marker = marker
                trackedEntities[uuid] = detected
                try {
                    api.show(marker)
                } catch (e: Throwable) {
                    logger.debug("PokemonRadarService: Impossible d'afficher le marqueur: {}", e.message)
                }
            }
        }

        detectedCount = currentTargetCount
        shinyNearbyCount = currentShinyCount

        // 3. Notification sonore et visuelle prioritaire pour les Shiny
        if (foundNewShinySpecies != null) {
            playShinyAlert(mc, player, foundNewShinySpecies)
        } else if (foundNewTargetSpecies != null) {
            playTargetAlert(mc, player, foundNewTargetSpecies)
        }
    }

    /**
     * Crée et configure un [MarkerOverlay] JourneyMap pour le Pokémon détecté.
     */
    private fun createMarkerOverlay(detected: DetectedPokemon, dimension: ResourceKey<Level>): MarkerOverlay {
        val icon = if (detected.isShiny) mapImageShiny else mapImageNormal
        val marker = MarkerOverlay(MOD_ID, detected.blockPos, icon)

        val shinyTag = if (detected.isShiny) " §6★ §eSHINY §6★" else ""
        val targetTag = if (!detected.isShiny) fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.getTargetTag(detected.species, detected.form) else ""
        val wildTag = if (detected.isWild) "§7(Sauvage)" else "§b(Dressé)"
        val titleText = "§f${detected.species.translatedName.string}$shinyTag$targetTag\n" +
                "§7Niveau: §e${detected.level} $wildTag\n" +
                "§8Position: ${detected.blockPos.x}, ${detected.blockPos.y}, ${detected.blockPos.z}"

        val labelPrefix = when {
            detected.isShiny -> "★ "
            fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.currentMode == fr.k0bus.cobblemon_jei.pokedex.TrackingMode.ALL_UNCAUGHT -> "[Dex] "
            fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.currentMode == fr.k0bus.cobblemon_jei.pokedex.TrackingMode.EVOLUTION_LINE -> "[Évo] "
            fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.currentMode == fr.k0bus.cobblemon_jei.pokedex.TrackingMode.CUSTOM_LIST -> "[★] "
            else -> ""
        }
        val labelText = "$labelPrefix${detected.species.translatedName.string} Lv.${detected.level}"

        marker.overlayGroupName = OVERLAY_GROUP_RADAR
        marker.title = titleText
        marker.label = labelText
        marker.dimension = dimension
        marker.setActiveUIs(Context.UI.Minimap, Context.UI.Fullscreen)
        marker.minZoom = UIState.FULLSCREEN_ZOOM_MIN
        marker.maxZoom = UIState.ZOOM_IN_MAX
        marker.displayOrder = if (detected.isShiny) 1600 else 1500

        val textColor = if (detected.isShiny) 0xFFD700 else 0xFFFFFF
        val textProps = TextProperties()
            .setScale(1.1f)
            .setColor(textColor)
            .setBackgroundColor(0x181920)
            .setBackgroundOpacity(0.85f)
            .setFontShadow(true)
            .setActiveUIs(Context.UI.Minimap, Context.UI.Fullscreen)
        marker.textProperties = textProps

        return marker
    }

    /**
     * Alerte sonore et actionbar lors de l'apparition d'un Pokémon Shiny (Sentinelle).
     */
    private fun playShinyAlert(
        mc: Minecraft,
        player: net.minecraft.world.entity.player.Player,
        species: Species
    ) {
        mc.execute {
            try {
                player.level().playLocalSound(
                    player.x, player.y, player.z,
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.2f,
                    false
                )
                player.displayClientMessage(
                    Component.translatable("chat.cobblemon_jm.radar.shiny", species.translatedName),
                    true
                )
            } catch (_: Throwable) {}
        }
    }

    /**
     * Alerte sonore et actionbar lors de l'apparition du Pokémon ciblé.
     */
    private fun playTargetAlert(
        mc: Minecraft,
        player: net.minecraft.world.entity.player.Player,
        species: Species
    ) {
        mc.execute {
            try {
                player.level().playLocalSound(
                    player.x, player.y, player.z,
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS,
                    0.75f,
                    1.0f,
                    false
                )
                val msg = when (fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.currentMode) {
                    fr.k0bus.cobblemon_jei.pokedex.TrackingMode.ALL_UNCAUGHT ->
                        Component.translatable("chat.cobblemon_jm.radar.nearby_uncaught", species.translatedName)
                    fr.k0bus.cobblemon_jei.pokedex.TrackingMode.EVOLUTION_LINE ->
                        Component.translatable("chat.cobblemon_jm.radar.nearby_line", species.translatedName)
                    fr.k0bus.cobblemon_jei.pokedex.TrackingMode.CUSTOM_LIST ->
                        Component.translatable("chat.cobblemon_jm.radar.nearby_list", species.translatedName)
                    else ->
                        Component.translatable("chat.cobblemon_jm.radar.nearby", species.translatedName)
                }
                player.displayClientMessage(msg, true)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Nettoie les marqueurs du Pokémon traqué mais conserve les Shiny détectés si le monde est toujours actif.
     */
    fun clearTrackedOnly() {
        val api = clientApi
        val iterator = trackedEntities.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val detected = entry.value
            if (!detected.isShiny) {
                if (api != null && detected.marker != null) {
                    try {
                        api.remove(detected.marker!!)
                    } catch (_: Throwable) {}
                }
                alertedEntities.remove(entry.key)
                iterator.remove()
            }
        }
        detectedCount = 0
    }

    /**
     * Nettoie tous les marqueurs du radar sur JourneyMap (déconnexion ou changement de dimension).
     */
    fun clearRadar() {
        val api = clientApi
        if (api != null) {
            try {
                for (detected in trackedEntities.values) {
                    detected.marker?.let { api.remove(it) }
                }
            } catch (_: Throwable) {}
        }
        trackedEntities.clear()
        alertedEntities.clear()
        detectedCount = 0
        shinyNearbyCount = 0
    }

    /**
     * Fournit un résumé textuel formaté de l'état du radar pour l'intégration dans l'interface HUD / Tooltips.
     */
    fun getStatusSummary(speciesName: String = ""): String {
        val modeSummary = fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.getModeSummary()
        val nameToShow = speciesName.ifBlank { modeSummary }
        return when {
            hasShinyNearby -> "§6★ $shinyNearbyCount SHINY à proximité ! §e($detectedCount $nameToShow)"
            detectedCount > 0 -> "§a$detectedCount cible(s) à portée §7($nameToShow)"
            fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.isTrackingActive -> "§70 cible ($nameToShow)"
            else -> "§7Aucun spécimen à portée"
        }
    }
}
