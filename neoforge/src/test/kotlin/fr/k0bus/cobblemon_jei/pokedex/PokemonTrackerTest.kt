package fr.k0bus.cobblemon_jei.pokedex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PokemonTrackerTest {

    @Test
    fun `test PokedexTrackerStats completion percentage`() {
        val stats = PokedexTrackerStats(
            totalSpecies = 1000,
            caughtCount = 250,
            encounteredCount = 100,
            missingCount = 650,
            readyEvolutionsCount = 5,
            pcEvolutionsCount = 12
        )

        assertEquals(25.0f, stats.completionPercent, 0.001f)
        assertEquals(250, stats.caughtCount)
        assertEquals(100, stats.encounteredCount)
        assertEquals(650, stats.missingCount)
        assertEquals(5, stats.readyEvolutionsCount)
    }

    @Test
    fun `test EvolutionFeasibility ordering`() {
        // L'ordre ordinal doit garantir que READY_TO_EVOLVE passe en premier
        val feasibilities = listOf(
            EvolutionFeasibility.MISSING_BASE_POKEMON,
            EvolutionFeasibility.READY_TO_EVOLVE,
            EvolutionFeasibility.CAN_EVOLVE_WITH_PC
        ).sorted()

        assertEquals(EvolutionFeasibility.READY_TO_EVOLVE, feasibilities[0])
        assertEquals(EvolutionFeasibility.CAN_EVOLVE_WITH_PC, feasibilities[1])
        assertEquals(EvolutionFeasibility.MISSING_BASE_POKEMON, feasibilities[2])
    }

    @Test
    fun `test PokedexStatus states`() {
        assertTrue(PokedexStatus.CAUGHT.ordinal < PokedexStatus.MISSING.ordinal)
    }
}
