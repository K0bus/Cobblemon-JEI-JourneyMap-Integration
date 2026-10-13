package com.cobblemon.jei.recipe

import com.cobblemon.jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.world.item.ItemStack

/**
 * Représente une recette d'évolution d'un Pokémon dans JEI.
 * Associe le Pokémon initial, le Pokémon évolué, l'objet requis (ex: Pierre Foudre)
 * et l'ensemble des conditions (niveau, bonheur, météo, etc.).
 */
data class PokemonEvolutionRecipe(
    val sourceSpecies: Species,
    val sourceForm: FormData,
    val targetSpecies: Species,
    val targetForm: FormData,
    val requiredItems: List<ItemStack> = emptyList(),
    val evolutionVariant: String,
    val requirementsSummary: String,
    val additionalConditions: List<String> = emptyList()
) {
    val inputIngredient = PokemonSpeciesIngredient(sourceSpecies, sourceForm)
    val outputIngredient = PokemonSpeciesIngredient(targetSpecies, targetForm)
}
