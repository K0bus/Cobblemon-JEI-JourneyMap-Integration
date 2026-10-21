package fr.k0bus.cobblemon_jei.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PokemonPokeSnackResolverTest {

    @Test
    fun `test how to create sealed class variants`() {
        val crafting = HowToCreate.Crafting(
            recipeDescription = "8 Lingots d'or + 1 Pomme",
            ingredients = emptyList()
        )
        assertEquals("8 Lingots d'or + 1 Pomme", crafting.recipeDescription)

        val nonCraftable = HowToCreate.NonCraftable(
            reason = "Loot rare uniquement"
        )
        assertEquals("Loot rare uniquement", nonCraftable.reason)
    }

    @Test
    fun `test where to find model properties`() {
        val where = WhereToFind(
            naturalBiomes = listOf("Jungle", "Montagne"),
            mobDrops = listOf(MobDropInfo("Draby", 5.0f)),
            specialLocations = listOf("Anciennes cités")
        )
        assertEquals(2, where.naturalBiomes.size)
        assertEquals("Jungle", where.naturalBiomes[0])
        assertEquals("Draby", where.mobDrops.first().speciesName)
        assertEquals(5.0f, where.mobDrops.first().percentage)
        assertEquals("Anciennes cités", where.specialLocations.first())
    }
}
