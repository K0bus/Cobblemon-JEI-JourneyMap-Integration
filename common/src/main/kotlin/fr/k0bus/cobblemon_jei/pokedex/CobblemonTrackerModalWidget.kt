package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.application.goals.GoalService
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import fr.k0bus.cobblemon_jei.journeymap.PokemonRadarService
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.max

/**
 * Modale de configuration du Radar et du Tracker Cobblemon (Watchlist, cibles uniques/lignées, filtres IVs/HA/Taille).
 */
class CobblemonTrackerModalWidget(
    private val getCurrentTracked: () -> TrackedPokemon?,
    private val getSelectedForm: () -> FormData?,
    private val getAllTracked: () -> List<TrackedPokemon>,
    private val getItemStack: (Species, FormData?) -> ItemStack,
    private val onSelectTracked: (TrackedPokemon, FormData?) -> Unit,
    private val onOpenExpeditions: () -> Unit,
    private val onOpenTargetsDetail: (String, String, List<PokemonTarget>) -> Unit,
    private val onTrackerStateChanged: () -> Unit,
    private val onCloseCallback: () -> Unit
) {
    var isOpen: Boolean = false
        private set

    private var watchlistScrollOffset: Double = 0.0
    private var watchlistMaxScroll: Double = 0.0

    fun open() {
        isOpen = true
        watchlistScrollOffset = 0.0
    }

    fun close() {
        if (isOpen) {
            isOpen = false
            onCloseCallback()
            onTrackerStateChanged()
        }
    }

    fun getBounds(screenWidth: Int, screenHeight: Int): IntArray {
        val modalW = 350
        val modalH = 295
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
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF141722.toInt())
        guiGraphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX - 1, modalY + modalH, modalX + modalW + 1, modalY + modalH + 1, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX - 1, modalY, modalX, modalY + modalH, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX + modalW, modalY, modalX + modalW + 1, modalY + modalH, 0xFF353C4D.toInt())

        // Barre de titre de la modale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xFF1A1F2C.toInt())
        guiGraphics.fill(modalX, modalY + 21, modalX + modalW, modalY + 22, 0xFF2A3144.toInt())

        val title = "§6§lRADAR & TRACKER DE POKÉMON§r"
        guiGraphics.drawString(font, title, modalX + 8, modalY + 7, 0xFFFFFFFF.toInt(), false)

        // Bouton [🏕 Expéditions] dans la barre de titre
        val expModalBtnX = modalX + modalW - 116
        val expModalBtnY = modalY + 4
        val isExpHovered = mouseX >= expModalBtnX && mouseX <= expModalBtnX + 92 && mouseY >= expModalBtnY && mouseY <= expModalBtnY + 14
        val expBg = if (isExpHovered) 0xFF27AE60.toInt() else 0xFF1E3A2B.toInt()
        guiGraphics.fill(expModalBtnX, expModalBtnY, expModalBtnX + 92, expModalBtnY + 14, expBg)
        guiGraphics.fill(expModalBtnX, expModalBtnY, expModalBtnX + 92, expModalBtnY + 1, 0xFF2ECC71.toInt())
        guiGraphics.fill(expModalBtnX, expModalBtnY + 13, expModalBtnX + 92, expModalBtnY + 14, 0xFF2ECC71.toInt())
        guiGraphics.drawCenteredString(font, "🏕 Expéditions", expModalBtnX + 46, expModalBtnY + 3, 0xFFFFFFFF.toInt())

        // Bouton Fermer [✕] en haut à droite
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        val isCloseHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14
        val closeColor = if (isCloseHovered) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
        guiGraphics.drawString(font, "§l✕§r", closeBtnX + 3, closeBtnY + 3, closeColor, false)

        // 3. Bandeau d'état actuel
        val statusY = modalY + 26
        guiGraphics.fill(modalX + 8, statusY, modalX + modalW - 8, statusY + 22, 0xFF181C28.toInt())
        guiGraphics.fill(modalX + 8, statusY, modalX + modalW - 8, statusY + 1, 0xFF2A3144.toInt())
        guiGraphics.fill(modalX + 8, statusY + 21, modalX + modalW - 8, statusY + 22, 0xFF2A3144.toInt())

        val modeLabel = when (PokemonTrackingManager.currentMode) {
            TrackingMode.OFF -> "§7Inactif (Sentinelle Shiny active)"
            TrackingMode.ALL_UNCAUGHT -> "§aTous les Pokémon non capturés"
            TrackingMode.SINGLE -> "§bCible : §f${PokemonTrackingManager.targetSpecies?.translatedName?.string ?: "Inconnu"}"
            TrackingMode.EVOLUTION_LINE -> "§dFamille de §f${PokemonTrackingManager.evolutionBaseSpecies?.translatedName?.string ?: "Inconnu"} §7(${PokemonTrackingManager.evolutionFamily.size})"
            TrackingMode.CUSTOM_LIST -> "§eListe personnalisée §7(${PokemonTrackingManager.customWatchlist.size} Pokémon)"
            TrackingMode.GOALS -> "§6Objectifs d'expéd. §7(${GoalService.getActiveGoals().size} actifs)"
        }
        guiGraphics.drawString(font, "Mode actif : $modeLabel", modalX + 12, statusY + 4, 0xFFFFFFFF.toInt(), false)

        if (PokemonTrackingManager.currentMode == TrackingMode.GOALS) {
            val goalsBtnW = 68
            val goalsBtnH = 14
            val goalsBtnX = modalX + modalW - 8 - goalsBtnW - 4
            val goalsBtnY = statusY + 4
            val isGoalsHovered = mouseX >= goalsBtnX && mouseX <= goalsBtnX + goalsBtnW && mouseY >= goalsBtnY && mouseY <= goalsBtnY + goalsBtnH
            val goalsBg = if (isGoalsHovered) 0xFFD35400.toInt() else 0xFFE67E22.toInt()
            guiGraphics.fill(goalsBtnX, goalsBtnY, goalsBtnX + goalsBtnW, goalsBtnY + goalsBtnH, goalsBg)
            guiGraphics.drawCenteredString(font, "🔍 Cibles", goalsBtnX + goalsBtnW / 2, goalsBtnY + 3, 0xFFFFFFFF.toInt())
        }

        val detCount = PokemonRadarService.detectedCount
        val shinyCount = PokemonRadarService.shinyNearbyCount
        val radarInfo = when {
            shinyCount > 0 -> "§6★ $shinyCount Shiny§r | §a$detCount cible(s) à portée (160m)"
            detCount > 0 -> "§a$detCount cible(s) à portée du radar (160m)"
            else -> "§7Aucun spécimen traqué à portée"
        }
        guiGraphics.drawString(font, radarInfo, modalX + 12, statusY + 13, 0xFFBBBBBB.toInt(), false)

        val curSp = getCurrentTracked()?.species
        val curName = curSp?.translatedName?.string ?: "ce Pokémon"

        // 4. Choix de Tracking par défaut
        val btnRow1Y = modalY + 52
        val fullW = modalW - 16

        // Choix 1 : Tracker tous les non-capturés
        val isUncaughtActive = PokemonTrackingManager.currentMode == TrackingMode.ALL_UNCAUGHT
        val isB1Hovered = mouseX >= modalX + 8 && mouseX <= modalX + 8 + fullW && mouseY >= btnRow1Y && mouseY <= btnRow1Y + 16
        val b1Bg = when {
            isUncaughtActive -> if (isB1Hovered) 0xFF27AE60.toInt() else 0xFF2ECC71.toInt()
            isB1Hovered -> 0xFF2D3345.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val b1Border = if (isUncaughtActive) 0xFF2ECC71.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(modalX + 8, btnRow1Y, modalX + 8 + fullW, btnRow1Y + 16, b1Bg)
        guiGraphics.fill(modalX + 8, btnRow1Y, modalX + 8 + fullW, btnRow1Y + 1, b1Border)
        guiGraphics.fill(modalX + 8, btnRow1Y + 15, modalX + 8 + fullW, btnRow1Y + 16, b1Border)
        val b1Text = if (isUncaughtActive) "✔ Tracker TOUS les non-capturés (Actif)" else "🔍 Tracker TOUS les Pokémon non capturés"
        guiGraphics.drawCenteredString(font, b1Text, modalX + modalW / 2, btnRow1Y + 4, 0xFFFFFFFF.toInt())

        // Choix 2 & 3 : Tracker ce Pokémon OU sa lignée évolutive
        val btnRow2Y = modalY + 72
        val halfW = (fullW - 4) / 2

        // Choix 2 : Tracker ce Pokémon
        val isSingleActive = PokemonTrackingManager.currentMode == TrackingMode.SINGLE && PokemonTrackingManager.targetSpecies == curSp
        val isB2Hovered = mouseX >= modalX + 8 && mouseX <= modalX + 8 + halfW && mouseY >= btnRow2Y && mouseY <= btnRow2Y + 16
        val b2Bg = when {
            isSingleActive -> if (isB2Hovered) 0xFF2980B9.toInt() else 0xFF3498DB.toInt()
            isB2Hovered -> 0xFF2D3345.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val b2Border = if (isSingleActive) 0xFF54A0FF.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(modalX + 8, btnRow2Y, modalX + 8 + halfW, btnRow2Y + 16, b2Bg)
        guiGraphics.fill(modalX + 8, btnRow2Y, modalX + 8 + halfW, btnRow2Y + 1, b2Border)
        guiGraphics.fill(modalX + 8, btnRow2Y + 15, modalX + 8 + halfW, btnRow2Y + 16, b2Border)
        val b2Short = font.plainSubstrByWidth(curName, halfW - 24)
        val b2Text = if (isSingleActive) "✔ $b2Short" else "🎯 $b2Short"
        guiGraphics.drawCenteredString(font, b2Text, modalX + 8 + halfW / 2, btnRow2Y + 4, 0xFFFFFFFF.toInt())

        // Choix 3 : Tracker toute la lignée évolutive
        val isLineActive = PokemonTrackingManager.currentMode == TrackingMode.EVOLUTION_LINE && PokemonTrackingManager.evolutionBaseSpecies == curSp
        val b3X = modalX + 8 + halfW + 4
        val isB3Hovered = mouseX >= b3X && mouseX <= b3X + halfW && mouseY >= btnRow2Y && mouseY <= btnRow2Y + 16
        val b3Bg = when {
            isLineActive -> if (isB3Hovered) 0xFF8E44AD.toInt() else 0xFF9B59B6.toInt()
            isB3Hovered -> 0xFF2D3345.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val b3Border = if (isLineActive) 0xFFBE2EDD.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(b3X, btnRow2Y, b3X + halfW, btnRow2Y + 16, b3Bg)
        guiGraphics.fill(b3X, btnRow2Y, b3X + halfW, btnRow2Y + 1, b3Border)
        guiGraphics.fill(b3X, btnRow2Y + 15, b3X + halfW, btnRow2Y + 16, b3Border)
        val b3Text = if (isLineActive) "✔ Lignée ($b2Short)" else "🧬 Lignée ($b2Short)"
        guiGraphics.drawCenteredString(font, b3Text, b3X + halfW / 2, btnRow2Y + 4, 0xFFFFFFFF.toInt())

        // Choix 4 & 5 : Activer les objectifs du compagnon OU Arrêter le tracking
        val btnRow3Y = modalY + 92
        val activeGoalsCount = GoalService.getActiveGoals().size
        val isGoalsActive = PokemonTrackingManager.currentMode == TrackingMode.GOALS || PokemonTrackingManager.currentMode == TrackingMode.CUSTOM_LIST
        val isB4Hovered = mouseX >= modalX + 8 && mouseX <= modalX + 8 + halfW && mouseY >= btnRow3Y && mouseY <= btnRow3Y + 16
        val b4Bg = when {
            isGoalsActive -> if (isB4Hovered) 0xFFD35400.toInt() else 0xFFE67E22.toInt()
            isB4Hovered -> 0xFF2D3345.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val b4Border = if (isGoalsActive) 0xFFFFAA00.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(modalX + 8, btnRow3Y, modalX + 8 + halfW, btnRow3Y + 16, b4Bg)
        guiGraphics.fill(modalX + 8, btnRow3Y, modalX + 8 + halfW, btnRow3Y + 1, b4Border)
        guiGraphics.fill(modalX + 8, btnRow3Y + 15, modalX + 8 + halfW, btnRow3Y + 16, b4Border)
        val b4Text = if (isGoalsActive) "✔ Mes Objectifs ($activeGoalsCount)" else "🎯 Suivre Objectifs ($activeGoalsCount)"
        guiGraphics.drawCenteredString(font, b4Text, modalX + 8 + halfW / 2, btnRow3Y + 4, 0xFFFFFFFF.toInt())

        // Bouton Arrêter le tracking
        val isB5Hovered = mouseX >= b3X && mouseX <= b3X + halfW && mouseY >= btnRow3Y && mouseY <= btnRow3Y + 16
        val b5Bg = if (isB5Hovered) 0xFF992222.toInt() else 0xFF551111.toInt()
        guiGraphics.fill(b3X, btnRow3Y, b3X + halfW, btnRow3Y + 16, b5Bg)
        guiGraphics.fill(b3X, btnRow3Y, b3X + halfW, btnRow3Y + 1, 0xFFE74C3C.toInt())
        guiGraphics.fill(b3X, btnRow3Y + 15, b3X + halfW, btnRow3Y + 16, 0xFFE74C3C.toInt())
        guiGraphics.drawCenteredString(font, "✕ Arrêter le suivi", b3X + halfW / 2, btnRow3Y + 4, 0xFFFFFFFF.toInt())

        // 5. Section Filtres Avancés & Rendu In-World 3D
        val filterSectionY = modalY + 114
        guiGraphics.fill(modalX + 8, filterSectionY, modalX + modalW - 8, filterSectionY + 1, 0xFF282D3C.toInt())

        val filterTitle = "§6§lFILTRES AVANCÉS & EFFETS 3D§r"
        guiGraphics.drawString(font, filterTitle, modalX + 10, filterSectionY + 4, 0xFFFFFFFF.toInt(), false)

        val advFilter = PokemonTrackingManager.advancedFilter
        val row1Y = filterSectionY + 16
        val row2Y = filterSectionY + 33
        val colW = 108
        val col3W = 110

        // Bouton 1 : Talent Caché (HA)
        val isHaActive = advFilter.onlyHiddenAbility
        val isHaHovered = mouseX >= modalX + 8 && mouseX <= modalX + 8 + colW && mouseY >= row1Y && mouseY <= row1Y + 14
        val haBg = if (isHaActive) (if (isHaHovered) 0xFF8E44AD.toInt() else 0xFF6C3483.toInt()) else (if (isHaHovered) 0xFF2D3345.toInt() else 0xFF1D212E.toInt())
        val haBorder = if (isHaActive) 0xFFBE2EDD.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(modalX + 8, row1Y, modalX + 8 + colW, row1Y + 14, haBg)
        guiGraphics.fill(modalX + 8, row1Y, modalX + 8 + colW, row1Y + 1, haBorder)
        guiGraphics.fill(modalX + 8, row1Y + 13, modalX + 8 + colW, row1Y + 14, haBorder)
        val haText = if (isHaActive) "Talent Caché : OUI" else "Talent Caché : Tout"
        guiGraphics.drawCenteredString(font, haText, modalX + 8 + colW / 2, row1Y + 3, 0xFFFFFFFF.toInt())

        // Bouton 2 : IVs 31 Parfaits
        val ivText = when (advFilter.minPerfectIvs) {
            1 -> "31 IVs : ≥ 1"
            3 -> "31 IVs : ≥ 3"
            6 -> "31 IVs : 6 (Max)"
            else -> "31 IVs : Tout"
        }
        val isIvActive = advFilter.minPerfectIvs > 0
        val isIvHovered = mouseX >= modalX + 120 && mouseX <= modalX + 120 + colW && mouseY >= row1Y && mouseY <= row1Y + 14
        val ivBg = if (isIvActive) (if (isIvHovered) 0xFFD35400.toInt() else 0xFFBA4A00.toInt()) else (if (isIvHovered) 0xFF2D3345.toInt() else 0xFF1D212E.toInt())
        val ivBorder = if (isIvActive) 0xFFE67E22.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(modalX + 120, row1Y, modalX + 120 + colW, row1Y + 14, ivBg)
        guiGraphics.fill(modalX + 120, row1Y, modalX + 120 + colW, row1Y + 1, ivBorder)
        guiGraphics.fill(modalX + 120, row1Y + 13, modalX + 120 + colW, row1Y + 14, ivBorder)
        guiGraphics.drawCenteredString(font, ivText, modalX + 120 + colW / 2, row1Y + 3, 0xFFFFFFFF.toInt())

        // Bouton 3 : Filtre de Taille
        val sizeText = when (advFilter.sizeFilter) {
            SizeFilter.RARE_ONLY -> "Taille : Rares"
            SizeFilter.ONLY_MICROSCOPIC -> "Taille : Micro"
            SizeFilter.ONLY_GIGANTIC -> "Taille : Colossal"
            SizeFilter.ANY -> "Taille : Toute"
        }
        val isSizeActive = advFilter.sizeFilter != SizeFilter.ANY
        val isSizeHovered = mouseX >= modalX + 232 && mouseX <= modalX + 232 + col3W && mouseY >= row1Y && mouseY <= row1Y + 14
        val sizeBg = if (isSizeActive) (if (isSizeHovered) 0xFF2980B9.toInt() else 0xFF1F618D.toInt()) else (if (isSizeHovered) 0xFF2D3345.toInt() else 0xFF1D212E.toInt())
        val sizeBorder = if (isSizeActive) 0xFF3498DB.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(modalX + 232, row1Y, modalX + 232 + col3W, row1Y + 14, sizeBg)
        guiGraphics.fill(modalX + 232, row1Y, modalX + 232 + col3W, row1Y + 1, sizeBorder)
        guiGraphics.fill(modalX + 232, row1Y + 13, modalX + 232 + col3W, row1Y + 14, sizeBorder)
        guiGraphics.drawCenteredString(font, sizeText, modalX + 232 + col3W / 2, row1Y + 3, 0xFFFFFFFF.toInt())

        // Bouton 4 : Lueur 3D In-World
        val isGlowHovered = mouseX >= modalX + 8 && mouseX <= modalX + 8 + colW && mouseY >= row2Y && mouseY <= row2Y + 14
        val glowBg = if (advFilter.inWorldGlowing) (if (isGlowHovered) 0xFF27AE60.toInt() else 0xFF1E8449.toInt()) else (if (isGlowHovered) 0xFF2D3345.toInt() else 0xFF1D212E.toInt())
        val glowBorder = if (advFilter.inWorldGlowing) 0xFF2ECC71.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(modalX + 8, row2Y, modalX + 8 + colW, row2Y + 14, glowBg)
        guiGraphics.fill(modalX + 8, row2Y, modalX + 8 + colW, row2Y + 1, glowBorder)
        guiGraphics.fill(modalX + 8, row2Y + 13, modalX + 8 + colW, row2Y + 14, glowBorder)
        val glowText = if (advFilter.inWorldGlowing) "Lueur 3D : OUI" else "Lueur 3D : NON"
        guiGraphics.drawCenteredString(font, glowText, modalX + 8 + colW / 2, row2Y + 3, 0xFFFFFFFF.toInt())

        // Bouton 5 : Particules In-World
        val isPartHovered = mouseX >= modalX + 120 && mouseX <= modalX + 120 + colW && mouseY >= row2Y && mouseY <= row2Y + 14
        val partBg = if (advFilter.inWorldParticles) (if (isPartHovered) 0xFF27AE60.toInt() else 0xFF1E8449.toInt()) else (if (isPartHovered) 0xFF2D3345.toInt() else 0xFF1D212E.toInt())
        val partBorder = if (advFilter.inWorldParticles) 0xFF2ECC71.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(modalX + 120, row2Y, modalX + 120 + colW, row2Y + 14, partBg)
        guiGraphics.fill(modalX + 120, row2Y, modalX + 120 + colW, row2Y + 1, partBorder)
        guiGraphics.fill(modalX + 120, row2Y + 13, modalX + 120 + colW, row2Y + 14, partBorder)
        val partText = if (advFilter.inWorldParticles) "Particules : OUI" else "Particules : NON"
        guiGraphics.drawCenteredString(font, partText, modalX + 120 + colW / 2, row2Y + 3, 0xFFFFFFFF.toInt())

        // Bouton 6 : Réinitialiser filtres
        val hasFilters = advFilter.hasAnyFilter()
        val isResetHovered = mouseX >= modalX + 232 && mouseX <= modalX + 232 + col3W && mouseY >= row2Y && mouseY <= row2Y + 14
        val resetBg = if (hasFilters) (if (isResetHovered) 0xFF992222.toInt() else 0xFF551111.toInt()) else 0xFF181C26.toInt()
        val resetBorder = if (hasFilters) 0xFFE74C3C.toInt() else 0xFF282D3C.toInt()
        guiGraphics.fill(modalX + 232, row2Y, modalX + 232 + col3W, row2Y + 14, resetBg)
        guiGraphics.fill(modalX + 232, row2Y, modalX + 232 + col3W, row2Y + 1, resetBorder)
        guiGraphics.fill(modalX + 232, row2Y + 13, modalX + 232 + col3W, row2Y + 14, resetBorder)
        val resetColor = if (hasFilters) 0xFFFFAAAA.toInt() else 0xFF666666.toInt()
        guiGraphics.drawCenteredString(font, "Réinitialiser", modalX + 232 + col3W / 2, row2Y + 3, resetColor)

        // 6. Section Liste personnalisée (Watchlist)
        val listSectionY = modalY + 166
        guiGraphics.fill(modalX + 8, listSectionY, modalX + modalW - 8, listSectionY + 1, 0xFF282D3C.toInt())

        val listTitle = "§e§lMA LISTE DE TRACKING§r §7(${PokemonTrackingManager.customWatchlist.size})§r"
        guiGraphics.drawString(font, listTitle, modalX + 10, listSectionY + 5, 0xFFFFFFFF.toInt(), false)

        // Bouton [+ Ajouter / - Retirer]
        val isInList = curSp != null && PokemonTrackingManager.isInWatchlist(curSp)
        val toggleBtnW = 100
        val toggleBtnX = modalX + modalW - 10 - toggleBtnW
        val toggleBtnY = listSectionY + 3
        val isToggleHovered = mouseX >= toggleBtnX && mouseX <= toggleBtnX + toggleBtnW && mouseY >= toggleBtnY && mouseY <= toggleBtnY + 13
        val toggleBg = if (isInList) {
            if (isToggleHovered) 0xFFC0392B.toInt() else 0xFF7F1D1D.toInt()
        } else {
            if (isToggleHovered) 0xFF27AE60.toInt() else 0xFF1E3A2B.toInt()
        }
        guiGraphics.fill(toggleBtnX, toggleBtnY, toggleBtnX + toggleBtnW, toggleBtnY + 13, toggleBg)
        val toggleText = if (isInList) "− Retirer $b2Short" else "+ Ajouter $b2Short"
        guiGraphics.drawCenteredString(font, toggleText, toggleBtnX + toggleBtnW / 2, toggleBtnY + 3, 0xFFFFFFFF.toInt())

        // Bouton Vider la liste
        if (PokemonTrackingManager.customWatchlist.isNotEmpty()) {
            val clearBtnX = toggleBtnX - 44
            val isClearHovered = mouseX >= clearBtnX && mouseX <= clearBtnX + 40 && mouseY >= toggleBtnY && mouseY <= toggleBtnY + 13
            val clearBg = if (isClearHovered) 0xFF882222.toInt() else 0xFF441818.toInt()
            guiGraphics.fill(clearBtnX, toggleBtnY, clearBtnX + 40, toggleBtnY + 13, clearBg)
            guiGraphics.drawCenteredString(font, "Vider", clearBtnX + 20, toggleBtnY + 3, 0xFFFFAAAA.toInt())
        }

        // Cadre de liste scrollable
        val boxX = modalX + 8
        val boxY = listSectionY + 19
        val boxW = modalW - 16
        val boxH = modalH - (boxY - modalY) - 8

        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF0D0F16.toInt())
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF1F2432.toInt())
        guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF1F2432.toInt())
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, 0xFF1F2432.toInt())
        guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFF1F2432.toInt())

        val list = PokemonTrackingManager.customWatchlist.toList()
        if (list.isEmpty()) {
            val emptyMsg1 = "§7Votre liste de tracking est actuellement vide."
            val emptyMsg2 = "§8Cliquez sur \"+ Ajouter\" ou sur l'étoile ⭐ pour y inscrire un Pokémon."
            guiGraphics.drawCenteredString(font, emptyMsg1, boxX + boxW / 2, boxY + boxH / 2 - 8, 0xFF888888.toInt())
            guiGraphics.drawCenteredString(font, emptyMsg2, boxX + boxW / 2, boxY + boxH / 2 + 3, 0xFF666666.toInt())
        } else {
            val rowH = 19
            val totalContentH = list.size * rowH
            watchlistMaxScroll = max(0.0, (totalContentH - boxH).toDouble())

            guiGraphics.enableScissor(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + boxH - 1)
            val startY = boxY + 2 - watchlistScrollOffset.toInt()

            for ((idx, spInList) in list.withIndex()) {
                val rowY = startY + idx * rowH
                if (rowY + rowH < boxY || rowY > boxY + boxH) continue

                val isRowHovered = mouseX >= boxX + 2 && mouseX <= boxX + boxW - 20 && mouseY >= rowY && mouseY <= rowY + rowH - 1
                val rowBg = if (isRowHovered) 0xFF1B202D.toInt() else if (idx % 2 == 0) 0xFF131722.toInt() else 0xFF0F121C.toInt()
                guiGraphics.fill(boxX + 2, rowY, boxX + boxW - 2, rowY + rowH - 1, rowBg)

                // Rendu item 3D
                val itemStack = getItemStack(spInList, spInList.standardForm)
                if (!itemStack.isEmpty) {
                    guiGraphics.renderItem(itemStack, boxX + 4, rowY + 1)
                }

                val dexNum = String.format("#%04d", spInList.nationalPokedexNumber)
                guiGraphics.drawString(font, "§6$dexNum§r §f${spInList.translatedName.string}§r", boxX + 24, rowY + 5, 0xFFFFFFFF.toInt(), false)

                // Bouton de suppression [✕]
                val delX = boxX + boxW - 16
                val isDelHovered = mouseX >= delX - 2 && mouseX <= delX + 10 && mouseY >= rowY + 2 && mouseY <= rowY + 14
                val delColor = if (isDelHovered) 0xFFFF4444.toInt() else 0xFF777777.toInt()
                guiGraphics.drawString(font, "✕", delX, rowY + 5, delColor, false)
            }

            guiGraphics.disableScissor()

            // Scrollbar si la liste dépasse
            if (watchlistMaxScroll > 0) {
                val sbX = boxX + boxW - 4
                val sbH = max(10, (boxH * boxH / totalContentH))
                val sbY = boxY + ((boxH - sbH) * (watchlistScrollOffset / watchlistMaxScroll)).toInt()
                guiGraphics.fill(sbX, boxY, sbX + 2, boxY + boxH, 0xFF181C28.toInt())
                guiGraphics.fill(sbX, sbY, sbX + 2, sbY + sbH, 0xFF54A0FF.toInt())
            }
        }

        pose.popPose()
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

        // Bouton [🏕 Expéditions]
        val expModalBtnX = modalX + modalW - 116
        val expModalBtnY = modalY + 4
        if (mouseX >= expModalBtnX && mouseX <= expModalBtnX + 92 && mouseY >= expModalBtnY && mouseY <= expModalBtnY + 14) {
            close()
            onOpenExpeditions()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }

        // Bouton [🔍 Cibles] si en mode GOALS
        if (PokemonTrackingManager.currentMode == TrackingMode.GOALS) {
            val statusY = modalY + 26
            val goalsBtnW = 68
            val goalsBtnH = 14
            val goalsBtnX = modalX + modalW - 8 - goalsBtnW - 4
            val goalsBtnY = statusY + 4
            if (mouseX >= goalsBtnX && mouseX <= goalsBtnX + goalsBtnW && mouseY >= goalsBtnY && mouseY <= goalsBtnY + goalsBtnH) {
                val activeGoals = GoalService.getActiveGoals()
                onOpenTargetsDetail("Objectifs d'Expédition", "${activeGoals.size} Pokémon cibles suivis", activeGoals.map { it.target })
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                return true
            }
        }

        // Clic en dehors de la fenêtre modale ferme la modale
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            close()
            return true
        }

        val curSp = getCurrentTracked()?.species
        val fullW = modalW - 16
        val halfW = (fullW - 4) / 2

        // 2. Bouton 1 : Tracker TOUS les non-capturés
        val btnRow1Y = modalY + 52
        if (mouseX >= modalX + 8 && mouseX <= modalX + 8 + fullW && mouseY >= btnRow1Y && mouseY <= btnRow1Y + 16) {
            if (PokemonTrackingManager.currentMode == TrackingMode.ALL_UNCAUGHT) {
                PokemonTrackingManager.stopTracking()
            } else {
                PokemonTrackingManager.trackAllUncaught()
            }
            onTrackerStateChanged()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }

        // 3. Bouton 2 : Tracker ce Pokémon
        val btnRow2Y = modalY + 72
        if (mouseX >= modalX + 8 && mouseX <= modalX + 8 + halfW && mouseY >= btnRow2Y && mouseY <= btnRow2Y + 16) {
            if (curSp != null) {
                if (PokemonTrackingManager.currentMode == TrackingMode.SINGLE && PokemonTrackingManager.targetSpecies == curSp) {
                    PokemonTrackingManager.stopTracking()
                } else {
                    PokemonTrackingManager.trackSingle(curSp, getSelectedForm())
                }
                onTrackerStateChanged()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            }
            return true
        }

        // 4. Bouton 3 : Tracker la lignée évolutive
        val b3X = modalX + 8 + halfW + 4
        if (mouseX >= b3X && mouseX <= b3X + halfW && mouseY >= btnRow2Y && mouseY <= btnRow2Y + 16) {
            if (curSp != null) {
                if (PokemonTrackingManager.currentMode == TrackingMode.EVOLUTION_LINE && PokemonTrackingManager.evolutionBaseSpecies == curSp) {
                    PokemonTrackingManager.stopTracking()
                } else {
                    PokemonTrackingManager.trackEvolutionLine(curSp)
                }
                onTrackerStateChanged()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            }
            return true
        }

        // 5. Bouton 4 : Suivre mes objectifs (ou liste personnalisée)
        val btnRow3Y = modalY + 92
        if (mouseX >= modalX + 8 && mouseX <= modalX + 8 + halfW && mouseY >= btnRow3Y && mouseY <= btnRow3Y + 16) {
            if (PokemonTrackingManager.currentMode == TrackingMode.GOALS || PokemonTrackingManager.currentMode == TrackingMode.CUSTOM_LIST) {
                PokemonTrackingManager.stopTracking()
            } else {
                val hasGoals = GoalService.getActiveGoals().isNotEmpty()
                if (hasGoals) {
                    PokemonTrackingManager.trackGoals()
                } else {
                    PokemonTrackingManager.trackCustomList()
                }
            }
            onTrackerStateChanged()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }

        // 6. Bouton 5 : Arrêter le suivi
        if (mouseX >= b3X && mouseX <= b3X + halfW && mouseY >= btnRow3Y && mouseY <= btnRow3Y + 16) {
            PokemonTrackingManager.stopTracking()
            onTrackerStateChanged()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8f))
            return true
        }

        // 5. Section Filtres Avancés & Rendu In-World
        val filterSectionY = modalY + 114
        val advFilter = PokemonTrackingManager.advancedFilter
        val row1Y = filterSectionY + 16
        val row2Y = filterSectionY + 33
        val colW = 108
        val col3W = 110

        // Clic Bouton 1 : Talent Caché (HA)
        if (mouseX >= modalX + 8 && mouseX <= modalX + 8 + colW && mouseY >= row1Y && mouseY <= row1Y + 14) {
            advFilter.onlyHiddenAbility = !advFilter.onlyHiddenAbility
            PokemonTrackingManager.saveConfig()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
            return true
        }

        // Clic Bouton 2 : IVs 31 Parfaits (Cycle: 0 -> 1 -> 3 -> 6 -> 0)
        if (mouseX >= modalX + 120 && mouseX <= modalX + 120 + colW && mouseY >= row1Y && mouseY <= row1Y + 14) {
            advFilter.minPerfectIvs = when (advFilter.minPerfectIvs) {
                0 -> 1
                1 -> 3
                3 -> 6
                else -> 0
            }
            PokemonTrackingManager.saveConfig()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
            return true
        }

        // Clic Bouton 3 : Filtre de Taille (Cycle: ANY -> RARE_ONLY -> ONLY_MICROSCOPIC -> ONLY_GIGANTIC -> ANY)
        if (mouseX >= modalX + 232 && mouseX <= modalX + 232 + col3W && mouseY >= row1Y && mouseY <= row1Y + 14) {
            advFilter.sizeFilter = advFilter.sizeFilter.next()
            PokemonTrackingManager.saveConfig()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
            return true
        }

        // Clic Bouton 4 : Lueur 3D In-World
        if (mouseX >= modalX + 8 && mouseX <= modalX + 8 + colW && mouseY >= row2Y && mouseY <= row2Y + 14) {
            advFilter.inWorldGlowing = !advFilter.inWorldGlowing
            PokemonTrackingManager.saveConfig()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }

        // Clic Bouton 5 : Particules In-World
        if (mouseX >= modalX + 120 && mouseX <= modalX + 120 + colW && mouseY >= row2Y && mouseY <= row2Y + 14) {
            advFilter.inWorldParticles = !advFilter.inWorldParticles
            PokemonTrackingManager.saveConfig()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }

        // Clic Bouton 6 : Réinitialiser Filtres
        if (mouseX >= modalX + 232 && mouseX <= modalX + 232 + col3W && mouseY >= row2Y && mouseY <= row2Y + 14) {
            if (advFilter.hasAnyFilter()) {
                advFilter.resetFilters()
                PokemonTrackingManager.saveConfig()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
            }
            return true
        }

        // 6. Bouton [+ Ajouter / - Retirer] dans la liste
        val listSectionY = modalY + 166
        val toggleBtnW = 100
        val toggleBtnX = modalX + modalW - 10 - toggleBtnW
        val toggleBtnY = listSectionY + 3
        if (mouseX >= toggleBtnX && mouseX <= toggleBtnX + toggleBtnW && mouseY >= toggleBtnY && mouseY <= toggleBtnY + 13) {
            if (curSp != null) {
                PokemonTrackingManager.toggleWatchlist(curSp)
                onTrackerStateChanged()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
            }
            return true
        }

        // 7. Bouton [Vider] la liste
        if (PokemonTrackingManager.customWatchlist.isNotEmpty()) {
            val clearBtnX = toggleBtnX - 44
            if (mouseX >= clearBtnX && mouseX <= clearBtnX + 40 && mouseY >= toggleBtnY && mouseY <= toggleBtnY + 13) {
                PokemonTrackingManager.clearWatchlist()
                onTrackerStateChanged()
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8f))
                return true
            }
        }

        // 8. Clics dans les éléments de la liste scrollable
        val boxX = modalX + 8
        val boxY = listSectionY + 19
        val boxW = modalW - 16
        val boxH = modalH - (boxY - modalY) - 8

        if (mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH) {
            val list = PokemonTrackingManager.customWatchlist.toList()
            val rowH = 19
            val startY = boxY + 2 - watchlistScrollOffset.toInt()

            for ((idx, spInList) in list.withIndex()) {
                val rowY = startY + idx * rowH
                if (mouseY >= rowY && mouseY <= rowY + rowH - 1) {
                    val delX = boxX + boxW - 16
                    if (mouseX >= delX - 4 && mouseX <= delX + 12) {
                        // Clic sur [✕]
                        PokemonTrackingManager.removeFromWatchlist(spInList)
                        onTrackerStateChanged()
                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
                    } else {
                        // Clic sur la ligne -> sélectionne le Pokémon dans l'écran Pokédex
                        val tracked = getAllTracked().find { PokemonTrackingManager.isSameSpecies(it.species, spInList) }
                        if (tracked != null) {
                            onSelectTracked(tracked, spInList.standardForm)
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                        }
                    }
                    return true
                }
            }
        }

        return true
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, screenWidth: Int, screenHeight: Int): Boolean =
        mouseClicked(mouseX, mouseY, screenWidth, screenHeight)

    fun mouseScrolled(scrollY: Double): Boolean {
        if (!isOpen) return false
        watchlistScrollOffset = (watchlistScrollOffset - scrollY * 20.0).coerceIn(0.0, watchlistMaxScroll)
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
}
