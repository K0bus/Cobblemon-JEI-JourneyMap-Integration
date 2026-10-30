package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import kotlin.math.max
import kotlin.math.min

/**
 * Modale de consultation détaillée des Pokémon cibles (affichage en loupe).
 * Partagée entre CobblemonPokedexScreen et CobblemonExpeditionScreen.
 */
class PokemonTargetsDetailModalWidget(
    private val getItemStack: (Species, Boolean) -> ItemStack,
    private val isPokemonCaught: (Species) -> Boolean = { false },
    private val isPokemonEncountered: (Species) -> Boolean = { false },
    private val onCloseCallback: () -> Unit = {}
) {
    var isOpen: Boolean = false
        private set

    private var title: String = ""
    private var subtitle: String = ""
    private var targets: List<PokemonTarget> = emptyList()
    private var scrollOffset: Double = 0.0
    private var maxScroll: Double = 0.0

    fun open(title: String, subtitle: String, targets: List<PokemonTarget>) {
        this.title = title
        this.subtitle = subtitle
        this.targets = targets
        this.scrollOffset = 0.0
        this.isOpen = true
    }

    fun close() {
        if (isOpen) {
            isOpen = false
            onCloseCallback()
        }
    }

    fun getBounds(screenWidth: Int, screenHeight: Int): IntArray {
        val modalW = min(420, screenWidth - 24)
        val modalH = min(280, screenHeight - 24)
        val modalX = (screenWidth - modalW) / 2
        val modalY = (screenHeight - modalH) / 2
        return intArrayOf(modalX, modalY, modalW, modalH)
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, screenWidth: Int, screenHeight: Int, font: Font) {
        if (!isOpen) return

        guiGraphics.flush()
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0f, 0f, 600f)

        // 1. Fond sombre obscurcissant
        guiGraphics.fill(0, 0, screenWidth, screenHeight, 0xD0050810.toInt())

        val (modalX, modalY, modalW, modalH) = getBounds(screenWidth, screenHeight)

        // 2. Fenêtre principale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFA121622.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 1, 0xFF354464.toInt())
        guiGraphics.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, 0xFF354464.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalH, 0xFF354464.toInt())
        guiGraphics.fill(modalX + modalW - 1, modalY, modalX + modalW, modalY + modalH, 0xFF354464.toInt())

        // 3. Header
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + 26, 0xFF192030.toInt())
        guiGraphics.fill(modalX + 1, modalY + 25, modalX + modalW - 1, modalY + 26, 0xFF2F3C55.toInt())
        guiGraphics.drawString(font, "🔍 §6§l$title§r", modalX + 8, modalY + 5, 0xFFFFFFFF.toInt(), false)
        guiGraphics.drawString(font, "§7$subtitle", modalX + 8, modalY + 15, 0xFFAAAAAA.toInt(), false)

        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        val isCloseHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14
        val closeColor = if (isCloseHovered) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
        guiGraphics.drawString(font, "§l✕§r", closeBtnX + 3, closeBtnY + 3, closeColor, false)

        // 4. Liste des cibles en grille 2 colonnes
        val viewportX = modalX + 8
        val viewportY = modalY + 28
        val viewportW = modalW - 16
        val viewportH = modalH - 34

        val colGap = 6
        val colW = (viewportW - colGap) / 2
        val rowH = 22
        val rowGap = 4
        val totalRows = (targets.size + 1) / 2
        val totalContentH = totalRows * (rowH + rowGap)
        maxScroll = max(0.0, (totalContentH - viewportH).toDouble())

        guiGraphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH)

        for ((index, target) in targets.withIndex()) {
            val col = index % 2
            val row = index / 2
            val cardX = viewportX + col * (colW + colGap)
            val cardY = (viewportY + row * (rowH + rowGap) - scrollOffset).toInt()

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
                val stack = getItemStack(sp, target.shiny)
                if (!stack.isEmpty) {
                    guiGraphics.renderItem(stack, cardX + 2, cardY + 3)
                }
            }

            val isCaught = sp != null && isPokemonCaught(sp)
            val isSeen = sp != null && isPokemonEncountered(sp)
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

        // 5. Scrollbar
        if (maxScroll > 0) {
            val scrollbarW = 4
            val scrollbarX = modalX + modalW - scrollbarW - 3
            val thumbH = max(20, (viewportH * (viewportH.toDouble() / totalContentH)).toInt())
            val thumbY = viewportY + ((viewportH - thumbH) * (scrollOffset / maxScroll)).toInt()
            guiGraphics.fill(scrollbarX, viewportY, scrollbarX + scrollbarW, viewportY + viewportH, 0xFF1B2030.toInt())
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFF3498DB.toInt())
        }

        pose.popPose()
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isOpen) return false

        val (modalX, modalY, modalW, modalH) = getBounds(screenWidth, screenHeight)
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14) {
            close()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }

        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            close()
            return true
        }
        return true
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, screenWidth: Int, screenHeight: Int): Boolean =
        mouseClicked(mouseX, mouseY, screenWidth, screenHeight)

    fun mouseScrolled(scrollY: Double): Boolean {
        if (!isOpen) return false
        scrollOffset = (scrollOffset - scrollY * 20.0).coerceIn(0.0, maxScroll)
        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean =
        mouseScrolled(scrollY)

    fun keyPressed(keyCode: Int, scanCode: Int = 0, modifiers: Int = 0): Boolean {
        if (!isOpen) return false
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            close()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }
        return true
    }
}
