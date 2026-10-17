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
            });

            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
                while (fr.k0bus.cobblemon_jei.pokedex.CobblemonKeyBindings.INSTANCE.getOPEN_POKEDEX_KEY().consumeClick()) {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.screen == null) {
                        mc.setScreen(new fr.k0bus.cobblemon_jei.pokedex.CobblemonPokedexScreen(null));
                    }
                }
            });
            LOGGER.info("Cobblemon JEI: Touche de raccourci Pokédex enregistrée.");
        } catch (Throwable t) {
            LOGGER.warn("Cobblemon JEI: Erreur lors de l'enregistrement des touches: {}", t.getMessage());
        }
    }
}
