package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Type de récolte en pâturage.
 */
enum class PastureHarvestType(
    val badgeColor: Int,
    val badgeKey: String,
    val tooltipKey: String
) {
    /** Interaction manuelle avec le Pokémon en pâturage (Brossage, Traite, Tonte, etc.) */
    INTERACTION(0xFFFF9F43.toInt(), "gui.cobblemon_jei.badge.manual", "gui.cobblemon_jei.category.pokemon_pasture.tooltip_title.manual"),
    /** Production automatique / butin passif généré périodiquement par le Pokémon dans l'enclos */
    PASSIVE_DROP(0xFF2ED573.toInt(), "gui.cobblemon_jei.badge.passive", "gui.cobblemon_jei.category.pokemon_pasture.tooltip_title.passive");

    fun getBadgeComponent(): Component = Component.translatable(badgeKey)
    fun getTooltipTitleComponent(): Component = Component.translatable(tooltipKey)
}

/**
 * Représente un objet produit par un Pokémon en pâturage avec son taux d'obtention et sa quantité.
 */
data class PastureDropItem(
    val itemStack: ItemStack,
    val chancePercentage: Float = 100.0f,
    val minCount: Int = 1,
    val maxCount: Int = 1,
    val conditions: List<String> = emptyList()
) {
    /**
     * Résumé textuel formaté de la quantité (ex: "1" ou "1 - 3").
     */
    val quantityDisplay: String
        get() = if (minCount == maxCount) "$minCount" else "$minCount - $maxCount"

    /**
     * Résumé textuel du pourcentage (ex: "100%" ou "5.0%").
     */
    val chanceDisplay: String
        get() = if (chancePercentage >= 100f) "100%" else String.format(java.util.Locale.ROOT, "%.1f%%", chancePercentage)
}

/**
 * Recette JEI représentant la production de ressources d'un Pokémon au pâturage.
 *
 * Rôles JEI :
 * - Le Pokémon est en INPUT (accessible via 'U' / Use sur le Pokémon).
 * - L'outil éventuel est en INPUT (accessible via 'U' / Use sur la brosse, le seau, etc.).
 * - Les objets produits sont en OUTPUT (accessibles via 'R' / Recipe sur les ressources).
 */
data class PokemonPastureRecipe(
    val species: Species,
    val form: FormData,
    val harvestType: PastureHarvestType,
    val actionTranslationKey: String,
    val requiredTools: List<ItemStack> = emptyList(),
    val outputs: List<PastureDropItem> = emptyList(),
    val conditions: List<String> = emptyList(),
    val notes: String? = null
) {
    val pokemonIngredient = PokemonSpeciesIngredient(species, form)
    val actionName: Component
        get() = Component.translatable(actionTranslationKey)
}
