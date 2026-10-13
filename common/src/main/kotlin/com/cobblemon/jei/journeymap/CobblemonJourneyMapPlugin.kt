package com.cobblemon.jei.journeymap

import journeymap.api.v2.client.IClientAPI
import journeymap.api.v2.client.IClientPlugin
import journeymap.api.v2.client.JourneyMapPlugin
import journeymap.api.v2.common.event.ClientEventRegistry
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

import journeymap.api.v2.client.fullscreen.IThemeButton
import journeymap.api.v2.client.fullscreen.IFullscreen
import net.minecraft.network.chat.Component

/**
 * Plugin JourneyMap côté client pour Cobblemon.
 * Implémente [IClientPlugin] pour écouter le cycle de vie de JourneyMap,
 * enregistrer le bouton dans la barre d'outils plein écran et gérer
 * les couches de surbrillance polygonales des zones d'apparition de Pokémon.
 */
@Suppress("DEPRECATION")
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
@journeymap.api.v2.common.JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
class CobblemonJourneyMapPlugin : IClientPlugin {

    companion object {
        const val MOD_ID = "cobblemon_jei"
        private val logger = LoggerFactory.getLogger("CobblemonJM/Plugin")
        val ICON = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/cobblemon_icon.png")

        var currentThemeButton: IThemeButton? = null
        var currentFullscreen: IFullscreen? = null

        /**
         * Met à jour dynamiquement l'état, l'intitulé et l'infobulle du bouton JourneyMap
         * selon le Pokémon actuellement actif.
         */
        fun updateButton() {
            val button = currentThemeButton ?: return
            val active = CobblemonOverlayManager.activeSpecies
            val isActive = CobblemonOverlayManager.isOverlayActive
            val speciesName = active?.translatedName?.string

            button.setToggled(isActive)
            if (isActive && speciesName != null) {
                button.setLabels("Cobblemon: $speciesName", "Cobblemon Spawns")
                val radarStatus = PokemonRadarService.getStatusSummary(speciesName)
                val structStatus = com.cobblemon.jei.journeymap.structure.StructureOverlayManager.getStatusSummary(active)
                button.setTooltip(
                    "Cobblemon Spawns",
                    "§ePokémon suivi : §f$speciesName",
                    "§bRadar : $radarStatus",
                    "§6Structures : §7$structStatus",
                    "§7Cliquer pour changer ou désactiver"
                )
                try {
                    button.button?.message = Component.literal("Cobblemon: $speciesName")
                } catch (_: Throwable) {}
            } else {
                button.setLabels("Cobblemon Spawns", "Cobblemon Spawns")
                button.setTooltip(
                    "Cobblemon Spawns",
                    "§7Aucun Pokémon sélectionné",
                    "§7Cliquer pour rechercher un Pokémon"
                )
                try {
                    button.button?.message = Component.literal("Cobblemon Spawns")
                } catch (_: Throwable) {}
            }
        }
    }

    override fun getModId(): String = MOD_ID

    override fun initialize(api: IClientAPI) {
        logger.info("Cobblemon JourneyMap Plugin: Initialisation réussie.")
        CobblemonOverlayManager.initialize(api)
        try {
            com.cobblemon.jei.journeymap.structure.StructureOverlayManager.initialize(api)
        } catch (t: Throwable) {
            logger.error("Cobblemon JourneyMap Plugin: Erreur initialisation StructureOverlayManager: {}", t.message)
        }
        try {
            PokemonRadarService.initialize(api)
        } catch (t: Throwable) {
            logger.error("Cobblemon JourneyMap Plugin: Erreur initialisation RadarService: {}", t.message)
        }
        try {
            CobblemonJourneyMapOverlayHud.register()
        } catch (_: Throwable) {}

        // Enregistrement du bouton dans la barre d'outils plein écran JourneyMap
        ClientEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(MOD_ID) { event ->
            try {
                val active = CobblemonOverlayManager.activeSpecies
                val isActive = CobblemonOverlayManager.isOverlayActive
                val speciesName = active?.translatedName?.string
                val initialLabel = if (isActive && speciesName != null) "Cobblemon: $speciesName" else "Cobblemon Spawns"

                val button = event.themeButtonDisplay.addThemeToggleButton(
                    initialLabel,
                    "Cobblemon Spawns",
                    ICON,
                    isActive
                ) { btn ->
                    Minecraft.getInstance().setScreen(
                        CobblemonJourneyMapSearchScreen(btn, event.fullscreen)
                    )
                }

                currentThemeButton = button
                currentFullscreen = event.fullscreen
                updateButton()
            } catch (e: Exception) {
                logger.error("Cobblemon JourneyMap Plugin: Erreur ajout bouton: {}", e.message)
            }
        }
    }
}
