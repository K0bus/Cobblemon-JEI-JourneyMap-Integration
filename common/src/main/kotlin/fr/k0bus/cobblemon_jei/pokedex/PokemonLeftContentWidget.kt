package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.application.expedition.ExpeditionSuggestionEngine
import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionSuggestion
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import fr.k0bus.cobblemon_jei.journeymap.ExpeditionMapOverlay

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import kotlin.math.max

/**
 * Widget dédié à l'affichage et à l'interaction du panneau gauche du Pokédex :
 * Grille Pokédex, Évolutions, Origines & Reproduction, Fossiles, Légendaires et Expéditions intégrées.
 */
class PokemonLeftContentWidget(
    private val getItemStack: (Species, FormData?) -> ItemStack,
    private val onSelectTracked: (TrackedPokemon) -> Unit,
    private val onSelectGoal: ((EvolutionGoal) -> Unit)? = null,
    private val onOpenTargetsDetail: (String, String, List<PokemonTarget>) -> Unit
) {
    var scrollOffset: Double = 0.0
    var maxScroll: Double = 0.0
    private var isDraggingScrollbar: Boolean = false
    private var dragStartMouseY: Double = 0.0
    private var dragStartScroll: Double = 0.0

    companion object {
        const val CARD_WIDTH = 44
        const val CARD_HEIGHT = 52
        const val CARD_GAP = 4
        const val EVO_CARD_HEIGHT = 46
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    fun resetScroll() {
        scrollOffset = 0.0
        maxScroll = 0.0
        isDraggingScrollbar = false
    }

    fun getLeftAreaBounds(screenWidth: Int, screenHeight: Int, isExpeditionTab: Boolean): IntArray {
        val x = 12
        val y = 73
        val w = if (isExpeditionTab) max(180, screenWidth - 24) else max(180, screenWidth - 215)
        val h = max(80, screenHeight - y - 26)
        return intArrayOf(x, y, w, h)
    }

    fun recomputeMaxScroll(screenWidth: Int, screenHeight: Int, data: LeftContentData) {
        val bounds = getLeftAreaBounds(screenWidth, screenHeight, data.mainTab == MainTab.EXPEDITIONS)
        val w = bounds[2]
        val h = bounds[3] - 8

        when (data.mainTab) {
            MainTab.POKEDEX -> {
                val usableW = w - 10
                val cols = max(1, usableW / (CARD_WIDTH + CARD_GAP))
                val rows = (data.filteredTracked.size + cols - 1) / cols
                val totalH = rows * (CARD_HEIGHT + CARD_GAP)
                maxScroll = max(0.0, (totalH - h).toDouble())
            }
            MainTab.EVOLUTIONS -> {
                when (data.evolutionSubTab) {
                    EvolutionSubTab.PENDING_EVOLUTIONS -> {
                        val totalH = data.filteredGoals.size * (EVO_CARD_HEIGHT + 4)
                        maxScroll = max(0.0, (totalH - h).toDouble())
                    }
                    EvolutionSubTab.ITEM_EVOLUTIONS -> {
                        val totalH = data.filteredItemGoals.size * (EVO_CARD_HEIGHT + 4)
                        maxScroll = max(0.0, (totalH - h).toDouble())
                    }
                    EvolutionSubTab.MISSING_ORIGINS -> {
                        val cardH = 62
                        val cardGap = 4
                        val totalH = data.filteredBreedingOrigins.size * (cardH + cardGap)
                        maxScroll = max(0.0, (totalH - h).toDouble())
                    }
                    EvolutionSubTab.FOSSILS -> {
                        val cardH = 64
                        val cardGap = 4
                        val totalH = data.filteredFossilGoals.size * (cardH + cardGap)
                        maxScroll = max(0.0, (totalH - h).toDouble())
                    }
                    EvolutionSubTab.LEGENDARIES -> {
                        val usableW = w - 10
                        val cols = max(1, usableW / (CARD_WIDTH + CARD_GAP))
                        val rows = (data.filteredLegendaries.size + cols - 1) / cols
                        val totalH = rows * (CARD_HEIGHT + CARD_GAP)
                        maxScroll = max(0.0, (totalH - h).toDouble())
                    }
                }
            }
            MainTab.EXPEDITIONS -> {
                val cardH = 60
                val cardGap = 6
                val totalH = data.expeditionSuggestions.take(5).size * (cardH + cardGap)
                maxScroll = max(0.0, (totalH - h).toDouble())
            }
        }
        scrollOffset = scrollOffset.coerceIn(0.0, maxScroll)
    }

    fun render(
        guiGraphics: GuiGraphics,
        data: LeftContentData,
        mouseX: Int,
        mouseY: Int,
        screenWidth: Int,
        screenHeight: Int,
        font: Font
    ) {
        val bounds = getLeftAreaBounds(screenWidth, screenHeight, data.mainTab == MainTab.EXPEDITIONS)
        val leftX = bounds[0]
        val topY = bounds[1]
        val areaW = bounds[2]
        val areaH = bounds[3]

        // Cadre de fond de la zone gauche
        guiGraphics.fill(leftX - 1, topY - 1, leftX + areaW + 1, topY + areaH + 1, 0xFF1D2029.toInt())
        guiGraphics.fill(leftX, topY, leftX + areaW, topY + areaH, 0xFF12141A.toInt())

        // Découpage d'affichage (Scissor)
        guiGraphics.enableScissor(leftX, topY, leftX + areaW, topY + areaH)

        when (data.mainTab) {
            MainTab.POKEDEX -> {
                renderGridTab(guiGraphics, leftX + 4, topY + 4, areaW - 14, areaH, mouseX, mouseY, data.filteredTracked, data.selectedTracked, font)
            }
            MainTab.EVOLUTIONS -> {
                when (data.evolutionSubTab) {
                    EvolutionSubTab.PENDING_EVOLUTIONS -> renderEvolutionsTab(guiGraphics, leftX + 4, topY + 4, areaW - 14, areaH, mouseX, mouseY, data.filteredGoals, data.selectedGoal, font)
                    EvolutionSubTab.ITEM_EVOLUTIONS -> renderEvolutionsTab(guiGraphics, leftX + 4, topY + 4, areaW - 14, areaH, mouseX, mouseY, data.filteredItemGoals, data.selectedGoal, font)
                    EvolutionSubTab.MISSING_ORIGINS -> renderMissingOriginsTab(guiGraphics, leftX + 4, topY + 4, areaW - 14, areaH, mouseX, mouseY, data.filteredBreedingOrigins, data.selectedTracked, font)
                    EvolutionSubTab.FOSSILS -> renderFossilsTab(guiGraphics, leftX + 4, topY + 4, areaW - 14, areaH, mouseX, mouseY, data.filteredFossilGoals, data.selectedTracked, font)
                    EvolutionSubTab.LEGENDARIES -> renderGridTab(guiGraphics, leftX + 4, topY + 4, areaW - 14, areaH, mouseX, mouseY, data.filteredLegendaries, data.selectedTracked, font)
                }
            }
            MainTab.EXPEDITIONS -> {
                renderIntegratedExpeditionsTab(guiGraphics, leftX + 4, topY + 4, areaW - 14, areaH, mouseX, mouseY, data.expeditionSuggestions, data.isExpeditionLoading, font)
            }
        }

        guiGraphics.disableScissor()

        // Scrollbar
        renderScrollbar(guiGraphics, leftX + areaW - 6, topY + 2, 4, areaH - 4)
    }

    private fun renderGridTab(
        guiGraphics: GuiGraphics,
        startX: Int,
        startY: Int,
        usableW: Int,
        usableH: Int,
        mouseX: Int,
        mouseY: Int,
        list: List<TrackedPokemon>,
        selectedTracked: TrackedPokemon?,
        font: Font
    ) {
        val cols = max(1, usableW / (CARD_WIDTH + CARD_GAP))
        val scrolledY = startY - scrollOffset.toInt()

        if (list.isEmpty()) {
            val emptyMsg = "Aucun Pokémon ne correspond à votre recherche."
            guiGraphics.drawCenteredString(font, emptyMsg, startX + usableW / 2, startY + 40, 0xFF888888.toInt())
            return
        }

        var hoveredPokemon: TrackedPokemon? = null

        for ((index, item) in list.withIndex()) {
            val col = index % cols
            val row = index / cols
            val cardX = startX + col * (CARD_WIDTH + CARD_GAP)
            val cardY = scrolledY + row * (CARD_HEIGHT + CARD_GAP)

            if (cardY + CARD_HEIGHT < startY || cardY > startY + usableH) {
                continue
            }

            val isSelected = (selectedTracked?.species?.let { PokemonTrackingManager.isSameSpecies(it, item.species) } == true)
            val isHovered = (mouseX >= cardX && mouseX < cardX + CARD_WIDTH && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT)
            if (isHovered && mouseY in startY until startY + usableH) {
                hoveredPokemon = item
            }

            val (bgColor, borderColor) = when (item.status) {
                PokedexStatus.CAUGHT -> Pair(0x44143D1A, 0xFF2ECC71.toInt())
                PokedexStatus.ENCOUNTERED -> Pair(0x444D3A14, 0xFFE67E22.toInt())
                PokedexStatus.MISSING -> Pair(0x441E2028, 0xFF353A47.toInt())
            }

            guiGraphics.fill(cardX, cardY, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, bgColor)
            guiGraphics.fill(cardX, cardY, cardX + CARD_WIDTH, cardY + 1, borderColor)
            guiGraphics.fill(cardX, cardY + CARD_HEIGHT - 1, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, borderColor)
            guiGraphics.fill(cardX, cardY, cardX + 1, cardY + CARD_HEIGHT, borderColor)
            guiGraphics.fill(cardX + CARD_WIDTH - 1, cardY, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, borderColor)

            if (isSelected) {
                guiGraphics.fill(cardX - 1, cardY - 1, cardX + CARD_WIDTH + 1, cardY + 1, 0xFF3498DB.toInt())
                guiGraphics.fill(cardX - 1, cardY + CARD_HEIGHT - 1, cardX + CARD_WIDTH + 1, cardY + CARD_HEIGHT + 1, 0xFF3498DB.toInt())
                guiGraphics.fill(cardX - 1, cardY - 1, cardX + 1, cardY + CARD_HEIGHT + 1, 0xFF3498DB.toInt())
                guiGraphics.fill(cardX + CARD_WIDTH - 1, cardY - 1, cardX + CARD_WIDTH + 1, cardY + CARD_HEIGHT + 1, 0xFF3498DB.toInt())
            } else if (isHovered) {
                guiGraphics.fill(cardX, cardY, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, 0x22FFFFFF)
            }

            val dexLabel = String.format("#%03d", item.nationalDexNumber)
            guiGraphics.drawString(font, dexLabel, cardX + 3, cardY + 3, 0xFF888E9B.toInt(), false)

            when (item.status) {
                PokedexStatus.CAUGHT -> guiGraphics.drawString(font, "✔", cardX + CARD_WIDTH - 9, cardY + 3, 0xFF2ECC71.toInt(), false)
                PokedexStatus.ENCOUNTERED -> guiGraphics.drawString(font, "👁", cardX + CARD_WIDTH - 11, cardY + 3, 0xFFE67E22.toInt(), false)
                PokedexStatus.MISSING -> guiGraphics.drawString(font, "✕", cardX + CARD_WIDTH - 9, cardY + 3, 0xFF5A6070.toInt(), false)
            }

            val stack = getItemStack(item.species, null)
            if (!stack.isEmpty) {
                val spriteX = cardX + (CARD_WIDTH - 16) / 2
                val spriteY = cardY + 14
                guiGraphics.renderItem(stack, spriteX, spriteY)
            }

            val rawName = item.species.translatedName.string
            val shortName = if (rawName.length > 6) rawName.substring(0, 5) + "…" else rawName
            val nameColor = if (item.isCaught) 0xFFE0E0E0.toInt() else if (item.isEncountered) 0xFFD4AC0D.toInt() else 0xFF7A8090.toInt()
            guiGraphics.drawCenteredString(font, shortName, cardX + CARD_WIDTH / 2, cardY + CARD_HEIGHT - 11, nameColor)

            if (item.ownedCount > 0) {
                val countText = "x${item.ownedCount}"
                guiGraphics.fill(cardX + 2, cardY + CARD_HEIGHT - 10, cardX + 3 + font.width(countText), cardY + CARD_HEIGHT - 2, 0xCC113311.toInt())
                guiGraphics.drawString(font, countText, cardX + 3, cardY + CARD_HEIGHT - 10, 0xFF55FF55.toInt(), false)
            }
        }

        hoveredPokemon?.let { item ->
            val tooltip = mutableListOf<Component>()
            val dexPrefix = String.format("#%04d ", item.nationalDexNumber)
            val titleComp = Component.literal(dexPrefix).withStyle(ChatFormatting.GOLD)
                .append(item.species.translatedName.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE))
            tooltip.add(titleComp)

            val typeDesc = item.species.standardForm.primaryType.displayName.copy()
            item.species.standardForm.secondaryType?.let { sec ->
                typeDesc.append(Component.literal(" / ")).append(sec.displayName)
            }
            tooltip.add(Component.literal("Types : ").withStyle(ChatFormatting.DARK_GRAY).append(typeDesc))

            val statusText = when (item.status) {
                PokedexStatus.CAUGHT -> Component.literal("✔ Capturé / Possédé").withStyle(ChatFormatting.GREEN)
                PokedexStatus.ENCOUNTERED -> Component.literal("👁 Rencontré (non capturé)").withStyle(ChatFormatting.GOLD)
                PokedexStatus.MISSING -> Component.literal("❌ Non découvert").withStyle(ChatFormatting.RED)
            }
            tooltip.add(statusText)

            if (item.ownedCount > 0) {
                tooltip.add(Component.literal("Dans vos réserves (${item.ownedCount}) :").withStyle(ChatFormatting.AQUA))
                for (info in item.ownedInstances.take(4)) {
                    tooltip.add(Component.literal(" • ${info.location} (Niv. ${info.level})").withStyle(ChatFormatting.GRAY))
                }
            }
            guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY)
        }
    }

    private fun renderEvolutionsTab(
        guiGraphics: GuiGraphics,
        startX: Int,
        startY: Int,
        usableW: Int,
        usableH: Int,
        mouseX: Int,
        mouseY: Int,
        goals: List<EvolutionGoal>,
        selectedGoal: EvolutionGoal?,
        font: Font
    ) {
        val scrolledY = startY - scrollOffset.toInt()

        if (goals.isEmpty()) {
            val emptyMsg = "Aucune évolution trouvée pour cette catégorie."
            guiGraphics.drawCenteredString(font, emptyMsg, startX + usableW / 2, startY + 40, 0xFF888888.toInt())
            return
        }

        var hoveredGoal: EvolutionGoal? = null

        for ((index, goal) in goals.withIndex()) {
            val cardY = scrolledY + index * (EVO_CARD_HEIGHT + 4)
            if (cardY + EVO_CARD_HEIGHT < startY || cardY > startY + usableH) {
                continue
            }

            val cardW = usableW - 6
            val isSelected = (selectedGoal == goal)
            val isHovered = (mouseX >= startX && mouseX < startX + cardW && mouseY >= cardY && mouseY < cardY + EVO_CARD_HEIGHT)
            if (isHovered && mouseY in startY until startY + usableH) {
                hoveredGoal = goal
            }

            val (bgColor, borderColor, badgeText, badgeColor) = when (goal.feasibility) {
                EvolutionFeasibility.READY_TO_EVOLVE -> Quad(0x44143D1A, 0xFF2ECC71.toInt(), "⭐ PRÊT À ÉVOLUER !", 0xFF2ECC71.toInt())
                EvolutionFeasibility.CAN_EVOLVE_WITH_PC -> Quad(0x443D3014, 0xFFE67E22.toInt(), "⏳ EN STOCK DANS LE PC", 0xFFE67E22.toInt())
                EvolutionFeasibility.MISSING_BASE_POKEMON -> Quad(0x331C1E26, 0xFF2E3342.toInt(), "POKÉMON DE BASE REQUIS", 0xFF7D8394.toInt())
            }

            guiGraphics.fill(startX, cardY, startX + cardW, cardY + EVO_CARD_HEIGHT, bgColor)
            guiGraphics.fill(startX, cardY, startX + cardW, cardY + 1, borderColor)
            guiGraphics.fill(startX, cardY + EVO_CARD_HEIGHT - 1, startX + cardW, cardY + EVO_CARD_HEIGHT, borderColor)
            guiGraphics.fill(startX, cardY, startX + 1, cardY + EVO_CARD_HEIGHT, borderColor)
            guiGraphics.fill(startX + cardW - 1, cardY, startX + cardW, cardY + EVO_CARD_HEIGHT, borderColor)

            if (isSelected) {
                guiGraphics.fill(startX, cardY, startX + 3, cardY + EVO_CARD_HEIGHT, 0xFF3498DB.toInt())
            }

            // 1. Pokémon Source
            val srcStack = getItemStack(goal.sourceSpecies, goal.sourceForm)
            if (!srcStack.isEmpty) {
                guiGraphics.renderItem(srcStack, startX + 6, cardY + 8)
            }
            val srcName = goal.sourceSpecies.translatedName.string
            val formNameStr = if (goal.sourceForm != goal.sourceSpecies.standardForm && goal.sourceForm.name.isNotBlank() && goal.sourceForm.name != "normal") {
                " (${goal.sourceForm.name.replaceFirstChar(Char::titlecase)})"
            } else ""
            val genderBadge = when (goal.requiredGender) {
                Gender.FEMALE -> " §d♀§r"
                Gender.MALE -> " §9♂§r"
                else -> ""
            }
            guiGraphics.drawString(font, srcName + formNameStr + genderBadge, startX + 26, cardY + 8, 0xFFE0E0E0.toInt(), false)

            val locText = if (!goal.hasFormMatch) {
                val fName = goal.sourceForm.name.replaceFirstChar(Char::titlecase)
                "§c✕ Mauvaise forme (0 $fName)§r"
            } else if (!goal.hasGenderMatch) {
                "§c✕ Mauvais sexe (0/${goal.ownedSourcePokemons.size})§r"
            } else if (goal.hasSourceInPC) {
                "§a✔ ${goal.sourceLocationSummary}§r"
            } else {
                "§7✕ Non possédé§r"
            }
            guiGraphics.drawString(font, locText, startX + 26, cardY + 20, 0xFFAAAAAA.toInt(), false)

            // 2. Flèche d'évolution
            val arrowX = startX + 155
            guiGraphics.drawString(font, "➔", arrowX, cardY + 14, 0xFF54A0FF.toInt(), false)

            var reqItemX = arrowX + 18
            if (goal.requiredItems.isNotEmpty()) {
                for (reqStack in goal.requiredItems.take(2)) {
                    guiGraphics.renderItem(reqStack, reqItemX, cardY + 8)
                    val hasThis = !goal.missingItems.any { it.item == reqStack.item }
                    val checkMark = if (hasThis) "§a✔§r" else "§c✕§r"
                    guiGraphics.drawString(font, checkMark, reqItemX + 13, cardY + 22, 0xFFFFFFFF.toInt(), false)
                    reqItemX += 24
                }
            }

            val moveBadge = if (goal.requiredMove != null) {
                val mvName = Moves.getByName(goal.requiredMove)?.displayName?.string ?: goal.requiredMove
                when (goal.moveStatus) {
                    MoveRequirementStatus.KNOWN -> "§a⚡ $mvName§r"
                    MoveRequirementStatus.RELEARNABLE -> "§a⚡ $mvName (Menu)§r"
                    MoveRequirementStatus.TM_IN_INVENTORY -> "§e⚡ CT $mvName (Sac)§r"
                    MoveRequirementStatus.TM_MISSING -> "§c⚡ CT $mvName (Manquante)§r"
                    MoveRequirementStatus.LEVEL_UP -> "§6⚡ $mvName (Niv. ${goal.moveLearnLevel})§r"
                    else -> "§c⚡ $mvName§r"
                }
            } else null

            val specialActionBadge = if (goal.stepsRequired != null) {
                "§b🚶 ${goal.stepsRequired} pas§r"
            } else if (goal.useMoveCount != null && goal.requiredMove != null) {
                val mvName = Moves.getByName(goal.requiredMove)?.displayName?.string ?: goal.requiredMove
                "§6⚔ $mvName (${goal.useMoveCount}x)§r"
            } else null

            val conditionStr = moveBadge ?: specialActionBadge ?: goal.requirementsSummary
            val shortCond = if (conditionStr.length > 34) conditionStr.substring(0, 32) + "…" else conditionStr
            guiGraphics.drawString(font, shortCond, reqItemX + 2, cardY + 10, 0xFFCCCCCC.toInt(), false)

            val levelSummary = if (goal.minLevel != null) {
                if (goal.isLevelMet) "§aNiv. ${goal.minLevel} atteint§r" else "§cNiv. ${goal.minLevel} requis§r"
            } else if (goal.requiredGender != null) {
                if (goal.hasGenderMatch) "§aSexe requis validé§r" else "§cSpécimen ${if (goal.requiredGender == Gender.FEMALE) "Femelle ♀" else "Mâle ♂"} requis§r"
            } else {
                goal.recipe.evolutionVariant
            }
            guiGraphics.drawString(font, levelSummary, reqItemX + 2, cardY + 22, 0xFFAAAAAA.toInt(), false)

            // 3. Pokémon Cible
            val tgtX = startX + cardW - 130
            val tgtStack = getItemStack(goal.targetSpecies, goal.targetForm)
            if (!tgtStack.isEmpty) {
                guiGraphics.renderItem(tgtStack, tgtX, cardY + 8)
            }
            val tgtName = goal.targetSpecies.translatedName.string
            guiGraphics.drawString(font, tgtName, tgtX + 20, cardY + 8, 0xFFFFEAA7.toInt(), false)
            val tgtDex = String.format("#%03d (Manquant)", goal.targetSpecies.nationalPokedexNumber)
            guiGraphics.drawString(font, tgtDex, tgtX + 20, cardY + 20, 0xFFE74C3C.toInt(), false)

            val badgeX = startX + cardW - font.width(badgeText) - 6
            guiGraphics.drawString(font, badgeText, badgeX, cardY + 34, badgeColor, false)
        }

        hoveredGoal?.let { goal ->
            renderGoalTooltip(guiGraphics, goal, mouseX, mouseY, font)
        }
    }

    private fun renderGoalTooltip(guiGraphics: GuiGraphics, goal: EvolutionGoal, mouseX: Int, mouseY: Int, font: Font) {
        val tooltip = mutableListOf<Component>()
        tooltip.add(
            Component.literal("Évolution : ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(goal.sourceSpecies.translatedName.string).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" ➔ ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(goal.targetSpecies.translatedName.string).withStyle(ChatFormatting.YELLOW))
        )

        val feasText = when (goal.feasibility) {
            EvolutionFeasibility.READY_TO_EVOLVE -> "⭐ Tout est prêt ! Évolution faisable dès maintenant."
            EvolutionFeasibility.CAN_EVOLVE_WITH_PC -> {
                if (!goal.hasFormMatch) "⏳ Pokémon présent mais pas sous la forme requise !"
                else if (!goal.hasGenderMatch) "⏳ Pokémon présent mais aucun spécimen du sexe requis !"
                else if (goal.moveStatus == MoveRequirementStatus.TM_MISSING) "⏳ Pokémon présent, mais la CT requise est manquante."
                else "⏳ Pokémon présent dans votre PC/équipe, prérequis manquants."
            }
            EvolutionFeasibility.MISSING_BASE_POKEMON -> {
                if (!goal.hasFormMatch) {
                    val fName = goal.sourceForm.name.replaceFirstChar(Char::titlecase)
                    "❌ Vous devez capturer la forme spécifique : $fName."
                } else {
                    "❌ Vous devez d'abord capturer le Pokémon de base."
                }
            }
        }
        tooltip.add(Component.literal(feasText).withStyle(if (goal.feasibility == EvolutionFeasibility.READY_TO_EVOLVE) ChatFormatting.GREEN else ChatFormatting.GOLD))

        if (goal.sourceForm != goal.sourceSpecies.standardForm && goal.sourceForm.name.isNotBlank() && goal.sourceForm.name != "normal") {
            tooltip.add(Component.empty())
            val fName = goal.sourceForm.name.replaceFirstChar(Char::titlecase)
            if (goal.hasFormMatch) {
                tooltip.add(Component.literal("Forme requise : $fName (✔ Spécimen valide en stock)").withStyle(ChatFormatting.GREEN))
            } else {
                tooltip.add(Component.literal("Forme requise : $fName (✕ Aucun spécimen de cette forme en stock !)").withStyle(ChatFormatting.RED))
            }
        }

        if (goal.requiredGender != null) {
            tooltip.add(Component.empty())
            val gName = if (goal.requiredGender == Gender.FEMALE) "Femelle ♀" else "Mâle ♂"
            if (goal.hasGenderMatch) {
                tooltip.add(Component.literal("Sexe requis : $gName (✔ Spécimen valide en stock)").withStyle(ChatFormatting.GREEN))
            } else {
                tooltip.add(Component.literal("Sexe requis : $gName (✕ Aucun spécimen de ce sexe en stock !)").withStyle(ChatFormatting.RED))
            }
        }

        if (goal.requiredMove != null) {
            tooltip.add(Component.empty())
            val mvName = Moves.getByName(goal.requiredMove)?.displayName?.string ?: goal.requiredMove
            tooltip.add(Component.literal("Capacité requise : ").withStyle(ChatFormatting.GRAY).append(Component.literal(mvName).withStyle(ChatFormatting.WHITE)))
            val moveExpl = when (goal.moveStatus) {
                MoveRequirementStatus.KNOWN -> Component.literal(" ✔ Déjà apprise par votre Pokémon").withStyle(ChatFormatting.GREEN)
                MoveRequirementStatus.RELEARNABLE -> Component.literal(" ✔ Réapprenable immédiatement (menu des capacités du Pokémon)").withStyle(ChatFormatting.GREEN)
                MoveRequirementStatus.TM_IN_INVENTORY -> Component.literal(" ✔ CT possédée dans votre inventaire (prêt à apprendre)").withStyle(ChatFormatting.YELLOW)
                MoveRequirementStatus.TM_MISSING -> Component.literal(" ✕ CT manquante (vous devez trouver ou fabriquer la CT : $mvName)").withStyle(ChatFormatting.RED)
                MoveRequirementStatus.LEVEL_UP -> Component.literal(" ⏳ S'apprend par montée de niveau (Niveau ${goal.moveLearnLevel})").withStyle(ChatFormatting.GOLD)
                else -> Component.literal(" ✕ Capacité non apprise").withStyle(ChatFormatting.RED)
            }
            tooltip.add(moveExpl)
        } else if (goal.requiredMoveType != null) {
            tooltip.add(Component.empty())
            tooltip.add(Component.literal("Capacité requise de type : ").withStyle(ChatFormatting.GRAY).append(Component.literal(goal.requiredMoveType.replaceFirstChar { it.uppercase() }).withStyle(ChatFormatting.LIGHT_PURPLE)))
            if (goal.moveStatus == MoveRequirementStatus.KNOWN) {
                tooltip.add(Component.literal(" ✔ Capacité du type requis déjà connue").withStyle(ChatFormatting.GREEN))
            } else {
                tooltip.add(Component.literal(" ✕ Aucune capacité de ce type connue").withStyle(ChatFormatting.RED))
            }
        }

        if (goal.specialActionSummary != null) {
            tooltip.add(Component.empty())
            tooltip.add(Component.literal("Actions requises :").withStyle(ChatFormatting.GRAY))
            if (goal.stepsRequired != null) {
                tooltip.add(Component.literal(" • 🚶 Marcher ${goal.stepsRequired} pas avec le Pokémon sorti").withStyle(ChatFormatting.AQUA))
            }
            if (goal.useMoveCount != null && goal.requiredMove != null) {
                val mvName = Moves.getByName(goal.requiredMove)?.displayName?.string ?: goal.requiredMove
                tooltip.add(Component.literal(" • ⚔ Utiliser la capacité $mvName ${goal.useMoveCount} fois en combat").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.criticalHits != null) {
                tooltip.add(Component.literal(" • 🎯 Infliger ${goal.recipe.criticalHits} coups critiques en un seul combat").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.damageTaken != null) {
                tooltip.add(Component.literal(" • 🛡 Subir ≥ ${goal.recipe.damageTaken} dégâts sans tomber K.O.").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.recoilDamage != null) {
                tooltip.add(Component.literal(" • 💥 Subir ≥ ${goal.recipe.recoilDamage} dégâts de contrecoup").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.defeatAmount != null) {
                tooltip.add(Component.literal(" • ⚔ Vaincre ${goal.recipe.defeatAmount} ${goal.recipe.defeatTarget ?: "adversaires"}").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.partyMember != null) {
                tooltip.add(Component.literal(" • 👥 Avoir ${goal.recipe.partyMember} dans l'équipe").withStyle(ChatFormatting.AQUA))
            }
            if (goal.recipe.moonPhase != null) {
                tooltip.add(Component.literal(" • 🌙 Phase lunaire requise : ${goal.recipe.moonPhase}").withStyle(ChatFormatting.AQUA))
            }
        }

        tooltip.add(Component.empty())
        tooltip.add(Component.literal("État du Pokémon source :").withStyle(ChatFormatting.GRAY))
        if (goal.hasSourceInPC) {
            for (owned in goal.ownedSourcePokemons) {
                val gSym = if (owned.gender == Gender.FEMALE) "§d♀§r" else if (owned.gender == Gender.MALE) "§9♂§r" else "§7⚲§r"
                tooltip.add(Component.literal(" • ${owned.location} - Niv. ${owned.level} ($gSym) - Bonheur: ${owned.friendship}").withStyle(ChatFormatting.AQUA))
            }
        } else {
            tooltip.add(Component.literal(" • Aucun spécimen dans l'équipe ou le PC").withStyle(ChatFormatting.RED))
        }

        if (goal.requiredItems.isNotEmpty()) {
            tooltip.add(Component.empty())
            tooltip.add(Component.literal("Objets requis :").withStyle(ChatFormatting.GRAY))
            for (item in goal.requiredItems) {
                val has = !goal.missingItems.any { it.item == item.item }
                val itemLine = if (has) {
                    Component.literal(" ✔ ${item.hoverName.string} (Possédé dans l'inventaire)").withStyle(ChatFormatting.GREEN)
                } else {
                    Component.literal(" ✕ ${item.hoverName.string} (Manquant)").withStyle(ChatFormatting.RED)
                }
                tooltip.add(itemLine)
            }
        }

        if (goal.additionalConditions.isNotEmpty()) {
            tooltip.add(Component.empty())
            tooltip.add(Component.literal("Autres conditions :").withStyle(ChatFormatting.GRAY))
            for (cond in goal.additionalConditions) {
                tooltip.add(Component.literal(" • $cond").withStyle(ChatFormatting.DARK_AQUA))
            }
        }

        guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY)
    }

    private fun renderMissingOriginsTab(
        guiGraphics: GuiGraphics,
        startX: Int,
        startY: Int,
        usableW: Int,
        usableH: Int,
        mouseX: Int,
        mouseY: Int,
        origins: List<BreedingOriginGoal>,
        selectedTracked: TrackedPokemon?,
        font: Font
    ) {
        val scrolledY = startY - scrollOffset.toInt()

        if (origins.isEmpty()) {
            val emptyMsg = "Aucune origine ou reproduction manquante trouvée."
            guiGraphics.drawCenteredString(font, emptyMsg, startX + usableW / 2, startY + 40, 0xFF888888.toInt())
            return
        }

        var hoveredOrigin: BreedingOriginGoal? = null
        val cardH = 62
        val cardGap = 4
        val cardW = usableW - 6

        for ((index, goal) in origins.withIndex()) {
            val cardY = scrolledY + index * (cardH + cardGap)
            if (cardY + cardH < startY || cardY > startY + usableH) {
                continue
            }

            val isSelected = (selectedTracked?.species?.let { PokemonTrackingManager.isSameSpecies(it, goal.baseSpecies) } == true)
            val isHovered = (mouseX >= startX && mouseX < startX + cardW && mouseY >= cardY && mouseY < cardY + cardH)
            if (isHovered && mouseY in startY until startY + usableH) {
                hoveredOrigin = goal
            }

            val (bgColor, borderColor, statusBadgeText, statusBadgeColor) = if (goal.isReadyToBreed) {
                Quad(0x44143D1A, 0xFF2ECC71.toInt(), "✔ PRÊT EN PENSION", 0xFF2ECC71.toInt())
            } else {
                Quad(0x331C1E26, 0xFF2E3342.toInt(), "PARENT CAPTURÉ (À RÉCUPÉRER)", 0xFF7D8394.toInt())
            }

            guiGraphics.fill(startX, cardY, startX + cardW, cardY + cardH, bgColor)
            guiGraphics.fill(startX, cardY, startX + cardW, cardY + 1, borderColor)
            guiGraphics.fill(startX, cardY + cardH - 1, startX + cardW, cardY + cardH, borderColor)
            guiGraphics.fill(startX, cardY, startX + 1, cardY + cardH, borderColor)
            guiGraphics.fill(startX + cardW - 1, cardY, startX + cardW, cardY + cardH, borderColor)

            if (isSelected) {
                guiGraphics.fill(startX, cardY, startX + 3, cardY + cardH, 0xFF3498DB.toInt())
            }

            val baseStack = getItemStack(goal.baseSpecies, null)
            if (!baseStack.isEmpty) {
                guiGraphics.renderItem(baseStack, startX + 6, cardY + 8)
            }
            val baseName = goal.baseSpecies.translatedName.string
            val dexNum = String.format("#%03d", goal.baseSpecies.nationalPokedexNumber)
            guiGraphics.drawString(font, "$dexNum §f$baseName", startX + 26, cardY + 8, 0xFFFFFFFF.toInt(), false)

            val originBadge = if (goal.isMultipleOrigins) "§6[Multi-lignées] §r" else ""
            val baseStatusText = when (goal.targetStatus) {
                PokedexStatus.ENCOUNTERED -> "§e👁 Rencontré (Non possédé)"
                else -> "§c✕ Non découvert"
            }
            guiGraphics.drawString(font, originBadge + baseStatusText, startX + 26, cardY + 22, 0xFFAAAAAA.toInt(), false)

            val arrowX = startX + 135
            guiGraphics.drawString(font, "⮜", arrowX, cardY + 14, 0xFFE67E22.toInt(), false)

            val parentLabelX = arrowX + 16
            guiGraphics.drawString(font, "Parents compatibles :", parentLabelX, cardY + 8, 0xFFB0B0B0.toInt(), false)

            var parentSpriteX = parentLabelX
            for (ancestor in goal.candidateParents.take(4)) {
                val ancestorStack = getItemStack(ancestor, null)
                if (!ancestorStack.isEmpty) {
                    guiGraphics.renderItem(ancestorStack, parentSpriteX, cardY + 20)
                }
                val isOwnedInPc = goal.ownedParents.any { PokemonTrackingManager.isSameSpecies(it.pokemon.species, ancestor) }
                val check = if (isOwnedInPc) "§a✔" else "§7✔"
                guiGraphics.drawString(font, check, parentSpriteX + 13, cardY + 34, 0xFFFFFFFF.toInt(), false)
                parentSpriteX += 24
            }

            val rightInfoX = startX + cardW - 145
            guiGraphics.drawString(font, statusBadgeText, rightInfoX, cardY + 8, statusBadgeColor, false)

            if (goal.requiredIncense != null) {
                guiGraphics.drawString(font, "§d♨ Encens requis :", rightInfoX, cardY + 22, 0xFFE082FF.toInt(), false)
                val shortIncense = if (goal.requiredIncense.length > 20) goal.requiredIncense.substring(0, 18) + "…" else goal.requiredIncense
                guiGraphics.drawString(font, "§f$shortIncense", rightInfoX, cardY + 34, 0xFFFFFFFF.toInt(), false)
            } else {
                guiGraphics.drawString(font, "§b🏠 Pension / Pâturage", rightInfoX, cardY + 22, 0xFF54A0FF.toInt(), false)
                guiGraphics.drawString(font, "§7Métamorph ou Partenaire", rightInfoX, cardY + 34, 0xFF888888.toInt(), false)
            }

            val adviceText = if (goal.isReadyToBreed) {
                "§2💡 Vous possédez un parent ! Déposez-le en pension avec un Métamorph pour obtenir un œuf de $baseName."
            } else {
                "§8💡 Vous avez déjà capturé un parent par le passé. Récupérez-en un pour reproduire $baseName."
            }
            guiGraphics.drawString(font, adviceText, startX + 6, cardY + 48, 0xFF666666.toInt(), false)
        }

        hoveredOrigin?.let { goal ->
            val tooltip = mutableListOf<Component>()
            val titleComp = Component.literal("Origine & Reproduction : ").withStyle(ChatFormatting.GOLD)
                .append(goal.baseSpecies.translatedName.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE))
            tooltip.add(titleComp)

            if (goal.requiredIncense != null) {
                tooltip.add(Component.literal("Objet requis : ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(goal.requiredIncense).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC)))
                tooltip.add(Component.literal("L'un des parents doit tenir cet encens pour obtenir cette forme bébé.").withStyle(ChatFormatting.GRAY))
            } else {
                tooltip.add(Component.literal("Reproduction classique (aucun encens requis).").withStyle(ChatFormatting.AQUA))
            }

            tooltip.add(Component.literal("Parents capables de donner naissance à ce Pokémon :").withStyle(ChatFormatting.GRAY))
            for (p in goal.candidateParents) {
                val isOwned = goal.ownedParents.any { PokemonTrackingManager.isSameSpecies(it.pokemon.species, p) }
                val state = if (isOwned) "§a✔ Présent en équipe/PC" else "§7✔ Enregistré au Pokédex"
                tooltip.add(Component.literal(" • ${p.translatedName.string} ($state)"))
            }
            guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY)
        }
    }

    private fun renderFossilsTab(
        guiGraphics: GuiGraphics,
        startX: Int,
        startY: Int,
        usableW: Int,
        usableH: Int,
        mouseX: Int,
        mouseY: Int,
        fossilGoals: List<FossilGoal>,
        selectedTracked: TrackedPokemon?,
        font: Font
    ) {
        val scrolledY = startY - scrollOffset.toInt()

        if (fossilGoals.isEmpty()) {
            val emptyMsg = "Aucun fossile ne correspond à votre recherche."
            guiGraphics.drawCenteredString(font, emptyMsg, startX + usableW / 2, startY + 40, 0xFF888888.toInt())
            return
        }

        var hoveredFossil: FossilGoal? = null
        val cardH = 64
        val cardGap = 4
        val cardW = usableW - 6

        for ((index, goal) in fossilGoals.withIndex()) {
            val cardY = scrolledY + index * (cardH + cardGap)
            if (cardY + cardH < startY || cardY > startY + usableH) {
                continue
            }

            val isSelected = (selectedTracked?.species?.let { goal.targetSpecies != null && PokemonTrackingManager.isSameSpecies(it, goal.targetSpecies) } == true)
            val isHovered = (mouseX >= startX && mouseX < startX + cardW && mouseY >= cardY && mouseY < cardY + cardH)
            if (isHovered && mouseY in startY until startY + usableH) {
                hoveredFossil = goal
            }

            val (bgColor, borderColor, statusBadgeText, statusBadgeColor) = if (goal.isReadyToRevive) {
                Quad(0x44143D1A, 0xFF2ECC71.toInt(), "✔ PRÊT À RESSUSCITER", 0xFF2ECC71.toInt())
            } else if (goal.heldFossilCount > 0) {
                Quad(0x443D3214, 0xFFFFA502.toInt(), "FOSSILE PARTIEL", 0xFFFFA502.toInt())
            } else {
                Quad(0x331C1E26, 0xFF2E3342.toInt(), "FOUILLES REQUISES", 0xFF7D8394.toInt())
            }

            guiGraphics.fill(startX, cardY, startX + cardW, cardY + cardH, bgColor)
            guiGraphics.fill(startX, cardY, startX + cardW, cardY + 1, borderColor)
            guiGraphics.fill(startX, cardY + cardH - 1, startX + cardW, cardY + cardH, borderColor)
            guiGraphics.fill(startX, cardY, startX + 1, cardY + cardH, borderColor)
            guiGraphics.fill(startX + cardW - 1, cardY, startX + cardW, cardY + cardH, borderColor)

            if (isSelected) {
                guiGraphics.fill(startX, cardY, startX + 3, cardY + cardH, 0xFF3498DB.toInt())
            }

            var fossilItemX = startX + 6
            for (fStack in goal.recipe.requiredFossilStacks) {
                if (!fStack.isEmpty) {
                    guiGraphics.renderItem(fStack, fossilItemX, cardY + 8)
                    guiGraphics.renderItemDecorations(font, fStack, fossilItemX, cardY + 8)
                }
                fossilItemX += 18
            }

            val fossilItemTextX = startX + 6 + max(20, goal.recipe.requiredFossilStacks.size * 18)
            val shortFossilTitle = if (goal.recipe.fossilDisplayName.length > 18) goal.recipe.fossilDisplayName.substring(0, 16) + "…" else goal.recipe.fossilDisplayName
            guiGraphics.drawString(font, "§e$shortFossilTitle", fossilItemTextX, cardY + 8, 0xFFFFFFFF.toInt(), false)

            val invText = if (goal.heldFossilCount > 0) "§a✔ En stock (x${goal.heldFossilCount})§r" else "§7✕ Non possédé§r"
            guiGraphics.drawString(font, invText, fossilItemTextX, cardY + 22, 0xFFAAAAAA.toInt(), false)

            val arrowX = startX + 148
            guiGraphics.drawString(font, "➔", arrowX, cardY + 14, 0xFFE67E22.toInt(), false)

            val targetSpriteX = arrowX + 16
            if (goal.targetSpecies != null) {
                val targetStack = getItemStack(goal.targetSpecies, null)
                if (!targetStack.isEmpty) {
                    guiGraphics.renderItem(targetStack, targetSpriteX, cardY + 8)
                }
                val dexNum = String.format("#%03d", goal.recipe.nationalDexNumber)
                val spName = goal.targetSpecies.translatedName.string
                val shortSpName = if (spName.length > 12) spName.substring(0, 10) + "…" else spName
                guiGraphics.drawString(font, "$dexNum §f$shortSpName", targetSpriteX + 20, cardY + 8, 0xFFFFFFFF.toInt(), false)

                val dexStatusText = when {
                    goal.isCaught -> "§a✔ Capturé au Dex"
                    goal.isEncountered -> "§e👁 Rencontré"
                    else -> "§c✕ Non découvert"
                }
                guiGraphics.drawString(font, dexStatusText, targetSpriteX + 20, cardY + 22, 0xFFAAAAAA.toInt(), false)
            }

            val rightInfoX = startX + cardW - 145
            guiGraphics.drawString(font, statusBadgeText, rightInfoX, cardY + 8, statusBadgeColor, false)

            val firstStruct = goal.recipe.associatedStructureDisplayNames.firstOrNull() ?: "Fouilles archéologiques"
            val shortStruct = if (firstStruct.length > 22) firstStruct.substring(0, 20) + "…" else firstStruct
            guiGraphics.drawString(font, "§6⛏ $shortStruct", rightInfoX, cardY + 22, 0xFFFFA502.toInt(), false)
            guiGraphics.drawString(font, "§b⚙ Analyseur + Cuve", rightInfoX, cardY + 36, 0xFF54A0FF.toInt(), false)
        }

        hoveredFossil?.let { goal ->
            val tooltip = mutableListOf<Component>()
            tooltip.add(Component.literal("Résurrection de Fossile : ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(goal.recipe.speciesDisplayName).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE)))
            tooltip.add(Component.literal("Fossile(s) requis : ${goal.recipe.fossilDisplayName}").withStyle(ChatFormatting.YELLOW))
            if (goal.recipe.associatedStructureDisplayNames.isNotEmpty()) {
                tooltip.add(Component.literal("Sites de fouilles (Pinceau sur sable/gravier suspect) :").withStyle(ChatFormatting.GRAY))
                for (sName in goal.recipe.associatedStructureDisplayNames.take(5)) {
                    tooltip.add(Component.literal(" • $sName").withStyle(ChatFormatting.AQUA))
                }
            }
            tooltip.add(Component.literal("Machine requise : Analyseur de fossiles + Cuve de restauration + Moniteur").withStyle(ChatFormatting.LIGHT_PURPLE))
            guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY)
        }
    }

    private fun renderIntegratedExpeditionsTab(
        guiGraphics: GuiGraphics,
        startX: Int,
        startY: Int,
        usableW: Int,
        usableH: Int,
        mouseX: Int,
        mouseY: Int,
        suggestions: List<ExpeditionSuggestion>,
        isLoading: Boolean,
        font: Font
    ) {
        if (isLoading) {
            guiGraphics.fill(startX, startY, startX + usableW, startY + usableH, 0xFF191C28.toInt())
            val loadingMsg = "§e⏳ Analyse des biomes et calcul des 5 expéditions..."
            guiGraphics.drawCenteredString(font, loadingMsg, startX + usableW / 2, startY + usableH / 2 - 4, 0xFFFFFFFF.toInt())
            return
        }

        if (suggestions.isEmpty()) {
            guiGraphics.fill(startX, startY, startX + usableW, startY + usableH, 0xFF191C28.toInt())
            guiGraphics.drawCenteredString(font, "§7Aucune expédition disponible. Explorez plus de territoires sur JourneyMap !", startX + usableW / 2, startY + usableH / 2 - 4, 0xFF888888.toInt())
            return
        }

        val cardH = 60
        val cardGap = 6
        val scrolledY = startY - scrollOffset.toInt()

        for ((index, sug) in suggestions.take(5).withIndex()) {
            val cardY = scrolledY + index * (cardH + cardGap)
            if (cardY + cardH >= startY && cardY <= startY + usableH) {
                PokemonExpeditionModalWidget.drawExpeditionSuggestionCard(guiGraphics, startX, cardY, usableW - 6, cardH, sug, mouseX, mouseY, font)
            }
        }
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        if (maxScroll <= 0) return

        guiGraphics.fill(x, y, x + w, y + h, 0x44000000)
        val thumbH = max(16, (h * (h / (h + maxScroll))).toInt())
        val thumbY = y + ((h - thumbH) * (scrollOffset / maxScroll)).toInt()

        val thumbColor = if (isDraggingScrollbar) 0xFF54A0FF.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(x, thumbY, x + w, thumbY + thumbH, thumbColor)
    }

    fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        data: LeftContentData,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (button != 0) return false

        val bounds = getLeftAreaBounds(screenWidth, screenHeight, data.mainTab == MainTab.EXPEDITIONS)
        val leftX = bounds[0]
        val topY = bounds[1]
        val areaW = bounds[2]
        val areaH = bounds[3]

        // 1. Clic sur la scrollbar gauche
        val scrollbarX = leftX + areaW - 8
        if (mouseX >= scrollbarX && mouseX <= leftX + areaW && mouseY >= topY && mouseY <= topY + areaH) {
            isDraggingScrollbar = true
            dragStartMouseY = mouseY
            dragStartScroll = scrollOffset
            return true
        }

        // 2. Clic dans la zone de contenu gauche
        if (mouseX >= leftX && mouseX <= leftX + areaW - 8 && mouseY >= topY && mouseY <= topY + areaH) {
            val scrolledY = topY + 4 - scrollOffset.toInt()

            when (data.mainTab) {
                MainTab.POKEDEX -> {
                    val usableW = areaW - 14
                    val cols = max(1, usableW / (CARD_WIDTH + CARD_GAP))
                    val col = ((mouseX - (leftX + 4)) / (CARD_WIDTH + CARD_GAP)).toInt()
                    val row = ((mouseY - scrolledY) / (CARD_HEIGHT + CARD_GAP)).toInt()
                    if (col in 0 until cols && row >= 0) {
                        val index = row * cols + col
                        if (index in data.filteredTracked.indices) {
                            onSelectTracked(data.filteredTracked[index])
                            return true
                        }
                    }
                }
                MainTab.EVOLUTIONS -> {
                    when (data.evolutionSubTab) {
                        EvolutionSubTab.PENDING_EVOLUTIONS -> {
                            val index = ((mouseY - scrolledY) / (EVO_CARD_HEIGHT + 4)).toInt()
                            if (index in data.filteredGoals.indices) {
                                val goal = data.filteredGoals[index]
                                onSelectGoal?.invoke(goal)
                                val tracked = data.allTracked.find { PokemonTrackingManager.isSameSpecies(it.species, goal.targetSpecies) }
                                if (tracked != null) {
                                    onSelectTracked(tracked)
                                }
                                return true
                            }
                        }
                        EvolutionSubTab.ITEM_EVOLUTIONS -> {
                            val index = ((mouseY - scrolledY) / (EVO_CARD_HEIGHT + 4)).toInt()
                            if (index in data.filteredItemGoals.indices) {
                                val goal = data.filteredItemGoals[index]
                                onSelectGoal?.invoke(goal)
                                val tracked = data.allTracked.find { PokemonTrackingManager.isSameSpecies(it.species, goal.targetSpecies) }
                                if (tracked != null) {
                                    onSelectTracked(tracked)
                                }
                                return true
                            }
                        }
                        EvolutionSubTab.MISSING_ORIGINS -> {
                            val cardH = 62
                            val cardGap = 4
                            val index = ((mouseY - scrolledY) / (cardH + cardGap)).toInt()
                            if (index in data.filteredBreedingOrigins.indices) {
                                val goal = data.filteredBreedingOrigins[index]
                                val tracked = data.allTracked.find { PokemonTrackingManager.isSameSpecies(it.species, goal.baseSpecies) }
                                if (tracked != null) {
                                    onSelectTracked(tracked)
                                }
                                return true
                            }
                        }
                        EvolutionSubTab.FOSSILS -> {
                            val cardH = 64
                            val cardGap = 4
                            val index = ((mouseY - scrolledY) / (cardH + cardGap)).toInt()
                            if (index in data.filteredFossilGoals.indices) {
                                val goal = data.filteredFossilGoals[index]
                                val target = goal.targetSpecies
                                if (target != null) {
                                    val tracked = data.allTracked.find { PokemonTrackingManager.isSameSpecies(it.species, target) }
                                    if (tracked != null) {
                                        onSelectTracked(tracked)
                                    }
                                    return true
                                }
                            }
                        }
                        EvolutionSubTab.LEGENDARIES -> {
                            val usableW = areaW - 14
                            val cols = max(1, usableW / (CARD_WIDTH + CARD_GAP))
                            val col = ((mouseX - (leftX + 4)) / (CARD_WIDTH + CARD_GAP)).toInt()
                            val row = ((mouseY - scrolledY) / (CARD_HEIGHT + CARD_GAP)).toInt()
                            if (col in 0 until cols && row >= 0) {
                                val index = row * cols + col
                                if (index in data.filteredLegendaries.indices) {
                                    onSelectTracked(data.filteredLegendaries[index])
                                    return true
                                }
                            }
                        }
                    }
                }
                MainTab.EXPEDITIONS -> {
                    if (!data.isExpeditionLoading && data.expeditionSuggestions.isNotEmpty()) {
                        val cardH = 60
                        val cardGap = 6
                        val cardW = areaW - 14 - 6

                        for ((index, sug) in data.expeditionSuggestions.take(5).withIndex()) {
                            val cardY = scrolledY + index * (cardH + cardGap)
                            if (cardY + cardH < topY || cardY > topY + areaH) continue

                            val partBtnW = 84
                            val partBtnH = 18
                            val partBtnX = leftX + 4 + cardW - partBtnW - 6
                            val partBtnY = cardY + 7

                            if (mouseX >= partBtnX && mouseX <= partBtnX + partBtnW && mouseY >= partBtnY && mouseY <= partBtnY + partBtnH) {
                                ExpeditionSuggestionEngine.acceptSuggestion(sug)
                                ExpeditionMapOverlay.showZoneOverlay(sug.primaryZone)
                                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                                return true
                            }

                            val markBtnW = 60
                            val markBtnH = 16
                            val markBtnX = leftX + 4 + cardW - markBtnW - 6
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

                            if (mouseX >= leftX + 4 && mouseX <= leftX + 4 + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
                                val firstTarget = sug.targets.firstOrNull()?.speciesId
                                if (firstTarget != null) {
                                    val tracked = data.allTracked.find { it.species.resourceIdentifier == firstTarget }
                                    if (tracked != null) {
                                        onSelectTracked(tracked)
                                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                                        return true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return false
    }

    fun mouseDragged(mouseY: Double, screenWidth: Int, screenHeight: Int, isExpeditionTab: Boolean): Boolean {
        if (isDraggingScrollbar && maxScroll > 0) {
            val bounds = getLeftAreaBounds(screenWidth, screenHeight, isExpeditionTab)
            val areaH = bounds[3]
            val totalTrackH = areaH - 4
            val delta = mouseY - dragStartMouseY
            val scrollDelta = (delta / totalTrackH) * maxScroll
            scrollOffset = (dragStartScroll + scrollDelta).coerceIn(0.0, maxScroll)
            return true
        }
        return false
    }

    fun mouseReleased(): Boolean {
        val wasDragging = isDraggingScrollbar
        isDraggingScrollbar = false
        return wasDragging
    }

    fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollY: Double,
        screenWidth: Int,
        screenHeight: Int,
        isExpeditionTab: Boolean
    ): Boolean {
        val bounds = getLeftAreaBounds(screenWidth, screenHeight, isExpeditionTab)
        val leftX = bounds[0]
        val topY = bounds[1]
        val areaW = bounds[2]
        val areaH = bounds[3]
        if (mouseX >= leftX && mouseX <= leftX + areaW && mouseY >= topY && mouseY <= topY + areaH) {
            scrollOffset = (scrollOffset - scrollY * 24.0).coerceIn(0.0, maxScroll)
            return true
        }
        return false
    }
}
