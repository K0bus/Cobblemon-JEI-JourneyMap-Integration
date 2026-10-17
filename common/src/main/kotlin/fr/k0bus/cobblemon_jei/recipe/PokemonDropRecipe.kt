package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.world.item.ItemStack

/**
 * Données d'un objet spécifique lâché à la défaite d'un Pokémon.
 */
data class PokemonDropItem(
    val itemStack: ItemStack,
    val percentage: Float,
    val minCount: Int,
    val maxCount: Int,
    val isEvolutionItem: Boolean = false
) {
    val quantityDisplay: String
        get() = if (minCount == maxCount) {
            "x$minCount"
        } else {
            "x$minCount-$maxCount"
        }

    val percentageDisplay: String
        get() = if (percentage >= 100f) {
            "100%"
        } else if (percentage % 1.0f == 0.0f) {
            "${percentage.toInt()}%"
        } else {
            String.format(java.util.Locale.US, "%.1f%%", percentage)
        }
}

/**
 * Recette JEI représentant la table de butins d'un Pokémon lorsqu'il est vaincu/meurt.
 */
data class PokemonDropRecipe(
    val species: Species,
    val form: FormData,
    val drops: List<PokemonDropItem>,
    val minRolls: Int = 1,
    val maxRolls: Int = 1
) {
    val pokemonIngredient: PokemonSpeciesIngredient = PokemonSpeciesIngredient(species, form)

    val formName: String? = form.name.takeIf { it.isNotBlank() && !it.equals("Normal", ignoreCase = true) }

    val rollsDisplay: String
        get() = if (minRolls == maxRolls) {
            if (minRolls <= 1) "1" else "$minRolls"
        } else {
            "$minRolls - $maxRolls"
        }
}
