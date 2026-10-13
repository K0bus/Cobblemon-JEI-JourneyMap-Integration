package com.cobblemon.jei.journeymap

import com.cobblemon.jei.data.PokemonSpawnDataLoader
import com.cobblemon.jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import journeymap.api.v2.client.fullscreen.IFullscreen
import journeymap.api.v2.client.fullscreen.IThemeButton
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Écran d'interface graphique dédié à la recherche et sélection de Pokémon pour JourneyMap.
 * Permet au joueur de filtrer par nom ou numéro de Pokédex, de visualiser les conditions
 * et d'activer/désactiver la surbrillance des zones de spawn sur la carte plein écran.
 */
class CobblemonJourneyMapSearchScreen(
    private val themeButton: IThemeButton?,
    private val fullscreen: IFullscreen?
) : Screen(Component.translatable("gui.cobblemon_jm.search.title")) {

    private lateinit var searchBox: EditBox
    private val allSpecies: List<Species> = PokemonSpecies.species.toList()
    private var filteredSpecies: List<Species> = allSpecies
    private var selectedSpecies: Species? = CobblemonOverlayManager.activeSpecies ?: allSpecies.firstOrNull()

    private val itemStackCache = mutableMapOf<Species, ItemStack>()

    private var currentPage: Int = 0
    private val itemsPerPage: Int = 7

    private lateinit var prevPageBtn: Button
    private lateinit var nextPageBtn: Button
    private lateinit var showOverlayBtn: Button
    private lateinit var clearOverlayBtn: Button

    private fun getItemStack(species: Species): ItemStack {
        return itemStackCache.computeIfAbsent(species) { sp ->
            try {
                PokemonSpeciesIngredient(sp, sp.standardForm).itemStack
            } catch (_: Throwable) {
                ItemStack.EMPTY
            }
        }
    }

    override fun init() {
        val centerX = width / 2
        val startY = 32

        // Champ de saisie pour recherche
        searchBox = EditBox(font, centerX - 180, startY, 360, 20, Component.literal(""))
        searchBox.setHint(Component.translatable("gui.cobblemon_jm.search.hint"))
        searchBox.setResponder { query -> onSearchChanged(query) }
        addRenderableWidget(searchBox)

        // Boutons de pagination
        prevPageBtn = Button.builder(Component.literal("◀ Précédent")) {
            if (currentPage > 0) currentPage--
        }.bounds(centerX - 180, height - 34, 80, 20).build()
        addRenderableWidget(prevPageBtn)

        nextPageBtn = Button.builder(Component.literal("Suivant ▶")) {
            val maxPage = (filteredSpecies.size - 1) / itemsPerPage
            if (currentPage < maxPage) currentPage++
        }.bounds(centerX - 95, height - 34, 80, 20).build()
        addRenderableWidget(nextPageBtn)

        // Bouton : Afficher sur la carte
        showOverlayBtn = Button.builder(Component.translatable("gui.cobblemon_jm.search.show_overlay")) {
            selectedSpecies?.let {
                CobblemonOverlayManager.setOverlay(it)
                com.cobblemon.jei.journeymap.structure.StructureOverlayManager.highlightStructureForSpecies(it)
                CobblemonJourneyMapPlugin.updateButton()
                onClose()
            }
        }.bounds(centerX + 10, height - 34, 110, 20).build()
        addRenderableWidget(showOverlayBtn)

        // Bouton : Désactiver le calque
        clearOverlayBtn = Button.builder(Component.translatable("gui.cobblemon_jm.search.clear_overlay")) {
            CobblemonOverlayManager.clearOverlay()
            com.cobblemon.jei.journeymap.structure.StructureOverlayManager.highlightStructureForSpecies(null)
            CobblemonJourneyMapPlugin.updateButton()
            onClose()
        }.bounds(centerX + 125, height - 34, 110, 20).build()
        addRenderableWidget(clearOverlayBtn)

        // Bouton Fermer (en haut à droite)
        addRenderableWidget(
            Button.builder(Component.literal("✕")) {
                onClose()
            }.bounds(width - 30, 8, 22, 20).build()
        )
    }

    private fun onSearchChanged(query: String) {
        val trimmed = query.trim().lowercase()
        filteredSpecies = if (trimmed.isEmpty()) {
            allSpecies
        } else {
            allSpecies.filter { sp ->
                sp.name.lowercase().contains(trimmed) ||
                        sp.translatedName.string.lowercase().contains(trimmed) ||
                        String.format("%04d", sp.nationalPokedexNumber).contains(trimmed) ||
                        sp.nationalPokedexNumber.toString() == trimmed
            }
        }
        currentPage = 0
        if (selectedSpecies !in filteredSpecies) {
            selectedSpecies = filteredSpecies.firstOrNull()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true

        val listLeft = width / 2 - 180
        val listTop = 60
        val itemHeight = 22

        val startIndex = currentPage * itemsPerPage
        val pageItems = filteredSpecies.drop(startIndex).take(itemsPerPage)

        for ((index, sp) in pageItems.withIndex()) {
            val itemY = listTop + index * itemHeight
            if (mouseX >= listLeft && mouseX <= listLeft + 170 && mouseY >= itemY && mouseY < itemY + itemHeight) {
                selectedSpecies = sp
                return true
            }
        }

        return false
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Fond plein écran semi-transparent sombre
        guiGraphics.fill(0, 0, width, height, 0xD0101116.toInt())

        // Titre de l'écran
        val titleText = Component.translatable("gui.cobblemon_jm.search.title").string
        guiGraphics.drawCenteredString(font, titleText, width / 2, 8, 0xFFFFFFFF.toInt())

        val active = CobblemonOverlayManager.activeSpecies
        if (CobblemonOverlayManager.isOverlayActive && active != null) {
            val activeText = "§7Actif sur la carte : §e${active.translatedName.string}§r"
            guiGraphics.drawCenteredString(font, activeText, width / 2, 20, 0xFFE0E0E0.toInt())
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // Rendu de la liste de Pokémon à gauche
        val listLeft = width / 2 - 180
        val listTop = 60
        val itemHeight = 22

        val startIndex = currentPage * itemsPerPage
        val pageItems = filteredSpecies.drop(startIndex).take(itemsPerPage)
        val maxPage = if (filteredSpecies.isEmpty()) 0 else (filteredSpecies.size - 1) / itemsPerPage

        prevPageBtn.active = currentPage > 0
        nextPageBtn.active = currentPage < maxPage
        showOverlayBtn.active = selectedSpecies != null
        clearOverlayBtn.active = CobblemonOverlayManager.isOverlayActive

        for ((index, sp) in pageItems.withIndex()) {
            val itemY = listTop + index * itemHeight
            val isSelected = sp == selectedSpecies
            val isHovered = mouseX >= listLeft && mouseX <= listLeft + 170 && mouseY >= itemY && mouseY < itemY + itemHeight

            val bgColor = when {
                isSelected -> 0x5554A0FF
                isHovered -> 0x3354A0FF
                else -> 0x22FFFFFF
            }

            guiGraphics.fill(listLeft, itemY, listLeft + 170, itemY + itemHeight - 2, bgColor)
            if (isSelected) {
                guiGraphics.fill(listLeft, itemY, listLeft + 3, itemY + itemHeight - 2, 0xFF54A0FF.toInt())
            }

            // Rendu du modèle Pokémon (identique à JEI)
            val stack = getItemStack(sp)
            if (!stack.isEmpty) {
                guiGraphics.renderItem(stack, listLeft + 4, itemY + 3)
            }

            val dexStr = String.format("#%04d", sp.nationalPokedexNumber)
            val nameStr = sp.translatedName.string
            val label = "$dexStr $nameStr"

            val textColor = if (isSelected) 0xFFFFFFFF.toInt() else 0xFFCCCCCC.toInt()
            guiGraphics.drawString(font, label, listLeft + 24, itemY + 6, textColor, false)
        }

        // Indicateur de page
        val pageInfo = "Page ${currentPage + 1} / ${maxPage + 1} (${filteredSpecies.size} Pokémon)"
        guiGraphics.drawString(font, pageInfo, listLeft, listTop + itemsPerPage * itemHeight + 4, 0xFFAAAAAA.toInt(), false)

        // Rendu du panneau de prévisualisation à droite
        val detailLeft = width / 2 + 5
        val detailTop = 60
        val detailWidth = 175
        val detailHeight = itemsPerPage * itemHeight + 14

        guiGraphics.fill(detailLeft, detailTop, detailLeft + detailWidth, detailTop + detailHeight, 0xFF181A22.toInt())
        guiGraphics.fill(detailLeft - 1, detailTop - 1, detailLeft + detailWidth + 1, detailTop, 0xFF2D3142.toInt())
        guiGraphics.fill(detailLeft - 1, detailTop + detailHeight, detailLeft + detailWidth + 1, detailTop + detailHeight + 1, 0xFF2D3142.toInt())
        guiGraphics.fill(detailLeft - 1, detailTop, detailLeft, detailTop + detailHeight, 0xFF2D3142.toInt())
        guiGraphics.fill(detailLeft + detailWidth, detailTop, detailLeft + detailWidth + 1, detailTop + detailHeight, 0xFF2D3142.toInt())

        selectedSpecies?.let { sp ->
            renderPokemonDetail(guiGraphics, sp, detailLeft + 8, detailTop + 8, detailWidth - 16)
        } ?: run {
            guiGraphics.drawCenteredString(font, "Aucun Pokémon sélectionné", detailLeft + detailWidth / 2, detailTop + 40, 0xFF888888.toInt())
        }
    }

    private fun renderPokemonDetail(guiGraphics: GuiGraphics, sp: Species, x: Int, y: Int, maxWidth: Int) {
        val dexStr = String.format("#%04d", sp.nationalPokedexNumber)
        val nameStr = sp.translatedName.string

        // Cadre de prévisualisation du modèle Pokémon 3D à droite
        val previewSize = 42
        val previewX = x + maxWidth - previewSize
        val previewY = y

        guiGraphics.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF0E0F12.toInt())
        guiGraphics.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY, 0xFF3D4050.toInt())
        guiGraphics.fill(previewX - 1, previewY + previewSize, previewX + previewSize + 1, previewY + previewSize + 1, 0xFF3D4050.toInt())
        guiGraphics.fill(previewX - 1, previewY, previewX, previewY + previewSize, 0xFF3D4050.toInt())
        guiGraphics.fill(previewX + previewSize, previewY, previewX + previewSize + 1, previewY + previewSize, 0xFF3D4050.toInt())

        // Modèle du Pokémon agrandi à 2.2x
        val stack = getItemStack(sp)
        if (!stack.isEmpty) {
            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate((previewX + 3).toFloat(), (previewY + 3).toFloat(), 0f)
            guiGraphics.pose().scale(2.2f, 2.2f, 1.0f)
            guiGraphics.renderItem(stack, 0, 0)
            guiGraphics.pose().popPose()
        }

        // Titre et infos à gauche du modèle
        guiGraphics.drawString(font, dexStr, x, y, 0xFFFFD32A.toInt(), false)
        val maxTextW = previewX - x - 4
        val displayName = if (font.width(nameStr) > maxTextW) {
            font.plainSubstrByWidth(nameStr, maxTextW - 6) + "…"
        } else {
            nameStr
        }
        guiGraphics.drawString(font, displayName, x, y + 11, 0xFFFFFFFF.toInt(), false)

        // Badges des types élémentaires
        var typeX = x
        val type1 = sp.primaryType.displayName.string.uppercase()
        val c1 = CobblemonOverlayManager.getElementColor(sp.primaryType.name)
        val w1 = font.width(type1) + 6
        guiGraphics.fill(typeX, y + 24, typeX + w1, y + 33, c1)
        guiGraphics.drawString(font, type1, typeX + 3, y + 25, 0xFFFFFFFF.toInt(), false)
        typeX += w1 + 4

        sp.secondaryType?.let { sec ->
            val type2 = sec.displayName.string.uppercase()
            val c2 = CobblemonOverlayManager.getElementColor(sec.name)
            val w2 = font.width(type2) + 6
            if (typeX + w2 <= previewX - 2) {
                guiGraphics.fill(typeX, y + 24, typeX + w2, y + 33, c2)
                guiGraphics.drawString(font, type2, typeX + 3, y + 25, 0xFFFFFFFF.toInt(), false)
            }
        }

        // Séparateur horizontal
        guiGraphics.fill(x - 2, y + 46, x + maxWidth + 2, y + 47, 0xFF2D3142.toInt())

        // Informations de spawn
        val recipes = PokemonSpawnDataLoader.getSpawnRecipesForSpecies(sp)
        val natural = recipes.filter { it.isNaturalSpawn }

        val infoY = y + 51
        guiGraphics.drawString(font, "Apparitions :", x, infoY, 0xFFFF9F43.toInt(), false)

        if (natural.isEmpty()) {
            guiGraphics.drawString(font, "✗ Aucun spawn naturel", x, infoY + 12, 0xFFFF4757.toInt(), false)
            guiGraphics.drawString(font, "(Évolution, fossile ou œuf)", x, infoY + 23, 0xFFAAAAAA.toInt(), false)
        } else {
            val best = natural.maxByOrNull { it.weight } ?: natural.first()
            val rarity = SpawnRarity.fromBucketOrWeight(best.bucket, best.weight)
            guiGraphics.drawString(font, "✓ Spawns sauvages actifs", x, infoY + 12, 0xFF2ED573.toInt(), false)
            guiGraphics.drawString(font, "• Rareté : ${rarity.displayName}", x, infoY + 23, (0xFF000000 or RarityColorPalette.createShapeProperties(rarity).strokeColor.toLong()).toInt(), false)
            guiGraphics.drawString(font, "• Niveaux : Lv. ${best.levelMin}-${best.levelMax}", x, infoY + 34, 0xFFCCCCCC.toInt(), false)
            guiGraphics.drawString(font, "• Heure : ${best.timeRange} | Météo : ${best.weather}", x, infoY + 45, 0xFFCCCCCC.toInt(), false)

            val dims = natural.flatMap { it.dimensions }.toSet()
            val dimsDesc = if (dims.isEmpty()) "Tous mondes" else dims.joinToString(", ") { d ->
                val clean = if (d.contains(":")) d.substringAfter(":") else d
                when (clean.lowercase()) {
                    "overworld" -> "Overworld"
                    "the_nether", "nether" -> "Nether"
                    "the_end", "end" -> "The End"
                    "the_aether", "aether" -> "L'Aether"
                    else -> clean.replaceFirstChar { it.uppercase() }
                }
            }
            guiGraphics.drawString(font, "• Monde : $dimsDesc", x, infoY + 56, 0xFF1DD1A1.toInt(), false)

            val biomesCount = natural.flatMap { it.biomes }.toSet().size
            val biomesDesc = if (biomesCount > 0) "$biomesCount biomes compatibles" else "Tous biomes de la dimension"
            guiGraphics.drawString(font, "• Lieux : $biomesDesc", x, infoY + 67, 0xFF54A0FF.toInt(), false)
        }

        // Détection de structures (fossiles ou monuments légendaires)
        val matchingStructs = com.cobblemon.jei.journeymap.structure.StructureOverlayManager.getDiscoveredStructuresForSpecies(sp)
        val isFossilOrLegendary = com.cobblemon.jei.journeymap.structure.DynamicStructureRegistryService.getStructuresForSpecies(sp).isNotEmpty() ||
            matchingStructs.isNotEmpty()

        if (isFossilOrLegendary) {
            val structY = if (natural.isEmpty()) infoY + 38 else infoY + 78
            guiGraphics.drawString(font, "• Structures :", x, structY, 0xFFFFA502.toInt(), false)
            if (matchingStructs.isNotEmpty()) {
                val first = matchingStructs.first()
                guiGraphics.drawString(font, "  ✓ ${matchingStructs.size} site(s) découvert(s)", x, structY + 11, 0xFF2ED573.toInt(), false)
                guiGraphics.drawString(font, "  [${first.pos.x}, ${first.pos.z}]", x, structY + 22, 0xFFCCCCCC.toInt(), false)
            } else {
                guiGraphics.drawString(font, "  ✗ Non découverte (zone explorée)", x, structY + 11, 0xFFFF6B6B.toInt(), false)
            }
        }

        // Statut du calque actuel
        val statusY = y + 138
        if (CobblemonOverlayManager.isOverlayActive && CobblemonOverlayManager.activeSpecies == sp) {
            guiGraphics.fill(x - 2, statusY - 2, x + maxWidth + 2, statusY + 12, 0x332ED573.toInt())
            val count = SpawnOverlayManager.activeRarityCounts.values.sum()
            val text = if (count > 0) "▶ Calque actif ($count chunks)" else "▶ Calque actif sur la carte"
            guiGraphics.drawString(font, text, x, statusY, 0xFF2ED573.toInt(), false)
        }
    }

    override fun onClose() {
        // Retour transparent sur la carte plein écran de JourneyMap
        minecraft?.setScreen(fullscreen?.screen)
    }

    override fun isPauseScreen(): Boolean = false
}
