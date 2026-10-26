package fr.k0bus.cobblemon_jei.application.progression

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import fr.k0bus.cobblemon_jei.application.goals.GoalService
import fr.k0bus.cobblemon_jei.domain.goals.ExplorationGoal
import fr.k0bus.cobblemon_jei.domain.goals.GoalType
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import org.slf4j.LoggerFactory

/**
 * Service de progression automatique : écoute les événements de jeu Cobblemon
 * et met à jour l'état des objectifs d'exploration.
 */
object ProgressionService {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/ProgressionService")
    private var isInitialized = false

    /**
     * Initialise les abonnements aux événements Cobblemon.
     */
    fun init() {
        if (isInitialized) return

        try {
            // 1. Événement de capture
            CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
                try {
                    val clientPlayer = Minecraft.getInstance().player ?: return@subscribe
                    if (event.player.uuid != clientPlayer.uuid) return@subscribe

                    val pokemon = event.pokemon
                    val speciesResId = pokemon.species.resourceIdentifier
                    val formResId = try {
                        ResourceLocation.parse(pokemon.form.name.lowercase())
                    } catch (_: Throwable) {
                        null
                    }
                    val isShiny = pokemon.shiny

                    val completedGoals = GoalService.completeMatchingGoals(
                        speciesId = speciesResId,
                        formId = formResId,
                        shiny = isShiny,
                        type = GoalType.CAPTURE
                    )

                    for (goal in completedGoals) {
                        notifyGoalCompleted(goal, "chat.cobblemon_jei.goal.completed.capture")
                    }
                } catch (t: Throwable) {
                    logger.debug("Erreur lors du traitement de l'événement POKEMON_CAPTURED: {}", t.message)
                }
            }

            // 2. Événement d'évolution
            CobblemonEvents.EVOLUTION_COMPLETE.subscribe { event ->
                try {
                    val clientPlayer = Minecraft.getInstance().player ?: return@subscribe
                    val ownerUuid = try { event.pokemon.getOwnerUUID() } catch (_: Throwable) { null }
                    if (ownerUuid != null && ownerUuid != clientPlayer.uuid) return@subscribe

                    val pokemon = event.pokemon
                    val speciesResId = pokemon.species.resourceIdentifier
                    val formResId = try {
                        ResourceLocation.parse(pokemon.form.name.lowercase())
                    } catch (_: Throwable) {
                        null
                    }
                    val isShiny = pokemon.shiny

                    val completedGoals = GoalService.completeMatchingGoals(
                        speciesId = speciesResId,
                        formId = formResId,
                        shiny = isShiny,
                        type = GoalType.EVOLVE
                    )

                    for (goal in completedGoals) {
                        notifyGoalCompleted(goal, "chat.cobblemon_jei.goal.completed.evolve")
                    }
                } catch (t: Throwable) {
                    logger.debug("Erreur lors du traitement de l'événement EVOLUTION_COMPLETE: {}", t.message)
                }
            }

            // 3. Événement de Pokédex mis à jour (découverte de forme)
            CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe { event ->
                try {
                    val pokemon = event.dataSource.pokemon
                    val speciesResId = pokemon.species.resourceIdentifier
                    val formResId = try {
                        ResourceLocation.parse(pokemon.form.name.lowercase())
                    } catch (_: Throwable) {
                        null
                    }
                    val isShiny = pokemon.shiny

                    if (event.knowledge == PokedexEntryProgress.CAUGHT) {
                        val completedGoals = GoalService.completeMatchingGoals(
                            speciesId = speciesResId,
                            formId = formResId,
                            shiny = isShiny,
                            type = GoalType.DISCOVER_FORM
                        )

                        for (goal in completedGoals) {
                            notifyGoalCompleted(goal, "chat.cobblemon_jei.goal.completed.form")
                        }
                    }
                } catch (t: Throwable) {
                    logger.debug("Erreur lors du traitement de l'événement POKEDEX_DATA_CHANGED_POST: {}", t.message)
                }
            }

            isInitialized = true
            logger.info("Cobblemon JEI: ProgressionService initialisé avec succès.")
        } catch (e: Throwable) {
            logger.warn("Impossible d'initialiser ProgressionService: {}", e.message)
        }
    }

    /**
     * Joue un son d'accomplissement et affiche un message au joueur.
     */
    private fun notifyGoalCompleted(goal: ExplorationGoal, translationKey: String) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        try {
            // Son de triomphe
            mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f))
        } catch (_: Throwable) {}

        val speciesName = goal.target.speciesId.path.replaceFirstChar { it.uppercase() }
        val shinySuffix = if (goal.target.shiny) " §6★ Shiny" else ""
        val message = Component.literal("§6§l[Objectif Terminé !]§r §a$speciesName$shinySuffix §7a été complété !")

        try {
            player.displayClientMessage(message, false)
        } catch (_: Throwable) {}
    }
}
