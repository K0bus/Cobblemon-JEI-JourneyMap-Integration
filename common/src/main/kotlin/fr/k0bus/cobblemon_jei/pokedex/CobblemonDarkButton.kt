package fr.k0bus.cobblemon_jei.pokedex

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Bouton d'interface aligné sur la DA sombre du mod Cobblemon JEI.
 * Supporte le rendu d'une icône custom, le style d'onglet sélectionné et les états hover/focus/disabled.
 */
class CobblemonDarkButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    private val iconRes: ResourceLocation? = null,
    private val isTab: Boolean = false,
    private val isSelected: () -> Boolean = { false },
    onPress: (Button) -> Unit
) : Button(x, y, width, height, message, OnPress { onPress(it) }, DEFAULT_NARRATION) {

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val selected = isSelected()
        val hovered = isHoveredOrFocused && active

        val bgColor = when {
            !active -> 0x66141720.toInt()
            selected -> 0xFF1D263B.toInt()
            hovered -> 0xFF252C3D.toInt()
            else -> 0xFF161922.toInt()
        }

        val borderColor = when {
            !active -> 0xFF202430.toInt()
            selected -> 0xFF3498DB.toInt()
            hovered -> 0xFF4A90E2.toInt()
            else -> 0xFF282D3C.toInt()
        }

        val textColor = when {
            !active -> 0xFF555555.toInt()
            selected -> 0xFFFFFFFF.toInt()
            hovered -> 0xFFFFFFFF.toInt()
            else -> 0xFFCCCCCC.toInt()
        }

        guiGraphics.fill(x, y, x + width, y + height, bgColor)
        guiGraphics.fill(x, y, x + width, y + 1, borderColor)
        guiGraphics.fill(x, y + height - 1, x + width, y + height, borderColor)
        guiGraphics.fill(x, y, x + 1, y + height, borderColor)
        guiGraphics.fill(x + width - 1, y, x + width, y + height, borderColor)

        if (selected && isTab) {
            guiGraphics.fill(x + 1, y + height - 2, x + width - 1, y + height, 0xFF3498DB.toInt())
        }

        var textStartX = x
        if (iconRes != null) {
            val iconSize = 12
            val iconX = if (message.string.isBlank()) x + (width - iconSize) / 2 else x + 5
            val iconY = y + (height - iconSize) / 2

            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            val a = ((textColor shr 24) and 0xFF) / 255f
            val r = ((textColor shr 16) and 0xFF) / 255f
            val g = ((textColor shr 8) and 0xFF) / 255f
            val b = (textColor and 0xFF) / 255f
            RenderSystem.setShaderColor(r, g, b, if (a > 0f) a else 1f)
            guiGraphics.blit(iconRes, iconX, iconY, iconSize, iconSize, 0f, 0f, 512, 512, 512, 512)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            if (message.string.isNotBlank()) {
                textStartX = iconX + iconSize + 4
            }
        }

        if (message.string.isNotBlank()) {
            val font = Minecraft.getInstance().font
            val textY = y + (height - 8) / 2
            if (iconRes != null) {
                guiGraphics.drawString(font, message, textStartX, textY, textColor, false)
            } else {
                guiGraphics.drawCenteredString(font, message, x + width / 2, textY, textColor)
            }
        }
    }
}
