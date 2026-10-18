package fr.k0bus.cobblemon_jei.compat.rct

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Interface graphique plein écran pour l'arbre de progression de Radical Cobblemon Trainers (RCT).
 * Hautement optimisée avec Culling de Frustum, cache de recherche O(1),
 * et rendu séquentiel évitant tout flou de post-traitement.
 */
class CobblemonRctProgressionScreen(
    private val parentScreen: Screen? = null
) : Screen(Component.translatable("gui.cobblemon_jei.rct.title")) {

    // Graphe de la série active
    private var graph: RctTreeGraph? = null
    private var currentSeriesId: String = "radicalred"
    private var onlyKeyTrainers: Boolean = true

    // État du Pan & Zoom sur le canevas
    private var zoom: Double = 1.0
    private var panX: Double = 50.0
    private var panY: Double = 70.0

    private var isDraggingCanvas: Boolean = false
    private var dragStartMouseX: Double = 0.0
    private var dragStartMouseY: Double = 0.0
    private var dragStartPanX: Double = 0.0
    private var dragStartPanY: Double = 0.0

    // Dresseur sélectionné et panneau de détails
    private var selectedNode: RctTreeNode? = null
    private var hoveredNode: RctTreeNode? = null
    private var isDetailDrawerOpen: Boolean = false
    private var detailScrollOffset: Double = 0.0
    private var maxDetailScroll: Double = 0.0

    // Recherche et filtrage avec cache O(1)
    private lateinit var searchBox: EditBox
    private var searchQuery: String = ""
    private val matchingTrainerIds = mutableSetOf<String>()

    // Sélecteur de série et bascule de filtre
    private lateinit var seriesBtn: Button
    private lateinit var keyFilterBtn: Button

    // Contrôles de vue
    private lateinit var zoomInBtn: Button
    private lateinit var zoomOutBtn: Button
    private lateinit var resetViewBtn: Button
    private lateinit var centerTargetBtn: Button

    // Cache d'ItemStacks pour les sprites de Pokémon
    private val pokeItemCache = mutableMapOf<String, ItemStack>()

    // Animation de pulsation
    private var animTick: Float = 0f

    override fun init() {
        RctDataLoader.ensureLoaded()
        currentSeriesId = RctProgressionService.getSelectedSeriesId()
        onlyKeyTrainers = RctProgressionService.isOnlyKeyTrainers()
        reloadGraph()

        val topY = 12
        val btnH = 20

        // Bouton Retour Pokédex
        val backBtn = Button.builder(Component.literal("← Pokédex")) {
            onClose()
        }.bounds(12, topY, 82, btnH).build()
        backBtn.tooltip = Tooltip.create(Component.literal("Retourner au Pokédex"))
        addRenderableWidget(backBtn)

        // Sélecteur de série
        val currentSeries = RctDataLoader.getSeries(currentSeriesId)
        val seriesTitle = currentSeries?.title ?: currentSeriesId
        seriesBtn = Button.builder(Component.literal("Série : $seriesTitle ▾")) {
            cycleSeries()
        }.bounds(98, topY, 145, btnH).build()
        seriesBtn.tooltip = Tooltip.create(Component.literal("Changer de série (Radical Red, Unbound, Sinnoh...)"))
        addRenderableWidget(seriesBtn)

        // Bascule : Dresseurs majeurs / Tous
        keyFilterBtn = Button.builder(getKeyFilterButtonText()) {
            onlyKeyTrainers = !onlyKeyTrainers
            RctProgressionService.setOnlyKeyTrainers(onlyKeyTrainers)
            keyFilterBtn.message = getKeyFilterButtonText()
            reloadGraph()
            centerOnNextTarget()
        }.bounds(247, topY, 115, btnH).build()
        keyFilterBtn.tooltip = Tooltip.create(Component.literal("Basculer entre les dresseurs clés (Arènes/Boss/Histoire) et la totalité des dresseurs"))
        addRenderableWidget(keyFilterBtn)

        // Champ de recherche
        val searchW = 110
        searchBox = EditBox(font, 366, topY + 1, searchW, btnH - 2, Component.literal(""))
        searchBox.setHint(Component.literal("Chercher..."))
        searchBox.setResponder { q ->
            searchQuery = q.trim()
            recomputeSearchMatches()
        }
        addRenderableWidget(searchBox)

        // Boutons de navigation canevas
        val ctrlX = width - 150
        zoomInBtn = Button.builder(Component.literal("+")) {
            zoomAt(width / 2.0, height / 2.0, 1.2)
        }.bounds(ctrlX, topY, 20, btnH).build()
        zoomInBtn.tooltip = Tooltip.create(Component.literal("Zoom avant"))
        addRenderableWidget(zoomInBtn)

        zoomOutBtn = Button.builder(Component.literal("-")) {
            zoomAt(width / 2.0, height / 2.0, 0.8)
        }.bounds(ctrlX + 22, topY, 20, btnH).build()
        zoomOutBtn.tooltip = Tooltip.create(Component.literal("Zoom arrière"))
        addRenderableWidget(zoomOutBtn)

        resetViewBtn = Button.builder(Component.literal("⟲")) {
            resetView()
        }.bounds(ctrlX + 44, topY, 22, btnH).build()
        resetViewBtn.tooltip = Tooltip.create(Component.literal("Réinitialiser la vue"))
        addRenderableWidget(resetViewBtn)

        centerTargetBtn = Button.builder(Component.literal("🎯")) {
            centerOnNextTarget()
        }.bounds(ctrlX + 68, topY, 22, btnH).build()
        centerTargetBtn.tooltip = Tooltip.create(Component.literal("Centrer sur le prochain dresseur à battre"))
        addRenderableWidget(centerTargetBtn)

        // Bouton Fermer
        val closeBtn = Button.builder(Component.literal("✕")) {
            onClose()
        }.bounds(width - 28, topY, 20, btnH).build()
        addRenderableWidget(closeBtn)

        if (selectedNode == null) {
            selectedNode = graph?.nodes?.firstOrNull { it.status == RctProgressionStatus.AVAILABLE }
                ?: graph?.nodes?.firstOrNull()
        }
    }

    private fun getKeyFilterButtonText(): Component {
        return if (onlyKeyTrainers) {
            Component.literal("⭐ Clés & Arènes")
        } else {
            Component.literal("⭐ Tous dresseurs")
        }
    }

    private fun reloadGraph() {
        graph = RctProgressionService.buildTreeGraph(currentSeriesId, onlyKeyTrainers)
        recomputeSearchMatches()
    }

    private fun recomputeSearchMatches() {
        matchingTrainerIds.clear()
        if (searchQuery.isBlank()) return
        val q = normalizeString(searchQuery)
        val nodes = graph?.nodes ?: return

        for (node in nodes) {
            val t = node.trainer
            if (normalizeString(t.name).contains(q) || normalizeString(t.type).contains(q)) {
                matchingTrainerIds.add(t.id)
                continue
            }
            val matchPoke = t.team.any { p ->
                normalizeString(p.species).contains(q) ||
                        (p.nickname != null && normalizeString(p.nickname).contains(q))
            }
            if (matchPoke) {
                matchingTrainerIds.add(t.id)
            }
        }
    }

    private fun cycleSeries() {
        val all = RctDataLoader.getAllSeries()
        if (all.isEmpty()) return
        val currentIndex = all.indexOfFirst { it.id == currentSeriesId }
        val nextIndex = (currentIndex + 1) % all.size
        val nextSeries = all[nextIndex]
        currentSeriesId = nextSeries.id
        RctProgressionService.setSelectedSeriesId(currentSeriesId)

        seriesBtn.message = Component.literal("Série : ${nextSeries.title} ▾")
        reloadGraph()
        selectedNode = graph?.nodes?.firstOrNull { it.status == RctProgressionStatus.AVAILABLE }
            ?: graph?.nodes?.firstOrNull()
        resetView()
    }

    private fun resetView() {
        zoom = 1.0
        panX = 50.0
        panY = 70.0
    }

    private fun centerOnNextTarget() {
        val target = graph?.nodes?.firstOrNull { it.status == RctProgressionStatus.AVAILABLE }
            ?: graph?.nodes?.firstOrNull() ?: return

        val drawerWidth = if (isDetailDrawerOpen) 240.0 else 0.0
        val visibleW = width - drawerWidth
        val visibleH = height - 60.0

        zoom = 1.05
        panX = visibleW / 2.0 - (target.x + target.width / 2.0) * zoom
        panY = 60.0 + visibleH / 2.0 - (target.y + target.height / 2.0) * zoom
        selectedNode = target
    }

    private fun zoomAt(cx: Double, cy: Double, factor: Double) {
        val oldZoom = zoom
        val newZoom = (zoom * factor).coerceIn(0.35, 2.5)
        panX = cx - (cx - panX) * (newZoom / oldZoom)
        panY = cy - (cy - panY) * (newZoom / oldZoom)
        zoom = newZoom
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        animTick += partialTick

        val headerH = 46
        val footerH = 22
        val canvasW = if (isDetailDrawerOpen) width - 240 else width
        val canvasH = height - headerH - footerH

        // 1. Fond transparent pour permettre au shader de flou Minecraft d'agir sur le monde derrière l'UI
        renderTransparentBackground(guiGraphics)

        // 2. Fond opaque ardoise de notre interface
        guiGraphics.fill(0, 0, width, height, 0xFF101218.toInt())

        // 3. FOND de l'en-tête et pied de page
        guiGraphics.fill(0, 0, width, headerH, 0xFF141720.toInt())
        guiGraphics.fill(0, headerH - 1, width, headerH, 0xFF282D3C.toInt())
        guiGraphics.fill(0, height - footerH, width, height, 0xFF141720.toInt())
        guiGraphics.fill(0, height - footerH, width, height - footerH + 1, 0xFF282D3C.toInt())

        // 4. Appel de super.render MAINTENANT (les boutons sont rendus au-dessus du fond et ne sont pas floutés)
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // 5. Calcul des limites du champ de vision pour le Frustum Culling
        val visibleMinX = (0.0 - panX) / zoom - 30.0
        val visibleMaxX = (canvasW.toDouble() - panX) / zoom + 30.0
        val visibleMinY = (headerH.toDouble() - panY) / zoom - 30.0
        val visibleMaxY = ((height - footerH).toDouble() - panY) / zoom + 30.0

        // 6. Rendu du canevas sous ciseau avec culling strict
        guiGraphics.enableScissor(0, headerH, canvasW, height - footerH)

        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(panX.toFloat(), panY.toFloat(), 0f)
        pose.scale(zoom.toFloat(), zoom.toFloat(), 1.0f)

        // Rendu des bandes de colonnes/jalons en arrière-plan
        renderColumnMilestones(guiGraphics, visibleMinX, visibleMaxX, visibleMinY, visibleMaxY)

        // Rendu des arêtes (filtrées par visibilité)
        graph?.edges?.let { edges ->
            for (edge in edges) {
                val eMinX = min(edge.fromNode.x, edge.toNode.x)
                val eMaxX = max(edge.fromNode.x + edge.fromNode.width, edge.toNode.x + edge.toNode.width)
                val eMinY = min(edge.fromNode.y, edge.toNode.y)
                val eMaxY = max(edge.fromNode.y + edge.fromNode.height, edge.toNode.y + edge.toNode.height)

                if (eMaxX < visibleMinX || eMinX > visibleMaxX || eMaxY < visibleMinY || eMinY > visibleMaxY) {
                    continue
                }
                renderEdge(guiGraphics, edge)
            }
        }

        // Rendu des nœuds (filtrés par visibilité)
        hoveredNode = null
        val worldMouseX = (mouseX - panX) / zoom
        val worldMouseY = (mouseY - panY) / zoom

        graph?.nodes?.let { nodes ->
            for (node in nodes) {
                // Frustum Culling : Ignore instantanément les nœuds hors écran
                if (node.x + node.width < visibleMinX || node.x > visibleMaxX ||
                    node.y + node.height < visibleMinY || node.y > visibleMaxY) {
                    continue
                }

                val isHovered = mouseX in 0 until canvasW &&
                        mouseY in headerH until (height - footerH) &&
                        worldMouseX >= node.x && worldMouseX <= node.x + node.width &&
                        worldMouseY >= node.y && worldMouseY <= node.y + node.height

                if (isHovered) {
                    hoveredNode = node
                }

                renderNode(guiGraphics, node, isSelected = (node == selectedNode), isHovered = isHovered)
            }
        }

        pose.popPose()
        guiGraphics.disableScissor()

        // 7. Panneau latéral de détails (si ouvert)
        if (isDetailDrawerOpen && selectedNode != null) {
            renderDetailDrawer(guiGraphics, width - 240, headerH, 240, height - headerH, mouseX, mouseY)
        }

        // 8. Textes de l'en-tête (au-dessus du fond, parfaitement nets)
        renderHeaderTexts(guiGraphics, headerH)

        // 9. Textes du pied de page
        renderFooterTexts(guiGraphics, height - footerH, footerH)

        // 10. Tooltip de survol d'un nœud
        if (hoveredNode != null && !isDetailDrawerOpen) {
            renderNodeTooltip(guiGraphics, hoveredNode!!, mouseX, mouseY)
        }
    }

    /**
     * Rendu très léger des colonnes de jalons de l'arbre.
     */
    private fun renderColumnMilestones(guiGraphics: GuiGraphics, minX: Double, maxX: Double, minY: Double, maxY: Double) {
        val g = graph ?: return
        val maxRank = g.nodes.maxOfOrNull { it.rank } ?: 0
        val colStep = 230.0
        val startX = 60.0

        for (r in 0..maxRank) {
            val cx = startX + r * colStep
            if (cx + 145.0 < minX || cx > maxX) continue
            // Bande verticale très subtile pour structurer l'arbre visuellement
            guiGraphics.fill((cx - 10).toInt(), (minY - 20).toInt(), (cx + 155).toInt(), (maxY + 20).toInt(), 0x08FFFFFF)
        }
    }

    /**
     * Rendu d'une ligne de liaison entre deux dresseurs.
     */
    private fun renderEdge(guiGraphics: GuiGraphics, edge: RctTreeEdge) {
        val from = edge.fromNode
        val to = edge.toNode

        val startX = (from.x + from.width).toInt()
        val startY = (from.y + from.height / 2.0).toInt()
        val endX = to.x.toInt()
        val endY = (to.y + to.height / 2.0).toInt()

        val color = when {
            edge.isComplete -> 0xFF2ECC71.toInt()
            edge.isReady -> {
                val pulse = (sin(animTick * 0.15) * 40 + 215).toInt().coerceIn(150, 255)
                ((0xFF shl 24) or (pulse shl 16) or ((pulse * 0.8).toInt() shl 8) or 0x0F)
            }
            else -> 0x336C7A89.toInt()
        }

        val thickness = if (edge.isComplete || edge.isReady) 2 else 1
        val midX = startX + (endX - startX) / 2

        guiGraphics.fill(startX, startY - thickness / 2, midX, startY + (thickness + 1) / 2, color)
        val topY = min(startY, endY)
        val botY = max(startY, endY)
        guiGraphics.fill(midX - thickness / 2, topY, midX + (thickness + 1) / 2, botY, color)
        guiGraphics.fill(midX, endY - thickness / 2, endX, endY + (thickness + 1) / 2, color)
        guiGraphics.fill(endX - 3, endY - 3, endX, endY + 3, color)
    }

    /**
     * Rendu d'un nœud dresseur individuel sur le canevas.
     */
    private fun renderNode(guiGraphics: GuiGraphics, node: RctTreeNode, isSelected: Boolean, isHovered: Boolean) {
        val t = node.trainer
        val nx = node.x.toInt()
        val ny = node.y.toInt()
        val nw = node.width.toInt()
        val nh = node.height.toInt()

        val isMatchQuery = searchQuery.isEmpty() || matchingTrainerIds.contains(t.id)

        val (bgColor, borderColor) = when (node.status) {
            RctProgressionStatus.DEFEATED -> Pair(0xFF14221A.toInt(), 0xFF2ECC71.toInt())
            RctProgressionStatus.AVAILABLE -> {
                val pulse = (sin(animTick * 0.12) * 35 + 220).toInt().coerceIn(180, 255)
                val pulseColor = ((0xFF shl 24) or (pulse shl 16) or ((pulse * 0.8).toInt() shl 8) or 0x10)
                Pair(0xFF221E14.toInt(), pulseColor)
            }
            RctProgressionStatus.LOCKED -> Pair(0xFF15181F.toInt(), 0xFF282F3E.toInt())
        }

        val finalBorder = when {
            isSelected -> 0xFF3498DB.toInt()
            isHovered -> 0xFFFFFFFF.toInt()
            else -> borderColor
        }

        val finalBg = if (!isMatchQuery) 0x66101218.toInt() else bgColor

        guiGraphics.fill(nx, ny, nx + nw, ny + nh, finalBg)
        guiGraphics.fill(nx, ny, nx + nw, ny + 1, finalBorder)
        guiGraphics.fill(nx, ny + nh - 1, nx + nw, ny + nh, finalBorder)
        guiGraphics.fill(nx, ny, nx + 1, ny + nh, finalBorder)
        guiGraphics.fill(nx + nw - 1, ny, nx + nw, ny + nh, finalBorder)

        if (isSelected) {
            guiGraphics.fill(nx - 1, ny - 1, nx + nw + 1, ny, 0xFF3498DB.toInt())
            guiGraphics.fill(nx - 1, ny + nh, nx + nw + 1, ny + nh + 1, 0xFF3498DB.toInt())
            guiGraphics.fill(nx - 1, ny, nx, ny + nh, 0xFF3498DB.toInt())
            guiGraphics.fill(nx + nw, ny, nx + nw + 1, ny + nh, 0xFF3498DB.toInt())
        }

        val faceSize = 36
        val faceX = nx + 8
        val faceY = ny + (nh - faceSize) / 2

        guiGraphics.fill(faceX - 1, faceY - 1, faceX + faceSize + 1, faceY + faceSize + 1, 0xFF242A38.toInt())
        guiGraphics.fill(faceX, faceY, faceX + faceSize, faceY + faceSize, 0xFF0D0F14.toInt())

        try {
            PlayerFaceRenderer.draw(guiGraphics, t.textureLocation, faceX, faceY, faceSize)
        } catch (_: Throwable) {
            guiGraphics.drawString(font, "👤", faceX + 12, faceY + 12, 0xFF888888.toInt(), false)
        }

        when (node.status) {
            RctProgressionStatus.DEFEATED -> {
                guiGraphics.fill(faceX + faceSize - 9, faceY + faceSize - 9, faceX + faceSize, faceY + faceSize, 0xFF2ECC71.toInt())
                guiGraphics.drawString(font, "✓", faceX + faceSize - 7, faceY + faceSize - 8, 0xFFFFFFFF.toInt(), false)
            }
            RctProgressionStatus.LOCKED -> {
                guiGraphics.fill(faceX + faceSize - 9, faceY + faceSize - 9, faceX + faceSize, faceY + faceSize, 0xFF444444.toInt())
                guiGraphics.drawString(font, "🔒", faceX + faceSize - 8, faceY + faceSize - 8, 0xFFCCCCCC.toInt(), false)
            }
            RctProgressionStatus.AVAILABLE -> {
                guiGraphics.fill(faceX + faceSize - 9, faceY + faceSize - 9, faceX + faceSize, faceY + faceSize, 0xFFF1C40F.toInt())
                guiGraphics.drawString(font, "⚔", faceX + faceSize - 8, faceY + faceSize - 8, 0xFF000000.toInt(), false)
            }
        }

        val textX = nx + faceSize + 14
        val maxTextW = nw - (faceSize + 18)

        val truncatedName = font.plainSubstrByWidth(t.name, maxTextW)
        val nameColor = if (node.status == RctProgressionStatus.LOCKED) 0xFFAAAAAA.toInt() else 0xFFFFFFFF.toInt()
        guiGraphics.drawString(font, truncatedName, textX, ny + 8, nameColor, false)

        val capY = ny + 22
        val capText = "Cap ${t.computedLevelCap}"
        val capW = font.width(capText) + 8
        val capColor = getCapBadgeColor(t.computedLevelCap)
        guiGraphics.fill(textX, capY, textX + capW, capY + 11, capColor)
        guiGraphics.drawString(font, capText, textX + 4, capY + 2, 0xFFFFFFFF.toInt(), false)

        val teamText = "${t.team.size} Poké"
        val teamColor = 0xFF8892B0.toInt()
        guiGraphics.drawString(font, teamText, textX + capW + 4, capY + 2, teamColor, false)

        val statusY = ny + 38
        if (t.optional) {
            guiGraphics.drawString(font, "★ Optionnel", textX, statusY, 0xFFE67E22.toInt(), false)
        } else {
            val statusLabel = when (node.status) {
                RctProgressionStatus.DEFEATED -> "✓ Vaincu"
                RctProgressionStatus.AVAILABLE -> "⚔ À battre"
                RctProgressionStatus.LOCKED -> "🔒 Bloqué"
            }
            val labelColor = when (node.status) {
                RctProgressionStatus.DEFEATED -> 0xFF2ECC71.toInt()
                RctProgressionStatus.AVAILABLE -> 0xFFF1C40F.toInt()
                RctProgressionStatus.LOCKED -> 0xFF7F8C8D.toInt()
            }
            guiGraphics.drawString(font, statusLabel, textX, statusY, labelColor, false)
        }
    }

    private fun getCapBadgeColor(level: Int): Int {
        return when {
            level <= 20 -> 0xFF27AE60.toInt()
            level <= 35 -> 0xFF2980B9.toInt()
            level <= 50 -> 0xFF8E44AD.toInt()
            level <= 70 -> 0xFFD35400.toInt()
            else -> 0xFFC0392B.toInt()
        }
    }

    private fun normalizeString(text: String): String {
        val norm = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(norm, "")
    }

    /**
     * Rendu des textes d'en-tête (exécuté après super.render pour une netteté parfaite sans flou).
     */
    private fun renderHeaderTexts(guiGraphics: GuiGraphics, headerH: Int) {
        guiGraphics.drawString(font, "⚔ Radical Trainers", 14, 34, 0xFFF1C40F.toInt(), true)

        val g = graph
        if (g != null) {
            val capText = "Mon Cap Actuel : §aNv. ${g.playerLevelCap}§r"
            guiGraphics.drawString(font, capText, 130, 34, 0xFFFFFFFF.toInt(), false)

            val progText = "Progression : §e${g.defeatedCount} / ${g.totalCount}§r"
            guiGraphics.drawString(font, progText, 255, 34, 0xFFFFFFFF.toInt(), false)

            val barX = 385
            val barY = 34
            val barW = 90
            val barH = 8
            val pct = if (g.totalCount > 0) g.defeatedCount.toFloat() / g.totalCount else 0f
            val filledW = (barW * pct).toInt()

            guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222634.toInt())
            if (filledW > 0) {
                guiGraphics.fill(barX, barY, barX + filledW, barY + barH, 0xFF2ECC71.toInt())
            }
            guiGraphics.fill(barX, barY, barX + barW, barY + 1, 0x44FFFFFF)
        }
    }

    private fun renderFooterTexts(guiGraphics: GuiGraphics, y: Int, h: Int) {
        val helpText = "Glisser : Déplacer | Molette : Zoom | Clic dresseur : Voir équipe & détails"
        guiGraphics.drawString(font, helpText, 14, y + 6, 0xFF8892B0.toInt(), false)

        val countInfo = if (onlyKeyTrainers) "Mode : Arènes & Dresseurs Clés" else "Mode : Tous les dresseurs (${graph?.nodes?.size ?: 0} affichés)"
        val seriesInfo = graph?.series?.let { "${it.title} • $countInfo" } ?: countInfo
        guiGraphics.drawCenteredString(font, seriesInfo, width / 2, y + 6, 0xFFA0AABF.toInt())
    }

    private fun renderNodeTooltip(guiGraphics: GuiGraphics, node: RctTreeNode, mouseX: Int, mouseY: Int) {
        val t = node.trainer
        val lines = mutableListOf<Component>()
        lines.add(Component.literal("§6§l${t.name}§r"))
        lines.add(Component.literal("§7Type : §f${t.type.replaceFirstChar { it.uppercase() }}"))
        lines.add(Component.literal("§7Cap de Niveau : §aNv. ${t.computedLevelCap}§r"))
        lines.add(Component.literal("§7Équipe : §f${t.team.size} Pokémon"))

        when (node.status) {
            RctProgressionStatus.DEFEATED -> lines.add(Component.literal("§a✓ Déjà vaincu !§r"))
            RctProgressionStatus.AVAILABLE -> lines.add(Component.literal("§e⚔ Prêt au duel (Prochain objectif)§r"))
            RctProgressionStatus.LOCKED -> {
                lines.add(Component.literal("§c🔒 Verrouillé§r"))
                if (t.requiredDefeats.isNotEmpty()) {
                    lines.add(Component.literal("§7Prérequis nécessaires :"))
                    for (group in t.requiredDefeats) {
                        val names = group.joinToString(" ou ") { id ->
                            RctDataLoader.getTrainer(id)?.name ?: id
                        }
                        lines.add(Component.literal("  §8• $names"))
                    }
                }
            }
        }

        guiGraphics.renderComponentTooltip(font, lines, mouseX, mouseY)
    }

    private fun renderDetailDrawer(
        guiGraphics: GuiGraphics,
        panelX: Int,
        panelY: Int,
        panelW: Int,
        panelH: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val node = selectedNode ?: return
        val t = node.trainer

        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF141720.toInt())
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF282D3C.toInt())

        val contentY = panelY + 10
        val contentH = panelH - 20

        guiGraphics.enableScissor(panelX, contentY, panelX + panelW, contentY + contentH)

        var curY = (contentY - detailScrollOffset).toInt()

        val avatarSize = 44
        val avatarX = panelX + 14
        guiGraphics.fill(avatarX - 1, curY - 1, avatarX + avatarSize + 1, curY + avatarSize + 1, 0xFF282D3C.toInt())
        guiGraphics.fill(avatarX, curY, avatarX + avatarSize, curY + avatarSize, 0xFF0D0F14.toInt())

        try {
            PlayerFaceRenderer.draw(guiGraphics, t.textureLocation, avatarX, curY, avatarSize)
        } catch (_: Throwable) {
            guiGraphics.drawString(font, "👤", avatarX + 16, curY + 16, 0xFF888888.toInt(), false)
        }

        val nameX = avatarX + avatarSize + 10
        guiGraphics.drawString(font, t.name, nameX, curY + 4, 0xFFFFFFFF.toInt(), true)
        guiGraphics.drawString(font, "Type : ${t.type.replaceFirstChar { it.uppercase() }}", nameX, curY + 18, 0xFF8892B0.toInt(), false)
        guiGraphics.drawString(font, "Format : ${t.battleFormat}", nameX, curY + 28, 0xFF8892B0.toInt(), false)

        curY += avatarSize + 14

        val toggleBtnW = panelW - 28
        val toggleBtnH = 18
        val toggleBtnX = panelX + 14
        val isDefeated = (node.status == RctProgressionStatus.DEFEATED)

        val toggleBg = if (isDefeated) 0xFF27AE60.toInt() else 0xFF2C3240.toInt()
        val toggleText = if (isDefeated) "✓ Dresseur Vaincu (Annuler)" else "Marquer comme Vaincu"

        guiGraphics.fill(toggleBtnX, curY, toggleBtnX + toggleBtnW, curY + toggleBtnH, toggleBg)
        guiGraphics.fill(toggleBtnX, curY, toggleBtnX + toggleBtnW, curY + 1, 0x44FFFFFF)
        guiGraphics.drawCenteredString(font, toggleText, toggleBtnX + toggleBtnW / 2, curY + 5, 0xFFFFFFFF.toInt())

        curY += toggleBtnH + 12

        val capBannerH = 26
        guiGraphics.fill(panelX + 14, curY, panelX + panelW - 14, curY + capBannerH, 0xFF1C2230.toInt())
        guiGraphics.fill(panelX + 14, curY, panelX + 17, curY + capBannerH, getCapBadgeColor(t.computedLevelCap))

        guiGraphics.drawString(font, "CAP DE NIVEAU DU COMBAT", panelX + 22, curY + 4, 0xFFA0AABF.toInt(), false)
        guiGraphics.drawString(font, "Nv. ${t.computedLevelCap}", panelX + 22, curY + 14, 0xFFF1C40F.toInt(), true)
        guiGraphics.drawString(font, "(Maximum toléré)", panelX + 70, curY + 14, 0xFF7F8C8D.toInt(), false)

        curY += capBannerH + 14

        val teamTitle = "ÉQUIPE ADVERSE (${t.team.size} Pokémon)"
        guiGraphics.drawString(font, teamTitle, panelX + 14, curY, 0xFFFFFFFF.toInt(), true)
        guiGraphics.fill(panelX + 14, curY + 10, panelX + panelW - 14, curY + 11, 0xFF282D3C.toInt())

        curY += 16

        for (pokemon in t.team) {
            val cardH = 38
            val cardW = panelW - 28
            val cardX = panelX + 14

            guiGraphics.fill(cardX, curY, cardX + cardW, curY + cardH, 0xFF181C26.toInt())
            guiGraphics.fill(cardX, curY, cardX + cardW, curY + 1, 0xFF242A38.toInt())
            guiGraphics.fill(cardX, curY, cardX + 1, curY + cardH, 0xFF242A38.toInt())

            val stack = getPokemonStack(pokemon.species, pokemon.aspects, pokemon.shiny)
            val spriteBoxSize = 28
            val spriteX = cardX + 5
            val spriteY = curY + 5

            guiGraphics.fill(spriteX, spriteY, spriteX + spriteBoxSize, spriteY + spriteBoxSize, 0xFF0D0F14.toInt())
            if (!stack.isEmpty) {
                guiGraphics.renderItem(stack, spriteX + 6, spriteY + 6)
            } else {
                guiGraphics.drawString(font, "●", spriteX + 10, spriteY + 10, 0xFF888888.toInt(), false)
            }

            val pokeName = pokemon.nickname ?: pokemon.species.substringAfter(':').replaceFirstChar { it.uppercase() }
            val infoX = spriteX + spriteBoxSize + 8

            guiGraphics.drawString(font, pokeName, infoX, curY + 6, 0xFFFFFFFF.toInt(), false)
            guiGraphics.drawString(font, "Nv. ${pokemon.level}", infoX, curY + 16, 0xFF2ECC71.toInt(), false)

            val detailParts = mutableListOf<String>()
            if (pokemon.ability != null) detailParts.add(pokemon.ability.substringAfter(':'))
            if (pokemon.heldItem != null) detailParts.add("@${pokemon.heldItem.substringAfter(':')}")
            val subText = detailParts.joinToString(" | ")
            if (subText.isNotBlank()) {
                val truncSub = font.plainSubstrByWidth(subText, cardW - (spriteBoxSize + 16))
                guiGraphics.drawString(font, truncSub, infoX, curY + 26, 0xFF7F8C8D.toInt(), false)
            }

            curY += cardH + 4
        }

        if (t.bag.isNotEmpty()) {
            curY += 8
            guiGraphics.drawString(font, "OBJETS EN SAC", panelX + 14, curY, 0xFFFFFFFF.toInt(), true)
            guiGraphics.fill(panelX + 14, curY + 10, panelX + panelW - 14, curY + 11, 0xFF282D3C.toInt())
            curY += 16

            for (item in t.bag) {
                val itemName = item.item.substringAfter(':').replace('_', ' ').replaceFirstChar { it.uppercase() }
                guiGraphics.drawString(font, "• $itemName x${item.quantity}", panelX + 18, curY, 0xFFBDC3C7.toInt(), false)
                curY += 12
            }
        }

        curY += 20
        maxDetailScroll = max(0.0, (curY - contentY + detailScrollOffset - contentH).toDouble())

        guiGraphics.disableScissor()

        val closeDrawerBtnX = panelX + panelW - 24
        val closeDrawerBtnY = panelY + 8
        guiGraphics.fill(closeDrawerBtnX, closeDrawerBtnY, closeDrawerBtnX + 16, closeDrawerBtnY + 16, 0xFF222634.toInt())
        guiGraphics.drawCenteredString(font, "✕", closeDrawerBtnX + 8, closeDrawerBtnY + 4, 0xFFFFFFFF.toInt())
    }

    private fun getPokemonStack(speciesName: String, aspects: List<String>, shiny: Boolean): ItemStack {
        val cacheKey = "$speciesName#${aspects.joinToString(",")}#$shiny"
        return pokeItemCache.computeIfAbsent(cacheKey) {
            try {
                val cleanName = speciesName.substringAfter(':').lowercase()
                val species = PokemonSpecies.getByName(cleanName) ?: return@computeIfAbsent ItemStack.EMPTY
                val aspectSet = aspects.toMutableSet()
                if (shiny) aspectSet.add("shiny")
                PokemonItem.from(species, aspectSet, 1)
            } catch (_: Throwable) {
                ItemStack.EMPTY
            }
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val headerH = 46
        val footerH = 22
        val canvasW = if (isDetailDrawerOpen) width - 240 else width

        // 1. Clic sur le panneau latéral de détails
        if (isDetailDrawerOpen && mouseX >= width - 240) {
            val closeBtnX = width - 24
            val closeBtnY = headerH + 8
            if (mouseX in closeBtnX.toDouble()..(closeBtnX + 16).toDouble() &&
                mouseY in closeBtnY.toDouble()..(closeBtnY + 16).toDouble()) {
                isDetailDrawerOpen = false
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                return true
            }

            val toggleBtnX = width - 240 + 14
            val toggleBtnY = headerH + 10 + 44 + 14 - detailScrollOffset
            val toggleBtnW = 240 - 28
            val toggleBtnH = 18

            if (mouseX >= toggleBtnX && mouseX <= toggleBtnX + toggleBtnW &&
                mouseY >= toggleBtnY && mouseY <= toggleBtnY + toggleBtnH) {
                selectedNode?.let { node ->
                    RctProgressionService.toggleTrainerDefeated(node.trainer.id)
                    reloadGraph()
                    selectedNode = graph?.nodes?.firstOrNull { it.trainer.id == node.trainer.id }
                    Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
                }
                return true
            }

            return true
        }

        // 2. Clic sur un nœud dresseur dans le canevas
        if (mouseX in 0.0..canvasW.toDouble() && mouseY in headerH.toDouble()..(height - footerH).toDouble()) {
            val worldMouseX = (mouseX - panX) / zoom
            val worldMouseY = (mouseY - panY) / zoom

            graph?.nodes?.let { nodes ->
                for (node in nodes) {
                    if (worldMouseX >= node.x && worldMouseX <= node.x + node.width &&
                        worldMouseY >= node.y && worldMouseY <= node.y + node.height) {

                        selectedNode = node
                        isDetailDrawerOpen = true
                        detailScrollOffset = 0.0
                        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                        return true
                    }
                }
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                isDraggingCanvas = true
                dragStartMouseX = mouseX
                dragStartMouseY = mouseY
                dragStartPanX = panX
                dragStartPanY = panY
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isDraggingCanvas) {
            panX = dragStartPanX + (mouseX - dragStartMouseX)
            panY = dragStartPanY + (mouseY - dragStartMouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        isDraggingCanvas = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val headerH = 46
        val footerH = 22

        if (isDetailDrawerOpen && mouseX >= width - 240) {
            detailScrollOffset = (detailScrollOffset - scrollY * 18.0).coerceIn(0.0, maxDetailScroll)
            return true
        }

        if (mouseY in headerH.toDouble()..(height - footerH).toDouble()) {
            val factor = if (scrollY > 0) 1.15 else 0.85
            zoomAt(mouseX, mouseY, factor)
            return true
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }
}
