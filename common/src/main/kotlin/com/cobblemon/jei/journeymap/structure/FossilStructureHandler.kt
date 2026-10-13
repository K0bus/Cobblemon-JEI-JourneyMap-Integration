package com.cobblemon.jei.journeymap.structure

import com.cobblemon.jei.journeymap.structure.DynamicStructureRegistryService.StructureMetadata
import journeymap.api.v2.client.IClientAPI
import journeymap.api.v2.client.display.Context
import journeymap.api.v2.client.display.MarkerOverlay
import journeymap.api.v2.client.model.MapImage
import journeymap.api.v2.client.model.TextProperties
import journeymap.api.v2.client.util.UIState
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BrushableBlock
import net.minecraft.world.level.block.entity.BrushableBlockEntity
import net.minecraft.world.level.chunk.LevelChunk
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestionnaire dédié à la détection et au rendu des sites archéologiques et fouilles de fossiles Cobblemon.
 *
 * Fonctionnalités :
 * 1. Création et gestion des [MarkerOverlay] JourneyMap pour la catégorie `"Cobblemon Fossils"`.
 * 2. Analyse des blocs archéologiques suspects ([BrushableBlockEntity], sable/gravier suspect) dans les chunks actifs.
 * 3. Génération d'infobulles détaillées avec les tables de fossiles et espèces préhistoriques potentielles.
 */
object FossilStructureHandler {

    const val MOD_ID = "cobblemon_jei"
    const val OVERLAY_GROUP_FOSSILS = "Cobblemon Fossils"

    private val logger = LoggerFactory.getLogger("CobblemonJM/FossilHandler")
    private val iconCache = ConcurrentHashMap<ResourceLocation, MapImage>()

    // Noms français formatés des fossiles et Pokémon correspondants
    private val FOSSIL_FRENCH_NAMES = mapOf(
        "helix_fossil" to "Fossile Nautile (Amonita)",
        "dome_fossil" to "Fossile Dôme (Kabuto)",
        "old_amber_fossil" to "Vieil Ambre (Ptéra)",
        "root_fossil" to "Fossile Racine (Lilia)",
        "claw_fossil" to "Fossile Griffe (Anorith)",
        "skull_fossil" to "Fossile Crâne (Kranidos)",
        "armor_fossil" to "Fossile Armure (Dinoclier)",
        "cover_fossil" to "Fossile Plaque (Carapagos)",
        "plume_fossil" to "Fossile Plume (Arkéapti)",
        "jaw_fossil" to "Fossile Mâchoire (Ptyranidur)",
        "sail_fossil" to "Fossile Nageoire (Amagara)",
        "fossilized_bird" to "Oiseau fossilisé (Galvagon / Galvagla)",
        "fossilized_fish" to "Poisson fossilisé (Hydragon / Hydragla)",
        "fossilized_drake" to "Dragon fossilisé (Galvagon / Hydragon)",
        "fossilized_dino" to "Dino fossilisé (Galvagla / Hydragla)"
    )

    /**
     * Crée un [MarkerOverlay] JourneyMap pour un site de fouille archéologique de fossiles.
     */
    fun createMarkerOverlay(
        structureId: ResourceLocation,
        pos: BlockPos,
        dimension: ResourceKey<Level>,
        metadata: StructureMetadata,
        isHighlighted: Boolean = false
    ): MarkerOverlay {
        val mapImage = getOrCreateMapImage(metadata.iconLocation, isHighlighted)
        val marker = MarkerOverlay(MOD_ID, pos, mapImage)

        marker.overlayGroupName = OVERLAY_GROUP_FOSSILS
        marker.dimension = dimension
        marker.setActiveUIs(Context.UI.Minimap, Context.UI.Fullscreen)
        marker.minZoom = UIState.FULLSCREEN_ZOOM_MIN
        marker.maxZoom = UIState.ZOOM_IN_MAX
        marker.displayOrder = if (isHighlighted) 1750 else 1450

        // Label court sous l'icône
        val labelPrefix = if (isHighlighted) "✦ " else "⛏ "
        marker.label = "$labelPrefix${metadata.displayName}"

        // Tooltip complet et soigné
        val tooltipBuilder = StringBuilder()
        tooltipBuilder.append("§6[Site Archéologique] §f${metadata.displayName}\n")
        tooltipBuilder.append("§7Dimension : §b${dimension.location().path} §8| §7Position : §e[${pos.x}, ${pos.y}, ${pos.z}]\n")
        tooltipBuilder.append("§8─────────────────────────\n")

        if (metadata.associatedFossils.isNotEmpty()) {
            tooltipBuilder.append("§eFossiles & Spécimens déterrés :\n")
            for (fossil in metadata.associatedFossils) {
                val frenchDesc = FOSSIL_FRENCH_NAMES[fossil] ?: fossil.replace('_', ' ')
                tooltipBuilder.append("§6• §f$frenchDesc\n")
            }
        } else if (metadata.associatedPokemonSpecies.isNotEmpty()) {
            tooltipBuilder.append("§ePokémon préhistoriques liés :\n")
            for (sp in metadata.associatedPokemonSpecies) {
                tooltipBuilder.append("§6• §f${sp.translatedName.string}\n")
            }
        } else {
            tooltipBuilder.append("§7Fouilles : Sable & Gravier suspects\n")
        }

        if (isHighlighted) {
            tooltipBuilder.append("§a✔ Correspondance avec le Pokémon suivi !\n")
        }

        marker.title = tooltipBuilder.toString().trimEnd()

        // Propriétés de texte stylisées
        val textColor = if (isHighlighted) 0xFFA502 else 0xECCC68
        val textProps = TextProperties()
            .setScale(1.05f)
            .setColor(textColor)
            .setBackgroundColor(0x1E1B18)
            .setBackgroundOpacity(0.85f)
            .setFontShadow(true)
            .setActiveUIs(Context.UI.Minimap, Context.UI.Fullscreen)

        marker.textProperties = textProps
        return marker
    }

    /**
     * Analyse un chunk chargé à la recherche de blocs archéologiques suspects
     * ([BrushableBlockEntity] ou [BrushableBlock]).
     *
     * @return Le centre moyen ou la coordonnée du premier bloc suspect trouvé, ou null si aucun.
     */
    fun scanSuspiciousBlocksInChunk(chunk: LevelChunk): BlockPos? {
        val blockEntities = chunk.blockEntities
        for ((pos, be) in blockEntities) {
            if (be is BrushableBlockEntity) {
                return pos
            }
        }

        // Si aucune BlockEntity trouvée dans le dictionnaire, vérification des sections de blocs
        for (sectionIndex in 0 until chunk.sectionsCount) {
            val section = chunk.getSection(sectionIndex)
            if (section.hasOnlyAir()) continue
            val sectionY = chunk.getSectionYFromSectionIndex(sectionIndex)
            val baseY = sectionY * 16

            for (x in 0..15) {
                for (z in 0..15) {
                    for (y in 0..15) {
                        val state = section.getBlockState(x, y, z)
                        if (state.block is BrushableBlock) {
                            return BlockPos(chunk.pos.minBlockX + x, baseY + y, chunk.pos.minBlockZ + z)
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Cache et prépare les MapImage JourneyMap pour éviter les instanciations répétées.
     */
    private fun getOrCreateMapImage(iconLoc: ResourceLocation, isHighlighted: Boolean): MapImage {
        val key = if (isHighlighted) ResourceLocation.fromNamespaceAndPath(iconLoc.namespace, "${iconLoc.path}#highlight") else iconLoc
        return iconCache.computeIfAbsent(key) {
            val size = if (isHighlighted) 28 else 24
            MapImage(iconLoc, size, size).centerAnchors()
        }
    }
}
