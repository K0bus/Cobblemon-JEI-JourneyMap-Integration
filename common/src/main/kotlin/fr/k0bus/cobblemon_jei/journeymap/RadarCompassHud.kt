package fr.k0bus.cobblemon_jei.journeymap

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.renderer.Rect2i

/**
 * HUD dynamique affichant la boussole et l'indicateur de guidage en jeu (overlay temps réel).
 * Indique :
 * - Le Pokémon physique traqué le plus proche (⚡ cyan).
 * - Quand on est dans la zone : confirmation + vérification de spawn immédiat (✔ vert) ou attente (⏳ orange).
 * - Quand on est hors zone : la direction et distance vers la zone éligible la plus proche (✦ vert).
 *
 * S'ajuste dynamiquement pour que Jade ne masque jamais le guidage (décalage vertical automatique et Z-order prioritaire).
 */
object RadarCompassHud {

    /**
     * Détecte si Jade est actif et retourne le bas de son infobulle pour éviter tout chevauchement.
     */
    private fun getJadeBottomOffset(): Int {
        return try {
            val clazz = Class.forName("snownee.jade.overlay.OverlayRenderer")
            val shownField = clazz.getField("shown")
            val isShown = shownField.getBoolean(null)
            if (isShown) {
                val rectField = clazz.getField("rect")
                val tooltipRect = rectField.get(null)
                if (tooltipRect != null) {
                    val rField = tooltipRect.javaClass.getField("rect")
                    val rect = rField.get(tooltipRect) as? Rect2i
                    if (rect != null && rect.height > 0) {
                        return rect.y + rect.height
                    }
                }
            }
            0
        } catch (_: Throwable) {
            0
        }
    }

    fun render(guiGraphics: GuiGraphics, partialTick: DeltaTracker? = null) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null) return
        if (mc.options.hideGui) return

        // Ne pas afficher si un écran autre que le chat est ouvert
        if (mc.screen != null && mc.screen !is ChatScreen) return

        val guidance = RadarCompassService.getCurrentGuidance() ?: return

        val font = mc.font
        val screenWidth = mc.window.guiScaledWidth

        val displayText: String
        val accentColor: Int

        when {
            // 1. Entité physique déjà apparue dans le monde
            guidance.isPhysicallyPresent -> {
                val altSuffix = if (guidance.relativeY != 0) " §8(${if (guidance.relativeY > 0) "+" else ""}${guidance.relativeY}m Y)" else ""
                displayText = "§b⚡ §e${guidance.arrowSymbol} §f${guidance.targetName} §7• §e${guidance.distanceBlocks}m$altSuffix"
                accentColor = 0xFF00D2D3.toInt()
            }
            // 2. Le joueur est déjà dans la zone de spawn
            guidance.isInZone -> {
                if (guidance.canSpawnNow) {
                    displayText = "§a✔ §fDans la zone de §e${guidance.targetName} §7• §aPeut apparaître maintenant !"
                    accentColor = 0xFF2ED573.toInt()
                } else {
                    val reason = guidance.waitingReason ?: "Conditions météo/heure"
                    displayText = "§6⏳ §fDans la zone de §e${guidance.targetName} §7• §6En attente : §e$reason"
                    accentColor = 0xFFFFA502.toInt()
                }
            }
            // 3. Le joueur est en route vers la zone
            else -> {
                displayText = "§a✦ §e${guidance.arrowSymbol} §7Zone : §f${guidance.targetName} §7• §e${guidance.distanceBlocks}m"
                accentColor = 0xFF2ED573.toInt()
            }
        }

        val textWidth = font.width(displayText)
        val boxWidth = textWidth + 16
        val boxHeight = 16
        val boxX = (screenWidth - boxWidth) / 2

        // Détection Jade pour ne JAMAIS être surpassé ou masqué
        val jadeBottom = getJadeBottomOffset()
        val boxY = if (jadeBottom > 0) jadeBottom + 4 else 8

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0f, 0f, 300f) // S'assure d'être au premier plan

        // 1. Fond semi-transparent midnight slate
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xD010131B.toInt())

        // 2. Bordures discrètes
        guiGraphics.fill(boxX - 1, boxY - 1, boxX + boxWidth + 1, boxY, 0xFF2A2E3D.toInt())
        guiGraphics.fill(boxX - 1, boxY + boxHeight, boxX + boxWidth + 1, boxY + boxHeight + 1, 0xFF2A2E3D.toInt())
        guiGraphics.fill(boxX - 1, boxY, boxX, boxY + boxHeight, 0xFF2A2E3D.toInt())
        guiGraphics.fill(boxX + boxWidth, boxY, boxX + boxWidth + 1, boxY + boxHeight, 0xFF2A2E3D.toInt())

        // 3. Barre d'accentuation supérieure
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, accentColor)

        // 4. Texte de guidage
        guiGraphics.drawString(font, displayText, boxX + 8, boxY + 4, 0xFFFFFFFF.toInt(), true)

        guiGraphics.pose().popPose()
    }
}
