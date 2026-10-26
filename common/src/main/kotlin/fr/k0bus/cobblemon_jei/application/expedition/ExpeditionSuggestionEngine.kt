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
                title = "🌟 Grand Foyer de Découverte : ${topZone.name}",
                subtitle = "${targets1.size} espèces non capturées réunies",
                description = "Concentration maximale d'espèces manquantes dans ce secteur.",
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
                title = "💎 Piste aux Espèces Rares : ${rareZone.name}",
                subtitle = "Spécimens inhabituels et précieux à débusquer",
                description = "Ce biotope abrite des espèces à faible taux d'apparition.",
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
                title = "🚶 Exploration Rapide : ${closeZone.name}",
                subtitle = "Proche de vous (${closeZone.distanceToPlayer.toInt()}m)",
                description = "Secteur directement accessible pour une session de capture express.",
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

        val habitatTitle = if (altZone.isSubterranean) "⛏ Exploration Souterraine : ${altZone.name}" else "🌿 Expédition d'Habitat : ${altZone.name}"
        val habitatSubtitle = if (altZone.isSubterranean) "Cavités, spéléologie et Pokémon des profondeurs" else "Faune endémique de l'environnement"
        val habitatDesc = if (altZone.isSubterranean) "Explorez ces cavités profondes pour débusquer les espèces cavernicoles." else "Explorez cet écosystème singulier pour capturer ses résidents."

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
            isRaining || isThundering -> "⚡ Alerte Météo : Averse Favorable !"
            isNight -> "🌙 Expédition Nocturne"
            isDuskOrDawn -> "🌅 Crépuscule & Aube : Spécimens Clés"
            else -> "☀️ Observation Diurne Approfondie"
        }
        val condSub = when {
            isRaining || isThundering -> "Spawns exclusifs activés sous la pluie"
            isNight -> "Créatures actives au clair de lune"
            isDuskOrDawn -> "Tranche horaire privilégiée pour certaines espèces"
            else -> "Conditions favorables pour le Pokédex"
        }
        val targets5 = condZone.matchingTargets.map { it.target }.takeLast(4)
        suggestions.add(
            ExpeditionSuggestion(
                id = "sugg_5_cond_${condZone.id}",
                title = "$condTitle : ${condZone.name}",
                subtitle = condSub,
                description = "Conditions environnementales idéales pour compléter votre collection.",
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
                val msg = Component.literal("§6§l[Expédition Lancée !]§r §a${suggestion.title} §7($addedCount objectifs suivis au radar)")
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
