package fr.k0bus.cobblemon_jei.recipe

import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PokemonDropRecipeTest {

    companion object {
        @org.junit.jupiter.api.BeforeAll
        @JvmStatic
        fun initBootstrap() {
            try {
                net.minecraft.SharedConstants.tryDetectVersion()
                net.minecraft.server.Bootstrap.bootStrap()
            } catch (_: Throwable) {}
        }
    }

    @Test
    fun `test PokemonDropItem quantity and percentage formatting`() {
        val empty = try { ItemStack.EMPTY } catch (_: Throwable) { null }
        if (empty != null) {
            val drop1 = PokemonDropItem(
                itemStack = empty,
                percentage = 100.0f,
                minCount = 1,
                maxCount = 1
            )
            assertEquals("x1", drop1.quantityDisplay)
            assertEquals("100%", drop1.percentageDisplay)

            val drop2 = PokemonDropItem(
                itemStack = empty,
                percentage = 5.0f,
                minCount = 1,
                maxCount = 3
            )
            assertEquals("x1-3", drop2.quantityDisplay)
            assertEquals("5%", drop2.percentageDisplay)

            val drop3 = PokemonDropItem(
                itemStack = empty,
                percentage = 2.5f,
                minCount = 0,
                maxCount = 2
            )
            assertEquals("x0-2", drop3.quantityDisplay)
            assertEquals("2.5%", drop3.percentageDisplay)
        }
    }
}
