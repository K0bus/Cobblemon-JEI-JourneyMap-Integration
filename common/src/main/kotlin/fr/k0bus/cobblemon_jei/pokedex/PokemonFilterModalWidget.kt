package fr.k0bus.cobblemon_jei.pokedex

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import org.lwjgl.glfw.GLFW
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Modale de filtrage avancée du Pokédex (Types, Raretés, Biomes, Générations, Statuts, Ordres de tri).
 */
class PokemonFilterModalWidget(
    val filterState: PokedexFilterState,
    private val getFilteredCount: () -> Int,
    private val getBiomeCounts: () -> Map<CobblemonBiomeFilter, PokedexFilterManager.FilterCount>,
    private val getRarityCounts: () -> Map<SpawnRarityFilter, PokedexFilterManager.FilterCount>,
    private val getGenCounts: () -> Map<GenerationFilter, PokedexFilterManager.FilterCount>,
    private val onFiltersChanged: () -> Unit,
    private val onCloseCallback: () -> Unit
) {
    var isOpen: Boolean = false
        private set

    var activeFilterCategory: FilterCategory = FilterCategory.TYPE
    private var biomeScrollOffset: Double = 0.0
    private var biomeMaxScroll: Double = 0.0
    private var isDraggingBiomeScrollbar: Boolean = false
    private var dragStartBiomeMouseY: Double = 0.0
    private var dragStartBiomeScroll: Double = 0.0
    private var activeModalTooltip: List<Component>? = null

    private var biomeSearchBox: EditBox? = null

    fun initSearchBox(font: Font) {
        val box = EditBox(font, 0, 0, 100, 16, Component.literal("Recherche de biome..."))
        box.setHint(Component.literal("§8Rechercher un biome...§r"))
        box.setResponder {
            biomeScrollOffset = 0.0
        }
        biomeSearchBox = box
    }

    fun open() {
        isOpen = true
        activeFilterCategory = FilterCategory.TYPE
        biomeScrollOffset = 0.0
        biomeSearchBox?.value = ""
        biomeSearchBox?.isFocused = false
    }

    fun close() {
        if (isOpen) {
            isOpen = false
            biomeSearchBox?.isFocused = false
            onCloseCallback()
        }
    }

    fun getBounds(screenWidth: Int, screenHeight: Int): IntArray {
        val modalW = min(480, screenWidth - 24)
        val modalH = min(320, screenHeight - 24)
        val modalX = (screenWidth - modalW) / 2
        val modalY = (screenHeight - modalH) / 2
        return intArrayOf(modalX, modalY, modalW, modalH)
    }

    private fun normalizeText(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, screenWidth: Int, screenHeight: Int, font: Font) {
        if (!isOpen) return

        activeModalTooltip = null

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

        val title = "§6§lFILTRES DU POKÉDEX§r"
        guiGraphics.drawString(font, title, modalX + 8, modalY + 7, 0xFFFFFFFF.toInt(), false)

        val activeCount = filterState.activeCount()
        if (filterState.isActive()) {
            val badge = "§a($activeCount actif${if (activeCount > 1) "s" else ""})§r"
            guiGraphics.drawString(font, badge, modalX + 8 + font.width("FILTRES DU POKÉDEX ") + 4, modalY + 7, 0xFFFFFFFF.toInt(), false)
        }

        // Bouton Fermer [✕] en haut à droite
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        val isCloseHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14
        val closeColor = if (isCloseHovered) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
        guiGraphics.drawString(font, "§l✕§r", closeBtnX + 3, closeBtnY + 3, closeColor, false)

        // 3. Barre latérale gauche (Catégories)
        val sideX = modalX + 5
        val sideY = modalY + 24
        val sideW = 96
        val sideH = modalH - 48
        val categories = FilterCategory.values()
        val catH = sideH / categories.size

        guiGraphics.fill(sideX, sideY, sideX + sideW, sideY + sideH, 0xFF10131B.toInt())
        guiGraphics.fill(sideX + sideW - 1, sideY, sideX + sideW, sideY + sideH, 0xFF222736.toInt())

        for ((idx, cat) in categories.withIndex()) {
            val cy = sideY + idx * catH
            val ch = if (idx == categories.size - 1) (sideY + sideH - cy) else catH
            val isSel = (cat == activeFilterCategory)
            val isHovered = mouseX >= sideX && mouseX <= sideX + sideW && mouseY >= cy && mouseY <= cy + ch

            val isCatActive = when (cat) {
                FilterCategory.TYPE -> filterState.selectedTypes.isNotEmpty()
                FilterCategory.RARITY -> filterState.selectedRarity != SpawnRarityFilter.ALL
                FilterCategory.BIOME -> filterState.selectedBiome != CobblemonBiomeFilter.ALL
                FilterCategory.GEN -> filterState.selectedGen != GenerationFilter.ALL
                FilterCategory.STATUS -> filterState.selectedStatus != StatusFilter.ALL || filterState.selectedEvolution != EvolutionFilter.ALL
                FilterCategory.SORT -> filterState.selectedSort != PokedexSortOrder.DEX_ASC
            }

            if (isSel) {
                guiGraphics.fill(sideX, cy, sideX + sideW, cy + ch, 0xFF222A3B.toInt())
                guiGraphics.fill(sideX, cy, sideX + 3, cy + ch, 0xFFFFA502.toInt())
            } else if (isHovered) {
                guiGraphics.fill(sideX, cy, sideX + sideW, cy + ch, 0xFF181D29.toInt())
            }

            val labelColor = if (isSel) 0xFFFFFFFF.toInt() else if (isHovered) 0xFFCCCCCC.toInt() else 0xFF888888.toInt()
            val textY = cy + (ch - 8) / 2
            val textStr = "${cat.icon} ${cat.label}" + if (isCatActive) " §a●§r" else ""
            guiGraphics.drawString(font, textStr, sideX + 6, textY, labelColor, false)
        }

        // 4. Panneau de contenu à droite
        val paneX = sideX + sideW + 6
        val paneY = modalY + 24
        val paneW = modalX + modalW - paneX - 5
        val paneH = sideH

        guiGraphics.fill(paneX, paneY, paneX + paneW, paneY + paneH, 0xFF0D0F17.toInt())
        guiGraphics.fill(paneX, paneY, paneX + paneW, paneY + 1, 0xFF1E2331.toInt())
        guiGraphics.fill(paneX, paneY + paneH - 1, paneX + paneW, paneY + paneH, 0xFF1E2331.toInt())
        guiGraphics.fill(paneX, paneY, paneX + 1, paneY + paneH, 0xFF1E2331.toInt())
        guiGraphics.fill(paneX + paneW - 1, paneY, paneX + paneW, paneY + paneH, 0xFF1E2331.toInt())

        when (activeFilterCategory) {
            FilterCategory.TYPE -> renderTypeFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH, font)
            FilterCategory.RARITY -> renderRarityFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH, font)
            FilterCategory.BIOME -> renderBiomeFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH, font)
            FilterCategory.GEN -> renderGenFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH, font)
            FilterCategory.STATUS -> renderStatusFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH, font)
            FilterCategory.SORT -> renderSortFilterPane(guiGraphics, mouseX, mouseY, paneX, paneY, paneW, paneH, font)
        }

        // 5. Barre inférieure (Actions & Compteur)
        val botY = modalY + modalH - 22
        guiGraphics.fill(modalX, botY, modalX + modalW, modalY + modalH, 0xFF161924.toInt())
        guiGraphics.fill(modalX, botY, modalX + modalW, botY + 1, 0xFF282D3C.toInt())

        // Bouton Réinitialiser
        val resetBtnX = modalX + 6
        val resetBtnY = botY + 3
        val resetBtnW = 84
        val resetBtnH = 15
        val isResetHov = mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= resetBtnY && mouseY <= resetBtnY + resetBtnH
        val resetBg = if (isResetHov) 0xFF4A1A1A.toInt() else 0xFF2A1515.toInt()
        val resetBorder = if (isResetHov) 0xFFFF5555.toInt() else 0xFF882222.toInt()
        guiGraphics.fill(resetBtnX, resetBtnY, resetBtnX + resetBtnW, resetBtnY + resetBtnH, resetBg)
        guiGraphics.fill(resetBtnX, resetBtnY, resetBtnX + resetBtnW, resetBtnY + 1, resetBorder)
        guiGraphics.fill(resetBtnX, resetBtnY + resetBtnH - 1, resetBtnX + resetBtnW, resetBtnY + resetBtnH, resetBorder)
        guiGraphics.fill(resetBtnX, resetBtnY, resetBtnX + 1, resetBtnY + resetBtnH, resetBorder)
        guiGraphics.fill(resetBtnX + resetBtnW - 1, resetBtnY, resetBtnX + resetBtnW, resetBtnY + resetBtnH, resetBorder)
        guiGraphics.drawCenteredString(font, "§c⟲ Tout effacer§r", resetBtnX + resetBtnW / 2, resetBtnY + 4, 0xFFFFFFFF.toInt())

        // Compteur de correspondances
        val countText = "§e${getFilteredCount()}§r §7correspondance(s)§r"
        guiGraphics.drawCenteredString(font, countText, modalX + modalW / 2 + 10, botY + 7, 0xFFCCCCCC.toInt())

        // Bouton Appliquer & Fermer
        val closeApplyW = 104
        val closeApplyX = modalX + modalW - closeApplyW - 6
        val closeApplyY = botY + 3
        val closeApplyH = 15
        val isApplyHov = mouseX >= closeApplyX && mouseX <= closeApplyX + closeApplyW && mouseY >= closeApplyY && mouseY <= closeApplyY + closeApplyH
        val applyBg = if (isApplyHov) 0xFF1E3A28.toInt() else 0xFF14261B.toInt()
        val applyBorder = if (isApplyHov) 0xFF2ECC71.toInt() else 0xFF1E824C.toInt()
        guiGraphics.fill(closeApplyX, closeApplyY, closeApplyX + closeApplyW, closeApplyY + closeApplyH, applyBg)
        guiGraphics.fill(closeApplyX, closeApplyY, closeApplyX + closeApplyW, closeApplyY + 1, applyBorder)
        guiGraphics.fill(closeApplyX, closeApplyY + closeApplyH - 1, closeApplyX + closeApplyW, closeApplyY + closeApplyH, applyBorder)
        guiGraphics.fill(closeApplyX, closeApplyY, closeApplyX + 1, closeApplyY + closeApplyH, applyBorder)
        guiGraphics.fill(closeApplyX + closeApplyW - 1, closeApplyY, closeApplyX + closeApplyW, closeApplyY + closeApplyH, applyBorder)
        guiGraphics.drawCenteredString(font, "§a✓ Appliquer§r", closeApplyX + closeApplyW / 2, closeApplyY + 4, 0xFFFFFFFF.toInt())

        if (activeModalTooltip != null) {
            guiGraphics.renderComponentTooltip(font, activeModalTooltip!!, mouseX, mouseY)
        }

        guiGraphics.flush()
        pose.popPose()
    }

    private fun renderTypeFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, font: Font) {
        val selectedCount = filterState.selectedTypes.size
        val subtitle = if (selectedCount == 0) {
            "§7Types élémentaires : §fTous acceptés§r"
        } else {
            "§7Types sélectionnés : §a$selectedCount type(s) (OU)§r"
        }
        guiGraphics.drawString(font, subtitle, x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        if (selectedCount > 0) {
            val clearBtnX = x + w - 58
            val clearBtnY = y + 4
            val isClearHov = mouseX >= clearBtnX && mouseX <= clearBtnX + 52 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12
            val clearCol = if (isClearHov) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
            guiGraphics.drawString(font, "§c[✕ Effacer]§r", clearBtnX, clearBtnY + 2, clearCol, false)
        }

        val allTypes = PokedexFilterManager.ALL_TYPES
        val colW = (w - 18) / 3
        val rowH = 16
        val startY = y + 18

        for ((idx, type) in allTypes.withIndex()) {
            val col = idx % 3
            val row = idx / 3
            val bx = x + 6 + col * (colW + 3)
            val by = startY + row * (rowH + 3)

            val isSel = filterState.selectedTypes.contains(type.id)
            val isHov = mouseX >= bx && mouseX <= bx + colW && mouseY >= by && mouseY <= by + rowH

            val bg = if (isSel) type.color else if (isHov) 0xFF2A3142.toInt() else 0xFF181D28.toInt()
            val border = if (isSel) 0xFFFFFFFF.toInt() else if (isHov) 0xFF4A5568.toInt() else 0xFF222736.toInt()

            guiGraphics.fill(bx, by, bx + colW, by + rowH, bg)
            guiGraphics.fill(bx, by, bx + colW, by + 1, border)
            guiGraphics.fill(bx, by + rowH - 1, bx + colW, by + rowH, border)
            guiGraphics.fill(bx, by, bx + 1, by + rowH, border)
            guiGraphics.fill(bx + colW - 1, by, bx + colW, by + rowH, border)

            val check = if (isSel) "✔ " else ""
            val name = "$check${type.displayName}"
            val textColor = if (isSel) 0xFFFFFFFF.toInt() else 0xFFCCCCCC.toInt()
            guiGraphics.drawCenteredString(font, name, bx + colW / 2, by + 4, textColor)
        }
    }

    private fun renderRarityFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, font: Font) {
        guiGraphics.drawString(font, "§7Niveau de rareté d'apparition sauvage Cobblemon :§r", x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        val rarities = SpawnRarityFilter.values()
        val rowH = 20
        val startY = y + 20
        val rarityCounts = getRarityCounts()

        for ((idx, opt) in rarities.withIndex()) {
            val ry = startY + idx * (rowH + 2)
            val isSel = (filterState.selectedRarity == opt)
            val isHov = mouseX >= x + 6 && mouseX <= x + w - 6 && mouseY >= ry && mouseY <= ry + rowH

            val bg = if (isSel) 0xFF232B3D.toInt() else if (isHov) 0xFF181D29.toInt() else 0xFF12151E.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(x + 6, ry, x + w - 6, ry + rowH, bg)
            guiGraphics.fill(x + 6, ry, x + w - 6, ry + 1, border)
            guiGraphics.fill(x + 6, ry + rowH - 1, x + w - 6, ry + rowH, border)
            guiGraphics.fill(x + 6, ry, x + 7, ry + rowH, border)
            guiGraphics.fill(x + w - 7, ry, x + w - 6, ry + rowH, border)

            val radio = if (isSel) "§6● " else "§7○ "
            val text = "$radio${opt.formatCode}${if (isSel) "§l${opt.displayName}§r" else opt.displayName}"
            guiGraphics.drawString(font, text, x + 12, ry + 6, 0xFFFFFFFF.toInt(), false)

            val counts = rarityCounts[opt] ?: PokedexFilterManager.FilterCount(0, 0)
            val missingColor = if (counts.missing > 0) "§c" else "§a"
            val countStr = "§7${counts.total} §8| $missingColor${counts.missing}§r"
            val countW = font.width(countStr)
            val countX = x + w - countW - 12
            guiGraphics.drawString(font, countStr, countX, ry + 6, 0xFFFFFFFF.toInt(), false)

            if (isHov) {
                activeModalTooltip = listOf(
                    Component.literal("${opt.formatCode}${opt.displayName}"),
                    Component.literal("§7Total : §f${counts.total} §7Pokémon"),
                    Component.literal(if (counts.missing > 0) "§cManquants : §f${counts.missing} §7à capturer" else "§a✔ Tous capturés (0 manquant)")
                )
            }
        }
    }

    private fun renderBiomeFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, font: Font) {
        val selectedName = filterState.selectedBiome.displayName
        guiGraphics.drawString(font, "§7Biome actif : §e$selectedName§r", x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        if (filterState.selectedBiome != CobblemonBiomeFilter.ALL) {
            val clearBtnX = x + w - 64
            val clearBtnY = y + 4
            val isClearHov = mouseX >= clearBtnX && mouseX <= clearBtnX + 58 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12
            val clearCol = if (isClearHov) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
            guiGraphics.drawString(font, "§c[✕ Réinit]§r", clearBtnX, clearBtnY + 2, clearCol, false)
        }

        // Barre de recherche de biome
        val searchBox = biomeSearchBox
        if (searchBox != null) {
            searchBox.setPosition(x + 6, y + 20)
            searchBox.width = w - 12
            searchBox.render(guiGraphics, mouseX, mouseY, 0f)

            if (searchBox.value.isNotEmpty()) {
                val clearSearchX = x + w - 18
                val clearSearchY = y + 24
                val isClearHov = mouseX >= clearSearchX - 2 && mouseX <= clearSearchX + 8 && mouseY >= clearSearchY - 2 && mouseY <= clearSearchY + 10
                val col = if (isClearHov) 0xFFFF5555.toInt() else 0xFF888888.toInt()
                guiGraphics.drawString(font, "✕", clearSearchX, clearSearchY, col, false)
            }
        }

        val normQuery = if (searchBox != null) normalizeText(searchBox.value) else ""
        val biomes = if (normQuery.isEmpty()) {
            CobblemonBiomeFilter.values().toList()
        } else {
            CobblemonBiomeFilter.values().filter { biome ->
                normalizeText(biome.displayName).contains(normQuery) ||
                biome.id.lowercase().contains(normQuery) ||
                biome.tagKeywords.any { normalizeText(it).contains(normQuery) }
            }
        }

        val listY = y + 40
        val listH = h - 44
        val rowH = 15

        if (biomes.isEmpty()) {
            val emptyMsg = "§7Aucun biome correspondant à \"${searchBox?.value ?: ""}\"§r"
            guiGraphics.drawCenteredString(font, emptyMsg, x + w / 2, listY + 20, 0xFF888888.toInt())
            biomeMaxScroll = 0.0
            biomeScrollOffset = 0.0
            return
        }

        val totalH = biomes.size * (rowH + 2)
        biomeMaxScroll = max(0.0, (totalH - listH).toDouble())
        biomeScrollOffset = biomeScrollOffset.coerceIn(0.0, biomeMaxScroll)

        val usableW = if (biomeMaxScroll > 0) w - 18 else w - 12
        val biomeCounts = getBiomeCounts()

        guiGraphics.enableScissor(x + 6, listY, x + w - 6, listY + listH)

        for ((idx, opt) in biomes.withIndex()) {
            val by = listY + idx * (rowH + 2) - biomeScrollOffset.toInt()
            if (by + rowH < listY || by > listY + listH) continue

            val isSel = (filterState.selectedBiome == opt)
            val isHov = mouseX >= x + 6 && mouseX <= x + 6 + usableW && mouseY >= by && mouseY <= by + rowH

            val bg = if (isSel) 0xFF232B3D.toInt() else if (isHov) 0xFF191E2A.toInt() else 0xFF12151E.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(x + 6, by, x + 6 + usableW, by + rowH, bg)
            guiGraphics.fill(x + 6, by, x + 6 + usableW, by + 1, border)
            guiGraphics.fill(x + 6, by + rowH - 1, x + 6 + usableW, by + rowH, border)
            guiGraphics.fill(x + 6, by, x + 7, by + rowH, border)
            guiGraphics.fill(x + 5 + usableW, by, x + 6 + usableW, by + rowH, border)

            val radio = if (isSel) "§6● " else "§7○ "
            val text = "$radio${opt.icon} ${if (isSel) "§e§l${opt.displayName}§r" else opt.displayName}"
            guiGraphics.drawString(font, text, x + 10, by + 4, 0xFFFFFFFF.toInt(), false)

            val counts = biomeCounts[opt] ?: PokedexFilterManager.FilterCount(0, 0)
            val missingColor = if (counts.missing > 0) "§c" else "§a"
            val countStr = "§7${counts.total} §8| $missingColor${counts.missing}§r"
            val countW = font.width(countStr)
            val countX = x + 6 + usableW - countW - 6
            guiGraphics.drawString(font, countStr, countX, by + 4, 0xFFFFFFFF.toInt(), false)

            if (isHov) {
                activeModalTooltip = listOf(
                    Component.literal("§e${opt.displayName}"),
                    Component.literal("§7Total : §f${counts.total} §7Pokémon"),
                    Component.literal(if (counts.missing > 0) "§cManquants : §f${counts.missing} §7à capturer" else "§a✔ Tous capturés (0 manquant)")
                )
            }
        }

        guiGraphics.disableScissor()

        // Barre de défilement
        if (biomeMaxScroll > 0) {
            val sbX = x + w - 10
            val sbW = 4
            guiGraphics.fill(sbX, listY, sbX + sbW, listY + listH, 0xFF141720.toInt())

            val thumbH = max(16, (listH * listH) / totalH)
            val thumbY = listY + ((biomeScrollOffset / biomeMaxScroll) * (listH - thumbH)).toInt()
            val thumbBg = if (isDraggingBiomeScrollbar) 0xFFFFA502.toInt() else 0xFF4A5568.toInt()
            guiGraphics.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, thumbBg)
        }
    }

    private fun renderGenFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, font: Font) {
        guiGraphics.drawString(font, "§7Génération et région d'origine du Pokémon :§r", x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        val gens = GenerationFilter.values()
        val genCounts = getGenCounts()
        val colW = (w - 18) / 2
        val rowH = 21
        val startY = y + 18

        for ((idx, gen) in gens.withIndex()) {
            val col = idx % 2
            val row = idx / 2
            val gx = x + 6 + col * (colW + 4)
            val gy = startY + row * (rowH + 2)

            val isSel = (filterState.selectedGen == gen)
            val isHov = mouseX >= gx && mouseX <= gx + colW && mouseY >= gy && mouseY <= gy + rowH

            val bg = if (isSel) 0xFF232B3D.toInt() else if (isHov) 0xFF181D29.toInt() else 0xFF12151E.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(gx, gy, gx + colW, gy + rowH, bg)
            guiGraphics.fill(gx, gy, gx + colW, gy + 1, border)
            guiGraphics.fill(gx, gy + rowH - 1, gx + colW, gy + rowH, border)
            guiGraphics.fill(gx, gy, gx + 1, gy + rowH, border)
            guiGraphics.fill(gx + colW - 1, gy, gx + colW, gy + rowH, border)

            val radio = if (isSel) "§6● " else "§7○ "
            val textTitle = "$radio${if (isSel) "§e§l${gen.displayName}§r" else gen.displayName}"
            guiGraphics.drawString(font, textTitle, gx + 6, gy + 6, 0xFFFFFFFF.toInt(), false)

            val counts = genCounts[gen] ?: PokedexFilterManager.FilterCount(0, 0)
            val missingColor = if (counts.missing > 0) "§c" else "§a"
            val countStr = "§7${counts.total} §8| $missingColor${counts.missing}§r"
            val countW = font.width(countStr)
            val countX = gx + colW - countW - 5
            guiGraphics.drawString(font, countStr, countX, gy + 6, 0xFFFFFFFF.toInt(), false)

            if (isHov) {
                activeModalTooltip = listOf(
                    Component.literal("§e${gen.displayName}"),
                    Component.literal("§7Total : §f${counts.total} §7Pokémon"),
                    Component.literal(if (counts.missing > 0) "§cManquants : §f${counts.missing} §7à capturer" else "§a✔ Tous capturés (0 manquant)")
                )
            }
        }
    }

    private fun renderStatusFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, font: Font) {
        guiGraphics.drawString(font, "§e§l1. Statut dans le Pokédex :§r", x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        val statuses = StatusFilter.values()
        val sColW = (w - 20) / 4
        val sRowH = 20
        val sY = y + 18

        for ((idx, st) in statuses.withIndex()) {
            val sx = x + 6 + idx * (sColW + 3)
            val isSel = (filterState.selectedStatus == st)
            val isHov = mouseX >= sx && mouseX <= sx + sColW && mouseY >= sY && mouseY <= sY + sRowH

            val bg = if (isSel) 0xFF232B3D.toInt() else if (isHov) 0xFF181D29.toInt() else 0xFF12151E.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(sx, sY, sx + sColW, sY + sRowH, bg)
            guiGraphics.fill(sx, sY, sx + sColW, sY + 1, border)
            guiGraphics.fill(sx, sY + sRowH - 1, sx + sColW, sY + sRowH, border)
            guiGraphics.fill(sx, sY, sx + 1, sY + sRowH, border)
            guiGraphics.fill(sx + sColW - 1, sY, sx + sColW, sY + sRowH, border)

            val radio = if (isSel) "§6● " else "§7○ "
            val label = "$radio${st.displayName.substringBefore(" ")}"
            guiGraphics.drawCenteredString(font, label, sx + sColW / 2, sY + 6, 0xFFFFFFFF.toInt())
        }

        guiGraphics.drawString(font, "§e§l2. Potentiel d'évolution :§r", x + 6, y + 46, 0xFFFFFFFF.toInt(), false)

        val evos = EvolutionFilter.values()
        val eRowH = 18
        val eStartY = y + 58

        for ((idx, evo) in evos.withIndex()) {
            val ey = eStartY + idx * (eRowH + 2)
            val isSel = (filterState.selectedEvolution == evo)
            val isHov = mouseX >= x + 6 && mouseX <= x + w - 6 && mouseY >= ey && mouseY <= ey + eRowH

            val bg = if (isSel) 0xFF232B3D.toInt() else if (isHov) 0xFF181D29.toInt() else 0xFF12151E.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(x + 6, ey, x + w - 6, ey + eRowH, bg)
            guiGraphics.fill(x + 6, ey, x + w - 6, ey + 1, border)
            guiGraphics.fill(x + 6, ey + eRowH - 1, x + w - 6, ey + eRowH, border)
            guiGraphics.fill(x + 6, ey, x + 7, ey + eRowH, border)
            guiGraphics.fill(x + w - 7, ey, x + w - 6, ey + eRowH, border)

            val radio = if (isSel) "§6● " else "§7○ "
            val text = "$radio${if (isSel) "§e§l${evo.displayName}§r" else evo.displayName}"
            guiGraphics.drawString(font, text, x + 12, ey + 5, 0xFFFFFFFF.toInt(), false)
        }
    }

    private fun renderSortFilterPane(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, x: Int, y: Int, w: Int, h: Int, font: Font) {
        guiGraphics.drawString(font, "§7Ordre et critère de tri des Pokémon affichés :§r", x + 6, y + 6, 0xFFFFFFFF.toInt(), false)

        val orders = PokedexSortOrder.values()
        val rowH = 20
        val startY = y + 20

        for ((idx, sort) in orders.withIndex()) {
            val sy = startY + idx * (rowH + 2)
            val isSel = (filterState.selectedSort == sort)
            val isHov = mouseX >= x + 6 && mouseX <= x + w - 6 && mouseY >= sy && mouseY <= sy + rowH

            val bg = if (isSel) 0xFF232B3D.toInt() else if (isHov) 0xFF181D29.toInt() else 0xFF12151E.toInt()
            val border = if (isSel) 0xFFFFA502.toInt() else if (isHov) 0xFF353D50.toInt() else 0xFF202636.toInt()

            guiGraphics.fill(x + 6, sy, x + w - 6, sy + rowH, bg)
            guiGraphics.fill(x + 6, sy, x + w - 6, sy + 1, border)
            guiGraphics.fill(x + 6, sy + rowH - 1, x + w - 6, sy + rowH, border)
            guiGraphics.fill(x + 6, sy, x + 7, sy + rowH, border)
            guiGraphics.fill(x + w - 7, sy, x + w - 6, sy + rowH, border)

            val radio = if (isSel) "§6● " else "§7○ "
            val text = "$radio${if (isSel) "§e§l${sort.displayName}§r" else sort.displayName}"
            guiGraphics.drawString(font, text, x + 12, sy + 6, 0xFFFFFFFF.toInt(), false)
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isOpen) return false

        val (modalX, modalY, modalW, modalH) = getBounds(screenWidth, screenHeight)

        // 1. Bouton Fermer [✕] en haut à droite
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14) {
            close()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }

        // 2. Barre inférieure
        val botY = modalY + modalH - 22

        // [⟲ Tout effacer]
        val resetBtnX = modalX + 6
        val resetBtnY = botY + 3
        val resetBtnW = 84
        val resetBtnH = 15
        if (mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= resetBtnY && mouseY <= resetBtnY + resetBtnH) {
            filterState.reset()
            onFiltersChanged()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
            return true
        }

        // [✓ Appliquer & Fermer]
        val closeApplyW = 104
        val closeApplyX = modalX + modalW - closeApplyW - 6
        val closeApplyY = botY + 3
        val closeApplyH = 15
        if (mouseX >= closeApplyX && mouseX <= closeApplyX + closeApplyW && mouseY >= closeApplyY && mouseY <= closeApplyY + closeApplyH) {
            close()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }

        // 3. Barre latérale gauche (Catégories)
        val sideX = modalX + 5
        val sideY = modalY + 24
        val sideW = 96
        val sideH = modalH - 48
        val categories = FilterCategory.values()
        val catH = sideH / categories.size

        if (mouseX >= sideX && mouseX <= sideX + sideW && mouseY >= sideY && mouseY <= sideY + sideH) {
            val idx = ((mouseY - sideY) / catH).toInt().coerceIn(0, categories.size - 1)
            activeFilterCategory = categories[idx]
            biomeSearchBox?.isFocused = false
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
            return true
        }

        // 4. Panneau de contenu
        val paneX = sideX + sideW + 6
        val paneY = modalY + 24
        val paneW = modalX + modalW - paneX - 5
        val paneH = sideH

        if (mouseX >= paneX && mouseX <= paneX + paneW && mouseY >= paneY && mouseY <= paneY + paneH) {
            when (activeFilterCategory) {
                FilterCategory.TYPE -> {
                    val clearBtnX = paneX + paneW - 58
                    val clearBtnY = paneY + 4
                    if (mouseX >= clearBtnX && mouseX <= clearBtnX + 52 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12) {
                        filterState.selectedTypes.clear()
                        onFiltersChanged()
                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
                        return true
                    }

                    val allTypes = PokedexFilterManager.ALL_TYPES
                    val colW = (paneW - 18) / 3
                    val rowH = 16
                    val startY = paneY + 18

                    for ((idx, type) in allTypes.withIndex()) {
                        val col = idx % 3
                        val row = idx / 3
                        val bx = paneX + 6 + col * (colW + 3)
                        val by = startY + row * (rowH + 3)

                        if (mouseX >= bx && mouseX <= bx + colW && mouseY >= by && mouseY <= by + rowH) {
                            if (filterState.selectedTypes.contains(type.id)) {
                                filterState.selectedTypes.remove(type.id)
                            } else {
                                filterState.selectedTypes.add(type.id)
                            }
                            onFiltersChanged()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
                            return true
                        }
                    }
                }
                FilterCategory.RARITY -> {
                    val rarities = SpawnRarityFilter.values()
                    val rowH = 20
                    val startY = paneY + 20

                    for ((idx, opt) in rarities.withIndex()) {
                        val ry = startY + idx * (rowH + 2)
                        if (mouseX >= paneX + 6 && mouseX <= paneX + paneW - 6 && mouseY >= ry && mouseY <= ry + rowH) {
                            filterState.selectedRarity = opt
                            onFiltersChanged()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                            return true
                        }
                    }
                }
                FilterCategory.BIOME -> {
                    val clearBtnX = paneX + paneW - 64
                    val clearBtnY = paneY + 4
                    if (mouseX >= clearBtnX && mouseX <= clearBtnX + 58 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12) {
                        filterState.selectedBiome = CobblemonBiomeFilter.ALL
                        onFiltersChanged()
                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
                        return true
                    }

                    val searchBox = biomeSearchBox
                    if (searchBox != null && searchBox.value.isNotEmpty()) {
                        val clearSearchX = paneX + paneW - 18
                        val clearSearchY = paneY + 24
                        if (mouseX >= clearSearchX - 4 && mouseX <= clearSearchX + 10 && mouseY >= clearSearchY - 4 && mouseY <= clearSearchY + 12) {
                            searchBox.value = ""
                            biomeScrollOffset = 0.0
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f))
                            return true
                        }
                    }

                    val searchBoxX = paneX + 6
                    val searchBoxY = paneY + 20
                    val searchBoxW = paneW - 12
                    val searchBoxH = 16
                    if (searchBox != null) {
                        if (mouseX >= searchBoxX && mouseX <= searchBoxX + searchBoxW && mouseY >= searchBoxY && mouseY <= searchBoxY + searchBoxH) {
                            searchBox.isFocused = true
                            searchBox.mouseClicked(mouseX, mouseY, 0)
                            return true
                        } else {
                            searchBox.isFocused = false
                        }
                    }

                    val normQuery = if (searchBox != null) normalizeText(searchBox.value) else ""
                    val biomes = if (normQuery.isEmpty()) {
                        CobblemonBiomeFilter.values().toList()
                    } else {
                        CobblemonBiomeFilter.values().filter { biome ->
                            normalizeText(biome.displayName).contains(normQuery) ||
                            biome.id.lowercase().contains(normQuery) ||
                            biome.tagKeywords.any { normalizeText(it).contains(normQuery) }
                        }
                    }

                    val listY = paneY + 40
                    val listH = paneH - 44
                    val rowH = 15
                    val usableW = if (biomeMaxScroll > 0) paneW - 18 else paneW - 12

                    if (biomeMaxScroll > 0 && mouseX >= paneX + paneW - 12 && mouseX <= paneX + paneW) {
                        isDraggingBiomeScrollbar = true
                        dragStartBiomeMouseY = mouseY
                        dragStartBiomeScroll = biomeScrollOffset
                        return true
                    }

                    if (mouseY >= listY && mouseY <= listY + listH) {
                        for ((idx, opt) in biomes.withIndex()) {
                            val by = listY + idx * (rowH + 2) - biomeScrollOffset.toInt()
                            if (by + rowH < listY || by > listY + listH) continue

                            if (mouseX >= paneX + 6 && mouseX <= paneX + 6 + usableW && mouseY >= by && mouseY <= by + rowH) {
                                filterState.selectedBiome = opt
                                onFiltersChanged()
                                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                                return true
                            }
                        }
                    }
                }
                FilterCategory.GEN -> {
                    val gens = GenerationFilter.values()
                    val colW = (paneW - 18) / 2
                    val rowH = 21
                    val startY = paneY + 18

                    for ((idx, gen) in gens.withIndex()) {
                        val col = idx % 2
                        val row = idx / 2
                        val gx = paneX + 6 + col * (colW + 4)
                        val gy = startY + row * (rowH + 2)

                        if (mouseX >= gx && mouseX <= gx + colW && mouseY >= gy && mouseY <= gy + rowH) {
                            filterState.selectedGen = gen
                            onFiltersChanged()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                            return true
                        }
                    }
                }
                FilterCategory.STATUS -> {
                    val statuses = StatusFilter.values()
                    val sColW = (paneW - 20) / 4
                    val sY = paneY + 18

                    for ((idx, st) in statuses.withIndex()) {
                        val sx = paneX + 6 + idx * (sColW + 3)
                        if (mouseX >= sx && mouseX <= sx + sColW && mouseY >= sY && mouseY <= sY + 20) {
                            filterState.selectedStatus = st
                            onFiltersChanged()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                            return true
                        }
                    }

                    val evoSectionY = paneY + 46
                    val evos = EvolutionFilter.values()
                    val eRowH = 18
                    val eStartY = evoSectionY + 12

                    for ((idx, evo) in evos.withIndex()) {
                        val ey = eStartY + idx * (eRowH + 2)
                        if (mouseX >= paneX + 6 && mouseX <= paneX + paneW - 6 && mouseY >= ey && mouseY <= ey + eRowH) {
                            filterState.selectedEvolution = evo
                            onFiltersChanged()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                            return true
                        }
                    }
                }
                FilterCategory.SORT -> {
                    val orders = PokedexSortOrder.values()
                    val rowH = 20
                    val startY = paneY + 20

                    for ((idx, sort) in orders.withIndex()) {
                        val sy = startY + idx * (rowH + 2)
                        if (mouseX >= paneX + 6 && mouseX <= paneX + paneW - 6 && mouseY >= sy && mouseY <= sy + rowH) {
                            filterState.selectedSort = sort
                            onFiltersChanged()
                            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
                            return true
                        }
                    }
                }
            }
            return true
        }

        // Clic en dehors de la boîte modale : ferme la modale
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
        if (activeFilterCategory == FilterCategory.BIOME && biomeMaxScroll > 0) {
            biomeScrollOffset = (biomeScrollOffset - scrollY * 20.0).coerceIn(0.0, biomeMaxScroll)
            return true
        }
        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean =
        mouseScrolled(scrollY)

    fun mouseDragged(mouseY: Double): Boolean {
        if (!isOpen) return false
        if (isDraggingBiomeScrollbar && biomeMaxScroll > 0) {
            val delta = mouseY - dragStartBiomeMouseY
            val (_, _, _, modalH) = intArrayOf(0, 0, 0, 320)
            val listH = (modalH - 48) - 44
            val scrollDelta = (delta / listH) * biomeMaxScroll
            biomeScrollOffset = (dragStartBiomeScroll + scrollDelta).coerceIn(0.0, biomeMaxScroll)
            return true
        }
        return false
    }

    fun mouseReleased(): Boolean {
        if (isDraggingBiomeScrollbar) {
            isDraggingBiomeScrollbar = false
            return true
        }
        return false
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (!isOpen) return false
        if (activeFilterCategory == FilterCategory.BIOME && biomeSearchBox != null && biomeSearchBox!!.isFocused) {
            return biomeSearchBox!!.charTyped(codePoint, modifiers)
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!isOpen) return false
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }
        if (activeFilterCategory == FilterCategory.BIOME && biomeSearchBox != null && biomeSearchBox!!.isFocused) {
            if (biomeSearchBox!!.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
        }
        return true
    }
}
