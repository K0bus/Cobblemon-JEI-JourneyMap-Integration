package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat
import fr.k0bus.cobblemon_jei.data.PokemonFossilDataLoader
import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionSuggestion
import net.minecraft.world.entity.player.Player

/**
 * Gestionnaire d'état, de données et de filtrage pour le Pokédex.
 * Découple la logique de requêtage, de pagination logique et de cache de données
 * de la couche d'affichage (CobblemonPokedexScreen).
 */
class PokedexStateHolder {

    var currentMainTab: MainTab = MainTab.POKEDEX
    var currentPokedexSubTab: PokedexSubTab = PokedexSubTab.ALL
    var currentEvolutionSubTab: EvolutionSubTab = EvolutionSubTab.PENDING_EVOLUTIONS
    var currentTab: Tab = Tab.ALL
    var onlyPcFeasibleEvolutions: Boolean = false

    val filterState: PokedexFilterState = PokedexFilterState()
    var searchQuery: String = ""

    // Données brutes
    var allTracked: List<TrackedPokemon> = emptyList()
    var allGoals: List<EvolutionGoal> = emptyList()
    var allBreedingOrigins: List<BreedingOriginGoal> = emptyList()
    var allFossilGoals: List<FossilGoal> = emptyList()
    var allItemGoals: List<EvolutionGoal> = emptyList()
    var allFossilSpecies: List<TrackedPokemon> = emptyList()
    var allLegendarySpecies: List<TrackedPokemon> = emptyList()

    // Données filtrées
    var filteredTracked: List<TrackedPokemon> = emptyList()
    var filteredGoals: List<EvolutionGoal> = emptyList()
    var filteredItemGoals: List<EvolutionGoal> = emptyList()
    var filteredBreedingOrigins: List<BreedingOriginGoal> = emptyList()
    var filteredFossilGoals: List<FossilGoal> = emptyList()
    var filteredFossils: List<TrackedPokemon> = emptyList()
    var filteredLegendaries: List<TrackedPokemon> = emptyList()

    // Éléments actuellement sélectionnés
    var selectedTracked: TrackedPokemon? = null
    var selectedGoal: EvolutionGoal? = null
    var selectedForm: FormData? = null

    // Caches pour les compteurs de filtres (Total | Manquants)
    private var biomeCountsCache: Map<CobblemonBiomeFilter, PokedexFilterManager.FilterCount>? = null
    private var rarityCountsCache: Map<SpawnRarityFilter, PokedexFilterManager.FilterCount>? = null
    private var genCountsCache: Map<GenerationFilter, PokedexFilterManager.FilterCount>? = null

    fun invalidateFilterCountsCache() {
        biomeCountsCache = null
        rarityCountsCache = null
        genCountsCache = null
    }

    fun getBiomeCounts(): Map<CobblemonBiomeFilter, PokedexFilterManager.FilterCount> {
        return biomeCountsCache ?: PokedexFilterManager.computeBiomeCounts(allTracked).also { biomeCountsCache = it }
    }

    fun getRarityCounts(): Map<SpawnRarityFilter, PokedexFilterManager.FilterCount> {
        return rarityCountsCache ?: PokedexFilterManager.computeRarityCounts(allTracked).also { rarityCountsCache = it }
    }

    fun getGenCounts(): Map<GenerationFilter, PokedexFilterManager.FilterCount> {
        return genCountsCache ?: PokedexFilterManager.computeGenCounts(allTracked).also { genCountsCache = it }
    }

    fun loadAllData(player: Player?) {
        PokemonTrackerService.refresh()
        allTracked = PokemonTrackerService.getAllTracked()
        invalidateFilterCountsCache()
        allGoals = PokemonTrackerService.getEvolutionGoals()
        allBreedingOrigins = PokemonTrackerService.getMissingOriginGoals()
        PokemonFossilDataLoader.ensureLoaded()
        allFossilSpecies = allTracked.filter { PokemonFossilDataLoader.isFossilLineage(it.species) }
        allFossilGoals = PokemonFossilDataLoader.getAllFossilRecipes().mapNotNull { rec ->
            val sp = rec.resultSpecies ?: return@mapNotNull null
            val tr = allTracked.find { PokemonTrackingManager.isSameSpecies(it.species, sp) }
            val ready = if (player != null) PokemonFossilDataLoader.isPlayerHoldingFossils(player, rec) else false
            val heldCount = if (player != null) rec.requiredFossilItemIds.sumOf { PokemonFossilDataLoader.getPlayerFossilCount(player, it) } else 0
            FossilGoal(
                recipe = rec,
                targetSpecies = sp,
                tracked = tr,
                isReadyToRevive = ready,
                heldFossilCount = heldCount,
                isCaught = tr?.isCaught == true,
                isEncountered = tr?.isEncountered == true
            )
        }
        allItemGoals = PokemonTrackerService.getItemEvolutionGoals()
        allLegendarySpecies = allTracked.filter {
            LegendaryMonumentsCompat.isCoveredSpecies(it.species.name) ||
                    it.species.labels.any { l -> l.equals("legendary", true) || l.equals("mythical", true) || l.equals("ultra_beast", true) }
        }

        if (selectedTracked == null) {
            selectedTracked = allTracked.firstOrNull()
        }
    }

    fun resetFilters() {
        filterState.reset()
        applyFilters(searchQuery)
    }

    fun applyFilters(query: String = searchQuery) {
        searchQuery = query
        val q = query.trim().lowercase()

        when (currentMainTab) {
            MainTab.POKEDEX -> {
                when (currentPokedexSubTab) {
                    PokedexSubTab.ALL -> {
                        val stream = allTracked.filter { matchesQuery(it, q) && PokedexFilterManager.matches(it, filterState) }
                        filteredTracked = PokedexFilterManager.sort(stream, filterState.selectedSort)
                    }
                    PokedexSubTab.MISSING -> {
                        val stream = allTracked.filter { (it.isMissing || it.isEncountered) && matchesQuery(it, q) && PokedexFilterManager.matches(it, filterState) }
                        filteredTracked = PokedexFilterManager.sort(stream, filterState.selectedSort)
                    }
                }
            }
            MainTab.EVOLUTIONS -> {
                when (currentEvolutionSubTab) {
                    EvolutionSubTab.PENDING_EVOLUTIONS -> {
                        var stream = allGoals
                        if (onlyPcFeasibleEvolutions) {
                            stream = stream.filter {
                                it.feasibility == EvolutionFeasibility.READY_TO_EVOLVE ||
                                        it.feasibility == EvolutionFeasibility.CAN_EVOLVE_WITH_PC
                            }
                        }
                        if (filterState.isActive()) {
                            stream = stream.filter { goal ->
                                val dummyTarget = TrackedPokemon(goal.targetSpecies, goal.targetStatus, emptyList())
                                PokedexFilterManager.matches(dummyTarget, filterState)
                            }
                        } else {
                            stream = stream.filter { it.targetStatus != PokedexStatus.CAUGHT }
                        }
                        filteredGoals = stream.filter { goal ->
                            matchesGoalQuery(goal, q)
                        }
                    }
                    EvolutionSubTab.ITEM_EVOLUTIONS -> {
                        var stream = allItemGoals
                        if (onlyPcFeasibleEvolutions) {
                            stream = stream.filter {
                                it.feasibility == EvolutionFeasibility.READY_TO_EVOLVE ||
                                        it.feasibility == EvolutionFeasibility.CAN_EVOLVE_WITH_PC
                            }
                        }
                        if (filterState.isActive()) {
                            stream = stream.filter { goal ->
                                val dummyTarget = TrackedPokemon(goal.targetSpecies, goal.targetStatus, emptyList())
                                PokedexFilterManager.matches(dummyTarget, filterState)
                            }
                        } else {
                            stream = stream.filter { it.targetStatus != PokedexStatus.CAUGHT }
                        }
                        filteredItemGoals = stream.filter { goal ->
                            matchesGoalQuery(goal, q)
                        }
                    }
                    EvolutionSubTab.MISSING_ORIGINS -> {
                        var stream = allBreedingOrigins
                        if (filterState.isActive()) {
                            stream = stream.filter { goal ->
                                val baseTracked = allTracked.find { PokemonTrackingManager.isSameSpecies(it.species, goal.baseSpecies) }
                                    ?: TrackedPokemon(goal.baseSpecies, PokedexStatus.MISSING, emptyList())
                                PokedexFilterManager.matches(baseTracked, filterState)
                            }
                        }
                        filteredBreedingOrigins = stream.filter { goal ->
                            matchesBreedingOriginQuery(goal, q)
                        }
                    }
                    EvolutionSubTab.FOSSILS -> {
                        val fossilGoalsStream = allFossilGoals.filter { goal ->
                            val nameMatch = q.isBlank() ||
                                    goal.recipe.speciesDisplayName.lowercase().contains(q) ||
                                    goal.recipe.fossilDisplayName.lowercase().contains(q) ||
                                    goal.recipe.associatedStructureDisplayNames.any { it.lowercase().contains(q) }
                            val statusMatch = when (filterState.selectedStatus) {
                                StatusFilter.ALL -> true
                                StatusFilter.CAUGHT -> goal.isCaught
                                StatusFilter.ENCOUNTERED -> goal.isEncountered
                                StatusFilter.MISSING -> !goal.isCaught
                            }
                            val typeMatch = goal.targetSpecies?.let { PokedexFilterManager.matchesTypes(it, filterState.selectedTypes) } ?: true
                            nameMatch && statusMatch && typeMatch
                        }
                        filteredFossilGoals = fossilGoalsStream.sortedWith(
                            compareByDescending<FossilGoal> { it.isReadyToRevive }
                                .thenByDescending { it.heldFossilCount > 0 }
                                .thenBy { it.recipe.nationalDexNumber }
                        )
                        val stream = allFossilSpecies.filter { matchesQuery(it, q) && PokedexFilterManager.matches(it, filterState) }
                        filteredFossils = PokedexFilterManager.sort(stream, filterState.selectedSort)
                    }
                    EvolutionSubTab.LEGENDARIES -> {
                        val stream = allLegendarySpecies.filter { matchesQuery(it, q) && PokedexFilterManager.matches(it, filterState) }
                        filteredLegendaries = PokedexFilterManager.sort(stream, filterState.selectedSort)
                    }
                }
            }
            MainTab.EXPEDITIONS -> {
                // Pas de filtrage complexe pour les suggestions d'expéditions
            }
        }
    }

    fun matchesQuery(item: TrackedPokemon, query: String): Boolean {
        if (query.isBlank()) return true
        val cleanName = item.species.translatedName.string.lowercase()
        val dexStr = String.format("%03d", item.nationalDexNumber)
        val dexFull = "#${dexStr}"
        val types = listOfNotNull(
            item.species.standardForm.primaryType.name.lowercase(),
            item.species.standardForm.secondaryType?.name?.lowercase()
        )
        return cleanName.contains(query) ||
                dexStr.contains(query) ||
                dexFull.contains(query) ||
                types.any { it.contains(query) }
    }

    fun matchesGoalQuery(goal: EvolutionGoal, query: String): Boolean {
        if (query.isBlank()) return true
        val src = goal.sourceSpecies.translatedName.string.lowercase()
        val tgt = goal.targetSpecies.translatedName.string.lowercase()
        val req = goal.requirementsSummary.lowercase()
        return src.contains(query) || tgt.contains(query) || req.contains(query)
    }

    fun matchesBreedingOriginQuery(goal: BreedingOriginGoal, query: String): Boolean {
        if (query.isBlank()) return true
        val baseName = goal.baseSpecies.translatedName.string.lowercase()
        val dexStr = String.format("%03d", goal.baseSpecies.nationalPokedexNumber)
        val dexFull = "#${dexStr}"
        val parents = goal.candidateParents.joinToString(" ") { it.translatedName.string.lowercase() }
        val incense = goal.requiredIncense?.lowercase() ?: ""
        return baseName.contains(query) || dexStr.contains(query) || dexFull.contains(query) || parents.contains(query) || incense.contains(query)
    }

    fun buildLeftContentData(
        isExpeditionLoading: Boolean = false,
        expeditionSuggestions: List<ExpeditionSuggestion> = emptyList()
    ): LeftContentData = LeftContentData(
        mainTab = currentMainTab,
        pokedexSubTab = currentPokedexSubTab,
        evolutionSubTab = currentEvolutionSubTab,
        selectedTracked = selectedTracked,
        selectedGoal = selectedGoal,
        isExpeditionLoading = isExpeditionLoading,
        allTracked = allTracked,
        filteredTracked = filteredTracked,
        filteredGoals = filteredGoals,
        filteredItemGoals = filteredItemGoals,
        filteredBreedingOrigins = filteredBreedingOrigins,
        filteredFossilGoals = filteredFossilGoals,
        filteredLegendaries = filteredLegendaries,
        expeditionSuggestions = expeditionSuggestions
    )
}
