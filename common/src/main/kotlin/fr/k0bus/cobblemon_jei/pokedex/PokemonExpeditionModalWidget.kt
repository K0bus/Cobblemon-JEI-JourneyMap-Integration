package fr.k0bus.cobblemon_jei.pokedex

import fr.k0bus.cobblemon_jei.application.expedition.ExpeditionPlannerService
import fr.k0bus.cobblemon_jei.application.expedition.ExpeditionSuggestionEngine
import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionPlan
import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionSuggestion
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import fr.k0bus.cobblemon_jei.journeymap.ExpeditionMapOverlay
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import org.lwjgl.glfw.GLFW
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min

/**
 * Modale de consultation et de démarrage d'expéditions de capture générées automatiquement.
 */
class PokemonExpeditionModalWidget(
    private val onOpenTargetsDetail: (String, String, List<PokemonTarget>) -> Unit,
    private val onCloseCallback: () -> Unit
) {
    var isOpen: Boolean = false
        private set

    var isLoading: Boolean = false
        private set

    private var suggestions: List<ExpeditionSuggestion> = emptyList()
    private var activePlan: ExpeditionPlan? = null
    private var scrollOffset: Double = 0.0
    private var maxScroll: Double = 0.0

    fun open() {
        isOpen = true
        scrollOffset = 0.0
        triggerCalculation()
    }

    fun close() {
        if (isOpen) {
            isOpen = false
            onCloseCallback()
        }
    }

    fun triggerCalculation() {
        isLoading = true
        scrollOffset = 0.0

        CompletableFuture.supplyAsync {
            val sug = ExpeditionSuggestionEngine.generateSuggestions()
            val plan = ExpeditionPlannerService.planForActiveGoals()
            Pair(sug, plan)
        }.thenAcceptAsync({ result ->
            suggestions = result.first
            activePlan = result.second
            isLoading = false
        }, Minecraft.getInstance())
    }

    fun getBounds(screenWidth: Int, screenHeight: Int): IntArray {
        val modalW = min(580, screenWidth - 20)
        val modalH = min(320, screenHeight - 20)
        val modalX = (screenWidth - modalW) / 2
        val modalY = (screenHeight - modalH) / 2
        return intArrayOf(modalX, modalY, modalW, modalH)
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, screenWidth: Int, screenHeight: Int, font: Font) {
        if (!isOpen) return

        guiGraphics.flush()
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0f, 0f, 400f)

        // 1. Fond sombre obscurcissant tout l'écran
        guiGraphics.fill(0, 0, screenWidth, screenHeight, 0xD0080A10.toInt())

        // 2. Fenêtre modale centrée
        val (modalX, modalY, modalW, modalH) = getBounds(screenWidth, screenHeight)

        // Fond et bordures de la modale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF141724.toInt())
        guiGraphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY, 0xFF353D52.toInt())
        guiGraphics.fill(modalX - 1, modalY + modalH, modalX + modalW + 1, modalY + modalH + 1, 0xFF353D52.toInt())
        guiGraphics.fill(modalX - 1, modalY, modalX, modalY + modalH, 0xFF353D52.toInt())
        guiGraphics.fill(modalX + modalW, modalY, modalX + modalW + 1, modalY + modalH, 0xFF353D52.toInt())

        // Barre de titre de la modale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xFF1B2030.toInt())
        guiGraphics.fill(modalX, modalY + 21, modalX + modalW, modalY + 22, 0xFF2B334C.toInt())

        val title = "§6§lCOMPAGNON D'EXPLORATION : 5 EXPÉDITIONS SUGGÉRÉES§r §7(Opportunités Pokédex)"
        guiGraphics.drawString(font, title, modalX + 8, modalY + 7, 0xFFFFFFFF.toInt(), false)

        // Bouton Fermer [✕] en haut à droite
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        val isCloseHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14
        val closeColor = if (isCloseHovered) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
        guiGraphics.drawString(font, "§l✕§r", closeBtnX + 3, closeBtnY + 3, closeColor, false)

        val viewportY = modalY + 28
        val viewportH = modalH - 34

        if (isLoading) {
            guiGraphics.fill(modalX + 8, viewportY, modalX + modalW - 8, viewportY + viewportH, 0xFF191C28.toInt())
            val loadingMsg = "§e⏳ Analyse des biomes et calcul des 5 expéditions..."
            guiGraphics.drawCenteredString(font, loadingMsg, modalX + modalW / 2, viewportY + viewportH / 2 - 4, 0xFFFFFFFF.toInt())
        } else if (suggestions.isEmpty()) {
            guiGraphics.fill(modalX + 8, viewportY, modalX + modalW - 8, viewportY + viewportH, 0xFF191C28.toInt())
            guiGraphics.drawString(font, "§7Aucune opportunité pour le moment. Explorez plus de territoires sur JourneyMap !", modalX + 16, viewportY + viewportH / 2 - 4, 0xFF888888.toInt(), false)
        } else {
            val cardH = 60
            val cardGap = 6
            val totalContentH = suggestions.size * (cardH + cardGap)
            maxScroll = max(0.0, (totalContentH - viewportH).toDouble())

            guiGraphics.enableScissor(modalX + 6, viewportY, modalX + modalW - 6, viewportY + viewportH)

            for ((index, sug) in suggestions.take(5).withIndex()) {
                val cardY = (viewportY + index * (cardH + cardGap) - scrollOffset).toInt()
                if (cardY + cardH >= viewportY && cardY <= viewportY + viewportH) {
                    drawExpeditionSuggestionCard(guiGraphics, modalX + 8, cardY, modalW - 16, cardH, sug, mouseX, mouseY, font)
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

        pose.popPose()
    }

    private fun drawExpeditionSuggestionCard(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        suggestion: ExpeditionSuggestion,
        mouseX: Int,
        mouseY: Int,
        font: Font
    ) {
        val isHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
        val bg = if (isHovered) 0xFF222838.toInt() else 0xFF181C28.toInt()
        val border = if (isHovered) 0xFFF39C12.toInt() else 0xFF2A3144.toInt()

        guiGraphics.fill(x, y, x + w, y + h, bg)
        guiGraphics.fill(x, y, x + w, y + 1, border)
        guiGraphics.fill(x, y + h - 1, x + w, y + h, border)
        guiGraphics.fill(x, y, x + 1, y + h, border)
        guiGraphics.fill(x + w - 1, y, x + w, y + h, border)

        val zone = suggestion.primaryZone

        // 1. Titre & Badge d'activité
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

        // 3. Liste des Pokémon cibles
        val targetNames = suggestion.targets.take(5).joinToString(", ") {
            PokemonTrackerService.getSpeciesTranslatedName(it.speciesId)
        }
        val moreCount = if (suggestion.targets.size > 5) " +${suggestion.targets.size - 5}" else ""
        guiGraphics.drawString(font, "§d🎯 Cibles (${suggestion.targets.size}) : §f$targetNames$moreCount", x + 8, y + 31, 0xFFFFFFFF.toInt(), false)

        // 4. Description concise
        guiGraphics.drawString(font, "§8ℹ ${suggestion.description}", x + 8, y + 45, 0xFFAAAAAA.toInt(), false)

        // 5. Boutons d'action : [▶ Partir], [📍 Repère] et [🔍 Cibles]
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

    fun mouseClicked(mouseX: Double, mouseY: Double, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isOpen) return false

        val (modalX, modalY, modalW, modalH) = getBounds(screenWidth, screenHeight)

        // 1. Bouton Fermer [✕]
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14) {
            close()
            return true
        }

        // Clic en dehors ferme la modale
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            close()
            return true
        }

        if (isLoading || suggestions.isEmpty()) return true

        val viewportY = modalY + 28
        val viewportH = modalH - 34
        val cardH = 60
        val cardGap = 6

        // Clic sur les 5 cartes de suggestions
        for ((index, sug) in suggestions.take(5).withIndex()) {
            val cardY = (viewportY + index * (cardH + cardGap) - scrollOffset).toInt()
            if (cardY + cardH < viewportY || cardY > viewportY + viewportH) continue

            val partBtnW = 84
            val partBtnH = 18
            val partBtnX = modalX + 8 + (modalW - 16) - partBtnW - 6
            val partBtnY = cardY + 7

            if (mouseX >= partBtnX && mouseX <= partBtnX + partBtnW && mouseY >= partBtnY && mouseY <= partBtnY + partBtnH) {
                ExpeditionSuggestionEngine.acceptSuggestion(sug)
                ExpeditionMapOverlay.showZoneOverlay(sug.primaryZone)
                close()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                return true
            }

            val markBtnW = 60
            val markBtnH = 16
            val markBtnX = modalX + 8 + (modalW - 16) - markBtnW - 6
            val markBtnY = cardY + 33

            if (mouseX >= markBtnX && mouseX <= markBtnX + markBtnW && mouseY >= markBtnY && mouseY <= markBtnY + markBtnH) {
                ExpeditionMapOverlay.createZoneWaypoint(sug.primaryZone)
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
                return true
            }

            val targetBtnW = 60
            val targetBtnH = 16
            val targetBtnX = markBtnX - targetBtnW - 4
            val targetBtnY = cardY + 33

            if (mouseX >= targetBtnX && mouseX <= targetBtnX + targetBtnW && mouseY >= targetBtnY && mouseY <= targetBtnY + targetBtnH) {
                onOpenTargetsDetail(sug.title, "${sug.primaryZone.name} • ${sug.targets.size} Pokémon cibles", sug.targets)
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                return true
            }
        }

        return true
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, screenWidth: Int, screenHeight: Int): Boolean =
        mouseClicked(mouseX, mouseY, screenWidth, screenHeight)

    fun mouseScrolled(scrollY: Double): Boolean {
        if (!isOpen) return false
        if (maxScroll > 0) {
            scrollOffset = (scrollOffset - scrollY * 20.0).coerceIn(0.0, maxScroll)
            return true
        }
        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean =
        mouseScrolled(scrollY)

    fun keyPressed(keyCode: Int): Boolean {
        if (!isOpen) return false
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }
        return true
    }

    fun keyPressed(keyCode: Int, scanCode: Int = 0, modifiers: Int = 0): Boolean =
        keyPressed(keyCode)

    companion object {
        fun drawExpeditionSuggestionCard(
            guiGraphics: GuiGraphics,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            suggestion: ExpeditionSuggestion,
            mouseX: Int,
            mouseY: Int,
            font: Font
        ) {
            val isHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
            val bg = if (isHovered) 0xFF222838.toInt() else 0xFF181C28.toInt()
            val border = if (isHovered) 0xFFF39C12.toInt() else 0xFF2A3144.toInt()

            guiGraphics.fill(x, y, x + w, y + h, bg)
            guiGraphics.fill(x, y, x + w, y + 1, border)
            guiGraphics.fill(x, y + h - 1, x + w, y + h, border)
            guiGraphics.fill(x, y, x + 1, y + h, border)
            guiGraphics.fill(x + w - 1, y, x + w, y + h, border)

            val zone = suggestion.primaryZone

            // 1. Titre & Badge d'activité
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

            // 3. Liste des Pokémon cibles
            val targetNames = suggestion.targets.take(5).joinToString(", ") {
                PokemonTrackerService.getSpeciesTranslatedName(it.speciesId)
            }
            val moreCount = if (suggestion.targets.size > 5) " +${suggestion.targets.size - 5}" else ""
            guiGraphics.drawString(font, "§d🎯 Cibles (${suggestion.targets.size}) : §f$targetNames$moreCount", x + 8, y + 31, 0xFFFFFFFF.toInt(), false)

            // 4. Description concise
            guiGraphics.drawString(font, "§8ℹ ${suggestion.description}", x + 8, y + 45, 0xFFAAAAAA.toInt(), false)

            // 5. Boutons d'action : [▶ Partir], [📍 Repère] et [🔍 Cibles]
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
            val markBg = if (isMarkHovered) 0xFF2980B9.toInt() else 0xFF3498DB.toInt()
            guiGraphics.fill(markBtnX, markBtnY, markBtnX + markBtnW, markBtnY + markBtnH, markBg)
            guiGraphics.drawCenteredString(font, "📍 Repère", markBtnX + markBtnW / 2, markBtnY + 4, 0xFFFFFFFF.toInt())

            val targetsBtnW = 60
            val targetsBtnH = 16
            val targetsBtnX = markBtnX - targetsBtnW - 4
            val targetsBtnY = y + 33

            val isTargetsHovered = mouseX >= targetsBtnX && mouseX <= targetsBtnX + targetsBtnW && mouseY >= targetsBtnY && mouseY <= targetsBtnY + targetsBtnH
            val targetsBg = if (isTargetsHovered) 0xFF4A5568.toInt() else 0xFF2D3748.toInt()
            guiGraphics.fill(targetsBtnX, targetsBtnY, targetsBtnX + targetsBtnW, targetsBtnY + targetsBtnH, targetsBg)
            guiGraphics.drawCenteredString(font, "🔍 Cibles", targetsBtnX + targetsBtnW / 2, targetsBtnY + 4, 0xFFFFFFFF.toInt())
        }
    }
}
