package fr.k0bus.cobblemon_jei.application.goals

import fr.k0bus.cobblemon_jei.domain.events.*
import fr.k0bus.cobblemon_jei.domain.goals.ExplorationGoal
import fr.k0bus.cobblemon_jei.domain.goals.GoalStatus
import fr.k0bus.cobblemon_jei.domain.goals.GoalType
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import fr.k0bus.cobblemon_jei.domain.session.SessionLifecycleListener
import fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager
import fr.k0bus.cobblemon_jei.infrastructure.persistence.JsonGoalStorage
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Service applicatif gérant le cycle de vie, la priorité et la cohérence des objectifs d'exploration.
 */
object GoalService : SessionLifecycleListener {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/GoalService")
    private val goals = CopyOnWriteArrayList<ExplorationGoal>()
    private var currentWorldId: String = "default"
    var storage: JsonGoalStorage = JsonGoalStorage()

    private var isInitialized = false

    init {
        WorldSessionManager.addListener(this)
        initForCurrentSession()
    }

    private fun initForCurrentSession() {
        val sessionId = WorldSessionManager.activeSessionId
        if (sessionId != null) {
            currentWorldId = sessionId
        }
        loadFromStorage()
        isInitialized = true
    }

    @Synchronized
    fun loadFromStorage() {
        goals.clear()
        val loaded = storage.loadGoals(currentWorldId)
        goals.addAll(loaded)
        logger.info("Cobblemon JEI: ${goals.size} objectifs chargés pour le monde '$currentWorldId'.")
        notifyTargetsChanged()
    }

    @Synchronized
    fun saveToStorage() {
        try {
            storage.saveGoals(currentWorldId, goals.toList())
        } catch (e: Throwable) {
            logger.error("Impossible de sauvegarder les objectifs: {}", e.message)
        }
    }

    // --- SessionLifecycleListener ---

    override fun onSessionStarted(sessionId: String, initialDimension: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>?) {
        currentWorldId = sessionId
        loadFromStorage()
    }

    override fun onSessionEnded(sessionId: String) {
        saveToStorage()
        goals.clear()
        currentWorldId = "default"
        notifyTargetsChanged()
    }

    // --- Opérations Métier ---

    /**
     * Retourne une copie de tous les objectifs enregistrés.
     */
    fun getAllGoals(): List<ExplorationGoal> = goals.toList()

    /**
     * Retourne uniquement les objectifs en cours (`ACTIVE`).
     */
    fun getActiveGoals(): List<ExplorationGoal> = goals.filter { it.isActive }

    /**
     * Retourne les cibles uniques de tous les objectifs actifs.
     */
    fun getActiveTargets(): List<PokemonTarget> = getActiveGoals().map { it.target }.distinct()

    /**
     * Vérifie si un Pokémon correspond à au moins un objectif actif.
     */
    fun isTargetActive(speciesId: ResourceLocation, formId: ResourceLocation? = null, shiny: Boolean = false): Boolean {
        return getActiveGoals().any { it.target.matches(speciesId, formId, shiny) }
    }

    /**
     * Trouve le premier objectif actif correspondant à une cible.
     */
    fun findActiveGoal(speciesId: ResourceLocation, formId: ResourceLocation? = null, shiny: Boolean = false): ExplorationGoal? {
        return getActiveGoals().firstOrNull { it.target.matches(speciesId, formId, shiny) }
    }

    /**
     * Ajoute un nouvel objectif ou réactive un objectif existant.
     */
    @Synchronized
    fun addGoal(
        target: PokemonTarget,
        type: GoalType = GoalType.CAPTURE,
        priority: Int = 0
    ): ExplorationGoal {
        val existingIndex = goals.indexOfFirst {
            it.target.speciesId == target.speciesId &&
            it.target.formId == target.formId &&
            it.target.shiny == target.shiny &&
            it.goalType == type
        }

        val goalToReturn: ExplorationGoal

        if (existingIndex >= 0) {
            val existing = goals[existingIndex]
            if (existing.status != GoalStatus.ACTIVE) {
                val updated = existing.copy(status = GoalStatus.ACTIVE, priority = priority)
                goals[existingIndex] = updated
                ExplorationEventBus.post(ExplorationGoalUpdated(updated))
                goalToReturn = updated
            } else {
                goalToReturn = existing
            }
        } else {
            val newGoal = ExplorationGoal(
                target = target,
                goalType = type,
                priority = priority,
                status = GoalStatus.ACTIVE
            )
            goals.add(newGoal)
            ExplorationEventBus.post(ExplorationGoalAdded(newGoal))
            goalToReturn = newGoal
        }

        saveToStorage()
        notifyTargetsChanged()
        return goalToReturn
    }

    /**
     * Alterne l'objectif : si déjà actif, le supprime ou le met en pause ; sinon l'ajoute.
     * Retourne true si l'objectif est désormais actif, false sinon.
     */
    @Synchronized
    fun toggleGoal(target: PokemonTarget, type: GoalType = GoalType.CAPTURE): Boolean {
        val existing = findActiveGoal(target.speciesId, target.formId, target.shiny)
        return if (existing != null) {
            removeGoal(existing.id)
            false
        } else {
            addGoal(target, type)
            true
        }
    }

    /**
     * Supprime définitivement un objectif par son identifiant.
     */
    @Synchronized
    fun removeGoal(id: UUID): Boolean {
        val removed = goals.removeIf { it.id == id }
        if (removed) {
            ExplorationEventBus.post(ExplorationGoalRemoved(id))
            saveToStorage()
            notifyTargetsChanged()
        }
        return removed
    }

    /**
     * Met à jour le statut d'un objectif.
     */
    @Synchronized
    fun updateStatus(id: UUID, newStatus: GoalStatus): Boolean {
        val index = goals.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = goals[index]
            val completedTime = if (newStatus == GoalStatus.COMPLETED) System.currentTimeMillis() else null
            val updated = old.copy(status = newStatus, completedAt = completedTime)
            goals[index] = updated

            if (newStatus == GoalStatus.COMPLETED) {
                ExplorationEventBus.post(ExplorationGoalCompleted(updated))
            } else {
                ExplorationEventBus.post(ExplorationGoalUpdated(updated))
            }

            saveToStorage()
            notifyTargetsChanged()
            return true
        }
        return false
    }

    /**
     * Valide et complète tous les objectifs actifs correspondant à un Pokémon et à un type d'événement.
     * Retourne la liste des objectifs nouvellement complétés.
     */
    @Synchronized
    fun completeMatchingGoals(
        speciesId: ResourceLocation,
        formId: ResourceLocation? = null,
        shiny: Boolean = false,
        type: GoalType
    ): List<ExplorationGoal> {
        val completed = mutableListOf<ExplorationGoal>()
        val now = System.currentTimeMillis()

        for (i in goals.indices) {
            val goal = goals[i]
            if (goal.isActive && (goal.goalType == type || (type == GoalType.CAPTURE && goal.goalType == GoalType.FIND_SHINY && shiny))) {
                if (goal.target.matches(speciesId, formId, shiny)) {
                    val finished = goal.copy(status = GoalStatus.COMPLETED, completedAt = now)
                    goals[i] = finished
                    completed.add(finished)
                    ExplorationEventBus.post(ExplorationGoalCompleted(finished))
                }
            }
        }

        if (completed.isNotEmpty()) {
            saveToStorage()
            notifyTargetsChanged()
        }

        return completed
    }

    private fun notifyTargetsChanged() {
        ExplorationEventBus.post(TargetSelectionChanged(getActiveTargets()))
    }
}
