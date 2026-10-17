package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.compat.cobbleworkers.CobbleworkersCompat
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PokemonPastureRecipeTest {

    companion object {
        @org.junit.jupiter.api.BeforeAll
        @JvmStatic
        fun setup() {
            try {
                net.minecraft.SharedConstants.tryDetectVersion()
                net.minecraft.server.Bootstrap.bootStrap()
            } catch (_: Throwable) {}
        }
    }

    @Test
    fun `test PokemonInteractionDrop quantity formatting`() {
        val emptyStack = try { ItemStack.EMPTY } catch (_: Throwable) { null }
        if (emptyStack != null) {
            val dropSingle = PokemonInteractionDrop(
                itemStack = emptyStack,
                minCount = 1,
                maxCount = 1
            )
            assertEquals("1", dropSingle.quantityDisplay)

            val dropRange = PokemonInteractionDrop(
                itemStack = emptyStack,
                minCount = 1,
                maxCount = 3
            )
            assertEquals("1 - 3", dropRange.quantityDisplay)
        }
    }

    @Test
    fun `test PokemonPastureRecipe properties`() {
        val recipeContinuous = PokemonPastureRecipe(
            jobId = "cobbleworkers:apricorn_harvester",
            jobTitleKey = "gui.cobblemon_jei.job.apricorn_harvester",
            eligiblePokemon = emptyList(),
            targetInputs = emptyList(),
            outputs = emptyList(),
            cooldownSeconds = 0,
            requirementsDescription = "Type : Insecte",
            actionDescriptionKey = "gui.cobblemon_jei.job.apricorn_harvester.desc"
        )
        assertTrue(recipeContinuous.isContinuous)
        assertEquals(0, recipeContinuous.cooldownSeconds)
        assertEquals("Type : Insecte", recipeContinuous.requirementsDescription)

        val recipeCooldown = PokemonPastureRecipe(
            jobId = "cobbleworkers:lava_generator",
            jobTitleKey = "gui.cobblemon_jei.job.lava_generator",
            eligiblePokemon = emptyList(),
            targetInputs = emptyList(),
            outputs = emptyList(),
            cooldownSeconds = 90,
            requirementsDescription = "Type : Feu",
            actionDescriptionKey = "gui.cobblemon_jei.job.lava_generator.desc"
        )
        assertFalse(recipeCooldown.isContinuous)
        assertEquals(90, recipeCooldown.cooldownSeconds)

        val recipeCyclic = PokemonPastureRecipe(
            jobId = "cobbleworkers:pickup_looter",
            jobTitleKey = "gui.cobblemon_jei.job.pickup_looter",
            eligiblePokemon = emptyList(),
            targetInputs = emptyList(),
            outputs = listOf(
                listOf(ItemStack.EMPTY),
                listOf(ItemStack.EMPTY, ItemStack.EMPTY)
            ),
            cooldownSeconds = 210,
            requirementsDescription = "Talent : Ramassage",
            actionDescriptionKey = "gui.cobblemon_jei.job.pickup_looter.desc"
        )
        assertEquals(2, recipeCyclic.outputs.size)
        assertEquals(1, recipeCyclic.outputs[0].size)
        assertEquals(2, recipeCyclic.outputs[1].size)
    }

    @Test
    fun `test CobbleworkersCompat isLoaded does not throw`() {
        // En environnement de test sans mod container complet, isLoaded doit renvoyer true (car la classe est sur le classpath) sans lever d'exception
        val loaded = CobbleworkersCompat.isLoaded
        assertTrue(loaded)
    }
}
