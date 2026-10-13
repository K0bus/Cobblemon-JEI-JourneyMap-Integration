package com.cobblemon.jei.journeymap

import com.cobblemon.jei.journeymap.structure.DynamicStructureRegistryService
import com.cobblemon.jei.journeymap.structure.DynamicStructureRegistryService.StructureType
import com.cobblemon.jei.journeymap.structure.LegendaryStructureHandler
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DynamicStructureRegistryServiceTest {

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
    fun testFossilStructureClassification() {
        // Structures fossiles natives Cobblemon
        val oakTree = ResourceLocation.fromNamespaceAndPath("cobblemon", "fossils/prehistoric_oak_tree")
        val metaOak = DynamicStructureRegistryService.getMetadata(oakTree)
        assertEquals(StructureType.FOSSIL, metaOak.type)
        assertTrue(metaOak.associatedFossils.contains("old_amber_fossil"))
        assertEquals("Prehistoric Oak Tree", metaOak.displayName)

        val birchTree = ResourceLocation.fromNamespaceAndPath("cobblemon", "fossils/prehistoric_birch_tree")
        val metaBirch = DynamicStructureRegistryService.getMetadata(birchTree)
        assertEquals(StructureType.FOSSIL, metaBirch.type)
        assertTrue(metaBirch.associatedFossils.contains("plume_fossil"))

        val sandyDen = ResourceLocation.fromNamespaceAndPath("cobblemon", "fossils/prehistoric_sandy_den")
        val metaSandy = DynamicStructureRegistryService.getMetadata(sandyDen)
        assertEquals(StructureType.FOSSIL, metaSandy.type)
        assertTrue(metaSandy.associatedFossils.contains("helix_fossil"))

        // Structure issue d'un datapack externe sans hardcoding
        val customDigSite = ResourceLocation.fromNamespaceAndPath("custom_pack", "ancient_fossil_dig_site")
        val metaCustom = DynamicStructureRegistryService.getMetadata(customDigSite)
        assertEquals(StructureType.FOSSIL, metaCustom.type)
    }

    @Test
    fun testLegendaryMonumentsClassification() {
        // Structures de Legendary Monuments
        val crownShrine = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "crown_shrine")
        val metaCrown = DynamicStructureRegistryService.getMetadata(crownShrine)
        assertEquals(StructureType.LEGENDARY, metaCrown.type)
        assertEquals("Crown Shrine", metaCrown.displayName)

        val lugiaTemple = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "lugia_temple")
        val metaLugia = DynamicStructureRegistryService.getMetadata(lugiaTemple)
        assertEquals(StructureType.LEGENDARY, metaLugia.type)

        val firescourge = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "firescourge_shrine")
        val metaFire = DynamicStructureRegistryService.getMetadata(firescourge)
        assertEquals(StructureType.LEGENDARY, metaFire.type)

        // Structure externe d'un mod/datapack avec mot-clé sanctuaire
        val customZapdos = ResourceLocation.fromNamespaceAndPath("kanto_shrines", "zapdos_mountain_altar")
        val metaZapdos = DynamicStructureRegistryService.getMetadata(customZapdos)
        assertEquals(StructureType.LEGENDARY, metaZapdos.type)
    }

    @Test
    fun testPedestalBlockRecognition() {
        assertTrue(LegendaryStructureHandler.isPedestalBlock("legendarymonuments", "dialga_pedestal"))
        assertTrue(LegendaryStructureHandler.isPedestalBlock("legendarymonuments", "firescourge_shrine"))
        assertTrue(LegendaryStructureHandler.isPedestalBlock("legendarymonuments", "eternatus_cocoon"))
        assertTrue(LegendaryStructureHandler.isPedestalBlock("legendarymonuments", "meloetta_jukebox"))
        assertTrue(LegendaryStructureHandler.isPedestalBlock("custom_mod", "kanto_pedestal"))
        assertTrue(LegendaryStructureHandler.isPedestalBlock("custom_mod", "hooh_shrine"))

        assertFalse(LegendaryStructureHandler.isPedestalBlock("minecraft", "stone"))
        assertFalse(LegendaryStructureHandler.isPedestalBlock("minecraft", "dirt"))
    }
}
