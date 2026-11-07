package fr.k0bus.cobblemon_jei.application.expedition

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import fr.k0bus.cobblemon_jei.application.goals.GoalService
import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionSuggestion
import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionZone
import fr.k0bus.cobblemon_jei.domain.expedition.SuggestionType
import fr.k0bus.cobblemon_jei.domain.goals.GoalType
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import fr.k0bus.cobblemon_jei.pokedex.PokemonTrackerService
import fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

/**
 * Moteur intelligent proposant des expéditions proactives et adaptées au joueur,
 * basées sur les Pokémon manquants au Pokédex et les conditions météo / temporelles en direct.
 */
object ExpeditionSuggestionEngine {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/SuggestionEngine")

    /**
     * Génère la liste des expéditions recommandées pour le joueur en ce moment.
     */
    fun generateSuggestions(): List<ExpeditionSuggestion> {
        val mc = try { Minecraft.getInstance() } catch (_: Throwable) { null }
        val level = mc?.level ?: return emptyList()

        var uncaught = getUncaughtPokemonTargets()
        if (uncaught.isEmpty()) {
            val allTracked = PokemonTrackerService.getAllTracked()
            val snapshot = fr.k0bus.cobblemon_jei.data.repository.PokemonDataRepository.getSnapshot()
            uncaught = allTracked.filter { tracked ->
                ExpeditionPlannerService.isSpeciesEligibleForExpedition(tracked.species.name, snapshot)
            }.take(80).map {
                PokemonTarget(it.species.resourceIdentifier)
            }
        }
        if (uncaught.isEmpty()) return emptyList()

        val plan = ExpeditionPlannerService.createPlan("Opportunités Pokédex", uncaught)
        val availableZones = plan.rankedZones

        if (availableZones.isEmpty()) return emptyList()

        val isRaining = level.isRaining
        val isThundering = level.isThundering
        val dayTime = level.dayTime % 24000L
        val isNight = dayTime in 13000L..23000L
        val isDuskOrDawn = dayTime in 12000L..13000L || dayTime in 23000L..24000L || dayTime in 0L..1000L

        val suggestions = mutableListOf<ExpeditionSuggestion>()
        val usedZoneIds = mutableSetOf<String>()

        // 1. Suggestion Majeure : Foyer de découverte principal (score maximal)
        val topZone = availableZones.first()
        usedZoneIds.add(topZone.id)
        val targets1 = topZone.matchingTargets.map { it.target }
        suggestions.add(
            ExpeditionSuggestion(
                id = "sugg_1_major_${topZone.id}",
                title = Component.translatable("gui.cobblemon_jei.expedition.sugg.major_title", topZone.name).string,
                subtitle = Component.translatable("gui.cobblemon_jei.expedition.sugg.major_subtitle", targets1.size).string,
                description = Component.translatable("gui.cobblemon_jei.expedition.sugg.major_desc").string,
                type = SuggestionType.POKEDEX_OPPORTUNITY,
                targets = targets1,
                primaryZone = topZone,
                isConditionActiveNow = true
            )
        )

        // 2. Suggestion Rareté : Zone ou cibles avec espèces rares
        val rareZone = availableZones.firstOrNull { it.id !in usedZoneIds && it.matchingTargets.any { m -> m.spawnConfidence == "rare" || m.spawnConfidence == "ultra-rare" } }
            ?: availableZones.getOrNull(1)
            ?: topZone
        usedZoneIds.add(rareZone.id)
        val targets2 = rareZone.matchingTargets.map { it.target }
        suggestions.add(
            ExpeditionSuggestion(
                id = "sugg_2_rare_${rareZone.id}",
                title = Component.translatable("gui.cobblemon_jei.expedition.sugg.rare_title", rareZone.name).string,
                subtitle = Component.translatable("gui.cobblemon_jei.expedition.sugg.rare_subtitle").string,
                description = Component.translatable("gui.cobblemon_jei.expedition.sugg.rare_desc").string,
                type = SuggestionType.POKEDEX_OPPORTUNITY,
                targets = targets2,
                primaryZone = rareZone,
                isConditionActiveNow = true
            )
        )

        // 3. Suggestion Proximité : La zone la plus proche du joueur
        val closeZone = availableZones.filter { it.id !in usedZoneIds }.minByOrNull { it.distanceToPlayer }
            ?: availableZones.getOrNull(2)
            ?: availableZones.minByOrNull { it.distanceToPlayer }
            ?: topZone
        usedZoneIds.add(closeZone.id)
        val targets3 = closeZone.matchingTargets.map { it.target }
        suggestions.add(
            ExpeditionSuggestion(
                id = "sugg_3_close_${closeZone.id}",
                title = Component.translatable("gui.cobblemon_jei.expedition.sugg.close_title", closeZone.name).string,
                subtitle = Component.translatable("gui.cobblemon_jei.expedition.sugg.close_subtitle", closeZone.distanceToPlayer.toInt()).string,
                description = Component.translatable("gui.cobblemon_jei.expedition.sugg.close_desc").string,
                type = SuggestionType.THEMATIC,
                targets = targets3,
                primaryZone = closeZone,
                isConditionActiveNow = true
            )
        )

        // 4. Suggestion d'Habitat Spécifique / Écosystème sous-terrain ou alternatif
        val subZone = availableZones.firstOrNull { it.id !in usedZoneIds && it.isSubterranean }
        val altZone = subZone
            ?: availableZones.firstOrNull { it.id !in usedZoneIds }
            ?: availableZones.getOrNull(3)
            ?: availableZones.getOrNull(0)
            ?: topZone
        usedZoneIds.add(altZone.id)
        val targets4 = if (altZone.matchingTargets.size > 3) altZone.matchingTargets.map { it.target }.shuffled().take(4) else altZone.matchingTargets.map { it.target }

        val habitatTitle = if (altZone.isSubterranean) {
            Component.translatable("gui.cobblemon_jei.expedition.sugg.cave_title", altZone.name).string
        } else {
            Component.translatable("gui.cobblemon_jei.expedition.sugg.habitat_title", altZone.name).string
        }
        val habitatSubtitle = if (altZone.isSubterranean) {
            Component.translatable("gui.cobblemon_jei.expedition.sugg.cave_subtitle").string
        } else {
            Component.translatable("gui.cobblemon_jei.expedition.sugg.habitat_subtitle").string
        }
        val habitatDesc = if (altZone.isSubterranean) {
            Component.translatable("gui.cobblemon_jei.expedition.sugg.cave_desc").string
        } else {
            Component.translatable("gui.cobblemon_jei.expedition.sugg.habitat_desc").string
        }

        suggestions.add(
            ExpeditionSuggestion(
                id = "sugg_4_habitat_${altZone.id}",
                title = habitatTitle,
                subtitle = habitatSubtitle,
                description = habitatDesc,
                type = SuggestionType.THEMATIC,
                targets = targets4,
                primaryZone = altZone,
                isConditionActiveNow = altZone.isSubterranean
            )
        )

        // 5. Suggestion Opportunité / Météo & Heure
        val condZone = availableZones.firstOrNull { it.id !in usedZoneIds }
            ?: availableZones.getOrNull(4)
            ?: topZone
        val isCondActive = isRaining || isThundering || isNight || isDuskOrDawn
        val condTitle = when {
            isRaining || isThundering -> Component.translatable("gui.cobblemon_jei.expedition.sugg.rain_title").string
            isNight -> Component.translatable("gui.cobblemon_jei.expedition.sugg.night_title").string
            isDuskOrDawn -> Component.translatable("gui.cobblemon_jei.expedition.sugg.dusk_title").string
            else -> Component.translatable("gui.cobblemon_jei.expedition.sugg.day_title").string
        }
        val condSub = when {
            isRaining || isThundering -> Component.translatable("gui.cobblemon_jei.expedition.sugg.rain_sub").string
            isNight -> Component.translatable("gui.cobblemon_jei.expedition.sugg.night_sub").string
            isDuskOrDawn -> Component.translatable("gui.cobblemon_jei.expedition.sugg.dusk_sub").string
            else -> Component.translatable("gui.cobblemon_jei.expedition.sugg.day_sub").string
        }
        val targets5 = condZone.matchingTargets.map { it.target }.takeLast(4)
        suggestions.add(
            ExpeditionSuggestion(
                id = "sugg_5_cond_${condZone.id}",
                title = "$condTitle : ${condZone.name}",
                subtitle = condSub,
                description = Component.translatable("gui.cobblemon_jei.expedition.sugg.day_sub").string,
                type = if (isRaining || isThundering) SuggestionType.WEATHER_TRIGGER else SuggestionType.TIME_TRIGGER,
                targets = targets5,
                primaryZone = condZone,
                isConditionActiveNow = isCondActive
            )
        )

        // Assurer des scores échelonnés et strictement distincts (par ex. 92, 84, 76, 68, 59)
        var baseScore = 94.0
        return suggestions.mapIndexed { index, sugg ->
            val distinctScore = (baseScore - (index * 8.0)).coerceAtLeast(20.0)
            val updatedZone = sugg.primaryZone.copy(score = distinctScore)
            sugg.copy(primaryZone = updatedZone)
        }
    }

    /**
     * Accepte une expédition suggérée en un clic :
     * 1. Transforme toutes les cibles en objectifs actifs dans [GoalService].
     * 2. Active le suivi radar en mode [PokemonTrackingManager.TrackingMode.GOALS].
     * 3. Affiche une notification de confirmation.
     */
    fun acceptSuggestion(suggestion: ExpeditionSuggestion) {
        var addedCount = 0
        for (target in suggestion.targets) {
            GoalService.addGoal(target, GoalType.CAPTURE)
            addedCount++
        }

        // Activer le mode de suivi des objectifs
        PokemonTrackingManager.trackGoals(notifyPlayer = false)

        try {
            val player = Minecraft.getInstance()?.player
            if (player != null) {
                val msg = Component.translatable("chat.cobblemon_jm.expedition_started", suggestion.title, addedCount)
                player.displayClientMessage(msg, false)
            }
        } catch (_: Throwable) {
            // Environnement de test headless sans client Minecraft actif
        }
        logger.info("Cobblemon JEI: Expédition '${suggestion.title}' acceptée avec ${suggestion.targets.size} cibles.")
    }

    private fun getUncaughtPokemonTargets(): List<PokemonTarget> {
        val all = PokemonTrackerService.getAllTracked()
        val snapshot = fr.k0bus.cobblemon_jei.data.repository.PokemonDataRepository.getSnapshot()
        return all.filter { tracked ->
            !tracked.isCaught && ExpeditionPlannerService.isSpeciesEligibleForExpedition(tracked.species.name, snapshot)
        }.map {
            PokemonTarget(it.species.resourceIdentifier)
        }
    }
}
