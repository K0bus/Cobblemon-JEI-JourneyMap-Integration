package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.stats.Stats
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
    val foodItems: List<ItemStack> = emptyList(),
    val specificFoodNames: List<String> = emptyList(),
    val outputs: List<PastureDropItem> = emptyList(),
    val conditions: List<String> = emptyList(),
    val cooldownTicks: Int = 0,
    val notes: String? = null
) {
    val pokemonIngredient = PokemonSpeciesIngredient(species, form)
    val actionName: Component
        get() = Component.translatable(actionTranslationKey)

    /**
     * Intervalle estimé en minutes selon les paramètres du serveur / de Cobblemon.
     * Pour les drops passifs : calculé via le métabolisme officiel de l'espèce indexé sur la vitesse et le BST.
     * Pour les interactions manuelles : calculé via le cooldown en ticks divisé par 1200 (20 ticks * 60 s).
     */
    val estimatedMinutes: Double
        get() {
            if (harvestType == PastureHarvestType.INTERACTION) {
                return if (cooldownTicks > 0) cooldownTicks / 1200.0 else 0.0
            }

            // Calcul du métabolisme officiel Cobblemon
            val speed = species.baseStats.getOrDefault(Stats.SPEED, 0)
            val bst = species.baseStats.values.sum().coerceAtLeast(1)
            val minM = 4.0
            val maxM = 20.0
            val seconds = ((maxM - (speed.toDouble() / bst.toDouble()) * maxM * minM) * 60.0).toInt().coerceAtLeast(60)

            // Facteur multiplicateur si le serveur modifie pastureBlockUpdateTicks (par défaut 40)
            val serverMultiplier = try {
                val updateTicks = Cobblemon.config.pastureBlockUpdateTicks
                if (updateTicks > 0) updateTicks.toDouble() / 40.0 else 1.0
            } catch (_: Throwable) {
                1.0
            }

            val finalMinutes = (seconds / 60.0) * serverMultiplier
            return (Math.round(finalMinutes * 10.0) / 10.0).coerceAtLeast(1.0)
        }

    val displayIntervalMinutes: String
        get() {
            val mins = estimatedMinutes
            return if (mins % 1.0 == 0.0) "${mins.toInt()}" else String.format(java.util.Locale.ROOT, "%.1f", mins)
        }
}
