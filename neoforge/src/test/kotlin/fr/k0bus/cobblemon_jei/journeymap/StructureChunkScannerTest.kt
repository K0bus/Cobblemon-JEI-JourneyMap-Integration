package fr.k0bus.cobblemon_jei.journeymap

import fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat
import fr.k0bus.cobblemon_jei.journeymap.structure.StructureChunkScanner
import fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class StructureChunkScannerTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            try {
                net.minecraft.SharedConstants.tryDetectVersion()
                net.minecraft.server.Bootstrap.bootStrap()
            } catch (_: Throwable) {}
        }
    }

    @Test
    fun testChunkScannerCacheReset() {
        StructureChunkScanner.clearCache()
        // Le cache doit être réinitialisé sans exception
        assertDoesNotThrow {
            StructureChunkScanner.clearCache()
        }
    }

    @Test
    fun testMonumentBlockToOverlayAndWaypointWorkflow() {
        val dim = Level.OVERWORLD
        val pos = BlockPos(500, 72, -800)
        val blockId = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "dialga_pedestal")

        // Simulation de la découverte directe
        StructureOverlayManager.onMonumentDiscovered(dim, pos, blockId)

        val discovered = StructureOverlayManager.getDiscoveredMonumentForSpecies("dialga")
        assertNotNull(discovered, "Le monument de Dialga doit être enregistré")
        assertEquals(pos, discovered!!.pos)
        assertEquals("Spear Pillar", discovered.metadata.displayName)
        assertEquals("dialga", discovered.metadata.targetPokemonName)

        val all = StructureOverlayManager.getAllDiscoveredStructures()
        assertTrue(all.any { it.metadata.targetPokemonName == "dialga" })
    }
}
