package com.cobblemon.jei.neoforge;

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

    public CobblemonJeiNeoForge() {
        LOGGER.info("Cobblemon JEI Integration (NeoForge) initialisé avec succès.");
        try {
            com.cobblemon.jei.journeymap.CobblemonJourneyMapOverlayHud.INSTANCE.register();
        } catch (Throwable t) {
            LOGGER.warn("Cobblemon JEI: Erreur lors de l'enregistrement de l'overlay HUD: {}", t.getMessage());
        }
    }
}
