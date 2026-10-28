package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import fr.k0bus.cobblemon_jei.application.expedition.ExpeditionPlannerService
import fr.k0bus.cobblemon_jei.application.expedition.ExpeditionSuggestionEngine
import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionPlan
import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionSuggestion
import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionZone
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import fr.k0bus.cobblemon_jei.journeymap.ExpeditionMapOverlay
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Interface graphique de planification et de suivi des expéditions de capture.
 *
 * Affiche :
 * 1. Les opportunités d'expéditions suggérées automatiquement (sans configuration préalable).
 * 2. Le classement des meilleures zones explorées pour les objectifs en cours.
 * 3. Les boutons d'action rapide pour tracer la zone sur JourneyMap ou poser un Waypoint natif.
 */
class CobblemonExpeditionScreen(
    private val parentScreen: Screen? = null
) : Screen(Component.literal("Expéditions Cobblemon")) {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/ExpeditionScreen")

    private var suggestions: List<ExpeditionSuggestion> = emptyList()
    private var activePlan: ExpeditionPlan? = null
    private var isLoading: Boolean = false
    private var scrollOffset: Double = 0.0
    private var maxScroll: Double = 0.0

    // Gestion de la modale Détails des Cibles (Loupe)
    private var isTargetsDetailModalOpen: Boolean = false
    private var targetsDetailTitle: String = ""
    private var targetsDetailSubtitle: String = ""
    private var targetsDetailList: List<PokemonTarget> = emptyList()
    private var targetsDetailScrollOffset: Double = 0.0
    private var targetsDetailMaxScroll: Double = 0.0

    private val itemStackCache = ConcurrentHashMap<Triple<String, Boolean, Boolean>, ItemStack>()

    private fun getItemStack(species: com.cobblemon.mod.common.pokemon.Species, shiny: Boolean = false): ItemStack {
        return itemStackCache.computeIfAbsent(Triple(species.resourceIdentifier.toString(), shiny, false)) {
            try {
                val aspects = mutableSetOf<String>()
                aspects.addAll(species.standardForm.aspects)
                if (shiny) aspects.add("shiny")
                PokemonItem.from(species, aspects, 1)
            } catch (_: Throwable) {
                ItemStack.EMPTY
            }
        }
    }

    private val cardHeight = 60
    private val cardGap = 6

    override fun init() {
        super.init()
        loadExpeditionDataAsync()

        val modalW = min(580, width - 24)
        val modalX = (width - modalW) / 2
        val modalY = 20

        val settingsBtnW = 18
        val settingsBtnH = 16
        val settingsBtnX = modalX + modalW - settingsBtnW - 6
        val settingsBtnY = modalY + 4
        val settingsBtn = CobblemonDarkButton(settingsBtnX, settingsBtnY, settingsBtnW, settingsBtnH, Component.empty(), CobblemonPokedexScreen.SETTINGS_ICON) {
            minecraft?.setScreen(CobblemonSettingsScreen(this))
        }
        settingsBtn.tooltip = Tooltip.create(Component.literal("Paramètres & Diagnostic du Mod"))
        addRenderableWidget(settingsBtn)

        val btnW = 120
        val btnH = 20
        addRenderableWidget(
            CobblemonDarkButton(width / 2 - btnW / 2, height - 26, btnW, btnH, Component.literal("Fermer")) {
                onClose()
            }
        )
    }

    private fun loadExpeditionDataAsync() {
        isLoading = true
        java.util.concurrent.CompletableFuture.supplyAsync({
            val sugs = ExpeditionSuggestionEngine.generateSuggestions()
            val plan = ExpeditionPlannerService.planForActiveGoals()
            Pair(sugs, plan)
        }).thenAcceptAsync({ result ->
            suggestions = result.first
            activePlan = result.second
            isLoading = false
        }, Minecraft.getInstance())
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Fond neutre sans déclencher le shader blur
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // 1. Fond sombre moderne et opaque
        guiGraphics.fill(0, 0, width, height, 0xFF101217.toInt())

        // 2. Fenêtre centrale
        val modalW = min(580, width - 24)
        val modalH = height - 56
        val modalX = (width - modalW) / 2
        val modalY = 20

        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF141724.toInt())
        guiGraphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY, 0xFF353D52.toInt())
        guiGraphics.fill(modalX - 1, modalY + modalH, modalX + modalW + 1, modalY + modalH + 1, 0xFF353D52.toInt())
        guiGraphics.fill(modalX - 1, modalY, modalX, modalY + modalH, 0xFF353D52.toInt())
        guiGraphics.fill(modalX + modalW, modalY, modalX + modalW + 1, modalY + modalH, 0xFF353D52.toInt())

        // Barre de titre
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 24, 0xFF1B2030.toInt())
        guiGraphics.fill(modalX, modalY + 23, modalX + modalW, modalY + 24, 0xFF2B334C.toInt())
        val titleText = "§6§lCOMPAGNON D'EXPLORATION : 5 EXPÉDITIONS SUGGÉRÉES§r §7(Opportunités Pokédex)"
        guiGraphics.drawString(font, titleText, modalX + 10, modalY + 8, 0xFFFFFFFF.toInt(), false)

        val viewportY = modalY + 28
        val viewportH = modalH - 34

        if (isLoading) {
            guiGraphics.fill(modalX + 8, viewportY, modalX + modalW - 8, viewportY + viewportH, 0xFF191C28.toInt())
            guiGraphics.drawCenteredString(font, "§e⏳ Analyse des biomes et calcul des 5 expéditions...", modalX + modalW / 2, viewportY + viewportH / 2 - 8, 0xFFFFFFFF.toInt())
            guiGraphics.drawCenteredString(font, "§7Calcul asynchrone pour préserver les FPS...", modalX + modalW / 2, viewportY + viewportH / 2 + 6, 0xFFAAAAAA.toInt())
        } else if (suggestions.isEmpty()) {
            guiGraphics.fill(modalX + 8, viewportY, modalX + modalW - 8, viewportY + viewportH, 0xFF191C28.toInt())
            guiGraphics.drawString(font, "§7Aucune opportunité pour le moment. Explorez plus de territoires sur JourneyMap !", modalX + 16, viewportY + viewportH / 2 - 4, 0xFF888888.toInt(), false)
        } else {
            val totalContentH = suggestions.size * (cardHeight + cardGap)
            maxScroll = max(0.0, (totalContentH - viewportH).toDouble())

            guiGraphics.enableScissor(modalX + 6, viewportY, modalX + modalW - 6, viewportY + viewportH)

            for ((index, sug) in suggestions.take(5).withIndex()) {
                val cardY = (viewportY + index * (cardHeight + cardGap) - scrollOffset).toInt()
                if (cardY + cardHeight >= viewportY && cardY <= viewportY + viewportH) {
                    drawSuggestionCard(guiGraphics, modalX + 8, cardY, modalW - 16, sug, mouseX, mouseY)
                }
            }

            guiGraphics.disableScissor()

            // Barre de défilement verticale
            if (maxScroll > 0) {
                val scrollbarW = 4
                val scrollbarX = modalX + modalW - scrollbarW - 3
                val thumbH = max(20, (viewportH * (viewportH.toDouble() / totalContentH)).toInt())
                val thumbY = viewportY + ((viewportH - thumbH) * (scrollOffset / maxScroll)).toInt()
                guiGraphics.fill(scrollbarX, viewportY, scrollbarX + scrollbarW, viewportY + viewportH, 0xFF1B2030.toInt())
                guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFFF39C12.toInt())
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // Rendu de la modale de Détail des Cibles (Loupe)
        renderTargetsDetailModal(guiGraphics, mouseX, mouseY)
    }

    private fun drawSuggestionCard(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        w: Int,
        suggestion: ExpeditionSuggestion,
        mouseX: Int,
        mouseY: Int
    ) {
        val isHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + cardHeight
        val bg = if (isHovered) 0xFF222838.toInt() else 0xFF181C28.toInt()
        val border = if (isHovered) 0xFFF39C12.toInt() else 0xFF2A3144.toInt()

        guiGraphics.fill(x, y, x + w, y + cardHeight, bg)
        guiGraphics.fill(x, y, x + w, y + 1, border)
        guiGraphics.fill(x, y + cardHeight - 1, x + w, y + cardHeight, border)
        guiGraphics.fill(x, y, x + 1, y + cardHeight, border)
        guiGraphics.fill(x + w - 1, y, x + w, y + cardHeight, border)

        val zone = suggestion.primaryZone

        // 1. Titre & Badge
        val badge = if (suggestion.isConditionActiveNow) " §e⚡ ACTIF MAINTENANT" else ""
        guiGraphics.drawString(font, "§6§l${suggestion.title}§r$badge", x + 8, y + 5, 0xFFFFFFFF.toInt(), false)

        // Badge Score
        val scoreText = "Score : ${zone.score.toInt()} pts"
        val scoreW = font.width(scoreText) + 8
        val scoreX = x + w - scoreW - 156
        guiGraphics.fill(scoreX, y + 4, scoreX + scoreW, y + 16, 0xFF2E220D.toInt())
        guiGraphics.fill(scoreX, y + 4, scoreX + scoreW, y + 5, 0xFFD35400.toInt())
        guiGraphics.drawString(font, "§e§l$scoreText§r", scoreX + 4, y + 6, 0xFFFFAA00.toInt(), false)

        // 2. Région de recherche & Coordonnées
        val distText = if (zone.distanceToPlayer > 0) " (${zone.distanceToPlayer.toInt()}m)" else ""
        val coordStr = if (zone.isSubterranean) "[${zone.centerPos.x}, Y=${zone.centerPos.y}, ${zone.centerPos.z}] §6⛏" else "[${zone.centerPos.x}, ${zone.centerPos.z}]"
        guiGraphics.drawString(font, "§b📍 Région : §f${zone.name} §7• Coord: $coordStr§e$distText", x + 8, y + 18, 0xFFE0E0E0.toInt(), false)

        // 3. Liste des Pokémon cibles avec noms traduits
        val targetNames = suggestion.targets.take(5).joinToString(", ") {
            PokemonTrackerService.getSpeciesTranslatedName(it.speciesId)
        }
        val moreCount = if (suggestion.targets.size > 5) " +${suggestion.targets.size - 5}" else ""
        guiGraphics.drawString(font, "§d🎯 Cibles (${suggestion.targets.size}) : §f$targetNames$moreCount", x + 8, y + 31, 0xFFFFFFFF.toInt(), false)

        // 4. Description concise
        guiGraphics.drawString(font, "§8ℹ ${suggestion.description}", x + 8, y + 45, 0xFFAAAAAA.toInt(), false)

        // 5. Boutons : [▶ Partir], [📍 Repère] et [🔍 Cibles]
        val partBtnW = 84
        val partBtnH = 18
        val partBtnX = x + w - partBtnW - 6
        val partBtnY = y + 7

        val isPartHovered = mouseX >= partBtnX && mouseX <= partBtnX + partBtnW && mouseY >= partBtnY && mouseY <= partBtnY + partBtnH
        val partBg = if (isPartHovered) 0xFF27AE60.toInt() else 0xFF2ECC71.toInt()
        guiGraphics.fill(partBtnX, partBtnY, partBtnX + partBtnW, partBtnY + partBtnH, partBg)
        guiGraphics.drawCenteredString(font, "▶ Partir", partBtnX + partBtnW / 2, partBtnY + 5, 0xFFFFFFFF.toInt())

        val markBtnW = 60
        val markBtnH = 16
        val markBtnX = x + w - markBtnW - 6
        val markBtnY = y + 33

        val isMarkHovered = mouseX >= markBtnX && mouseX <= markBtnX + markBtnW && mouseY >= markBtnY && mouseY <= markBtnY + markBtnH
        val markBg = if (isMarkHovered) 0xFFD35400.toInt() else 0xFFE67E22.toInt()
        guiGraphics.fill(markBtnX, markBtnY, markBtnX + markBtnW, markBtnY + markBtnH, markBg)
        guiGraphics.drawCenteredString(font, "📍 Repère", markBtnX + markBtnW / 2, markBtnY + 4, 0xFFFFFFFF.toInt())

        val targetBtnW = 60
        val targetBtnH = 16
        val targetBtnX = markBtnX - targetBtnW - 4
        val targetBtnY = y + 33

        val isTargetHovered = mouseX >= targetBtnX && mouseX <= targetBtnX + targetBtnW && mouseY >= targetBtnY && mouseY <= targetBtnY + targetBtnH
        val targetBg = if (isTargetHovered) 0xFF2980B9.toInt() else 0xFF3498DB.toInt()
        guiGraphics.fill(targetBtnX, targetBtnY, targetBtnX + targetBtnW, targetBtnY + targetBtnH, targetBg)
        guiGraphics.drawCenteredString(font, "🔍 Cibles", targetBtnX + targetBtnW / 2, targetBtnY + 4, 0xFFFFFFFF.toInt())
    }

    fun openTargetsDetailModal(title: String, subtitle: String, targets: List<PokemonTarget>) {
        targetsDetailTitle = title
        targetsDetailSubtitle = subtitle
        targetsDetailList = targets
        targetsDetailScrollOffset = 0.0
        isTargetsDetailModalOpen = true
    }

    fun closeTargetsDetailModal() {
        isTargetsDetailModalOpen = false
    }

    private fun getTargetsDetailModalBounds(): IntArray {
        val modalW = min(420, width - 24)
        val modalH = min(280, height - 24)
        val modalX = (width - modalW) / 2
        val modalY = (height - modalH) / 2
        return intArrayOf(modalX, modalY, modalW, modalH)
    }

    private fun renderTargetsDetailModal(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!isTargetsDetailModalOpen) return

        guiGraphics.flush()
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0f, 0f, 600f)

        // Fond sombre
        guiGraphics.fill(0, 0, width, height, 0xD0050810.toInt())

        val (modalX, modalY, modalW, modalH) = getTargetsDetailModalBounds()

        // Fenêtre principale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFA121622.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 1, 0xFF354464.toInt())
        guiGraphics.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, 0xFF354464.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalH, 0xFF354464.toInt())
        guiGraphics.fill(modalX + modalW - 1, modalY, modalX + modalW, modalY + modalH, 0xFF354464.toInt())

        // Header
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + 26, 0xFF192030.toInt())
        guiGraphics.fill(modalX + 1, modalY + 25, modalX + modalW - 1, modalY + 26, 0xFF2F3C55.toInt())
        guiGraphics.drawString(font, "🔍 §6§l$targetsDetailTitle§r", modalX + 8, modalY + 5, 0xFFFFFFFF.toInt(), false)
        guiGraphics.drawString(font, "§7$targetsDetailSubtitle", modalX + 8, modalY + 15, 0xFFAAAAAA.toInt(), false)

        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        val isCloseHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14
        val closeColor = if (isCloseHovered) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
        guiGraphics.drawString(font, "§l✕§r", closeBtnX + 3, closeBtnY + 3, closeColor, false)

        // Grille 2 colonnes
        val viewportX = modalX + 8
        val viewportY = modalY + 28
        val viewportW = modalW - 16
        val viewportH = modalH - 34

        val colGap = 6
        val colW = (viewportW - colGap) / 2
        val rowH = 22
        val rowGap = 4
        val totalRows = (targetsDetailList.size + 1) / 2
        val totalContentH = totalRows * (rowH + rowGap)
        targetsDetailMaxScroll = max(0.0, (totalContentH - viewportH).toDouble())

        guiGraphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH)

        for ((index, target) in targetsDetailList.withIndex()) {
            val col = index % 2
            val row = index / 2
            val cardX = viewportX + col * (colW + colGap)
            val cardY = (viewportY + row * (rowH + rowGap) - targetsDetailScrollOffset).toInt()

            if (cardY + rowH < viewportY || cardY > viewportY + viewportH) continue

            val isCardHovered = mouseX >= cardX && mouseX <= cardX + colW && mouseY >= cardY && mouseY <= cardY + rowH
            val cardBg = if (isCardHovered) 0xFF222B3D.toInt() else 0xFF161B27.toInt()
            val cardBorder = if (isCardHovered) 0xFF4A658E.toInt() else 0xFF252D3F.toInt()

            guiGraphics.fill(cardX, cardY, cardX + colW, cardY + rowH, cardBg)
            guiGraphics.fill(cardX, cardY, cardX + colW, cardY + 1, cardBorder)
            guiGraphics.fill(cardX, cardY + rowH - 1, cardX + colW, cardY + rowH, cardBorder)
            guiGraphics.fill(cardX, cardY, cardX + 1, cardY + rowH, cardBorder)
            guiGraphics.fill(cardX + colW - 1, cardY, cardX + colW, cardY + rowH, cardBorder)

            val sp = PokemonSpecies.getByIdentifier(target.speciesId)
            val translatedName = PokemonTrackerService.getSpeciesTranslatedName(target.speciesId)

            if (sp != null) {
                val stack = getItemStack(sp, shiny = target.shiny)
                if (!stack.isEmpty) {
                    guiGraphics.renderItem(stack, cardX + 2, cardY + 3)
                }
            }

            val isCaught = PokemonTrackerService.getAllTracked().find { it.species.resourceIdentifier == target.speciesId }?.isCaught == true
            val isSeen = PokemonTrackerService.getAllTracked().find { it.species.resourceIdentifier == target.speciesId }?.isEncountered == true
            val statusIcon = when {
                isCaught -> "§a✔"
                isSeen -> "§e👁"
                else -> "§7✕"
            }
            val shinyTag = if (target.shiny) "§e★ " else ""
            val displayName = "$statusIcon $shinyTag§f$translatedName"

            val numStr = if (sp != null) String.format("#%04d • ", sp.nationalPokedexNumber) else ""
            guiGraphics.drawString(font, displayName, cardX + 22, cardY + 3, 0xFFFFFFFF.toInt(), false)
            guiGraphics.drawString(font, "§8$numStr${target.speciesId.path}", cardX + 22, cardY + 12, 0xFF888888.toInt(), false)
        }

        guiGraphics.disableScissor()

        // Scrollbar
        if (targetsDetailMaxScroll > 0) {
            val scrollbarW = 4
            val scrollbarX = modalX + modalW - scrollbarW - 3
            val thumbH = max(20, (viewportH * (viewportH.toDouble() / totalContentH)).toInt())
            val thumbY = viewportY + ((viewportH - thumbH) * (targetsDetailScrollOffset / targetsDetailMaxScroll)).toInt()
            guiGraphics.fill(scrollbarX, viewportY, scrollbarX + scrollbarW, viewportY + viewportH, 0xFF1B2030.toInt())
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFF3498DB.toInt())
        }

        pose.popPose()
    }

    private fun handleTargetsDetailModalClick(mouseX: Double, mouseY: Double) {
        val (modalX, modalY, modalW, modalH) = getTargetsDetailModalBounds()
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14) {
            closeTargetsDetailModal()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return
        }

        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            closeTargetsDetailModal()
            return
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (isTargetsDetailModalOpen) {
            targetsDetailScrollOffset = (targetsDetailScrollOffset - scrollY * 20.0).coerceIn(0.0, targetsDetailMaxScroll)
            return true
        }
        if (maxScroll > 0) {
            scrollOffset = (scrollOffset - scrollY * 20.0).coerceIn(0.0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        if (isTargetsDetailModalOpen) {
            handleTargetsDetailModalClick(mouseX, mouseY)
            return true
        }

        val modalW = min(580, width - 24)
        val modalH = height - 56
        val modalX = (width - modalW) / 2
        val modalY = 20

        if (isLoading || suggestions.isEmpty()) return super.mouseClicked(mouseX, mouseY, button)

        val viewportY = modalY + 28
        val viewportH = modalH - 34

        for ((index, sug) in suggestions.take(5).withIndex()) {
            val cardY = (viewportY + index * (cardHeight + cardGap) - scrollOffset).toInt()
            if (cardY + cardHeight < viewportY || cardY > viewportY + viewportH) continue

            val partBtnW = 84
            val partBtnH = 18
            val partBtnX = modalX + 8 + (modalW - 16) - partBtnW - 6
            val partBtnY = cardY + 7

            if (mouseX >= partBtnX && mouseX <= partBtnX + partBtnW && mouseY >= partBtnY && mouseY <= partBtnY + partBtnH) {
                ExpeditionSuggestionEngine.acceptSuggestion(sug)
                ExpeditionMapOverlay.showZoneOverlay(sug.primaryZone)
                onClose()
                return true
            }

            val markBtnW = 60
            val markBtnH = 16
            val markBtnX = modalX + 8 + (modalW - 16) - markBtnW - 6
            val markBtnY = cardY + 33

            if (mouseX >= markBtnX && mouseX <= markBtnX + markBtnW && mouseY >= markBtnY && mouseY <= markBtnY + markBtnH) {
                ExpeditionMapOverlay.createZoneWaypoint(sug.primaryZone)
                return true
            }

            val targetBtnW = 60
            val targetBtnH = 16
            val targetBtnX = markBtnX - targetBtnW - 4
            val targetBtnY = cardY + 33

            if (mouseX >= targetBtnX && mouseX <= targetBtnX + targetBtnW && mouseY >= targetBtnY && mouseY <= targetBtnY + targetBtnH) {
                openTargetsDetailModal(sug.title, "${sug.primaryZone.name} • ${sug.targets.size} Pokémon cibles", sug.targets)
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (isTargetsDetailModalOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeTargetsDetailModal()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                return true
            }
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }
}
