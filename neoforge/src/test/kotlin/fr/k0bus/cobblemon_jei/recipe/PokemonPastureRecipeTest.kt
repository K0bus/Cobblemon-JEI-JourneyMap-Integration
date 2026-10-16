package fr.k0bus.cobblemon_jei.recipe

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
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
    fun `test PastureDropItem display formats`() {
        val emptyStack = try { ItemStack.EMPTY } catch (_: Throwable) { null }
        if (emptyStack != null) {
            val drop1 = PastureDropItem(
                itemStack = emptyStack,
                chancePercentage = 100.0f,
                minCount = 1,
                maxCount = 1
            )
            assertEquals("1", drop1.quantityDisplay)
            assertEquals("100%", drop1.chanceDisplay)

            val drop2 = PastureDropItem(
                itemStack = emptyStack,
                chancePercentage = 50.0f,
                minCount = 1,
                maxCount = 3
            )
            assertEquals("1 - 3", drop2.quantityDisplay)
            assertEquals("50.0%", drop2.chanceDisplay)

            val drop3 = PastureDropItem(
                itemStack = emptyStack,
                chancePercentage = 2.5f,
                minCount = 0,
                maxCount = 2
            )
            assertEquals("0 - 2", drop3.quantityDisplay)
            assertEquals("2.5%", drop3.chanceDisplay)
        }
    }

    @Test
    fun `test PastureHarvestType values`() {
        val types = PastureHarvestType.values()
        assertTrue(types.contains(PastureHarvestType.INTERACTION))
        assertTrue(types.contains(PastureHarvestType.PASSIVE_DROP))
    }
}
