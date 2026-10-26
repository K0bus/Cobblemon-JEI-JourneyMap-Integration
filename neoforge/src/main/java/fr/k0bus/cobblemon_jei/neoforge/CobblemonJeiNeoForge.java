package fr.k0bus.cobblemon_jei.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée principal du mod sous NeoForge.
 * L'intégration JEI est découverte et chargée automatiquement via l'annotation @JeiPlugin.
 */
@Mod(value = "cobblemon_jei", dist = Dist.CLIENT)
public class CobblemonJeiNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("CobblemonJEI/NeoForge");

    public CobblemonJeiNeoForge(net.neoforged.bus.api.IEventBus modBus) {
        LOGGER.info("Cobblemon JEI Integration (NeoForge) initialisé avec succès.");
        try {
            fr.k0bus.cobblemon_jei.journeymap.CobblemonJourneyMapOverlayHud.INSTANCE.register();
        } catch (Throwable t) {
            LOGGER.warn("Cobblemon JEI: Erreur lors de l'enregistrement de l'overlay HUD: {}", t.getMessage());
        }

        try {
            modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) -> {
                event.register(fr.k0bus.cobblemon_jei.pokedex.CobblemonKeyBindings.INSTANCE.getOPEN_POKEDEX_KEY());
                event.register(fr.k0bus.cobblemon_jei.pokedex.CobblemonKeyBindings.INSTANCE.getTOGGLE_LEADERBOARD_HUD_KEY());
            });

            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
                fr.k0bus.cobblemon_jei.pokedex.PokedexNotificationHud.INSTANCE.tick();
                while (fr.k0bus.cobblemon_jei.pokedex.CobblemonKeyBindings.INSTANCE.getOPEN_POKEDEX_KEY().consumeClick()) {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.screen == null) {
                        mc.setScreen(new fr.k0bus.cobblemon_jei.pokedex.CobblemonPokedexScreen(null));
                    }
                }
                while (fr.k0bus.cobblemon_jei.pokedex.CobblemonKeyBindings.INSTANCE.getTOGGLE_LEADERBOARD_HUD_KEY().consumeClick()) {
                    fr.k0bus.cobblemon_jei.leaderboard.CobblemonLeaderboardHud.INSTANCE.cycleOrToggle();
                }
            });

            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) -> {
                if (net.neoforged.neoforge.client.gui.VanillaGuiLayers.SCOREBOARD_SIDEBAR.equals(event.getName())) {
                    if (fr.k0bus.cobblemon_jei.leaderboard.CobblemonLeaderboardHud.INSTANCE.shouldHideVanillaSidebar()) {
                        event.setCanceled(true);
                    }
                }
            });

            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, false, (net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) -> {
                fr.k0bus.cobblemon_jei.pokedex.PokedexNotificationHud.INSTANCE.render(event.getGuiGraphics(), event.getPartialTick());
                fr.k0bus.cobblemon_jei.leaderboard.CobblemonLeaderboardHud.INSTANCE.render(event.getGuiGraphics(), event.getPartialTick());
                fr.k0bus.cobblemon_jei.journeymap.RadarCompassHud.INSTANCE.render(event.getGuiGraphics(), event.getPartialTick());
            });

            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {
                if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
                    fr.k0bus.cobblemon_jei.journeymap.structure.StructureChunkScanner.INSTANCE.onChunkLoaded(chunk);
                }
            });

            fr.k0bus.cobblemon_jei.pokedex.PokedexNotificationHud.INSTANCE.init();
            fr.k0bus.cobblemon_jei.application.progression.ProgressionService.INSTANCE.init();
            LOGGER.info("Cobblemon JEI: HUD de notification Pokédex et ProgressionService initialisés et enregistrés.");
        } catch (Throwable t) {
            LOGGER.warn("Cobblemon JEI: Erreur lors de l'enregistrement des événements HUD/touches: {}", t.getMessage());
        }
    }
}
