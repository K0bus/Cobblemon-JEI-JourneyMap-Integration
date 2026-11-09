package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import fr.k0bus.cobblemon_jei.application.scan.GlobalScanService
import fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat
import fr.k0bus.cobblemon_jei.config.CobblemonModConfig
import fr.k0bus.cobblemon_jei.data.repository.PokemonDataRepository
import fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager
import fr.k0bus.cobblemon_jei.infrastructure.persistence.DiskChunkCacheStorage
import fr.k0bus.cobblemon_jei.journeymap.ChunkBiomeTagCache
import fr.k0bus.cobblemon_jei.journeymap.CobblemonJourneyMapPlugin
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.level.Level
import kotlin.math.max
import kotlin.math.min

/**
 * Écran de configuration, de contrôle du scan global et de diagnostic pour le mod Cobblemon & JourneyMap.
 * Entièrement aligné sur la Direction Artistique (DA) sombre du mod avec boutons custom et zéro chevauchement.
 */
class CobblemonSettingsScreen(
    private val parentScreen: Screen? = null
) : Screen(Component.translatable("gui.cobblemon_jei.settings.title")) {

    enum class SettingsTab(private val defaultLabel: String, val iconRes: ResourceLocation, val translationKey: String) {
        OPTIONS("Options & Radar", CobblemonPokedexScreen.SETTINGS_ICON, "gui.cobblemon_jei.settings.tab.options"),
        WEB_SERVER("Écran 2 & Web", CobblemonPokedexScreen.TRACKER_ICON, "gui.cobblemon_jei.settings.tab.web"),
        GLOBAL_SCAN("Cartographie & Scan", CobblemonPokedexScreen.EXPEDITION_ICON, "gui.cobblemon_jei.settings.tab.scan"),
        INFO("Statut & Info", CobblemonPokedexScreen.POKEDEX_ICON, "gui.cobblemon_jei.settings.tab.info");

        val label: String
            get() {
                val trans = Component.translatable(translationKey).string
                return if (trans == translationKey) defaultLabel else trans
            }
    }

    private var currentTab: SettingsTab = SettingsTab.OPTIONS
    private var forceDeepScan: Boolean = true

    override fun init() {
        super.init()
        clearWidgets()

        val modalW = min(560, width - 24)
        val modalH = min(360, height - 32)
        val modalX = (width - modalW) / 2
        val modalY = (height - modalH) / 2

        val tabY = modalY + 28
        val tabW = (modalW - 16) / SettingsTab.values().size
        val tabH = 20

        // Boutons d'onglets au style CobblemonDarkButton
        for ((index, tab) in SettingsTab.values().withIndex()) {
            val tabX = modalX + 8 + index * tabW
            val btn = CobblemonDarkButton(
                tabX, tabY, tabW - 2, tabH,
                Component.literal(tab.label),
                tab.iconRes,
                isTab = true,
                isSelected = { currentTab == tab }
            ) {
                currentTab = tab
                init()
                minecraft?.soundManager?.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            }
            addRenderableWidget(btn)
        }

        // Bouton Fermer (croix en haut à droite)
        val closeBtn = CobblemonDarkButton(modalX + modalW - 22, modalY + 4, 16, 16, Component.literal("✕")) {
            onClose()
        }
        addRenderableWidget(closeBtn)

        // Bouton Retour / Fermer au bas
        val returnBtnW = 110
        val returnBtnH = 20
        val returnBtnX = modalX + (modalW - returnBtnW) / 2
        val returnBtnY = modalY + modalH - returnBtnH - 8

        val returnBtn = CobblemonDarkButton(returnBtnX, returnBtnY, returnBtnW, returnBtnH, Component.translatable("gui.cobblemon_jei.settings.back")) {
            onClose()
        }
        addRenderableWidget(returnBtn)

        val contentY = tabY + tabH + 8

        when (currentTab) {
            SettingsTab.OPTIONS -> initOptionsWidgets(modalX, contentY, modalW)
            SettingsTab.WEB_SERVER -> initWebServerWidgets(modalX, contentY, modalW)
            SettingsTab.GLOBAL_SCAN -> initScanWidgets(modalX, contentY, modalW)
            SettingsTab.INFO -> initInfoWidgets(modalX, contentY, modalW)
        }
    }

    private fun initWebServerWidgets(modalX: Int, contentY: Int, modalW: Int) {
        val actionBtnW = 240
        val actionBtnX = modalX + (modalW - actionBtnW) / 2
        var y = contentY + 95

        // Bouton Ouvrir dans le navigateur
        val openBtn = CobblemonDarkButton(actionBtnX, y, actionBtnW, 22, Component.translatable("gui.cobblemon_jei.settings.open_web")) {
            try {
                net.minecraft.Util.getPlatform().openUri(java.net.URI.create("http://localhost:${fr.k0bus.cobblemon_jei.web.WebServerService.activePort}"))
            } catch (e: Throwable) {
                minecraft?.player?.displayClientMessage(Component.translatable("gui.cobblemon_jei.settings.open_web_error", e.message ?: ""), true)
            }
        }
        openBtn.tooltip = Tooltip.create(Component.translatable("gui.cobblemon_jei.settings.open_web_tooltip"))
        addRenderableWidget(openBtn)
        y += 28

        // Bouton Activer / Désactiver le serveur
        val isRunning = fr.k0bus.cobblemon_jei.web.WebServerService.isRunning
        val toggleComp = if (isRunning) Component.translatable("gui.cobblemon_jei.settings.stop_web") else Component.translatable("gui.cobblemon_jei.settings.start_web")
        val toggleBtn = CobblemonDarkButton(actionBtnX, y, actionBtnW, 20, toggleComp) {
            if (isRunning) {
                fr.k0bus.cobblemon_jei.web.WebServerService.stop()
            } else {
                fr.k0bus.cobblemon_jei.web.WebServerService.start()
            }
            init()
        }
        addRenderableWidget(toggleBtn)
        y += 26

        // Bouton Redémarrer
        if (isRunning) {
            val restartBtn = CobblemonDarkButton(actionBtnX, y, actionBtnW, 20, Component.translatable("gui.cobblemon_jei.settings.restart_web")) {
                fr.k0bus.cobblemon_jei.web.WebServerService.stop()
                fr.k0bus.cobblemon_jei.web.WebServerService.start()
                init()
            }
            addRenderableWidget(restartBtn)
        }
    }

    private fun initOptionsWidgets(modalX: Int, contentY: Int, modalW: Int) {
        val rowH = 24
        var y = contentY + 4
        val btnW = 90
        val btnH = 18
        val btnX = modalX + modalW - btnW - 16

        val enabledComp = Component.translatable("gui.cobblemon_jei.settings.enabled")
        val disabledComp = Component.translatable("gui.cobblemon_jei.settings.disabled")
        val yesComp = Component.translatable("gui.cobblemon_jei.settings.yes")
        val noComp = Component.translatable("gui.cobblemon_jei.settings.no")

        // 1. Notifications HUD
        addRenderableWidget(
            CobblemonDarkButton(btnX, y, btnW, btnH, if (CobblemonModConfig.hudNotificationsEnabled) enabledComp else disabledComp) {
                CobblemonModConfig.hudNotificationsEnabled = !CobblemonModConfig.hudNotificationsEnabled
                PokemonTrackingManager.saveConfig()
                init()
            }
        )
        y += rowH

        // 2. Son d'alerte HUD
        addRenderableWidget(
            CobblemonDarkButton(btnX, y, btnW, btnH, if (CobblemonModConfig.hudSoundEnabled) enabledComp else disabledComp) {
                CobblemonModConfig.hudSoundEnabled = !CobblemonModConfig.hudSoundEnabled
                PokemonTrackingManager.saveConfig()
                init()
            }
        )
        y += rowH

        // 3. Halo lumineux in-world
        addRenderableWidget(
            CobblemonDarkButton(btnX, y, btnW, btnH, if (CobblemonModConfig.inWorldGlowing) enabledComp else disabledComp) {
                CobblemonModConfig.inWorldGlowing = !CobblemonModConfig.inWorldGlowing
                init()
            }
        )
        y += rowH

        // 4. Particules in-world
        addRenderableWidget(
            CobblemonDarkButton(btnX, y, btnW, btnH, if (CobblemonModConfig.inWorldParticles) enabledComp else disabledComp) {
                CobblemonModConfig.inWorldParticles = !CobblemonModConfig.inWorldParticles
                init()
            }
        )
        y += rowH

        // 5. Talent Caché Uniquement
        addRenderableWidget(
            CobblemonDarkButton(btnX, y, btnW, btnH, if (CobblemonModConfig.filterOnlyHiddenAbility) yesComp else noComp) {
                CobblemonModConfig.filterOnlyHiddenAbility = !CobblemonModConfig.filterOnlyHiddenAbility
                init()
            }
        )
        y += rowH

        // 6. Seuil IVs parfaits (0 à 6)
        val ivText = when (CobblemonModConfig.filterMinPerfectIvs) {
            0 -> "Tous"
            6 -> "6 IVs (Parfait)"
            else -> "${CobblemonModConfig.filterMinPerfectIvs}+ IVs"
        }
        addRenderableWidget(
            CobblemonDarkButton(btnX, y, btnW, btnH, Component.literal(ivText)) {
                CobblemonModConfig.filterMinPerfectIvs = (CobblemonModConfig.filterMinPerfectIvs + 1) % 7
                init()
            }
        )
        y += rowH

        // 7. Filtre de taille
        val sizeText = when (CobblemonModConfig.filterSize) {
            SizeFilter.ANY -> "Toutes"
            SizeFilter.RARE_ONLY -> "Rares"
            SizeFilter.ONLY_MICROSCOPIC -> "Micro"
            SizeFilter.ONLY_GIGANTIC -> "Géant"
        }
        addRenderableWidget(
            CobblemonDarkButton(btnX, y, btnW, btnH, Component.literal(sizeText)) {
                CobblemonModConfig.filterSize = CobblemonModConfig.filterSize.next()
                init()
            }
        )
        y += rowH

        // 8. Découverte des Légendaires & Monuments
        val legMode = CobblemonModConfig.legendaryTrackingMode
        val legText = when (legMode) {
            LegendaryMonumentsCompat.MonumentTrackingMode.ALL -> Component.translatable("gui.cobblemon_jei.settings.legendary_short.all")
            LegendaryMonumentsCompat.MonumentTrackingMode.MAJOR_ONLY -> Component.translatable("gui.cobblemon_jei.settings.legendary_short.major")
            LegendaryMonumentsCompat.MonumentTrackingMode.FOOTPRINTS_ONLY -> Component.translatable("gui.cobblemon_jei.settings.legendary_short.footprints")
            LegendaryMonumentsCompat.MonumentTrackingMode.DISABLED -> Component.translatable("gui.cobblemon_jei.settings.legendary_short.disabled")
        }
        val legBtn = CobblemonDarkButton(btnX, y, btnW, btnH, legText) {
            CobblemonModConfig.legendaryTrackingMode = CobblemonModConfig.legendaryTrackingMode.next()
            init()
        }
        legBtn.tooltip = Tooltip.create(
            Component.translatable(
                "gui.cobblemon_jei.settings.legendary_mode_tooltip",
                Component.translatable(legMode.translationKey)
            )
        )
        addRenderableWidget(legBtn)
        y += rowH

        // 9. Vider la Watchlist
        val clearBtn = CobblemonDarkButton(btnX, y, btnW, btnH, Component.translatable("gui.cobblemon_jei.settings.clear_watchlist")) {
            PokemonTrackingManager.clearWatchlist()
            minecraft?.player?.displayClientMessage(Component.translatable("gui.cobblemon_jei.settings.clear_watchlist_msg"), true)
            init()
        }
        clearBtn.tooltip = Tooltip.create(Component.translatable("gui.cobblemon_jei.settings.clear_watchlist_tooltip"))
        addRenderableWidget(clearBtn)
    }

    private fun initScanWidgets(modalX: Int, contentY: Int, modalW: Int) {
        val actionBtnW = 230
        val actionBtnX = modalX + (modalW - actionBtnW) / 2
        val actionsStartY = contentY + 114

        // 1. Bouton de bascule Mode Approfondi / Incrémental
        val deepBtnComp = if (forceDeepScan) Component.translatable("gui.cobblemon_jei.settings.deep_scan_full") else Component.translatable("gui.cobblemon_jei.settings.deep_scan_inc")
        val deepBtn = CobblemonDarkButton(actionBtnX, actionsStartY, actionBtnW, 20, deepBtnComp) {
            forceDeepScan = !forceDeepScan
            init()
        }
        deepBtn.tooltip = Tooltip.create(Component.translatable("gui.cobblemon_jei.settings.deep_scan_tooltip"))
        addRenderableWidget(deepBtn)

        // 2. Bouton Lancer Scan Global ou Interrompre
        if (GlobalScanService.isScanning) {
            val cancelBtn = CobblemonDarkButton(actionBtnX, actionsStartY + 26, actionBtnW, 22, Component.translatable("gui.cobblemon_jei.settings.cancel_scan")) {
                GlobalScanService.cancelScan()
                init()
            }
            addRenderableWidget(cancelBtn)
        } else {
            val scanBtn = CobblemonDarkButton(actionBtnX, actionsStartY + 26, actionBtnW, 22, Component.translatable("gui.cobblemon_jei.settings.start_scan")) {
                GlobalScanService.startScan(forceRefresh = forceDeepScan, onProgressUpdate = {
                    // Update live
                }, onComplete = {
                    // Complete
                })
                init()
            }
            scanBtn.tooltip = Tooltip.create(Component.translatable("gui.cobblemon_jei.settings.start_scan_tooltip"))
            addRenderableWidget(scanBtn)

            // 3. Bouton Vider le cache mémoire
            val wipeBtn = CobblemonDarkButton(actionBtnX, actionsStartY + 54, actionBtnW, 20, Component.translatable("gui.cobblemon_jei.settings.clear_cache")) {
                GlobalScanService.clearCache()
                minecraft?.player?.displayClientMessage(Component.translatable("gui.cobblemon_jei.settings.clear_cache_msg"), true)
                init()
            }
            wipeBtn.tooltip = Tooltip.create(Component.translatable("gui.cobblemon_jei.settings.clear_cache_tooltip"))
            addRenderableWidget(wipeBtn)
        }
    }

    private fun initInfoWidgets(modalX: Int, contentY: Int, modalW: Int) {
        // Informations statiques et dynamiques affichées directement dans render()
    }

    override fun isPauseScreen(): Boolean = false

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Neutralise complètement le shader de flou (menu_blur) de Minecraft vanilla
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // 1. Fond sombre moderne et opaque
        guiGraphics.fill(0, 0, width, height, 0xF00E121B.toInt())

        val modalW = min(560, width - 24)
        val modalH = min(360, height - 32)
        val modalX = (width - modalW) / 2
        val modalY = (height - modalH) / 2

        // 2. Fenêtre modale opaque et nette (DA Cobblemon)
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF141724.toInt())
        guiGraphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY, 0xFF353D52.toInt())
        guiGraphics.fill(modalX - 1, modalY + modalH, modalX + modalW + 1, modalY + modalH + 1, 0xFF353D52.toInt())
        guiGraphics.fill(modalX - 1, modalY, modalX, modalY + modalH, 0xFF353D52.toInt())
        guiGraphics.fill(modalX + modalW, modalY, modalX + modalW + 1, modalY + modalH, 0xFF353D52.toInt())

        // 3. Barre de titre opaque
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 24, 0xFF1B2030.toInt())
        guiGraphics.fill(modalX, modalY + 23, modalX + modalW, modalY + 24, 0xFF2B334C.toInt())

        // 4. Rendu des widgets (boutons d'onglets, toggles, boutons d'action)
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // 5. Rendu du texte et des informations par-dessus pour une netteté absolue
        guiGraphics.drawString(font, Component.translatable("gui.cobblemon_jei.settings.header"), modalX + 10, modalY + 8, 0xFFFFAA00.toInt(), false)
        val verText = Component.translatable("gui.cobblemon_jei.settings.version").string
        val verW = font.width(verText)
        guiGraphics.drawString(font, verText, modalX + modalW - verW - 30, modalY + 8, 0xFFAAAAAA.toInt(), false)

        val contentY = modalY + 28 + 20 + 8

        when (currentTab) {
            SettingsTab.OPTIONS -> renderOptions(guiGraphics, modalX, contentY, modalW)
            SettingsTab.WEB_SERVER -> renderWebServer(guiGraphics, modalX, contentY, modalW)
            SettingsTab.GLOBAL_SCAN -> renderScan(guiGraphics, modalX, contentY, modalW)
            SettingsTab.INFO -> renderInfo(guiGraphics, modalX, contentY, modalW)
        }
    }

    private fun renderWebServer(guiGraphics: GuiGraphics, modalX: Int, contentY: Int, modalW: Int) {
        val y = contentY + 4
        guiGraphics.drawString(font, Component.translatable("gui.cobblemon_jei.settings.web_header"), modalX + 14, y, 0xFFFFAA00.toInt(), false)
        guiGraphics.drawString(font, Component.translatable("gui.cobblemon_jei.settings.web_desc"), modalX + 14, y + 12, 0xFFCCCCCC.toInt(), false)

        val cardX = modalX + 14
        val cardY = contentY + 30
        val cardW = modalW - 28
        val cardH = 56

        guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xFF161A26.toInt())
        guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + 1, 0xFF282D3C.toInt())
        guiGraphics.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0xFF282D3C.toInt())
        guiGraphics.fill(cardX, cardY, cardX + 1, cardY + cardH, 0xFF282D3C.toInt())
        guiGraphics.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0xFF282D3C.toInt())

        val isRunning = fr.k0bus.cobblemon_jei.web.WebServerService.isRunning
        val statusText = if (isRunning) Component.translatable("gui.cobblemon_jei.settings.web_active").string else Component.translatable("gui.cobblemon_jei.settings.web_stopped").string
        val port = fr.k0bus.cobblemon_jei.web.WebServerService.activePort
        val url = if (isRunning) "http://localhost:$port" else "Désactivé"

        guiGraphics.drawString(font, Component.translatable("gui.cobblemon_jei.settings.web_status", statusText).string, cardX + 10, cardY + 8, 0xFFFFFFFF.toInt(), false)
        guiGraphics.drawString(font, Component.translatable("gui.cobblemon_jei.settings.web_url", url).string, cardX + 10, cardY + 22, 0xFFFFFFFF.toInt(), false)
        guiGraphics.drawString(font, Component.translatable("gui.cobblemon_jei.settings.web_proto"), cardX + 10, cardY + 36, 0xFF888888.toInt(), false)
    }

    private fun renderOptions(guiGraphics: GuiGraphics, modalX: Int, contentY: Int, modalW: Int) {
        val rowH = 24
        var y = contentY + 8

        val labels = listOf(
            Component.translatable("gui.cobblemon_jei.settings.opt_hud_title").string to Component.translatable("gui.cobblemon_jei.settings.opt_hud_desc").string,
            Component.translatable("gui.cobblemon_jei.settings.opt_sound_title").string to Component.translatable("gui.cobblemon_jei.settings.opt_sound_desc").string,
            Component.translatable("gui.cobblemon_jei.settings.opt_glow_title").string to Component.translatable("gui.cobblemon_jei.settings.opt_glow_desc").string,
            Component.translatable("gui.cobblemon_jei.settings.opt_particles_title").string to Component.translatable("gui.cobblemon_jei.settings.opt_particles_desc").string,
            Component.translatable("gui.cobblemon_jei.settings.opt_ha_title").string to Component.translatable("gui.cobblemon_jei.settings.opt_ha_desc").string,
            Component.translatable("gui.cobblemon_jei.settings.opt_iv_title").string to Component.translatable("gui.cobblemon_jei.settings.opt_iv_desc").string,
            Component.translatable("gui.cobblemon_jei.settings.opt_size_title").string to Component.translatable("gui.cobblemon_jei.settings.opt_size_desc").string,
            Component.translatable("gui.cobblemon_jei.settings.opt_legendary_title").string to Component.translatable("gui.cobblemon_jei.settings.opt_legendary_desc").string,
            Component.translatable("gui.cobblemon_jei.settings.opt_watchlist_title").string to Component.translatable("gui.cobblemon_jei.settings.opt_watchlist_desc", PokemonTrackingManager.customWatchlist.size).string
        )

        for ((title, desc) in labels) {
            guiGraphics.drawString(font, "§f$title", modalX + 14, y - 2, 0xFFFFFFFF.toInt(), false)
            guiGraphics.drawString(font, "§8$desc", modalX + 14, y + 8, 0xFF888888.toInt(), false)
            y += rowH
        }
    }

    private fun renderScan(guiGraphics: GuiGraphics, modalX: Int, contentY: Int, modalW: Int) {
        val y = contentY + 4
        guiGraphics.drawString(font, Component.translatable("gui.cobblemon_jei.settings.mapping_header"), modalX + 14, y, 0xFFFFAA00.toInt(), false)
        guiGraphics.drawString(font, Component.translatable("gui.cobblemon_jei.settings.mapping_desc"), modalX + 14, y + 12, 0xFFCCCCCC.toInt(), false)

        // Compteurs de chunks en cache dans une carte dédiée DA sombre
        val mc = minecraft
        val dim = mc?.level?.dimension() ?: Level.OVERWORLD
        val surfaceChunks = ChunkBiomeTagCache.getChunkProfilesForDimension(dim).size
        val subProfiles = ChunkBiomeTagCache.getAllSubterraneanProfiles(dim)
        val subCount = subProfiles.values.sumOf { it.size }

        val statusBoxX = modalX + 14
        val statusBoxY = contentY + 30
        val statusBoxW = modalW - 28
        val statusBoxH = 56

        guiGraphics.fill(statusBoxX, statusBoxY, statusBoxX + statusBoxW, statusBoxY + statusBoxH, 0xFF161A26.toInt())
        guiGraphics.fill(statusBoxX, statusBoxY, statusBoxX + statusBoxW, statusBoxY + 1, 0xFF282D3C.toInt())
        guiGraphics.fill(statusBoxX, statusBoxY + statusBoxH - 1, statusBoxX + statusBoxW, statusBoxY + statusBoxH, 0xFF282D3C.toInt())
        guiGraphics.fill(statusBoxX, statusBoxY, statusBoxX + 1, statusBoxY + statusBoxH, 0xFF282D3C.toInt())
        guiGraphics.fill(statusBoxX + statusBoxW - 1, statusBoxY, statusBoxX + statusBoxW, statusBoxY + statusBoxH, 0xFF282D3C.toInt())

        guiGraphics.drawString(font, Component.translatable("gui.cobblemon_jei.settings.dimension_info", dim.location().toString()).string, statusBoxX + 10, statusBoxY + 6, 0xFFFFFFFF.toInt(), false)
        guiGraphics.drawString(font, Component.translatable("gui.cobblemon_jei.settings.surface_chunks", surfaceChunks).string, statusBoxX + 10, statusBoxY + 18, 0xFFFFFFFF.toInt(), false)
        guiGraphics.drawString(font, Component.translatable("gui.cobblemon_jei.settings.sub_chunks", subCount).string, statusBoxX + 10, statusBoxY + 30, 0xFFFFFFFF.toInt(), false)
        val scanMsg = Component.translatable("gui.cobblemon_jei.settings.scan_status", GlobalScanService.statusMessage).string
        guiGraphics.drawString(font, scanMsg, statusBoxX + 10, statusBoxY + 42, 0xFFFFAA00.toInt(), false)

        // Barre de progression (située entre la boîte de statut et les boutons d'action)
        if (GlobalScanService.isScanning || GlobalScanService.progressPercent > 0) {
            val barY = statusBoxY + statusBoxH + 6
            val barH = 10
            val barW = statusBoxW
            guiGraphics.fill(statusBoxX, barY, statusBoxX + barW, barY + barH, 0xFF10141D.toInt())
            val filledW = (barW * (GlobalScanService.progressPercent / 100.0)).toInt().coerceIn(0, barW)
            guiGraphics.fill(statusBoxX, barY, statusBoxX + filledW, barY + barH, 0xFF2ECC71.toInt())
            guiGraphics.drawCenteredString(font, "${GlobalScanService.progressPercent}%", statusBoxX + barW / 2, barY + 1, 0xFFFFFFFF.toInt())
        }
    }

    private fun renderInfo(guiGraphics: GuiGraphics, modalX: Int, contentY: Int, modalW: Int) {
        var y = contentY + 6
        val mc = minecraft
        val dim = mc?.level?.dimension() ?: Level.OVERWORLD
        val worldId = WorldSessionManager.activeSessionId ?: WorldSessionManager.resolveCurrentWorldId()
        val stats = PokemonTrackerService.getStats()
        val snapshot = PokemonDataRepository.getSnapshot()
        val jmConnected = CobblemonJourneyMapPlugin.clientApi != null
        val cacheFile = DiskChunkCacheStorage.getCacheFile(worldId, dim)

        val cardX = modalX + 14
        val cardW = modalW - 28
        val cardH = 200

        guiGraphics.fill(cardX, y - 2, cardX + cardW, y + cardH, 0xFF161A26.toInt())
        guiGraphics.fill(cardX, y - 2, cardX + cardW, y - 1, 0xFF282D3C.toInt())
        guiGraphics.fill(cardX, y + cardH - 1, cardX + cardW, y + cardH, 0xFF282D3C.toInt())
        guiGraphics.fill(cardX, y - 2, cardX + 1, y + cardH, 0xFF282D3C.toInt())
        guiGraphics.fill(cardX + cardW - 1, y - 2, cardX + cardW, y + cardH, 0xFF282D3C.toInt())

        y += 6
        val jmStatus = if (jmConnected) {
            "§a" + Component.translatable("gui.cobblemon_jei.settings.info.jm_connected").string
        } else {
            "§c" + Component.translatable("gui.cobblemon_jei.settings.info.jm_not_found").string
        }
        val cacheStatus = if (cacheFile.exists()) {
            Component.translatable("gui.cobblemon_jei.settings.info.cache_stat", cacheFile.name, cacheFile.length() / 1024).string
        } else {
            "${cacheFile.name} (" + Component.translatable("gui.cobblemon_jei.settings.info.cache_not_created").string + ")"
        }
        val infoItems = listOf(
            Component.translatable("gui.cobblemon_jei.settings.info.mod_id").string to "cobblemon_jei",
            Component.translatable("gui.cobblemon_jei.settings.info.version").string to "1.7.0 (Minecraft 1.21.1)",
            Component.translatable("gui.cobblemon_jei.settings.info.author").string to "K0bus",
            Component.translatable("gui.cobblemon_jei.settings.info.jm_integration").string to jmStatus,
            Component.translatable("gui.cobblemon_jei.settings.info.world_session").string to "§e$worldId",
            Component.translatable("gui.cobblemon_jei.settings.info.current_dim").string to "${dim.location()}",
            Component.translatable("gui.cobblemon_jei.settings.info.species_loaded").string to Component.translatable("gui.cobblemon_jei.settings.info.species_count", PokemonSpecies.species.size, snapshot.allSpawns.size).string,
            Component.translatable("gui.cobblemon_jei.settings.info.pokedex_progress").string to Component.translatable("gui.cobblemon_jei.settings.info.pokedex_stat", stats.caughtCount, stats.encounteredCount, stats.totalSpecies).string,
            Component.translatable("gui.cobblemon_jei.settings.info.cache_file").string to cacheStatus
        )

        for ((label, value) in infoItems) {
            guiGraphics.drawString(font, "§6• $label :", cardX + 12, y, 0xFFFFAA00.toInt(), false)
            guiGraphics.drawString(font, "§f$value", cardX + 180, y, 0xFFFFFFFF.toInt(), false)
            y += 19
        }
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }
}
