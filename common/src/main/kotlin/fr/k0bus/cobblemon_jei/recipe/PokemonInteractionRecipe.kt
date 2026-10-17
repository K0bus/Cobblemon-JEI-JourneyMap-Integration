package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Représente un objet obtenu lors d'une interaction manuelle avec un Pokémon (tonte, traite, brossage, etc.).
 */
data class PokemonInteractionDrop(
    val itemStack: ItemStack,
    val minCount: Int = 1,
    val maxCount: Int = 1
) {
    val quantityDisplay: String
        get() = if (minCount == maxCount) "$minCount" else "$minCount - $maxCount"
}

/**
 * Recette JEI pour une interaction directe avec un Pokémon dans le monde (Cobblemon).
 *
 * Rôles JEI :
 * - Le Pokémon est en INPUT.
 * - L'outil éventuel (cisailles, seau, brosse, bouteille) est en INPUT.
 * - Les ressources récoltées sont en OUTPUT.
 */
data class PokemonInteractionRecipe(
    val species: Species,
    val form: FormData,
    val actionTranslationKey: String,
    val requiredTools: List<ItemStack> = emptyList(),
    val outputs: List<PokemonInteractionDrop> = emptyList(),
    val conditions: List<String> = emptyList(),
    val cooldownTicks: Int = 0
) {
    val pokemonIngredient = PokemonSpeciesIngredient(species, form)

    val actionName: Component
        get() = Component.translatable(actionTranslationKey)

    val cooldownSeconds: Int
        get() = if (cooldownTicks > 0) cooldownTicks / 20 else 0
}
