package fr.k0bus.cobblemon_jei.leaderboard

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min

/**
 * Écran complet de classement des joueurs accessible depuis le Pokédex.
 * Rendu net sans shader de flou (menu_blur), avec affichage détaillé des captures et entrées vues.
 */
class CobblemonLeaderboardScreen(
    private val parent: Screen? = null
) : Screen(Component.literal("Classement des Joueurs")) {

    private var selectedCategory: CobblemonLeaderboardCategory = CobblemonLeaderboardCategory.POKEDEX
    private var allEntries: List<LeaderboardPlayerEntry> = emptyList()
    private var filteredEntries: List<LeaderboardPlayerEntry> = emptyList()

    private lateinit var searchBox: EditBox
    private var scrollOffset: Double = 0.0

    private val categoryButtons = mutableListOf<Button>()

    override fun init() {
        val available = CobblemonScoreboardService.getAvailableCategories()
        if (available.isNotEmpty() && !available.contains(selectedCategory)) {
            selectedCategory = available.first()
        }

        refreshData()

        val topY = 24
        val tabCount = max(1, available.size)
        val maxAvailableTabSpace = width - 56 // Marge gauche 14 + bouton fermer droite 26 + espacement
        val tabWidth = min(88, max(60, (maxAvailableTabSpace - (tabCount - 1) * 4) / tabCount))
        val tabHeight = 18
        var currentX = 14

        categoryButtons.clear()
        for (cat in available) {
            val btn = Button.builder(Component.literal("${cat.iconEmoji} ${cat.shortName}")) {
                selectedCategory = cat
                scrollOffset = 0.0
                updateCategoryButtons()
                refreshData()
            }.bounds(currentX, topY, tabWidth, tabHeight).build()

            addRenderableWidget(btn)
            categoryButtons.add(btn)
            currentX += tabWidth + 4
        }
        updateCategoryButtons()

        // Bouton Retour / Fermer
        val closeBtn = Button.builder(Component.literal("✕")) {
            onClose()
        }.bounds(width - 26, topY, 18, tabHeight).build()
        addRenderableWidget(closeBtn)

        // Barre de recherche
        val searchY = 46
        val searchW = min(180, width - 28)
        searchBox = EditBox(font, 14, searchY, searchW, 16, Component.literal(""))
        searchBox.setHint(Component.literal("Rechercher un joueur..."))
        searchBox.setResponder { applySearchFilter() }
        addRenderableWidget(searchBox)
    }

    private fun updateCategoryButtons() {
        val available = CobblemonScoreboardService.getAvailableCategories()
        for ((index, btn) in categoryButtons.withIndex()) {
            if (index < available.size) {
                btn.active = (available[index] != selectedCategory)
            }
        }
    }

    private fun refreshData() {
        allEntries = CobblemonScoreboardService.getEntries(selectedCategory)
        applySearchFilter()
    }

    private fun applySearchFilter() {
        val query = if (::searchBox.isInitialized) searchBox.value.trim().lowercase() else ""
        filteredEntries = if (query.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { it.username.lowercase().contains(query) }
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Fond sombre moderne et net (AUCUN renderBackground pour éviter l'application du shader menu_blur)
        guiGraphics.fill(0, 0, width, height, 0xF00E121B.toInt())

        // Bande supérieure d'en-tête
        guiGraphics.fill(0, 0, width, 68, 0xFF141722.toInt())
        guiGraphics.fill(0, 67, width, 68, 0xFF2A3448.toInt())

        // Rendu des widgets enfants (boutons, champ de recherche)
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val available = CobblemonScoreboardService.getAvailableCategories()
        if (available.isEmpty()) {
            renderUnavailableNotice(guiGraphics)
            return
        }

        // En-tête : Titre principal
        guiGraphics.drawString(
            font,
            Component.literal("🏆 §lClassement des Joueurs§r §7— ${selectedCategory.displayName}§r"),
            14,
            10,
            0xFFFFFF,
            true
        )

        // Podium Top 3 (si au moins 1 joueur classé)
        val podiumHeight = 84
        val podiumY = 74
        if (allEntries.isNotEmpty()) {
            renderPodium(guiGraphics, podiumY, podiumHeight)
        }

        // Liste des joueurs (Scrollable) — toujours placée sous le podium dès qu'au moins 1 joueur est affiché
        val listY = if (allEntries.isNotEmpty()) podiumY + podiumHeight + 8 else podiumY
        val listBottomY = height - 28
        val listHeight = max(40, listBottomY - listY)
        renderPlayerList(guiGraphics, mouseX, mouseY, listY, listHeight)

        // Barre fixe d'information personnelle en bas
        renderLocalPlayerStickyBar(guiGraphics)
    }

    private fun renderUnavailableNotice(guiGraphics: GuiGraphics) {
        val centerX = width / 2
        val centerY = height / 2 - 10

        val boxW = 280
        val boxH = 70
        guiGraphics.fill(centerX - boxW / 2, centerY - boxH / 2, centerX + boxW / 2, centerY + boxH / 2, 0xD0121624.toInt())
        guiGraphics.renderOutline(centerX - boxW / 2, centerY - boxH / 2, boxW, boxH, 0xFF4A5568.toInt())

        val title = "§e⚠ Classement non disponible sur ce serveur§r"
        val desc = "§7Ce serveur n'a pas activé les objectifs de classement (CobblemonSB).§r"
        val hint = "§8Cette fonctionnalité est automatiquement masquée hors des serveurs compatibles.§r"

        guiGraphics.drawCenteredString(font, Component.literal(title), centerX, centerY - 20, 0xFFFFFF)
        guiGraphics.drawCenteredString(font, Component.literal(desc), centerX, centerY, 0xCCCCCC)
        guiGraphics.drawCenteredString(font, Component.literal(hint), centerX, centerY + 16, 0x888888)
    }

    private fun renderPodium(guiGraphics: GuiGraphics, y: Int, height: Int) {
        val centerX = width / 2
        val gap = 10
        val colWidth = min(110, (width - 40 - gap * 2) / 3)

        val top1 = allEntries.getOrNull(0)
        val top2 = allEntries.getOrNull(1)
        val top3 = allEntries.getOrNull(2)

        val top1X = centerX - colWidth / 2
        val top2X = top1X - gap - colWidth
        val top3X = top1X + colWidth + gap

        // #2 Argent (Gauche)
        if (top2 != null) {
            renderPodiumPillar(guiGraphics, top2X, y + 8, colWidth, height - 8, top2, 2, 0xFFC0C0C0.toInt(), "🥈")
        }

        // #1 Or (Centre - Plus haut)
        if (top1 != null) {
            renderPodiumPillar(guiGraphics, top1X, y, colWidth, height, top1, 1, 0xFFFFD700.toInt(), "🥇")
        }

        // #3 Bronze (Droite)
        if (top3 != null) {
            renderPodiumPillar(guiGraphics, top3X, y + 14, colWidth, height - 14, top3, 3, 0xFFCD7F32.toInt(), "🥉")
        }
    }

    private fun renderPodiumPillar(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        entry: LeaderboardPlayerEntry,
        rank: Int,
        accentColor: Int,
        medal: String
    ) {
        // Socle avec dégradé subtil
        guiGraphics.fill(x, y, x + w, y + h, 0xD0182030.toInt())
        guiGraphics.fill(x, y, x + w, y + 2, accentColor)
        guiGraphics.renderOutline(x, y, w, h, 0xFF2B374E.toInt())

        val headSize = if (rank == 1) 22 else 18
        val headX = x + (w - headSize) / 2
        val headY = y + 5
        val skin = entry.skin ?: CobblemonScoreboardService.resolvePlayerSkin(entry.username)
        try {
            PlayerFaceRenderer.draw(guiGraphics, skin.texture, headX, headY, headSize)
        } catch (_: Throwable) {}

        // Pseudo & Médaille
        val nameY = headY + headSize + 4
        var name = entry.username
        if (font.width(name) > w - 10) {
            name = font.plainSubstrByWidth(name, w - 16) + "…"
        }
        val nameColor = if (entry.isLocalPlayer) 0xFFE066 else 0xFFFFFF
        guiGraphics.drawCenteredString(font, Component.literal("$medal $name"), x + w / 2, nameY, nameColor)

        // Score formaté
        val scoreStr = "§l${entry.score}§r §7${selectedCategory.unit}§r"
        guiGraphics.drawCenteredString(font, Component.literal(scoreStr), x + w / 2, nameY + 11, accentColor)

        // Détail des captures et des entrées vues au Pokédex
        val detailStr = if (entry.caughtCount > 0 || entry.seenCount > 0) {
            "§a${entry.caughtCount} C. §8| §6${entry.seenCount} V."
        } else {
            "§8—"
        }
        guiGraphics.drawCenteredString(font, Component.literal(detailStr), x + w / 2, nameY + 22, 0xCCCCCC)
    }

    private fun renderPlayerList(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, y: Int, h: Int) {
        val listX = 14
        val listW = width - 28
        val rowHeight = 20

        guiGraphics.fill(listX, y, listX + listW, y + h, 0xC00F1420.toInt())
        guiGraphics.renderOutline(listX, y, listW, h, 0xFF242E42.toInt())

        // Header du tableau
        val headerH = 16
        guiGraphics.fill(listX, y, listX + listW, y + headerH, 0xD0172033.toInt())
        guiGraphics.drawString(font, Component.literal("§7Rang"), listX + 8, y + 4, 0xAAAAAA, false)
        guiGraphics.drawString(font, Component.literal("§7Joueur"), listX + 50, y + 4, 0xAAAAAA, false)
        guiGraphics.drawString(font, Component.literal("§7Capturés / Vus"), listX + listW - 195, y + 4, 0xAAAAAA, false)
        guiGraphics.drawString(font, Component.literal("§7Score"), listX + listW - 80, y + 4, 0xAAAAAA, false)

        val contentY = y + headerH
        val contentH = h - headerH

        val totalContentHeight = filteredEntries.size * rowHeight
        val maxScroll = max(0.0, (totalContentHeight - contentH).toDouble())
        scrollOffset = scrollOffset.coerceIn(0.0, maxScroll)

        // Découpage Scissor pour le scroll
        guiGraphics.enableScissor(listX, contentY, listX + listW, contentY + contentH)

        val startIndex = (scrollOffset / rowHeight).toInt()
        val endIndex = min(filteredEntries.size, startIndex + (contentH / rowHeight) + 2)

        for (i in startIndex until endIndex) {
            val entry = filteredEntries[i]
            val rowY = (contentY + (i * rowHeight) - scrollOffset).toInt()
            val isLocal = entry.isLocalPlayer

            // Fond alterné ou highlight local
            if (isLocal) {
                guiGraphics.fill(listX + 1, rowY, listX + listW - 1, rowY + rowHeight, 0x33FFD700)
            } else if (i % 2 == 1) {
                guiGraphics.fill(listX + 1, rowY, listX + listW - 1, rowY + rowHeight, 0x1AFFFFFF)
            }

            // Rang
            val rankText = when (entry.rank) {
                1 -> "§6#1"
                2 -> "§7#2"
                3 -> "§c#3"
                else -> "§8#${entry.rank}"
            }
            guiGraphics.drawString(font, Component.literal(rankText), listX + 8, rowY + 6, 0xFFFFFF, false)

            // Tête de skin (12x12)
            val headX = listX + 32
            val headY = rowY + 4
            val skin = entry.skin ?: CobblemonScoreboardService.resolvePlayerSkin(entry.username)
            try {
                PlayerFaceRenderer.draw(guiGraphics, skin.texture, headX, headY, 12)
            } catch (_: Throwable) {}

            // Pseudo
            val nameColor = if (isLocal) 0xFFE066 else 0xEEEEEE
            val displayName = if (isLocal) "§l${entry.username} (Vous)§r" else entry.username
            guiGraphics.drawString(font, Component.literal(displayName), listX + 50, rowY + 6, nameColor, false)

            // Colonne Capturés / Vus au Pokédex
            val detailText = if (entry.caughtCount > 0 || entry.seenCount > 0) {
                "§a${entry.caughtCount} C. §8| §6${entry.seenCount} V."
            } else {
                "§8—"
            }
            guiGraphics.drawString(font, Component.literal(detailText), listX + listW - 195, rowY + 6, 0xCCCCCC, false)

            // Score et unité
            val scoreText = "§f§l${entry.score}§r §7${selectedCategory.unit}§r"
            val scoreW = font.width(Component.literal(scoreText))
            guiGraphics.drawString(font, Component.literal(scoreText), listX + listW - scoreW - 10, rowY + 6, 0xFFFFFF, false)
        }

        guiGraphics.disableScissor()

        // Scrollbar
        if (maxScroll > 0) {
            val scrollbarW = 4
            val scrollbarX = listX + listW - scrollbarW - 1
            val thumbH = max(15, (contentH * contentH / totalContentHeight))
            val thumbY = (contentY + (scrollOffset / maxScroll * (contentH - thumbH))).toInt()

            guiGraphics.fill(scrollbarX, contentY, scrollbarX + scrollbarW, contentY + contentH, 0x33000000)
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFF4A5A78.toInt())
        }
    }

    private fun renderLocalPlayerStickyBar(guiGraphics: GuiGraphics) {
        val barY = height - 24
        val barH = 20
        guiGraphics.fill(0, barY, width, barY + barH, 0xEE111724.toInt())
        guiGraphics.fill(0, barY, width, barY + 1, 0xFF2A374D.toInt())

        val localEntry = CobblemonScoreboardService.getLocalPlayerEntry(selectedCategory)
        val localStats = try {
            fr.k0bus.cobblemon_jei.pokedex.PokemonTrackerService.getStats()
        } catch (_: Throwable) {
            null
        }

        val caught = localEntry?.caughtCount ?: (localStats?.caughtCount ?: 0)
        val seen = localEntry?.seenCount ?: (localStats?.encounteredCount ?: 0)
        val shinies = localEntry?.shiniesCount ?: 0
        val total = caught + seen

        val text = if (localEntry != null) {
            "Votre position : §6§l#${localEntry.rank}§r | Pokédex : §a§l$caught Capturés§r §8•§r §6§l$seen Vus§r §8(§e§l$total Total§r§8) •§r §d§l$shinies Shinies§r"
        } else {
            "Votre position : §7Non classé§r | Pokédex : §a§l$caught Capturés§r §8•§r §6§l$seen Vus§r §8(§e§l$total Total§r§8)"
        }

        guiGraphics.drawString(font, Component.literal(text), 14, barY + 6, 0xFFFFFF, true)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scrollOffset -= scrollY * 22.0
        return true
    }

    override fun onClose() {
        if (parent != null) {
            minecraft?.setScreen(parent)
        } else {
            super.onClose()
        }
    }
}
