package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Gender
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
    val additionalConditions: List<String> = emptyList(),
    val requiredGender: Gender? = null,
    val requiredMove: String? = null,
    val requiredMoveType: String? = null,
    val useMoveCount: Int? = null,
    val stepsAmount: Int? = null,
    val criticalHits: Int? = null,
    val damageTaken: Int? = null,
    val recoilDamage: Int? = null,
    val defeatTarget: String? = null,
    val defeatAmount: Int? = null,
    val partyMember: String? = null,
    val statCompare: String? = null,
    val moonPhase: String? = null,
    val minLevel: Int? = null,
    val minFriendship: Int? = null
) {
    val inputIngredient = PokemonSpeciesIngredient(sourceSpecies, sourceForm)
    val outputIngredient = PokemonSpeciesIngredient(targetSpecies, targetForm)
}
