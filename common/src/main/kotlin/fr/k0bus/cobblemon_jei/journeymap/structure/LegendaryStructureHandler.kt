package fr.k0bus.cobblemon_jei.journeymap.structure

import fr.k0bus.cobblemon_jei.journeymap.structure.DynamicStructureRegistryService.StructureMetadata
import journeymap.api.v2.client.display.Context
import journeymap.api.v2.client.display.MarkerOverlay
import journeymap.api.v2.client.model.MapImage
import journeymap.api.v2.client.model.TextProperties
import journeymap.api.v2.client.util.UIState
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestionnaire dédié à la détection, au centrage sur piédestal et au rendu
 * des monuments et sanctuaires de Pokémon légendaires (Cobblemon: Legendary Monuments, etc.).
 *
 * Fonctionnalités :
 * 1. Création et gestion des [MarkerOverlay] JourneyMap pour la catégorie `"Legendary Monuments"`.
 * 2. Détection précise des blocs de piédestal (`*_pedestal`), sanctuaires (`*_shrine`) et autels
 *    dans les chunks chargés pour centrer fidèlement le waypoint sur le bloc rituel.
 * 3. Rendu doré haut de gamme avec icônes dynamiques issues des assets du mod.
 */
object LegendaryStructureHandler {

    const val MOD_ID = "cobblemon_jei"
    const val OVERLAY_GROUP_LEGENDARY = "Legendary Monuments"

    private val logger = LoggerFactory.getLogger("CobblemonJM/LegendaryHandler")
    private val iconCache = ConcurrentHashMap<ResourceLocation, MapImage>()

    /**
     * Mots-clés désignant un bloc d'invocation, piédestal ou autel rituel.
     */
    private val PEDESTAL_KEYWORDS = listOf(
        "pedestal", "shrine", "altar", "cocoon", "jukebox", "statue", "boss_summon", "lock"
    )

    /**
     * Crée un [MarkerOverlay] doré et distinctif pour un monument ou sanctuaire de Pokémon légendaire.
     */
    fun createMarkerOverlay(
        structureId: ResourceLocation,
        pos: BlockPos,
        dimension: ResourceKey<Level>,
        metadata: StructureMetadata,
        pedestalPos: BlockPos? = null,
        isHighlighted: Boolean = false
    ): MarkerOverlay {
        val targetPos = pedestalPos ?: pos
        val iconLoc = StructureOverlayManager.LEGENDARY_STAR_ICON
        val mapImage = getOrCreateMapImage(iconLoc, isHighlighted)
        val marker = MarkerOverlay(MOD_ID, targetPos, mapImage)

        marker.overlayGroupName = OVERLAY_GROUP_LEGENDARY
        marker.dimension = dimension
        marker.setActiveUIs(Context.UI.Minimap, Context.UI.Fullscreen)
        marker.minZoom = UIState.FULLSCREEN_ZOOM_MIN
        marker.maxZoom = UIState.ZOOM_IN_MAX
        marker.displayOrder = if (isHighlighted) 1800 else 1600

        // Label doré distinctif affichant le NOM DE LA STRUCTURE
        val pokeTag = metadata.targetPokemonName?.replaceFirstChar { it.uppercase() }
        val structName = metadata.displayName
        val labelText = if (pokeTag != null && !structName.contains(pokeTag, ignoreCase = true)) {
            "★ $structName ($pokeTag)"
        } else {
            "★ $structName"
        }
        marker.label = labelText

        // Tooltip complet, élégant et soigné
        val tooltipBuilder = StringBuilder()
        tooltipBuilder.append("§6★ [Structure Légendaire] §e$structName\n")

        if (pokeTag != null) {
            tooltipBuilder.append("§dPokémon Légendaire lié : §f$pokeTag\n")
        }

        tooltipBuilder.append("§7Dimension : §b${dimension.location().path} §8| §7Position : §e[${targetPos.x}, ${targetPos.y}, ${targetPos.z}]\n")
        tooltipBuilder.append("§8─────────────────────────\n")

        if (pedestalPos != null) {
            tooltipBuilder.append("§a✔ Bloc rituel / Autel localisé précisément au centre !\n")
        } else {
            tooltipBuilder.append("§7Position : Zone centrale de la structure\n")
        }

        if (isHighlighted) {
            tooltipBuilder.append("§6✦ SÉLECTION EN COURS : Structure ciblée par la recherche !\n")
        }

        marker.title = tooltipBuilder.toString().trimEnd()

        // Typographie dorée haut de gamme
        val textColor = if (isHighlighted) 0xFFD700 else 0xFFA502
        val textProps = TextProperties()
            .setScale(1.1f)
            .setColor(textColor)
            .setBackgroundColor(0x231A29)
            .setBackgroundOpacity(0.90f)
            .setFontShadow(true)
            .setActiveUIs(Context.UI.Minimap, Context.UI.Fullscreen)

        marker.textProperties = textProps
        return marker
    }

    /**
     * Scanne un chunk chargé pour détecter la présence d'un piédestal ou d'un bloc d'autel
     * (`*_pedestal`, `*_shrine`, `*_altar`, etc.).
     *
     * @return La coordonnée exacte du piédestal le plus significatif, ou null si non trouvé.
     */
    fun findPedestalBlockInChunk(chunk: LevelChunk): BlockPos? {
        for (sectionIndex in 0 until chunk.sectionsCount) {
            val section = chunk.getSection(sectionIndex)
            if (section.hasOnlyAir()) continue
            val sectionY = chunk.getSectionYFromSectionIndex(sectionIndex)
            val baseY = sectionY * 16

            for (x in 0..15) {
                for (z in 0..15) {
                    for (y in 0..15) {
                        val state = section.getBlockState(x, y, z)
                        val blockId = BuiltInRegistries.BLOCK.getKey(state.block)
                        val blockPath = blockId.path.lowercase(Locale.ROOT)

                        if (isPedestalBlock(blockId.namespace, blockPath)) {
                            return BlockPos(chunk.pos.minBlockX + x, baseY + y, chunk.pos.minBlockZ + z)
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Vérifie si un nom de bloc correspond à un piédestal ou autel rituel.
     */
    fun isPedestalBlock(namespace: String, path: String): Boolean {
        if (namespace == "legendarymonuments") {
            return PEDESTAL_KEYWORDS.any { path.contains(it) }
        }
        return path.endsWith("_pedestal") || path.endsWith("_shrine") || path.endsWith("_altar")
    }

    /**
     * Cache et prépare les MapImage JourneyMap.
     */
    private fun getOrCreateMapImage(iconLoc: ResourceLocation, isHighlighted: Boolean): MapImage {
        val key = if (isHighlighted) ResourceLocation.fromNamespaceAndPath(iconLoc.namespace, "${iconLoc.path}#highlight") else iconLoc
        return iconCache.computeIfAbsent(key) {
            val size = if (isHighlighted) 28 else 24
            MapImage(iconLoc, 0, 0, 16, 16, 0xFFFFFF, 1.0f)
                .setDisplayWidth(size.toDouble())
                .setDisplayHeight(size.toDouble())
                .centerAnchors()
        }
    }
}
