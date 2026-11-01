package fr.k0bus.cobblemon_jei.neoforge

import fr.k0bus.cobblemon_jei.application.progression.ProgressionService
import fr.k0bus.cobblemon_jei.config.CobblemonModConfig
import fr.k0bus.cobblemon_jei.journeymap.CobblemonJourneyMapOverlayHud
import fr.k0bus.cobblemon_jei.journeymap.RadarCompassHud
import fr.k0bus.cobblemon_jei.journeymap.structure.StructureChunkScanner
import fr.k0bus.cobblemon_jei.leaderboard.CobblemonLeaderboardHud
import fr.k0bus.cobblemon_jei.pokedex.CobblemonKeyBindings
import fr.k0bus.cobblemon_jei.pokedex.CobblemonPokedexScreen
import fr.k0bus.cobblemon_jei.pokedex.PokedexNotificationHud
import fr.k0bus.cobblemon_jei.web.WebServerService
import net.minecraft.client.Minecraft
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.ChunkEvent
import org.slf4j.LoggerFactory

/**
 * Point d'entrée principal du mod sous NeoForge.
 * L'intégration JEI est découverte et chargée automatiquement via l'annotation @JeiPlugin.
 */
@Mod(value = "cobblemon_jei", dist = [Dist.CLIENT])
class CobblemonJeiNeoForge(modBus: IEventBus) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger("CobblemonJEI/NeoForge")
    }

    init {
        LOGGER.info("Cobblemon JEI Integration (NeoForge) initialisé avec succès.")
        try {
            CobblemonJourneyMapOverlayHud.register()
        } catch (t: Throwable) {
            LOGGER.warn("Cobblemon JEI: Erreur lors de l'enregistrement de l'overlay HUD: {}", t.message)
        }

        try {
            modBus.addListener<RegisterKeyMappingsEvent> { event ->
                event.register(CobblemonKeyBindings.OPEN_POKEDEX_KEY)
                event.register(CobblemonKeyBindings.TOGGLE_LEADERBOARD_HUD_KEY)
            }

            NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> { _ ->
                PokedexNotificationHud.tick()
                while (CobblemonKeyBindings.OPEN_POKEDEX_KEY.consumeClick()) {
                    val mc = Minecraft.getInstance()
                    if (mc.screen == null) {
                        mc.setScreen(CobblemonPokedexScreen(null))
                    }
                }
                while (CobblemonKeyBindings.TOGGLE_LEADERBOARD_HUD_KEY.consumeClick()) {
                    CobblemonLeaderboardHud.cycleOrToggle()
                }
            }

            NeoForge.EVENT_BUS.addListener<RenderGuiLayerEvent.Pre> { event ->
                if (VanillaGuiLayers.SCOREBOARD_SIDEBAR == event.name) {
                    if (CobblemonLeaderboardHud.shouldHideVanillaSidebar()) {
                        event.isCanceled = true
                    }
                }
            }

            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false) { event: RenderGuiEvent.Post ->
                PokedexNotificationHud.render(event.guiGraphics, event.partialTick)
                CobblemonLeaderboardHud.render(event.guiGraphics, event.partialTick)
                RadarCompassHud.render(event.guiGraphics, event.partialTick)
            }

            NeoForge.EVENT_BUS.addListener<ChunkEvent.Load> { event ->
                val chunk = event.chunk
                if (chunk is LevelChunk) {
                    StructureChunkScanner.onChunkLoaded(chunk)
                }
            }

            PokedexNotificationHud.init()
            ProgressionService.init()

            modBus.addListener<FMLLoadCompleteEvent> { _ ->
                try {
                    WebServerService.start(CobblemonModConfig.webServerPort)
                    LOGGER.info("Cobblemon JEI: WebServerService démarré après FMLLoadCompleteEvent.")
                } catch (t: Throwable) {
                    LOGGER.warn("Cobblemon JEI: Erreur lors du démarrage du WebServerService: {}", t.message)
                }
            }

            LOGGER.info("Cobblemon JEI: HUD de notification et ProgressionService initialisés.")
        } catch (t: Throwable) {
            LOGGER.warn("Cobblemon JEI: Erreur lors de l'enregistrement des événements HUD/touches: {}", t.message)
        }
    }
}
