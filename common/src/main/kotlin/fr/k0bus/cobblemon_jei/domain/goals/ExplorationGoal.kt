package fr.k0bus.cobblemon_jei.domain.goals

import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * Type d'objectif d'exploration ou de collection.
 */
enum class GoalType {
    CAPTURE,
    DISCOVER_FORM,
    EVOLVE,
    FIND_SHINY
}

/**
 * Statut actuel d'un objectif d'exploration.
 */
enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    PAUSED
}

/**
 * Cible Pokémon pour un objectif.
 */
data class PokemonTarget(
    val speciesId: ResourceLocation,
    val formId: ResourceLocation? = null,
    val shiny: Boolean = false,
    val customLabel: String? = null
) {
    /**
     * Vérifie si une espèce et une forme correspondent à cette cible.
     */
    fun matches(speciesRes: ResourceLocation, formRes: ResourceLocation? = null, isShiny: Boolean = false): Boolean {
        if (speciesRes != speciesId) return false
        if (formId != null && formRes != null && formRes != formId) return false
        if (shiny && !isShiny) return false
        return true
    }
}

/**
 * Représente un objectif d'exploration formulé par le joueur ou suggéré par le système.
 */
data class ExplorationGoal(
    val id: UUID = UUID.randomUUID(),
    val target: PokemonTarget,
    val goalType: GoalType = GoalType.CAPTURE,
    val priority: Int = 0,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val isActive: Boolean get() = status == GoalStatus.ACTIVE
    val isCompleted: Boolean get() = status == GoalStatus.COMPLETED
    val isPaused: Boolean get() = status == GoalStatus.PAUSED
}
