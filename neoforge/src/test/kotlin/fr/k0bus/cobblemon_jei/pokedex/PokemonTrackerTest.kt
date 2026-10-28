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

    private fun createDummySpecies(name: String, dex: Int): com.cobblemon.mod.common.pokemon.Species {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val sp = unsafe.allocateInstance(com.cobblemon.mod.common.pokemon.Species::class.java) as com.cobblemon.mod.common.pokemon.Species
        sp.name = name
        sp.nationalPokedexNumber = dex
        return sp
    }

    @Test
    fun `test BreedingOriginGoal properties`() {
        val mockBase = createDummySpecies("pichu", 172)
        val mockParent1 = createDummySpecies("pikachu", 25)
        val mockParent2 = createDummySpecies("raichu", 26)

        val goal = BreedingOriginGoal(
            baseSpecies = mockBase,
            targetStatus = PokedexStatus.MISSING,
            isBaby = true,
            candidateParents = listOf(mockParent1, mockParent2),
            ownedParents = emptyList(),
            caughtParents = listOf(mockParent1),
            requiredIncense = null
        )

        assertEquals(172, goal.nationalDexNumber)
        assertTrue(goal.isBaby)
        assertTrue(goal.isMultipleOrigins)
        assertEquals(false, goal.isReadyToBreed)
        assertTrue(goal.caughtParents.isNotEmpty())
    }
}
