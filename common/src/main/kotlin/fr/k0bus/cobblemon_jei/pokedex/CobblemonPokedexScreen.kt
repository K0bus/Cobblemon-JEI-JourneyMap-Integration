package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.data.PokemonEvolutionDataLoader
import fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader
import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import fr.k0bus.cobblemon_jei.journeymap.CobblemonOverlayManager
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
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

    private var currentTab: Tab = Tab.ALL
    private var onlyPcFeasibleEvolutions: Boolean = false

    // Éléments d'interface
    private lateinit var searchBox: EditBox
    private lateinit var tabAllBtn: Button
    private lateinit var tabMissingBtn: Button
    private lateinit var tabEvolutionsBtn: Button
    private lateinit var togglePcEvolutionsBtn: Button

    // Données filtrées
    private var allTracked: List<TrackedPokemon> = emptyList()
    private var filteredTracked: List<TrackedPokemon> = emptyList()
    private var selectedTracked: TrackedPokemon? = null

    private var allGoals: List<EvolutionGoal> = emptyList()
    private var filteredGoals: List<EvolutionGoal> = emptyList()
    private var selectedGoal: EvolutionGoal? = null

    // Cache d'items
    private val itemStackCache = mutableMapOf<Pair<Species, FormData?>, ItemStack>()

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

    private fun getItemStack(species: Species, form: FormData? = null): ItemStack {
        return itemStackCache.computeIfAbsent(Pair(species, form)) {
            try {
                PokemonSpeciesIngredient(species, form ?: species.standardForm).itemStack
            } catch (_: Throwable) {
                ItemStack.EMPTY
            }
        }
    }

    override fun init() {
        PokemonTrackerService.refresh()
        allTracked = PokemonTrackerService.getAllTracked()
        allGoals = PokemonTrackerService.getEvolutionGoals()

        if (selectedTracked == null) {
            selectedTracked = allTracked.firstOrNull()
        }

        val topY = 28
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

        // Champ de recherche
        val searchX = width - 380
        val searchWidth = 160
        searchBox = EditBox(font, searchX, topY, searchWidth, tabHeight, Component.literal(""))
        searchBox.setHint(Component.translatable("gui.cobblemon_jei.pokedex.search.hint"))
        searchBox.setResponder { applyFilters() }
        addRenderableWidget(searchBox)

        // Bouton Fermer
        val closeBtn = Button.builder(Component.literal("✕")) {
            onClose()
        }.bounds(width - 24, topY, 18, tabHeight).build()
        addRenderableWidget(closeBtn)

        updateTabButtonStyles()
        applyFilters()
    }

    private fun switchTab(tab: Tab) {
        currentTab = tab
        scrollOffset = 0.0
        togglePcEvolutionsBtn.visible = (currentTab == Tab.EVOLUTIONS)
        updateTabButtonStyles()
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

    private fun applyFilters() {
        val query = searchBox.value.trim().lowercase()

        when (currentTab) {
            Tab.ALL -> {
                filteredTracked = allTracked.filter { matchesQuery(it, query) }
            }
            Tab.MISSING -> {
                filteredTracked = allTracked.filter { (it.isMissing || it.isEncountered) && matchesQuery(it, query) }
            }
            Tab.EVOLUTIONS -> {
                var stream = allGoals
                if (onlyPcFeasibleEvolutions) {
                    stream = stream.filter {
                        it.feasibility == EvolutionFeasibility.READY_TO_EVOLVE ||
                                it.feasibility == EvolutionFeasibility.CAN_EVOLVE_WITH_PC
                    }
                }
                filteredGoals = stream.filter { goal ->
                    matchesGoalQuery(goal, query)
                }
            }
        }

        recomputeMaxScroll()
        scrollOffset = scrollOffset.coerceIn(0.0, maxScroll)
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
        val y = 52
        val w = max(180, width - 215)
        val h = max(100, height - y - 10)
        return intArrayOf(x, y, w, h)
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

        // Bande supérieure d'en-tête
        guiGraphics.fill(0, 0, width, 50, 0xFF14171E.toInt())
        guiGraphics.fill(0, 49, width, 50, 0xFF282D3C.toInt())

        // Titre et statistiques globales
        val stats = PokemonTrackerService.getStats()
        val title = "§e§lPOKÉDEX TRACKER§r §8|§r Complétion : §a${String.format("%.1f", stats.completionPercent)}%§r"
        guiGraphics.drawString(font, title, 14, 10, 0xFFFFFFFF.toInt(), false)

        val statsSummary = "Capturés: §a${stats.caughtCount}§r §8/§r Vus: §6${stats.encounteredCount}§r §8/§r Manquants: §c${stats.missingCount}§r §8|§r Prêts PC: §b${stats.readyEvolutionsCount}§r"
        guiGraphics.drawString(font, statsSummary, width - font.width(statsSummary) - 14, 10, 0xFFBBBBBB.toInt(), false)

        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // Rendu des zones principales
        renderLeftContent(guiGraphics, mouseX, mouseY)
        renderRightPanel(guiGraphics, mouseX, mouseY)
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
            guiGraphics.drawString(font, srcName, startX + 26, cardY + 8, 0xFFE0E0E0.toInt(), false)

            val locText = if (goal.hasSourceInPC) "§a✔ ${goal.sourceLocationSummary}§r" else "§7✕ Non possédé§r"
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

            // Résumé texte de la condition
            val conditionStr = goal.requirementsSummary
            val shortCond = if (conditionStr.length > 28) conditionStr.substring(0, 26) + "…" else conditionStr
            guiGraphics.drawString(font, shortCond, reqItemX + 2, cardY + 10, 0xFFCCCCCC.toInt(), false)

            val levelSummary = if (goal.minLevel != null) {
                if (goal.isLevelMet) "§aNiv. ${goal.minLevel} atteint§r" else "§cNiv. ${goal.minLevel} requis§r"
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
            EvolutionFeasibility.CAN_EVOLVE_WITH_PC -> "⏳ Pokémon présent dans votre PC/équipe, prérequis manquants."
            EvolutionFeasibility.MISSING_BASE_POKEMON -> "❌ Vous devez d'abord capturer le Pokémon de base."
        }
        tooltip.add(Component.literal(feasText).withStyle(if (goal.feasibility == EvolutionFeasibility.READY_TO_EVOLVE) ChatFormatting.GREEN else ChatFormatting.GOLD))

        tooltip.add(Component.empty())
        tooltip.add(Component.literal("État du Pokémon source :").withStyle(ChatFormatting.GRAY))
        if (goal.hasSourceInPC) {
            for (owned in goal.ownedSourcePokemons) {
                tooltip.add(Component.literal(" • ${owned.location} - Niv. ${owned.level} - Bonheur: ${owned.friendship}").withStyle(ChatFormatting.AQUA))
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
        val panelX = width - 200
        val panelY = 52
        val panelW = 188
        val panelH = max(100, height - panelY - 10)

        // Fond et bordures du panneau droit
        guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF242834.toInt())
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF14161F.toInt())

        val currentTracked = selectedTracked ?: allTracked.firstOrNull() ?: return
        val sp = currentTracked.species

        // 1. Cadre du Pokémon et grand sprite
        val previewSize = 48
        val previewX = panelX + (panelW - previewSize) / 2
        val previewY = panelY + 12

        guiGraphics.fill(previewX - 4, previewY - 4, previewX + previewSize + 4, previewY + previewSize + 4, 0xFF1D212E.toInt())
        guiGraphics.fill(previewX - 5, previewY - 5, previewX + previewSize + 5, previewY - 4, 0xFF353B4F.toInt())

        val stack = getItemStack(sp)
        if (!stack.isEmpty) {
            val pose = guiGraphics.pose()
            pose.pushPose()
            pose.translate(previewX.toFloat(), previewY.toFloat(), 0f)
            pose.scale(3.0f, 3.0f, 1.0f)
            guiGraphics.renderItem(stack, 0, 0)
            pose.popPose()
        }

        // 2. Nom et Numéro Pokédex
        val dexStr = String.format("#%04d", sp.nationalPokedexNumber)
        val nameStr = sp.translatedName.string
        guiGraphics.drawCenteredString(font, "§6$dexStr§r §f§l$nameStr§r", panelX + panelW / 2, previewY + previewSize + 8, 0xFFFFFFFF.toInt())

        // 3. Types élémentaires
        val primary = sp.standardForm.primaryType
        val secondary = sp.standardForm.secondaryType
        val typeDesc = if (secondary != null) {
            "Type : §b${primary.name}§r / §b${secondary.name}§r"
        } else {
            "Type : §b${primary.name}§r"
        }
        guiGraphics.drawCenteredString(font, typeDesc, panelX + panelW / 2, previewY + previewSize + 22, 0xFFCCCCCC.toInt())

        // 4. Statut Pokédex
        val statusLine = when (currentTracked.status) {
            PokedexStatus.CAUGHT -> "§a✔ Capturé / Découvert§r"
            PokedexStatus.ENCOUNTERED -> "§6👁 Rencontré uniquement§r"
            PokedexStatus.MISSING -> "§c✕ Non découvert§r"
        }
        guiGraphics.drawCenteredString(font, statusLine, panelX + panelW / 2, previewY + previewSize + 36, 0xFFFFFFFF.toInt())

        // Séparateur
        var curY = previewY + previewSize + 50
        guiGraphics.fill(panelX + 10, curY, panelX + panelW - 10, curY + 1, 0xFF2A2F3D.toInt())
        curY += 6

        // 5. Stocks dans le PC et l'équipe
        guiGraphics.drawString(font, "§e§lRéserves du joueur :§r", panelX + 10, curY, 0xFFFFFFFF.toInt(), false)
        curY += 12

        if (currentTracked.ownedCount > 0) {
            for (owned in currentTracked.ownedInstances.take(3)) {
                val txt = "• ${owned.location} (Niv. ${owned.level})"
                guiGraphics.drawString(font, txt, panelX + 14, curY, 0xFF55FF55.toInt(), false)
                curY += 10
            }
            if (currentTracked.ownedCount > 3) {
                guiGraphics.drawString(font, "• +${currentTracked.ownedCount - 3} autre(s)...", panelX + 14, curY, 0xFFAAAAAA.toInt(), false)
                curY += 10
            }
        } else {
            guiGraphics.drawString(font, "• Aucun dans le PC ou l'équipe", panelX + 14, curY, 0xFF888888.toInt(), false)
            curY += 10
        }

        // Séparateur
        curY += 4
        guiGraphics.fill(panelX + 10, curY, panelX + panelW - 10, curY + 1, 0xFF2A2F3D.toInt())
        curY += 6

        // 6. Évolutions directes de cette espèce
        guiGraphics.drawString(font, "§e§lLignée évolutive :§r", panelX + 10, curY, 0xFFFFFFFF.toInt(), false)
        curY += 12

        val evolutionsFromThis = PokemonEvolutionDataLoader.loadEvolutionRecipes()
            .filter { it.sourceSpecies == sp }

        if (evolutionsFromThis.isNotEmpty()) {
            for (evo in evolutionsFromThis.take(2)) {
                val tgtName = evo.targetSpecies.translatedName.string
                guiGraphics.drawString(font, "➔ $tgtName", panelX + 14, curY, 0xFFFFEAA7.toInt(), false)
                curY += 10
                val cond = evo.requirementsSummary
                val shortCond = if (cond.length > 24) cond.substring(0, 22) + "…" else cond
                guiGraphics.drawString(font, "  ($shortCond)", panelX + 14, curY, 0xFFAAAAAA.toInt(), false)
                curY += 11
            }
        } else {
            guiGraphics.drawString(font, "• Aucune évolution suivante", panelX + 14, curY, 0xFF888888.toInt(), false)
            curY += 11
        }

        // 7. Bouton JourneyMap en bas du panneau
        val btnY = panelY + panelH - 24
        val isJmHovered = mouseX >= panelX + 10 && mouseX <= panelX + panelW - 10 && mouseY >= btnY && mouseY <= btnY + 18
        val btnBg = if (isJmHovered) 0xFF3498DB.toInt() else 0xFF212530.toInt()

        guiGraphics.fill(panelX + 10, btnY, panelX + panelW - 10, btnY + 18, btnBg)
        guiGraphics.fill(panelX + 10, btnY, panelX + panelW - 10, btnY + 1, 0xFF54A0FF.toInt())
        guiGraphics.fill(panelX + 10, btnY + 17, panelX + panelW - 10, btnY + 18, 0xFF54A0FF.toInt())

        val btnText = if (CobblemonOverlayManager.activeSpecies == sp) "✔ Actif sur carte" else "🗺 Voir sur JourneyMap"
        guiGraphics.drawCenteredString(font, btnText, panelX + panelW / 2, btnY + 5, 0xFFFFFFFF.toInt())
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        val (leftX, topY, areaW, areaH) = getLeftAreaBounds()

        // 1. Clic sur la scrollbar
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
                        return true
                    }
                }
            }
        }

        // 3. Clic sur le bouton JourneyMap dans le panneau droit
        val panelX = width - 200
        val panelY = 52
        val panelW = 188
        val panelH = max(100, height - panelY - 10)
        val btnY = panelY + panelH - 24
        if (mouseX >= panelX + 10 && mouseX <= panelX + panelW - 10 && mouseY >= btnY && mouseY <= btnY + 18) {
            selectedTracked?.let {
                CobblemonOverlayManager.setOverlay(it.species, it.species.standardForm)
            }
            return true
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isDraggingScrollbar && maxScroll > 0) {
            val (_, topY, _, areaH) = getLeftAreaBounds()
            val totalTrackH = areaH - 4
            val delta = mouseY - dragStartMouseY
            val scrollDelta = (delta / totalTrackH) * maxScroll
            scrollOffset = (dragStartScroll + scrollDelta).coerceIn(0.0, maxScroll)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            isDraggingScrollbar = false
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val (leftX, topY, areaW, areaH) = getLeftAreaBounds()
        if (mouseX >= leftX && mouseX <= leftX + areaW && mouseY >= topY && mouseY <= topY + areaH) {
            scrollOffset = (scrollOffset - scrollY * 24.0).coerceIn(0.0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        if (CobblemonKeyBindings.OPEN_POKEDEX_KEY.matches(keyCode, scanCode)) {
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
