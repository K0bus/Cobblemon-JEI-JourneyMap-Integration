package fr.k0bus.cobblemon_jei.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class PokemonFossilDataLoaderTest {

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
    fun `test fossil recipes loading and mapping`() {
        PokemonFossilDataLoader.ensureLoaded()
        val allRecipes = PokemonFossilDataLoader.getAllFossilRecipes()

        assertTrue(allRecipes.size >= 15, "Au moins 15 recettes de fossiles natives doivent être chargées")

        val omanyte = allRecipes.find { it.resultSpeciesName == "omanyte" }
        assertNotNull(omanyte, "Omanyte doit avoir une recette de résurrection")
        assertEquals(listOf("cobblemon:helix_fossil"), omanyte!!.requiredFossilItemIds)
        assertEquals("Fossile Nautile", omanyte.fossilDisplayName)
        assertTrue(omanyte.associatedStructures.contains("sandy_den"), "Le Fossile Nautile doit être associé à la structure sandy_den")
        assertTrue(omanyte.associatedStructureDisplayNames.contains("Repaire Sableux Préhistorique"))

        val dracozolt = allRecipes.find { it.resultSpeciesName == "dracozolt" }
        assertNotNull(dracozolt, "Galvagon (Dracozolt) doit être présent")
        assertEquals(2, dracozolt!!.requiredFossilItemIds.size, "Galvagon nécessite 2 fossiles combinés")
        assertTrue(dracozolt.requiredFossilItemIds.contains("cobblemon:fossilized_bird"))
        assertTrue(dracozolt.requiredFossilItemIds.contains("cobblemon:fossilized_drake"))
    }

    @Test
    fun `test fossil lineage detection`() {
        PokemonFossilDataLoader.ensureLoaded()

        val aerodactyl = PokemonEvolutionDataLoader.findSpeciesByName("aerodactyl")
        if (aerodactyl != null) {
            assertTrue(PokemonFossilDataLoader.isFossilLineage(aerodactyl))
            val origin = PokemonFossilDataLoader.getFossilLineageOrigin(aerodactyl)
            assertNotNull(origin)
            assertEquals("aerodactyl", origin!!.first.name.lowercase())
            assertEquals("old_amber_fossil", origin.second.requiredFossilItemIds.first().substringAfter(":"))
        }

        val omastar = PokemonEvolutionDataLoader.findSpeciesByName("omastar")
        if (omastar != null) {
            assertTrue(PokemonFossilDataLoader.isFossilLineage(omastar))
            val origin = PokemonFossilDataLoader.getFossilLineageOrigin(omastar)
            assertNotNull(origin)
            assertEquals("omanyte", origin!!.first.name.lowercase())
            assertEquals("helix_fossil", origin.second.requiredFossilItemIds.first().substringAfter(":"))
        }

        val pikachu = PokemonEvolutionDataLoader.findSpeciesByName("pikachu")
        if (pikachu != null) {
            assertFalse(PokemonFossilDataLoader.isFossilLineage(pikachu), "Pikachu ne doit pas être considéré comme un fossile")
        }
    }
}
