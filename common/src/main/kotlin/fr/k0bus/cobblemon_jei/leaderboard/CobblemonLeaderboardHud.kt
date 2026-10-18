package fr.k0bus.cobblemon_jei.leaderboard

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.network.chat.Component

/**
 * Scoreboard Custom HUD : remplace intégralement la sidebar scoreboard vanilla de Minecraft
 * en positionnant un affichage compact, discret et soigné directement sous la minimap JourneyMap.
 *
 * - Se masque totalement pendant les combats Cobblemon.
 * - Fond sombre et discret avec opacité allégée (~40%) pour un impact visuel minimal sur le HUD.
 * - Format ultra-compact (largeur et espacement réduits).
 * - Intègre par défaut TOUS les objectifs de CobblemonSB (statistiques personnelles et leaders).
 * - La touche de raccourci permet de basculer vers les classements détaillés Top 5 par catégorie, ou de masquer le HUD.
 */
object CobblemonLeaderboardHud {

    enum class HudMode {
        ALL_OBJECTIVES,
        POKEDEX,
        CAPTURES,
        SHINIES,
        CAP_LEVEL
    }

    var isVisible: Boolean = true
    private var modeIndex: Int = 0

    /**
     * Vérifie si le joueur est actuellement engagé dans un combat Pokémon.
     */
    fun isInBattle(): Boolean {
        return try {
            com.cobblemon.mod.common.client.CobblemonClient.battle != null
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Fait défiler les modes : Tous les Objectifs -> Pokédex -> Captures -> Shinies -> Cap Level -> Masqué.
     */
    fun cycleOrToggle() {
        if (!CobblemonScoreboardService.isAvailable()) {
            isVisible = false
            return
        }

        if (!isVisible) {
            isVisible = true
            modeIndex = 0
            return
        }

        modeIndex++
        if (modeIndex > 4) {
            isVisible = false
            modeIndex = 0
        }
    }

    fun getCurrentMode(): HudMode {
        return when (modeIndex) {
            1 -> HudMode.POKEDEX
            2 -> HudMode.CAPTURES
            3 -> HudMode.SHINIES
            4 -> HudMode.CAP_LEVEL
            else -> HudMode.ALL_OBJECTIVES
        }
    }

    /**
     * Indique si la sidebar scoreboard vanilla doit être entièrement masquée
     * pour laisser la place à notre HUD personnalisé (ou masquée pendant un combat).
     */
    fun shouldHideVanillaSidebar(): Boolean {
        if (isInBattle()) return true
        if (!isVisible) return false
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return false
        return CobblemonScoreboardService.isAvailable()
    }

    /**
     * Récupère la coordonnée Y du bas de la minimap JourneyMap si présente dans le coin haut-droit.
     */
    private fun getMinimapBottom(screenHeight: Int): Int {
        try {
            val api = fr.k0bus.cobblemon_jei.journeymap.CobblemonJourneyMapPlugin.clientApi
            if (api != null) {
                val uiState = api.getUIState(journeymap.api.v2.client.display.Context.UI.Minimap)
                if (uiState != null && uiState.active && uiState.displayBounds != null) {
                    val b = uiState.displayBounds
                    val mc = Minecraft.getInstance()
                    val guiScale = mc.window.guiScale.coerceAtLeast(1.0)
                    val scaleFactor = if (b.y + b.height > screenHeight) guiScale else 1.0
                    val bottom = ((b.y + b.height) / scaleFactor).toInt()
                    if (bottom in 10 until screenHeight) {
                        return bottom
                    }
                }
            }
        } catch (_: Throwable) {}
        return 0
    }

    /**
     * Calcule la position du HUD : aligné à droite à la place de la sidebar scoreboard vanilla,
     * centré verticalement et positionné sous la minimap si celle-ci est présente.
     */
    private fun computeHudPosition(screenWidth: Int, screenHeight: Int, cardWidth: Int, cardHeight: Int): Pair<Int, Int> {
        val x = (screenWidth - cardWidth - 3).coerceAtLeast(4)
        val minimapBottom = getMinimapBottom(screenHeight)
        val defaultCenterY = (screenHeight - cardHeight) / 2
        val y = maxOf(defaultCenterY, minimapBottom + 4).coerceIn(4, maxOf(4, screenHeight - cardHeight - 4))
        return Pair(x, y)
    }

    /**
     * Rendu principal du HUD en jeu.
     */
    fun render(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        val mc = Minecraft.getInstance()
        if (!isVisible || isInBattle() || mc.options.hideGui || mc.screen != null || mc.gui.debugOverlay.showDebugScreen()) {
            return
        }

        if (!CobblemonScoreboardService.isAvailable()) {
            return
        }

        val screenWidth = mc.window.guiScaledWidth
        val screenHeight = mc.window.guiScaledHeight
        val currentMode = getCurrentMode()

        if (currentMode == HudMode.ALL_OBJECTIVES) {
            renderAllObjectivesView(guiGraphics, mc, screenWidth, screenHeight)
        } else {
            val cat = when (currentMode) {
                HudMode.POKEDEX -> CobblemonLeaderboardCategory.POKEDEX
                HudMode.CAPTURES -> CobblemonLeaderboardCategory.CAPTURES
                HudMode.SHINIES -> CobblemonLeaderboardCategory.SHINIES
                HudMode.CAP_LEVEL -> CobblemonLeaderboardCategory.CAP_LEVEL
                else -> CobblemonLeaderboardCategory.POKEDEX
            }
            renderCategoryDetailView(guiGraphics, mc, screenWidth, screenHeight, cat)
        }
    }

    /**
     * Affiche la vue globale "Tous les Objectifs", compacte et discrète (~40% d'opacité).
     */
    private fun renderAllObjectivesView(guiGraphics: GuiGraphics, mc: Minecraft, screenWidth: Int, screenHeight: Int) {
        val font = mc.font
        val cardWidth = 158
        val headerHeight = 15

        val stats = CobblemonScoreboardService.getLocalPersonalStats()
        val topDex = CobblemonScoreboardService.getTopEntries(CobblemonLeaderboardCategory.POKEDEX, 1).firstOrNull()
        val topCapt = CobblemonScoreboardService.getTopEntries(CobblemonLeaderboardCategory.CAPTURES, 1).firstOrNull()
        val topShiny = CobblemonScoreboardService.getTopEntries(CobblemonLeaderboardCategory.SHINIES, 1).firstOrNull()
        val topCap = CobblemonScoreboardService.getTopEntries(CobblemonLeaderboardCategory.CAP_LEVEL, 1).firstOrNull()
        val hasValidCapLeader = topCap != null && topCap.score > 5

        val leaderCount = if (hasValidCapLeader) 4 else 3
        val personalLinesHeight = 20
        val sectionTitleHeight = 11
        val leaderRowHeight = 11
        val cardHeight = headerHeight + personalLinesHeight + sectionTitleHeight + (leaderCount * leaderRowHeight) + 3

        val (cardX, cardY) = computeHudPosition(screenWidth, screenHeight, cardWidth, cardHeight)

        // 1. Fond sombre translucide discret
        drawCardBackground(guiGraphics, cardX, cardY, cardWidth, cardHeight, headerHeight)

        // 2. En-tête : Titre Cobblemon SB
        val titleText = "§6✦ §eCOBBLEMON SB §6✦"
        val titleWidth = font.width(Component.literal(titleText))
        guiGraphics.drawString(font, Component.literal(titleText), cardX + (cardWidth - titleWidth) / 2, cardY + 3, 0xFFFFFF, true)

        // 3. Section Statistiques Personnelles (Dex, Capt, Shiny, Cap)
        var rowY = cardY + headerHeight + 2
        val line1 = "§a📖 Dex: §f${stats.pokedex} §8| §c🔴 Capt: §f${stats.caught}"
        guiGraphics.drawString(font, Component.literal(line1), cardX + 5, rowY, 0xFFFFFF, false)

        rowY += 9
        val capStr = if (stats.capLevel > 5) "§d${stats.capLevel}" else "§7—"
        val line2 = "§6✨ Shiny: §f${stats.shinies} §8| §d👑 Cap: $capStr"
        guiGraphics.drawString(font, Component.literal(line2), cardX + 5, rowY, 0xFFFFFF, false)

        // 4. Titre de section discret entre les stats du joueur et le Toplist
        val sectionTitleY = rowY + 10
        val sectionTitle = "§8— §eTop Serveur §8—"
        val sectionTitleW = font.width(Component.literal(sectionTitle))
        guiGraphics.drawString(font, Component.literal(sectionTitle), cardX + (cardWidth - sectionTitleW) / 2, sectionTitleY, 0xFFFFFF, false)

        // 5. Section Leaders du serveur (sans #1)
        var leaderY = sectionTitleY + 10
        renderAllObjectiveLeaderLine(guiGraphics, font, "§aDex: ", topDex, cardX, leaderY, cardWidth, "§a")
        leaderY += leaderRowHeight

        renderAllObjectiveLeaderLine(guiGraphics, font, "§cCapt: ", topCapt, cardX, leaderY, cardWidth, "§c")
        leaderY += leaderRowHeight

        renderAllObjectiveLeaderLine(guiGraphics, font, "§6Shiny: ", topShiny, cardX, leaderY, cardWidth, "§6")
        leaderY += leaderRowHeight

        if (hasValidCapLeader) {
            renderAllObjectiveLeaderLine(guiGraphics, font, "§dCap: ", topCap, cardX, leaderY, cardWidth, "§d")
        }
    }

    private fun renderAllObjectiveLeaderLine(
        guiGraphics: GuiGraphics,
        font: net.minecraft.client.gui.Font,
        label: String,
        leader: LeaderboardPlayerEntry?,
        cardX: Int,
        y: Int,
        width: Int,
        colorPrefix: String
    ) {
        val labelW = font.width(Component.literal(label))
        guiGraphics.drawString(font, Component.literal(label), cardX + 4, y + 1, 0xFFFFFF, false)

        if (leader == null || leader.score <= 0) {
            guiGraphics.drawString(font, Component.literal("§7—"), cardX + 4 + labelW + 2, y + 1, 0x888888, false)
            return
        }

        if (leader.isLocalPlayer) {
            guiGraphics.fill(cardX + 2, y, cardX + width - 2, y + 10, 0x1AFFE066)
        }

        val faceX = cardX + 34
        val skin = leader.skin ?: CobblemonScoreboardService.resolvePlayerSkin(leader.username)
        try {
            PlayerFaceRenderer.draw(guiGraphics, skin.texture, faceX, y + 1, 8)
        } catch (_: Throwable) {}

        val nameX = faceX + 10
        val scoreStr = "§7($colorPrefix${leader.score}§7)"
        val scoreW = font.width(Component.literal(scoreStr))
        val maxNameW = (cardX + width - scoreW - 4) - nameX

        var name = leader.username
        if (font.width(name) > maxNameW) {
            name = font.plainSubstrByWidth(name, maxNameW - 3) + "…"
        }

        val nameColor = if (leader.isLocalPlayer) 0xFFE066 else 0xCCCCCC
        guiGraphics.drawString(font, Component.literal(name), nameX, y + 1, nameColor, false)
        guiGraphics.drawString(font, Component.literal(scoreStr), cardX + width - scoreW - 3, y + 1, 0xFFFFFF, false)
    }

    /**
     * Affiche la vue détaillée Top 5 pour une catégorie spécifique.
     */
    private fun renderCategoryDetailView(
        guiGraphics: GuiGraphics,
        mc: Minecraft,
        screenWidth: Int,
        screenHeight: Int,
        category: CobblemonLeaderboardCategory
    ) {
        val font = mc.font
        val topEntries = CobblemonScoreboardService.getTopEntries(category, 5)
        val localEntry = CobblemonScoreboardService.getLocalPlayerEntry(category)
        val showLocalSeparately = localEntry != null && topEntries.isNotEmpty() && topEntries.none { it.isLocalPlayer }

        val cardWidth = 158
        val headerHeight = 15
        val rowHeight = 11
        val entryCount = if (topEntries.isEmpty()) 1 else topEntries.size
        val separatorHeight = if (showLocalSeparately) 4 else 0
        val localRowHeight = if (showLocalSeparately) rowHeight else 0
        val cardHeight = headerHeight + (entryCount * rowHeight) + separatorHeight + localRowHeight + 4

        val (cardX, cardY) = computeHudPosition(screenWidth, screenHeight, cardWidth, cardHeight)

        drawCardBackground(guiGraphics, cardX, cardY, cardWidth, cardHeight, headerHeight)

        // En-tête : Titre et Icône
        val titleText = "${category.iconEmoji} §l${category.shortName}§r §7(Top 5)§r"
        guiGraphics.drawString(font, Component.literal(titleText), cardX + 5, cardY + 3, 0xFFFFFF, true)

        var rowY = cardY + headerHeight + 2
        if (topEntries.isEmpty()) {
            guiGraphics.drawString(font, Component.literal("§7Aucun joueur classé"), cardX + 6, rowY + 1, 0x888888, false)
        } else {
            for (entry in topEntries) {
                renderPlayerDetailRow(guiGraphics, font, entry, cardX, rowY, cardWidth, category)
                rowY += rowHeight
            }
        }

        if (showLocalSeparately) {
            val sepY = rowY + 1
            guiGraphics.fill(cardX + 5, sepY, cardX + cardWidth - 5, sepY + 1, 0x22FFFFFF)
            val localY = sepY + 2
            renderPlayerDetailRow(guiGraphics, font, localEntry, cardX, localY, cardWidth, category, isStickyLocal = true)
        }
    }

    private fun renderPlayerDetailRow(
        guiGraphics: GuiGraphics,
        font: net.minecraft.client.gui.Font,
        entry: LeaderboardPlayerEntry,
        cardX: Int,
        y: Int,
        width: Int,
        category: CobblemonLeaderboardCategory,
        isStickyLocal: Boolean = false
    ) {
        val isLocal = entry.isLocalPlayer

        if (isLocal) {
            guiGraphics.fill(cardX + 2, y, cardX + width - 2, y + 10, 0x1AFFE066)
        }

        val rankStr = when (entry.rank) {
            1 -> "§6#1"
            2 -> "§7#2"
            3 -> "§c#3"
            else -> "§8#${entry.rank}"
        }
        guiGraphics.drawString(font, Component.literal(rankStr), cardX + 4, y + 1, 0xFFFFFF, false)

        val faceX = cardX + 22
        val skin = entry.skin ?: CobblemonScoreboardService.resolvePlayerSkin(entry.username)
        try {
            PlayerFaceRenderer.draw(guiGraphics, skin.texture, faceX, y + 1, 8)
        } catch (_: Throwable) {}

        val nameX = faceX + 10
        val nameColor = if (isLocal) 0xFFE066 else 0xCCCCCC

        val scoreStr = when (category) {
            CobblemonLeaderboardCategory.POKEDEX -> "§a${entry.caughtCount}§7C §8| §6${entry.seenCount}§7V"
            CobblemonLeaderboardCategory.CAPTURES -> "§c${entry.caughtCount}§7C §8| §6${entry.seenCount}§7V"
            CobblemonLeaderboardCategory.SHINIES -> "§6${entry.score}§7✨"
            CobblemonLeaderboardCategory.CAP_LEVEL -> "§d${entry.score}§7Nv"
        }
        val scoreWidth = font.width(Component.literal(scoreStr))
        val maxNameWidth = (cardX + width - scoreWidth - 4) - nameX

        var displayName = entry.username
        if (font.width(displayName) > maxNameWidth) {
            displayName = font.plainSubstrByWidth(displayName, maxNameWidth - 3) + "…"
        }
        val prefix = if (isStickyLocal) "§7👉 §r" else ""
        guiGraphics.drawString(font, Component.literal(prefix + displayName), nameX, y + 1, nameColor, false)
        guiGraphics.drawString(font, Component.literal(scoreStr), cardX + width - scoreWidth - 3, y + 1, 0xFFFFFF, false)
    }

    /**
     * Rendu discret du fond : opacité douce (~40%), bordures fines translucides (~22%).
     */
    private fun drawCardBackground(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, headerHeight: Int) {
        val bgColor = 0x66080C14.toInt() // ~40% opacité (verre fumé très discret)
        val borderColor = 0x38344566.toInt() // ~22% bordure douce translucide
        val headerBg = 0x770E1522.toInt() // ~47% opacité pour l'en-tête

        guiGraphics.fill(x, y, x + width, y + height, bgColor)
        guiGraphics.fill(x, y, x + width, y + headerHeight, headerBg)

        guiGraphics.fill(x, y, x + width, y + 1, borderColor)
        guiGraphics.fill(x, y + height - 1, x + width, y + height, borderColor)
        guiGraphics.fill(x, y, x + 1, y + height, borderColor)
        guiGraphics.fill(x + width - 1, y, x + width, y + height, borderColor)
        guiGraphics.fill(x, y + headerHeight, x + width, y + headerHeight + 1, borderColor)
    }
}
