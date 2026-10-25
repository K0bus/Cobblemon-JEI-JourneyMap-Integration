package fr.k0bus.cobblemon_jei.compat.legendarymonuments

import fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat.MonumentTrackingMode
import fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class LegendaryMonumentsTrackingModeTest {

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
    fun testBlockCategorization() {
        val zacianPedestal = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "zacian_pedestal")
        val hoopaShrine = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "hoopa_shrine")
        val eternatusCocoon = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "eternatus_cocoon")
        val cobalionFootprint = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "cobalion_footprint")
        val virizionFootprint = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "virizion_footprint")
        val stone = ResourceLocation.fromNamespaceAndPath("minecraft", "stone")

        // Footprint checks
        assertTrue(LegendaryMonumentsCompat.isFootprintBlock(cobalionFootprint))
        assertTrue(LegendaryMonumentsCompat.isFootprintBlock(virizionFootprint))
        assertFalse(LegendaryMonumentsCompat.isFootprintBlock(zacianPedestal))
        assertFalse(LegendaryMonumentsCompat.isFootprintBlock(stone))

        // Major monument checks
        assertTrue(LegendaryMonumentsCompat.isMajorMonumentBlock(zacianPedestal))
        assertTrue(LegendaryMonumentsCompat.isMajorMonumentBlock(hoopaShrine))
        assertTrue(LegendaryMonumentsCompat.isMajorMonumentBlock(eternatusCocoon))
        assertFalse(LegendaryMonumentsCompat.isMajorMonumentBlock(cobalionFootprint))
        assertFalse(LegendaryMonumentsCompat.isMajorMonumentBlock(stone))
    }

    @Test
    fun testTrackingModeFiltering() {
        val pedestal = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "zacian_pedestal")
        val footprint = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "virizion_footprint")
        val stone = ResourceLocation.fromNamespaceAndPath("minecraft", "stone")

        // Mode DISABLED
        assertFalse(LegendaryMonumentsCompat.matchesTrackingMode(pedestal, MonumentTrackingMode.DISABLED))
        assertFalse(LegendaryMonumentsCompat.matchesTrackingMode(footprint, MonumentTrackingMode.DISABLED))
        assertFalse(LegendaryMonumentsCompat.matchesTrackingMode(stone, MonumentTrackingMode.DISABLED))

        // Mode MAJOR_ONLY
        assertTrue(LegendaryMonumentsCompat.matchesTrackingMode(pedestal, MonumentTrackingMode.MAJOR_ONLY))
        assertFalse(LegendaryMonumentsCompat.matchesTrackingMode(footprint, MonumentTrackingMode.MAJOR_ONLY))
        assertFalse(LegendaryMonumentsCompat.matchesTrackingMode(stone, MonumentTrackingMode.MAJOR_ONLY))

        // Mode FOOTPRINTS_ONLY
        assertFalse(LegendaryMonumentsCompat.matchesTrackingMode(pedestal, MonumentTrackingMode.FOOTPRINTS_ONLY))
        assertTrue(LegendaryMonumentsCompat.matchesTrackingMode(footprint, MonumentTrackingMode.FOOTPRINTS_ONLY))
        assertFalse(LegendaryMonumentsCompat.matchesTrackingMode(stone, MonumentTrackingMode.FOOTPRINTS_ONLY))

        // Mode ALL
        assertTrue(LegendaryMonumentsCompat.matchesTrackingMode(pedestal, MonumentTrackingMode.ALL))
        assertTrue(LegendaryMonumentsCompat.matchesTrackingMode(footprint, MonumentTrackingMode.ALL))
        assertFalse(LegendaryMonumentsCompat.matchesTrackingMode(stone, MonumentTrackingMode.ALL))
    }

    @Test
    fun testModeCycling() {
        var mode = MonumentTrackingMode.MAJOR_ONLY
        mode = mode.next()
        assertEquals(MonumentTrackingMode.FOOTPRINTS_ONLY, mode)
        mode = mode.next()
        assertEquals(MonumentTrackingMode.ALL, mode)
        mode = mode.next()
        assertEquals(MonumentTrackingMode.DISABLED, mode)
        mode = mode.next()
        assertEquals(MonumentTrackingMode.MAJOR_ONLY, mode)
    }

    @Test
    fun testStructureOverlayManagerShouldScan() {
        val pedestal = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "dialga_pedestal")
        val footprint = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "terrakion_footprint")

        StructureOverlayManager.setTrackingMode(MonumentTrackingMode.MAJOR_ONLY)
        assertTrue(StructureOverlayManager.shouldScanBlock(pedestal))
        assertFalse(StructureOverlayManager.shouldScanBlock(footprint))

        StructureOverlayManager.setTrackingMode(MonumentTrackingMode.FOOTPRINTS_ONLY)
        assertFalse(StructureOverlayManager.shouldScanBlock(pedestal))
        assertTrue(StructureOverlayManager.shouldScanBlock(footprint))

        StructureOverlayManager.setTrackingMode(MonumentTrackingMode.ALL)
        assertTrue(StructureOverlayManager.shouldScanBlock(pedestal))
        assertTrue(StructureOverlayManager.shouldScanBlock(footprint))

        StructureOverlayManager.setTrackingMode(MonumentTrackingMode.DISABLED)
        assertFalse(StructureOverlayManager.shouldScanBlock(pedestal))
        assertFalse(StructureOverlayManager.shouldScanBlock(footprint))

        // Reset to default
        StructureOverlayManager.setTrackingMode(MonumentTrackingMode.MAJOR_ONLY)
    }
}
