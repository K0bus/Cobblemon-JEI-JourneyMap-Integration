package fr.k0bus.cobblemon_jei.domain.events

import fr.k0bus.cobblemon_jei.domain.goals.ExplorationGoal
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hiérarchie des événements d'exploration.
 */
sealed interface ExplorationEvent

data class ExplorationGoalAdded(val goal: ExplorationGoal) : ExplorationEvent
data class ExplorationGoalUpdated(val goal: ExplorationGoal) : ExplorationEvent
data class ExplorationGoalCompleted(val goal: ExplorationGoal) : ExplorationEvent
data class ExplorationGoalRemoved(val goalId: UUID) : ExplorationEvent
data class TargetSelectionChanged(val activeTargets: List<PokemonTarget>) : ExplorationEvent

/**
 * Bus d'événements léger et thread-safe pour découpler le domaine, l'application et les interfaces.
 */
object ExplorationEventBus {

    private val listeners = CopyOnWriteArrayList<(ExplorationEvent) -> Unit>()

    /**
     * Enregistre un écouteur d'événements.
     */
    fun subscribe(listener: (ExplorationEvent) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /**
     * Publie un événement à tous les écouteurs enregistrés.
     */
    fun post(event: ExplorationEvent) {
        for (listener in listeners) {
            try {
                listener(event)
            } catch (t: Throwable) {
                // Ignore listener exceptions to avoid cascading failures
            }
        }
    }

    /**
     * Réinitialise les écouteurs (utile pour les tests unitaires).
     */
    fun clearListeners() {
        listeners.clear()
    }
}
