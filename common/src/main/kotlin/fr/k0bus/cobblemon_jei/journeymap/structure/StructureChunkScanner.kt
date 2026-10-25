package fr.k0bus.cobblemon_jei.journeymap.structure

import fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Scanner ultra-performant de structures et monuments légendaires opérant au chargement de chunks.
 *
 * Remplace l'ancienne lecture de fichiers Anvil (.mca) sur disque :
 * 1. Détecte directement les blocs propres à [Cobblemon: Legendary Monuments] (piédestaux, autels, cocons, etc.).
 * 2. Utilise [net.minecraft.world.level.chunk.LevelChunkSection.maybeHas] pour tester les palettes en O(1)
 *    sans itérer sur les 4096 blocs d'une section si aucun bloc de monument n'y est présent.
 * 3. Transmet immédiatement la position exacte au [StructureOverlayManager] pour créer
 *    l'overlay minimap et le waypoint JourneyMap.
 */
object StructureChunkScanner {

    private val logger = LoggerFactory.getLogger("CobblemonJM/StructureChunkScanner")

    // Cache des coordonnées de chunks déjà scannés pour la session actuelle (évite les répétitions inutiles)
    private val scannedChunks = ConcurrentHashMap.newKeySet<Long>()

    /**
     * Analyse un [LevelChunk] dès sa mise à disposition côté client.
     */
    fun onChunkLoaded(chunk: LevelChunk) {
        val level = chunk.level ?: return

        // Court-circuit immédiat si le tracking des monuments est désactivé (0 CPU consommé)
        if (StructureOverlayManager.trackingMode == LegendaryMonumentsCompat.MonumentTrackingMode.DISABLED) return

        val chunkKey = chunk.pos.toLong()

        // Si ce chunk a déjà été analysé durant la session, on l'ignore immédiatement
        if (!scannedChunks.add(chunkKey)) return

        try {
            for (sectionIndex in 0 until chunk.sectionsCount) {
                val section = chunk.getSection(sectionIndex)
                if (section.hasOnlyAir()) continue

                // Test instantané de palette : si aucun bloc ciblé par le mode actif n'est dans la section, on passe à la suivante
                val mightHaveMonument = section.maybeHas { state ->
                    val blockId = BuiltInRegistries.BLOCK.getKey(state.block)
                    StructureOverlayManager.shouldScanBlock(blockId)
                }

                if (!mightHaveMonument) continue

                // Localisation précise des coordonnées du bloc rituel
                val sectionY = chunk.getSectionYFromSectionIndex(sectionIndex)
                val baseY = sectionY * 16

                for (x in 0..15) {
                    for (z in 0..15) {
                        for (y in 0..15) {
                            val state = section.getBlockState(x, y, z)
                            val blockId = BuiltInRegistries.BLOCK.getKey(state.block)

                            if (StructureOverlayManager.shouldScanBlock(blockId)) {
                                val blockPos = BlockPos(chunk.pos.minBlockX + x, baseY + y, chunk.pos.minBlockZ + z)
                                logger.info("Cobblemon JM: Bloc de monument légendaire détecté ! {} à {}", blockId, blockPos)
                                StructureOverlayManager.onMonumentDiscovered(level.dimension(), blockPos, blockId)
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            logger.debug("Cobblemon JM: Erreur lors de l'analyse du chunk {}: {}", chunk.pos, t.message)
        }
    }

    /**
     * Réinitialise le cache des chunks scannés (ex: changement de dimension ou déconnexion).
     */
    fun clearCache() {
        scannedChunks.clear()
        logger.debug("Cobblemon JM: Cache de chunks scannés réinitialisé.")
    }
}
