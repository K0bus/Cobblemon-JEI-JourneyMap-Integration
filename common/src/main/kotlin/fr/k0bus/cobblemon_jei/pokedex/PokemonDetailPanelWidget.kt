package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import com.mojang.blaze3d.systems.RenderSystem
import fr.k0bus.cobblemon_jei.compat.cobbleworkers.CobbleworkersCompat
import fr.k0bus.cobblemon_jei.data.CobbleworkersJobDataLoader
import fr.k0bus.cobblemon_jei.data.HowToCreate
import fr.k0bus.cobblemon_jei.data.PokemonDropDataLoader
import fr.k0bus.cobblemon_jei.data.PokemonEvolutionDataLoader
import fr.k0bus.cobblemon_jei.data.PokemonFossilDataLoader
import fr.k0bus.cobblemon_jei.data.PokemonPokeSnackResolver
import fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader
import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import fr.k0bus.cobblemon_jei.journeymap.CobblemonOverlayManager
import fr.k0bus.cobblemon_jei.journeymap.PokemonRadarService
import fr.k0bus.cobblemon_jei.journeymap.SpawnOverlayManager
import fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager
import fr.k0bus.cobblemon_jei.recipe.PokemonEvolutionRecipe
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Widget représentant le panneau d'inspection droit du Pokédex (détails du Pokémon, 8 onglets, preview et interactions).
 */
class PokemonDetailPanelWidget(
    private val getItemStack: (Species, FormData?, Boolean) -> ItemStack,
    private val onSelectSpecies: (Species, FormData?) -> Unit,
    private val onOpenZoom: (FormData?) -> Unit,
    private val onOpenTrackerModal: () -> Unit,
    private val onTrackerStateChanged: () -> Unit = {},
    private val isAnyModalOpen: () -> Boolean = { false }
) {
    var currentTab: RightPanelTab = RightPanelTab.DESCRIPTION
    var currentSpawnCategory: SpawnSubCategory = SpawnSubCategory.NATURAL

    var scrollOffset: Double = 0.0
    var maxScroll: Double = 0.0
    private var isDraggingScrollbar: Boolean = false
    private var dragStartMouseY: Double = 0.0
    private var dragStartScroll: Double = 0.0

    val individualShinyForms: MutableMap<String, Boolean> = mutableMapOf()
    var globalShinyForms: Boolean = false

    private var lastScreenWidth: Int = 0
    private var lastScreenHeight: Int = 0

    var topPreviewRect: IntArray? = null
        private set
    var watchlistHeaderButtonRect: IntArray? = null
        private set
    var trackHeaderButtonRect: IntArray? = null
        private set
    private var zoomHeaderButtonRect: IntArray? = null
    private var shinyHeaderButtonRect: IntArray? = null
    private val zoomCardButtonRects: MutableList<Pair<IntArray, FormData>> = mutableListOf()
    private val selectCardButtonRects: MutableList<Pair<IntArray, FormData>> = mutableListOf()
    private val shinyCardButtonRects: MutableList<Pair<IntArray, FormData>> = mutableListOf()
    private var fossilMapButtonRect: IntArray? = null
    private var monumentTrackerToggleRect: IntArray? = null
    private var spawnCategoryNaturalBtnRect: IntArray? = null
    private var spawnCategorySnackBtnRect: IntArray? = null
    private val rightPanelClickablePokemon: MutableList<Triple<IntArray, Species, Boolean>> = mutableListOf()

    private var hoveredTabTooltip: String? = null
    private var hoveredPokeSnackStack: ItemStack? = null
    private var hoveredZoomCardForm: FormData? = null

    private val font: Font get() = Minecraft.getInstance().font

    var selectedTracked: TrackedPokemon? = null
        private set
    var selectedForm: FormData? = null
    var allTracked: List<TrackedPokemon> = emptyList()
        private set

    private fun getFormStatusInfo(sp: Species, form: FormData, isShiny: Boolean, currentTracked: TrackedPokemon?): FormStatusInfo =
        PokedexRenderHelper.getFormStatusInfo(sp, form, isShiny, currentTracked)

    private fun getFormDisplayName(form: FormData?, sp: Species): String =
        PokedexRenderHelper.getFormDisplayName(form, sp)

    private fun truncateString(str: String, maxW: Int): String =
        PokedexRenderHelper.truncateString(font, str, maxW)


    fun resetScroll() {
        scrollOffset = 0.0
    }

    fun isFormShiny(form: FormData?): Boolean {
        if (form == null) return globalShinyForms
        val key = form.name.lowercase()
        return if (globalShinyForms) {
            !individualShinyForms.contains(key)
        } else {
            individualShinyForms.contains(key)
        }
    }

    fun toggleFormShiny(form: FormData) {
        val key = form.name.lowercase()
        if (individualShinyForms.contains(key)) {
            individualShinyForms.remove(key)
        } else {
            individualShinyForms[key] = true
        }
    }

    fun getBounds(screenWidth: Int = lastScreenWidth, screenHeight: Int = lastScreenHeight): IntArray {
        val panelX = screenWidth - 200
        val panelY = 73
        val panelW = 192
        val panelH = max(80, screenHeight - panelY - 26)
        return intArrayOf(panelX, panelY, panelW, panelH)
    }

    fun getTabRect(tab: RightPanelTab, screenWidth: Int = lastScreenWidth, screenHeight: Int = lastScreenHeight): IntArray {
        val (panelX, panelY, panelW, _) = getBounds(screenWidth, screenHeight)
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

    fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        currentTracked: TrackedPokemon?,
        selectedForm: FormData?,
        allTracked: List<TrackedPokemon>,
        screenWidth: Int = lastScreenWidth,
        screenHeight: Int = lastScreenHeight,
        onFormSelected: (FormData?) -> Unit = {}
    ): Boolean {
        if (button != 0) return false
        val (panelX, panelY, panelW, panelH) = getBounds(screenWidth, screenHeight)
        val contentY = panelY + 107
        val contentH = (panelY + panelH - 26) - contentY
        val scrollTrackX = panelX + panelW - 5

        watchlistHeaderButtonRect?.let { (bx, by, bw, bh) ->
            if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                currentTracked?.species?.let {
                    PokemonTrackingManager.toggleWatchlist(it)
                    onTrackerStateChanged()
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
                }
                return true
            }
        }

        trackHeaderButtonRect?.let { (bx, by, bw, bh) ->
            if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                onOpenTrackerModal()
                return true
            }
        }

        // Clic sur le cadre de preview en haut du panneau droit (ouvre Grand Format)
        topPreviewRect?.let { (px, py, pw, ph) ->
            if (mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + ph) {
                onOpenZoom(selectedForm ?: currentTracked?.species?.standardForm)
                return true
            }
        }

        // Clic sur les onglets du panneau droit
        for (tab in RightPanelTab.values()) {
            val (tx, ty, tw, th) = getTabRect(tab, screenWidth, screenHeight)
            if (mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY <= ty + th) {
                currentTab = tab
                scrollOffset = 0.0
                return true
            }
        }

        // Clics dans l'onglet Formes (Zoom header, Shiny header, Zoom carte, Sélection carte, Shiny carte)
        if (currentTab == RightPanelTab.FORMS) {
            zoomHeaderButtonRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    onOpenZoom(selectedForm ?: currentTracked?.species?.standardForm)
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
                        onOpenZoom(form)
                        return true
                    }
                }

                // 2. Clic sur le corps de la carte pour la sélectionner
                for ((rect, form) in selectCardButtonRects) {
                    val (bx, by, bw, bh) = rect
                    if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                        onFormSelected(form)
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

        // Clics dans l'onglet Zones (Sélecteur Naturel vs Poké Snack, ou Toggle Monument Tracker, ou Fossil JourneyMap)
        if (currentTab == RightPanelTab.SPAWNS) {
            fossilMapButtonRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    val sp = currentTracked?.species
                    if (sp != null) {
                        val origin = PokemonFossilDataLoader.getFossilLineageOrigin(sp)
                        val targetSp = origin?.first ?: sp
                        val currentTarget = StructureOverlayManager.highlightedSpecies
                        if (currentTarget != null && PokemonTrackingManager.isSameSpecies(currentTarget, targetSp)) {
                            StructureOverlayManager.highlightStructureForSpecies(null)
                        } else {
                            StructureOverlayManager.highlightStructureForSpecies(targetSp)
                        }
                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                    }
                    return true
                }
            }
            monumentTrackerToggleRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    StructureOverlayManager.cycleTrackingMode()
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                    return true
                }
            }
            spawnCategoryNaturalBtnRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    currentSpawnCategory = SpawnSubCategory.NATURAL
                    scrollOffset = 0.0
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                    return true
                }
            }
            spawnCategorySnackBtnRect?.let { (bx, by, bw, bh) ->
                if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                    currentSpawnCategory = SpawnSubCategory.POKE_SNACK
                    scrollOffset = 0.0
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                    return true
                }
            }
        }

        // Clic sur la barre de défilement du panneau droit
        if (maxScroll > 0 && mouseX >= scrollTrackX - 3 && mouseX <= scrollTrackX + 5 && mouseY >= contentY && mouseY <= contentY + contentH) {
            isDraggingScrollbar = true
            dragStartMouseY = mouseY
            dragStartScroll = scrollOffset
            return true
        }

        // Clic sur un Pokémon dans l'onglet Évolutions ou Zones pour naviguer vers lui
        if ((currentTab == RightPanelTab.EVOLUTIONS || currentTab == RightPanelTab.SPAWNS) && mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= contentY && mouseY <= contentY + contentH) {
            for ((rect, clickedSp, clickedForm) in rightPanelClickablePokemon) {
                val (rx, ry, rw, rh) = rect
                if (mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh) {
                    onSelectSpecies(clickedSp, null)
                    resetScroll()
                    return true
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
                onOpenTrackerModal()
                return true
            } else if (mouseX >= jmBtnX && mouseX <= jmBtnX + halfW) {
                currentTracked?.let {
                    CobblemonOverlayManager.setOverlay(it.species, selectedForm ?: it.species.standardForm)
                }
                return true
            }
        }

        return false
    }

    fun mouseDragged(mouseY: Double, screenWidth: Int = lastScreenWidth, screenHeight: Int = lastScreenHeight): Boolean {
        if (isDraggingScrollbar && maxScroll > 0) {
            val (_, panelY, _, panelH) = getBounds(screenWidth, screenHeight)
            val contentY = panelY + 107
            val contentH = (panelY + panelH - 26) - contentY
            val totalTrackH = contentH - 4
            val delta = mouseY - dragStartMouseY
            val scrollDelta = (delta / totalTrackH) * maxScroll
            scrollOffset = (dragStartScroll + scrollDelta).coerceIn(0.0, maxScroll)
            return true
        }
        return false
    }

    fun mouseReleased(): Boolean {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false
            return true
        }
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double, screenWidth: Int = lastScreenWidth, screenHeight: Int = lastScreenHeight): Boolean {
        val (panelX, panelY, panelW, panelH) = getBounds(screenWidth, screenHeight)
        val contentY = panelY + 107
        val contentH = (panelY + panelH - 26) - contentY
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= contentY && mouseY <= contentY + contentH) {
            scrollOffset = (scrollOffset - scrollY * 20.0).coerceIn(0.0, maxScroll)
            return true
        }
        return false
    }

        fun render(
        guiGraphics: GuiGraphics,
        currentTracked: TrackedPokemon?,
        selectedForm: FormData?,
        allTracked: List<TrackedPokemon>,
        mouseX: Int,
        mouseY: Int,
        screenWidth: Int,
        screenHeight: Int,
        font: Font
    ) {
        lastScreenWidth = screenWidth
        lastScreenHeight = screenHeight
        this.selectedTracked = currentTracked
        this.selectedForm = selectedForm
        this.allTracked = allTracked
        val (panelX, panelY, panelW, panelH) = getBounds(screenWidth, screenHeight)

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
        val formTitleSub = if (activeForm != sp.standardForm) " · §e${PokedexRenderHelper.getFormDisplayName(activeForm, sp)}§r" else ""
        guiGraphics.drawCenteredString(font, "$typeDesc · ${formStatus.text}$formTitleSub", panelX + panelW / 2, previewY + previewSize + 16, 0xFFCCCCCC.toInt())

        // Séparateur avant onglets
        guiGraphics.fill(panelX + 6, panelY + 70, panelX + panelW - 6, panelY + 71, 0xFF282D3C.toInt())

        // 2. Rendu des onglets (2 lignes)
        var hoveredTabTooltip: String? = null
        for (tab in RightPanelTab.values()) {
            val (tx, ty, tw, th) = getTabRect(tab, screenWidth, screenHeight)
            val isHovered = mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY <= ty + th
            val isActive = (currentTab == tab)

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
        val curY = contentY + 2 - scrollOffset.toInt()

        val finalY = when (currentTab) {
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
        maxScroll = max(0.0, (totalH - contentH).toDouble())

        // Barre de défilement verticale
        if (maxScroll > 0) {
            val scrollTrackX = panelX + panelW - 5
            val thumbH = max(10, (contentH * (contentH.toDouble() / totalH)).toInt())
            val thumbY = contentY + ((contentH - thumbH) * (scrollOffset / maxScroll)).toInt()
            val thumbColor = if (isDraggingScrollbar) 0xFF54A0FF.toInt() else 0xFF353C4D.toInt()
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

        val isJmActive = SpawnOverlayManager.isOverlayActive && SpawnOverlayManager.activeSpecies?.let { PokemonTrackingManager.isSameSpecies(it, sp) } == true
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
        if (!isAnyModalOpen()) {
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
            } else if (currentTab == RightPanelTab.FORMS) {
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
                            val fName = PokedexRenderHelper.getFormDisplayName(form, sp)
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

        val allForms = PokedexRenderHelper.getSpeciesForms(sp)

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
            val formTitle = PokedexRenderHelper.getFormDisplayName(form, sp)
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
            val statusInfo = PokedexRenderHelper.getFormStatusInfo(sp, form, isShiny, currentTracked)
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
            val isCurrentStandard = (form == sp.standardForm || form.name.isBlank() || form.name.equals("normal", true))
            val formEvos = allRecipes.filter {
                PokemonTrackingManager.isSameSpecies(it.sourceSpecies, sp) && (
                    (isCurrentStandard && (it.sourceForm == it.sourceSpecies.standardForm || it.sourceForm.name.isBlank() || it.sourceForm.name.equals("normal", true))) ||
                    (!isCurrentStandard && it.sourceForm.name.isNotBlank() && it.sourceForm.name.equals(form.name, true))
                )
            }
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

        val recipes = try {
            PokemonDropDataLoader.loadDropRecipes().filter { PokemonTrackingManager.isSameSpecies(it.species, sp) }
        } catch (_: Throwable) {
            emptyList()
        }
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

        val jobs = CobbleworkersJobDataLoader.loadPastureRecipes().filter { it.eligiblePokemon.any { ing -> PokemonTrackingManager.isSameSpecies(ing.species, sp) } }
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
        y += 12

        // Badge dynamique temps réel
        val mc = Minecraft.getInstance()
        val player = mc.player
        val level = mc.level
        val activeForm = selectedForm ?: sp.standardForm
        val statusRes = fr.k0bus.cobblemon_jei.data.SpawnConditionEvaluator.evaluate(player, level, sp, activeForm)
        
        val badgeX = contentX + 2
        val badgeY = y
        val badgeW = contentW - 8
        val badgeH = 14
        val isBadgeHovered = mouseX >= badgeX && mouseX <= badgeX + badgeW && mouseY >= badgeY && mouseY <= badgeY + badgeH
        
        val badgeBg = when (statusRes.status) {
            fr.k0bus.cobblemon_jei.data.SpawnEligibilityStatus.AVAILABLE_NOW -> if (isBadgeHovered) 0x662ED573 else 0x442ED573
            fr.k0bus.cobblemon_jei.data.SpawnEligibilityStatus.ENVIRONMENT_MATCH -> if (isBadgeHovered) 0x66FFA502 else 0x44FFA502
            fr.k0bus.cobblemon_jei.data.SpawnEligibilityStatus.UNAVAILABLE -> if (isBadgeHovered) 0x66FF4757 else 0x44FF4757
        }
        val badgeBorder = statusRes.status.badgeColor
        
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, badgeBg.toInt())
        guiGraphics.renderOutline(badgeX, badgeY, badgeW, badgeH, badgeBorder)
        val badgeText = "${statusRes.status.icon} ${statusRes.summary.string}"
        guiGraphics.drawString(font, badgeText, badgeX + 4, badgeY + 3, 0xFFFFFFFF.toInt(), false)
        
        if (isBadgeHovered && statusRes.blockingReasons.isNotEmpty()) {
            val tooltipLines = mutableListOf<Component>()
            tooltipLines.add(Component.literal("§6Conditions manquantes ici :"))
            for (reason in statusRes.blockingReasons) {
                tooltipLines.add(Component.literal("§7 • $reason"))
            }
            guiGraphics.renderComponentTooltip(font, tooltipLines, mouseX, mouseY)
        }
        y += badgeH + 6

        monumentTrackerToggleRect = null
        fossilMapButtonRect = null

        val spawns = PokemonSpawnDataLoader.getSpawnRecipesForSpecies(sp)
        if (spawns.isEmpty()) {
            guiGraphics.drawString(font, "§c✕ N'apparaît pas dans la nature§r", contentX + 4, y, 0xFFFF7675.toInt(), false)
            y += 12

            val fossilOrigin = fr.k0bus.cobblemon_jei.data.PokemonFossilDataLoader.getFossilLineageOrigin(sp)
            if (fossilOrigin != null) {
                val (ancestorSpecies, fossilRecipe) = fossilOrigin
                val isDirect = PokemonTrackingManager.isSameSpecies(ancestorSpecies, sp)
                val title = if (isDirect) {
                    "§6🏛 Résurrection de Fossile (Cobblemon)§r"
                } else {
                    "§6🏛 Issu du Fossile de ${ancestorSpecies.translatedName.string}§r"
                }
                guiGraphics.drawString(font, title, contentX + 4, y, 0xFFFFD700.toInt(), false)
                y += 12

                guiGraphics.drawString(font, "§eMachine requise : §fAnalyseur + Cuve + Moniteur§r", contentX + 4, y, 0xFFFFFFFF.toInt(), false)
                y += 11

                guiGraphics.drawString(font, "§7Fossile(s) d'origine :§r", contentX + 4, y, 0xFFCCCCCC.toInt(), false)
                y += 10

                for ((fIdx, fossilId) in fossilRecipe.requiredFossilItemIds.withIndex()) {
                    val stack = fossilRecipe.requiredFossilStacks.getOrNull(fIdx) ?: ItemStack.EMPTY
                    val count = fr.k0bus.cobblemon_jei.data.PokemonFossilDataLoader.getPlayerFossilCount(player, fossilId)
                    val stackX = contentX + 6
                    val stackY = y
                    if (!stack.isEmpty) {
                        guiGraphics.renderItem(stack, stackX, stackY)
                    }
                    val itemName = if (!stack.isEmpty) stack.hoverName.string else (fr.k0bus.cobblemon_jei.data.PokemonFossilDataLoader.FOSSIL_NAMES_FR[fossilId.substringAfter(":")] ?: fossilId.substringAfter(":"))
                    val countTxt = if (count > 0) "§a✔ $count en inventaire§r" else "§c✕ 0 en inventaire§r"
                    guiGraphics.drawString(font, "§f$itemName§r", stackX + 20, stackY + 1, 0xFFFFFFFF.toInt(), false)
                    guiGraphics.drawString(font, countTxt, stackX + 20, stackY + 10, 0xFFE0E0E0.toInt(), false)
                    y += 20
                }

                val structs = fossilRecipe.associatedStructures
                if (structs.isNotEmpty()) {
                    guiGraphics.drawString(font, "§dFouilles archéologiques (Pinceau) :§r", contentX + 4, y, 0xFFFF77FF.toInt(), false)
                    y += 10
                    for (structId in structs) {
                        val sName = fr.k0bus.cobblemon_jei.data.PokemonFossilDataLoader.STRUCTURE_DISPLAY_NAMES_FR[structId] ?: structId
                        guiGraphics.drawString(font, "§7 • §b$sName§r", contentX + 6, y, 0xFFE0E0E0.toInt(), false)
                        y += 9
                    }
                    y += 3

                    // Bouton interactif pour surligner les structures du fossile sur JourneyMap
                    val currentHighlight = fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager.highlightedSpecies
                    val isHighlighted = (currentHighlight != null && PokemonTrackingManager.isSameSpecies(currentHighlight, ancestorSpecies))
                    val btnX = contentX + 4
                    val btnY = y
                    val btnW = contentW - 12
                    val btnH = 14
                    val isHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH
                    val btnBg = if (isHighlighted) {
                        if (isHover) 0x662ED573 else 0x442ED573
                    } else {
                        if (isHover) 0x66FFA502 else 0x44FFA502
                    }
                    val btnBorder = if (isHighlighted) 0xFF2ED573.toInt() else 0xFFFFA502.toInt()
                    val btnTxt = if (isHighlighted) {
                        "§a✔ Structures en surbrillance (Désactiver)"
                    } else {
                        "§6🗺 Mettre en valeur sur JourneyMap"
                    }
                    guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg.toInt())
                    guiGraphics.renderOutline(btnX, btnY, btnW, btnH, btnBorder)
                    guiGraphics.drawString(font, btnTxt, btnX + 4, btnY + 3, 0xFFFFFFFF.toInt(), false)
                    fossilMapButtonRect = intArrayOf(btnX, btnY, btnW, btnH)
                    y += btnH + 6
                }

                val tipText = "§8Astuce : Des Pokémon de pâturage (ouvriers miniers) peuvent aussi déterrer des fossiles.§r"
                val tipLines = font.split(Component.literal(tipText), contentW - 8)
                for (l in tipLines) {
                    guiGraphics.drawString(font, l, contentX + 4, y, 0xFF888888.toInt(), false)
                    y += 9
                }
            } else {
                val allRecipes = PokemonEvolutionDataLoader.loadEvolutionRecipes()
                val preEvos = allRecipes.filter { PokemonTrackingManager.isSameSpecies(it.targetSpecies, sp) }
                if (preEvos.isNotEmpty()) {
                    guiGraphics.drawString(font, "§e🔄 Évolution exclusive§r", contentX + 4, y, 0xFFFFA502.toInt(), false)
                    y += 10
                    val preNames = preEvos.map { it.sourceSpecies.translatedName.string }.distinct().joinToString(", ")
                    val evoLine = "§7S'obtient en faisant évoluer : §f$preNames§r"
                    for (l in font.split(Component.literal(evoLine), contentW - 8)) {
                        guiGraphics.drawString(font, l, contentX + 4, y, 0xFFAAAAAA.toInt(), false)
                        y += 9
                    }
                    y += 3
                }

                val postEvos = allRecipes.filter { PokemonTrackingManager.isSameSpecies(it.sourceSpecies, sp) }
                if (preEvos.isEmpty() && postEvos.isNotEmpty()) {
                    guiGraphics.drawString(font, "§d🥚 Reproduction / Événement spécial§r", contentX + 4, y, 0xFFFF77FF.toInt(), false)
                    y += 10
                    val eggLine = "§7S'obtient par reproduction en pâturage ou événements spécifiques.§r"
                    for (l in font.split(Component.literal(eggLine), contentW - 8)) {
                        guiGraphics.drawString(font, l, contentX + 4, y, 0xFFAAAAAA.toInt(), false)
                        y += 9
                    }
                    y += 3
                }
            }
            return y + 6
        }

        for (spawn in spawns.take(5)) {
            if (spawn.isMonumentSpawn) {
                guiGraphics.drawString(font, "§6★ [Monument Légendaire]§r", contentX + 2, y, 0xFFFFD700.toInt(), false)
                y += 10

                val info = fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat.KNOWN_MONUMENTS[sp.name.lowercase()]
                val structName = info?.structureDisplayName ?: "Sanctuaire d'invocation"
                guiGraphics.drawString(font, "§eStructure : §f$structName§r", contentX + 4, y, 0xFFFFFFFF.toInt(), false)
                y += 9

                val pedestal = spawn.monumentPedestalBlock?.substringAfter(":")?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Piédestal d'autel"
                guiGraphics.drawString(font, "§7Bloc rituel : §b$pedestal§r", contentX + 4, y, 0xFFE0E0E0.toInt(), false)
                y += 9

                val dim = spawn.dimensions.firstOrNull()?.substringAfter(":")?.replaceFirstChar { it.uppercase() } ?: "Overworld"
                guiGraphics.drawString(font, "§7Dimension : §a$dim§r", contentX + 4, y, 0xFFE0E0E0.toInt(), false)
                y += 9

                // Badge interactif d'état du tracker
                val currentMode = fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager.trackingMode
                val badgeX = contentX + 4
                val badgeY = y
                val badgeW = contentW - 12
                val badgeH = 14
                val isHovered = mouseX >= badgeX && mouseX <= badgeX + badgeW && mouseY >= badgeY && mouseY <= badgeY + badgeH

                val (badgeBg, borderCol, modeText) = when (currentMode) {
                    fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat.MonumentTrackingMode.DISABLED ->
                        Triple(if (isHovered) 0x66FF4757 else 0x44FF4757, 0xFFFF4757.toInt(), "§c⛔ Tracker : Désactivé (Cliquer pour activer)")
                    fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat.MonumentTrackingMode.MAJOR_ONLY ->
                        Triple(if (isHovered) 0x662ED573 else 0x442ED573, 0xFF2ED573.toInt(), "§a🏛 Tracker : Monuments Majeurs (Actif)")
                    fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat.MonumentTrackingMode.FOOTPRINTS_ONLY ->
                        Triple(if (isHovered) 0x661E90FF else 0x441E90FF, 0xFF1E90FF.toInt(), "§b🐾 Tracker : Empreintes Seules (Actif)")
                    fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat.MonumentTrackingMode.ALL ->
                        Triple(if (isHovered) 0x66FFA502 else 0x44FFA502, 0xFFFFA502.toInt(), "§6🏛+🐾 Tracker : Tout Tracker (Actif)")
                }

                guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, badgeBg.toInt())
                guiGraphics.renderOutline(badgeX, badgeY, badgeW, badgeH, borderCol)
                guiGraphics.drawString(font, modeText, badgeX + 4, badgeY + 3, 0xFFFFFFFF.toInt(), false)
                monumentTrackerToggleRect = intArrayOf(badgeX, badgeY, badgeW, badgeH)
                y += badgeH + 4

                val discovered = fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager.getDiscoveredMonumentForSpecies(sp.name)
                if (discovered != null) {
                    val p = discovered.pedestalPos ?: discovered.pos
                    val sName = discovered.metadata.displayName
                    guiGraphics.drawString(font, "§a✔ $sName à [${p.x}, ${p.y}, ${p.z}]§r", contentX + 4, y, 0xFF2ED573.toInt(), false)
                    y += 9
                    guiGraphics.drawString(font, "§6★ Waypoint actif sur la minimap§r", contentX + 4, y, 0xFFFFD700.toInt(), false)
                    y += 10
                } else {
                    guiGraphics.drawString(font, "§c✗ Non localisé dans ce monde§r", contentX + 4, y, 0xFFFF7675.toInt(), false)
                    y += 9
                    guiGraphics.drawString(font, "§8(Explorez les chunks pour cartographier le monument)§r", contentX + 4, y, 0xFFAAAAAA.toInt(), false)
                    y += 10
                }
                guiGraphics.fill(contentX + 2, y, contentX + contentW - 4, y + 1, 0xFF3D4050.toInt())
                y += 5
                continue
            }

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
            val isFishing = spawn.context.contains("fishing", ignoreCase = true) || spawn.rodType != null || spawn.bait != null
            val ctxStr = if (isFishing) "§b🎣 Pêche§r" else spawn.context
            guiGraphics.drawString(font, "§7$timeStr · $weatherStr · $ctxStr§r", contentX + 4, y, 0xFFCCCCCC.toInt(), false)
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
        val preEvos = allRecipes.filter { PokemonTrackingManager.isSameSpecies(it.targetSpecies, sp) }

        if (preEvos.isEmpty()) {
            guiGraphics.drawString(font, "§7• Aucune pré-évolution (base)§r", contentX + 4, y, 0xFF888888.toInt(), false)
            y += 12
        } else {
            for (pre in preEvos) {
                val preSp = pre.sourceSpecies
                val stack = getItemStack(preSp, pre.sourceForm, false)
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

                if (pre.requiredItems.isNotEmpty()) {
                    var itemX = contentX + 6
                    for (reqStack in pre.requiredItems) {
                        if (!reqStack.isEmpty) {
                            guiGraphics.renderItem(reqStack, itemX, y)
                            itemX += 18
                        }
                    }
                    if (itemX > contentX + 6) y += 18
                }
                if ((pre.stepsAmount ?: 0) > 0) {
                    guiGraphics.drawString(font, "  §b💡 Sortez le Pokémon (R) et marchez (${pre.stepsAmount} pas)§r", contentX + 2, y, 0xFF85C1E9.toInt(), false)
                    y += 9
                }
                if ((pre.minFriendship ?: 0) > 0) {
                    guiGraphics.drawString(font, "  §b💡 Augmentez l'amitié (Grelot Zen, combats, baies amitié)§r", contentX + 2, y, 0xFF85C1E9.toInt(), false)
                    y += 9
                }

                val ownedCount = allTracked.find { PokemonTrackingManager.isSameSpecies(it.species, preSp) }?.ownedInstances?.count {
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

        val nextEvos = allRecipes.filter { PokemonTrackingManager.isSameSpecies(it.sourceSpecies, sp) }

        if (nextEvos.isEmpty()) {
            guiGraphics.drawString(font, "§7• Aucune évolution suivante (final)§r", contentX + 4, y, 0xFF888888.toInt(), false)
            y += 12
        } else {
            for (next in nextEvos) {
                val tgtSp = next.targetSpecies
                val stack = getItemStack(tgtSp, next.targetForm, false)
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

                if (next.requiredItems.isNotEmpty()) {
                    var itemX = contentX + 6
                    for (reqStack in next.requiredItems) {
                        if (!reqStack.isEmpty) {
                            guiGraphics.renderItem(reqStack, itemX, y)
                            itemX += 18
                        }
                    }
                    if (itemX > contentX + 6) y += 18
                }
                if ((next.stepsAmount ?: 0) > 0) {
                    guiGraphics.drawString(font, "  §b💡 Sortez le Pokémon (R) et marchez (${next.stepsAmount} pas)§r", contentX + 2, y, 0xFF85C1E9.toInt(), false)
                    y += 9
                }
                if ((next.minFriendship ?: 0) > 0) {
                    guiGraphics.drawString(font, "  §b💡 Augmentez l'amitié (Grelot Zen, combats, baies amitié)§r", contentX + 2, y, 0xFF85C1E9.toInt(), false)
                    y += 9
                }
                if ((next.damageTaken ?: 0) > 0) {
                    guiGraphics.drawString(font, "  §b💡 Subir ${next.damageTaken} dégâts sans tomber K.O.§r", contentX + 2, y, 0xFF85C1E9.toInt(), false)
                    y += 9
                }
                if ((next.recoilDamage ?: 0) > 0) {
                    guiGraphics.drawString(font, "  §b💡 Subir ${next.recoilDamage} dégâts de recul sans tomber K.O.§r", contentX + 2, y, 0xFF85C1E9.toInt(), false)
                    y += 9
                }
                if ((next.criticalHits ?: 0) > 0) {
                    guiGraphics.drawString(font, "  §b💡 Infliger ${next.criticalHits} coups critiques en un combat§r", contentX + 2, y, 0xFF85C1E9.toInt(), false)
                    y += 9
                }
                if (next.defeatAmount != null && next.defeatAmount > 0) {
                    val tgt = next.defeatTarget ?: "ennemis"
                    guiGraphics.drawString(font, "  §b💡 Vaincre ${next.defeatAmount}x $tgt§r", contentX + 2, y, 0xFF85C1E9.toInt(), false)
                    y += 9
                }
                if (next.partyMember != null) {
                    guiGraphics.drawString(font, "  §b💡 Avoir un ${next.partyMember} dans votre équipe§r", contentX + 2, y, 0xFF85C1E9.toInt(), false)
                    y += 9
                }

                val currentTracked = allTracked.find { PokemonTrackingManager.isSameSpecies(it.species, sp) }
                val hasReqForm = currentTracked?.ownedInstances?.any {
                    PokemonTrackerService.isFormMatch(it.pokemon, next.sourceForm, sp)
                } ?: false

                if (!hasReqForm && (currentTracked?.ownedCount ?: 0) > 0 && next.sourceForm != sp.standardForm && next.sourceForm.name.isNotBlank() && next.sourceForm.name != "normal") {
                    guiGraphics.drawString(font, "  §c⚠ Nécessite la forme ${next.sourceForm.name.replaceFirstChar(Char::titlecase)} !§r", contentX + 2, y, 0xFFFFFFFF.toInt(), false)
                    y += 10
                }

                val isCaught = allTracked.find { PokemonTrackingManager.isSameSpecies(it.species, tgtSp) }?.isCaught == true
                val caughtTxt = if (isCaught) "  §a✔ Déjà enregistré au Pokédex§r" else "  §6✕ Non découvert au Pokédex§r"
                guiGraphics.drawString(font, caughtTxt, contentX + 2, y, 0xFFFFFFFF.toInt(), false)
                y += 13
            }
        }

        return y + 4
    }


}
