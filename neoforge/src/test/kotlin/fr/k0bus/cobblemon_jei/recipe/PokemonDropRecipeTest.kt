package fr.k0bus.cobblemon_jei.recipe

import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PokemonDropRecipeTest {

    @Test
    fun `test PokemonDropItem quantity and percentage formatting`() {
        val drop1 = PokemonDropItem(
            itemStack = ItemStack.EMPTY,
            percentage = 100.0f,
            minCount = 1,
            maxCount = 1
        )
        assertEquals("x1", drop1.quantityDisplay)
        assertEquals("100%", drop1.percentageDisplay)

        val drop2 = PokemonDropItem(
            itemStack = ItemStack.EMPTY,
            percentage = 5.0f,
            minCount = 1,
            maxCount = 3
        )
        assertEquals("x1-3", drop2.quantityDisplay)
        assertEquals("5%", drop2.percentageDisplay)

        val drop3 = PokemonDropItem(
            itemStack = ItemStack.EMPTY,
            percentage = 2.5f,
            minCount = 0,
            maxCount = 2
        )
        assertEquals("x0-2", drop3.quantityDisplay)
        assertEquals("2.5%", drop3.percentageDisplay)
    }
}
