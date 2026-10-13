package com.cobblemon.jei.recipe

import com.cobblemon.jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species

/**
 * Modèle représentant une recette d'apparition (spawn) de Pokémon dans JEI.
 * Agrège les critères environnementaux (biomes, météo, heure, niveau, rareté).
 */
data class PokemonSpawnRecipe(
    val species: Species,
    val form: FormData = species.standardForm,
    val isNaturalSpawn: Boolean = true,
    val bucket: String = "common", // common, uncommon, rare, ultra-rare
    val weight: Float = 0f,
    val levelMin: Int = 1,
    val levelMax: Int = 100,
    val context: String = "grounded", // grounded, submerged, etc.
    val biomes: Set<String> = emptySet(),
    val antiBiomes: Set<String> = emptySet(),
    val dimensions: Set<String> = emptySet(),
    val timeRange: String = "any", // day, night, dawn, dusk, any
    val weather: String = "any", // clear, rain, thundering, any
    val canSeeSky: Boolean? = null,
    val minSkyLight: Int? = null,
    val maxSkyLight: Int? = null,
    val structures: Set<String> = emptySet(),
    val notes: String = ""
) {
    /**
     * Ingrédient lié à cette recette.
     */
    val pokemonIngredient: PokemonSpeciesIngredient = PokemonSpeciesIngredient(species, form)
}
