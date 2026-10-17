package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Recette JEI représentant un travail de pâturage (Cobbleworkers).
 *
 * Rôles JEI :
 * - Les Pokémon éligibles (déterminés selon types, espèces, talents, capacités) sont en INPUT.
 * - Le bloc cible ou infrastructure dans l'enclos (chaudron, abricorns, cultures, ruche, fourneau...) est en INPUT.
 * - Les objets produits ou récoltés sont en OUTPUT.
 */
data class PokemonPastureRecipe(
    val jobId: String,
    val jobTitleKey: String,
    val eligiblePokemon: List<PokemonSpeciesIngredient>,
    val targetInputs: List<ItemStack>,
    val outputs: List<List<ItemStack>>,
    val cooldownSeconds: Int = 0,
    val requirementsDescription: String,
    val actionDescriptionKey: String
) {
    val jobTitle: Component
        get() = Component.translatable(jobTitleKey)

    val actionDescription: Component
        get() = Component.translatable(actionDescriptionKey)

    val isContinuous: Boolean
        get() = cooldownSeconds <= 0
}
