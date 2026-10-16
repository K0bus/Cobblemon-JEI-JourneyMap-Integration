package fr.k0bus.cobblemon_jei.journeymap.structure

import fr.k0bus.cobblemon_jei.journeymap.structure.DynamicStructureRegistryService.StructureMetadata
import fr.k0bus.cobblemon_jei.journeymap.structure.DynamicStructureRegistryService.StructureType
import com.cobblemon.mod.common.pokemon.Species
import journeymap.api.v2.client.IClientAPI
import journeymap.api.v2.client.display.MarkerOverlay
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinateur JourneyMap centralisant la création, la mise à jour,
 * le filtrage par calques et le nettoyage des [MarkerOverlay] de structures.
 *
 * Gère deux catégories distinctes dans JourneyMap :
 * 1. `"Cobblemon Fossils"` pour les sites archéologiques de fouilles.
 * 2. `"Legendary Monuments"` pour les sanctuaires et monuments de légendaires.
 */
object StructureOverlayManager {

    private val logger = LoggerFactory.getLogger("CobblemonJM/StructureOverlayManager")
    private var clientApi: IClientAPI? = null

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

    /**
     * Initialise la liaison avec l'API JourneyMap v2.
     */
    fun initialize(api: IClientAPI) {
        this.clientApi = api
        logger.info("Cobblemon JourneyMap StructureOverlayManager initialisé avec succès.")
    }

    /**
     * Déclenche l'actualisation du registre et le scan asynchrone des régions.
     */
    fun onWorldLoad(dimension: ResourceKey<Level>) {
        this.currentDimension = dimension
        DynamicStructureRegistryService.refreshRegistry(null)

        // Lancement du scan asynchrone sans bloquer le rendu
        StructureRegionScanner.scanExploredStructuresAsync(dimension) { structId, pos, _ ->
            registerDiscoveredStructure(structId, pos, dimension)
        }
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
     * Génère et affiche le [MarkerOverlay] JourneyMap pour la structure.
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

        // Réactualise tous les marqueurs pour appliquer le style doré / pulsant
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

        val targetName = target.name.lowercase().replace(" ", "_").replace("-", "_")
        if (meta.targetPokemonName?.lowercase()?.replace("-", "_") == targetName) {
            return true
        }

        return structure.id.path.contains(targetName)
    }

    /**
     * Retourne toutes les structures découvertes associées à une espèce de Pokémon.
     */
    fun getDiscoveredStructuresForSpecies(species: Species): List<DiscoveredStructure> {
        val targetName = species.name.lowercase().replace(" ", "_").replace("-", "_")
        return discoveredStructures.values.filter { structure ->
            val meta = structure.metadata
            meta.associatedPokemonSpecies.any { it.name.equals(species.name, ignoreCase = true) } ||
            meta.targetPokemonName?.lowercase()?.replace("-", "_") == targetName ||
            structure.id.path.contains(targetName)
        }
    }

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
