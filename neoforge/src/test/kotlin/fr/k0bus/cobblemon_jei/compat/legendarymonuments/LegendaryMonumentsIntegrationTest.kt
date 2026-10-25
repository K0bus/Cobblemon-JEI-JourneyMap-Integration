package fr.k0bus.cobblemon_jei.compat.legendarymonuments

import fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader
import fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class LegendaryMonumentsIntegrationTest {

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
    fun testMonumentBlockRecognition() {
        // Blocs officiels de Legendary Monuments
        val zacianPedestal = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "zacian_pedestal")
        val eternatusCocoon = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "eternatus_cocoon")
        val meloettaJukebox = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "meloetta_jukebox")
        val lugiaPedestal = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "lugia_pedestal")
        val hoopaShrine = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "hoopa_shrine")

        assertTrue(LegendaryMonumentsCompat.isMonumentBlock(zacianPedestal))
        assertTrue(LegendaryMonumentsCompat.isMonumentBlock(eternatusCocoon))
        assertTrue(LegendaryMonumentsCompat.isMonumentBlock(meloettaJukebox))
        assertTrue(LegendaryMonumentsCompat.isMonumentBlock(lugiaPedestal))
        assertTrue(LegendaryMonumentsCompat.isMonumentBlock(hoopaShrine))

        // Blocs vanilla ou autres non concernés
        val stone = ResourceLocation.fromNamespaceAndPath("minecraft", "stone")
        val diamondBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "diamond_block")
        assertFalse(LegendaryMonumentsCompat.isMonumentBlock(stone))
        assertFalse(LegendaryMonumentsCompat.isMonumentBlock(diamondBlock))
    }

    @Test
    fun testSpeciesExtractionFromBlock() {
        assertEquals("zacian", LegendaryMonumentsCompat.extractSpeciesNameFromBlock(
            ResourceLocation.fromNamespaceAndPath("legendarymonuments", "zacian_pedestal")
        ))
        assertEquals("eternatus", LegendaryMonumentsCompat.extractSpeciesNameFromBlock(
            ResourceLocation.fromNamespaceAndPath("legendarymonuments", "eternatus_cocoon")
        ))
        assertEquals("meloetta", LegendaryMonumentsCompat.extractSpeciesNameFromBlock(
            ResourceLocation.fromNamespaceAndPath("legendarymonuments", "meloetta_jukebox")
        ))
        assertEquals("dialga", LegendaryMonumentsCompat.extractSpeciesNameFromBlock(
            ResourceLocation.fromNamespaceAndPath("legendarymonuments", "dialga_pedestal")
        ))
        assertEquals("hoopa", LegendaryMonumentsCompat.extractSpeciesNameFromBlock(
            ResourceLocation.fromNamespaceAndPath("legendarymonuments", "hoopa_shrine")
        ))
    }

    @Test
    fun testCoveredSpeciesCatalogue() {
        assertTrue(LegendaryMonumentsCompat.isCoveredSpecies("zacian"))
        assertTrue(LegendaryMonumentsCompat.isCoveredSpecies("Zacian"))
        assertTrue(LegendaryMonumentsCompat.isCoveredSpecies("dialga"))
        assertTrue(LegendaryMonumentsCompat.isCoveredSpecies("lugia"))
        assertTrue(LegendaryMonumentsCompat.isCoveredSpecies("ho-oh"))
        assertTrue(LegendaryMonumentsCompat.isCoveredSpecies("chien-pao"))
        assertTrue(LegendaryMonumentsCompat.isCoveredSpecies("Chien Pao"))

        assertFalse(LegendaryMonumentsCompat.isCoveredSpecies("pikachu"))
        assertFalse(LegendaryMonumentsCompat.isCoveredSpecies("charmander"))
        assertFalse(LegendaryMonumentsCompat.isCoveredSpecies("caterpie"))
    }

    @Test
    fun testMonumentDiscoveryLifecycle() {
        val dim = Level.OVERWORLD
        val pos = BlockPos(120, 68, -450)
        val blockId = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "zacian_pedestal")

        // Découverte du monument
        StructureOverlayManager.onMonumentDiscovered(dim, pos, blockId)

        // Consultation depuis JEI / Pokédex
        val discovered = StructureOverlayManager.getDiscoveredMonumentForSpecies("zacian")
        assertNotNull(discovered, "Le monument pour Zacian doit être trouvé dans StructureOverlayManager")
        assertEquals(pos, discovered!!.pos)
        assertEquals(pos, discovered.pedestalPos)
        assertEquals(dim, discovered.dimension)
        assertEquals("Sword Temple", discovered.metadata.displayName)
    }

    @Test
    fun testSpawnRecipesContainMonumentSpawns() {
        val recipes = PokemonSpawnDataLoader.loadSpawnRecipes()

        // Vérification de la présence des recettes de type monument
        val monumentRecipes = recipes.filter { it.isMonumentSpawn }
        assertTrue(monumentRecipes.isNotEmpty(), "Des recettes de monuments doivent être générées")

        val zacianRecipe = monumentRecipes.firstOrNull { it.species.name.equals("zacian", ignoreCase = true) }
        assertNotNull(zacianRecipe, "Zacian doit posséder une fiche d'apparition par monument")
        assertEquals("monument", zacianRecipe!!.bucket)
        assertFalse(zacianRecipe.isNaturalSpawn)
        assertEquals("legendarymonuments:zacian_pedestal", zacianRecipe.monumentPedestalBlock)
    }
}
