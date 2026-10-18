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
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
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
    private var currentRightPanelTab: RightPanelTab = RightPanelTab.DESCRIPTION
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

    // Données filtrées
    private var allTracked: List<TrackedPokemon> = emptyList()
    private var filteredTracked: List<TrackedPokemon> = emptyList()
    private var selectedTracked: TrackedPokemon? = null

    private var allGoals: List<EvolutionGoal> = emptyList()
    private var filteredGoals: List<EvolutionGoal> = emptyList()
    private var selectedGoal: EvolutionGoal? = null

    // Cache d'items (incluant la forme et le statut Shiny)
    private val itemStackCache = mutableMapOf<Triple<Species, FormData?, Boolean>, ItemStack>()

    // Gestion de l'affichage Shiny dans l'onglet Formes
    private var globalShinyForms: Boolean = false
    private val individualShinyForms = mutableSetOf<String>()
    private var shinyHeaderButtonRect: IntArray? = null
    private val shinyCardButtonRects = mutableListOf<Pair<IntArray, FormData>>()

    private fun isFormShiny(form: FormData): Boolean {
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
        return itemStackCache.computeIfAbsent(Triple(species, form, shiny)) {
            try {
                val pokemon = Pokemon().apply {
                    this.species = species
                    this.form = form ?: species.standardForm
                    this.level = 1
                    this.shiny = shiny
                }
                PokemonItem.from(pokemon, 1)
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

        // Champ de recherche : sur une nouvelle ligne sous les boutons, occupant toute la largeur de la liste des Pokémon
        val searchX = 12
        val searchY = 48
        val searchWidth = max(180, width - 215)
        val searchHeight = 18
        searchBox = EditBox(font, searchX, searchY, searchWidth, searchHeight, Component.literal(""))
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

        // Rendu des zones principales
        renderLeftContent(guiGraphics, mouseX, mouseY)
        renderRightPanel(guiGraphics, mouseX, mouseY)
        renderBottomProgressBar(guiGraphics, mouseX, mouseY, stats)
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

        // 1. Cadre du Pokémon et sprite
        val previewSize = 36
        val previewX = panelX + (panelW - previewSize) / 2
        val previewY = panelY + 6

        guiGraphics.fill(previewX - 4, previewY - 4, previewX + previewSize + 4, previewY + previewSize + 4, 0xFF1D212E.toInt())
        guiGraphics.fill(previewX - 5, previewY - 5, previewX + previewSize + 5, previewY - 4, 0xFF353B4F.toInt())

        val stack = getItemStack(sp)
        if (!stack.isEmpty) {
            val pose = guiGraphics.pose()
            pose.pushPose()
            pose.translate(previewX.toFloat() - 6f, previewY.toFloat() - 6f, 0f)
            pose.scale(3.0f, 3.0f, 1.0f)
            guiGraphics.renderItem(stack, 0, 0)
            pose.popPose()
        }

        // Nom et Numéro Pokédex
        val dexStr = String.format("#%04d", sp.nationalPokedexNumber)
        val nameStr = sp.translatedName.string
        guiGraphics.drawCenteredString(font, "§6$dexStr§r §f§l$nameStr§r", panelX + panelW / 2, previewY + previewSize + 4, 0xFFFFFFFF.toInt())

        // Types et statut
        val primary = sp.standardForm.primaryType
        val secondary = sp.standardForm.secondaryType
        val typeDesc = if (secondary != null) {
            "§b${primary.name}§r/§b${secondary.name}§r"
        } else {
            "§b${primary.name}§r"
        }
        val statusIcon = when (currentTracked.status) {
            PokedexStatus.CAUGHT -> "§a✔ Capturé§r"
            PokedexStatus.ENCOUNTERED -> "§6👁 Rencontré§r"
            PokedexStatus.MISSING -> "§c✕ Manquant§r"
        }
        guiGraphics.drawCenteredString(font, "$typeDesc · $statusIcon", panelX + panelW / 2, previewY + previewSize + 16, 0xFFCCCCCC.toInt())

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
            RightPanelTab.SPAWNS -> renderTabSpawns(guiGraphics, sp, contentX, curY, contentW)
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

        // 4. Bouton JourneyMap en bas du panneau
        val btnY = panelY + panelH - 22
        val isJmHovered = mouseX >= panelX + 8 && mouseX <= panelX + panelW - 8 && mouseY >= btnY && mouseY <= btnY + 16
        val btnBg = if (isJmHovered) 0xFF3498DB.toInt() else 0xFF212530.toInt()

        guiGraphics.fill(panelX + 8, btnY, panelX + panelW - 8, btnY + 16, btnBg)
        guiGraphics.fill(panelX + 8, btnY, panelX + panelW - 8, btnY + 1, 0xFF54A0FF.toInt())
        guiGraphics.fill(panelX + 8, btnY + 15, panelX + panelW - 8, btnY + 16, 0xFF54A0FF.toInt())

        val btnText = if (CobblemonOverlayManager.activeSpecies == sp) "✔ Actif sur carte" else "🗺 Voir sur JourneyMap"
        guiGraphics.drawCenteredString(font, btnText, panelX + panelW / 2, btnY + 4, 0xFFFFFFFF.toInt())

        // Tooltip de survol d'onglet et éléments interactifs
        if (hoveredTabTooltip != null) {
            guiGraphics.renderTooltip(font, Component.literal(hoveredTabTooltip), mouseX, mouseY)
        } else if (currentRightPanelTab == RightPanelTab.FORMS) {
            shinyHeaderButtonRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    val tt = if (globalShinyForms) "Désactiver le mode Chromatique (Shiny)" else "Activer le mode Chromatique (Shiny) pour toutes les formes"
                    guiGraphics.renderTooltip(font, Component.literal(tt), mouseX, mouseY)
                }
            }
            if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= contentY && mouseY <= contentY + contentH) {
                for ((rect, form) in shinyCardButtonRects) {
                    val (bx, by, bw, bh) = rect
                    if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                        val isSh = isFormShiny(form)
                        val fName = if (form == sp.standardForm || form.name.isBlank()) "Forme Standard" else "Forme de ${form.name.replaceFirstChar(Char::titlecase)}"
                        val tt = if (isSh) "Cliquer pour afficher la version normale ($fName)" else "Cliquer pour afficher la version Shiny ($fName)"
                        guiGraphics.renderTooltip(font, Component.literal(tt), mouseX, mouseY)
                        break
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

        // Récupérer toutes les formes de l'espèce
        val allForms = mutableListOf<FormData>()
        allForms.add(sp.standardForm)
        for (f in sp.forms) {
            if (f != sp.standardForm && allForms.none { it.name.equals(f.name, ignoreCase = true) }) {
                allForms.add(f)
            }
        }

        guiGraphics.drawString(font, "§e§lFormes & Variantes (${allForms.size})§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)

        // Bouton global Shiny dans l'en-tête
        val shinyBtnW = 54
        val shinyBtnH = 12
        val shinyBtnX = contentX + contentW - shinyBtnW
        val shinyBtnY = y - 1
        shinyHeaderButtonRect = intArrayOf(shinyBtnX, shinyBtnY, shinyBtnW, shinyBtnH)

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

        y += 14

        val allRecipes = PokemonEvolutionDataLoader.loadEvolutionRecipes()

        for (form in allForms) {
            val isStandard = (form == sp.standardForm || form.name.isBlank() || form.name.equals("normal", ignoreCase = true))
            val formTitle = if (isStandard) {
                "Forme Standard"
            } else {
                "Forme de ${form.name.replaceFirstChar(Char::titlecase)}"
            }

            val isShiny = isFormShiny(form)
            val cardY = y
            val cardH = 46

            // Carte de fond (avec teinte dorée subtile si shiny)
            val cardBg = if (isShiny) 0xFF1A1812.toInt() else 0xFF151922.toInt()
            val cardBorder = if (isShiny) 0xFF8E6E1A.toInt() else 0xFF282D3C.toInt()

            guiGraphics.fill(contentX, cardY, contentX + contentW, cardY + cardH, cardBg)
            guiGraphics.fill(contentX, cardY, contentX + contentW, cardY + 1, cardBorder)
            guiGraphics.fill(contentX, cardY + cardH - 1, contentX + contentW, cardY + cardH, cardBorder)
            guiGraphics.fill(contentX, cardY, contentX + 1, cardY + cardH, cardBorder)
            guiGraphics.fill(contentX + contentW - 1, cardY, contentX + contentW, cardY + cardH, cardBorder)

            // Zone Sprite + Étoile Shiny cliquable
            val spriteX = contentX + 4
            val spriteY = cardY + 6
            val toggleBox = intArrayOf(contentX + 2, cardY + 4, 20, 36)
            shinyCardButtonRects.add(Pair(toggleBox, form))

            val isSpriteHovered = mouseX >= toggleBox[0] && mouseX <= toggleBox[0] + toggleBox[2] &&
                    mouseY >= toggleBox[1] && mouseY <= toggleBox[1] + toggleBox[3]

            if (isSpriteHovered) {
                guiGraphics.fill(toggleBox[0], toggleBox[1], toggleBox[0] + toggleBox[2], toggleBox[1] + toggleBox[3], 0x33F1C40F.toInt())
                guiGraphics.fill(toggleBox[0], toggleBox[1], toggleBox[0] + toggleBox[2], toggleBox[1] + 1, 0xFFF1C40F.toInt())
                guiGraphics.fill(toggleBox[0], toggleBox[1] + toggleBox[3] - 1, toggleBox[0] + toggleBox[2], toggleBox[1] + toggleBox[3], 0xFFF1C40F.toInt())
                guiGraphics.fill(toggleBox[0], toggleBox[1], toggleBox[0] + 1, toggleBox[1] + toggleBox[3], 0xFFF1C40F.toInt())
                guiGraphics.fill(toggleBox[0] + toggleBox[2] - 1, toggleBox[1], toggleBox[0] + toggleBox[2], toggleBox[1] + toggleBox[3], 0xFFF1C40F.toInt())
            }

            // Sprite de la forme (Normal ou Shiny selon l'état)
            val stack = getItemStack(sp, form, isShiny)
            if (!stack.isEmpty) {
                guiGraphics.renderItem(stack, spriteX, spriteY)
            }

            // Petit badge étoile sous le sprite
            val starTxt = if (isShiny) "§e★§r" else "§8☆§r"
            guiGraphics.drawCenteredString(font, starTxt, spriteX + 8, spriteY + 18, 0xFFFFFFFF.toInt())

            // Titre et Aspects
            var headerX = contentX + 26
            val titlePrefix = if (isShiny) "§6★ §f§l" else "§f§l"
            guiGraphics.drawString(font, "$titlePrefix$formTitle§r", headerX, cardY + 6, 0xFFFFFFFF.toInt(), false)
            headerX += font.width("$titlePrefix$formTitle§r") + 4

            if (form.aspects.isNotEmpty()) {
                val aspectsStr = "§8[${form.aspects.joinToString(", ")}]§r"
                guiGraphics.drawString(font, aspectsStr, headerX, cardY + 7, 0xFFAAAAAA.toInt(), false)
            }

            // Statut de capture / possession (en tenant compte de Shiny si activé)
            val formRecord = try {
                val specRecord = CobblemonClient.clientPokedexData.speciesRecords[sp.resourceIdentifier]
                specRecord?.getFormRecord(form.name.lowercase().ifBlank { "normal" })
                    ?: specRecord?.getFormRecord(form.name.lowercase())
                    ?: specRecord?.getFormRecord("")
            } catch (_: Throwable) { null }

            val statusText: String
            val statusColor: Int

            if (isShiny) {
                val ownedShinyCount = currentTracked?.ownedInstances?.count {
                    it.pokemon.shiny && PokemonTrackerService.isFormMatch(it.pokemon, form, sp)
                } ?: 0
                val isDexShinyCaught = formRecord?.hasSeenShinyState(true) == true && (formRecord.knowledge == PokedexEntryProgress.CAUGHT)
                val isDexShinySeen = formRecord?.hasSeenShinyState(true) == true

                when {
                    ownedShinyCount > 0 -> {
                        statusText = "§6★ Possédé ($ownedShinyCount)§r"
                        statusColor = 0xFFF1C40F.toInt()
                    }
                    isDexShinyCaught -> {
                        statusText = "§6★ Capturé Shiny§r"
                        statusColor = 0xFFF1C40F.toInt()
                    }
                    isDexShinySeen -> {
                        statusText = "§e👁 Vu Shiny (Pokédex)§r"
                        statusColor = 0xFFE67E22.toInt()
                    }
                    else -> {
                        statusText = "§c✕ Non découvert Shiny§r"
                        statusColor = 0xFFE74C3C.toInt()
                    }
                }
            } else {
                val ownedCount = currentTracked?.ownedInstances?.count { PokemonTrackerService.isFormMatch(it.pokemon, form, sp) } ?: 0
                val isDexCaught = formRecord?.knowledge == PokedexEntryProgress.CAUGHT
                val isDexSeen = formRecord?.knowledge == PokedexEntryProgress.ENCOUNTERED

                when {
                    ownedCount > 0 -> {
                        statusText = "§a✔ Possédé ($ownedCount)§r"
                        statusColor = 0xFF2ECC71.toInt()
                    }
                    isDexCaught -> {
                        statusText = "§a✔ Capturé§r"
                        statusColor = 0xFF2ECC71.toInt()
                    }
                    isDexSeen -> {
                        statusText = "§6👁 Vu (Pokédex)§r"
                        statusColor = 0xFFE67E22.toInt()
                    }
                    else -> {
                        statusText = "§c✕ Non découvert§r"
                        statusColor = 0xFFE74C3C.toInt()
                    }
                }
            }

            val statusW = font.width(statusText)
            guiGraphics.drawString(font, statusText, contentX + contentW - statusW - 6, cardY + 6, statusColor, false)

            // Types élémentaires
            var typeX = contentX + 26
            val primColor = CobblemonOverlayManager.getElementColor(form.primaryType.name)
            val primName = form.primaryType.displayName.string.uppercase()
            val primW = font.width(primName) + 6
            guiGraphics.fill(typeX, cardY + 18, typeX + primW, cardY + 28, primColor)
            guiGraphics.drawString(font, primName, typeX + 3, cardY + 19, 0xFFFFFFFF.toInt(), true)
            typeX += primW + 4

            form.secondaryType?.let { secType ->
                val secColor = CobblemonOverlayManager.getElementColor(secType.name)
                val secName = secType.displayName.string.uppercase()
                val secW = font.width(secName) + 6
                guiGraphics.fill(typeX, cardY + 18, typeX + secW, cardY + 28, secColor)
                guiGraphics.drawString(font, secName, typeX + 3, cardY + 19, 0xFFFFFFFF.toInt(), true)
            }

            // Évolutions de cette forme
            val formEvos = allRecipes.filter { it.sourceSpecies == sp && (it.sourceForm == form || (it.sourceForm.name.isNotBlank() && it.sourceForm.name.equals(form.name, true))) }
            val evoText = if (formEvos.isNotEmpty()) {
                val targets = formEvos.map { it.targetSpecies.translatedName.string }.distinct()
                "§7Évolue en : §e${targets.joinToString(", ")}§r"
            } else if (allForms.size > 1) {
                "§8✕ Aucune évolution possible pour cette forme§r"
            } else {
                "§8• Aucune évolution connue§r"
            }
            guiGraphics.drawString(font, evoText, contentX + 26, cardY + 32, 0xFFCCCCCC.toInt(), false)

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

    private fun renderTabSpawns(guiGraphics: GuiGraphics, sp: Species, contentX: Int, startY: Int, contentW: Int): Int {
        var y = startY
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

            if (spawn.biomes.isNotEmpty()) {
                val bList = spawn.biomes.take(3).map {
                    it.substringAfter(":").replace("_", " ").replaceFirstChar(Char::titlecase)
                }
                val more = if (spawn.biomes.size > 3) " (+${spawn.biomes.size - 3})" else ""
                val bText = "Biomes: ${bList.joinToString(", ")}$more"
                val bLines = font.split(Component.literal("§8$bText§r"), contentW - 8)
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

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

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

        // Clics dans l'onglet Formes (Toggle Shiny global ou par carte)
        if (currentRightPanelTab == RightPanelTab.FORMS) {
            shinyHeaderButtonRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    globalShinyForms = !globalShinyForms
                    individualShinyForms.clear()
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
                    return true
                }
            }

            if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= contentY && mouseY <= contentY + contentH) {
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

        // Clic sur la barre de défilement du panneau droit
        if (rightPanelMaxScroll > 0 && mouseX >= scrollTrackX - 3 && mouseX <= scrollTrackX + 5 && mouseY >= contentY && mouseY <= contentY + contentH) {
            isDraggingRightScrollbar = true
            dragStartRightMouseY = mouseY
            dragStartRightScroll = rightPanelScrollOffset
            return true
        }

        // Clic sur un Pokémon dans l'onglet Évolutions pour naviguer vers lui
        if (currentRightPanelTab == RightPanelTab.EVOLUTIONS && mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= contentY && mouseY <= contentY + contentH) {
            for ((rect, clickedSp, _) in rightPanelClickablePokemon) {
                val (rx, ry, rw, rh) = rect
                if (mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh) {
                    val found = allTracked.find { it.species == clickedSp }
                    if (found != null) {
                        selectedTracked = found
                        individualShinyForms.clear()
                        rightPanelScrollOffset = 0.0
                        return true
                    }
                }
            }
        }

        // Clic sur le bouton JourneyMap dans le panneau droit
        val btnY = panelY + panelH - 22
        if (mouseX >= panelX + 8 && mouseX <= panelX + panelW - 8 && mouseY >= btnY && mouseY <= btnY + 16) {
            selectedTracked?.let {
                CobblemonOverlayManager.setOverlay(it.species, it.species.standardForm)
            }
            return true
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
                    individualShinyForms.clear()
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
                        individualShinyForms.clear()
                        rightPanelScrollOffset = 0.0
                        return true
                    }
                }
            }
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
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
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

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
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
