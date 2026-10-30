package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.compat.rct.CobblemonRctProgressionScreen
import fr.k0bus.cobblemon_jei.compat.rct.RctCompat
import fr.k0bus.cobblemon_jei.compat.rct.RctDataLoader
import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionSuggestion
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import fr.k0bus.cobblemon_jei.leaderboard.CobblemonLeaderboardScreen
import fr.k0bus.cobblemon_jei.leaderboard.CobblemonScoreboardService
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Interface graphique complète du Pokédex & Tracker d'évolutions.
 * Propose une navigation hiérarchique avec boutons custom (DA sombre).
 */
class CobblemonPokedexScreen(
    private val parentScreen: Screen? = null
) : Screen(Component.translatable("gui.cobblemon_jei.pokedex.title")) {

    private val state = PokedexStateHolder()
    private val itemStackCache = ConcurrentHashMap<Triple<Species, FormData, Boolean>, ItemStack>()

    private var currentMainTab by state::currentMainTab
    private var currentPokedexSubTab by state::currentPokedexSubTab
    private var currentEvolutionSubTab by state::currentEvolutionSubTab
    private var currentTab by state::currentTab
    private var onlyPcFeasibleEvolutions by state::onlyPcFeasibleEvolutions
    private val filterState get() = state.filterState

    private var allTracked by state::allTracked
    private var filteredTracked by state::filteredTracked
    private var allGoals by state::allGoals
    private var filteredGoals by state::filteredGoals
    private var allItemGoals by state::allItemGoals
    private var filteredItemGoals by state::filteredItemGoals
    private var allBreedingOrigins by state::allBreedingOrigins
    private var filteredBreedingOrigins by state::filteredBreedingOrigins
    private var allFossilGoals by state::allFossilGoals
    private var filteredFossilGoals by state::filteredFossilGoals
    private var allFossilSpecies by state::allFossilSpecies
    private var filteredFossils by state::filteredFossils
    private var allLegendarySpecies by state::allLegendarySpecies
    private var filteredLegendaries by state::filteredLegendaries

    private var selectedTracked by state::selectedTracked
    private var selectedGoal by state::selectedGoal
    private var selectedForm by state::selectedForm



    // Boutons d'interface Custom
    private lateinit var searchBox: EditBox
    private lateinit var mainPokedexBtn: CobblemonDarkButton
    private lateinit var mainEvolutionsBtn: CobblemonDarkButton
    private lateinit var mainExpeditionsBtn: CobblemonDarkButton

    private lateinit var subPokedexAllBtn: CobblemonDarkButton
    private lateinit var subPokedexMissingBtn: CobblemonDarkButton
    private lateinit var subEvoPendingBtn: CobblemonDarkButton
    private lateinit var subEvoItemsBtn: CobblemonDarkButton
    private lateinit var subEvoOriginsBtn: CobblemonDarkButton
    private lateinit var subEvoFossilsBtn: CobblemonDarkButton
    private lateinit var subEvoLegendariesBtn: CobblemonDarkButton
    private lateinit var togglePcEvolutionsBtn: CobblemonDarkButton
    private lateinit var refreshExpeditionsBtn: CobblemonDarkButton

    private lateinit var trackerBtn: CobblemonDarkButton
    private var rctBtn: CobblemonDarkButton? = null
    private var leaderboardBtn: CobblemonDarkButton? = null
    private lateinit var settingsBtn: CobblemonDarkButton
    private lateinit var closeBtn: CobblemonDarkButton
    private lateinit var filterBtn: CobblemonDarkButton
    private lateinit var quickResetFilterBtn: CobblemonDarkButton

    // Gestion de la modale des Expéditions intégrées
    private var isExpeditionLoading: Boolean = false
    private var expeditionSuggestions: List<ExpeditionSuggestion> = emptyList()

    // Widgets overlays et modales autonomes
    private val targetsDetailModal = PokemonTargetsDetailModalWidget(
        getItemStack = { sp, shiny -> getItemStack(sp, shiny = shiny) },
        isPokemonCaught = { sp -> allTracked.find { it.species.resourceIdentifier == sp.resourceIdentifier }?.isCaught == true },
        isPokemonEncountered = { sp -> allTracked.find { it.species.resourceIdentifier == sp.resourceIdentifier }?.isEncountered == true },
        onCloseCallback = { if (!isAnyModalOpen) searchBox.active = true }
    )

    private val zoomModal = PokemonZoomModalWidget(
        getItemStack = { sp, form, shiny -> getItemStack(sp, form, shiny) },
        isFormShiny = { false },
        onFormChanged = { form -> selectedForm = form },
        onCloseCallback = { if (!isAnyModalOpen) searchBox.active = true }
    )

    private val trackerModal = CobblemonTrackerModalWidget(
        getCurrentTracked = { selectedTracked },
        getSelectedForm = { selectedForm },
        getAllTracked = { allTracked },
        getItemStack = { sp, form -> getItemStack(sp, form) },
        onSelectTracked = { tracked, form ->
            selectedTracked = tracked
            selectedForm = form ?: tracked.species.standardForm
        },
        onOpenExpeditions = { switchMainTab(MainTab.EXPEDITIONS) },
        onOpenTargetsDetail = { title, sub, targets -> openTargetsDetailModal(title, sub, targets) },
        onTrackerStateChanged = { updateTrackerButton() },
        onCloseCallback = { if (!isAnyModalOpen) searchBox.active = true }
    )

    private val filterModal = PokemonFilterModalWidget(
        filterState = filterState,
        getFilteredCount = { filteredTracked.size },
        getBiomeCounts = { state.getBiomeCounts() },
        getRarityCounts = { state.getRarityCounts() },
        getGenCounts = { state.getGenCounts() },
        onFiltersChanged = { applyFilters() },
        onCloseCallback = {
            if (!isAnyModalOpen) searchBox.active = true
            updateFilterButton()
        }
    )

    private val expeditionModal = PokemonExpeditionModalWidget(
        onOpenTargetsDetail = { title, sub, targets -> openTargetsDetailModal(title, sub, targets) },
        onCloseCallback = { if (!isAnyModalOpen) searchBox.active = true }
    )

    val isZoomModalOpen: Boolean get() = zoomModal.isOpen
    val isFilterModalOpen: Boolean get() = filterModal.isOpen
    val isTrackerModalOpen: Boolean get() = trackerModal.isOpen
    val isExpeditionModalOpen: Boolean get() = expeditionModal.isOpen
    val isTargetsDetailModalOpen: Boolean get() = targetsDetailModal.isOpen
    val isAnyModalOpen: Boolean get() = targetsDetailModal.isOpen || zoomModal.isOpen || trackerModal.isOpen || filterModal.isOpen || expeditionModal.isOpen

    private val detailPanel: PokemonDetailPanelWidget = PokemonDetailPanelWidget(
        getItemStack = { sp, form, shiny -> getItemStack(sp, form, shiny) },
        onSelectSpecies = { sp, form ->
            val found = allTracked.find { PokemonTrackingManager.isSameSpecies(it.species, sp) }
            if (found != null) {
                selectedTracked = found
                selectedForm = form ?: found.species.standardForm
            }
        },
        onOpenZoom = { form -> openZoomModal(form) },
        onOpenTrackerModal = { openTrackerModal() },
        onTrackerStateChanged = { updateTrackerButton() },
        isAnyModalOpen = { isAnyModalOpen }
    )

    private val leftContentWidget: PokemonLeftContentWidget = PokemonLeftContentWidget(
        getItemStack = { sp, form -> getItemStack(sp, form) },
        onSelectTracked = { tracked ->
            selectedTracked = tracked
            selectedForm = null
            detailPanel.resetScroll()
        },
        onSelectGoal = { goal ->
            selectedGoal = goal
        },
        onOpenTargetsDetail = { title, subtitle, targets ->
            openTargetsDetailModal(title, subtitle, targets)
        }
    )

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
        state.loadAllData(Minecraft.getInstance().player)

        // Préchauffage asynchrone des données RCT en arrière-plan sans bloquer l'interface
        if (RctCompat.isLoaded) {
            RctDataLoader.preloadAsync()
        }

        val topY = 6
        val btnH = 18

        // 1. Onglets principaux (Row 1)
        mainPokedexBtn = CobblemonDarkButton(12, topY, 80, btnH, Component.literal("Pokédex"), PokedexIcons.POKEDEX_ICON, isTab = true, isSelected = { currentMainTab == MainTab.POKEDEX }) {
            switchMainTab(MainTab.POKEDEX)
        }
        addRenderableWidget(mainPokedexBtn)

        val evoGoalsReady = allGoals.count { it.feasibility == EvolutionFeasibility.READY_TO_EVOLVE }
        val evoLabel = if (evoGoalsReady > 0) "Évolution (§a$evoGoalsReady§r)" else "Évolution"
        mainEvolutionsBtn = CobblemonDarkButton(96, topY, 130, btnH, Component.literal(evoLabel), PokedexIcons.EVOLUTION_ICON, isTab = true, isSelected = { currentMainTab == MainTab.EVOLUTIONS }) {
            switchMainTab(MainTab.EVOLUTIONS)
        }
        addRenderableWidget(mainEvolutionsBtn)

        mainExpeditionsBtn = CobblemonDarkButton(230, topY, 105, btnH, Component.literal("Expéditions"), PokedexIcons.EXPEDITION_ICON, isTab = true, isSelected = { currentMainTab == MainTab.EXPEDITIONS }) {
            switchMainTab(MainTab.EXPEDITIONS)
        }
        addRenderableWidget(mainExpeditionsBtn)

        // Actions rapides à droite (Row 1)
        var rightX = width - 22
        closeBtn = CobblemonDarkButton(rightX, topY, 16, btnH, Component.literal("✕")) {
            onClose()
        }
        addRenderableWidget(closeBtn)

        rightX -= 22
        settingsBtn = CobblemonDarkButton(rightX, topY, 18, btnH, Component.empty(), PokedexIcons.SETTINGS_ICON) {
            minecraft?.setScreen(CobblemonSettingsScreen(this))
        }
        settingsBtn.tooltip = Tooltip.create(Component.literal("Paramètres & Diagnostic du Mod"))
        addRenderableWidget(settingsBtn)

        if (CobblemonScoreboardService.isAvailable()) {
            rightX -= 22
            val lBtn = CobblemonDarkButton(rightX, topY, 18, btnH, Component.empty(), PokedexIcons.LEADERBOARD_ICON) {
                minecraft?.setScreen(CobblemonLeaderboardScreen(this))
            }
            lBtn.tooltip = Tooltip.create(Component.literal("Classement des Joueurs"))
            addRenderableWidget(lBtn)
            leaderboardBtn = lBtn
        }

        if (RctCompat.isAvailable()) {
            rightX -= 22
            val rBtn = CobblemonDarkButton(rightX, topY, 18, btnH, Component.empty(), PokedexIcons.RCT_ICON) {
                minecraft?.setScreen(CobblemonRctProgressionScreen(this))
            }
            rBtn.tooltip = Tooltip.create(Component.literal("Arbre de Progression Radical Trainers"))
            addRenderableWidget(rBtn)
            rctBtn = rBtn
        }

        rightX -= 94
        trackerBtn = CobblemonDarkButton(rightX, topY, 90, btnH, Component.literal(PokemonTrackingManager.getModeSummary()), PokedexIcons.TRACKER_ICON) {
            openTrackerModal()
        }
        trackerBtn.tooltip = Tooltip.create(Component.literal("Mode Radar & Tracker : ${PokemonTrackingManager.getModeSummary()} (Cliquer pour gérer)"))
        addRenderableWidget(trackerBtn)

        // 2. Sous-onglets (Row 2, y = 27, height = 17)
        val subY = 27
        val subH = 17

        subPokedexAllBtn = CobblemonDarkButton(12, subY, 65, subH, Component.literal("Global"), isTab = true, isSelected = { currentMainTab == MainTab.POKEDEX && currentPokedexSubTab == PokedexSubTab.ALL }) {
            switchPokedexSubTab(PokedexSubTab.ALL)
        }
        subPokedexMissingBtn = CobblemonDarkButton(81, subY, 85, subH, Component.literal("Manquants"), isTab = true, isSelected = { currentMainTab == MainTab.POKEDEX && currentPokedexSubTab == PokedexSubTab.MISSING }) {
            switchPokedexSubTab(PokedexSubTab.MISSING)
        }
        addRenderableWidget(subPokedexAllBtn)
        addRenderableWidget(subPokedexMissingBtn)

        subEvoPendingBtn = CobblemonDarkButton(12, subY, 95, subH, Component.literal("Évolutions (§a$evoGoalsReady§r)"), isTab = true, isSelected = { currentMainTab == MainTab.EVOLUTIONS && currentEvolutionSubTab == EvolutionSubTab.PENDING_EVOLUTIONS }) {
            switchEvolutionSubTab(EvolutionSubTab.PENDING_EVOLUTIONS)
        }
        val itemsReady = allItemGoals.count { it.feasibility == EvolutionFeasibility.READY_TO_EVOLVE }
        val itemsLabel = if (itemsReady > 0) "Pierres (§a$itemsReady§r)" else "Pierres & Objets"
        subEvoItemsBtn = CobblemonDarkButton(110, subY, 105, subH, Component.literal(itemsLabel), isTab = true, isSelected = { currentMainTab == MainTab.EVOLUTIONS && currentEvolutionSubTab == EvolutionSubTab.ITEM_EVOLUTIONS }) {
            switchEvolutionSubTab(EvolutionSubTab.ITEM_EVOLUTIONS)
        }
        val originsCount = allBreedingOrigins.count { it.isReadyToBreed }
        val originsLabel = if (originsCount > 0) "Origines (§e$originsCount§r)" else "Origines & Repro"
        subEvoOriginsBtn = CobblemonDarkButton(218, subY, 105, subH, Component.literal(originsLabel), isTab = true, isSelected = { currentMainTab == MainTab.EVOLUTIONS && currentEvolutionSubTab == EvolutionSubTab.MISSING_ORIGINS }) {
            switchEvolutionSubTab(EvolutionSubTab.MISSING_ORIGINS)
        }
        val fossilsReady = allFossilGoals.count { it.isReadyToRevive }
        val fossilsLabel = if (fossilsReady > 0) "Fossiles (§a$fossilsReady§r)" else "Fossiles"
        subEvoFossilsBtn = CobblemonDarkButton(326, subY, 80, subH, Component.literal(fossilsLabel), isTab = true, isSelected = { currentMainTab == MainTab.EVOLUTIONS && currentEvolutionSubTab == EvolutionSubTab.FOSSILS }) {
            switchEvolutionSubTab(EvolutionSubTab.FOSSILS)
        }
        subEvoLegendariesBtn = CobblemonDarkButton(409, subY, 85, subH, Component.literal("Légendaires"), isTab = true, isSelected = { currentMainTab == MainTab.EVOLUTIONS && currentEvolutionSubTab == EvolutionSubTab.LEGENDARIES }) {
            switchEvolutionSubTab(EvolutionSubTab.LEGENDARIES)
        }
        togglePcEvolutionsBtn = CobblemonDarkButton(497, subY, 120, subH, getTogglePcButtonText()) {
            onlyPcFeasibleEvolutions = !onlyPcFeasibleEvolutions
            togglePcEvolutionsBtn.message = getTogglePcButtonText()
            applyFilters()
        }
        addRenderableWidget(subEvoPendingBtn)
        addRenderableWidget(subEvoItemsBtn)
        addRenderableWidget(subEvoOriginsBtn)
        addRenderableWidget(subEvoFossilsBtn)
        addRenderableWidget(subEvoLegendariesBtn)
        addRenderableWidget(togglePcEvolutionsBtn)

        refreshExpeditionsBtn = CobblemonDarkButton(12, subY, 140, subH, Component.literal("↻ Actualiser l'analyse")) {
            triggerExpeditionCalculation()
        }
        addRenderableWidget(refreshExpeditionsBtn)

        // 3. Barre de recherche et filtres (Row 3, y = 48, height = 18)
        val searchX = 12
        val searchY = 48
        val totalSearchRowWidth = max(180, width - 215)
        val filterBtnW = 86
        val gap = 4
        val quickResetW = 18
        val searchWidth = max(70, totalSearchRowWidth - filterBtnW - gap - quickResetW - gap)
        val searchHeight = 18

        searchBox = EditBox(font, searchX, searchY, searchWidth, searchHeight, Component.literal(""))
        searchBox.setHint(Component.translatable("gui.cobblemon_jei.pokedex.search.hint"))
        searchBox.setResponder { applyFilters() }
        addRenderableWidget(searchBox)

        val filterBtnX = searchX + searchWidth + gap
        filterBtn = CobblemonDarkButton(filterBtnX, searchY, filterBtnW, searchHeight, getFilterButtonText()) {
            openFilterModal()
        }
        addRenderableWidget(filterBtn)

        val resetBtnX = filterBtnX + filterBtnW + gap
        quickResetFilterBtn = CobblemonDarkButton(resetBtnX, searchY, quickResetW, searchHeight, Component.literal("✕")) {
            resetFilters()
        }
        quickResetFilterBtn.visible = filterState.isActive()
        quickResetFilterBtn.setTooltip(Tooltip.create(Component.literal("Réinitialiser tous les filtres")))
        addRenderableWidget(quickResetFilterBtn)

        // Initialisation de la modale de filtres
        filterModal.initSearchBox(font)

        updateSubTabVisibility()
        updateTrackerButton()
        applyFilters()
    }

    private fun updateSubTabVisibility() {
        val isPokedex = (currentMainTab == MainTab.POKEDEX)
        val isEvolutions = (currentMainTab == MainTab.EVOLUTIONS)
        val isExpeditions = (currentMainTab == MainTab.EXPEDITIONS)

        subPokedexAllBtn.visible = isPokedex
        subPokedexMissingBtn.visible = isPokedex

        subEvoPendingBtn.visible = isEvolutions
        subEvoItemsBtn.visible = isEvolutions
        subEvoOriginsBtn.visible = isEvolutions
        subEvoFossilsBtn.visible = isEvolutions
        subEvoLegendariesBtn.visible = isEvolutions
        togglePcEvolutionsBtn.visible = (isEvolutions && (currentEvolutionSubTab == EvolutionSubTab.PENDING_EVOLUTIONS || currentEvolutionSubTab == EvolutionSubTab.ITEM_EVOLUTIONS))

        refreshExpeditionsBtn.visible = isExpeditions
    }

    private fun switchMainTab(mainTab: MainTab) {
        currentMainTab = mainTab
        currentTab = when (mainTab) {
            MainTab.POKEDEX -> if (currentPokedexSubTab == PokedexSubTab.MISSING) Tab.MISSING else Tab.ALL
            MainTab.EVOLUTIONS -> Tab.EVOLUTIONS
            MainTab.EXPEDITIONS -> Tab.ALL
        }
        leftContentWidget.resetScroll()
        if (mainTab == MainTab.EXPEDITIONS && expeditionSuggestions.isEmpty() && !isExpeditionLoading) {
            triggerExpeditionCalculation()
        }
        updateSubTabVisibility()
        applyFilters()
    }

    private fun switchPokedexSubTab(sub: PokedexSubTab) {
        currentPokedexSubTab = sub
        currentTab = if (sub == PokedexSubTab.MISSING) Tab.MISSING else Tab.ALL
        leftContentWidget.resetScroll()
        applyFilters()
    }

    private fun switchEvolutionSubTab(sub: EvolutionSubTab) {
        currentEvolutionSubTab = sub
        currentTab = Tab.EVOLUTIONS
        leftContentWidget.resetScroll()
        updateSubTabVisibility()
        applyFilters()
    }

    private fun switchTab(tab: Tab) {
        currentTab = tab
        when (tab) {
            Tab.ALL -> {
                currentMainTab = MainTab.POKEDEX
                currentPokedexSubTab = PokedexSubTab.ALL
            }
            Tab.MISSING -> {
                currentMainTab = MainTab.POKEDEX
                currentPokedexSubTab = PokedexSubTab.MISSING
            }
            Tab.EVOLUTIONS -> {
                currentMainTab = MainTab.EVOLUTIONS
                currentEvolutionSubTab = EvolutionSubTab.PENDING_EVOLUTIONS
            }
        }
        leftContentWidget.resetScroll()
        updateSubTabVisibility()
        updateTrackerButton()
        applyFilters()
    }

    fun updateTrackerButton() {
        if (::trackerBtn.isInitialized) {
            trackerBtn.message = Component.literal(PokemonTrackingManager.getModeSummary())
            trackerBtn.setTooltip(Tooltip.create(Component.literal("Mode Radar & Tracker : ${PokemonTrackingManager.getModeSummary()} (Cliquer pour gérer)")))
        }
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
            filterBtn.setTooltip(if (filterState.isActive()) {
                Tooltip.create(Component.literal("Filtres actifs : ${filterState.activeCount()} (Cliquer pour modifier)"))
            } else {
                Tooltip.create(Component.literal("Ouvrir le panneau de filtres avancés"))
            })
        }
        if (::quickResetFilterBtn.isInitialized) {
            quickResetFilterBtn.visible = filterState.isActive()
        }
    }

    fun openFilterModal() {
        searchBox.isFocused = false
        searchBox.active = false
        filterModal.open()
    }

    fun closeFilterModal() {
        filterModal.close()
    }

    private fun resetFilters() {
        state.resetFilters()
        applyFilters()
    }

    private fun applyFilters() {
        state.applyFilters(searchBox.value)
        leftContentWidget.recomputeMaxScroll(width, height, buildLeftContentData())
        updateFilterButton()
    }

    private fun buildLeftContentData(): LeftContentData = state.buildLeftContentData(isExpeditionLoading, expeditionSuggestions)

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Fond sombre moderne
        guiGraphics.fill(0, 0, width, height, 0xE8101217.toInt())

        // Bande supérieure d'en-tête (englobe le titre, les boutons d'onglets et la barre de recherche)
        guiGraphics.fill(0, 0, width, 70, 0xFF14171E.toInt())
        guiGraphics.fill(0, 69, width, 70, 0xFF282D3C.toInt())

        // Statistiques globales pour la barre de progression inférieure
        val stats = PokemonTrackerService.getStats()

        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val isAnyModalOpen = isZoomModalOpen || isFilterModalOpen || isTrackerModalOpen || isExpeditionModalOpen || isTargetsDetailModalOpen
        val contentMouseX = if (isAnyModalOpen) -1000 else mouseX
        val contentMouseY = if (isAnyModalOpen) -1000 else mouseY

        // Rendu des zones principales
        leftContentWidget.render(guiGraphics, buildLeftContentData(), contentMouseX, contentMouseY, width, height, font)
        if (currentMainTab != MainTab.EXPEDITIONS) {
            detailPanel.render(guiGraphics, selectedTracked, selectedForm, allTracked, contentMouseX, contentMouseY, width, height, font)
        }
        PokedexRenderHelper.renderBottomProgressBar(guiGraphics, contentMouseX, contentMouseY, width, height, stats, font)

        // Rendu des modales via les widgets autonomes
        zoomModal.render(guiGraphics, mouseX, mouseY, width, height, font)
        filterModal.render(guiGraphics, mouseX, mouseY, width, height, font)
        trackerModal.render(guiGraphics, mouseX, mouseY, width, height, font)
        expeditionModal.render(guiGraphics, mouseX, mouseY, width, height, font)
        targetsDetailModal.render(guiGraphics, mouseX, mouseY, width, height, font)
    }





    fun openZoomModal(form: FormData? = null) {
        searchBox.active = false
        zoomModal.open(selectedTracked, form)
    }

    fun closeZoomModal() {
        zoomModal.close()
    }

    fun openTrackerModal() {
        searchBox.active = false
        trackerModal.open()
    }

    fun closeTrackerModal() {
        trackerModal.close()
    }

    fun openExpeditionModal() {
        searchBox.active = false
        expeditionModal.open()
    }

    fun closeExpeditionModal() {
        expeditionModal.close()
    }

    fun triggerExpeditionCalculation() {
        expeditionModal.triggerCalculation()
    }

    fun openTargetsDetailModal(title: String, subtitle: String, targets: List<fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget>) {
        searchBox.active = false
        targetsDetailModal.open(title, subtitle, targets)
    }

    fun closeTargetsDetailModal() {
        targetsDetailModal.close()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        if (targetsDetailModal.mouseClicked(mouseX, mouseY, button, width, height)) return true
        if (expeditionModal.mouseClicked(mouseX, mouseY, button, width, height)) return true
        if (trackerModal.mouseClicked(mouseX, mouseY, button, width, height)) return true
        if (filterModal.mouseClicked(mouseX, mouseY, button, width, height)) return true
        if (zoomModal.mouseClicked(mouseX, mouseY, button, width, height)) return true

        if (currentMainTab != MainTab.EXPEDITIONS) {
            if (detailPanel.mouseClicked(mouseX, mouseY, button, selectedTracked, selectedForm, allTracked, width, height) { newForm -> selectedForm = newForm }) return true
        }

        if (leftContentWidget.mouseClicked(mouseX, mouseY, button, buildLeftContentData(), width, height)) return true

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (filterModal.mouseDragged(mouseY)) return true
        if (leftContentWidget.mouseDragged(mouseY, width, height, currentMainTab == MainTab.EXPEDITIONS)) return true
        if (currentMainTab != MainTab.EXPEDITIONS && detailPanel.mouseDragged(mouseY, width, height)) return true
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            filterModal.mouseReleased()
            detailPanel.mouseReleased()
            leftContentWidget.mouseReleased()
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (targetsDetailModal.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        if (expeditionModal.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        if (trackerModal.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        if (filterModal.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        if (zoomModal.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true

        if (currentMainTab != MainTab.EXPEDITIONS && detailPanel.mouseScrolled(mouseX, mouseY, scrollY, width, height)) return true

        if (leftContentWidget.mouseScrolled(mouseX, mouseY, scrollY, width, height, currentMainTab == MainTab.EXPEDITIONS)) return true
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (filterModal.charTyped(codePoint, modifiers)) return true
        return super.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (targetsDetailModal.keyPressed(keyCode, scanCode, modifiers)) return true
        if (expeditionModal.keyPressed(keyCode, scanCode, modifiers)) return true
        if (trackerModal.keyPressed(keyCode, scanCode, modifiers)) return true
        if (filterModal.keyPressed(keyCode, scanCode, modifiers)) return true
        if (zoomModal.keyPressed(keyCode, scanCode, modifiers)) return true

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

    companion object {
        val POKEDEX_ICON: ResourceLocation get() = PokedexIcons.POKEDEX_ICON
        val EVOLUTION_ICON: ResourceLocation get() = PokedexIcons.EVOLUTION_ICON
        val SETTINGS_ICON: ResourceLocation get() = PokedexIcons.SETTINGS_ICON
        val RCT_ICON: ResourceLocation get() = PokedexIcons.RCT_ICON
        val EXPEDITION_ICON: ResourceLocation get() = PokedexIcons.EXPEDITION_ICON
        val TRACKER_ICON: ResourceLocation get() = PokedexIcons.TRACKER_ICON
        val LEADERBOARD_ICON: ResourceLocation get() = PokedexIcons.LEADERBOARD_ICON
    }
}
