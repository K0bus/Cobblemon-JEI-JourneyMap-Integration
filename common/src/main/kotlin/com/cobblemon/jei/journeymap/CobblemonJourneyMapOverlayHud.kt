package com.cobblemon.jei.journeymap

import com.cobblemon.jei.data.PokemonSpawnDataLoader
import com.cobblemon.jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.LoggerFactory

/**
 * HUD d'affichage flottant placé sous le bouton de menu (Pokéball) de JourneyMap.
 * Présente le Pokémon actuellement en cours de recherche, ses caractéristiques clés
 * (Pokédex #, nom, types, rareté) et une barre de progression en temps réel du scan global.
 */
object CobblemonJourneyMapOverlayHud {

    private val logger = LoggerFactory.getLogger("CobblemonJM/OverlayHud")
    private var isRegistered = false

    private data class Bounds(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun contains(mx: Double, my: Double): Boolean =
            mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    private var currentBounds: Bounds? = null
    private var closeBtnBounds: Bounds? = null

    private val itemStackCache = mutableMapOf<Pair<Species, FormData?>, ItemStack>()

    /**
     * Enregistre les écouteurs d'événements ScreenEvent auprès du bus NeoForge.
     */
    fun register() {
        if (isRegistered) return
        isRegistered = true
        try {
            NeoForge.EVENT_BUS.addListener(::onScreenRenderPost)
            NeoForge.EVENT_BUS.addListener(::onMouseButtonPressedPre)
            logger.info("Cobblemon JourneyMap Overlay HUD enregistré avec succès.")
        } catch (e: Throwable) {
            logger.error("Cobblemon JM: Impossible d'enregistrer l'overlay HUD : {}", e.message)
        }
    }

    private fun getItemStack(species: Species, form: FormData?): ItemStack {
        return itemStackCache.computeIfAbsent(Pair(species, form)) {
            try {
                PokemonSpeciesIngredient(species, form ?: species.standardForm).itemStack
            } catch (_: Throwable) {
                ItemStack.EMPTY
            }
        }
    }

    /**
     * Rendu post-screen du widget d'information sous le bouton de menu Pokéball.
     */
    fun onScreenRenderPost(event: ScreenEvent.Render.Post) {
        val screen = event.screen ?: return

        // Ne pas afficher si un autre écran (ex: recherche Cobblemon) est ouvert
        if (screen is CobblemonJourneyMapSearchScreen) {
            currentBounds = null
            closeBtnBounds = null
            return
        }

        if (!CobblemonOverlayManager.isOverlayActive) {
            currentBounds = null
            closeBtnBounds = null
            return
        }

        val species = CobblemonOverlayManager.activeSpecies ?: run {
            currentBounds = null
            closeBtnBounds = null
            return
        }

        // Vérifier si l'écran courant est bien la carte plein écran de JourneyMap
        val isFullscreen = (CobblemonJourneyMapPlugin.currentFullscreen?.screen == screen) ||
                screen.javaClass.name.contains("Fullscreen", ignoreCase = true)
        if (!isFullscreen) {
            currentBounds = null
            closeBtnBounds = null
            return
        }

        val button = CobblemonJourneyMapPlugin.currentThemeButton?.button ?: run {
            currentBounds = null
            closeBtnBounds = null
            return
        }

        if (!button.visible) {
            currentBounds = null
            closeBtnBounds = null
            return
        }

        renderHudCard(event.guiGraphics, screen, button.x, button.y, button.width, button.height, event.mouseX, event.mouseY, species)
    }

    private fun renderHudCard(
        guiGraphics: GuiGraphics,
        screen: Screen,
        btnX: Int,
        btnY: Int,
        btnW: Int,
        btnH: Int,
        mouseX: Int,
        mouseY: Int,
        species: Species
    ) {
        val mc = Minecraft.getInstance()
        val font = mc.font

        val cardWidth = 180
        val cardHeight = 82

        // Positionnement horizontal avec contrainte d'écran
        var cardX = btnX
        if (cardX + cardWidth > screen.width - 4) {
            cardX = screen.width - cardWidth - 4
        }
        if (cardX < 4) cardX = 4

        // Positionnement vertical : sous le bouton par défaut, au-dessus si proche du bord bas
        var cardY = btnY + btnH + 4
        if (cardY + cardHeight > screen.height - 4) {
            cardY = btnY - cardHeight - 4
        }
        if (cardY < 4) cardY = 4

        val bounds = Bounds(cardX, cardY, cardWidth, cardHeight)
        currentBounds = bounds
        val closeBounds = Bounds(cardX + cardWidth - 14, cardY + 5, 10, 10)
        closeBtnBounds = closeBounds

        val isHovered = bounds.contains(mouseX.toDouble(), mouseY.toDouble())
        val isCloseHovered = closeBounds.contains(mouseX.toDouble(), mouseY.toDouble())

        // 1. Fond semi-transparent midnight slate
        guiGraphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xF210131B.toInt())

        // 2. Bordure extérieure (subtilement illuminée au survol)
        val borderColor = if (isHovered) 0xFF4A5578.toInt() else 0xFF2A2E3D.toInt()
        guiGraphics.fill(cardX - 1, cardY - 1, cardX + cardWidth + 1, cardY, borderColor)
        guiGraphics.fill(cardX - 1, cardY + cardHeight, cardX + cardWidth + 1, cardY + cardHeight + 1, borderColor)
        guiGraphics.fill(cardX - 1, cardY, cardX, cardY + cardHeight, borderColor)
        guiGraphics.fill(cardX + cardWidth, cardY, cardX + cardWidth + 1, cardY + cardHeight, borderColor)

        // 3. Barre d'accentuation supérieure colorée selon le type primaire
        val primaryColor = CobblemonOverlayManager.getElementColor(species.primaryType.name)
        guiGraphics.fill(cardX, cardY, cardX + cardWidth, cardY + 2, primaryColor)

        // 4. Bouton de fermeture rapide '✕'
        val closeColor = if (isCloseHovered) 0xFFFF4757.toInt() else 0xFF888E9E.toInt()
        guiGraphics.drawString(font, "✕", closeBounds.x, closeBounds.y, closeColor, false)

        // 5. Cadre et modèle 3D du Pokémon
        val iconBoxX = cardX + 6
        val iconBoxY = cardY + 7
        val iconBoxSize = 35
        guiGraphics.fill(iconBoxX, iconBoxY, iconBoxX + iconBoxSize, iconBoxY + iconBoxSize, 0xFF141722.toInt())
        guiGraphics.fill(iconBoxX - 1, iconBoxY - 1, iconBoxX + iconBoxSize + 1, iconBoxY, 0xFF282D3D.toInt())
        guiGraphics.fill(iconBoxX - 1, iconBoxY + iconBoxSize, iconBoxX + iconBoxSize + 1, iconBoxY + iconBoxSize + 1, 0xFF282D3D.toInt())
        guiGraphics.fill(iconBoxX - 1, iconBoxY, iconBoxX, iconBoxY + iconBoxSize, 0xFF282D3D.toInt())
        guiGraphics.fill(iconBoxX + iconBoxSize, iconBoxY, iconBoxX + iconBoxSize + 1, iconBoxY + iconBoxSize, 0xFF282D3D.toInt())

        val stack = getItemStack(species, CobblemonOverlayManager.activeForm)
        if (!stack.isEmpty) {
            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate((iconBoxX + 1).toFloat(), (iconBoxY + 1).toFloat(), 0f)
            guiGraphics.pose().scale(1.9f, 1.9f, 1.0f)
            guiGraphics.renderItem(stack, 0, 0)
            guiGraphics.pose().popPose()
        }

        // 6. Informations textuelles à droite du modèle
        val textX = cardX + 46
        val dexStr = String.format("#%04d", species.nationalPokedexNumber)
        val nameStr = species.translatedName.string

        // Ligne 1 : #0025 + Nom
        guiGraphics.drawString(font, dexStr, textX, cardY + 7, 0xFFFFD32A.toInt(), false)
        val maxNameW = cardWidth - 46 - 18
        val displayName = if (font.width(nameStr) > maxNameW) font.plainSubstrByWidth(nameStr, maxNameW - 6) + "…" else nameStr
        guiGraphics.drawString(font, displayName, textX, cardY + 17, 0xFFFFFFFF.toInt(), false)

        // Ligne 2 : Badges élémentaires
        var badgeX = textX
        val type1 = species.primaryType.displayName.string.uppercase()
        val c1 = CobblemonOverlayManager.getElementColor(species.primaryType.name)
        val w1 = font.width(type1) + 4
        guiGraphics.fill(badgeX, cardY + 28, badgeX + w1, cardY + 36, c1)
        guiGraphics.drawString(font, type1, badgeX + 2, cardY + 28, 0xFFFFFFFF.toInt(), false)
        badgeX += w1 + 3

        species.secondaryType?.let { sec ->
            val type2 = sec.displayName.string.uppercase()
            val c2 = CobblemonOverlayManager.getElementColor(sec.name)
            val w2 = font.width(type2) + 4
            if (badgeX + w2 <= cardX + cardWidth - 8) {
                guiGraphics.fill(badgeX, cardY + 28, badgeX + w2, cardY + 36, c2)
                guiGraphics.drawString(font, type2, badgeX + 2, cardY + 28, 0xFFFFFFFF.toInt(), false)
            }
        }

        // Ligne 3 : Rareté estimée
        val naturalRecipes = PokemonSpawnDataLoader.getSpawnRecipesForSpecies(species).filter { it.isNaturalSpawn }
        val bestRecipe = naturalRecipes.maxByOrNull { it.weight } ?: naturalRecipes.firstOrNull()
        val rarity = bestRecipe?.let { SpawnRarity.fromBucketOrWeight(it.bucket, it.weight) } ?: SpawnRarity.COMMON
        val rarityColor = (0xFF000000 or RarityColorPalette.createShapeProperties(rarity).strokeColor.toLong()).toInt()
        val rarityText = "Rareté : ${rarity.displayName}"
        guiGraphics.drawString(font, rarityText, textX, cardY + 37, rarityColor, false)

        // 7. Ligne de séparation
        guiGraphics.fill(cardX + 6, cardY + 47, cardX + cardWidth - 6, cardY + 48, 0xFF25293A.toInt())

        // 8. État du scan et barre de chargement
        val progress = CobblemonOverlayManager.scanProgress
        val barX = cardX + 6
        val barY = cardY + 62
        val barW = cardWidth - 12
        val barH = 4

        if (progress.isScanning) {
            val pct = progress.progressPercent
            val scanText = "§b⚡ Scan : $pct% §7(${progress.completedRegions}/${progress.totalRegions} rég.)"
            guiGraphics.drawString(font, scanText, cardX + 6, cardY + 51, 0xFFFFFFFF.toInt(), false)

            // Piste de la barre de progression
            guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF141724.toInt())
            guiGraphics.fill(barX - 1, barY - 1, barX + barW + 1, barY, 0xFF2A2E40.toInt())
            guiGraphics.fill(barX - 1, barY + barH, barX + barW + 1, barY + barH + 1, 0xFF2A2E40.toInt())
            guiGraphics.fill(barX - 1, barY, barX, barY + barH, 0xFF2A2E40.toInt())
            guiGraphics.fill(barX + barW, barY, barX + barW + 1, barY + barH, 0xFF2A2E40.toInt())

            // Remplissage animé (cyan vif)
            val fillW = (barW * pct / 100).coerceIn(0, barW)
            if (fillW > 0) {
                guiGraphics.fill(barX, barY, barX + fillW, barY + barH, 0xFF00D2D3.toInt())
            }

            // Sous-texte
            val radarSummary = PokemonRadarService.getStatusSummary(species.translatedName.string)
            val subText = "§7${progress.eligibleChunks} zones • $radarSummary"
            guiGraphics.drawString(font, subText, cardX + 6, cardY + 69, 0xFFAAAAAA.toInt(), false)
        } else {
            val totalEligible = progress.eligibleChunks.coerceAtLeast(SpawnOverlayManager.activeRarityCounts.values.sum())
            val radarCount = PokemonRadarService.detectedCount
            val isShiny = PokemonRadarService.hasShinyNearby

            if (totalEligible > 0) {
                val foundText = if (isShiny) {
                    "§6★ SHINY détecté ! §7($totalEligible zones)"
                } else if (radarCount > 0) {
                    "§b⚡ $radarCount cible(s) à portée §7($totalEligible zones)"
                } else {
                    "§a✔ $totalEligible zones éligibles"
                }
                guiGraphics.drawString(font, foundText, cardX + 6, cardY + 51, if (isShiny) 0xFFFFD700.toInt() else 0xFF2ED573.toInt(), false)

                // Barre de statut
                val barColor = if (isShiny) 0xFFFFD700.toInt() else if (radarCount > 0) 0xFF00D2D3.toInt() else 0xFF2ED573.toInt()
                guiGraphics.fill(barX, barY + 1, barX + barW, barY + 3, barColor)

                val radarStatus = PokemonRadarService.getStatusSummary(species.translatedName.string)
                val subText = if (isHovered) "§e▶ Cliquer pour changer" else "§7Radar : $radarStatus"
                guiGraphics.drawString(font, subText, cardX + 6, cardY + 69, 0xFFAAAAAA.toInt(), false)
            } else {
                val noneText = "§c✗ Aucun spawn trouvé"
                guiGraphics.drawString(font, noneText, cardX + 6, cardY + 51, 0xFFFF4757.toInt(), false)

                // Barre rouge fine
                guiGraphics.fill(barX, barY + 1, barX + barW, barY + 3, 0xFFFF4757.toInt())

                guiGraphics.drawString(font, "§8Dimension incompatible", cardX + 6, cardY + 69, 0xFF888888.toInt(), false)
            }
        }
    }

    /**
     * Intercepte les clics souris sur le HUD pour :
     * 1. Fermer le calque via la croix '✕'.
     * 2. Ouvrir l'écran de sélection Cobblemon lors d'un clic sur la carte HUD.
     */
    fun onMouseButtonPressedPre(event: ScreenEvent.MouseButtonPressed.Pre) {
        if (event.button != 0) return // Clic gauche uniquement
        if (!CobblemonOverlayManager.isOverlayActive) return

        val closeBounds = closeBtnBounds
        if (closeBounds != null && closeBounds.contains(event.mouseX, event.mouseY)) {
            CobblemonOverlayManager.clearOverlay()
            event.isCanceled = true
            return
        }

        val bounds = currentBounds
        if (bounds != null && bounds.contains(event.mouseX, event.mouseY)) {
            val mc = Minecraft.getInstance()
            mc.setScreen(
                CobblemonJourneyMapSearchScreen(
                    CobblemonJourneyMapPlugin.currentThemeButton,
                    CobblemonJourneyMapPlugin.currentFullscreen
                )
            )
            event.isCanceled = true
            return
        }
    }
}
