package com.cobblemon.jei.ingredient

import mezz.jei.api.ingredients.IIngredientHelper
import mezz.jei.api.ingredients.IIngredientType
import mezz.jei.api.ingredients.subtypes.UidContext
import net.minecraft.resources.ResourceLocation

/**
 * Implémentation de [IIngredientHelper] pour les espèces et formes de Pokémon Cobblemon.
 * Gère l'indexation, la recherche par nom/dex/types et les identifiants uniques.
 */
class PokemonIngredientHelper : IIngredientHelper<PokemonSpeciesIngredient> {

    override fun getIngredientType(): IIngredientType<PokemonSpeciesIngredient> {
        return PokemonSpeciesIngredient.TYPE
    }

    override fun getDisplayName(ingredient: PokemonSpeciesIngredient): String {
        val baseName = ingredient.species.translatedName.string
        return if (ingredient.form.name.isNotBlank() && ingredient.form.name != "normal") {
            val capitalizedForm = ingredient.form.name.replaceFirstChar { it.uppercase() }
            "$baseName ($capitalizedForm)"
        } else {
            baseName
        }
    }

    override fun getUniqueId(ingredient: PokemonSpeciesIngredient, context: UidContext): String {
        val res = ingredient.id
        return "${res}#${ingredient.form.name}"
    }

    override fun getWildcardId(ingredient: PokemonSpeciesIngredient): String {
        return ingredient.id.toString()
    }

    override fun getResourceLocation(ingredient: PokemonSpeciesIngredient): ResourceLocation {
        return ingredient.id
    }

    override fun getDisplayModId(ingredient: PokemonSpeciesIngredient): String {
        return "Cobblemon"
    }

    override fun copyIngredient(ingredient: PokemonSpeciesIngredient): PokemonSpeciesIngredient {
        return ingredient.copy()
    }

    override fun isValidIngredient(ingredient: PokemonSpeciesIngredient): Boolean {
        return true
    }

    override fun getErrorInfo(ingredient: PokemonSpeciesIngredient?): String {
        return if (ingredient != null) {
            "PokemonSpeciesIngredient[species=${ingredient.species.name}, form=${ingredient.form.name}]"
        } else {
            "PokemonSpeciesIngredient[null]"
        }
    }

    override fun getColors(ingredient: PokemonSpeciesIngredient): Iterable<Int> {
        val colors = mutableListOf<Int>()
        try {
            colors.add(ingredient.form.primaryType.hue)
            ingredient.form.secondaryType?.let { sec ->
                colors.add(sec.hue)
            }
        } catch (_: Throwable) {}
        return colors
    }
}
