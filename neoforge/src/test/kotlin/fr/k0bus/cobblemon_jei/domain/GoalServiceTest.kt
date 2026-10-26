package fr.k0bus.cobblemon_jei.domain

import fr.k0bus.cobblemon_jei.application.goals.GoalService
import fr.k0bus.cobblemon_jei.domain.events.*
import fr.k0bus.cobblemon_jei.domain.goals.GoalStatus
import fr.k0bus.cobblemon_jei.domain.goals.GoalType
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager
import fr.k0bus.cobblemon_jei.infrastructure.persistence.JsonGoalStorage
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GoalServiceTest {

    @TempDir
    lateinit var tempDir: File

    private val bulbasaur = ResourceLocation.fromNamespaceAndPath("cobblemon", "bulbasaur")
    private val pikachu = ResourceLocation.fromNamespaceAndPath("cobblemon", "pikachu")

    @BeforeEach
    fun setup() {
        ExplorationEventBus.clearListeners()
        GoalService.storage = JsonGoalStorage { tempDir }
        WorldSessionManager.startNewSession(customId = "test_world_1")
    }

    @AfterEach
    fun teardown() {
        WorldSessionManager.endSession()
        ExplorationEventBus.clearListeners()
    }

    @Test
    fun `test add and toggle goals`() {
        val target = PokemonTarget(bulbasaur)
        assertFalse(GoalService.isTargetActive(bulbasaur))

        // Ajout
        val goal = GoalService.addGoal(target, GoalType.CAPTURE)
        assertTrue(GoalService.isTargetActive(bulbasaur))
        assertEquals(GoalStatus.ACTIVE, goal.status)
        assertEquals(1, GoalService.getActiveGoals().size)

        // Toggle pour supprimer
        val activeAfterToggle = GoalService.toggleGoal(target)
        assertFalse(activeAfterToggle)
        assertFalse(GoalService.isTargetActive(bulbasaur))
        assertEquals(0, GoalService.getActiveGoals().size)

        // Toggle pour réactiver
        val activeAfterSecondToggle = GoalService.toggleGoal(target)
        assertTrue(activeAfterSecondToggle)
        assertTrue(GoalService.isTargetActive(bulbasaur))
    }

    @Test
    fun `test complete matching goals on capture`() {
        val targetBulba = PokemonTarget(bulbasaur)
        val targetPika = PokemonTarget(pikachu)
        GoalService.addGoal(targetBulba, GoalType.CAPTURE)
        GoalService.addGoal(targetPika, GoalType.CAPTURE)

        assertEquals(2, GoalService.getActiveGoals().size)

        var completedEventReceived = false
        ExplorationEventBus.subscribe { event ->
            if (event is ExplorationGoalCompleted && event.goal.target.speciesId == bulbasaur) {
                completedEventReceived = true
            }
        }

        val completed = GoalService.completeMatchingGoals(bulbasaur, type = GoalType.CAPTURE)
        assertEquals(1, completed.size)
        assertEquals(bulbasaur, completed[0].target.speciesId)
        assertTrue(completed[0].isCompleted)
        assertNotNull(completed[0].completedAt)

        assertTrue(completedEventReceived)
        assertFalse(GoalService.isTargetActive(bulbasaur))
        assertTrue(GoalService.isTargetActive(pikachu))
        assertEquals(1, GoalService.getActiveGoals().size)
    }

    @Test
    fun `test world session isolation and persistence`() {
        val target = PokemonTarget(bulbasaur)
        GoalService.addGoal(target, GoalType.CAPTURE)
        assertEquals(1, GoalService.getActiveGoals().size)

        // Changement vers un second monde
        WorldSessionManager.startNewSession(customId = "test_world_2")
        assertEquals(0, GoalService.getActiveGoals().size)
        assertFalse(GoalService.isTargetActive(bulbasaur))

        // Retour au premier monde : les objectifs doivent être rechargés depuis le stockage
        WorldSessionManager.startNewSession(customId = "test_world_1")
        assertEquals(1, GoalService.getActiveGoals().size)
        assertTrue(GoalService.isTargetActive(bulbasaur))
    }
}
