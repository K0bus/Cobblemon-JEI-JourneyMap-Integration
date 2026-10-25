package fr.k0bus.cobblemon_jei.journeymap

import fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader
import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
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

import fr.k0bus.cobblemon_jei.recipe.PokemonSpawnRecipe
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level

/**
 * Écran d'interface graphique dédié à la recherche et sélection de Pokémon pour JourneyMap.
 * Permet au joueur de filtrer par nom ou numéro de Pokédex, par dimension/monde,
 * de visualiser les conditions et d'activer/désactiver la surbrillance des zones de spawn.
 */
class CobblemonJourneyMapSearchScreen(
    private val themeButton: IThemeButton?,
    private val fullscreen: IFullscreen?
) : Screen(Component.translatable("gui.cobblemon_jm.search.title")) {

    sealed class DimensionFilter {
        abstract val shortName: String
        abstract val fullName: Component

        data object All : DimensionFilter() {
            override val shortName: String = "Tous"
            override val fullName: Component = Component.translatable("gui.cobblemon_jm.filter.all_dimensions")
        }

        data class Current(val dimKey: ResourceKey<Level>) : DimensionFilter() {
            val readableName: String = getReadableDimensionName(dimKey)
            override val shortName: String = "Actuel"
            override val fullName: Component = Component.translatable("gui.cobblemon_jm.filter.current_dimension", readableName)
        }

        data class Specific(val dimKey: ResourceKey<Level>, val label: String) : DimensionFilter() {
            override val shortName: String = label
            override val fullName: Component = Component.literal(label)
        }
    }

    private lateinit var searchBox: EditBox
    private lateinit var dimFilterBtn: Button
    private lateinit var monumentModeBtn: Button
    private val allSpecies: List<Species> = PokemonSpecies.species.toList()
    private var filteredSpecies: List<Species> = allSpecies
    private var selectedSpecies: Species? = CobblemonOverlayManager.activeSpecies ?: allSpecies.firstOrNull()

    private var availableFilters: List<DimensionFilter> = emptyList()
    private var currentDimensionFilter: DimensionFilter = DimensionFilter.All
    private var dimensionCompatibleSpecies: Set<Species>? = null

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

        initDimensionFilters()

        val searchWidth = 236
        val filterWidth = 120
        val gap = 4

        // Champ de saisie pour recherche
        searchBox = EditBox(font, centerX - 180, startY, searchWidth, 20, Component.literal(""))
        searchBox.setHint(Component.translatable("gui.cobblemon_jm.search.hint"))
        searchBox.setResponder { applyFilters() }
        addRenderableWidget(searchBox)

        // Bouton de filtre par dimension au bout de la barre de recherche
        dimFilterBtn = Button.builder(getFilterButtonText()) {
            cycleDimensionFilter(forward = true)
        }.bounds(centerX - 180 + searchWidth + gap, startY, filterWidth, 20).build()
        updateFilterButtonTooltip()
        addRenderableWidget(dimFilterBtn)

        // Boutons de pagination
        prevPageBtn = Button.builder(Component.translatable("gui.cobblemon_jm.search.prev")) {
            if (currentPage > 0) currentPage--
        }.bounds(centerX - 180, height - 34, 80, 20).build()
        addRenderableWidget(prevPageBtn)

        nextPageBtn = Button.builder(Component.translatable("gui.cobblemon_jm.search.next")) {
            val maxPage = (filteredSpecies.size - 1) / itemsPerPage
            if (currentPage < maxPage) currentPage++
        }.bounds(centerX - 95, height - 34, 80, 20).build()
        addRenderableWidget(nextPageBtn)

        // Bouton : Afficher sur la carte
        showOverlayBtn = Button.builder(Component.translatable("gui.cobblemon_jm.search.show_overlay")) {
            selectedSpecies?.let {
                CobblemonOverlayManager.setOverlay(it)
                fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager.highlightStructureForSpecies(it)
                CobblemonJourneyMapPlugin.updateButton()
                onClose()
            }
        }.bounds(centerX + 10, height - 34, 110, 20).build()
        addRenderableWidget(showOverlayBtn)

        // Bouton : Désactiver le calque
        clearOverlayBtn = Button.builder(Component.translatable("gui.cobblemon_jm.search.clear_overlay")) {
            CobblemonOverlayManager.clearOverlay()
            fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager.highlightStructureForSpecies(null)
            CobblemonJourneyMapPlugin.updateButton()
            onClose()
        }.bounds(centerX + 125, height - 34, 110, 20).build()
        addRenderableWidget(clearOverlayBtn)

        // Bouton Pokédex Tracker
        addRenderableWidget(
            Button.builder(Component.literal("📖 Pokédex")) {
                minecraft?.setScreen(fr.k0bus.cobblemon_jei.pokedex.CobblemonPokedexScreen(this))
            }.bounds(width - 130, 8, 95, 20).build()
        )

        // Bouton Mode Monument Tracker (en haut à gauche)
        monumentModeBtn = Button.builder(Component.literal("")) {
            fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager.cycleTrackingMode()
            updateMonumentButton()
        }.bounds(10, 8, 125, 20).build()
        updateMonumentButton()
        addRenderableWidget(monumentModeBtn)

        // Bouton Fermer (en haut à droite)
        addRenderableWidget(
            Button.builder(Component.literal("✕")) {
                onClose()
            }.bounds(width - 30, 8, 22, 20).build()
        )

        applyFilters()
    }

    private fun updateMonumentButton() {
        val mode = fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager.trackingMode
        monumentModeBtn.message = Component.literal("${mode.icon} ${mode.shortLabel}")

        val tooltipLines = mutableListOf<Component>()
        tooltipLines.add(Component.translatable("gui.cobblemon_jm.monuments.tracker_title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
        tooltipLines.add(Component.translatable("gui.cobblemon_jm.filter.active", Component.translatable(mode.translationKey)).withStyle(ChatFormatting.AQUA))
        tooltipLines.add(Component.empty())
        tooltipLines.add(Component.translatable("gui.cobblemon_jm.monuments.tooltip_cycle").withStyle(ChatFormatting.DARK_GRAY))

        monumentModeBtn.tooltip = Tooltip.create(
            Component.literal(tooltipLines.joinToString("\n") { it.string })
        )
    }

    private fun initDimensionFilters() {
        val currentDim = minecraft?.level?.dimension()
        val allSpawnRecipes = PokemonSpawnDataLoader.loadSpawnRecipes()
        val filters = mutableListOf<DimensionFilter>()

        // Option "Monde actuel" si le joueur se trouve dans une dimension contenant des Pokémon
        if (currentDim != null && hasPokemonInDimension(currentDim, allSpawnRecipes)) {
            filters.add(DimensionFilter.Current(currentDim))
        }
        filters.add(DimensionFilter.All)

        val seen = mutableSetOf<ResourceKey<Level>>()

        // Dimensions candidates : Overworld, Nether, The End + dimensions moddées enregistrées
        val candidateDims = mutableListOf<ResourceKey<Level>>()
        candidateDims.add(Level.OVERWORLD)
        candidateDims.add(Level.NETHER)
        candidateDims.add(Level.END)

        val registeredLevels = minecraft?.connection?.levels()?.toSet() ?: emptySet()
        val extraRegistered = registeredLevels
            .filter { it !in candidateDims }
            .sortedBy { it.location().toString() }
        candidateDims.addAll(extraRegistered)

        // Seuls les mondes contenant effectivement au moins un Pokémon sont conservés
        for (dimKey in candidateDims) {
            if (seen.add(dimKey) && hasPokemonInDimension(dimKey, allSpawnRecipes)) {
                val cleanName = getReadableDimensionName(dimKey)
                filters.add(DimensionFilter.Specific(dimKey, cleanName))
            }
        }

        availableFilters = filters
        if (currentDimensionFilter !in availableFilters) {
            currentDimensionFilter = availableFilters.firstOrNull { it is DimensionFilter.Current }
                ?: availableFilters.firstOrNull { it is DimensionFilter.All }
                ?: DimensionFilter.All
        }

        recomputeDimensionCompatibleSpecies()
    }

    private fun hasPokemonInDimension(
        dimKey: ResourceKey<Level>,
        allSpawnRecipes: List<PokemonSpawnRecipe>
    ): Boolean {
        // 1. Spawns sauvages naturels
        if (allSpawnRecipes.any { it.isNaturalSpawn && isRecipeInDimension(it, dimKey) }) {
            return true
        }

        // 2. Structures découvertes ou répertoriées (fossiles, sanctuaires légendaires)
        try {
            if (dimKey == Level.OVERWORLD && allSpecies.any {
                fr.k0bus.cobblemon_jei.journeymap.structure.DynamicStructureRegistryService.getStructuresForSpecies(it).isNotEmpty()
            }) {
                return true
            }
            if (allSpecies.any { sp ->
                fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager.getDiscoveredStructuresForSpecies(sp).any { it.dimension == dimKey }
            }) {
                return true
            }
        } catch (_: Throwable) {}

        return false
    }

    private fun cycleDimensionFilter(forward: Boolean) {
        if (availableFilters.isEmpty()) return
        val currentIndex = availableFilters.indexOf(currentDimensionFilter)
        val nextIndex = if (forward) {
            (currentIndex + 1) % availableFilters.size
        } else {
            if (currentIndex <= 0) availableFilters.size - 1 else currentIndex - 1
        }
        currentDimensionFilter = availableFilters[nextIndex]
        dimFilterBtn.message = getFilterButtonText()
        updateFilterButtonTooltip()
        recomputeDimensionCompatibleSpecies()
        applyFilters()
    }

    private fun getFilterButtonText(): Component {
        return Component.literal("🌐 ${currentDimensionFilter.shortName}")
    }

    private fun updateFilterButtonTooltip() {
        val currentDim = minecraft?.level?.dimension()
        val tooltipLines = mutableListOf<Component>()
        tooltipLines.add(Component.translatable("gui.cobblemon_jm.filter.dimension_title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
        tooltipLines.add(Component.translatable("gui.cobblemon_jm.filter.active", currentDimensionFilter.fullName).withStyle(ChatFormatting.AQUA))

        if (currentDimensionFilter is DimensionFilter.Current && currentDim != null) {
            val dimName = getReadableDimensionName(currentDim)
            tooltipLines.add(Component.literal("• ID : ${currentDim.location()} ($dimName)").withStyle(ChatFormatting.GRAY))
        }

        tooltipLines.add(Component.empty())
        tooltipLines.add(Component.translatable("gui.cobblemon_jm.filter.hint_cycle").withStyle(ChatFormatting.DARK_GRAY))

        dimFilterBtn.tooltip = Tooltip.create(
            Component.literal(tooltipLines.joinToString("\n") { it.string })
        )
    }

    private fun recomputeDimensionCompatibleSpecies() {
        val filter = currentDimensionFilter
        if (filter is DimensionFilter.All) {
            dimensionCompatibleSpecies = null
            return
        }

        val targetDim: ResourceKey<Level> = when (filter) {
            is DimensionFilter.Current -> filter.dimKey
            is DimensionFilter.Specific -> filter.dimKey
            else -> {
                dimensionCompatibleSpecies = null
                return
            }
        }

        val matching = mutableSetOf<Species>()
        val allSpawnRecipes = PokemonSpawnDataLoader.loadSpawnRecipes()

        for (recipe in allSpawnRecipes) {
            if (!recipe.isNaturalSpawn) continue
            if (isRecipeInDimension(recipe, targetDim)) {
                matching.add(recipe.species)
            }
        }

        try {
            for (species in allSpecies) {
                val structures = fr.k0bus.cobblemon_jei.journeymap.structure.DynamicStructureRegistryService.getStructuresForSpecies(species)
                if (structures.isNotEmpty() && targetDim == Level.OVERWORLD) {
                    matching.add(species)
                }
                val discovered = fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager.getDiscoveredStructuresForSpecies(species)
                if (discovered.any { it.dimension == targetDim }) {
                    matching.add(species)
                }
            }
        } catch (_: Throwable) {}

        dimensionCompatibleSpecies = matching
    }

    private fun isRecipeInDimension(recipe: PokemonSpawnRecipe, dimension: ResourceKey<Level>): Boolean {
        return CobblemonSpawnMatcher.isRecipeInDimension(recipe, dimension)
    }

    private fun applyFilters() {
        val query = if (::searchBox.isInitialized) searchBox.value.trim().lowercase() else ""
        val textMatched = if (query.isEmpty()) {
            allSpecies
        } else {
            allSpecies.filter { sp ->
                sp.name.lowercase().contains(query) ||
                        sp.translatedName.string.lowercase().contains(query) ||
                        String.format("%04d", sp.nationalPokedexNumber).contains(query) ||
                        sp.nationalPokedexNumber.toString() == query
            }
        }

        val dimSet = dimensionCompatibleSpecies
        filteredSpecies = if (dimSet == null) {
            textMatched
        } else {
            textMatched.filter { sp -> sp in dimSet }
        }

        currentPage = 0
        if (selectedSpecies !in filteredSpecies) {
            selectedSpecies = filteredSpecies.firstOrNull()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Clic droit sur le bouton de filtre pour reculer dans la liste des mondes
        if (button == 1 && ::dimFilterBtn.isInitialized && dimFilterBtn.isMouseOver(mouseX, mouseY)) {
            cycleDimensionFilter(forward = false)
            dimFilterBtn.playDownSound(minecraft?.soundManager ?: net.minecraft.client.Minecraft.getInstance().soundManager)
            return true
        }

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
        val filterTag = if (currentDimensionFilter !is DimensionFilter.All) " [${currentDimensionFilter.shortName}]" else ""
        val pageInfo = Component.translatable("gui.cobblemon_jm.search.page_info", currentPage + 1, maxPage + 1, filteredSpecies.size, filterTag).string
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
            val noSelection = Component.translatable("gui.cobblemon_jm.search.no_selection").string
            guiGraphics.drawCenteredString(font, noSelection, detailLeft + detailWidth / 2, detailTop + 40, 0xFF888888.toInt())
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

        val targetDim: ResourceKey<Level>? = when (val filter = currentDimensionFilter) {
            is DimensionFilter.Current -> filter.dimKey
            is DimensionFilter.Specific -> filter.dimKey
            else -> null
        }
        val dimFiltered = if (targetDim != null) natural.filter { isRecipeInDimension(it, targetDim) } else natural
        val displayRecipes = if (dimFiltered.isNotEmpty()) dimFiltered else natural

        val infoY = y + 51
        val spawnsTitle = Component.translatable("gui.cobblemon_jm.detail.spawns_title").string
        guiGraphics.drawString(font, spawnsTitle, x, infoY, 0xFFFF9F43.toInt(), false)

        if (natural.isEmpty()) {
            val noNat = Component.translatable("gui.cobblemon_jm.detail.no_natural_spawns").string
            guiGraphics.drawString(font, noNat, x, infoY + 12, 0xFFFF4757.toInt(), false)
            val subNat = Component.translatable("gui.cobblemon_jm.detail.no_natural_spawns_sub").string
            guiGraphics.drawString(font, subNat, x, infoY + 23, 0xFFAAAAAA.toInt(), false)
        } else if (targetDim != null && dimFiltered.isEmpty()) {
            val filterName = currentDimensionFilter.shortName
            val absent = Component.translatable("gui.cobblemon_jm.detail.absent_from", filterName).string
            guiGraphics.drawString(font, absent, x, infoY + 12, 0xFFFF4757.toInt(), false)
            val allDims = natural.flatMap { it.dimensions }.toSet()
            val dimsDesc = if (allDims.isEmpty()) "Overworld" else allDims.joinToString(", ") { d ->
                val clean = if (d.contains(":")) d.substringAfter(":") else d
                when (clean.lowercase()) {
                    "overworld" -> "Overworld"
                    "the_nether", "nether" -> "Nether"
                    "the_end", "end" -> "The End"
                    "the_aether", "aether" -> "L'Aether"
                    else -> clean.replaceFirstChar { it.uppercase() }
                }
            }
            val present = Component.translatable("gui.cobblemon_jm.detail.present_in", dimsDesc).string
            guiGraphics.drawString(font, present, x, infoY + 23, 0xFFAAAAAA.toInt(), false)
        } else {
            val best = displayRecipes.maxByOrNull { it.weight } ?: displayRecipes.first()
            val rarity = SpawnRarity.fromBucketOrWeight(best.bucket, best.weight)
            val activeSpawns = Component.translatable("gui.cobblemon_jm.detail.wild_spawns_active").string
            guiGraphics.drawString(font, activeSpawns, x, infoY + 12, 0xFF2ED573.toInt(), false)

            val rarityLine = Component.translatable("gui.cobblemon_jm.detail.rarity", rarity.displayName).string
            guiGraphics.drawString(font, rarityLine, x, infoY + 23, (0xFF000000 or RarityColorPalette.createShapeProperties(rarity).strokeColor.toLong()).toInt(), false)

            val levelsLine = Component.translatable("gui.cobblemon_jm.detail.levels", best.levelMin, best.levelMax).string
            guiGraphics.drawString(font, levelsLine, x, infoY + 34, 0xFFCCCCCC.toInt(), false)

            val timeWeatherLine = Component.translatable("gui.cobblemon_jm.detail.time_weather", best.timeRange, best.weather).string
            guiGraphics.drawString(font, timeWeatherLine, x, infoY + 45, 0xFFCCCCCC.toInt(), false)

            val dims = displayRecipes.flatMap { it.dimensions }.toSet()
            val dimsDesc = if (dims.isEmpty()) "Overworld" else dims.joinToString(", ") { d ->
                val clean = if (d.contains(":")) d.substringAfter(":") else d
                when (clean.lowercase()) {
                    "overworld" -> "Overworld"
                    "the_nether", "nether" -> "Nether"
                    "the_end", "end" -> "The End"
                    "the_aether", "aether" -> "L'Aether"
                    else -> clean.replaceFirstChar { it.uppercase() }
                }
            }
            val worldLine = Component.translatable("gui.cobblemon_jm.detail.world", dimsDesc).string
            guiGraphics.drawString(font, worldLine, x, infoY + 56, 0xFF1DD1A1.toInt(), false)

            val biomesCount = displayRecipes.flatMap { it.biomes }.toSet().size
            val biomesLine = if (biomesCount > 0) {
                Component.translatable("gui.cobblemon_jm.detail.locations_count", biomesCount).string
            } else {
                Component.translatable("gui.cobblemon_jm.detail.locations_all").string
            }
            guiGraphics.drawString(font, biomesLine, x, infoY + 67, 0xFF54A0FF.toInt(), false)
        }

        // Détection de structures (fossiles ou monuments légendaires)
        val matchingStructs = fr.k0bus.cobblemon_jei.journeymap.structure.StructureOverlayManager.getDiscoveredStructuresForSpecies(sp)
        val isFossilOrLegendary = fr.k0bus.cobblemon_jei.journeymap.structure.DynamicStructureRegistryService.getStructuresForSpecies(sp).isNotEmpty() ||
            matchingStructs.isNotEmpty()

        if (isFossilOrLegendary) {
            val structY = if (natural.isEmpty()) infoY + 38 else infoY + 78
            val structTitle = Component.translatable("gui.cobblemon_jm.detail.structures_title").string
            guiGraphics.drawString(font, structTitle, x, structY, 0xFFFFA502.toInt(), false)
            if (matchingStructs.isNotEmpty()) {
                val first = matchingStructs.first()
                val discovered = Component.translatable("gui.cobblemon_jm.detail.sites_discovered", matchingStructs.size).string
                guiGraphics.drawString(font, discovered, x, structY + 11, 0xFF2ED573.toInt(), false)
                guiGraphics.drawString(font, "  [${first.pos.x}, ${first.pos.z}]", x, structY + 22, 0xFFCCCCCC.toInt(), false)
            } else {
                val notDiscovered = Component.translatable("gui.cobblemon_jm.detail.sites_not_discovered").string
                guiGraphics.drawString(font, notDiscovered, x, structY + 11, 0xFFFF6B6B.toInt(), false)
            }
        }

        // Statut du calque actuel
        val statusY = y + 138
        if (CobblemonOverlayManager.isOverlayActive && CobblemonOverlayManager.activeSpecies == sp) {
            guiGraphics.fill(x - 2, statusY - 2, x + maxWidth + 2, statusY + 12, 0x332ED573.toInt())
            val count = SpawnOverlayManager.activeRarityCounts.values.sum()
            val text = if (count > 0) {
                Component.translatable("gui.cobblemon_jm.detail.layer_active_chunks", count).string
            } else {
                Component.translatable("gui.cobblemon_jm.detail.layer_active").string
            }
            guiGraphics.drawString(font, text, x, statusY, 0xFF2ED573.toInt(), false)
        }
    }

    override fun onClose() {
        // Retour transparent sur la carte plein écran de JourneyMap
        minecraft?.setScreen(fullscreen?.screen)
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        fun getReadableDimensionName(dimKey: ResourceKey<Level>): String {
            val path = dimKey.location().path.lowercase()
            return when (path) {
                "overworld" -> "Overworld"
                "the_nether", "nether" -> "Nether"
                "the_end", "end" -> "The End"
                "the_aether", "aether" -> "L'Aether"
                "twilight_forest" -> "Twilight Forest"
                "undergarden" -> "Undergarden"
                else -> dimKey.location().path.replace("_", " ").split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            }
        }
    }
}
