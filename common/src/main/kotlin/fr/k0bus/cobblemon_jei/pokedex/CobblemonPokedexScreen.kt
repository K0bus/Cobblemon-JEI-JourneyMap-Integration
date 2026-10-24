package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.compat.cobbleworkers.CobbleworkersCompat
import fr.k0bus.cobblemon_jei.data.CobbleworkersJobDataLoader
import fr.k0bus.cobblemon_jei.data.PokemonDropDataLoader
import fr.k0bus.cobblemon_jei.data.PokemonEvolutionDataLoader
import fr.k0bus.cobblemon_jei.data.PokemonPokeSnackResolver
import fr.k0bus.cobblemon_jei.data.HowToCreate
import fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader
import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import fr.k0bus.cobblemon_jei.journeymap.CobblemonOverlayManager
import fr.k0bus.cobblemon_jei.journeymap.PokemonRadarService
import fr.k0bus.cobblemon_jei.journeymap.SpawnOverlayManager
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Interface graphique complète du Pokédex & Tracker d'évolutions.
 * Propose une grille de suivi des Pokémon (Tous, Manquants) et un onglet dédié
 * aux évolutions à réaliser, avec mise en avant de celles faisables grâce au PC.
 */
class CobblemonPokedexScreen(
    private val parentScreen: Screen? = null
) : Screen(Component.translatable("gui.cobblemon_jei.pokedex.title")) {

    enum class Tab(val labelKey: String) {
        ALL("gui.cobblemon_jei.pokedex.tab.all"),
        MISSING("gui.cobblemon_jei.pokedex.tab.missing"),
        EVOLUTIONS("gui.cobblemon_jei.pokedex.tab.evolutions")
    }

    enum class RightPanelTab(val label: String, val tooltip: String) {
        DESCRIPTION("Infos", "Description & Morphologie"),
        FORMS("Formes", "Formes & Variantes régionales"),
        ABILITIES("Talents", "Talents & Capacités spéciales"),
        STATS("Stats", "Statistiques de base & EV"),
        EVOLUTIONS("Évos", "Lignée évolutive (Source & Destination)"),
        DROPS("Butin", "Objets lâchés à la défaite"),
        PASTURE("Pâturage", "Métiers au pâturage (Cobbleworkers)"),
        SPAWNS("Zones", "Zones d'apparition sauvage")
    }

    private var currentTab: Tab = Tab.ALL
    private var onlyPcFeasibleEvolutions: Boolean = false

    // Onglet et défilement du panneau droit
    enum class SpawnSubCategory(val label: String, val tooltip: String) {
        NATURAL("🌲 Naturel", "Zones et conditions d'apparition sauvage"),
        POKE_SNACK("🍪 Poké Snack", "Assaisonnements optimaux et recette pour attirer ce Pokémon")
    }

    private var currentRightPanelTab: RightPanelTab = RightPanelTab.DESCRIPTION
    private var currentSpawnCategory: SpawnSubCategory = SpawnSubCategory.NATURAL
    private var spawnCategoryNaturalBtnRect: IntArray? = null
    private var spawnCategorySnackBtnRect: IntArray? = null
    private var hoveredPokeSnackStack: ItemStack? = null
    private var rightPanelScrollOffset: Double = 0.0
    private var rightPanelMaxScroll: Double = 0.0
    private var isDraggingRightScrollbar: Boolean = false
    private var dragStartRightMouseY: Double = 0.0
    private var dragStartRightScroll: Double = 0.0
    private val rightPanelClickablePokemon = mutableListOf<Triple<IntArray, Species, Boolean>>()

    // Éléments d'interface
    private lateinit var searchBox: EditBox
    private lateinit var tabAllBtn: Button
    private lateinit var tabMissingBtn: Button
    private lateinit var tabEvolutionsBtn: Button
    private lateinit var togglePcEvolutionsBtn: Button
    private lateinit var trackerBtn: Button

    // Gestion du Tracker Modal et Watchlist
    private var isTrackerModalOpen: Boolean = false
    private var trackerWatchlistScrollOffset: Double = 0.0
    private var trackerWatchlistMaxScroll: Double = 0.0
    private var watchlistHeaderButtonRect: IntArray? = null
    private var trackHeaderButtonRect: IntArray? = null

    // Gestion des filtres avancés et de la modale de filtres
    private val filterState = PokedexFilterState()
    private var isFilterModalOpen: Boolean = false
    private var activeFilterCategory: FilterCategory = FilterCategory.TYPE
    private var biomeScrollOffset: Double = 0.0
    private var biomeMaxScroll: Double = 0.0
    private var isDraggingBiomeScrollbar: Boolean = false
    private var dragStartBiomeMouseY: Double = 0.0
    private var dragStartBiomeScroll: Double = 0.0
    private lateinit var filterBtn: Button
    private lateinit var quickResetFilterBtn: Button
    private lateinit var biomeSearchBox: EditBox

    // Données filtrées
    private var allTracked: List<TrackedPokemon> = emptyList()
    private var filteredTracked: List<TrackedPokemon> = emptyList()
    private var selectedTracked: TrackedPokemon? = null

    // Caches pour les compteurs de filtres (Total | Manquants)
    private var biomeCountsCache: Map<CobblemonBiomeFilter, PokedexFilterManager.FilterCount>? = null
    private var rarityCountsCache: Map<SpawnRarityFilter, PokedexFilterManager.FilterCount>? = null
    private var genCountsCache: Map<GenerationFilter, PokedexFilterManager.FilterCount>? = null
    private var activeModalTooltip: List<Component>? = null

    private fun invalidateFilterCountsCache() {
        biomeCountsCache = null
        rarityCountsCache = null
        genCountsCache = null
    }

    private fun getBiomeCounts(): Map<CobblemonBiomeFilter, PokedexFilterManager.FilterCount> {
        return biomeCountsCache ?: PokedexFilterManager.computeBiomeCounts(allTracked).also { biomeCountsCache = it }
    }

    private fun getRarityCounts(): Map<SpawnRarityFilter, PokedexFilterManager.FilterCount> {
        return rarityCountsCache ?: PokedexFilterManager.computeRarityCounts(allTracked).also { rarityCountsCache = it }
    }

    private fun getGenCounts(): Map<GenerationFilter, PokedexFilterManager.FilterCount> {
        return genCountsCache ?: PokedexFilterManager.computeGenCounts(allTracked).also { genCountsCache = it }
    }

    private var allGoals: List<EvolutionGoal> = emptyList()
    private var filteredGoals: List<EvolutionGoal> = emptyList()
    private var selectedGoal: EvolutionGoal? = null

    // Cache d'items (incluant la forme et le statut Shiny)
    private val itemStackCache = mutableMapOf<Triple<Species, FormData?, Boolean>, ItemStack>()

    // Gestion de l'affichage Shiny et sélection de forme
    private var selectedForm: FormData? = null
    private var globalShinyForms: Boolean = false
    private val individualShinyForms = mutableSetOf<String>()
    private var shinyHeaderButtonRect: IntArray? = null
    private var zoomHeaderButtonRect: IntArray? = null
    private val shinyCardButtonRects = mutableListOf<Pair<IntArray, FormData>>()
    private val zoomCardButtonRects = mutableListOf<Pair<IntArray, FormData>>()
    private val selectCardButtonRects = mutableListOf<Pair<IntArray, FormData>>()
    private var topPreviewRect: IntArray? = null
    private var hoveredZoomCardForm: FormData? = null

    // Visionneuse Grand Format (Showcase Modal)
    private var isZoomModalOpen: Boolean = false
    private var zoomModalForm: FormData? = null
    private var zoomModalShiny: Boolean = false

    private fun isFormShiny(form: FormData?): Boolean {
        if (form == null) return globalShinyForms
        val key = form.name.lowercase()
        return if (globalShinyForms) {
            !individualShinyForms.contains(key)
        } else {
            individualShinyForms.contains(key)
        }
    }

    private fun toggleFormShiny(form: FormData) {
        val key = form.name.lowercase()
        if (individualShinyForms.contains(key)) {
            individualShinyForms.remove(key)
        } else {
            individualShinyForms.add(key)
        }
    }

    private fun getSpeciesForms(sp: Species): List<FormData> {
        val allForms = mutableListOf<FormData>()
        allForms.add(sp.standardForm)
        for (f in sp.forms) {
            if (f != sp.standardForm && allForms.none { it.name.equals(f.name, ignoreCase = true) }) {
                allForms.add(f)
            }
        }
        return allForms
    }

    private fun getFormDisplayName(form: FormData?, sp: Species): String =
        PokemonTrackerService.getFormDisplayName(form, sp)

    private data class FormStatusInfo(val text: String, val color: Int)

    private fun getFormStatusInfo(sp: Species, form: FormData, isShiny: Boolean, currentTracked: TrackedPokemon?): FormStatusInfo {
        val formRecord = try {
            val specRecord = CobblemonClient.clientPokedexData.speciesRecords[sp.resourceIdentifier]
            specRecord?.getFormRecord(form.name.lowercase().ifBlank { "normal" })
                ?: specRecord?.getFormRecord(form.name.lowercase())
                ?: specRecord?.getFormRecord("")
        } catch (_: Throwable) { null }

        return if (isShiny) {
            val ownedShinyCount = currentTracked?.ownedInstances?.count {
                it.pokemon.shiny && PokemonTrackerService.isFormMatch(it.pokemon, form, sp)
            } ?: 0
            val isDexShinyCaught = formRecord?.hasSeenShinyState(true) == true && (formRecord.knowledge == PokedexEntryProgress.CAUGHT)
            val isDexShinySeen = formRecord?.hasSeenShinyState(true) == true

            when {
                ownedShinyCount > 0 -> FormStatusInfo("§6★ Possédé ($ownedShinyCount)§r", 0xFFF1C40F.toInt())
                isDexShinyCaught -> FormStatusInfo("§6★ Capturé Shiny§r", 0xFFF1C40F.toInt())
                isDexShinySeen -> FormStatusInfo("§e👁 Vu Shiny (Pokédex)§r", 0xFFE67E22.toInt())
                else -> FormStatusInfo("§c✕ Non découvert Shiny§r", 0xFFE74C3C.toInt())
            }
        } else {
            val ownedCount = currentTracked?.ownedInstances?.count { PokemonTrackerService.isFormMatch(it.pokemon, form, sp) } ?: 0
            val isDexCaught = formRecord?.knowledge == PokedexEntryProgress.CAUGHT
            val isDexSeen = formRecord?.knowledge == PokedexEntryProgress.ENCOUNTERED

            when {
                ownedCount > 0 -> FormStatusInfo("§a✔ Possédé ($ownedCount)§r", 0xFF2ECC71.toInt())
                isDexCaught -> FormStatusInfo("§a✔ Capturé§r", 0xFF2ECC71.toInt())
                isDexSeen -> FormStatusInfo("§6👁 Vu (Pokédex)§r", 0xFFE67E22.toInt())
                else -> FormStatusInfo("§c✕ Non découvert§r", 0xFFE74C3C.toInt())
            }
        }
    }

    private fun openZoomModal(form: FormData?) {
        val sp = selectedTracked?.species ?: return
        zoomModalForm = form ?: selectedForm ?: sp.standardForm
        selectedForm = zoomModalForm
        zoomModalShiny = isFormShiny(zoomModalForm ?: sp.standardForm)
        isZoomModalOpen = true
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
    }

    private fun cycleZoomModalForm(direction: Int) {
        val sp = selectedTracked?.species ?: return
        val allForms = getSpeciesForms(sp)
        if (allForms.isEmpty()) return
        val curIndex = allForms.indexOfFirst { it.name.equals(zoomModalForm?.name ?: sp.standardForm.name, ignoreCase = true) }.coerceAtLeast(0)
        val newIndex = Math.floorMod(curIndex + direction, allForms.size)
        zoomModalForm = allForms[newIndex]
        selectedForm = zoomModalForm
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
    }

    // Scroll de la zone gauche
    private var scrollOffset: Double = 0.0
    private var maxScroll: Double = 0.0
    private var isDraggingScrollbar = false
    private var dragStartMouseY = 0.0
    private var dragStartScroll = 0.0

    // Dimensions de la grille
    private val cardWidth = 44
    private val cardHeight = 52
    private val cardGap = 4

    // Dimensions des cartes d'évolution
    private val evoCardHeight = 46

    private fun getItemStack(species: Species, form: FormData? = null, shiny: Boolean = false): ItemStack {
        val targetForm = form ?: species.standardForm
        return itemStackCache.computeIfAbsent(Triple(species, targetForm, shiny)) {
            try {
                val aspects = mutableSetOf<String>()
                aspects.addAll(targetForm.aspects)
                if (shiny) {
                    aspects.add("shiny")
                }
                PokemonItem.from(species, aspects, 1)
            } catch (_: Throwable) {
                ItemStack.EMPTY
            }
        }
    }

    override fun init() {
        PokemonTrackerService.refresh()
        allTracked = PokemonTrackerService.getAllTracked()
        invalidateFilterCountsCache()
        allGoals = PokemonTrackerService.getEvolutionGoals()

        if (selectedTracked == null) {
            selectedTracked = allTracked.firstOrNull()
        }

        val topY = 26
        val tabWidth = 110
        val tabHeight = 18

        // Boutons d'onglets
        tabAllBtn = Button.builder(Component.translatable(Tab.ALL.labelKey)) {
            switchTab(Tab.ALL)
        }.bounds(12, topY, tabWidth, tabHeight).build()

        tabMissingBtn = Button.builder(Component.translatable(Tab.MISSING.labelKey)) {
            switchTab(Tab.MISSING)
        }.bounds(12 + tabWidth + 4, topY, tabWidth + 10, tabHeight).build()

        val goalsCount = allGoals.count { it.feasibility == EvolutionFeasibility.READY_TO_EVOLVE }
        val evoLabel = if (goalsCount > 0) {
            Component.translatable(Tab.EVOLUTIONS.labelKey).append(" (§a$goalsCount§r)")
        } else {
            Component.translatable(Tab.EVOLUTIONS.labelKey)
        }
        tabEvolutionsBtn = Button.builder(evoLabel) {
            switchTab(Tab.EVOLUTIONS)
        }.bounds(12 + (tabWidth + 4) * 2 + 10, topY, tabWidth + 24, tabHeight).build()

        addRenderableWidget(tabAllBtn)
        addRenderableWidget(tabMissingBtn)
        addRenderableWidget(tabEvolutionsBtn)

        // Filtre toggle pour les évolutions faisables via PC
        togglePcEvolutionsBtn = Button.builder(getTogglePcButtonText()) {
            onlyPcFeasibleEvolutions = !onlyPcFeasibleEvolutions
            togglePcEvolutionsBtn.message = getTogglePcButtonText()
            applyFilters()
        }.bounds(tabEvolutionsBtn.x + tabEvolutionsBtn.width + 8, topY, 130, tabHeight).build()
        togglePcEvolutionsBtn.visible = (currentTab == Tab.EVOLUTIONS)
        addRenderableWidget(togglePcEvolutionsBtn)

        trackerBtn = Button.builder(getTrackerButtonText()) {
            openTrackerModal()
        }.bounds(getTrackerButtonX(), topY, 130, tabHeight).build()
        addRenderableWidget(trackerBtn)

        // Champ de recherche et boutons de filtres
        val searchX = 12
        val searchY = 48
        val totalSearchRowWidth = max(180, width - 215)
        val filterBtnW = 86
        val gap = 4
        val quickResetW = 20
        val searchWidth = max(70, totalSearchRowWidth - filterBtnW - gap - quickResetW - gap)
        val searchHeight = 18

        searchBox = EditBox(font, searchX, searchY, searchWidth, searchHeight, Component.literal(""))
        searchBox.setHint(Component.translatable("gui.cobblemon_jei.pokedex.search.hint"))
        searchBox.setResponder { applyFilters() }
        addRenderableWidget(searchBox)

        val filterBtnX = searchX + searchWidth + gap
        filterBtn = Button.builder(getFilterButtonText()) {
            openFilterModal()
        }.bounds(filterBtnX, searchY, filterBtnW, searchHeight).build()
        addRenderableWidget(filterBtn)

        val resetBtnX = filterBtnX + filterBtnW + gap
        quickResetFilterBtn = Button.builder(Component.literal("✕")) {
            resetFilters()
        }.bounds(resetBtnX, searchY, quickResetW, searchHeight).build()
        quickResetFilterBtn.visible = filterState.isActive()
        quickResetFilterBtn.tooltip = Tooltip.create(Component.literal("Réinitialiser tous les filtres"))
        addRenderableWidget(quickResetFilterBtn)

        // Bouton Radical Cobblemon Trainers (si le mod RCT est disponible ou séries chargées)
        if (fr.k0bus.cobblemon_jei.compat.rct.RctCompat.isAvailable()) {
            val rctBtnX = if (fr.k0bus.cobblemon_jei.leaderboard.CobblemonScoreboardService.isAvailable()) {
                width - 72
            } else {
                width - 48
            }
            val rctBtn = Button.builder(Component.literal("⚔")) {
                minecraft?.setScreen(fr.k0bus.cobblemon_jei.compat.rct.CobblemonRctProgressionScreen(this))
            }.bounds(rctBtnX, topY, 20, tabHeight).build()
            rctBtn.tooltip = Tooltip.create(Component.literal("Arbre de Progression Radical Trainers"))
            addRenderableWidget(rctBtn)
        }

        // Bouton Classement (uniquement si disponible sur le serveur)
        if (fr.k0bus.cobblemon_jei.leaderboard.CobblemonScoreboardService.isAvailable()) {
            val leaderboardBtn = Button.builder(Component.literal("🏆")) {
                minecraft?.setScreen(fr.k0bus.cobblemon_jei.leaderboard.CobblemonLeaderboardScreen(this))
            }.bounds(width - 48, topY, 20, tabHeight).build()
            leaderboardBtn.tooltip = Tooltip.create(Component.literal("Classement des Joueurs"))
            addRenderableWidget(leaderboardBtn)
        }

        // Bouton Fermer
        val closeBtn = Button.builder(Component.literal("✕")) {
            onClose()
        }.bounds(width - 24, topY, 18, tabHeight).build()
        addRenderableWidget(closeBtn)

        // Boîte de recherche pour le filtre de biomes
        biomeSearchBox = EditBox(font, 0, 0, 100, 16, Component.literal(""))
        biomeSearchBox.setHint(Component.translatable("gui.cobblemon_jei.pokedex.filter.biome_search"))
        biomeSearchBox.setResponder {
            biomeScrollOffset = 0.0
        }
        addWidget(biomeSearchBox)

        updateTabButtonStyles()
        updateFilterModalWidgetVisibility()
        updateTrackerButton()
        applyFilters()
    }

    private fun getTrackerButtonX(): Int {
        return if (currentTab == Tab.EVOLUTIONS) {
            togglePcEvolutionsBtn.x + togglePcEvolutionsBtn.width + 6
        } else {
            tabEvolutionsBtn.x + tabEvolutionsBtn.width + 8
        }
    }

    private fun getTrackerButtonText(): Component {
        return Component.literal("🎯 ${PokemonTrackingManager.getModeSummary()}")
    }

    fun updateTrackerButton() {
        if (::trackerBtn.isInitialized) {
            trackerBtn.x = getTrackerButtonX()
            trackerBtn.message = getTrackerButtonText()
            trackerBtn.tooltip = Tooltip.create(Component.literal("Mode Radar & Tracker : ${PokemonTrackingManager.getModeSummary()} (Cliquer pour gérer)"))
        }
    }

    private fun switchTab(tab: Tab) {
        currentTab = tab
        scrollOffset = 0.0
        togglePcEvolutionsBtn.visible = (currentTab == Tab.EVOLUTIONS)
        updateTabButtonStyles()
        updateTrackerButton()
        applyFilters()
    }

    private fun updateTabButtonStyles() {
        tabAllBtn.active = (currentTab != Tab.ALL)
        tabMissingBtn.active = (currentTab != Tab.MISSING)
        tabEvolutionsBtn.active = (currentTab != Tab.EVOLUTIONS)
    }

    private fun getTogglePcButtonText(): Component {
        return if (onlyPcFeasibleEvolutions) {
            Component.literal("⭐ Faisables PC : §aOUI§r")
        } else {
            Component.literal("⭐ Faisables PC : §7Tous§r")
        }
    }

    private fun getFilterButtonText(): Component {
        return if (filterState.isActive()) {
            Component.literal("⚡ Filtres §a(${filterState.activeCount()})§r")
        } else {
            Component.literal("⚡ Filtres")
        }
    }

    private fun updateFilterButton() {
        if (::filterBtn.isInitialized) {
            filterBtn.message = getFilterButtonText()
            filterBtn.tooltip = if (filterState.isActive()) {
                Tooltip.create(Component.literal("Filtres actifs : ${filterState.activeCount()} (Cliquer pour modifier)"))
            } else {
                Tooltip.create(Component.literal("Ouvrir le panneau de filtres avancés"))
            }
        }
        if (::quickResetFilterBtn.isInitialized) {
            quickResetFilterBtn.visible = filterState.isActive()
        }
    }

    private fun openFilterModal() {
        isFilterModalOpen = true
        searchBox.isFocused = false
        updateFilterModalWidgetVisibility()
    }

    private fun closeFilterModal() {
        isFilterModalOpen = false
        updateFilterModalWidgetVisibility()
    }

    private fun updateFilterModalWidgetVisibility() {
        val showBiome = isFilterModalOpen && activeFilterCategory == FilterCategory.BIOME
        if (::biomeSearchBox.isInitialized) {
            biomeSearchBox.visible = showBiome
            biomeSearchBox.active = showBiome
            if (!showBiome) {
                biomeSearchBox.isFocused = false
            }
        }
        if (::searchBox.isInitialized) {
            searchBox.active = !isFilterModalOpen
            if (isFilterModalOpen) {
                searchBox.isFocused = false
            }
        }
    }

    private fun normalizeText(text: String): String {
        val decomposed = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{M}"), "")
            .replace("œ", "oe")
            .replace("æ", "ae")
    }

    private fun resetFilters() {
        filterState.reset()
        applyFilters()
    }

    private fun getFilterModalBounds(): IntArray {
        val modalW = 410.coerceAtMost(width - 20)
        val modalH = 226.coerceAtMost(height - 20)
        val modalX = (width - modalW) / 2
        val modalY = (height - modalH) / 2
        return intArrayOf(modalX, modalY, modalW, modalH)
    }

    private fun applyFilters() {
        val query = searchBox.value.trim().lowercase()

        when (currentTab) {
            Tab.ALL -> {
                val stream = allTracked.filter { matchesQuery(it, query) && PokedexFilterManager.matches(it, filterState) }
                filteredTracked = PokedexFilterManager.sort(stream, filterState.selectedSort)
            }
            Tab.MISSING -> {
                val stream = allTracked.filter { (it.isMissing || it.isEncountered) && matchesQuery(it, query) && PokedexFilterManager.matches(it, filterState) }
                filteredTracked = PokedexFilterManager.sort(stream, filterState.selectedSort)
            }
            Tab.EVOLUTIONS -> {
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
                }
                filteredGoals = stream.filter { goal ->
                    matchesGoalQuery(goal, query)
                }
            }
        }

        recomputeMaxScroll()
        scrollOffset = scrollOffset.coerceIn(0.0, maxScroll)
        updateFilterButton()
    }

    private fun matchesQuery(item: TrackedPokemon, query: String): Boolean {
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

    private fun matchesGoalQuery(goal: EvolutionGoal, query: String): Boolean {
        if (query.isBlank()) return true
        val src = goal.sourceSpecies.translatedName.string.lowercase()
        val tgt = goal.targetSpecies.translatedName.string.lowercase()
        val req = goal.requirementsSummary.lowercase()
        return src.contains(query) || tgt.contains(query) || req.contains(query)
    }

    private fun getLeftAreaBounds(): IntArray {
        val x = 12
        val y = 73
        val w = max(180, width - 215)
        val h = max(80, height - y - 26)
        return intArrayOf(x, y, w, h)
    }

    private fun getRightPanelBounds(): IntArray {
        val panelX = width - 200
        val panelY = 73
        val panelW = 192
        val panelH = max(80, height - panelY - 26)
        return intArrayOf(panelX, panelY, panelW, panelH)
    }

    private fun getRightPanelTabRect(tab: RightPanelTab): IntArray {
        val (panelX, panelY, panelW, _) = getRightPanelBounds()
        val x0 = panelX + 6
        val tabsW = panelW - 12
        val gap = 2
        val row1H = 13
        val row2H = 13
        val row1Y = panelY + 74
        val row2Y = panelY + 89
        val tabW = (tabsW - 3 * gap) / 4

        return when (tab) {
            RightPanelTab.DESCRIPTION -> intArrayOf(x0, row1Y, tabW, row1H)
            RightPanelTab.FORMS -> intArrayOf(x0 + tabW + gap, row1Y, tabW, row1H)
            RightPanelTab.ABILITIES -> intArrayOf(x0 + (tabW + gap) * 2, row1Y, tabW, row1H)
            RightPanelTab.STATS -> {
                val x = x0 + (tabW + gap) * 3
                intArrayOf(x, row1Y, (x0 + tabsW) - x, row1H)
            }
            RightPanelTab.EVOLUTIONS -> intArrayOf(x0, row2Y, tabW, row2H)
            RightPanelTab.DROPS -> intArrayOf(x0 + tabW + gap, row2Y, tabW, row2H)
            RightPanelTab.PASTURE -> intArrayOf(x0 + (tabW + gap) * 2, row2Y, tabW, row2H)
            RightPanelTab.SPAWNS -> {
                val x = x0 + (tabW + gap) * 3
                intArrayOf(x, row2Y, (x0 + tabsW) - x, row2H)
            }
        }
    }

    private fun truncateString(str: String, maxW: Int): String {
        if (font.width(str) <= maxW) return str
        var s = str
        while (s.isNotEmpty() && font.width("$s…") > maxW) {
            s = s.dropLast(1)
        }
        return "$s…"
    }

    private fun recomputeMaxScroll() {
        val (_, _, w, h) = getLeftAreaBounds()
        if (currentTab == Tab.EVOLUTIONS) {
            val totalH = filteredGoals.size * (evoCardHeight + 4)
            maxScroll = max(0.0, (totalH - h).toDouble())
        } else {
            val usableW = w - 10 // réserve la scrollbar
            val cols = max(1, usableW / (cardWidth + cardGap))
            val rows = (filteredTracked.size + cols - 1) / cols
            val totalH = rows * (cardHeight + cardGap)
            maxScroll = max(0.0, (totalH - h).toDouble())
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Fond sombre moderne
        guiGraphics.fill(0, 0, width, height, 0xE8101217.toInt())

        // Bande supérieure d'en-tête (englobe le titre, les boutons d'onglets et la barre de recherche)
        guiGraphics.fill(0, 0, width, 70, 0xFF14171E.toInt())
        guiGraphics.fill(0, 69, width, 70, 0xFF282D3C.toInt())

        // Titre et statistiques globales
        val stats = PokemonTrackerService.getStats()
        val title = "§e§lPOKÉDEX TRACKER§r §8|§r Complétion : §a${String.format("%.1f", stats.completionPercent)}%§r"
        guiGraphics.drawString(font, title, 14, 10, 0xFFFFFFFF.toInt(), false)

        val statsSummary = "Capturés: §a${stats.caughtCount}§r §8/§r Vus: §6${stats.encounteredCount}§r §8/§r Manquants: §c${stats.missingCount}§r §8|§r Prêts PC: §b${stats.readyEvolutionsCount}§r"
        guiGraphics.drawString(font, statsSummary, width - font.width(statsSummary) - 14, 10, 0xFFBBBBBB.toInt(), false)

        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val isAnyModalOpen = isZoomModalOpen || isFilterModalOpen || isTrackerModalOpen
        val contentMouseX = if (isAnyModalOpen) -1000 else mouseX
        val contentMouseY = if (isAnyModalOpen) -1000 else mouseY

        // Rendu des zones principales
        renderLeftContent(guiGraphics, contentMouseX, contentMouseY)
        renderRightPanel(guiGraphics, contentMouseX, contentMouseY)
        renderBottomProgressBar(guiGraphics, contentMouseX, contentMouseY, stats)

        // Rendu de la visionneuse Grand Format (au-dessus de tout)
        renderZoomModal(guiGraphics, mouseX, mouseY)

        // Rendu de la modale de filtres avancés
        renderFilterModal(guiGraphics, mouseX, mouseY)

        // Rendu de la modale du Tracker
        renderTrackerModal(guiGraphics, mouseX, mouseY)
    }

    private fun renderBottomProgressBar(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, stats: PokedexTrackerStats) {
        val total = max(1, stats.totalSpecies)
        val caught = stats.caughtCount
        val seenOnly = stats.encounteredCount
        val totalSeen = caught + seenOnly

        val caughtPct = (caught.toDouble() / total) * 100.0
        val seenOnlyPct = (seenOnly.toDouble() / total) * 100.0
        val totalSeenPct = (totalSeen.toDouble() / total) * 100.0
        val missingPct = (stats.missingCount.toDouble() / total) * 100.0

        val footerY = height - 23
        val barX = 14
        val barW = max(50, width - 28)
        val barH = 5
        val barY = footerY + 15
        val textY = footerY + 4

        // 1. Fond et bordure du bandeau inférieur
        guiGraphics.fill(0, footerY, width, height, 0xFF14171E.toInt())
        guiGraphics.fill(0, footerY, width, footerY + 1, 0xFF282D3C.toInt())

        // 2. Chiffres & icônes sans texte superflu
        var curX = 14

        // Icône Pokéball + Valeur Capturé (Vert)
        val pokeStack = try {
            ItemStack(CobblemonItems.POKE_BALL)
        } catch (_: Throwable) {
            ItemStack.EMPTY
        }

        if (!pokeStack.isEmpty) {
            val pose = guiGraphics.pose()
            pose.pushPose()
            pose.translate(curX.toFloat(), (textY - 2).toFloat(), 0f)
            pose.scale(0.68f, 0.68f, 1.0f)
            guiGraphics.renderItem(pokeStack, 0, 0)
            pose.popPose()
            curX += 13
        } else {
            guiGraphics.drawString(font, "●", curX, textY, 0xFF2ECC71.toInt(), false)
            curX += 9
        }

        val caughtStr = "§a§l$caught§r §7(${String.format("%.1f", caughtPct)}%)§r"
        guiGraphics.drawString(font, caughtStr, curX, textY, 0xFFFFFFFF.toInt(), false)
        curX += font.width(caughtStr) + 16

        // Icône Œil + Vus uniquement + Somme (Total vus)
        val eyeIcon = "§6👁§r"
        guiGraphics.drawString(font, eyeIcon, curX, textY, 0xFFFFFFFF.toInt(), false)
        curX += font.width(eyeIcon) + 4

        val encStr = "§6§l$seenOnly§r §8(§7Total: §e§l$totalSeen§r §7- ${String.format("%.1f", totalSeenPct)}%§8)§r"
        guiGraphics.drawString(font, encStr, curX, textY, 0xFFFFFFFF.toInt(), false)

        // Valeurs à droite : Total et Non découverts
        val totalStr = "§8/§r §f§l${stats.totalSpecies}§r §7(100%)§r"
        val totalX = width - 14 - font.width(totalStr)
        guiGraphics.drawString(font, totalStr, totalX, textY, 0xFFFFFFFF.toInt(), false)

        val missingStr = "§c✕ §l${stats.missingCount}§r §7(${String.format("%.1f", missingPct)}%)§r"
        val missingX = totalX - font.width(missingStr) - 14
        if (missingX > curX + font.width(encStr) + 16) {
            guiGraphics.drawString(font, missingStr, missingX, textY, 0xFFFFFFFF.toInt(), false)
        }

        // 3. Barre de progression à 2 segments (Capturé: Vert / Vu: Orange)
        guiGraphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF282D3C.toInt())
        guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF141720.toInt())

        var caughtW = ((caught.toDouble() / total) * barW).toInt()
        var encW = ((seenOnly.toDouble() / total) * barW).toInt()

        if (caught > 0 && caughtW == 0) caughtW = 1
        if (seenOnly > 0 && encW == 0) encW = 1
        if (caughtW + encW > barW) {
            encW = max(0, barW - caughtW)
        }

        // Segment vert (Capturé)
        if (caughtW > 0) {
            guiGraphics.fill(barX, barY, barX + caughtW, barY + barH, 0xFF2ECC71.toInt())
            guiGraphics.fill(barX, barY, barX + caughtW, barY + 1, 0xFF55FF55.toInt())
        }

        // Segment orange (Vu uniquement)
        if (encW > 0) {
            val encStartX = barX + caughtW
            guiGraphics.fill(encStartX, barY, encStartX + encW, barY + barH, 0xFFE67E22.toInt())
            guiGraphics.fill(encStartX, barY, encStartX + encW, barY + 1, 0xFFFFAA00.toInt())
        }

        // 4. Infobulle au survol
        if (mouseX >= barX && mouseX <= barX + barW && mouseY >= footerY && mouseY <= height) {
            val tooltip = listOf(
                Component.literal("§e§lProgression globale du Pokédex§r"),
                Component.literal("§a✔ Capturés : §f$caught §7(${String.format("%.1f", caughtPct)}%)§r"),
                Component.literal("§6👁 Vus uniquement : §f$seenOnly §7(${String.format("%.1f", seenOnlyPct)}%)§r"),
                Component.literal("§e👁+✔ Total vus (Vus + Capturés) : §f$totalSeen §7(${String.format("%.1f", totalSeenPct)}%)§r"),
                Component.literal("§c✕ Non découverts : §f${stats.missingCount} §7(${String.format("%.1f", missingPct)}%)§r"),
                Component.literal("§8Total d'espèces possibles : §f${stats.totalSpecies}§r")
            )
            guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY)
        }
    }

    private fun renderLeftContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val (leftX, topY, areaW, areaH) = getLeftAreaBounds()

        // Cadre de fond de la zone gauche
        guiGraphics.fill(leftX - 1, topY - 1, leftX + areaW + 1, topY + areaH + 1, 0xFF1D2029.toInt())
        guiGraphics.fill(leftX, topY, leftX + areaW, topY + areaH, 0xFF12141A.toInt())

        // Découpage d'affichage (Scissor)
        guiGraphics.enableScissor(leftX, topY, leftX + areaW, topY + areaH)

        if (currentTab == Tab.EVOLUTIONS) {
            renderEvolutionsTab(guiGraphics, leftX + 4, topY + 4, areaW - 14, areaH, mouseX, mouseY)
        } else {
            renderGridTab(guiGraphics, leftX + 4, topY + 4, areaW - 14, areaH, mouseX, mouseY)
        }

        guiGraphics.disableScissor()

        // Scrollbar
        renderScrollbar(guiGraphics, leftX + areaW - 6, topY + 2, 4, areaH - 4)
    }

    private fun renderGridTab(
        guiGraphics: GuiGraphics,
        startX: Int,
        startY: Int,
        usableW: Int,
        usableH: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val cols = max(1, usableW / (cardWidth + cardGap))
        val scrolledY = startY - scrollOffset.toInt()

        if (filteredTracked.isEmpty()) {
            val emptyMsg = "Aucun Pokémon ne correspond à votre recherche."
            guiGraphics.drawCenteredString(font, emptyMsg, startX + usableW / 2, startY + 40, 0xFF888888.toInt())
            return
        }

        var hoveredPokemon: TrackedPokemon? = null

        for ((index, item) in filteredTracked.withIndex()) {
            val col = index % cols
            val row = index / cols
            val cardX = startX + col * (cardWidth + cardGap)
            val cardY = scrolledY + row * (cardHeight + cardGap)

            // Optimisation : saut si hors de l'écran visible
            if (cardY + cardHeight < startY || cardY > startY + usableH) {
                continue
            }

            val isSelected = (selectedTracked?.species == item.species)
            val isHovered = (mouseX >= cardX && mouseX < cardX + cardWidth && mouseY >= cardY && mouseY < cardY + cardHeight)
            if (isHovered && mouseY in startY until startY + usableH) {
                hoveredPokemon = item
            }

            // Couleurs selon l'état
            val (bgColor, borderColor) = when (item.status) {
                PokedexStatus.CAUGHT -> Pair(0x44143D1A, 0xFF2ECC71.toInt())
                PokedexStatus.ENCOUNTERED -> Pair(0x444D3A14, 0xFFE67E22.toInt())
                PokedexStatus.MISSING -> Pair(0x441E2028, 0xFF353A47.toInt())
            }

            // Fond et bordure
            guiGraphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, bgColor)
            guiGraphics.fill(cardX, cardY, cardX + cardWidth, cardY + 1, borderColor)
            guiGraphics.fill(cardX, cardY + cardHeight - 1, cardX + cardWidth, cardY + cardHeight, borderColor)
            guiGraphics.fill(cardX, cardY, cardX + 1, cardY + cardHeight, borderColor)
            guiGraphics.fill(cardX + cardWidth - 1, cardY, cardX + cardWidth, cardY + cardHeight, borderColor)

            if (isSelected) {
                // Surlignage bleu
                guiGraphics.fill(cardX - 1, cardY - 1, cardX + cardWidth + 1, cardY + 1, 0xFF3498DB.toInt())
                guiGraphics.fill(cardX - 1, cardY + cardHeight - 1, cardX + cardWidth + 1, cardY + cardHeight + 1, 0xFF3498DB.toInt())
                guiGraphics.fill(cardX - 1, cardY - 1, cardX + 1, cardY + cardHeight + 1, 0xFF3498DB.toInt())
                guiGraphics.fill(cardX + cardWidth - 1, cardY - 1, cardX + cardWidth + 1, cardY + cardHeight + 1, 0xFF3498DB.toInt())
            } else if (isHovered) {
                guiGraphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0x22FFFFFF)
            }

            // Numéro Pokédex
            val dexLabel = String.format("#%03d", item.nationalDexNumber)
            guiGraphics.drawString(font, dexLabel, cardX + 3, cardY + 3, 0xFF888E9B.toInt(), false)

            // Badge d'état dans le coin
            when (item.status) {
                PokedexStatus.CAUGHT -> {
                    guiGraphics.drawString(font, "✔", cardX + cardWidth - 9, cardY + 3, 0xFF2ECC71.toInt(), false)
                }
                PokedexStatus.ENCOUNTERED -> {
                    guiGraphics.drawString(font, "👁", cardX + cardWidth - 11, cardY + 3, 0xFFE67E22.toInt(), false)
                }
                PokedexStatus.MISSING -> {
                    guiGraphics.drawString(font, "✕", cardX + cardWidth - 9, cardY + 3, 0xFF5A6070.toInt(), false)
                }
            }

            // Sprite de Pokémon
            val stack = getItemStack(item.species)
            if (!stack.isEmpty) {
                val spriteX = cardX + (cardWidth - 16) / 2
                val spriteY = cardY + 14
                guiGraphics.renderItem(stack, spriteX, spriteY)
            }

            // Nom tronqué au bas
            val rawName = item.species.translatedName.string
            val shortName = if (rawName.length > 6) rawName.substring(0, 5) + "…" else rawName
            val nameColor = if (item.isCaught) 0xFFE0E0E0.toInt() else if (item.isEncountered) 0xFFD4AC0D.toInt() else 0xFF7A8090.toInt()
            guiGraphics.drawCenteredString(font, shortName, cardX + cardWidth / 2, cardY + cardHeight - 11, nameColor)

            // Indicateur si possédé dans le PC
            if (item.ownedCount > 0) {
                val countText = "x${item.ownedCount}"
                guiGraphics.fill(cardX + 2, cardY + cardHeight - 10, cardX + 3 + font.width(countText), cardY + cardHeight - 2, 0xCC113311.toInt())
                guiGraphics.drawString(font, countText, cardX + 3, cardY + cardHeight - 10, 0xFF55FF55.toInt(), false)
            }
        }

        // Tooltip au survol
        hoveredPokemon?.let { item ->
            val tooltip = mutableListOf<Component>()
            val dexPrefix = String.format("#%04d ", item.nationalDexNumber)
            val titleComp = Component.literal(dexPrefix).withStyle(ChatFormatting.GOLD)
                .append(item.species.translatedName.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE))
            tooltip.add(titleComp)

            val typeDesc = item.species.standardForm.primaryType.displayName.copy()
            item.species.standardForm.secondaryType?.let { sec ->
                typeDesc.append(Component.literal(" / ")).append(sec.displayName)
            }
            tooltip.add(Component.literal("Types : ").withStyle(ChatFormatting.DARK_GRAY).append(typeDesc))

            val statusText = when (item.status) {
                PokedexStatus.CAUGHT -> Component.literal("✔ Capturé / Possédé").withStyle(ChatFormatting.GREEN)
                PokedexStatus.ENCOUNTERED -> Component.literal("👁 Rencontré (non capturé)").withStyle(ChatFormatting.GOLD)
                PokedexStatus.MISSING -> Component.literal("❌ Non découvert").withStyle(ChatFormatting.RED)
            }
            tooltip.add(statusText)

            if (item.ownedCount > 0) {
                tooltip.add(Component.literal("Dans vos réserves (${item.ownedCount}) :").withStyle(ChatFormatting.AQUA))
                for (info in item.ownedInstances.take(4)) {
                    tooltip.add(Component.literal(" • ${info.location} (Niv. ${info.level})").withStyle(ChatFormatting.GRAY))
                }
            }
            guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY)
        }
    }

    private fun renderEvolutionsTab(
        guiGraphics: GuiGraphics,
        startX: Int,
        startY: Int,
        usableW: Int,
        usableH: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val scrolledY = startY - scrollOffset.toInt()

        if (filteredGoals.isEmpty()) {
            val emptyMsg = "Aucune évolution manquante trouvée."
            guiGraphics.drawCenteredString(font, emptyMsg, startX + usableW / 2, startY + 40, 0xFF888888.toInt())
            return
        }

        var hoveredGoal: EvolutionGoal? = null

        for ((index, goal) in filteredGoals.withIndex()) {
            val cardY = scrolledY + index * (evoCardHeight + 4)
            if (cardY + evoCardHeight < startY || cardY > startY + usableH) {
                continue
            }

            val cardW = usableW - 6
            val isSelected = (selectedGoal == goal)
            val isHovered = (mouseX >= startX && mouseX < startX + cardW && mouseY >= cardY && mouseY < cardY + evoCardHeight)
            if (isHovered && mouseY in startY until startY + usableH) {
                hoveredGoal = goal
            }

            // Couleurs selon faisabilité
            val (bgColor, borderColor, badgeText, badgeColor) = when (goal.feasibility) {
                EvolutionFeasibility.READY_TO_EVOLVE -> Quad(
                    0x44143D1A,
                    0xFF2ECC71.toInt(),
                    "⭐ PRÊT À ÉVOLUER !",
                    0xFF2ECC71.toInt()
                )
                EvolutionFeasibility.CAN_EVOLVE_WITH_PC -> Quad(
                    0x443D3014,
                    0xFFE67E22.toInt(),
                    "⏳ EN STOCK DANS LE PC",
                    0xFFE67E22.toInt()
                )
                EvolutionFeasibility.MISSING_BASE_POKEMON -> Quad(
                    0x331C1E26,
                    0xFF2E3342.toInt(),
                    "POKÉMON DE BASE REQUIS",
                    0xFF7D8394.toInt()
                )
            }

            guiGraphics.fill(startX, cardY, startX + cardW, cardY + evoCardHeight, bgColor)
            guiGraphics.fill(startX, cardY, startX + cardW, cardY + 1, borderColor)
            guiGraphics.fill(startX, cardY + evoCardHeight - 1, startX + cardW, cardY + evoCardHeight, borderColor)
            guiGraphics.fill(startX, cardY, startX + 1, cardY + evoCardHeight, borderColor)
            guiGraphics.fill(startX + cardW - 1, cardY, startX + cardW, cardY + evoCardHeight, borderColor)

            if (isSelected) {
                guiGraphics.fill(startX, cardY, startX + 3, cardY + evoCardHeight, 0xFF3498DB.toInt())
            }

            // 1. Pokémon Source
            val srcStack = getItemStack(goal.sourceSpecies, goal.sourceForm)
            if (!srcStack.isEmpty) {
                guiGraphics.renderItem(srcStack, startX + 6, cardY + 8)
            }
            val srcName = goal.sourceSpecies.translatedName.string
            val formNameStr = if (goal.sourceForm != goal.sourceSpecies.standardForm && goal.sourceForm.name.isNotBlank() && goal.sourceForm.name != "normal") {
                " (${goal.sourceForm.name.replaceFirstChar(Char::titlecase)})"
            } else ""
            val genderBadge = when (goal.requiredGender) {
                Gender.FEMALE -> " §d♀§r"
                Gender.MALE -> " §9♂§r"
                else -> ""
            }
            guiGraphics.drawString(font, srcName + formNameStr + genderBadge, startX + 26, cardY + 8, 0xFFE0E0E0.toInt(), false)

            val locText = if (!goal.hasFormMatch) {
                val fName = goal.sourceForm.name.replaceFirstChar(Char::titlecase)
                "§c✕ Mauvaise forme (0 $fName)§r"
            } else if (!goal.hasGenderMatch) {
                "§c✕ Mauvais sexe (0/${goal.ownedSourcePokemons.size})§r"
            } else if (goal.hasSourceInPC) {
                "§a✔ ${goal.sourceLocationSummary}§r"
            } else {
                "§7✕ Non possédé§r"
            }
            guiGraphics.drawString(font, locText, startX + 26, cardY + 20, 0xFFAAAAAA.toInt(), false)

            // 2. Flèche d'évolution et conditions au centre
            val arrowX = startX + 155
            guiGraphics.drawString(font, "➔", arrowX, cardY + 14, 0xFF54A0FF.toInt(), false)

            // Objets requis
            var reqItemX = arrowX + 18
            if (goal.requiredItems.isNotEmpty()) {
                for (reqStack in goal.requiredItems.take(2)) {
                    guiGraphics.renderItem(reqStack, reqItemX, cardY + 8)
                    val hasThis = !goal.missingItems.any { it.item == reqStack.item }
                    val checkMark = if (hasThis) "§a✔§r" else "§c✕§r"
                    guiGraphics.drawString(font, checkMark, reqItemX + 13, cardY + 22, 0xFFFFFFFF.toInt(), false)
                    reqItemX += 24
                }
            }

            // Badge Capacité / Move
            val moveBadge = if (goal.requiredMove != null) {
                val mvName = Moves.getByName(goal.requiredMove)?.displayName?.string ?: goal.requiredMove
                when (goal.moveStatus) {
                    MoveRequirementStatus.KNOWN -> "§a⚡ $mvName§r"
                    MoveRequirementStatus.RELEARNABLE -> "§a⚡ $mvName (Menu)§r"
                    MoveRequirementStatus.TM_IN_INVENTORY -> "§e⚡ CT $mvName (Sac)§r"
                    MoveRequirementStatus.TM_MISSING -> "§c⚡ CT $mvName (Manquante)§r"
                    MoveRequirementStatus.LEVEL_UP -> "§6⚡ $mvName (Niv. ${goal.moveLearnLevel})§r"
                    else -> "§c⚡ $mvName§r"
                }
            } else null

            // Badge Action spéciale (Pas, Combat, etc.)
            val specialActionBadge = if (goal.stepsRequired != null) {
                "§b🚶 ${goal.stepsRequired} pas§r"
            } else if (goal.useMoveCount != null && goal.requiredMove != null) {
                val mvName = Moves.getByName(goal.requiredMove)?.displayName?.string ?: goal.requiredMove
                "§6⚔ $mvName (${goal.useMoveCount}x)§r"
            } else null

            // Résumé texte de la condition
            val conditionStr = moveBadge ?: specialActionBadge ?: goal.requirementsSummary
            val shortCond = if (conditionStr.length > 34) conditionStr.substring(0, 32) + "…" else conditionStr
            guiGraphics.drawString(font, shortCond, reqItemX + 2, cardY + 10, 0xFFCCCCCC.toInt(), false)

            val levelSummary = if (goal.minLevel != null) {
                if (goal.isLevelMet) "§aNiv. ${goal.minLevel} atteint§r" else "§cNiv. ${goal.minLevel} requis§r"
            } else if (goal.requiredGender != null) {
                if (goal.hasGenderMatch) "§aSexe requis validé§r" else "§cSpécimen ${if (goal.requiredGender == Gender.FEMALE) "Femelle ♀" else "Mâle ♂"} requis§r"
            } else {
                goal.recipe.evolutionVariant
            }
            guiGraphics.drawString(font, levelSummary, reqItemX + 2, cardY + 22, 0xFFAAAAAA.toInt(), false)

            // 3. Pokémon Cible
            val tgtX = startX + cardW - 130
            val tgtStack = getItemStack(goal.targetSpecies, goal.targetForm)
            if (!tgtStack.isEmpty) {
                guiGraphics.renderItem(tgtStack, tgtX, cardY + 8)
            }
            val tgtName = goal.targetSpecies.translatedName.string
            guiGraphics.drawString(font, tgtName, tgtX + 20, cardY + 8, 0xFFFFEAA7.toInt(), false)
            val tgtDex = String.format("#%03d (Manquant)", goal.targetSpecies.nationalPokedexNumber)
            guiGraphics.drawString(font, tgtDex, tgtX + 20, cardY + 20, 0xFFE74C3C.toInt(), false)

            // Badge de statut en haut à droite
            val badgeX = startX + cardW - font.width(badgeText) - 6
            guiGraphics.drawString(font, badgeText, badgeX, cardY + 34, badgeColor, false)
        }

        hoveredGoal?.let { goal ->
            renderGoalTooltip(guiGraphics, goal, mouseX, mouseY)
        }
    }

    private fun renderGoalTooltip(guiGraphics: GuiGraphics, goal: EvolutionGoal, mouseX: Int, mouseY: Int) {
        val tooltip = mutableListOf<Component>()
        tooltip.add(
            Component.literal("Évolution : ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(goal.sourceSpecies.translatedName.string).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" ➔ ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(goal.targetSpecies.translatedName.string).withStyle(ChatFormatting.YELLOW))
        )

        val feasText = when (goal.feasibility) {
            EvolutionFeasibility.READY_TO_EVOLVE -> "⭐ Tout est prêt ! Évolution faisable dès maintenant."
            EvolutionFeasibility.CAN_EVOLVE_WITH_PC -> {
                if (!goal.hasFormMatch) "⏳ Pokémon présent mais pas sous la forme requise !"
                else if (!goal.hasGenderMatch) "⏳ Pokémon présent mais aucun spécimen du sexe requis !"
                else if (goal.moveStatus == MoveRequirementStatus.TM_MISSING) "⏳ Pokémon présent, mais la CT requise est manquante."
                else "⏳ Pokémon présent dans votre PC/équipe, prérequis manquants."
            }
            EvolutionFeasibility.MISSING_BASE_POKEMON -> {
                if (!goal.hasFormMatch) {
                    val fName = goal.sourceForm.name.replaceFirstChar(Char::titlecase)
                    "❌ Vous devez capturer la forme spécifique : $fName."
                } else {
                    "❌ Vous devez d'abord capturer le Pokémon de base."
                }
            }
        }
        tooltip.add(Component.literal(feasText).withStyle(if (goal.feasibility == EvolutionFeasibility.READY_TO_EVOLVE) ChatFormatting.GREEN else ChatFormatting.GOLD))

        // Section Forme requise
        if (goal.sourceForm != goal.sourceSpecies.standardForm && goal.sourceForm.name.isNotBlank() && goal.sourceForm.name != "normal") {
            tooltip.add(Component.empty())
            val fName = goal.sourceForm.name.replaceFirstChar(Char::titlecase)
            if (goal.hasFormMatch) {
                tooltip.add(Component.literal("Forme requise : $fName (✔ Spécimen valide en stock)").withStyle(ChatFormatting.GREEN))
            } else {
                tooltip.add(Component.literal("Forme requise : $fName (✕ Aucun spécimen de cette forme en stock !)").withStyle(ChatFormatting.RED))
            }
        }

        // Section Sexe requis
        if (goal.requiredGender != null) {
            tooltip.add(Component.empty())
            val gName = if (goal.requiredGender == Gender.FEMALE) "Femelle ♀" else "Mâle ♂"
            if (goal.hasGenderMatch) {
                tooltip.add(Component.literal("Sexe requis : $gName (✔ Spécimen valide en stock)").withStyle(ChatFormatting.GREEN))
            } else {
                tooltip.add(Component.literal("Sexe requis : $gName (✕ Aucun spécimen de ce sexe en stock !)").withStyle(ChatFormatting.RED))
            }
        }

        // Section Capacité / Move / CT
        if (goal.requiredMove != null) {
            tooltip.add(Component.empty())
            val mvName = Moves.getByName(goal.requiredMove)?.displayName?.string ?: goal.requiredMove
            tooltip.add(Component.literal("Capacité requise : ").withStyle(ChatFormatting.GRAY).append(Component.literal(mvName).withStyle(ChatFormatting.WHITE)))
            val moveExpl = when (goal.moveStatus) {
                MoveRequirementStatus.KNOWN -> Component.literal(" ✔ Déjà apprise par votre Pokémon").withStyle(ChatFormatting.GREEN)
                MoveRequirementStatus.RELEARNABLE -> Component.literal(" ✔ Réapprenable immédiatement (menu des capacités du Pokémon)").withStyle(ChatFormatting.GREEN)
                MoveRequirementStatus.TM_IN_INVENTORY -> Component.literal(" ✔ CT possédée dans votre inventaire (prêt à apprendre)").withStyle(ChatFormatting.YELLOW)
                MoveRequirementStatus.TM_MISSING -> Component.literal(" ✕ CT manquante (vous devez trouver ou fabriquer la CT : $mvName)").withStyle(ChatFormatting.RED)
                MoveRequirementStatus.LEVEL_UP -> Component.literal(" ⏳ S'apprend par montée de niveau (Niveau ${goal.moveLearnLevel})").withStyle(ChatFormatting.GOLD)
                else -> Component.literal(" ✕ Capacité non apprise").withStyle(ChatFormatting.RED)
            }
            tooltip.add(moveExpl)
        } else if (goal.requiredMoveType != null) {
            tooltip.add(Component.empty())
            tooltip.add(Component.literal("Capacité requise de type : ").withStyle(ChatFormatting.GRAY).append(Component.literal(goal.requiredMoveType.replaceFirstChar { it.uppercase() }).withStyle(ChatFormatting.LIGHT_PURPLE)))
            if (goal.moveStatus == MoveRequirementStatus.KNOWN) {
                tooltip.add(Component.literal(" ✔ Capacité du type requis déjà connue").withStyle(ChatFormatting.GREEN))
            } else {
                tooltip.add(Component.literal(" ✕ Aucune capacité de ce type connue").withStyle(ChatFormatting.RED))
            }
        }

        // Actions & Compteurs spéciaux
        if (goal.specialActionSummary != null) {
            tooltip.add(Component.empty())
            tooltip.add(Component.literal("Actions requises :").withStyle(ChatFormatting.GRAY))
            if (goal.stepsRequired != null) {
                tooltip.add(Component.literal(" • 🚶 Marcher ${goal.stepsRequired} pas avec le Pokémon sorti").withStyle(ChatFormatting.AQUA))
            }
            if (goal.useMoveCount != null && goal.requiredMove != null) {
                val mvName = Moves.getByName(goal.requiredMove)?.displayName?.string ?: goal.requiredMove
                tooltip.add(Component.literal(" • ⚔ Utiliser la capacité $mvName ${goal.useMoveCount} fois en combat").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.criticalHits != null) {
                tooltip.add(Component.literal(" • 🎯 Infliger ${goal.recipe.criticalHits} coups critiques en un seul combat").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.damageTaken != null) {
                tooltip.add(Component.literal(" • 🛡 Subir ≥ ${goal.recipe.damageTaken} dégâts sans tomber K.O.").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.recoilDamage != null) {
                tooltip.add(Component.literal(" • 💥 Subir ≥ ${goal.recipe.recoilDamage} dégâts de contrecoup").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.defeatAmount != null) {
                tooltip.add(Component.literal(" • ⚔ Vaincre ${goal.recipe.defeatAmount} ${goal.recipe.defeatTarget ?: "adversaires"}").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.partyMember != null) {
                tooltip.add(Component.literal(" • 👥 Avoir ${goal.recipe.partyMember} dans l'équipe").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.moonPhase != null) {
                tooltip.add(Component.literal(" • 🌙 Phase lunaire requise : ${goal.recipe.moonPhase}").withStyle(ChatFormatting.AQUA))
            }
        }

        tooltip.add(Component.empty())
        tooltip.add(Component.literal("État du Pokémon source :").withStyle(ChatFormatting.GRAY))
        if (goal.hasSourceInPC) {
            for (owned in goal.ownedSourcePokemons) {
                val gSym = if (owned.gender == Gender.FEMALE) "§d♀§r" else if (owned.gender == Gender.MALE) "§9♂§r" else "§7⚲§r"
                tooltip.add(Component.literal(" • ${owned.location} - Niv. ${owned.level} ($gSym) - Bonheur: ${owned.friendship}").withStyle(ChatFormatting.AQUA))
            }
        } else {
            tooltip.add(Component.literal(" • Aucun spécimen dans l'équipe ou le PC").withStyle(ChatFormatting.RED))
        }

        if (goal.requiredItems.isNotEmpty()) {
            tooltip.add(Component.empty())
            tooltip.add(Component.literal("Objets requis :").withStyle(ChatFormatting.GRAY))
            for (item in goal.requiredItems) {
                val has = !goal.missingItems.any { it.item == item.item }
                val itemLine = if (has) {
                    Component.literal(" ✔ ${item.hoverName.string} (Possédé dans l'inventaire)").withStyle(ChatFormatting.GREEN)
                } else {
                    Component.literal(" ✕ ${item.hoverName.string} (Manquant)").withStyle(ChatFormatting.RED)
                }
                tooltip.add(itemLine)
            }
        }

        if (goal.additionalConditions.isNotEmpty()) {
            tooltip.add(Component.empty())
            tooltip.add(Component.literal("Autres conditions :").withStyle(ChatFormatting.GRAY))
            for (cond in goal.additionalConditions) {
                tooltip.add(Component.literal(" • $cond").withStyle(ChatFormatting.DARK_AQUA))
            }
        }

        guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY)
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        if (maxScroll <= 0) return

        guiGraphics.fill(x, y, x + w, y + h, 0x44000000)
        val thumbH = max(16, (h * (h / (h + maxScroll))).toInt())
        val thumbY = y + ((h - thumbH) * (scrollOffset / maxScroll)).toInt()

        val thumbColor = if (isDraggingScrollbar) 0xFF54A0FF.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(x, thumbY, x + w, thumbY + thumbH, thumbColor)
    }

    private fun renderRightPanel(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val (panelX, panelY, panelW, panelH) = getRightPanelBounds()

        // Fond et bordures du panneau droit
        guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF242834.toInt())
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF14161F.toInt())

        val currentTracked = selectedTracked ?: allTracked.firstOrNull() ?: return
        val sp = currentTracked.species

        val activeForm = selectedForm ?: sp.standardForm
        val isShinyActive = isFormShiny(activeForm)

        // 1. Cadre du Pokémon et sprite (cliquable pour Grand Format)
        val previewSize = 36
        val previewX = panelX + (panelW - previewSize) / 2
        val previewY = panelY + 6

        val isPreviewHovered = mouseX >= previewX - 4 && mouseX <= previewX + previewSize + 4 &&
                mouseY >= previewY - 4 && mouseY <= previewY + previewSize + 4
        topPreviewRect = intArrayOf(previewX - 4, previewY - 4, previewSize + 8, previewSize + 8)

        val previewBg = if (isPreviewHovered) 0xFF282F42.toInt() else 0xFF1D212E.toInt()
        val previewBorder = if (isPreviewHovered) 0xFF54A0FF.toInt() else 0xFF353B4F.toInt()

        guiGraphics.fill(previewX - 4, previewY - 4, previewX + previewSize + 4, previewY + previewSize + 4, previewBg)
        guiGraphics.fill(previewX - 4, previewY - 4, previewX + previewSize + 4, previewY - 3, previewBorder)
        guiGraphics.fill(previewX - 4, previewY + previewSize + 3, previewX + previewSize + 4, previewY + previewSize + 4, previewBorder)
        guiGraphics.fill(previewX - 4, previewY - 4, previewX - 3, previewY + previewSize + 4, previewBorder)
        guiGraphics.fill(previewX + previewSize + 3, previewY - 4, previewX + previewSize + 4, previewY + previewSize + 4, previewBorder)

        // Boutons compacts à gauche et à droite du cadre de preview
        val starBtnX = previewX - 20
        val starBtnY = previewY + 10
        val isStarHovered = mouseX >= starBtnX && mouseX <= starBtnX + 14 && mouseY >= starBtnY && mouseY <= starBtnY + 14
        val isInWatchlist = PokemonTrackingManager.isInWatchlist(sp)
        watchlistHeaderButtonRect = intArrayOf(starBtnX, starBtnY, 14, 14)
        val starBg = if (isInWatchlist) 0xFF4A3B18.toInt() else if (isStarHovered) 0xFF2A2E3D.toInt() else 0xFF181B26.toInt()
        val starBorder = if (isInWatchlist) 0xFFF1C40F.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(starBtnX, starBtnY, starBtnX + 14, starBtnY + 14, starBg)
        guiGraphics.fill(starBtnX, starBtnY, starBtnX + 14, starBtnY + 1, starBorder)
        guiGraphics.fill(starBtnX, starBtnY + 13, starBtnX + 14, starBtnY + 14, starBorder)
        guiGraphics.fill(starBtnX, starBtnY, starBtnX + 1, starBtnY + 14, starBorder)
        guiGraphics.fill(starBtnX + 13, starBtnY, starBtnX + 14, starBtnY + 14, starBorder)
        val starIcon = if (isInWatchlist) "§6★" else "§7☆"
        guiGraphics.drawString(font, starIcon, starBtnX + 3, starBtnY + 3, 0xFFFFFFFF.toInt(), false)

        val trackBtnQuickX = previewX + previewSize + 6
        val trackBtnQuickY = previewY + 10
        val isTrackQuickHovered = mouseX >= trackBtnQuickX && mouseX <= trackBtnQuickX + 14 && mouseY >= trackBtnQuickY && mouseY <= trackBtnQuickY + 14
        val isQuickTarget = PokemonTrackingManager.isTarget(sp, activeForm)
        trackHeaderButtonRect = intArrayOf(trackBtnQuickX, trackBtnQuickY, 14, 14)
        val trackQuickBg = if (isQuickTarget) 0xFF1E3A2B.toInt() else if (isTrackQuickHovered) 0xFF2A2E3D.toInt() else 0xFF181B26.toInt()
        val trackQuickBorder = if (isQuickTarget) 0xFF2ECC71.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(trackBtnQuickX, trackBtnQuickY, trackBtnQuickX + 14, trackBtnQuickY + 14, trackQuickBg)
        guiGraphics.fill(trackBtnQuickX, trackBtnQuickY, trackBtnQuickX + 14, trackBtnQuickY + 1, trackQuickBorder)
        guiGraphics.fill(trackBtnQuickX, trackBtnQuickY + 13, trackBtnQuickX + 14, trackBtnQuickY + 14, trackQuickBorder)
        guiGraphics.fill(trackBtnQuickX, trackBtnQuickY, trackBtnQuickX + 1, trackBtnQuickY + 14, trackQuickBorder)
        guiGraphics.fill(trackBtnQuickX + 13, trackBtnQuickY, trackBtnQuickX + 14, trackBtnQuickY + 14, trackQuickBorder)
        val trackQuickIcon = if (isQuickTarget) "§a🎯" else "§7🎯"
        guiGraphics.drawString(font, trackQuickIcon, trackBtnQuickX + 2, trackBtnQuickY + 3, 0xFFFFFFFF.toInt(), false)

        val stack = getItemStack(sp, activeForm, isShinyActive)
        if (!stack.isEmpty) {
            val pose = guiGraphics.pose()
            pose.pushPose()
            pose.translate(previewX.toFloat() - 6f, previewY.toFloat() - 6f, 0f)
            pose.scale(3.0f, 3.0f, 1.0f)
            guiGraphics.renderItem(stack, 0, 0)
            pose.popPose()
        }

        // Petite loupe discrète dans le coin du cadre
        val zoomIconColor = if (isPreviewHovered) 0xFF54A0FF.toInt() else 0x88AAAAAA.toInt()
        guiGraphics.drawString(font, "🔍", previewX + previewSize - 6, previewY + previewSize - 6, zoomIconColor, false)

        // Nom et Numéro Pokédex
        val dexStr = String.format("#%04d", sp.nationalPokedexNumber)
        val nameStr = sp.translatedName.string
        guiGraphics.drawCenteredString(font, "§6$dexStr§r §f§l$nameStr§r", panelX + panelW / 2, previewY + previewSize + 4, 0xFFFFFFFF.toInt())

        // Types et statut de la forme active
        val primary = activeForm.primaryType
        val secondary = activeForm.secondaryType
        val typeDesc = if (secondary != null) {
            "§b${primary.name}§r/§b${secondary.name}§r"
        } else {
            "§b${primary.name}§r"
        }
        val formStatus = getFormStatusInfo(sp, activeForm, isShinyActive, currentTracked)
        val formTitleSub = if (activeForm != sp.standardForm) " · §e${getFormDisplayName(activeForm, sp)}§r" else ""
        guiGraphics.drawCenteredString(font, "$typeDesc · ${formStatus.text}$formTitleSub", panelX + panelW / 2, previewY + previewSize + 16, 0xFFCCCCCC.toInt())

        // Séparateur avant onglets
        guiGraphics.fill(panelX + 6, panelY + 70, panelX + panelW - 6, panelY + 71, 0xFF282D3C.toInt())

        // 2. Rendu des onglets (2 lignes)
        var hoveredTabTooltip: String? = null
        for (tab in RightPanelTab.values()) {
            val (tx, ty, tw, th) = getRightPanelTabRect(tab)
            val isHovered = mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY <= ty + th
            val isActive = (currentRightPanelTab == tab)

            if (isHovered) {
                hoveredTabTooltip = tab.tooltip
            }

            val bg = when {
                isActive -> 0xFF3498DB.toInt()
                isHovered -> 0xFF2D3345.toInt()
                else -> 0xFF1D212E.toInt()
            }
            val border = when {
                isActive -> 0xFF54A0FF.toInt()
                isHovered -> 0xFF3D465E.toInt()
                else -> 0xFF282D3C.toInt()
            }
            val textColor = when {
                isActive -> 0xFFFFFFFF.toInt()
                isHovered -> 0xFFE0E0E0.toInt()
                else -> 0xFFAAAAAA.toInt()
            }

            guiGraphics.fill(tx, ty, tx + tw, ty + th, bg)
            guiGraphics.fill(tx, ty, tx + tw, ty + 1, border)
            guiGraphics.fill(tx, ty + th - 1, tx + tw, ty + th, border)
            guiGraphics.fill(tx, ty, tx + 1, ty + th, border)
            guiGraphics.fill(tx + tw - 1, ty, tx + tw, ty + th, border)

            guiGraphics.drawCenteredString(font, tab.label, tx + tw / 2, ty + 3, textColor)
        }

        // Séparateur sous les onglets
        guiGraphics.fill(panelX + 6, panelY + 104, panelX + panelW - 6, panelY + 105, 0xFF282D3C.toInt())

        // 3. Zone scrollable de contenu de l'onglet
        val contentX = panelX + 6
        val contentY = panelY + 107
        val contentW = panelW - 12
        val contentH = (panelY + panelH - 26) - contentY

        guiGraphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH)
        val curY = contentY + 2 - rightPanelScrollOffset.toInt()

        val finalY = when (currentRightPanelTab) {
            RightPanelTab.DESCRIPTION -> renderTabDescription(guiGraphics, sp, currentTracked, contentX, curY, contentW)
            RightPanelTab.FORMS -> renderTabForms(guiGraphics, sp, currentTracked, contentX, curY, contentW, mouseX, mouseY)
            RightPanelTab.ABILITIES -> renderTabAbilities(guiGraphics, sp, contentX, curY, contentW)
            RightPanelTab.STATS -> renderTabStats(guiGraphics, sp, contentX, curY, contentW)
            RightPanelTab.DROPS -> renderTabDrops(guiGraphics, sp, contentX, curY, contentW)
            RightPanelTab.PASTURE -> renderTabPasture(guiGraphics, sp, contentX, curY, contentW)
            RightPanelTab.SPAWNS -> renderTabSpawns(guiGraphics, sp, contentX, curY, contentW, mouseX, mouseY)
            RightPanelTab.EVOLUTIONS -> renderTabEvolutions(guiGraphics, sp, contentX, curY, contentW)
        }

        guiGraphics.disableScissor()

        val totalH = finalY - curY
        rightPanelMaxScroll = max(0.0, (totalH - contentH).toDouble())

        // Barre de défilement verticale
        if (rightPanelMaxScroll > 0) {
            val scrollTrackX = panelX + panelW - 5
            val thumbH = max(10, (contentH * (contentH.toDouble() / totalH)).toInt())
            val thumbY = contentY + ((contentH - thumbH) * (rightPanelScrollOffset / rightPanelMaxScroll)).toInt()
            val thumbColor = if (isDraggingRightScrollbar) 0xFF54A0FF.toInt() else 0xFF353C4D.toInt()
            guiGraphics.fill(scrollTrackX, contentY, scrollTrackX + 2, contentY + contentH, 0xFF181B24.toInt())
            guiGraphics.fill(scrollTrackX, thumbY, scrollTrackX + 2, thumbY + thumbH, thumbColor)
        }

        // 4. Boutons Tracker & JourneyMap en bas du panneau
        val btnY = panelY + panelH - 22
        val halfW = (panelW - 20) / 2
        val trackBtnX = panelX + 8
        val jmBtnX = trackBtnX + halfW + 4

        val isTarget = PokemonTrackingManager.isTarget(sp, activeForm)
        val isTrackHovered = mouseX >= trackBtnX && mouseX <= trackBtnX + halfW && mouseY >= btnY && mouseY <= btnY + 16
        val trackBg = when {
            isTarget -> if (isTrackHovered) 0xFF27AE60.toInt() else 0xFF2ECC71.toInt()
            isTrackHovered -> 0xFF3498DB.toInt()
            else -> 0xFF212530.toInt()
        }
        val trackBorder = if (isTarget) 0xFF2ECC71.toInt() else 0xFF54A0FF.toInt()

        guiGraphics.fill(trackBtnX, btnY, trackBtnX + halfW, btnY + 16, trackBg)
        guiGraphics.fill(trackBtnX, btnY, trackBtnX + halfW, btnY + 1, trackBorder)
        guiGraphics.fill(trackBtnX, btnY + 15, trackBtnX + halfW, btnY + 16, trackBorder)

        val trackText = if (isTarget) "✔ Traqué" else "🎯 Tracker"
        guiGraphics.drawCenteredString(font, trackText, trackBtnX + halfW / 2, btnY + 4, 0xFFFFFFFF.toInt())

        val isJmActive = SpawnOverlayManager.isOverlayActive && SpawnOverlayManager.activeSpecies == sp
        val isJmHovered = mouseX >= jmBtnX && mouseX <= jmBtnX + halfW && mouseY >= btnY && mouseY <= btnY + 16
        val jmBg = when {
            isJmActive -> if (isJmHovered) 0xFF2980B9.toInt() else 0xFF3498DB.toInt()
            isJmHovered -> 0xFF2D3345.toInt()
            else -> 0xFF212530.toInt()
        }
        val jmBorder = if (isJmActive) 0xFF54A0FF.toInt() else 0xFF3D465E.toInt()

        guiGraphics.fill(jmBtnX, btnY, jmBtnX + halfW, btnY + 16, jmBg)
        guiGraphics.fill(jmBtnX, btnY, jmBtnX + halfW, btnY + 1, jmBorder)
        guiGraphics.fill(jmBtnX, btnY + 15, jmBtnX + halfW, btnY + 16, jmBorder)

        val jmText = if (isJmActive) "✔ Carte" else "🗺 Carte"
        guiGraphics.drawCenteredString(font, jmText, jmBtnX + halfW / 2, btnY + 4, 0xFFFFFFFF.toInt())

        // Tooltip de survol d'onglet et éléments interactifs (affichés uniquement si aucune modale n'est ouverte)
        if (!isZoomModalOpen && !isFilterModalOpen && !isTrackerModalOpen) {
            watchlistHeaderButtonRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    val tt = if (PokemonTrackingManager.isInWatchlist(sp)) "Retirer de la liste de tracking" else "Ajouter à la liste de tracking personnalisée"
                    guiGraphics.renderTooltip(font, Component.literal(tt), mouseX, mouseY)
                    return
                }
            }
            trackHeaderButtonRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    val tt = "Options du radar & tracker (Cliquer pour gérer)"
                    guiGraphics.renderTooltip(font, Component.literal(tt), mouseX, mouseY)
                    return
                }
            }
            if (hoveredTabTooltip != null) {
                guiGraphics.renderTooltip(font, Component.literal(hoveredTabTooltip), mouseX, mouseY)
            } else if (hoveredPokeSnackStack != null) {
                guiGraphics.renderTooltip(font, hoveredPokeSnackStack!!, mouseX, mouseY)
            } else if (isPreviewHovered) {
                guiGraphics.renderComponentTooltip(font, listOf(Component.literal("§b🔍 Cliquer pour voir en grand format")), mouseX, mouseY)
            } else if (currentRightPanelTab == RightPanelTab.FORMS) {
                zoomHeaderButtonRect?.let { (bx, by, bw, bh) ->
                    if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                        guiGraphics.renderComponentTooltip(font, listOf(Component.literal("§b🔍 Ouvrir la visionneuse de skins en grand format")), mouseX, mouseY)
                        return
                    }
                }
                shinyHeaderButtonRect?.let { (bx, by, bw, bh) ->
                    if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                        val tt = if (globalShinyForms) "Désactiver le mode Chromatique (Shiny)" else "Activer le mode Chromatique (Shiny) pour toutes les formes"
                        guiGraphics.renderTooltip(font, Component.literal(tt), mouseX, mouseY)
                        return
                    }
                }
                if (hoveredZoomCardForm != null) {
                    guiGraphics.renderComponentTooltip(font, listOf(Component.literal("§b🔍 Voir cette variante en grand format")), mouseX, mouseY)
                } else if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= contentY && mouseY <= contentY + contentH) {
                    for ((rect, form) in shinyCardButtonRects) {
                        val (bx, by, bw, bh) = rect
                        if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                            val isSh = isFormShiny(form)
                            val fName = getFormDisplayName(form, sp)
                            val tt = if (isSh) "Cliquer pour afficher la version normale ($fName)" else "Cliquer pour afficher la version Shiny ($fName)"
                            guiGraphics.renderTooltip(font, Component.literal(tt), mouseX, mouseY)
                            break
                        }
                    }
                }
            }
        }
    }

    private fun renderTabDescription(guiGraphics: GuiGraphics, sp: Species, currentTracked: TrackedPokemon, contentX: Int, startY: Int, contentW: Int): Int {
        var y = startY

        // Description Pokédex
        guiGraphics.drawString(font, "§e§lDescription du Pokédex§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 12

        var hasDesc = false
        for (entry in sp.pokedex) {
            val text = Component.translatable(entry).string
            if (text.isNotBlank() && text != entry) {
                hasDesc = true
                val lines = font.split(Component.literal("§7$text§r"), contentW - 6)
                for (l in lines) {
                    guiGraphics.drawString(font, l, contentX + 4, y, 0xFFDDDDDD.toInt(), false)
                    y += 9
                }
                y += 2
            }
        }
        if (!hasDesc) {
            guiGraphics.drawString(font, "§7• Aucune description d'archive.§r", contentX + 4, y, 0xFF888888.toInt(), false)
            y += 11
        }

        y += 4
        // Morphologie & Genre
        guiGraphics.drawString(font, "§e§lMorphologie & Genre§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 11

        val heightStr = String.format("%.1f m", sp.height / 10.0)
        val weightStr = String.format("%.1f kg", sp.weight / 10.0)
        guiGraphics.drawString(font, "Taille : §b$heightStr§r · Poids : §b$weightStr§r", contentX + 4, y, 0xFFCCCCCC.toInt(), false)
        y += 10

        val genderStr = if (sp.maleRatio < 0) {
            "§7Asexué§r"
        } else {
            val mPct = (sp.maleRatio * 100).toInt()
            val fPct = 100 - mPct
            "§b♂ $mPct%§r §8/§r §d♀ $fPct%§r"
        }
        guiGraphics.drawString(font, "Genre : $genderStr", contentX + 4, y, 0xFFCCCCCC.toInt(), false)
        y += 14

        // Reproduction & Capture
        guiGraphics.drawString(font, "§e§lReproduction & Capture§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 11

        guiGraphics.drawString(font, "Taux de capture : §a${sp.catchRate}§r §8/ 255§r", contentX + 4, y, 0xFFCCCCCC.toInt(), false)
        y += 10
        guiGraphics.drawString(font, "Bonheur base : §a${sp.baseFriendship}§r · XP base : §6${sp.baseExperienceYield}§r", contentX + 4, y, 0xFFCCCCCC.toInt(), false)
        y += 10

        val eggsStr = if (sp.eggGroups.isEmpty()) "Aucun" else sp.eggGroups.joinToString { it.name.lowercase().replaceFirstChar(Char::titlecase) }
        guiGraphics.drawString(font, "Œuf : §f$eggsStr§r", contentX + 4, y, 0xFFCCCCCC.toInt(), false)
        y += 10
        guiGraphics.drawString(font, "Éclosion : §f${sp.eggCycles} cycles§r", contentX + 4, y, 0xFFCCCCCC.toInt(), false)
        y += 14

        // Aptitudes & Caractéristiques spéciales
        val traits = mutableListOf<String>()
        val activeForm = selectedForm ?: sp.standardForm
        val isRideable = try {
            (activeForm.riding?.let { it.seats.isNotEmpty() || (it.behaviours?.isNotEmpty() == true) } == true) ||
            sp.riding.let { it.seats.isNotEmpty() || (it.behaviours?.isNotEmpty() == true) }
        } catch (_: Throwable) { false }
        if (isRideable) {
            traits.add("§a🏇 Monture chevauchable§r")
        }
        if (activeForm.shoulderMountable || sp.shoulderMountable) {
            traits.add("§b🐾 Portable sur l'épaule§r")
        }
        val isFireImmune = try {
            activeForm.behaviour?.fireImmune ?: sp.behaviour.fireImmune
        } catch (_: Throwable) { false }
        if (isFireImmune) {
            traits.add("§6🔥 Immunisé au feu & lave§r")
        }

        val dampensVibrations = try {
            activeForm.behaviour?.dampensVibrations ?: sp.behaviour.dampensVibrations
        } catch (_: Throwable) { false }
        if (dampensVibrations) {
            traits.add("§d🔇 Amortit les vibrations (Sculk)§r")
        }
        if (try { sp.canGmax() } catch (_: Throwable) { false }) {
            traits.add("§e⚡ Forme Gigamax disponible§r")
        }

        if (traits.isNotEmpty()) {
            guiGraphics.drawString(font, "§e§lAptitudes & Capacités§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
            y += 11
            for (trait in traits) {
                guiGraphics.drawString(font, "• $trait", contentX + 4, y, 0xFFE0E0E0.toInt(), false)
                y += 10
            }
            y += 4
        }

        // Réserves
        guiGraphics.drawString(font, "§e§lRéserves du joueur§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 11

        if (currentTracked.ownedCount > 0) {
            for (owned in currentTracked.ownedInstances.take(4)) {
                val txt = "• ${owned.location} (Niv. ${owned.level})"
                guiGraphics.drawString(font, txt, contentX + 4, y, 0xFF55FF55.toInt(), false)
                y += 10
            }
            if (currentTracked.ownedCount > 4) {
                guiGraphics.drawString(font, "• +${currentTracked.ownedCount - 4} autre(s)...", contentX + 4, y, 0xFFAAAAAA.toInt(), false)
                y += 10
            }
        } else {
            guiGraphics.drawString(font, "• Aucun dans le PC ou l'équipe", contentX + 4, y, 0xFF888888.toInt(), false)
            y += 10
        }

        return y + 4
    }

    private fun renderTabForms(
        guiGraphics: GuiGraphics,
        sp: Species,
        currentTracked: TrackedPokemon?,
        contentX: Int,
        startY: Int,
        contentW: Int,
        mouseX: Int,
        mouseY: Int
    ): Int {
        var y = startY
        shinyCardButtonRects.clear()
        zoomCardButtonRects.clear()
        selectCardButtonRects.clear()
        hoveredZoomCardForm = null

        val allForms = getSpeciesForms(sp)

        // Titre compact
        val titleText = "§e§lVariantes (${allForms.size})§r"
        guiGraphics.drawString(font, titleText, contentX + 2, y + 1, 0xFFFFFFFF.toInt(), false)

        // Boutons en-tête : [🔍 Zoom] et [★ Shiny]
        val shinyBtnW = 46
        val shinyBtnH = 12
        val shinyBtnX = contentX + contentW - shinyBtnW
        val shinyBtnY = y - 1
        shinyHeaderButtonRect = intArrayOf(shinyBtnX, shinyBtnY, shinyBtnW, shinyBtnH)

        val zoomBtnW = 44
        val zoomBtnH = 12
        val zoomBtnX = shinyBtnX - zoomBtnW - 4
        val zoomBtnY = y - 1
        zoomHeaderButtonRect = intArrayOf(zoomBtnX, zoomBtnY, zoomBtnW, zoomBtnH)

        val isZoomHovered = mouseX >= zoomBtnX && mouseX <= zoomBtnX + zoomBtnW && mouseY >= zoomBtnY && mouseY <= zoomBtnY + zoomBtnH
        val zoomBg = if (isZoomHovered) 0xFF353C4D.toInt() else 0xFF1D212E.toInt()
        val zoomBorder = if (isZoomHovered) 0xFF54A0FF.toInt() else 0xFF3D465E.toInt()
        guiGraphics.fill(zoomBtnX, zoomBtnY, zoomBtnX + zoomBtnW, zoomBtnY + zoomBtnH, zoomBg)
        guiGraphics.fill(zoomBtnX, zoomBtnY, zoomBtnX + zoomBtnW, zoomBtnY + 1, zoomBorder)
        guiGraphics.fill(zoomBtnX, zoomBtnY + zoomBtnH - 1, zoomBtnX + zoomBtnW, zoomBtnY + zoomBtnH, zoomBorder)
        guiGraphics.fill(zoomBtnX, zoomBtnY, zoomBtnX + 1, zoomBtnY + zoomBtnH, zoomBorder)
        guiGraphics.fill(zoomBtnX + zoomBtnW - 1, zoomBtnY, zoomBtnX + zoomBtnW, zoomBtnY + zoomBtnH, zoomBorder)
        guiGraphics.drawCenteredString(font, "§b🔍 Zoom§r", zoomBtnX + zoomBtnW / 2, zoomBtnY + 2, 0xFFFFFFFF.toInt())

        val isHeaderHovered = mouseX >= shinyBtnX && mouseX <= shinyBtnX + shinyBtnW && mouseY >= shinyBtnY && mouseY <= shinyBtnY + shinyBtnH
        val headerBg = when {
            globalShinyForms -> if (isHeaderHovered) 0xFFD4AC0D.toInt() else 0xFFB7950B.toInt()
            isHeaderHovered -> 0xFF353C4D.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val headerBorder = if (globalShinyForms) 0xFFF1C40F.toInt() else 0xFF3D465E.toInt()
        val headerText = if (globalShinyForms) "§6★ Shiny§r" else "§7☆ Shiny§r"

        guiGraphics.fill(shinyBtnX, shinyBtnY, shinyBtnX + shinyBtnW, shinyBtnY + shinyBtnH, headerBg)
        guiGraphics.fill(shinyBtnX, shinyBtnY, shinyBtnX + shinyBtnW, shinyBtnY + 1, headerBorder)
        guiGraphics.fill(shinyBtnX, shinyBtnY + shinyBtnH - 1, shinyBtnX + shinyBtnW, shinyBtnY + shinyBtnH, headerBorder)
        guiGraphics.fill(shinyBtnX, shinyBtnY, shinyBtnX + 1, shinyBtnY + shinyBtnH, headerBorder)
        guiGraphics.fill(shinyBtnX + shinyBtnW - 1, shinyBtnY, shinyBtnX + shinyBtnW, shinyBtnY + shinyBtnH, headerBorder)
        guiGraphics.drawCenteredString(font, headerText, shinyBtnX + shinyBtnW / 2, shinyBtnY + 2, 0xFFFFFFFF.toInt())

        y += 15

        val allRecipes = PokemonEvolutionDataLoader.loadEvolutionRecipes()
        val cardH = 52

        for (form in allForms) {
            val isSelectedForm = (form == (selectedForm ?: sp.standardForm))
            val formTitle = getFormDisplayName(form, sp)
            val isShiny = isFormShiny(form)
            val cardY = y

            // Fond et bordures de la carte
            val cardBg = when {
                isSelectedForm && isShiny -> 0xFF2A2414.toInt()
                isSelectedForm -> 0xFF1C2433.toInt()
                isShiny -> 0xFF1A1812.toInt()
                else -> 0xFF151922.toInt()
            }
            val cardBorder = when {
                isSelectedForm && isShiny -> 0xFFF1C40F.toInt()
                isSelectedForm -> 0xFF54A0FF.toInt()
                isShiny -> 0xFF8E6E1A.toInt()
                else -> 0xFF282D3C.toInt()
            }

            guiGraphics.fill(contentX, cardY, contentX + contentW, cardY + cardH, cardBg)
            guiGraphics.fill(contentX, cardY, contentX + contentW, cardY + 1, cardBorder)
            guiGraphics.fill(contentX, cardY + cardH - 1, contentX + contentW, cardY + cardH, cardBorder)
            guiGraphics.fill(contentX, cardY, contentX + 1, cardY + cardH, cardBorder)
            guiGraphics.fill(contentX + contentW - 1, cardY, contentX + contentW, cardY + cardH, cardBorder)

            // Zone Sprite + Star à gauche (cliquable pour basculer shiny individuel)
            val spriteBoxX = contentX + 3
            val spriteBoxY = cardY + 3
            val spriteBoxW = 26
            val spriteBoxH = cardH - 6
            val toggleBox = intArrayOf(spriteBoxX, spriteBoxY, spriteBoxW, spriteBoxH)
            shinyCardButtonRects.add(Pair(toggleBox, form))

            val isSpriteHovered = mouseX >= spriteBoxX && mouseX <= spriteBoxX + spriteBoxW &&
                    mouseY >= spriteBoxY && mouseY <= spriteBoxY + spriteBoxH

            val spriteBg = if (isSpriteHovered) 0xFF222838.toInt() else 0xFF10121A.toInt()
            val spriteBorder = if (isSpriteHovered) 0xFF54A0FF.toInt() else 0xFF1E2330.toInt()
            guiGraphics.fill(spriteBoxX, spriteBoxY, spriteBoxX + spriteBoxW, spriteBoxY + spriteBoxH, spriteBg)
            guiGraphics.fill(spriteBoxX, spriteBoxY, spriteBoxX + spriteBoxW, spriteBoxY + 1, spriteBorder)
            guiGraphics.fill(spriteBoxX, spriteBoxY + spriteBoxH - 1, spriteBoxX + spriteBoxW, spriteBoxY + spriteBoxH, spriteBorder)
            guiGraphics.fill(spriteBoxX, spriteBoxY, spriteBoxX + 1, spriteBoxY + spriteBoxH, spriteBorder)
            guiGraphics.fill(spriteBoxX + spriteBoxW - 1, spriteBoxY, spriteBoxX + spriteBoxW, spriteBoxY + spriteBoxH, spriteBorder)

            // Sprite de la forme
            val stack = getItemStack(sp, form, isShiny)
            if (!stack.isEmpty) {
                guiGraphics.renderItem(stack, spriteBoxX + 5, spriteBoxY + 5)
            }

            // Étoile sous le sprite
            val starTxt = if (isShiny) "§e★§r" else "§8☆§r"
            guiGraphics.drawCenteredString(font, starTxt, spriteBoxX + spriteBoxW / 2, spriteBoxY + 28, 0xFFFFFFFF.toInt())

            // Clic sur le reste de la carte pour sélectionner la forme
            val selectCardBox = intArrayOf(contentX + spriteBoxW + 4, cardY, contentW - spriteBoxW - 22, cardH)
            selectCardButtonRects.add(Pair(selectCardBox, form))

            // Bouton Zoom [🔍] individuel en haut à droite de la carte
            val zoomCardBox = intArrayOf(contentX + contentW - 16, cardY + 3, 13, 12)
            zoomCardButtonRects.add(Pair(zoomCardBox, form))
            val isZoomCardHovered = mouseX >= zoomCardBox[0] && mouseX <= zoomCardBox[0] + zoomCardBox[2] &&
                    mouseY >= zoomCardBox[1] && mouseY <= zoomCardBox[1] + zoomCardBox[3]
            if (isZoomCardHovered) {
                hoveredZoomCardForm = form
            }
            val zoomIconColor = if (isZoomCardHovered) 0xFF54A0FF.toInt() else 0xFF888888.toInt()
            guiGraphics.drawString(font, "🔍", zoomCardBox[0], zoomCardBox[1] + 1, zoomIconColor, false)

            val infoX = contentX + spriteBoxW + 6

            // Ligne 1 : Titre de la forme
            val titlePrefix = if (isShiny) "§6★ §f§l" else "§f§l"
            guiGraphics.drawString(font, "$titlePrefix$formTitle§r", infoX, cardY + 5, 0xFFFFFFFF.toInt(), false)

            // Ligne 2 : Statut de capture / possession (DÉDIÉ, AUCUN OVERLAP)
            val statusInfo = getFormStatusInfo(sp, form, isShiny, currentTracked)
            guiGraphics.drawString(font, statusInfo.text, infoX, cardY + 16, statusInfo.color, false)

            // Ligne 3 : Badges de Types élémentaires
            var typeX = infoX
            val primColor = CobblemonOverlayManager.getElementColor(form.primaryType.name)
            val primName = form.primaryType.displayName.string.uppercase()
            val primW = font.width(primName) + 4
            guiGraphics.fill(typeX, cardY + 27, typeX + primW, cardY + 36, primColor)
            guiGraphics.drawString(font, primName, typeX + 2, cardY + 28, 0xFFFFFFFF.toInt(), true)
            typeX += primW + 3

            form.secondaryType?.let { secType ->
                val secColor = CobblemonOverlayManager.getElementColor(secType.name)
                val secName = secType.displayName.string.uppercase()
                val secW = font.width(secName) + 4
                guiGraphics.fill(typeX, cardY + 27, typeX + secW, cardY + 36, secColor)
                guiGraphics.drawString(font, secName, typeX + 2, cardY + 28, 0xFFFFFFFF.toInt(), true)
            }

            // Ligne 4 : Évolutions
            val formEvos = allRecipes.filter { it.sourceSpecies == sp && (it.sourceForm == form || (it.sourceForm.name.isNotBlank() && it.sourceForm.name.equals(form.name, true))) }
            val evoText = if (formEvos.isNotEmpty()) {
                val targets = formEvos.map { it.targetSpecies.translatedName.string }.distinct()
                "§7Évolue en : §e${targets.joinToString(", ")}§r"
            } else if (allForms.size > 1) {
                "§8✕ Pas d'évolution (cette forme)§r"
            } else {
                "§8• Aucune évolution connue§r"
            }
            guiGraphics.drawString(font, evoText, infoX, cardY + 39, 0xFFCCCCCC.toInt(), false)

            y += cardH + 4
        }

        return y + 2
    }

    private fun renderTabAbilities(guiGraphics: GuiGraphics, sp: Species, contentX: Int, startY: Int, contentW: Int): Int {
        var y = startY
        guiGraphics.drawString(font, "§e§lTalents & Capacités§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 12

        val abilities = sp.standardForm.abilities
        if (abilities.isEmpty()) {
            guiGraphics.drawString(font, "§7• Aucun talent enregistré§r", contentX + 4, y, 0xFF888888.toInt(), false)
            return y + 14
        }

        for (pot in abilities) {
            val template = pot.template
            val isHidden = pot is HiddenAbility || pot.priority == Priority.LOWEST
            val badge = if (isHidden) "§d[Caché]§r" else "§b[Normal]§r"

            val rawName = template.displayName.ifBlank { template.name }
            val nameComp = Component.translatable(rawName)
            val displayName = if (nameComp.string.isNotBlank() && nameComp.string != rawName) nameComp.string else rawName.replaceFirstChar(Char::titlecase)

            guiGraphics.drawString(font, "$badge §f§l$displayName§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
            y += 11

            val rawDesc = template.description
            if (rawDesc.isNotBlank()) {
                val descComp = Component.translatable(rawDesc)
                val descText = if (descComp.string.isNotBlank() && descComp.string != rawDesc) descComp.string else ""
                if (descText.isNotBlank()) {
                    val lines = font.split(Component.literal(descText), contentW - 8)
                    for (l in lines) {
                        guiGraphics.drawString(font, l, contentX + 4, y, 0xFFAAAAAA.toInt(), false)
                        y += 9
                    }
                }
            }
            y += 6
        }

        return y + 4
    }

    private fun renderTabStats(guiGraphics: GuiGraphics, sp: Species, contentX: Int, startY: Int, contentW: Int): Int {
        var y = startY
        guiGraphics.drawString(font, "§e§lStatistiques de base§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 12

        val bStats = sp.baseStats
        val statsList = listOf(
            "PV" to (bStats[Stats.HP] ?: 0),
            "Attaque" to (bStats[Stats.ATTACK] ?: 0),
            "Défense" to (bStats[Stats.DEFENCE] ?: 0),
            "Att. Spé" to (bStats[Stats.SPECIAL_ATTACK] ?: 0),
            "Déf. Spé" to (bStats[Stats.SPECIAL_DEFENCE] ?: 0),
            "Vitesse" to (bStats[Stats.SPEED] ?: 0)
        )
        val bst = statsList.sumOf { it.second }

        for ((name, value) in statsList) {
            guiGraphics.drawString(font, name, contentX + 2, y, 0xFFCCCCCC.toInt(), false)
            val valStr = value.toString()
            guiGraphics.drawString(font, valStr, contentX + 48, y, 0xFFFFFFFF.toInt(), false)

            val barX = contentX + 70
            val barW = max(20, contentW - 74)
            val fillW = ((value.toDouble() / 160.0).coerceIn(0.0, 1.0) * barW).toInt()

            val color = when {
                value < 50 -> 0xFFE74C3C.toInt()
                value < 80 -> 0xFFE67E22.toInt()
                value < 100 -> 0xFFF1C40F.toInt()
                value < 130 -> 0xFF2ECC71.toInt()
                else -> 0xFF1ABC9C.toInt()
            }

            guiGraphics.fill(barX, y + 2, barX + barW, y + 7, 0xFF20232E.toInt())
            if (fillW > 0) {
                guiGraphics.fill(barX, y + 2, barX + fillW, y + 7, color)
            }
            y += 12
        }

        y += 2
        guiGraphics.drawString(font, "Total (BST) : §e§l$bst§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 16

        guiGraphics.drawString(font, "§e§lRendement EV§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 12

        val evs = sp.evYield
        val evParts = mutableListOf<String>()
        if ((evs[Stats.HP] ?: 0) > 0) evParts.add("+${evs[Stats.HP]} PV")
        if ((evs[Stats.ATTACK] ?: 0) > 0) evParts.add("+${evs[Stats.ATTACK]} Attaque")
        if ((evs[Stats.DEFENCE] ?: 0) > 0) evParts.add("+${evs[Stats.DEFENCE]} Défense")
        if ((evs[Stats.SPECIAL_ATTACK] ?: 0) > 0) evParts.add("+${evs[Stats.SPECIAL_ATTACK]} Att. Spé")
        if ((evs[Stats.SPECIAL_DEFENCE] ?: 0) > 0) evParts.add("+${evs[Stats.SPECIAL_DEFENCE]} Déf. Spé")
        if ((evs[Stats.SPEED] ?: 0) > 0) evParts.add("+${evs[Stats.SPEED]} Vitesse")

        if (evParts.isNotEmpty()) {
            val evStr = evParts.joinToString(", ")
            val lines = font.split(Component.literal("§a$evStr§r"), contentW - 4)
            for (l in lines) {
                guiGraphics.drawString(font, l, contentX + 4, y, 0xFFFFFFFF.toInt(), false)
                y += 10
            }
        } else {
            guiGraphics.drawString(font, "§7• Aucun EV à la défaite§r", contentX + 4, y, 0xFF888888.toInt(), false)
            y += 10
        }

        return y + 6
    }

    private fun renderTabDrops(guiGraphics: GuiGraphics, sp: Species, contentX: Int, startY: Int, contentW: Int): Int {
        var y = startY
        guiGraphics.drawString(font, "§e§lObjets lâchés à la défaite§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 14

        val recipes = PokemonDropDataLoader.loadDropRecipes().filter { it.species == sp }
        val drops = recipes.flatMap { it.drops }.distinctBy { it.itemStack.item.descriptionId + ":" + it.percentage }

        if (drops.isEmpty()) {
            guiGraphics.drawString(font, "§7• Aucun butin répertorié.§r", contentX + 4, y, 0xFF888888.toInt(), false)
            return y + 14
        }

        for (drop in drops) {
            guiGraphics.renderItem(drop.itemStack, contentX + 2, y)
            guiGraphics.renderItemDecorations(font, drop.itemStack, contentX + 2, y)

            val name = truncateString(drop.itemStack.hoverName.string, contentW - 24)
            guiGraphics.drawString(font, name, contentX + 22, y + 1, 0xFFFFFFFF.toInt(), false)

            val pctStr = if (drop.percentage >= 100) "100%" else String.format("%.1f%%", drop.percentage)
            val qStr = if (drop.minCount == drop.maxCount) "${drop.minCount}" else "${drop.minCount}-${drop.maxCount}"
            val evoTag = if (drop.isEvolutionItem) " §d[Évol.]§r" else ""
            guiGraphics.drawString(font, "§a$pctStr§r · §e${qStr}x§r$evoTag", contentX + 22, y + 10, 0xFFCCCCCC.toInt(), false)

            y += 22
        }

        return y + 4
    }

    private fun renderTabPasture(guiGraphics: GuiGraphics, sp: Species, contentX: Int, startY: Int, contentW: Int): Int {
        var y = startY
        guiGraphics.drawString(font, "§e§lMétiers au Pâturage§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 14

        if (!CobbleworkersCompat.isLoaded) {
            guiGraphics.drawString(font, "§c✕ Mod Cobbleworkers absent§r", contentX + 4, y, 0xFFFF7675.toInt(), false)
            y += 11
            val lines = font.split(Component.literal("§7Installez Cobbleworkers pour attribuer des tâches de travail dans l'enclos.§r"), contentW - 8)
            for (l in lines) {
                guiGraphics.drawString(font, l, contentX + 4, y, 0xFFAAAAAA.toInt(), false)
                y += 9
            }
            return y + 6
        }

        val jobs = CobbleworkersJobDataLoader.loadPastureRecipes().filter { it.eligiblePokemon.any { ing -> ing.species == sp } }
        if (jobs.isEmpty()) {
            guiGraphics.drawString(font, "§7• Aucun métier de travail assignable.§r", contentX + 4, y, 0xFF888888.toInt(), false)
            return y + 14
        }

        for (job in jobs) {
            guiGraphics.drawString(font, "§6§l${job.jobTitle.string}§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
            y += 11

            val cadenceStr = if (job.isContinuous) "§aCadence : Continue§r" else "§eCooldown : ${job.cooldownSeconds}s§r"
            guiGraphics.drawString(font, cadenceStr, contentX + 4, y, 0xFFCCCCCC.toInt(), false)
            y += 10

            val descLines = font.split(job.actionDescription, contentW - 8)
            for (l in descLines) {
                guiGraphics.drawString(font, l, contentX + 4, y, 0xFFAAAAAA.toInt(), false)
                y += 9
            }

            val allOutputs = job.outputs.flatten().distinctBy { it.item.descriptionId }
            if (allOutputs.isNotEmpty()) {
                guiGraphics.drawString(font, "§7Produits :§r", contentX + 4, y, 0xFF888888.toInt(), false)
                y += 10
                var outX = contentX + 4
                for (out in allOutputs.take(5)) {
                    guiGraphics.renderItem(out, outX, y)
                    guiGraphics.renderItemDecorations(font, out, outX, y)
                    outX += 18
                }
                y += 20
            }

            guiGraphics.fill(contentX + 4, y, contentX + contentW - 4, y + 1, 0xFF282D3C.toInt())
            y += 6
        }

        return y + 4
    }

    private fun renderTabSpawns(guiGraphics: GuiGraphics, sp: Species, contentX: Int, startY: Int, contentW: Int, mouseX: Int, mouseY: Int): Int {
        var y = startY
        hoveredPokeSnackStack = null

        // Sélecteur de sous-catégorie : Naturel vs Poké Snack
        val tabBtnW = (contentW - 6) / 2
        val tabBtnH = 15
        val naturalX = contentX + 2
        val snackX = naturalX + tabBtnW + 2
        val tabBtnY = y

        spawnCategoryNaturalBtnRect = intArrayOf(naturalX, tabBtnY, tabBtnW, tabBtnH)
        spawnCategorySnackBtnRect = intArrayOf(snackX, tabBtnY, tabBtnW, tabBtnH)

        val isNatActive = (currentSpawnCategory == SpawnSubCategory.NATURAL)
        val isSnackActive = (currentSpawnCategory == SpawnSubCategory.POKE_SNACK)

        val isNatHovered = mouseX >= naturalX && mouseX <= naturalX + tabBtnW && mouseY >= tabBtnY && mouseY <= tabBtnY + tabBtnH
        val isSnackHovered = mouseX >= snackX && mouseX <= snackX + tabBtnW && mouseY >= tabBtnY && mouseY <= tabBtnY + tabBtnH

        // Bouton 1 : Naturel
        val natBg = when {
            isNatActive -> 0xFF3498DB.toInt()
            isNatHovered -> 0xFF2D3345.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val natBorder = if (isNatActive) 0xFF54A0FF.toInt() else 0xFF282D3C.toInt()
        guiGraphics.fill(naturalX, tabBtnY, naturalX + tabBtnW, tabBtnY + tabBtnH, natBg)
        guiGraphics.fill(naturalX, tabBtnY, naturalX + tabBtnW, tabBtnY + 1, natBorder)
        guiGraphics.fill(naturalX, tabBtnY + tabBtnH - 1, naturalX + tabBtnW, tabBtnY + tabBtnH, natBorder)
        guiGraphics.fill(naturalX, tabBtnY, naturalX + 1, tabBtnY + tabBtnH, natBorder)
        guiGraphics.fill(naturalX + tabBtnW - 1, tabBtnY, naturalX + tabBtnW, tabBtnY + tabBtnH, natBorder)
        guiGraphics.drawCenteredString(font, "🌲 Naturel", naturalX + tabBtnW / 2, tabBtnY + 4, if (isNatActive) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt())

        // Bouton 2 : Poké Snack
        val snackBg = when {
            isSnackActive -> 0xFF3498DB.toInt()
            isSnackHovered -> 0xFF2D3345.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val snackBorder = if (isSnackActive) 0xFF54A0FF.toInt() else 0xFF282D3C.toInt()
        guiGraphics.fill(snackX, tabBtnY, snackX + tabBtnW, tabBtnY + tabBtnH, snackBg)
        guiGraphics.fill(snackX, tabBtnY, snackX + tabBtnW, tabBtnY + 1, snackBorder)
        guiGraphics.fill(snackX, tabBtnY + tabBtnH - 1, snackX + tabBtnW, tabBtnY + tabBtnH, snackBorder)
        guiGraphics.fill(snackX, tabBtnY, snackX + 1, tabBtnY + tabBtnH, snackBorder)
        guiGraphics.fill(snackX + tabBtnW - 1, tabBtnY, snackX + tabBtnW, tabBtnY + tabBtnH, snackBorder)
        guiGraphics.drawCenteredString(font, "🍪 Poké Snack", snackX + tabBtnW / 2, tabBtnY + 4, if (isSnackActive) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt())

        y += tabBtnH + 6

        // Si la sous-catégorie Poké Snack est active, afficher le guide d'assaisonnement dédié
        if (currentSpawnCategory == SpawnSubCategory.POKE_SNACK) {
            return renderTabPokeSnack(guiGraphics, sp, contentX, y, contentW, mouseX, mouseY)
        }

        guiGraphics.drawString(font, "§e§lZones d'apparition§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 14

        val spawns = PokemonSpawnDataLoader.getSpawnRecipesForSpecies(sp)
        if (spawns.isEmpty()) {
            guiGraphics.drawString(font, "§c✕ N'apparaît pas dans la nature§r", contentX + 4, y, 0xFFFF7675.toInt(), false)
            y += 11
            val lines = font.split(Component.literal("§7Ce Pokémon s'obtient uniquement par évolution, œuf, fossile ou échange.§r"), contentW - 8)
            for (l in lines) {
                guiGraphics.drawString(font, l, contentX + 4, y, 0xFFAAAAAA.toInt(), false)
                y += 9
            }
            return y + 6
        }

        for (spawn in spawns.take(5)) {
            val rarity = when (spawn.bucket.lowercase()) {
                "ultra-rare" -> "§d§l★ Ultra-rare§r"
                "rare" -> "§6§l★ Rare§r"
                "uncommon" -> "§ePeu commun§r"
                else -> "§aCommun§r"
            }
            guiGraphics.drawString(font, "$rarity §8|§r Niv. §b${spawn.levelMin}-${spawn.levelMax}§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
            y += 10

            val timeStr = when (spawn.timeRange) {
                "day" -> "Jour ☀"
                "night" -> "Nuit 🌙"
                else -> "Tout temps"
            }
            val weatherStr = when (spawn.weather) {
                "rain" -> "Pluie 🌧"
                "thundering" -> "Orage ⚡"
                "clear" -> "Clair ☀"
                else -> "Toute météo"
            }
            guiGraphics.drawString(font, "§7$timeStr · $weatherStr · ${spawn.context}§r", contentX + 4, y, 0xFFCCCCCC.toInt(), false)
            y += 10

            // 1. Altitude Y (minY / maxY)
            if (spawn.minY != null || spawn.maxY != null) {
                val yRangeStr = when {
                    spawn.minY != null && spawn.maxY != null -> "Y: ${spawn.minY} à ${spawn.maxY}"
                    spawn.minY != null -> "Y ≥ ${spawn.minY}"
                    else -> "Y ≤ ${spawn.maxY}"
                }
                guiGraphics.drawString(font, "§6Altitude :§r §b$yRangeStr§r", contentX + 4, y, 0xFFE0E0E0.toInt(), false)
                y += 9
            }

            // 2. Bloc sous le Pokémon (Sol)
            if (spawn.neededBaseBlocks.isNotEmpty()) {
                val blockNames = spawn.neededBaseBlocks.take(3).map { formatBlockOrTagId(it) }
                val more = if (spawn.neededBaseBlocks.size > 3) " (+${spawn.neededBaseBlocks.size - 3})" else ""
                val baseText = "§6Sol :§r §f${blockNames.joinToString(", ")}$more§r"
                val baseLines = font.split(Component.literal(baseText), contentW - 8)
                for (l in baseLines) {
                    guiGraphics.drawString(font, l, contentX + 4, y, 0xFFFFFFFF.toInt(), false)
                    y += 9
                }
            }

            // 3. Blocs requis à proximité
            if (spawn.neededNearbyBlocks.isNotEmpty()) {
                val nearNames = spawn.neededNearbyBlocks.take(3).map { formatBlockOrTagId(it) }
                val more = if (spawn.neededNearbyBlocks.size > 3) " (+${spawn.neededNearbyBlocks.size - 3})" else ""
                val nearText = "§6Proche :§r §b${nearNames.joinToString(", ")}$more§r"
                val nearLines = font.split(Component.literal(nearText), contentW - 8)
                for (l in nearLines) {
                    guiGraphics.drawString(font, l, contentX + 4, y, 0xFFFFFFFF.toInt(), false)
                    y += 9
                }
            }

            // 4. Badges Ciel, Lumière, Slime
            val envBadges = mutableListOf<String>()
            if (spawn.canSeeSky == true) envBadges.add("§b☀ Ciel ouvert§r")
            else if (spawn.canSeeSky == false) envBadges.add("§7⛏ En sous-sol / abri§r")
            if (spawn.isSlimeChunk == true) envBadges.add("§a🧪 Chunk Slime§r")
            if (spawn.minSkyLight != null || spawn.maxSkyLight != null) {
                envBadges.add("§eLumière: ${spawn.minSkyLight ?: 0}-${spawn.maxSkyLight ?: 15}§r")
            }
            if (spawn.minLight != null || spawn.maxLight != null) {
                envBadges.add("§cLumière bloc: ${spawn.minLight ?: 0}-${spawn.maxLight ?: 15}§r")
            }
            if (envBadges.isNotEmpty()) {
                val envText = envBadges.joinToString(" · ")
                val envLines = font.split(Component.literal(envText), contentW - 8)
                for (l in envLines) {
                    guiGraphics.drawString(font, l, contentX + 4, y, 0xFFE0E0E0.toInt(), false)
                    y += 9
                }
            }

            // 5. Structures
            if (spawn.structures.isNotEmpty()) {
                val sList = spawn.structures.take(2).map { formatBlockOrTagId(it) }
                val more = if (spawn.structures.size > 2) " (+${spawn.structures.size - 2})" else ""
                val sText = "§d🏛 Structure :§r §f${sList.joinToString(", ")}$more§r"
                val sLines = font.split(Component.literal(sText), contentW - 8)
                for (sl in sLines) {
                    guiGraphics.drawString(font, sl, contentX + 4, y, 0xFFFFFFFF.toInt(), false)
                    y += 9
                }
            }

            // 6. Phase lunaire & Pêche
            val extraList = mutableListOf<String>()
            if (spawn.moonPhase != null) {
                val moonName = when (spawn.moonPhase) {
                    0 -> "Pleine lune"
                    4 -> "Nouvelle lune"
                    else -> "Phase ${spawn.moonPhase}"
                }
                extraList.add("§d🌙 $moonName§r")
            }
            if (spawn.minLureLevel != null || spawn.rodType != null || spawn.bait != null) {
                val fishDetails = mutableListOf<String>()
                spawn.rodType?.let { fishDetails.add(it.substringAfter(":").replace("_", " ")) }
                spawn.minLureLevel?.let { fishDetails.add("Leurre $it+") }
                spawn.bait?.let { fishDetails.add("Appât: ${it.substringAfter(":")}") }
                extraList.add("§9🎣 ${fishDetails.joinToString(", ")}§r")
            }
            if (extraList.isNotEmpty()) {
                guiGraphics.drawString(font, extraList.joinToString(" · "), contentX + 4, y, 0xFFFFFFFF.toInt(), false)
                y += 9
            }

            // 7. Biomes
            if (spawn.biomes.isNotEmpty()) {
                val bList = spawn.biomes.take(3).map {
                    it.substringAfter(":").replace("_", " ").replaceFirstChar(Char::titlecase)
                }
                val more = if (spawn.biomes.size > 3) " (+${spawn.biomes.size - 3})" else ""
                val bText = "§7Biomes :§r §e${bList.joinToString(", ")}$more§r"
                val bLines = font.split(Component.literal(bText), contentW - 8)
                for (bl in bLines) {
                    guiGraphics.drawString(font, bl, contentX + 4, y, 0xFFAAAAAA.toInt(), false)
                    y += 9
                }
            }

            guiGraphics.fill(contentX + 4, y, contentX + contentW - 4, y + 1, 0xFF282D3C.toInt())
            y += 5
        }

        return y + 4
    }

    private fun renderTabPokeSnack(guiGraphics: GuiGraphics, sp: Species, contentX: Int, startY: Int, contentW: Int, mouseX: Int, mouseY: Int): Int {
        var y = startY
        hoveredPokeSnackStack = null

        val activeForm = selectedForm ?: sp.standardForm
        val advice = PokemonPokeSnackResolver.resolvePokeSnack(sp, activeForm)

        // 1. En-tête : Poké Snack Dédié
        guiGraphics.drawString(font, "§e§lPoké Snack Dédié§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 12

        // 2. Statut d'apparition dans la nature
        if (advice.hasWildSpawns) {
            val rarityBadge = when (advice.baseRarity) {
                "ultra-rare" -> "§d§l★ Ultra-rare§r"
                "rare" -> "§6§l★ Rare§r"
                "uncommon" -> "§ePeu commun§r"
                else -> "§aCommun§r"
            }
            guiGraphics.drawString(font, "§7Cible sauvage : §r$rarityBadge", contentX + 4, y, 0xFFE0E0E0.toInt(), false)
            y += 11
        } else {
            guiGraphics.drawString(font, "§c✕ N'apparaît pas à l'état sauvage§r", contentX + 4, y, 0xFFFF7675.toInt(), false)
            y += 10
            if (advice.fallbackPreEvolution != null) {
                val preSp = advice.fallbackPreEvolution
                val evoCardH = 20
                val evoCardW = contentW - 8
                val evoCardX = contentX + 4
                val isEvoHovered = mouseX >= evoCardX && mouseX <= evoCardX + evoCardW && mouseY >= y && mouseY <= y + evoCardH
                guiGraphics.fill(evoCardX, y, evoCardX + evoCardW, y + evoCardH, if (isEvoHovered) 0xFF2D3345.toInt() else 0xFF1D212E.toInt())
                guiGraphics.fill(evoCardX, y, evoCardX + evoCardW, y + 1, 0xFF54A0FF.toInt())
                guiGraphics.fill(evoCardX, y, evoCardX + evoCardW, y + evoCardH, 0xFF54A0FF.toInt())
                guiGraphics.fill(evoCardX, y, evoCardX + 1, y + evoCardH, 0xFF54A0FF.toInt())
                guiGraphics.fill(evoCardX + evoCardW - 1, y, evoCardX + evoCardW, y + evoCardH, 0xFF54A0FF.toInt())

                val preStack = PokemonItem.from(preSp.create())
                guiGraphics.renderItem(preStack, evoCardX + 3, y + 2)
                guiGraphics.drawString(font, "§b➜ ${preSp.name}§r §7(Cliquer pour afficher)§r", evoCardX + 22, y + 6, 0xFFFFFFFF.toInt(), false)
                rightPanelClickablePokemon.add(Triple(intArrayOf(evoCardX, y, evoCardW, evoCardH), preSp, false))
                y += evoCardH + 4
            }
        }

        // Explication générale
        val explLines = font.split(Component.literal("§7${advice.explanationSummary}§r"), contentW - 8)
        for (l in explLines) {
            guiGraphics.drawString(font, l, contentX + 4, y, 0xFFAAAAAA.toInt(), false)
            y += 9
        }
        y += 4

        // 3. Recette de base du Poké Snack (Chaudron de feu de camp)
        val potBoxH = 36
        guiGraphics.fill(contentX + 2, y, contentX + contentW - 2, y + potBoxH, 0xFF181B24.toInt())
        guiGraphics.fill(contentX + 2, y, contentX + contentW - 2, y + 1, 0xFF282D3C.toInt())
        guiGraphics.fill(contentX + 2, y + potBoxH - 1, contentX + contentW - 2, y + potBoxH, 0xFF282D3C.toInt())
        guiGraphics.fill(contentX + 2, y, contentX + 3, y + potBoxH, 0xFF282D3C.toInt())
        guiGraphics.fill(contentX + contentW - 3, y, contentX + contentW - 2, y + potBoxH, 0xFF282D3C.toInt())
        guiGraphics.drawString(font, "§6🥘 Recette de Base (Chaudron)§r", contentX + 6, y + 3, 0xFFFFAA00.toInt(), false)

        var itemX = contentX + 6
        for (ing in advice.potBaseIngredients) {
            guiGraphics.renderItem(ing, itemX, y + 15)
            guiGraphics.renderItemDecorations(font, ing, itemX, y + 15)
            if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= y + 15 && mouseY <= y + 31) {
                hoveredPokeSnackStack = ing
            }
            itemX += 20
        }

        guiGraphics.drawString(font, "§7+ 3 Assaisonnements :§r", itemX + 4, y + 19, 0xFF888888.toInt(), false)
        y += potBoxH + 6

        // 4. Les 3 meilleurs assaisonnements calculés
        guiGraphics.drawString(font, "§e§l3 Meilleurs Assaisonnements§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 12

        for (rec in advice.seasonings) {
            val cardX = contentX + 2
            val cardW = contentW - 4

            // Séparateur supérieur
            guiGraphics.fill(cardX, y, cardX + cardW, y + 1, 0xFF282D3C.toInt())
            y += 4

            // Icône de l'assaisonnement
            guiGraphics.renderItem(rec.itemStack, cardX + 4, y)
            guiGraphics.renderItemDecorations(font, rec.itemStack, cardX + 4, y)
            if (mouseX >= cardX + 4 && mouseX <= cardX + 20 && mouseY >= y && mouseY <= y + 16) {
                hoveredPokeSnackStack = rec.itemStack
            }

            // Titre et Rôle
            guiGraphics.drawString(font, "§6Slot ${rec.slotNumber} : §f${rec.itemStack.hoverName.string}§r", cardX + 24, y + 1, 0xFFFFFFFF.toInt(), false)
            val roleLines = font.split(Component.literal("§b${rec.roleDescription}§r"), cardW - 26)
            var ry = y + 11
            for (rl in roleLines) {
                guiGraphics.drawString(font, rl, cardX + 24, ry, 0xFFE0E0E0.toInt(), false)
                ry += 9
            }
            y = max(y + 20, ry + 2)

            // Comment le créer (Fabrication / Croisement)
            when (val create = rec.howToCreate) {
                is HowToCreate.BerryMutation -> {
                    guiGraphics.drawString(font, "§e🧬 Mutation :§r §f${create.description}§r", cardX + 6, y, 0xFFFFFFFF.toInt(), false)
                    y += 10
                    val mutY = y
                    guiGraphics.renderItem(create.parent1, cardX + 8, mutY)
                    guiGraphics.renderItemDecorations(font, create.parent1, cardX + 8, mutY)
                    if (mouseX >= cardX + 8 && mouseX <= cardX + 24 && mouseY >= mutY && mouseY <= mutY + 16) {
                        hoveredPokeSnackStack = create.parent1
                    }
                    guiGraphics.drawString(font, "§7+§r", cardX + 28, mutY + 4, 0xFFFFFFFF.toInt(), false)
                    guiGraphics.renderItem(create.parent2, cardX + 38, mutY)
                    guiGraphics.renderItemDecorations(font, create.parent2, cardX + 38, mutY)
                    if (mouseX >= cardX + 38 && mouseX <= cardX + 54 && mouseY >= mutY && mouseY <= mutY + 16) {
                        hoveredPokeSnackStack = create.parent2
                    }
                    guiGraphics.drawString(font, "§7➜§r", cardX + 58, mutY + 4, 0xFFFFFFFF.toInt(), false)
                    guiGraphics.renderItem(rec.itemStack, cardX + 70, mutY)
                    guiGraphics.renderItemDecorations(font, rec.itemStack, cardX + 70, mutY)
                    if (mouseX >= cardX + 70 && mouseX <= cardX + 86 && mouseY >= mutY && mouseY <= mutY + 16) {
                        hoveredPokeSnackStack = rec.itemStack
                    }
                    y += 19
                }
                is HowToCreate.Crafting -> {
                    guiGraphics.drawString(font, "§a🔨 Fabrication :§r §f${create.recipeDescription}§r", cardX + 6, y, 0xFFFFFFFF.toInt(), false)
                    y += 10
                }
                is HowToCreate.NonCraftable -> {
                    guiGraphics.drawString(font, "§7🔨 Fabrication :§r §8${create.reason}§r", cardX + 6, y, 0xFFAAAAAA.toInt(), false)
                    y += 10
                }
            }

            // Où le trouver (Biomes, Mob drops, Structures)
            val where = rec.whereToFind
            if (where.naturalBiomes.isNotEmpty()) {
                val bText = "§2🌲 Biomes sauvages :§r §e${where.naturalBiomes.take(4).joinToString(", ")}§r"
                val bLines = font.split(Component.literal(bText), cardW - 12)
                for (bl in bLines) {
                    guiGraphics.drawString(font, bl, cardX + 6, y, 0xFFFFFFFF.toInt(), false)
                    y += 9
                }
            }

            if (where.mobDrops.isNotEmpty()) {
                val dText = "§d⚔ Butin Pokémon :§r §f" + where.mobDrops.joinToString(", ") { "${it.speciesName} (${String.format("%.1f", it.percentage)}%)" } + "§r"
                val dLines = font.split(Component.literal(dText), cardW - 12)
                for (dl in dLines) {
                    guiGraphics.drawString(font, dl, cardX + 6, y, 0xFFFFFFFF.toInt(), false)
                    y += 9
                }
            }

            if (where.specialLocations.isNotEmpty()) {
                val sText = "§6🏛 Coffres :§r §f${where.specialLocations.take(3).joinToString(", ")}§r"
                val sLines = font.split(Component.literal(sText), cardW - 12)
                for (sl in sLines) {
                    guiGraphics.drawString(font, sl, cardX + 6, y, 0xFFFFFFFF.toInt(), false)
                    y += 9
                }
            }

            y += 5
        }

        // 5. Conseils pratiques Cobblemon
        val tipsH = 46
        guiGraphics.fill(contentX + 2, y, contentX + contentW - 2, y + tipsH, 0xFF141720.toInt())
        guiGraphics.fill(contentX + 2, y, contentX + contentW - 2, y + 1, 0xFF2D3345.toInt())
        guiGraphics.fill(contentX + 2, y + tipsH - 1, contentX + contentW - 2, y + tipsH, 0xFF2D3345.toInt())
        guiGraphics.fill(contentX + 2, y, contentX + 3, y + tipsH, 0xFF2D3345.toInt())
        guiGraphics.fill(contentX + contentW - 3, y, contentX + contentW - 2, y + tipsH, 0xFF2D3345.toInt())
        guiGraphics.drawString(font, "§e💡 Bon à savoir :§r", contentX + 6, y + 3, 0xFFFFFF55.toInt(), false)
        guiGraphics.drawString(font, "§7• Rayon d'action : 8 blocs autour du snack.§r", contentX + 6, y + 13, 0xFFCCCCCC.toInt(), false)
        guiGraphics.drawString(font, "§7• Durabilité : 9 bouchées (1 spawn / bouchée).§r", contentX + 6, y + 23, 0xFFCCCCCC.toInt(), false)
        guiGraphics.drawString(font, "§7• Les baies de type cumulent jusqu'à ×100 !§r", contentX + 6, y + 33, 0xFFCCCCCC.toInt(), false)
        y += tipsH + 6

        return y + 4
    }

    private fun formatBlockOrTagId(id: String): String {
        val clean = id.removePrefix("#").substringAfter(":")
        return clean.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
    }

    private fun renderTabEvolutions(guiGraphics: GuiGraphics, sp: Species, contentX: Int, startY: Int, contentW: Int): Int {
        var y = startY
        rightPanelClickablePokemon.clear()

        // 1. Forme source (Pré-évolutions)
        guiGraphics.drawString(font, "§e§l1. Forme(s) d'origine§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 12

        val allRecipes = PokemonEvolutionDataLoader.loadEvolutionRecipes()
        val preEvos = allRecipes.filter { it.targetSpecies == sp }

        if (preEvos.isEmpty()) {
            guiGraphics.drawString(font, "§7• Aucune pré-évolution (base)§r", contentX + 4, y, 0xFF888888.toInt(), false)
            y += 12
        } else {
            for (pre in preEvos) {
                val preSp = pre.sourceSpecies
                val stack = getItemStack(preSp, pre.sourceForm)
                if (!stack.isEmpty) {
                    guiGraphics.renderItem(stack, contentX + 2, y)
                }
                val preFormStr = if (pre.sourceForm != preSp.standardForm && pre.sourceForm.name.isNotBlank() && pre.sourceForm.name != "normal") {
                    " (${pre.sourceForm.name.replaceFirstChar(Char::titlecase)})"
                } else ""
                guiGraphics.drawString(font, "➔ §f§l${preSp.translatedName.string}$preFormStr§r", contentX + 20, y + 4, 0xFFFFFFFF.toInt(), false)
                rightPanelClickablePokemon.add(Triple(intArrayOf(contentX + 2, y, contentW - 4, 18), preSp, true))
                y += 18

                val condLines = font.split(Component.literal("  §e${pre.requirementsSummary}§r"), contentW - 8)
                for (l in condLines) {
                    guiGraphics.drawString(font, l, contentX + 2, y, 0xFFE0E0E0.toInt(), false)
                    y += 9
                }

                val ownedCount = allTracked.find { it.species == preSp }?.ownedInstances?.count {
                    PokemonTrackerService.isFormMatch(it.pokemon, pre.sourceForm, preSp)
                } ?: 0
                val resTxt = if (ownedCount > 0) "  §a✔ En stock dans le PC/Équipe ($ownedCount)§r" else "  §c✕ Aucun spécimen valide en réserve§r"
                guiGraphics.drawString(font, resTxt, contentX + 2, y, 0xFFFFFFFF.toInt(), false)
                y += 13
            }
        }

        y += 4
        // 2. Évolutions suivantes (Destination)
        guiGraphics.drawString(font, "§e§l2. Évolution(s) suivante(s)§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
        y += 12

        val nextEvos = allRecipes.filter { it.sourceSpecies == sp }

        if (nextEvos.isEmpty()) {
            guiGraphics.drawString(font, "§7• Aucune évolution suivante (final)§r", contentX + 4, y, 0xFF888888.toInt(), false)
            y += 12
        } else {
            for (next in nextEvos) {
                val tgtSp = next.targetSpecies
                val stack = getItemStack(tgtSp, next.targetForm)
                if (!stack.isEmpty) {
                    guiGraphics.renderItem(stack, contentX + 2, y)
                }
                val srcFormReqStr = if (next.sourceForm != sp.standardForm && next.sourceForm.name.isNotBlank() && next.sourceForm.name != "normal") {
                    " [Forme: ${next.sourceForm.name.replaceFirstChar(Char::titlecase)}]"
                } else ""
                val tgtFormStr = if (next.targetForm != tgtSp.standardForm && next.targetForm.name.isNotBlank() && next.targetForm.name != "normal") {
                    " (${next.targetForm.name.replaceFirstChar(Char::titlecase)})"
                } else ""
                guiGraphics.drawString(font, "➔ §f§l${tgtSp.translatedName.string}$tgtFormStr$srcFormReqStr§r", contentX + 20, y + 4, 0xFFFFFFFF.toInt(), false)
                rightPanelClickablePokemon.add(Triple(intArrayOf(contentX + 2, y, contentW - 4, 18), tgtSp, false))
                y += 18

                val condLines = font.split(Component.literal("  §e${next.requirementsSummary}§r"), contentW - 8)
                for (l in condLines) {
                    guiGraphics.drawString(font, l, contentX + 2, y, 0xFFE0E0E0.toInt(), false)
                    y += 9
                }

                val currentTracked = allTracked.find { it.species == sp }
                val hasReqForm = currentTracked?.ownedInstances?.any {
                    PokemonTrackerService.isFormMatch(it.pokemon, next.sourceForm, sp)
                } ?: false

                if (!hasReqForm && (currentTracked?.ownedCount ?: 0) > 0 && next.sourceForm != sp.standardForm && next.sourceForm.name.isNotBlank() && next.sourceForm.name != "normal") {
                    guiGraphics.drawString(font, "  §c⚠ Nécessite la forme ${next.sourceForm.name.replaceFirstChar(Char::titlecase)} !§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
                    y += 10
                }

                val isCaught = allTracked.find { it.species == tgtSp }?.isCaught == true
                val caughtTxt = if (isCaught) "  §a✔ Déjà enregistré au Pokédex§r" else "  §6✕ Non découvert au Pokédex§r"
                guiGraphics.drawString(font, caughtTxt, contentX + 2, y, 0xFFFFFFFF.toInt(), false)
                y += 13
            }
        }

        return y + 4
    }

    private fun renderZoomModal(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!isZoomModalOpen) return

        guiGraphics.flush()
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0f, 0f, 400f)

        val currentTracked = selectedTracked ?: allTracked.firstOrNull() ?: return
        val sp = currentTracked.species
        val allForms = getSpeciesForms(sp)
        val activeZoomForm = zoomModalForm ?: selectedForm ?: sp.standardForm
        val formIndex = allForms.indexOfFirst { it.name.equals(activeZoomForm.name, ignoreCase = true) }.coerceAtLeast(0)

        // 1. Fond sombre obscurcissant tout l'écran
        guiGraphics.fill(0, 0, width, height, 0xCC080A10.toInt())

        // 2. Fenêtre modale centrée
        val modalW = 240
        val modalH = 226
        val modalX = (width - modalW) / 2
        val modalY = (height - modalH) / 2

        // Fond et bordures de la modale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF141722.toInt())
        guiGraphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX - 1, modalY + modalH, modalX + modalW + 1, modalY + modalH + 1, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX - 1, modalY, modalX, modalY + modalH, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX + modalW, modalY, modalX + modalW + 1, modalY + modalH, 0xFF353C4D.toInt())

        // Barre de titre de la modale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xFF1A1F2C.toInt())
        guiGraphics.fill(modalX, modalY + 21, modalX + modalW, modalY + 22, 0xFF2A3144.toInt())

        val title = "§e§l${sp.translatedName.string}§r §7#${String.format("%04d", sp.nationalPokedexNumber)}§r"
        guiGraphics.drawString(font, title, modalX + 8, modalY + 7, 0xFFFFFFFF.toInt(), false)

        // Bouton Fermer [✕] en haut à droite
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        val isCloseHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14
        val closeColor = if (isCloseHovered) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
        guiGraphics.drawString(font, "§l✕§r", closeBtnX + 3, closeBtnY + 3, closeColor, false)

        // Sous-titre : Nom de la variante en grand
        val formDisplayName = getFormDisplayName(activeZoomForm, sp)
        guiGraphics.drawCenteredString(font, "§f§l$formDisplayName§r", modalX + modalW / 2, modalY + 27, 0xFFFFFFFF.toInt())

        // 3. Grande scène d'exposition 3D (Showcase)
        val stageW = 180
        val stageH = 100
        val stageX = modalX + (modalW - stageW) / 2
        val stageY = modalY + 40

        guiGraphics.fill(stageX, stageY, stageX + stageW, stageY + stageH, 0xFF0D0F17.toInt())
        guiGraphics.fill(stageX, stageY, stageX + stageW, stageY + 1, 0xFF2A3144.toInt())
        guiGraphics.fill(stageX, stageY + stageH - 1, stageX + stageW, stageY + stageH, 0xFF2A3144.toInt())
        guiGraphics.fill(stageX, stageY, stageX + 1, stageY + stageH, 0xFF2A3144.toInt())
        guiGraphics.fill(stageX + stageW - 1, stageY, stageX + stageW, stageY + stageH, 0xFF2A3144.toInt())

        // Modèle 3D rendu en grand format (Scale 5.0f -> 80x80px)
        val zoomStack = getItemStack(sp, activeZoomForm, zoomModalShiny)
        if (!zoomStack.isEmpty) {
            pose.pushPose()
            val modelScale = 5.0f
            val modelRenderSize = 16f * modelScale
            val modelX = stageX + (stageW - modelRenderSize) / 2f
            val modelY = stageY + (stageH - modelRenderSize) / 2f
            pose.translate(modelX, modelY, 0f)
            pose.scale(modelScale, modelScale, 1.0f)
            guiGraphics.renderItem(zoomStack, 0, 0)
            pose.popPose()
        }

        // 4. Ligne de contrôle : [ ◀ ]   [ ★ Shiny ]   [ ▶ ]
        val ctrlY = stageY + stageH + 6

        // Flèche Précédent [ ◀ ]
        val prevBtnX = stageX
        val prevBtnW = 24
        val prevBtnH = 15
        val isPrevHovered = mouseX >= prevBtnX && mouseX <= prevBtnX + prevBtnW && mouseY >= ctrlY && mouseY <= ctrlY + prevBtnH
        val prevBg = if (isPrevHovered) 0xFF353C4D.toInt() else 0xFF1D212E.toInt()
        val prevBorder = if (isPrevHovered) 0xFF54A0FF.toInt() else 0xFF2A3144.toInt()
        guiGraphics.fill(prevBtnX, ctrlY, prevBtnX + prevBtnW, ctrlY + prevBtnH, prevBg)
        guiGraphics.fill(prevBtnX, ctrlY, prevBtnX + prevBtnW, ctrlY + 1, prevBorder)
        guiGraphics.fill(prevBtnX, ctrlY + prevBtnH - 1, prevBtnX + prevBtnW, ctrlY + prevBtnH, prevBorder)
        guiGraphics.fill(prevBtnX, ctrlY, prevBtnX + 1, ctrlY + prevBtnH, prevBorder)
        guiGraphics.fill(prevBtnX + prevBtnW - 1, ctrlY, prevBtnX + prevBtnW, ctrlY + prevBtnH, prevBorder)
        guiGraphics.drawCenteredString(font, "◀", prevBtnX + prevBtnW / 2, ctrlY + 3, 0xFFFFFFFF.toInt())

        // Bouton Shiny au centre [ ★ Shiny ]
        val shinyBtnW = 68
        val shinyBtnH = 15
        val shinyBtnX = modalX + (modalW - shinyBtnW) / 2
        val isShinyHovered = mouseX >= shinyBtnX && mouseX <= shinyBtnX + shinyBtnW && mouseY >= ctrlY && mouseY <= ctrlY + shinyBtnH
        val shinyBg = when {
            zoomModalShiny -> if (isShinyHovered) 0xFFD4AC0D.toInt() else 0xFFB7950B.toInt()
            isShinyHovered -> 0xFF353C4D.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val shinyBorder = if (zoomModalShiny) 0xFFF1C40F.toInt() else 0xFF2A3144.toInt()
        val shinyText = if (zoomModalShiny) "§6★ Shiny§r" else "§7☆ Shiny§r"

        guiGraphics.fill(shinyBtnX, ctrlY, shinyBtnX + shinyBtnW, ctrlY + shinyBtnH, shinyBg)
        guiGraphics.fill(shinyBtnX, ctrlY, shinyBtnX + shinyBtnW, ctrlY + 1, shinyBorder)
        guiGraphics.fill(shinyBtnX, ctrlY, shinyBtnX + shinyBtnW, ctrlY + 1, shinyBorder)
        guiGraphics.fill(shinyBtnX, ctrlY + shinyBtnH - 1, shinyBtnX + shinyBtnW, ctrlY + shinyBtnH, shinyBorder)
        guiGraphics.fill(shinyBtnX, ctrlY, shinyBtnX + 1, ctrlY + shinyBtnH, shinyBorder)
        guiGraphics.fill(shinyBtnX + shinyBtnW - 1, ctrlY, shinyBtnX + shinyBtnW, ctrlY + shinyBtnH, shinyBorder)
        guiGraphics.drawCenteredString(font, shinyText, shinyBtnX + shinyBtnW / 2, ctrlY + 4, 0xFFFFFFFF.toInt())

        // Flèche Suivant [ ▶ ]
        val nextBtnX = stageX + stageW - 24
        val nextBtnW = 24
        val nextBtnH = 15
        val isNextHovered = mouseX >= nextBtnX && mouseX <= nextBtnX + nextBtnW && mouseY >= ctrlY && mouseY <= ctrlY + nextBtnH
        val nextBg = if (isNextHovered) 0xFF353C4D.toInt() else 0xFF1D212E.toInt()
        val nextBorder = if (isNextHovered) 0xFF54A0FF.toInt() else 0xFF2A3144.toInt()
        guiGraphics.fill(nextBtnX, ctrlY, nextBtnX + nextBtnW, ctrlY + nextBtnH, nextBg)
        guiGraphics.fill(nextBtnX, ctrlY, nextBtnX + nextBtnW, ctrlY + 1, nextBorder)
        guiGraphics.fill(nextBtnX, ctrlY + nextBtnH - 1, nextBtnX + nextBtnW, ctrlY + nextBtnH, nextBorder)
        guiGraphics.fill(nextBtnX, ctrlY, nextBtnX + 1, ctrlY + nextBtnH, nextBorder)
        guiGraphics.fill(nextBtnX + nextBtnW - 1, ctrlY, nextBtnX + nextBtnW, ctrlY + nextBtnH, nextBorder)
        guiGraphics.drawCenteredString(font, "▶", nextBtnX + nextBtnW / 2, ctrlY + 3, 0xFFFFFFFF.toInt())

        // 5. Compteur de variante : Variante X / Total
        guiGraphics.drawCenteredString(font, "§7Variante ${formIndex + 1} / ${allForms.size}§r", modalX + modalW / 2, ctrlY + 19, 0xFFAAAAAA.toInt())

        // 6. Types élémentaires de la forme
        val primColor = CobblemonOverlayManager.getElementColor(activeZoomForm.primaryType.name)
        val primName = activeZoomForm.primaryType.displayName.string.uppercase()
        val primW = font.width(primName) + 6
        val sec = activeZoomForm.secondaryType
        val totalTypeW = if (sec != null) primW + 4 + font.width(sec.displayName.string.uppercase()) + 6 else primW

        var typeStartX = modalX + (modalW - totalTypeW) / 2
        val typeY = ctrlY + 30
        guiGraphics.fill(typeStartX, typeY, typeStartX + primW, typeY + 10, primColor)
        guiGraphics.drawString(font, primName, typeStartX + 3, typeY + 1, 0xFFFFFFFF.toInt(), true)
        typeStartX += primW + 4

        if (sec != null) {
            val secColor = CobblemonOverlayManager.getElementColor(sec.name)
            val secName = sec.displayName.string.uppercase()
            val secW = font.width(secName) + 6
            guiGraphics.fill(typeStartX, typeY, typeStartX + secW, typeY + 10, secColor)
            guiGraphics.drawString(font, secName, typeStartX + 3, typeY + 1, 0xFFFFFFFF.toInt(), true)
        }

        // 7. Statut de capture / possession
        val statusInfo = getFormStatusInfo(sp, activeZoomForm, zoomModalShiny, currentTracked)
        guiGraphics.drawCenteredString(font, statusInfo.text, modalX + modalW / 2, ctrlY + 44, statusInfo.color)

        // 8. Bouton Fermer en bas
        val bottomCloseW = 90
        val bottomCloseH = 14
        val bottomCloseX = modalX + (modalW - bottomCloseW) / 2
        val bottomCloseY = modalY + modalH - 18
        val isBottomCloseHovered = mouseX >= bottomCloseX && mouseX <= bottomCloseX + bottomCloseW &&
                mouseY >= bottomCloseY && mouseY <= bottomCloseY + bottomCloseH
        val bCloseBg = if (isBottomCloseHovered) 0xFF353C4D.toInt() else 0xFF1D212E.toInt()
        val bCloseBorder = if (isBottomCloseHovered) 0xFF54A0FF.toInt() else 0xFF2A3144.toInt()

        guiGraphics.fill(bottomCloseX, bottomCloseY, bottomCloseX + bottomCloseW, bottomCloseY + bottomCloseH, bCloseBg)
        guiGraphics.fill(bottomCloseX, bottomCloseY, bottomCloseX + bottomCloseW, bottomCloseY + 1, bCloseBorder)
        guiGraphics.fill(bottomCloseX, bottomCloseY + bottomCloseH - 1, bottomCloseX + bottomCloseW, bottomCloseY + bottomCloseH, bCloseBorder)
        guiGraphics.fill(bottomCloseX, bottomCloseY, bottomCloseX + 1, bottomCloseY + bottomCloseH, bCloseBorder)
        guiGraphics.fill(bottomCloseX + bottomCloseW - 1, bottomCloseY, bottomCloseX + bottomCloseW, bottomCloseY + bottomCloseH, bCloseBorder)
        guiGraphics.drawCenteredString(font, "§7Fermer (Échap)§r", bottomCloseX + bottomCloseW / 2, bottomCloseY + 3, 0xFFFFFFFF.toInt())

        guiGraphics.flush()
        pose.popPose()
    }

    private fun handleZoomModalClick(mouseX: Double, mouseY: Double) {
        val modalW = 240
        val modalH = 226
        val modalX = (width - modalW) / 2
        val modalY = (height - modalH) / 2

        // Clic sur [ ✕ ] haut droite
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14) {
            isZoomModalOpen = false
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return
        }

        // Clic sur bouton fermer en bas
        val bottomCloseW = 90
        val bottomCloseH = 14
        val bottomCloseX = modalX + (modalW - bottomCloseW) / 2
        val bottomCloseY = modalY + modalH - 18
        if (mouseX >= bottomCloseX && mouseX <= bottomCloseX + bottomCloseW && mouseY >= bottomCloseY && mouseY <= bottomCloseY + bottomCloseH) {
            isZoomModalOpen = false
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return
        }

        val stageW = 180
        val stageH = 100
        val stageX = modalX + (modalW - stageW) / 2
        val stageY = modalY + 40
        val ctrlY = stageY + stageH + 6

        // [ ◀ ]
        val prevBtnX = stageX
        val prevBtnW = 24
        val prevBtnH = 15
        if (mouseX >= prevBtnX && mouseX <= prevBtnX + prevBtnW && mouseY >= ctrlY && mouseY <= ctrlY + prevBtnH) {
            cycleZoomModalForm(-1)
            return
        }

        // [ ▶ ]
        val nextBtnX = stageX + stageW - 24
        val nextBtnW = 24
        val nextBtnH = 15
        if (mouseX >= nextBtnX && mouseX <= nextBtnX + nextBtnW && mouseY >= ctrlY && mouseY <= ctrlY + nextBtnH) {
            cycleZoomModalForm(1)
            return
        }

        // [ ★ Shiny ]
        val shinyBtnW = 68
        val shinyBtnH = 15
        val shinyBtnX = modalX + (modalW - shinyBtnW) / 2
        if (mouseX >= shinyBtnX && mouseX <= shinyBtnX + shinyBtnW && mouseY >= ctrlY && mouseY <= ctrlY + shinyBtnH) {
            zoomModalShiny = !zoomModalShiny
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
            return
        }

        // Clic en dehors de la boîte modale : ferme la modale
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            isZoomModalOpen = false
            return
        }
    }

    private fun renderFilterModal(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!isFilterModalOpen) return

        activeModalTooltip = null

        guiGraphics.flush()
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0f, 0f, 400f)

        // 1. Fond sombre obscurcissant tout l'écran
        guiGraphics.fill(0, 0, width, height, 0xD0080A10.toInt())

        // 2. Fenêtre modale centrée
        val (modalX, modalY, modalW, modalH) = getFilterModalBounds()

        // Fond et bordures de la modale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF141722.toInt())
        guiGraphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX - 1, modalY + modalH, modalX + modalW + 1, modalY + modalH + 1, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX - 1, modalY, modalX, modalY + modalH, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX + modalW, modalY, modalX + modalW + 1, modalY + modalH, 0xFF353C4D.toInt())

        // Barre de titre de la modale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xFF1A1F2C.toInt())
        guiGraphics.fill(modalX, modalY + 21, modalX + modalW, modalY + 22, 0xFF2A3144.toInt())

        val title = "§6§lFILTRES DU POKÉDEX§r"
        guiGraphics.drawString(font, title, modalX + 8, modalY + 7, 0xFFFFFFFF.toInt(), false)

        val activeCount = filterState.activeCount()
        if (filterState.isActive()) {
            val badge = "§a($activeCount actif${if (activeCount > 1) "s" else ""})§r"
            guiGraphics.drawString(font, badge, modalX + 8 + font.width("FILTRES DU POKÉDEX ") + 4, modalY + 7, 0xFFFFFFFF.toInt(), false)
        }

        // Bouton Fermer [✕] en haut à droite
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        val isCloseHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14
        val closeColor = if (isCloseHovered) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
        guiGraphics.drawString(font, "§l✕§r", closeBtnX + 3, closeBtnY + 3, closeColor, false)

        // 3. Barre latérale gauche (Catégories)
        val sideX = modalX + 5
        val sideY = modalY + 24
        val sideW = 96
        val sideH = modalH - 48
        val categories = FilterCategory.values()
        val catH = sideH / categories.size

        guiGraphics.fill(sideX, sideY, sideX + sideW, sideY + sideH, 0xFF10131B.toInt())
        guiGraphics.fill(sideX + sideW - 1, sideY, sideX + sideW, sideY + sideH, 0xFF222736.toInt())

        for ((idx, cat) in categories.withIndex()) {
            val cy = sideY + idx * catH
            val ch = if (idx == categories.size - 1) (sideY + sideH - cy) else catH
            val isSel = (cat == activeFilterCategory)
            val isHovered = mouseX >= sideX && mouseX <= sideX + sideW && mouseY >= cy && mouseY <= cy + ch

            val isCatActive = when (cat) {
                FilterCategory.TYPE -> filterState.selectedTypes.isNotEmpty()
                FilterCategory.RARITY -> filterState.selectedRarity != SpawnRarityFilter.ALL
                FilterCategory.BIOME -> filterState.selectedBiome != CobblemonBiomeFilter.ALL
                FilterCategory.GEN -> filterState.selectedGen != GenerationFilter.ALL
                FilterCategory.STATUS -> filterState.selectedStatus != StatusFilter.ALL || filterState.selectedEvolution != EvolutionFilter.ALL
                FilterCategory.SORT -> filterState.selectedSort != PokedexSortOrder.DEX_ASC
            }

            if (isSel) {
                guiGraphics.fill(sideX, cy, sideX + sideW, cy + ch, 0xFF222A3B.toInt())
                guiGraphics.fill(sideX, cy, sideX + 3, cy + ch, 0xFFFFA502.toInt())
            } else if (isHovered) {
                guiGraphics.fill(sideX, cy, sideX + sideW, cy + ch, 0xFF181D29.toInt())
            }

            val labelColor = if (isSel) 0xFFFFFFFF.toInt() else if (isHovered) 0xFFCCCCCC.toInt() else 0xFF888888.toInt()
            val textY = cy + (ch - 8) / 2
            val textStr = "${cat.icon} ${cat.label}" + if (isCatActive) " §a●§r" else ""
            guiGraphics.drawString(font, textStr, sideX + 6, textY, labelColor, false)
        }

        // 4. Panneau de contenu à droite
        val paneX = sideX + sideW + 6
        val paneY = modalY + 24
        val paneW = modalX + modalW - paneX - 5
        val paneH = sideH

        guiGraphics.fill(paneX, paneY, paneX + paneW, paneY + paneH, 0xFF0D0F17.toInt())
        guiGraphics.fill(paneX, paneY, paneX + paneW, paneY + 1, 0xFF1E2331.toInt())
        guiGraphics.fill(paneX, paneY + paneH - 1, paneX + paneW, paneY + paneH, 0xFF1E2331.toInt())
        guiGraphics.fill(paneX, paneY, paneX + 1, paneY + paneH, 0xFF1E2331.toInt())
        guiGraphics.fill(paneX + paneW - 1, paneY, paneX + paneW, paneY + paneH, 0xFF1E2331.toInt())

        when (activeFilterCategory) {
            FilterCategory.TYPE -> renderTypeFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH)
            FilterCategory.RARITY -> renderRarityFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH)
            FilterCategory.BIOME -> renderBiomeFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH)
            FilterCategory.GEN -> renderGenFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH)
            FilterCategory.STATUS -> renderStatusFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH)
            FilterCategory.SORT -> renderSortFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH)
        }

        // 5. Barre inférieure (Actions & Compteur)
        val botY = modalY + modalH - 22
        guiGraphics.fill(modalX, botY, modalX + modalW, modalY + modalH, 0xFF161924.toInt())
        guiGraphics.fill(modalX, botY, modalX + modalW, botY + 1, 0xFF282D3C.toInt())

        // Bouton Réinitialiser
        val resetBtnX = modalX + 6
        val resetBtnY = botY + 3
        val resetBtnW = 84
        val resetBtnH = 15
        val isResetHov = mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= resetBtnY && mouseY <= resetBtnY + resetBtnH
        val resetBg = if (isResetHov) 0xFF4A1A1A.toInt() else 0xFF2A1515.toInt()
        val resetBorder = if (isResetHov) 0xFFFF5555.toInt() else 0xFF882222.toInt()
        guiGraphics.fill(resetBtnX, resetBtnY, resetBtnX + resetBtnW, resetBtnY + resetBtnH, resetBg)
        guiGraphics.fill(resetBtnX, resetBtnY, resetBtnX + resetBtnW, resetBtnY + 1, resetBorder)
        guiGraphics.fill(resetBtnX, resetBtnY + resetBtnH - 1, resetBtnX + resetBtnW, resetBtnY + resetBtnH, resetBorder)
        guiGraphics.fill(resetBtnX, resetBtnY, resetBtnX + 1, resetBtnY + resetBtnH, resetBorder)
        guiGraphics.fill(resetBtnX + resetBtnW - 1, resetBtnY, resetBtnX + resetBtnW, resetBtnY + resetBtnH, resetBorder)
        guiGraphics.drawCenteredString(font, "§c⟲ Tout effacer§r", resetBtnX + resetBtnW / 2, resetBtnY + 4, 0xFFFFFFFF.toInt())

        // Compteur de correspondances
        val countText = "§e${filteredTracked.size}§r §7correspondance(s)§r"
        guiGraphics.drawCenteredString(font, countText, modalX + modalW / 2 + 10, botY + 7, 0xFFCCCCCC.toInt())

        // Bouton Appliquer & Fermer
        val closeApplyW = 104
        val closeApplyX = modalX + modalW - closeApplyW - 6
        val closeApplyY = botY + 3
        val closeApplyH = 15
        val isApplyHov = mouseX >= closeApplyX && mouseX <= closeApplyX + closeApplyW && mouseY >= closeApplyY && mouseY <= closeApplyY + closeApplyH
        val applyBg = if (isApplyHov) 0xFF1E3A28.toInt() else 0xFF14261B.toInt()
        val applyBorder = if (isApplyHov) 0xFF2ECC71.toInt() else 0xFF1E824C.toInt()
        guiGraphics.fill(closeApplyX, closeApplyY, closeApplyX + closeApplyW, closeApplyY + closeApplyH, applyBg)
        guiGraphics.fill(closeApplyX, closeApplyY, closeApplyX + closeApplyW, closeApplyY + 1, applyBorder)
        guiGraphics.fill(closeApplyX, closeApplyY + closeApplyH - 1, closeApplyX + closeApplyW, closeApplyY + closeApplyH, applyBorder)
        guiGraphics.fill(closeApplyX, closeApplyY, closeApplyX + 1, closeApplyY + closeApplyH, applyBorder)
        guiGraphics.fill(closeApplyX + closeApplyW - 1, closeApplyY, closeApplyX + closeApplyW, closeApplyY + closeApplyH, applyBorder)
        guiGraphics.drawCenteredString(font, "§a✓ Appliquer§r", closeApplyX + closeApplyW / 2, closeApplyY + 4, 0xFFFFFFFF.toInt())

        if (activeModalTooltip != null) {
            guiGraphics.renderComponentTooltip(font, activeModalTooltip!!, mouseX, mouseY)
        }

        guiGraphics.flush()
        pose.popPose()
    }

    private fun renderTypeFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val selectedCount = filterState.selectedTypes.size
        val subtitle = if (selectedCount == 0) {
            "§7Types élémentaires : §fTous acceptés§r"
        } else {
            "§7Types sélectionnés : §a$selectedCount type(s) (OU)§r"
        }
        guiGraphics.drawString(font, subtitle, x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        if (selectedCount > 0) {
            val clearBtnX = x + w - 58
            val clearBtnY = y + 4
            val isClearHov = mouseX >= clearBtnX && mouseX <= clearBtnX + 52 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12
            val clearCol = if (isClearHov) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
            guiGraphics.drawString(font, "§c[✕ Tous]§r", clearBtnX, clearBtnY + 2, clearCol, false)
        }

        val allTypes = PokedexFilterManager.ALL_TYPES
        val colW = (w - 18) / 3
        val rowH = 16
        val startY = y + 18

        for ((idx, type) in allTypes.withIndex()) {
            val col = idx % 3
            val row = idx / 3
            val bx = x + 6 + col * (colW + 3)
            val by = startY + row * (rowH + 3)

            val isSel = filterState.selectedTypes.contains(type.id)
            val isHov = mouseX >= bx && mouseX <= bx + colW && mouseY >= by && mouseY <= by + rowH

            val bg = if (isSel) (0x55000000 or (type.color and 0xFFFFFF)) else if (isHov) 0xFF1D2230.toInt() else 0xFF141722.toInt()
            val border = if (isSel) type.color else if (isHov) 0xFF3E4860.toInt() else 0xFF242A3B.toInt()

            guiGraphics.fill(bx, by, bx + colW, by + rowH, bg)
            guiGraphics.fill(bx, by, bx + colW, by + 1, border)
            guiGraphics.fill(bx, by + rowH - 1, bx + colW, by + rowH, border)
            guiGraphics.fill(bx, by, bx + 1, by + rowH, border)
            guiGraphics.fill(bx + colW - 1, by, bx + colW, by + rowH, border)

            guiGraphics.fill(bx + 4, by + 5, bx + 10, by + 11, type.color)

            val label = if (isSel) "§l${type.displayName}§r" else type.displayName
            val textColor = if (isSel) 0xFFFFFFFF.toInt() else 0xFFCCCCCC.toInt()
            guiGraphics.drawString(font, label, bx + 14, by + 4, textColor, false)

            if (isSel) {
                guiGraphics.drawString(font, "§a✔§r", bx + colW - 10, by + 4, 0xFF55FF55.toInt(), false)
            }
        }
    }

    private fun renderRarityFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        guiGraphics.drawString(font, "§7Rareté des apparitions sauvages :§r", x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        val rarities = SpawnRarityFilter.values()
        val rarityCounts = getRarityCounts()
        val rowH = 20
        val startY = y + 20

        for ((idx, opt) in rarities.withIndex()) {
            val ry = startY + idx * (rowH + 2)
            val isSel = (filterState.selectedRarity == opt)
            val isHov = mouseX >= x + 6 && mouseX <= x + w - 6 && mouseY >= ry && mouseY <= ry + rowH

            val bg = if (isSel) 0xFF222B3D.toInt() else if (isHov) 0xFF181D29.toInt() else 0xFF12151F.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(x + 6, ry, x + w - 6, ry + rowH, bg)
            guiGraphics.fill(x + 6, ry, x + w - 6, ry + 1, border)
            guiGraphics.fill(x + 6, ry + rowH - 1, x + w - 6, ry + rowH, border)
            guiGraphics.fill(x + 6, ry, x + 7, ry + rowH, border)
            guiGraphics.fill(x + w - 7, ry, x + w - 6, ry + rowH, border)

            val radio = if (isSel) "§6● §r" else "§7○ §r"
            val title = "$radio${opt.formatCode}§l${opt.displayName}§r"
            guiGraphics.drawString(font, title, x + 12, ry + 6, 0xFFFFFFFF.toInt(), false)

            val counts = rarityCounts[opt] ?: PokedexFilterManager.FilterCount(0, 0)
            val missingColor = if (counts.missing > 0) "§c" else "§a"
            val countStr = "§7${counts.total} §8| $missingColor${counts.missing}§r"
            val countW = font.width(countStr)
            val countX = x + w - 6 - countW - 8
            guiGraphics.drawString(font, countStr, countX, ry + 6, 0xFFFFFFFF.toInt(), false)

            if (isHov) {
                activeModalTooltip = listOf(
                    Component.literal("${opt.formatCode}§l${opt.displayName}"),
                    Component.literal("§7Total : §f${counts.total} §7Pokémon"),
                    Component.literal(if (counts.missing > 0) "§cManquants : §f${counts.missing} §7à capturer" else "§a✔ Tous capturés (0 manquant)")
                )
            }
        }
    }

    private fun renderBiomeFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        val selectedName = filterState.selectedBiome.displayName
        guiGraphics.drawString(font, "§7Biome actif : §e$selectedName§r", x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        if (filterState.selectedBiome != CobblemonBiomeFilter.ALL) {
            val clearBtnX = x + w - 64
            val clearBtnY = y + 4
            val isClearHov = mouseX >= clearBtnX && mouseX <= clearBtnX + 58 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12
            val clearCol = if (isClearHov) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
            guiGraphics.drawString(font, "§c[✕ Réinit]§r", clearBtnX, clearBtnY + 2, clearCol, false)
        }

        // Barre de recherche de biome
        if (::biomeSearchBox.isInitialized) {
            biomeSearchBox.setPosition(x + 6, y + 20)
            biomeSearchBox.width = w - 12
            biomeSearchBox.render(guiGraphics, mouseX, mouseY, 0f)

            if (biomeSearchBox.value.isNotEmpty()) {
                val clearSearchX = x + w - 18
                val clearSearchY = y + 24
                val isClearHov = mouseX >= clearSearchX - 2 && mouseX <= clearSearchX + 8 && mouseY >= clearSearchY - 2 && mouseY <= clearSearchY + 10
                val col = if (isClearHov) 0xFFFF5555.toInt() else 0xFF888888.toInt()
                guiGraphics.drawString(font, "✕", clearSearchX, clearSearchY, col, false)
            }
        }

        val normQuery = if (::biomeSearchBox.isInitialized) normalizeText(biomeSearchBox.value) else ""
        val biomes = if (normQuery.isEmpty()) {
            CobblemonBiomeFilter.values().toList()
        } else {
            CobblemonBiomeFilter.values().filter { biome ->
                normalizeText(biome.displayName).contains(normQuery) ||
                biome.id.lowercase().contains(normQuery) ||
                biome.tagKeywords.any { normalizeText(it).contains(normQuery) }
            }
        }

        val listY = y + 40
        val listH = h - 44
        val rowH = 15

        if (biomes.isEmpty()) {
            val emptyMsg = "§7Aucun biome correspondant à \"${biomeSearchBox.value}\"§r"
            guiGraphics.drawCenteredString(font, emptyMsg, x + w / 2, listY + 20, 0xFF888888.toInt())
            biomeMaxScroll = 0.0
            biomeScrollOffset = 0.0
            return
        }

        val totalH = biomes.size * (rowH + 2)
        biomeMaxScroll = max(0.0, (totalH - listH).toDouble())
        biomeScrollOffset = biomeScrollOffset.coerceIn(0.0, biomeMaxScroll)

        val usableW = if (biomeMaxScroll > 0) w - 18 else w - 12
        val biomeCounts = getBiomeCounts()

        guiGraphics.enableScissor(x + 6, listY, x + w - 6, listY + listH)

        for ((idx, opt) in biomes.withIndex()) {
            val by = listY + idx * (rowH + 2) - biomeScrollOffset.toInt()
            if (by + rowH < listY || by > listY + listH) continue

            val isSel = (filterState.selectedBiome == opt)
            val isHov = mouseX >= x + 6 && mouseX <= x + 6 + usableW && mouseY >= by && mouseY <= by + rowH

            val bg = if (isSel) 0xFF232B3D.toInt() else if (isHov) 0xFF191E2A.toInt() else 0xFF12151E.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(x + 6, by, x + 6 + usableW, by + rowH, bg)
            guiGraphics.fill(x + 6, by, x + 6 + usableW, by + 1, border)
            guiGraphics.fill(x + 6, by + rowH - 1, x + 6 + usableW, by + rowH, border)
            guiGraphics.fill(x + 6, by, x + 7, by + rowH, border)
            guiGraphics.fill(x + 5 + usableW, by, x + 6 + usableW, by + rowH, border)

            val radio = if (isSel) "§6● " else "§7○ "
            val text = "$radio${opt.icon} ${if (isSel) "§e§l${opt.displayName}§r" else opt.displayName}"
            guiGraphics.drawString(font, text, x + 10, by + 4, 0xFFFFFFFF.toInt(), false)

            // Affichage en bout de ligne : Nombre total dans le biome et nombre manquant (en couleur, pas de texte)
            val counts = biomeCounts[opt] ?: PokedexFilterManager.FilterCount(0, 0)
            val missingColor = if (counts.missing > 0) "§c" else "§a"
            val countStr = "§7${counts.total} §8| $missingColor${counts.missing}§r"
            val countW = font.width(countStr)
            val countX = x + 6 + usableW - countW - 6
            guiGraphics.drawString(font, countStr, countX, by + 4, 0xFFFFFFFF.toInt(), false)

            if (isHov) {
                activeModalTooltip = listOf(
                    Component.literal("§e${opt.displayName}"),
                    Component.literal("§7Total : §f${counts.total} §7Pokémon"),
                    Component.literal(if (counts.missing > 0) "§cManquants : §f${counts.missing} §7à capturer" else "§a✔ Tous capturés (0 manquant)")
                )
            }
        }

        guiGraphics.disableScissor()

        // Barre de défilement
        if (biomeMaxScroll > 0) {
            val sbX = x + w - 10
            val sbW = 4
            guiGraphics.fill(sbX, listY, sbX + sbW, listY + listH, 0xFF141720.toInt())

            val thumbH = max(16, (listH * listH) / totalH)
            val thumbY = listY + ((biomeScrollOffset / biomeMaxScroll) * (listH - thumbH)).toInt()
            val thumbBg = if (isDraggingBiomeScrollbar) 0xFFFFA502.toInt() else 0xFF4A5568.toInt()
            guiGraphics.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, thumbBg)
        }
    }

    private fun renderGenFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        guiGraphics.drawString(font, "§7Génération et région d'origine du Pokémon :§r", x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        val gens = GenerationFilter.values()
        val genCounts = getGenCounts()
        val colW = (w - 18) / 2
        val rowH = 21
        val startY = y + 18

        for ((idx, gen) in gens.withIndex()) {
            val col = idx % 2
            val row = idx / 2
            val gx = x + 6 + col * (colW + 4)
            val gy = startY + row * (rowH + 2)

            val isSel = (filterState.selectedGen == gen)
            val isHov = mouseX >= gx && mouseX <= gx + colW && mouseY >= gy && mouseY <= gy + rowH

            val bg = if (isSel) 0xFF232B3D.toInt() else if (isHov) 0xFF181D29.toInt() else 0xFF12151E.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(gx, gy, gx + colW, gy + rowH, bg)
            guiGraphics.fill(gx, gy, gx + colW, gy + 1, border)
            guiGraphics.fill(gx, gy + rowH - 1, gx + colW, gy + rowH, border)
            guiGraphics.fill(gx, gy, gx + 1, gy + rowH, border)
            guiGraphics.fill(gx + colW - 1, gy, gx + colW, gy + rowH, border)

            val radio = if (isSel) "§6● " else "§7○ "
            val title = "$radio${if (isSel) "§e§l${gen.displayName}§r" else gen.displayName}"
            guiGraphics.drawString(font, title, gx + 6, gy + 6, 0xFFFFFFFF.toInt(), false)

            val counts = genCounts[gen] ?: PokedexFilterManager.FilterCount(0, 0)
            val missingColor = if (counts.missing > 0) "§c" else "§a"
            val countStr = "§7${counts.total} §8| $missingColor${counts.missing}§r"
            val countW = font.width(countStr)
            val countX = gx + colW - countW - 5
            guiGraphics.drawString(font, countStr, countX, gy + 6, 0xFFFFFFFF.toInt(), false)

            if (isHov) {
                activeModalTooltip = listOf(
                    Component.literal("§e${gen.displayName}"),
                    Component.literal("§7Total : §f${counts.total} §7Pokémon"),
                    Component.literal(if (counts.missing > 0) "§cManquants : §f${counts.missing} §7à capturer" else "§a✔ Tous capturés (0 manquant)")
                )
            }
        }
    }

    private fun renderStatusFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        guiGraphics.drawString(font, "§e§l1. Statut dans le Pokédex :§r", x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        val statuses = StatusFilter.values()
        val sColW = (w - 20) / 4
        val sRowH = 20
        val sY = y + 18

        for ((idx, st) in statuses.withIndex()) {
            val sx = x + 6 + idx * (sColW + 3)
            val isSel = (filterState.selectedStatus == st)
            val isHov = mouseX >= sx && mouseX <= sx + sColW && mouseY >= sY && mouseY <= sY + sRowH

            val bg = if (isSel) 0xFF232B3D.toInt() else if (isHov) 0xFF181D29.toInt() else 0xFF12151E.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(sx, sY, sx + sColW, sY + sRowH, bg)
            guiGraphics.fill(sx, sY, sx + sColW, sY + 1, border)
            guiGraphics.fill(sx, sY + sRowH - 1, sx + sColW, sY + sRowH, border)
            guiGraphics.fill(sx, sY, sx + 1, sY + sRowH, border)
            guiGraphics.fill(sx + sColW - 1, sY, sx + sColW, sY + sRowH, border)

            val label = when (st) {
                StatusFilter.ALL -> "Tous"
                StatusFilter.CAUGHT -> "§a✔ Capturés§r"
                StatusFilter.ENCOUNTERED -> "§6👁 Vus§r"
                StatusFilter.MISSING -> "§c❓ Manquants§r"
            }
            guiGraphics.drawCenteredString(font, label, sx + sColW / 2, sY + 6, 0xFFFFFFFF.toInt())
        }

        val evoSectionY = y + 46
        guiGraphics.drawString(font, "§e§l2. Stade et potentiel évolutif :§r", x + 6, evoSectionY, 0xFFFFFFFF.toInt(), false)

        val evos = EvolutionFilter.values()
        val eRowH = 18
        val eStartY = evoSectionY + 12

        for ((idx, evo) in evos.withIndex()) {
            val ey = eStartY + idx * (eRowH + 2)
            val isSel = (filterState.selectedEvolution == evo)
            val isHov = mouseX >= x + 6 && mouseX <= x + w - 6 && mouseY >= ey && mouseY <= ey + eRowH

            val bg = if (isSel) 0xFF232B3D.toInt() else if (isHov) 0xFF181D29.toInt() else 0xFF12151E.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(x + 6, ey, x + w - 6, ey + eRowH, bg)
            guiGraphics.fill(x + 6, ey, x + w - 6, ey + 1, border)
            guiGraphics.fill(x + 6, ey + eRowH - 1, x + w - 6, ey + eRowH, border)
            guiGraphics.fill(x + 6, ey, x + 7, ey + eRowH, border)
            guiGraphics.fill(x + w - 7, ey, x + w - 6, ey + eRowH, border)

            val radio = if (isSel) "§6● " else "§7○ "
            val title = "$radio${if (isSel) "§e§l${evo.displayName}§r" else evo.displayName}"
            guiGraphics.drawString(font, title, x + 12, ey + 5, 0xFFFFFFFF.toInt(), false)
        }
    }

    private fun renderSortFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int) {
        guiGraphics.drawString(font, "§7Ordre d'affichage des Pokémon dans la grille :§r", x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        val orders = PokedexSortOrder.values()
        val rowH = 20
        val startY = y + 20

        for ((idx, sort) in orders.withIndex()) {
            val sy = startY + idx * (rowH + 2)
            val isSel = (filterState.selectedSort == sort)
            val isHov = mouseX >= x + 6 && mouseX <= x + w - 6 && mouseY >= sy && mouseY <= sy + rowH

            val bg = if (isSel) 0xFF232B3D.toInt() else if (isHov) 0xFF181D29.toInt() else 0xFF12151E.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(x + 6, sy, x + w - 6, sy + rowH, bg)
            guiGraphics.fill(x + 6, sy, x + w - 6, sy + 1, border)
            guiGraphics.fill(x + 6, sy + rowH - 1, x + w - 6, sy + rowH, border)
            guiGraphics.fill(x + 6, sy, x + 7, sy + rowH, border)
            guiGraphics.fill(x + w - 7, sy, x + w - 6, sy + rowH, border)

            val radio = if (isSel) "§6● " else "§7○ "
            val title = "$radio${if (isSel) "§e§l${sort.displayName}§r" else sort.displayName}"
            guiGraphics.drawString(font, title, x + 12, sy + 6, 0xFFFFFFFF.toInt(), false)
        }
    }

    private fun handleFilterModalClick(mouseX: Double, mouseY: Double) {
        val (modalX, modalY, modalW, modalH) = getFilterModalBounds()

        // 1. Bouton Fermer [✕] en haut à droite
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14) {
            closeFilterModal()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return
        }

        // 2. Barre inférieure
        val botY = modalY + modalH - 22

        // [⟲ Tout effacer]
        val resetBtnX = modalX + 6
        val resetBtnY = botY + 3
        val resetBtnW = 84
        val resetBtnH = 15
        if (mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= resetBtnY && mouseY <= resetBtnY + resetBtnH) {
            filterState.reset()
            applyFilters()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
            return
        }

        // [✓ Appliquer & Fermer]
        val closeApplyW = 104
        val closeApplyX = modalX + modalW - closeApplyW - 6
        val closeApplyY = botY + 3
        val closeApplyH = 15
        if (mouseX >= closeApplyX && mouseX <= closeApplyX + closeApplyW && mouseY >= closeApplyY && mouseY <= closeApplyY + closeApplyH) {
            closeFilterModal()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return
        }

        // 3. Barre latérale gauche (Catégories)
        val sideX = modalX + 5
        val sideY = modalY + 24
        val sideW = 96
        val sideH = modalH - 48
        val categories = FilterCategory.values()
        val catH = sideH / categories.size

        if (mouseX >= sideX && mouseX <= sideX + sideW && mouseY >= sideY && mouseY <= sideY + sideH) {
            val idx = ((mouseY - sideY) / catH).toInt().coerceIn(0, categories.size - 1)
            activeFilterCategory = categories[idx]
            updateFilterModalWidgetVisibility()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
            return
        }

        // 4. Panneau de contenu
        val paneX = sideX + sideW + 6
        val paneY = modalY + 24
        val paneW = modalX + modalW - paneX - 5
        val paneH = sideH

        if (mouseX >= paneX && mouseX <= paneX + paneW && mouseY >= paneY && mouseY <= paneY + paneH) {
            when (activeFilterCategory) {
                FilterCategory.TYPE -> {
                    val clearBtnX = paneX + paneW - 58
                    val clearBtnY = paneY + 4
                    if (mouseX >= clearBtnX && mouseX <= clearBtnX + 52 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12) {
                        filterState.selectedTypes.clear()
                        applyFilters()
                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
                        return
                    }

                    val allTypes = PokedexFilterManager.ALL_TYPES
                    val colW = (paneW - 18) / 3
                    val rowH = 16
                    val startY = paneY + 18

                    for ((idx, type) in allTypes.withIndex()) {
                        val col = idx % 3
                        val row = idx / 3
                        val bx = paneX + 6 + col * (colW + 3)
                        val by = startY + row * (rowH + 3)

                        if (mouseX >= bx && mouseX <= bx + colW && mouseY >= by && mouseY <= by + rowH) {
                            if (filterState.selectedTypes.contains(type.id)) {
                                filterState.selectedTypes.remove(type.id)
                            } else {
                                filterState.selectedTypes.add(type.id)
                            }
                            applyFilters()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
                            return
                        }
                    }
                }
                FilterCategory.RARITY -> {
                    val rarities = SpawnRarityFilter.values()
                    val rowH = 20
                    val startY = paneY + 20

                    for ((idx, opt) in rarities.withIndex()) {
                        val ry = startY + idx * (rowH + 2)
                        if (mouseX >= paneX + 6 && mouseX <= paneX + paneW - 6 && mouseY >= ry && mouseY <= ry + rowH) {
                            filterState.selectedRarity = opt
                            applyFilters()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                            return
                        }
                    }
                }
                FilterCategory.BIOME -> {
                    val clearBtnX = paneX + paneW - 64
                    val clearBtnY = paneY + 4
                    if (mouseX >= clearBtnX && mouseX <= clearBtnX + 58 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12) {
                        filterState.selectedBiome = CobblemonBiomeFilter.ALL
                        applyFilters()
                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
                        return
                    }

                    // Clic sur le bouton [✕] de vidage de recherche biome
                    if (::biomeSearchBox.isInitialized && biomeSearchBox.value.isNotEmpty()) {
                        val clearSearchX = paneX + paneW - 18
                        val clearSearchY = paneY + 24
                        if (mouseX >= clearSearchX - 4 && mouseX <= clearSearchX + 10 && mouseY >= clearSearchY - 4 && mouseY <= clearSearchY + 12) {
                            biomeSearchBox.value = ""
                            biomeScrollOffset = 0.0
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
                            return
                        }
                    }

                    // Clic sur le champ de recherche biome
                    val searchBoxX = paneX + 6
                    val searchBoxY = paneY + 20
                    val searchBoxW = paneW - 12
                    val searchBoxH = 16
                    if (::biomeSearchBox.isInitialized) {
                        if (mouseX >= searchBoxX && mouseX <= searchBoxX + searchBoxW && mouseY >= searchBoxY && mouseY <= searchBoxY + searchBoxH) {
                            biomeSearchBox.isFocused = true
                            biomeSearchBox.mouseClicked(mouseX, mouseY, 0)
                            return
                        } else {
                            biomeSearchBox.isFocused = false
                        }
                    }

                    val normQuery = if (::biomeSearchBox.isInitialized) normalizeText(biomeSearchBox.value) else ""
                    val biomes = if (normQuery.isEmpty()) {
                        CobblemonBiomeFilter.values().toList()
                    } else {
                        CobblemonBiomeFilter.values().filter { biome ->
                            normalizeText(biome.displayName).contains(normQuery) ||
                            biome.id.lowercase().contains(normQuery) ||
                            biome.tagKeywords.any { normalizeText(it).contains(normQuery) }
                        }
                    }

                    val listY = paneY + 40
                    val listH = paneH - 44
                    val rowH = 15
                    val usableW = if (biomeMaxScroll > 0) paneW - 18 else paneW - 12

                    if (biomeMaxScroll > 0 && mouseX >= paneX + paneW - 12 && mouseX <= paneX + paneW) {
                        isDraggingBiomeScrollbar = true
                        dragStartBiomeMouseY = mouseY
                        dragStartBiomeScroll = biomeScrollOffset
                        return
                    }

                    if (mouseY >= listY && mouseY <= listY + listH) {
                        for ((idx, opt) in biomes.withIndex()) {
                            val by = listY + idx * (rowH + 2) - biomeScrollOffset.toInt()
                            if (by + rowH < listY || by > listY + listH) continue

                            if (mouseX >= paneX + 6 && mouseX <= paneX + 6 + usableW && mouseY >= by && mouseY <= by + rowH) {
                                filterState.selectedBiome = opt
                                applyFilters()
                                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                                return
                            }
                        }
                    }
                }
                FilterCategory.GEN -> {
                    val gens = GenerationFilter.values()
                    val colW = (paneW - 18) / 2
                    val rowH = 21
                    val startY = paneY + 18

                    for ((idx, gen) in gens.withIndex()) {
                        val col = idx % 2
                        val row = idx / 2
                        val gx = paneX + 6 + col * (colW + 4)
                        val gy = startY + row * (rowH + 2)

                        if (mouseX >= gx && mouseX <= gx + colW && mouseY >= gy && mouseY <= gy + rowH) {
                            filterState.selectedGen = gen
                            applyFilters()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                            return
                        }
                    }
                }
                FilterCategory.STATUS -> {
                    val statuses = StatusFilter.values()
                    val sColW = (paneW - 20) / 4
                    val sRowH = 20
                    val sY = paneY + 18

                    for ((idx, st) in statuses.withIndex()) {
                        val sx = paneX + 6 + idx * (sColW + 3)
                        if (mouseX >= sx && mouseX <= sx + sColW && mouseY >= sY && mouseY <= sY + sRowH) {
                            filterState.selectedStatus = st
                            applyFilters()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                            return
                        }
                    }

                    val evoSectionY = paneY + 46
                    val evos = EvolutionFilter.values()
                    val eRowH = 18
                    val eStartY = evoSectionY + 12

                    for ((idx, evo) in evos.withIndex()) {
                        val ey = eStartY + idx * (eRowH + 2)
                        if (mouseX >= paneX + 6 && mouseX <= paneX + paneW - 6 && mouseY >= ey && mouseY <= ey + eRowH) {
                            filterState.selectedEvolution = evo
                            applyFilters()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                            return
                        }
                    }
                }
                FilterCategory.SORT -> {
                    val orders = PokedexSortOrder.values()
                    val rowH = 20
                    val startY = paneY + 20

                    for ((idx, sort) in orders.withIndex()) {
                        val sy = startY + idx * (rowH + 2)
                        if (mouseX >= paneX + 6 && mouseX <= paneX + paneW - 6 && mouseY >= sy && mouseY <= sy + rowH) {
                            filterState.selectedSort = sort
                            applyFilters()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                            return
                        }
                    }
                }
            }
            return
        }

        // Clic en dehors de la boîte modale : ferme la modale
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            closeFilterModal()
            return
        }
    }

    private fun getTrackerModalBounds(): IntArray {
        val modalW = 340
        val modalH = 260
        val modalX = (width - modalW) / 2
        val modalY = (height - modalH) / 2
        return intArrayOf(modalX, modalY, modalW, modalH)
    }

    fun openTrackerModal() {
        isTrackerModalOpen = true
        searchBox.active = false
        trackerWatchlistScrollOffset = 0.0
    }

    fun closeTrackerModal() {
        isTrackerModalOpen = false
        searchBox.active = true
        updateTrackerButton()
    }

    private fun renderTrackerModal(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!isTrackerModalOpen) return

        guiGraphics.flush()
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0f, 0f, 400f)

        // 1. Fond sombre obscurcissant tout l'écran
        guiGraphics.fill(0, 0, width, height, 0xD0080A10.toInt())

        // 2. Fenêtre modale centrée
        val (modalX, modalY, modalW, modalH) = getTrackerModalBounds()

        // Fond et bordures de la modale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF141722.toInt())
        guiGraphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX - 1, modalY + modalH, modalX + modalW + 1, modalY + modalH + 1, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX - 1, modalY, modalX, modalY + modalH, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX + modalW, modalY, modalX + modalW + 1, modalY + modalH, 0xFF353C4D.toInt())

        // Barre de titre de la modale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xFF1A1F2C.toInt())
        guiGraphics.fill(modalX, modalY + 21, modalX + modalW, modalY + 22, 0xFF2A3144.toInt())

        val title = "§6§lRADAR & TRACKER DE POKÉMON§r"
        guiGraphics.drawString(font, title, modalX + 8, modalY + 7, 0xFFFFFFFF.toInt(), false)

        // Bouton Fermer [✕] en haut à droite
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        val isCloseHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14
        val closeColor = if (isCloseHovered) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
        guiGraphics.drawString(font, "§l✕§r", closeBtnX + 3, closeBtnY + 3, closeColor, false)

        // 3. Bandeau d'état actuel
        val statusY = modalY + 26
        guiGraphics.fill(modalX + 8, statusY, modalX + modalW - 8, statusY + 22, 0xFF181C28.toInt())
        guiGraphics.fill(modalX + 8, statusY, modalX + modalW - 8, statusY + 1, 0xFF2A3144.toInt())
        guiGraphics.fill(modalX + 8, statusY + 21, modalX + modalW - 8, statusY + 22, 0xFF2A3144.toInt())

        val modeLabel = when (PokemonTrackingManager.currentMode) {
            TrackingMode.OFF -> "§7Inactif (Sentinelle Shiny active)"
            TrackingMode.ALL_UNCAUGHT -> "§aTous les Pokémon non capturés"
            TrackingMode.SINGLE -> "§bCible : §f${PokemonTrackingManager.targetSpecies?.translatedName?.string ?: "Inconnu"}"
            TrackingMode.EVOLUTION_LINE -> "§dFamille de §f${PokemonTrackingManager.evolutionBaseSpecies?.translatedName?.string ?: "Inconnu"} §7(${PokemonTrackingManager.evolutionFamily.size})"
            TrackingMode.CUSTOM_LIST -> "§eListe personnalisée §7(${PokemonTrackingManager.customWatchlist.size} Pokémon)"
        }
        guiGraphics.drawString(font, "Mode actif : $modeLabel", modalX + 12, statusY + 4, 0xFFFFFFFF.toInt(), false)

        val detCount = PokemonRadarService.detectedCount
        val shinyCount = PokemonRadarService.shinyNearbyCount
        val radarInfo = when {
            shinyCount > 0 -> "§6★ $shinyCount Shiny§r | §a$detCount cible(s) à portée (160m)"
            detCount > 0 -> "§a$detCount cible(s) à portée du radar (160m)"
            else -> "§7Aucun spécimen traqué à portée"
        }
        guiGraphics.drawString(font, radarInfo, modalX + 12, statusY + 13, 0xFFBBBBBB.toInt(), false)

        val curSp = selectedTracked?.species
        val curName = curSp?.translatedName?.string ?: "ce Pokémon"

        // 4. Choix de Tracking par défaut
        val btnRow1Y = modalY + 52
        val fullW = modalW - 16

        // Choix 1 : Tracker tous les non-capturés
        val isUncaughtActive = PokemonTrackingManager.currentMode == TrackingMode.ALL_UNCAUGHT
        val isB1Hovered = mouseX >= modalX + 8 && mouseX <= modalX + 8 + fullW && mouseY >= btnRow1Y && mouseY <= btnRow1Y + 16
        val b1Bg = when {
            isUncaughtActive -> if (isB1Hovered) 0xFF27AE60.toInt() else 0xFF2ECC71.toInt()
            isB1Hovered -> 0xFF2D3345.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val b1Border = if (isUncaughtActive) 0xFF2ECC71.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(modalX + 8, btnRow1Y, modalX + 8 + fullW, btnRow1Y + 16, b1Bg)
        guiGraphics.fill(modalX + 8, btnRow1Y, modalX + 8 + fullW, btnRow1Y + 1, b1Border)
        guiGraphics.fill(modalX + 8, btnRow1Y + 15, modalX + 8 + fullW, btnRow1Y + 16, b1Border)
        val b1Text = if (isUncaughtActive) "✔ Tracker TOUS les non-capturés (Actif)" else "🔍 Tracker TOUS les Pokémon non capturés"
        guiGraphics.drawCenteredString(font, b1Text, modalX + modalW / 2, btnRow1Y + 4, 0xFFFFFFFF.toInt())

        // Choix 2 & 3 : Tracker ce Pokémon OU sa lignée évolutive
        val btnRow2Y = modalY + 72
        val halfW = (fullW - 4) / 2

        // Choix 2 : Tracker ce Pokémon
        val isSingleActive = PokemonTrackingManager.currentMode == TrackingMode.SINGLE && PokemonTrackingManager.targetSpecies == curSp
        val isB2Hovered = mouseX >= modalX + 8 && mouseX <= modalX + 8 + halfW && mouseY >= btnRow2Y && mouseY <= btnRow2Y + 16
        val b2Bg = when {
            isSingleActive -> if (isB2Hovered) 0xFF2980B9.toInt() else 0xFF3498DB.toInt()
            isB2Hovered -> 0xFF2D3345.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val b2Border = if (isSingleActive) 0xFF54A0FF.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(modalX + 8, btnRow2Y, modalX + 8 + halfW, btnRow2Y + 16, b2Bg)
        guiGraphics.fill(modalX + 8, btnRow2Y, modalX + 8 + halfW, btnRow2Y + 1, b2Border)
        guiGraphics.fill(modalX + 8, btnRow2Y + 15, modalX + 8 + halfW, btnRow2Y + 16, b2Border)
        val b2Short = font.plainSubstrByWidth(curName, halfW - 24)
        val b2Text = if (isSingleActive) "✔ $b2Short" else "🎯 $b2Short"
        guiGraphics.drawCenteredString(font, b2Text, modalX + 8 + halfW / 2, btnRow2Y + 4, 0xFFFFFFFF.toInt())

        // Choix 3 : Tracker toute la lignée évolutive
        val isLineActive = PokemonTrackingManager.currentMode == TrackingMode.EVOLUTION_LINE && PokemonTrackingManager.evolutionBaseSpecies == curSp
        val b3X = modalX + 8 + halfW + 4
        val isB3Hovered = mouseX >= b3X && mouseX <= b3X + halfW && mouseY >= btnRow2Y && mouseY <= btnRow2Y + 16
        val b3Bg = when {
            isLineActive -> if (isB3Hovered) 0xFF8E44AD.toInt() else 0xFF9B59B6.toInt()
            isB3Hovered -> 0xFF2D3345.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val b3Border = if (isLineActive) 0xFFBE2EDD.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(b3X, btnRow2Y, b3X + halfW, btnRow2Y + 16, b3Bg)
        guiGraphics.fill(b3X, btnRow2Y, b3X + halfW, btnRow2Y + 1, b3Border)
        guiGraphics.fill(b3X, btnRow2Y + 15, b3X + halfW, btnRow2Y + 16, b3Border)
        val b3Text = if (isLineActive) "✔ Lignée ($b2Short)" else "🧬 Lignée ($b2Short)"
        guiGraphics.drawCenteredString(font, b3Text, b3X + halfW / 2, btnRow2Y + 4, 0xFFFFFFFF.toInt())

        // Choix 4 & 5 : Activer la liste OU Arrêter le tracking
        val btnRow3Y = modalY + 92
        val isListActive = PokemonTrackingManager.currentMode == TrackingMode.CUSTOM_LIST
        val isB4Hovered = mouseX >= modalX + 8 && mouseX <= modalX + 8 + halfW && mouseY >= btnRow3Y && mouseY <= btnRow3Y + 16
        val b4Bg = when {
            isListActive -> if (isB4Hovered) 0xFFD35400.toInt() else 0xFFE67E22.toInt()
            isB4Hovered -> 0xFF2D3345.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val b4Border = if (isListActive) 0xFFFFAA00.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(modalX + 8, btnRow3Y, modalX + 8 + halfW, btnRow3Y + 16, b4Bg)
        guiGraphics.fill(modalX + 8, btnRow3Y, modalX + 8 + halfW, btnRow3Y + 1, b4Border)
        guiGraphics.fill(modalX + 8, btnRow3Y + 15, modalX + 8 + halfW, btnRow3Y + 16, b4Border)
        val b4Text = if (isListActive) "✔ Ma Liste (${PokemonTrackingManager.customWatchlist.size})" else "⭐ Suivre Ma Liste (${PokemonTrackingManager.customWatchlist.size})"
        guiGraphics.drawCenteredString(font, b4Text, modalX + 8 + halfW / 2, btnRow3Y + 4, 0xFFFFFFFF.toInt())

        // Bouton Arrêter le tracking
        val isB5Hovered = mouseX >= b3X && mouseX <= b3X + halfW && mouseY >= btnRow3Y && mouseY <= btnRow3Y + 16
        val b5Bg = if (isB5Hovered) 0xFF992222.toInt() else 0xFF551111.toInt()
        guiGraphics.fill(b3X, btnRow3Y, b3X + halfW, btnRow3Y + 16, b5Bg)
        guiGraphics.fill(b3X, btnRow3Y, b3X + halfW, btnRow3Y + 1, 0xFFE74C3C.toInt())
        guiGraphics.fill(b3X, btnRow3Y + 15, b3X + halfW, btnRow3Y + 16, 0xFFE74C3C.toInt())
        guiGraphics.drawCenteredString(font, "✕ Arrêter le suivi", b3X + halfW / 2, btnRow3Y + 4, 0xFFFFFFFF.toInt())

        // 5. Section Liste personnalisée (Watchlist)
        val listSectionY = modalY + 114
        guiGraphics.fill(modalX + 8, listSectionY, modalX + modalW - 8, listSectionY + 1, 0xFF282D3C.toInt())

        val listTitle = "§e§lMA LISTE DE TRACKING§r §7(${PokemonTrackingManager.customWatchlist.size})§r"
        guiGraphics.drawString(font, listTitle, modalX + 10, listSectionY + 6, 0xFFFFFFFF.toInt(), false)

        // Bouton [+ Ajouter / - Retirer]
        val isInList = curSp != null && PokemonTrackingManager.isInWatchlist(curSp)
        val toggleBtnW = 100
        val toggleBtnX = modalX + modalW - 10 - toggleBtnW
        val toggleBtnY = listSectionY + 4
        val isToggleHovered = mouseX >= toggleBtnX && mouseX <= toggleBtnX + toggleBtnW && mouseY >= toggleBtnY && mouseY <= toggleBtnY + 14
        val toggleBg = if (isInList) {
            if (isToggleHovered) 0xFFC0392B.toInt() else 0xFF7F1D1D.toInt()
        } else {
            if (isToggleHovered) 0xFF27AE60.toInt() else 0xFF1E3A2B.toInt()
        }
        guiGraphics.fill(toggleBtnX, toggleBtnY, toggleBtnX + toggleBtnW, toggleBtnY + 14, toggleBg)
        val toggleText = if (isInList) "− Retirer $b2Short" else "+ Ajouter $b2Short"
        guiGraphics.drawCenteredString(font, toggleText, toggleBtnX + toggleBtnW / 2, toggleBtnY + 3, 0xFFFFFFFF.toInt())

        // Bouton Vider la liste
        if (PokemonTrackingManager.customWatchlist.isNotEmpty()) {
            val clearBtnX = toggleBtnX - 44
            val isClearHovered = mouseX >= clearBtnX && mouseX <= clearBtnX + 40 && mouseY >= toggleBtnY && mouseY <= toggleBtnY + 14
            val clearBg = if (isClearHovered) 0xFF882222.toInt() else 0xFF441818.toInt()
            guiGraphics.fill(clearBtnX, toggleBtnY, clearBtnX + 40, toggleBtnY + 14, clearBg)
            guiGraphics.drawCenteredString(font, "Vider", clearBtnX + 20, toggleBtnY + 3, 0xFFFFAAAA.toInt())
        }

        // Cadre de liste scrollable
        val boxX = modalX + 8
        val boxY = listSectionY + 22
        val boxW = modalW - 16
        val boxH = modalH - (boxY - modalY) - 8

        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF0D0F16.toInt())
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF1F2432.toInt())
        guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF1F2432.toInt())
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, 0xFF1F2432.toInt())
        guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFF1F2432.toInt())

        val list = PokemonTrackingManager.customWatchlist.toList()
        if (list.isEmpty()) {
            val emptyMsg1 = "§7Votre liste de tracking est actuellement vide."
            val emptyMsg2 = "§8Cliquez sur \"+ Ajouter\" ou sur l'étoile ⭐ pour y inscrire un Pokémon."
            guiGraphics.drawCenteredString(font, emptyMsg1, boxX + boxW / 2, boxY + boxH / 2 - 8, 0xFF888888.toInt())
            guiGraphics.drawCenteredString(font, emptyMsg2, boxX + boxW / 2, boxY + boxH / 2 + 3, 0xFF666666.toInt())
        } else {
            val rowH = 19
            val totalContentH = list.size * rowH
            trackerWatchlistMaxScroll = max(0.0, (totalContentH - boxH).toDouble())

            guiGraphics.enableScissor(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + boxH - 1)
            val startY = boxY + 2 - trackerWatchlistScrollOffset.toInt()

            for ((idx, spInList) in list.withIndex()) {
                val rowY = startY + idx * rowH
                if (rowY + rowH < boxY || rowY > boxY + boxH) continue

                val isRowHovered = mouseX >= boxX + 2 && mouseX <= boxX + boxW - 20 && mouseY >= rowY && mouseY <= rowY + rowH - 1
                val rowBg = if (isRowHovered) 0xFF1B202D.toInt() else if (idx % 2 == 0) 0xFF131722.toInt() else 0xFF0F121C.toInt()
                guiGraphics.fill(boxX + 2, rowY, boxX + boxW - 2, rowY + rowH - 1, rowBg)

                // Rendu item 3D
                val itemStack = getItemStack(spInList, spInList.standardForm)
                if (!itemStack.isEmpty) {
                    guiGraphics.renderItem(itemStack, boxX + 4, rowY + 1)
                }

                val dexNum = String.format("#%04d", spInList.nationalPokedexNumber)
                guiGraphics.drawString(font, "§6$dexNum§r §f${spInList.translatedName.string}§r", boxX + 24, rowY + 5, 0xFFFFFFFF.toInt(), false)

                // Bouton de suppression [✕]
                val delX = boxX + boxW - 16
                val isDelHovered = mouseX >= delX - 2 && mouseX <= delX + 10 && mouseY >= rowY + 2 && mouseY <= rowY + 14
                val delColor = if (isDelHovered) 0xFFFF4444.toInt() else 0xFF777777.toInt()
                guiGraphics.drawString(font, "✕", delX, rowY + 5, delColor, false)
            }

            guiGraphics.disableScissor()

            // Scrollbar si la liste dépasse
            if (trackerWatchlistMaxScroll > 0) {
                val sbX = boxX + boxW - 4
                val sbH = max(10, (boxH * boxH / totalContentH))
                val sbY = boxY + ((boxH - sbH) * (trackerWatchlistScrollOffset / trackerWatchlistMaxScroll)).toInt()
                guiGraphics.fill(sbX, boxY, sbX + 2, boxY + boxH, 0xFF181C28.toInt())
                guiGraphics.fill(sbX, sbY, sbX + 2, sbY + sbH, 0xFF54A0FF.toInt())
            }
        }

        pose.popPose()
    }

    private fun handleTrackerModalClick(mouseX: Double, mouseY: Double) {
        val (modalX, modalY, modalW, modalH) = getTrackerModalBounds()

        // 1. Bouton Fermer [✕]
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14) {
            closeTrackerModal()
            return
        }

        // Clic en dehors de la fenêtre modale ferme la modale
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            closeTrackerModal()
            return
        }

        val curSp = selectedTracked?.species
        val fullW = modalW - 16
        val halfW = (fullW - 4) / 2

        // 2. Bouton 1 : Tracker TOUS les non-capturés
        val btnRow1Y = modalY + 52
        if (mouseX >= modalX + 8 && mouseX <= modalX + 8 + fullW && mouseY >= btnRow1Y && mouseY <= btnRow1Y + 16) {
            if (PokemonTrackingManager.currentMode == TrackingMode.ALL_UNCAUGHT) {
                PokemonTrackingManager.stopTracking()
            } else {
                PokemonTrackingManager.trackAllUncaught()
            }
            updateTrackerButton()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return
        }

        // 3. Bouton 2 : Tracker ce Pokémon
        val btnRow2Y = modalY + 72
        if (mouseX >= modalX + 8 && mouseX <= modalX + 8 + halfW && mouseY >= btnRow2Y && mouseY <= btnRow2Y + 16) {
            if (curSp != null) {
                if (PokemonTrackingManager.currentMode == TrackingMode.SINGLE && PokemonTrackingManager.targetSpecies == curSp) {
                    PokemonTrackingManager.stopTracking()
                } else {
                    PokemonTrackingManager.trackSingle(curSp, selectedForm)
                }
                updateTrackerButton()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            }
            return
        }

        // 4. Bouton 3 : Tracker la lignée évolutive
        val b3X = modalX + 8 + halfW + 4
        if (mouseX >= b3X && mouseX <= b3X + halfW && mouseY >= btnRow2Y && mouseY <= btnRow2Y + 16) {
            if (curSp != null) {
                if (PokemonTrackingManager.currentMode == TrackingMode.EVOLUTION_LINE && PokemonTrackingManager.evolutionBaseSpecies == curSp) {
                    PokemonTrackingManager.stopTracking()
                } else {
                    PokemonTrackingManager.trackEvolutionLine(curSp)
                }
                updateTrackerButton()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            }
            return
        }

        // 5. Bouton 4 : Suivre ma liste
        val btnRow3Y = modalY + 92
        if (mouseX >= modalX + 8 && mouseX <= modalX + 8 + halfW && mouseY >= btnRow3Y && mouseY <= btnRow3Y + 16) {
            if (PokemonTrackingManager.currentMode == TrackingMode.CUSTOM_LIST) {
                PokemonTrackingManager.stopTracking()
            } else {
                PokemonTrackingManager.trackCustomList()
            }
            updateTrackerButton()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return
        }

        // 6. Bouton 5 : Arrêter le suivi
        if (mouseX >= b3X && mouseX <= b3X + halfW && mouseY >= btnRow3Y && mouseY <= btnRow3Y + 16) {
            PokemonTrackingManager.stopTracking()
            updateTrackerButton()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8f))
            return
        }

        // 7. Bouton [+ Ajouter / - Retirer] dans la liste
        val listSectionY = modalY + 114
        val toggleBtnW = 100
        val toggleBtnX = modalX + modalW - 10 - toggleBtnW
        val toggleBtnY = listSectionY + 4
        if (mouseX >= toggleBtnX && mouseX <= toggleBtnX + toggleBtnW && mouseY >= toggleBtnY && mouseY <= toggleBtnY + 14) {
            if (curSp != null) {
                PokemonTrackingManager.toggleWatchlist(curSp)
                updateTrackerButton()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
            }
            return
        }

        // 8. Bouton [Vider] la liste
        if (PokemonTrackingManager.customWatchlist.isNotEmpty()) {
            val clearBtnX = toggleBtnX - 44
            if (mouseX >= clearBtnX && mouseX <= clearBtnX + 40 && mouseY >= toggleBtnY && mouseY <= toggleBtnY + 14) {
                PokemonTrackingManager.clearWatchlist()
                updateTrackerButton()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8f))
                return
            }
        }

        // 9. Clics dans les éléments de la liste scrollable
        val boxX = modalX + 8
        val boxY = listSectionY + 22
        val boxW = modalW - 16
        val boxH = modalH - (boxY - modalY) - 8

        if (mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH) {
            val list = PokemonTrackingManager.customWatchlist.toList()
            val rowH = 19
            val startY = boxY + 2 - trackerWatchlistScrollOffset.toInt()

            for ((idx, spInList) in list.withIndex()) {
                val rowY = startY + idx * rowH
                if (mouseY >= rowY && mouseY <= rowY + rowH - 1) {
                    val delX = boxX + boxW - 16
                    if (mouseX >= delX - 4 && mouseX <= delX + 12) {
                        // Clic sur [✕]
                        PokemonTrackingManager.removeFromWatchlist(spInList)
                        updateTrackerButton()
                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
                    } else {
                        // Clic sur la ligne -> sélectionne le Pokémon dans l'écran Pokédex
                        val tracked = allTracked.find { it.species == spInList }
                        if (tracked != null) {
                            selectedTracked = tracked
                            selectedForm = spInList.standardForm
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                        }
                    }
                    return
                }
            }
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        // Si la modale du Tracker est ouverte, elle capture tous les clics
        if (isTrackerModalOpen) {
            handleTrackerModalClick(mouseX, mouseY)
            return true
        }

        // Si la modale des filtres est ouverte, elle capture tous les clics
        if (isFilterModalOpen) {
            handleFilterModalClick(mouseX, mouseY)
            return true
        }

        // Si la modale Grand Format est ouverte, elle capture tous les clics
        if (isZoomModalOpen) {
            handleZoomModalClick(mouseX, mouseY)
            return true
        }

        // Clic sur boutons de la fiche du Pokémon (Watchlist ⭐ et Tracker 🎯)
        watchlistHeaderButtonRect?.let { (bx, by, bw, bh) ->
            if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                selectedTracked?.species?.let {
                    PokemonTrackingManager.toggleWatchlist(it)
                    updateTrackerButton()
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
                }
                return true
            }
        }

        trackHeaderButtonRect?.let { (bx, by, bw, bh) ->
            if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                openTrackerModal()
                return true
            }
        }

        // Clic sur le cadre de preview en haut du panneau droit (ouvre Grand Format)
        topPreviewRect?.let { (px, py, pw, ph) ->
            if (mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + ph) {
                openZoomModal(selectedForm ?: selectedTracked?.species?.standardForm)
                return true
            }
        }

        // Clic sur les onglets du panneau droit
        for (tab in RightPanelTab.values()) {
            val (tx, ty, tw, th) = getRightPanelTabRect(tab)
            if (mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY <= ty + th) {
                currentRightPanelTab = tab
                rightPanelScrollOffset = 0.0
                return true
            }
        }

        val (panelX, panelY, panelW, panelH) = getRightPanelBounds()
        val contentY = panelY + 107
        val contentH = (panelY + panelH - 26) - contentY
        val scrollTrackX = panelX + panelW - 5

        // Clics dans l'onglet Formes (Zoom header, Shiny header, Zoom carte, Sélection carte, Shiny carte)
        if (currentRightPanelTab == RightPanelTab.FORMS) {
            zoomHeaderButtonRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    openZoomModal(selectedForm ?: selectedTracked?.species?.standardForm)
                    return true
                }
            }

            shinyHeaderButtonRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    globalShinyForms = !globalShinyForms
                    individualShinyForms.clear()
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
                    return true
                }
            }

            if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= contentY && mouseY <= contentY + contentH) {
                // 1. Clic sur bouton Zoom [🔍] d'une carte
                for ((rect, form) in zoomCardButtonRects) {
                    val (bx, by, bw, bh) = rect
                    if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                        openZoomModal(form)
                        return true
                    }
                }

                // 2. Clic sur le corps de la carte pour la sélectionner
                for ((rect, form) in selectCardButtonRects) {
                    val (bx, by, bw, bh) = rect
                    if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                        selectedForm = form
                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
                        return true
                    }
                }

                // 3. Clic sur le bouton étoile / sprite pour basculer shiny individuel
                for ((rect, form) in shinyCardButtonRects) {
                    val (bx, by, bw, bh) = rect
                    if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                        toggleFormShiny(form)
                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                        return true
                    }
                }
            }
        }

        // Clics dans l'onglet Zones (Sélecteur Naturel vs Poké Snack)
        if (currentRightPanelTab == RightPanelTab.SPAWNS) {
            spawnCategoryNaturalBtnRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    currentSpawnCategory = SpawnSubCategory.NATURAL
                    rightPanelScrollOffset = 0.0
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                    return true
                }
            }
            spawnCategorySnackBtnRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    currentSpawnCategory = SpawnSubCategory.POKE_SNACK
                    rightPanelScrollOffset = 0.0
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                    return true
                }
            }
        }

        // Clic sur la barre de défilement du panneau droit
        if (rightPanelMaxScroll > 0 && mouseX >= scrollTrackX - 3 && mouseX <= scrollTrackX + 5 && mouseY >= contentY && mouseY <= contentY + contentH) {
            isDraggingRightScrollbar = true
            dragStartRightMouseY = mouseY
            dragStartRightScroll = rightPanelScrollOffset
            return true
        }

        // Clic sur un Pokémon dans l'onglet Évolutions ou Zones pour naviguer vers lui
        if ((currentRightPanelTab == RightPanelTab.EVOLUTIONS || currentRightPanelTab == RightPanelTab.SPAWNS) && mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= contentY && mouseY <= contentY + contentH) {
            for ((rect, clickedSp, _) in rightPanelClickablePokemon) {
                val (rx, ry, rw, rh) = rect
                if (mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh) {
                    val found = allTracked.find { it.species == clickedSp }
                    if (found != null) {
                        selectedTracked = found
                        selectedForm = null
                        individualShinyForms.clear()
                        zoomModalForm = null
                        rightPanelScrollOffset = 0.0
                        return true
                    }
                }
            }
        }

        // Clic sur les boutons en bas du panneau droit (Tracker et JourneyMap)
        val btnY = panelY + panelH - 22
        val availW = panelW - 16
        val halfW = (availW - 4) / 2
        val trackBtnX = panelX + 8
        val jmBtnX = trackBtnX + halfW + 4

        if (mouseY >= btnY && mouseY <= btnY + 16) {
            if (mouseX >= trackBtnX && mouseX <= trackBtnX + halfW) {
                openTrackerModal()
                return true
            } else if (mouseX >= jmBtnX && mouseX <= jmBtnX + halfW) {
                selectedTracked?.let {
                    CobblemonOverlayManager.setOverlay(it.species, selectedForm ?: it.species.standardForm)
                }
                return true
            }
        }

        val (leftX, topY, areaW, areaH) = getLeftAreaBounds()

        // 1. Clic sur la scrollbar gauche
        val scrollbarX = leftX + areaW - 8
        if (mouseX >= scrollbarX && mouseX <= leftX + areaW && mouseY >= topY && mouseY <= topY + areaH) {
            isDraggingScrollbar = true
            dragStartMouseY = mouseY
            dragStartScroll = scrollOffset
            return true
        }

        // 2. Clic dans la zone de contenu gauche
        if (mouseX >= leftX && mouseX <= leftX + areaW - 8 && mouseY >= topY && mouseY <= topY + areaH) {
            val scrolledY = topY + 4 - scrollOffset.toInt()

            if (currentTab == Tab.EVOLUTIONS) {
                val index = ((mouseY - scrolledY) / (evoCardHeight + 4)).toInt()
                if (index in filteredGoals.indices) {
                    val goal = filteredGoals[index]
                    selectedGoal = goal
                    selectedTracked = allTracked.find { it.species == goal.targetSpecies }
                    selectedForm = null
                    individualShinyForms.clear()
                    zoomModalForm = null
                    rightPanelScrollOffset = 0.0
                    return true
                }
            } else {
                val usableW = areaW - 14
                val cols = max(1, usableW / (cardWidth + cardGap))
                val col = ((mouseX - (leftX + 4)) / (cardWidth + cardGap)).toInt()
                val row = ((mouseY - scrolledY) / (cardHeight + cardGap)).toInt()
                if (col in 0 until cols && row >= 0) {
                    val index = row * cols + col
                    if (index in filteredTracked.indices) {
                        selectedTracked = filteredTracked[index]
                        selectedForm = null
                        individualShinyForms.clear()
                        zoomModalForm = null
                        rightPanelScrollOffset = 0.0
                        return true
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isFilterModalOpen && isDraggingBiomeScrollbar && biomeMaxScroll > 0) {
            val (modalX, modalY, modalW, modalH) = getFilterModalBounds()
            val sideW = 96
            val paneX = modalX + 5 + sideW + 6
            val paneY = modalY + 24
            val paneH = modalH - 48
            val listH = paneH - 44
            val totalTrackH = listH - 4
            val delta = mouseY - dragStartBiomeMouseY
            val scrollDelta = (delta / totalTrackH) * biomeMaxScroll
            biomeScrollOffset = (dragStartBiomeScroll + scrollDelta).coerceIn(0.0, biomeMaxScroll)
            return true
        }
        if (isDraggingScrollbar && maxScroll > 0) {
            val (_, topY, _, areaH) = getLeftAreaBounds()
            val totalTrackH = areaH - 4
            val delta = mouseY - dragStartMouseY
            val scrollDelta = (delta / totalTrackH) * maxScroll
            scrollOffset = (dragStartScroll + scrollDelta).coerceIn(0.0, maxScroll)
            return true
        }
        if (isDraggingRightScrollbar && rightPanelMaxScroll > 0) {
            val (_, panelY, _, panelH) = getRightPanelBounds()
            val contentY = panelY + 107
            val contentH = (panelY + panelH - 26) - contentY
            val totalTrackH = contentH - 4
            val delta = mouseY - dragStartRightMouseY
            val scrollDelta = (delta / totalTrackH) * rightPanelMaxScroll
            rightPanelScrollOffset = (dragStartRightScroll + scrollDelta).coerceIn(0.0, rightPanelMaxScroll)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            isDraggingScrollbar = false
            isDraggingRightScrollbar = false
            isDraggingBiomeScrollbar = false
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (isTrackerModalOpen) {
            trackerWatchlistScrollOffset = (trackerWatchlistScrollOffset - scrollY * 18.0).coerceAtLeast(0.0)
            return true
        }
        if (isFilterModalOpen) {
            if (activeFilterCategory == FilterCategory.BIOME && biomeMaxScroll > 0) {
                biomeScrollOffset = (biomeScrollOffset - scrollY * 20.0).coerceIn(0.0, biomeMaxScroll)
                return true
            }
            return true
        }
        if (isZoomModalOpen) {
            cycleZoomModalForm(if (scrollY > 0) -1 else 1)
            return true
        }

        val (panelX, panelY, panelW, panelH) = getRightPanelBounds()
        val contentY = panelY + 107
        val contentH = (panelY + panelH - 26) - contentY
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= contentY && mouseY <= contentY + contentH) {
            rightPanelScrollOffset = (rightPanelScrollOffset - scrollY * 20.0).coerceIn(0.0, rightPanelMaxScroll)
            return true
        }

        val (leftX, topY, areaW, areaH) = getLeftAreaBounds()
        if (mouseX >= leftX && mouseX <= leftX + areaW && mouseY >= topY && mouseY <= topY + areaH) {
            scrollOffset = (scrollOffset - scrollY * 24.0).coerceIn(0.0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (isFilterModalOpen) {
            if (activeFilterCategory == FilterCategory.BIOME && ::biomeSearchBox.isInitialized && biomeSearchBox.isFocused) {
                if (biomeSearchBox.charTyped(codePoint, modifiers)) {
                    return true
                }
            }
            return true
        }
        return super.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (isTrackerModalOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeTrackerModal()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                return true
            }
            return true
        }
        if (isFilterModalOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeFilterModal()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                return true
            }
            if (activeFilterCategory == FilterCategory.BIOME && ::biomeSearchBox.isInitialized && biomeSearchBox.isFocused) {
                if (biomeSearchBox.keyPressed(keyCode, scanCode, modifiers)) {
                    return true
                }
            }
            return true
        }
        if (isZoomModalOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                isZoomModalOpen = false
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                cycleZoomModalForm(-1)
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                cycleZoomModalForm(1)
                return true
            }
            return true
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        if (!searchBox.isFocused && CobblemonKeyBindings.OPEN_POKEDEX_KEY.matches(keyCode, scanCode)) {
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
