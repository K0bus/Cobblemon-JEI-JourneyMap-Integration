package fr.k0bus.cobblemon_jei.journeymap

import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionZone
import journeymap.api.v2.client.IClientAPI
import journeymap.api.v2.client.display.Context
import journeymap.api.v2.client.display.DisplayType
import journeymap.api.v2.client.display.PolygonOverlay
import journeymap.api.v2.client.model.ShapeProperties
import journeymap.api.v2.client.model.TextProperties
import journeymap.api.v2.client.util.UIState
import journeymap.api.v2.common.waypoint.WaypointFactory
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory

/**
 * Gestionnaire d'affichage cartographique pour les zones d'expédition sur JourneyMap.
 *
 * Implémente la cartographie honnête à 3 couches :
 * - Couche 1 : Habitat théorique compatible (polygones semi-transparents avec infobulle explicite).
 * - Couche 2 : Zone explorée (délimitations JourneyMap existantes).
 * - Couche 3 : Observations réelles (géré par [PokemonRadarService]).
 */
object ExpeditionMapOverlay {

    private const val MOD_ID = "cobblemon_jei"
    private const val OVERLAY_GROUP_EXPEDITION = "Cobblemon Expeditions"
    private val logger = LoggerFactory.getLogger("CobblemonJM/ExpeditionOverlay")

    var clientApi: IClientAPI? = null
    var activeExpeditionZone: ExpeditionZone? = null
        private set

    fun initialize(api: IClientAPI) {
        this.clientApi = api
    }

    /**
     * Affiche les chunks d'une zone d'expédition sous forme de polygone semi-transparent sur JourneyMap.
     */
    fun showZoneOverlay(zone: ExpeditionZone) {
        val api = clientApi ?: return
        clearExpeditionOverlays()
        activeExpeditionZone = zone

        val mc = Minecraft.getInstance()
        val playerY = mc.player?.blockPosition()?.y ?: 64

        val dimKey = ResourceKey.create(Registries.DIMENSION, zone.dimension)
        val mergedPolygons = ChunkPolygonMerger.mergeChunks(zone.sampleChunks, playerY)

        val shapeProps = ShapeProperties()
            .setStrokeColor(0xF1C40F)
            .setStrokeWidth(2f)
            .setStrokeOpacity(0.9f)
            .setFillColor(0xF39C12)
            .setFillOpacity(0.25f)

        val targetsStr = zone.matchingTargets.joinToString(", ") {
            it.target.speciesId.path.replaceFirstChar { c -> c.uppercase() }
        }

        val conditions = mutableListOf<String>()
        if (zone.weatherRequirements.isNotEmpty()) conditions.add("Météo : ${zone.weatherRequirements.joinToString()}")
        if (zone.timeRequirements.isNotEmpty()) conditions.add("Horaire : ${zone.timeRequirements.joinToString()}")
        val condStr = if (conditions.isNotEmpty()) "\n§e" + conditions.joinToString(" • ") else ""

        val tooltip = "§6§l[Expédition] ${zone.name}§r\n" +
                "§aHabitat compatible pour : §f$targetsStr$condStr\n" +
                "§7Distance : §f${zone.distanceToPlayer.toInt()} blocs\n" +
                "§8(Habitat théorique selon les biomes — présence non garantie en direct)"

        for (poly in mergedPolygons) {
            val overlay = PolygonOverlay(MOD_ID, dimKey, shapeProps, poly)
            overlay.overlayGroupName = OVERLAY_GROUP_EXPEDITION
            overlay.title = tooltip
            overlay.setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap)
            overlay.minZoom = UIState.FULLSCREEN_ZOOM_MIN
            overlay.maxZoom = UIState.ZOOM_IN_MAX

            val textProps = TextProperties()
                .setScale(1.2f)
                .setColor(0xFFF1C40F.toInt())
                .setBackgroundColor(0x181920)
                .setBackgroundOpacity(0.85f)
                .setFontShadow(true)
                .setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap)
            overlay.setTextProperties(textProps)

            try {
                api.show(overlay)
            } catch (e: Throwable) {
                logger.debug("Erreur lors de l'affichage de l'overlay d'expédition: {}", e.message)
            }
        }

        mc.player?.displayClientMessage(
            Component.literal("§6🗺 [Expédition]§r §fZone §e${zone.name} §fbalisée sur la carte (${zone.sampleChunks.size} chunks)."),
            true
        )
    }

    /**
     * Crée un Waypoint JourneyMap natif au centre géographique de la zone d'expédition.
     */
    fun createZoneWaypoint(zone: ExpeditionZone): Boolean {
        val api = clientApi ?: return false
        val dimKey = ResourceKey.create(Registries.DIMENSION, zone.dimension)

        return try {
            val waypointName = "[Expédition] ${zone.name}"
            val waypoint = WaypointFactory.createClientWaypoint(
                waypointName,
                zone.centerPos,
                "Expéditions",
                dimKey,
                true
            )
            api.addWaypoint(MOD_ID, waypoint)
            val coordStr = if (zone.isSubterranean) "[${zone.centerPos.x}, Y=${zone.centerPos.y}, ${zone.centerPos.z}]" else "[${zone.centerPos.x}, ${zone.centerPos.z}]"
            Minecraft.getInstance().player?.displayClientMessage(
                Component.literal("§a📍 Repère d'expédition créé : §f$waypointName §7aux coordonnées $coordStr"),
                false
            )
            true
        } catch (e: Throwable) {
            logger.warn("Impossible de créer le waypoint JourneyMap d'expédition: {}", e.message)
            false
        }
    }

    /**
     * Nettoie les overlays de zones d'expéditions.
     */
    fun clearExpeditionOverlays() {
        val api = clientApi ?: return
        try {
            api.removeAll(MOD_ID, DisplayType.Polygon)
        } catch (_: Throwable) {}
        activeExpeditionZone = null
    }
}
