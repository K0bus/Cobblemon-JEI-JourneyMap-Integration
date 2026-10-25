package fr.k0bus.cobblemon_jei.journeymap.structure

import fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat
import fr.k0bus.cobblemon_jei.journeymap.structure.DynamicStructureRegistryService.StructureMetadata
import fr.k0bus.cobblemon_jei.journeymap.structure.DynamicStructureRegistryService.StructureType
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import journeymap.api.v2.client.IClientAPI
import journeymap.api.v2.client.display.MarkerOverlay
import journeymap.api.v2.common.waypoint.WaypointFactory
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinateur JourneyMap centralisant la détection des monuments légendaires,
 * la création des marqueurs sur la minimap, l'enregistrement des waypoints dans JourneyMap
 * et l'exposition des structures découvertes pour l'intégration JEI et le Pokédex.
 *
 * Catégories gérées :
 * 1. `"Legendary Monument"` pour les sanctuaires et monuments de Pokémon légendaires.
 * 2. `"Cobblemon Fossils"` pour les sites archéologiques de fouilles.
 */
object StructureOverlayManager {

    private const val MOD_ID = "cobblemon_jei"
    const val WAYPOINT_GROUP_LEGENDARY = "Legendary Monument"

    private val logger = LoggerFactory.getLogger("CobblemonJM/StructureOverlayManager")
    private var clientApi: IClientAPI? = null

    // Mode actuel de suivi des monuments légendaires (par défaut : uniquement les monuments majeurs)
    var trackingMode: LegendaryMonumentsCompat.MonumentTrackingMode = LegendaryMonumentsCompat.MonumentTrackingMode.MAJOR_ONLY
        private set

    private var isConfigLoaded = false
    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class DiscoveredStructure(
        val id: ResourceLocation,
        var pos: BlockPos,
        val dimension: ResourceKey<Level>,
        val metadata: StructureMetadata,
        var pedestalPos: BlockPos? = null,
        var marker: MarkerOverlay? = null
    )

    // Table de toutes les structures découvertes (Dimension + BlockPos -> DiscoveredStructure)
    private val discoveredStructures = ConcurrentHashMap<Pair<ResourceKey<Level>, BlockPos>, DiscoveredStructure>()

    // Espèce actuellement ciblée pour mise en évidence
    var highlightedSpecies: Species? = null
        private set

    private var currentDimension: ResourceKey<Level>? = null

    private fun getConfigFile(): File? {
        return try {
            val gameDir = Minecraft.getInstance().gameDirectory ?: return null
            val configDir = File(gameDir, "config")
            if (!configDir.exists()) configDir.mkdirs()
            File(configDir, "cobblemon_monument_tracker.json")
        } catch (_: Throwable) {
            null
        }
    }

    fun ensureConfigLoaded() {
        if (!isConfigLoaded) {
            loadConfig()
        }
    }

    fun loadConfig() {
        isConfigLoaded = true
        try {
            val file = getConfigFile() ?: return
            if (!file.exists()) return
            val json = file.readText(Charsets.UTF_8)
            val root = JsonParser.parseString(json).asJsonObject
            if (root.has("mode")) {
                val modeStr = root.get("mode").asString
                trackingMode = try {
                    LegendaryMonumentsCompat.MonumentTrackingMode.valueOf(modeStr)
                } catch (_: Throwable) {
                    LegendaryMonumentsCompat.MonumentTrackingMode.MAJOR_ONLY
                }
            }
            logger.info("Cobblemon JM: Mode de tracking des monuments chargé : {}", trackingMode.name)
        } catch (e: Throwable) {
            logger.debug("Cobblemon JM: Erreur chargement config monument tracker: {}", e.message)
        }
    }

    fun saveConfig() {
        try {
            val file = getConfigFile() ?: return
            val root = JsonObject()
            root.addProperty("mode", trackingMode.name)
            file.writeText(gson.toJson(root), Charsets.UTF_8)
        } catch (e: Throwable) {
            logger.debug("Cobblemon JM: Erreur sauvegarde config monument tracker: {}", e.message)
        }
    }

    /**
     * Fait défiler le mode de tracking vers le mode suivant et applique le changement.
     */
    fun cycleTrackingMode(): LegendaryMonumentsCompat.MonumentTrackingMode {
        ensureConfigLoaded()
        val nextMode = trackingMode.next()
        setTrackingMode(nextMode)
        return nextMode
    }

    /**
     * Définit le mode de tracking des monuments légendaires.
     */
    fun setTrackingMode(newMode: LegendaryMonumentsCompat.MonumentTrackingMode) {
        this.trackingMode = newMode
        saveConfig()
        logger.info("Cobblemon JM: Mode de tracking des monuments basculé sur : {}", newMode.name)

        // Réinitialise le cache du scanner de chunks pour réanalyser selon les nouveaux critères
        StructureChunkScanner.clearCache()

        // Met à jour la visibilité des marqueurs existants sur JourneyMap
        updateMarkersVisibility()
    }

    /**
     * Vérifie si un bloc de monument donné doit être scanné et affiché selon le mode actif.
     */
    fun shouldScanBlock(blockId: ResourceLocation): Boolean {
        ensureConfigLoaded()
        return LegendaryMonumentsCompat.matchesTrackingMode(blockId, trackingMode)
    }

    /**
     * Actualise l'affichage des marqueurs affichés selon le mode actuel.
     */
    private fun updateMarkersVisibility() {
        val api = clientApi ?: return
        for (structure in discoveredStructures.values) {
            if (structure.metadata.type == StructureType.LEGENDARY) {
                val shouldBeVisible = LegendaryMonumentsCompat.matchesTrackingMode(structure.id, trackingMode)
                if (!shouldBeVisible) {
                    structure.marker?.let { m ->
                        try { api.remove(m) } catch (_: Throwable) {}
                    }
                    structure.marker = null
                } else if (structure.marker == null) {
                    renderStructureMarker(structure)
                }
            }
        }
    }

    /**
     * Initialise la liaison avec l'API JourneyMap v2.
     */
    fun initialize(api: IClientAPI) {
        this.clientApi = api
        ensureConfigLoaded()
        logger.info("Cobblemon JourneyMap StructureOverlayManager initialisé avec succès. Mode : {}", trackingMode.name)
    }

    /**
     * Réinitialise les caches lors du chargement d'un monde ou changement de dimension.
     */
    fun onWorldLoad(dimension: ResourceKey<Level>) {
        this.currentDimension = dimension
        ensureConfigLoaded()
        DynamicStructureRegistryService.refreshRegistry(null)
        StructureChunkScanner.clearCache()
        logger.info("Cobblemon JM: Nouveau monde/dimension chargé : {}", dimension.location())
    }

    /**
     * Déclenché en direct par [StructureChunkScanner] dès qu'un bloc rituel propre à un monument
     * légendaire est identifié dans un chunk chargé.
     */
    fun onMonumentDiscovered(
        dimension: ResourceKey<Level>,
        pos: BlockPos,
        blockId: ResourceLocation
    ) {
        if (!shouldScanBlock(blockId)) return

        val key = Pair(dimension, pos)
        if (discoveredStructures.containsKey(key)) return

        // 1. Déduction de l'espèce de Pokémon et des métadonnées
        val speciesName = LegendaryMonumentsCompat.extractSpeciesNameFromBlock(blockId)
        val species = speciesName?.let {
            try { PokemonSpecies.getByName(it) } catch (_: Throwable) { null }
        }

        val knownInfo = speciesName?.let { LegendaryMonumentsCompat.KNOWN_MONUMENTS[it] }
        val displayName = knownInfo?.structureDisplayName
            ?: (species?.translatedName?.string ?: speciesName?.replaceFirstChar { it.uppercase() } ?: "Monument Légendaire")

        val iconLoc = resolveMonumentIcon(blockId, speciesName)

        val metadata = StructureMetadata(
            id = blockId,
            type = StructureType.LEGENDARY,
            displayName = displayName,
            targetPokemonName = speciesName,
            associatedPokemonSpecies = if (species != null) listOf(species) else emptyList(),
            iconLocation = iconLoc
        )

        val structure = DiscoveredStructure(
            id = blockId,
            pos = pos,
            dimension = dimension,
            metadata = metadata,
            pedestalPos = pos
        )

        discoveredStructures[key] = structure
        logger.info("Cobblemon JM: Monument enregistré : {} à {} [Dim: {}]", displayName, pos, dimension.location())

        // 2. Affichage sur la minimap et la carte plein écran
        renderStructureMarker(structure)

        // 3. Création automatique du Waypoint JourneyMap
        createJourneyMapWaypoint(dimension, pos, metadata, speciesName, blockId)
    }

    /**
     * Enregistre un Waypoint persistant dans JourneyMap sous la catégorie "Legendary Monument".
     */
    private fun createJourneyMapWaypoint(
        dimension: ResourceKey<Level>,
        pos: BlockPos,
        metadata: StructureMetadata,
        speciesName: String?,
        blockId: ResourceLocation
    ) {
        val api = clientApi ?: return
        try {
            val isFootprint = LegendaryMonumentsCompat.isFootprintBlock(blockId)
            val pokeDisplayName = speciesName?.replaceFirstChar { it.uppercase() } ?: metadata.displayName
            val waypointName = if (isFootprint) "Empreinte : $pokeDisplayName" else "Monument : $pokeDisplayName"

            // Vérification si un waypoint similaire n'existe pas déjà
            // Pour les empreintes, on applique une déduplication à 32 blocs pour éviter de saturer la liste
            val existingWaypoints = api.getWaypoints(MOD_ID)
            val dimStr = dimension.location().toString()
            val alreadyPresent = existingWaypoints.any { wp ->
                if (wp.primaryDimension != dimStr) return@any false
                if (isFootprint) {
                    wp.blockPos.distSqr(pos) <= 32.0 * 32.0
                } else {
                    wp.blockPos == pos
                }
            }

            if (!alreadyPresent) {
                val waypoint = WaypointFactory.createClientWaypoint(
                    waypointName,
                    pos,
                    WAYPOINT_GROUP_LEGENDARY,
                    dimension,
                    true
                )
                waypoint.color = if (isFootprint) 0x2ED573 else 0xFFD700 // Vert émeraude pour empreintes, Or pour monuments
                try {
                    waypoint.setIconResourceLoctaion(metadata.iconLocation)
                } catch (_: Throwable) {}

                api.addWaypoint(MOD_ID, waypoint)
                logger.info("Cobblemon JM: Waypoint JourneyMap créé dans '{}': {} à {}", WAYPOINT_GROUP_LEGENDARY, waypointName, pos)
            }
        } catch (t: Throwable) {
            logger.warn("Cobblemon JM: Impossible de créer le Waypoint JourneyMap: {}", t.message)
        }
    }


    /**
     * Résout l'icône JourneyMap la plus pertinente pour un bloc de monument.
     */
    private fun resolveMonumentIcon(blockId: ResourceLocation, speciesName: String?): ResourceLocation {
        if (blockId.namespace == "legendarymonuments") {
            // Tente d'utiliser une icône de structure officielle du mod
            val simple = blockId.path.removeSuffix("_pedestal").removeSuffix("_shrine").removeSuffix("_altar")
            val candidate = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "textures/gui/structure_icons/$simple.png")
            return candidate
        }
        return DynamicStructureRegistryService.DEFAULT_LEGENDARY_ICON
    }

    /**
     * Enregistre une structure découverte et génère son marqueur JourneyMap.
     */
    fun registerDiscoveredStructure(
        id: ResourceLocation,
        pos: BlockPos,
        dimension: ResourceKey<Level>
    ) {
        val meta = DynamicStructureRegistryService.getMetadata(id)
        if (meta.type == StructureType.OTHER) return

        val key = Pair(dimension, pos)
        if (discoveredStructures.containsKey(key)) return

        val structure = DiscoveredStructure(
            id = id,
            pos = pos,
            dimension = dimension,
            metadata = meta
        )

        discoveredStructures[key] = structure
        renderStructureMarker(structure)
    }

    /**
     * Génère et affiche le [MarkerOverlay] JourneyMap pour la structure sur la minimap et le fullscreen.
     */
    private fun renderStructureMarker(structure: DiscoveredStructure) {
        val api = clientApi ?: return
        val isTarget = isMatchingHighlightedSpecies(structure)

        val newMarker = when (structure.metadata.type) {
            StructureType.FOSSIL -> FossilStructureHandler.createMarkerOverlay(
                structureId = structure.id,
                pos = structure.pos,
                dimension = structure.dimension,
                metadata = structure.metadata,
                isHighlighted = isTarget
            )
            StructureType.LEGENDARY -> LegendaryStructureHandler.createMarkerOverlay(
                structureId = structure.id,
                pos = structure.pos,
                dimension = structure.dimension,
                metadata = structure.metadata,
                pedestalPos = structure.pedestalPos,
                isHighlighted = isTarget
            )
            else -> null
        } ?: return

        // Remplacement propre du marqueur existant
        structure.marker?.let { old ->
            try { api.remove(old) } catch (_: Throwable) {}
        }

        structure.marker = newMarker
        try {
            api.show(newMarker)
        } catch (t: Throwable) {
            logger.debug("Cobblemon JM: Erreur affichage marqueur structure {}: {}", structure.id, t.message)
        }
    }

    /**
     * Affine la coordonnée exacte du piédestal détecté dans un chunk chargé.
     */
    fun refinePedestalPosition(dimension: ResourceKey<Level>, chunkPos: ChunkPos, pedestalPos: BlockPos) {
        for ((key, structure) in discoveredStructures) {
            if (key.first == dimension && structure.metadata.type == StructureType.LEGENDARY) {
                val sChunkX = structure.pos.x shr 4
                val sChunkZ = structure.pos.z shr 4
                if (kotlin.math.abs(sChunkX - chunkPos.x) <= 2 && kotlin.math.abs(sChunkZ - chunkPos.z) <= 2) {
                    if (structure.pedestalPos != pedestalPos) {
                        structure.pedestalPos = pedestalPos
                        renderStructureMarker(structure)
                    }
                }
            }
        }
    }

    /**
     * Affine la coordonnée du site de fossile avec la position d'un bloc suspect détecté.
     */
    fun refineFossilSitePosition(dimension: ResourceKey<Level>, chunkPos: ChunkPos, suspiciousPos: BlockPos) {
        for ((key, structure) in discoveredStructures) {
            if (key.first == dimension && structure.metadata.type == StructureType.FOSSIL) {
                val sChunkX = structure.pos.x shr 4
                val sChunkZ = structure.pos.z shr 4
                if (kotlin.math.abs(sChunkX - chunkPos.x) <= 1 && kotlin.math.abs(sChunkZ - chunkPos.z) <= 1) {
                    structure.pos = suspiciousPos
                    renderStructureMarker(structure)
                }
            }
        }
    }

    /**
     * Active la mise en évidence des structures associées au Pokémon sélectionné par le joueur.
     */
    fun highlightStructureForSpecies(species: Species?) {
        this.highlightedSpecies = species

        for (structure in discoveredStructures.values) {
            renderStructureMarker(structure)
        }
    }

    /**
     * Vérifie si une structure correspond au Pokémon actuellement mis en avant.
     */
    private fun isMatchingHighlightedSpecies(structure: DiscoveredStructure): Boolean {
        val target = highlightedSpecies ?: return false
        val meta = structure.metadata

        if (meta.associatedPokemonSpecies.any { it.name.equals(target.name, ignoreCase = true) }) {
            return true
        }

        val targetName = target.name.lowercase(Locale.ROOT).replace(" ", "_").replace("-", "_")
        if (meta.targetPokemonName?.lowercase(Locale.ROOT)?.replace("-", "_") == targetName) {
            return true
        }

        return structure.id.path.contains(targetName)
    }

    /**
     * Retourne toutes les structures découvertes associées à une espèce de Pokémon.
     */
    fun getDiscoveredStructuresForSpecies(species: Species): List<DiscoveredStructure> {
        val targetName = species.name.lowercase(Locale.ROOT).replace(" ", "_").replace("-", "_")
        return discoveredStructures.values.filter { structure ->
            val meta = structure.metadata
            meta.associatedPokemonSpecies.any { it.name.equals(species.name, ignoreCase = true) } ||
            meta.targetPokemonName?.lowercase(Locale.ROOT)?.replace("-", "_") == targetName ||
            structure.id.path.contains(targetName)
        }
    }

    /**
     * Retourne le monument découvert pour une espèce donnée, si exploré dans le monde.
     */
    fun getDiscoveredMonumentForSpecies(speciesName: String): DiscoveredStructure? {
        val target = speciesName.lowercase(Locale.ROOT).replace(" ", "_").replace("-", "_")
        return discoveredStructures.values.firstOrNull { structure ->
            val meta = structure.metadata
            meta.associatedPokemonSpecies.any { it.name.equals(speciesName, ignoreCase = true) } ||
            meta.targetPokemonName?.lowercase(Locale.ROOT)?.replace("-", "_") == target ||
            structure.id.path.contains(target)
        }
    }

    /**
     * Retourne l'ensemble de toutes les structures découvertes jusqu'à présent.
     */
    fun getAllDiscoveredStructures(): Collection<DiscoveredStructure> = discoveredStructures.values

    /**
     * Résumé textuel pour les infobulles ou écrans de recherche.
     */
    fun getStatusSummary(species: Species): String {
        val matching = getDiscoveredStructuresForSpecies(species)
        return if (matching.isNotEmpty()) {
            val first = matching.first()
            "✔ ${matching.size} structure(s) découverte(s) (${first.metadata.displayName} [${first.pos.x}, ${first.pos.z}])"
        } else {
            "✗ Aucune structure découverte dans les zones explorées"
        }
    }

    /**
     * Nettoie tous les marqueurs affichés.
     */
    fun clearAllOverlays() {
        val api = clientApi
        if (api != null) {
            for (structure in discoveredStructures.values) {
                structure.marker?.let { m ->
                    try { api.remove(m) } catch (_: Throwable) {}
                }
                structure.marker = null
            }
        }
        discoveredStructures.clear()
        highlightedSpecies = null
    }
}
