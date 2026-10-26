package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import fr.k0bus.cobblemon_jei.application.scan.GlobalScanService
import fr.k0bus.cobblemon_jei.config.CobblemonModConfig
import fr.k0bus.cobblemon_jei.data.repository.PokemonDataRepository
import fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager
import fr.k0bus.cobblemon_jei.infrastructure.persistence.DiskChunkCacheStorage
import fr.k0bus.cobblemon_jei.journeymap.ChunkBiomeTagCache
import fr.k0bus.cobblemon_jei.journeymap.CobblemonJourneyMapPlugin
import fr.k0bus.cobblemon_jei.journeymap.JourneyMapDiskScanner
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.level.Level
import kotlin.math.max
import kotlin.math.min

/**
 * Écran de configuration, de contrôle du scan global et de diagnostic pour le mod Cobblemon & JourneyMap.
 */
class CobblemonSettingsScreen(
    private val parentScreen: Screen? = null
) : Screen(Component.literal("Paramètres & Diagnostic Cobblemon JEI")) {

    enum class SettingsTab(val label: String, val icon: String) {
        OPTIONS("Options & Radar", "⚙"),
        GLOBAL_SCAN("Cartographie & Scan", "🌍"),
        INFO("Informations & Statut", "ℹ")
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
        val tabW = (modalW - 16) / 3
        val tabH = 20

        // Boutons d'onglets
        for ((index, tab) in SettingsTab.values().withIndex()) {
            val tabX = modalX + 8 + index * tabW
            val isSelected = currentTab == tab
            val label = "${tab.icon} ${tab.label}"
            val btn = Button.builder(Component.literal(label)) {
                currentTab = tab
                init()
                minecraft?.soundManager?.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            }.bounds(tabX, tabY, tabW - 2, tabH).build()
            addRenderableWidget(btn)
        }

        // Bouton Retour / Fermer au bas
        val returnBtnW = 100
        val returnBtnH = 20
        val returnBtnX = modalX + (modalW - returnBtnW) / 2
        val returnBtnY = modalY + modalH - returnBtnH - 8

        val returnBtn = Button.builder(Component.literal("⬅ Retour")) {
            onClose()
        }.bounds(returnBtnX, returnBtnY, returnBtnW, returnBtnH).build()
        addRenderableWidget(returnBtn)

        val contentY = tabY + tabH + 8

        when (currentTab) {
            SettingsTab.OPTIONS -> initOptionsWidgets(modalX, contentY, modalW)
            SettingsTab.GLOBAL_SCAN -> initScanWidgets(modalX, contentY, modalW)
            SettingsTab.INFO -> initInfoWidgets(modalX, contentY, modalW)
        }
    }

    private fun initOptionsWidgets(modalX: Int, contentY: Int, modalW: Int) {
        val rowH = 24
        var y = contentY + 4
        val btnW = 90
        val btnH = 18
        val btnX = modalX + modalW - btnW - 16

        // 1. Notifications HUD
        addRenderableWidget(
            Button.builder(Component.literal(if (CobblemonModConfig.hudNotificationsEnabled) "§aACTIVÉ" else "§7DÉSACTIVÉ")) {
                CobblemonModConfig.hudNotificationsEnabled = !CobblemonModConfig.hudNotificationsEnabled
                PokemonTrackingManager.saveConfig()
                init()
            }.bounds(btnX, y, btnW, btnH).build()
        )
        y += rowH

        // 2. Son d'alerte HUD
        addRenderableWidget(
            Button.builder(Component.literal(if (CobblemonModConfig.hudSoundEnabled) "§aACTIVÉ" else "§7DÉSACTIVÉ")) {
                CobblemonModConfig.hudSoundEnabled = !CobblemonModConfig.hudSoundEnabled
                PokemonTrackingManager.saveConfig()
                init()
            }.bounds(btnX, y, btnW, btnH).build()
        )
        y += rowH

        // 3. Halo lumineux in-world
        addRenderableWidget(
            Button.builder(Component.literal(if (CobblemonModConfig.inWorldGlowing) "§aACTIVÉ" else "§7DÉSACTIVÉ")) {
                CobblemonModConfig.inWorldGlowing = !CobblemonModConfig.inWorldGlowing
                init()
            }.bounds(btnX, y, btnW, btnH).build()
        )
        y += rowH

        // 4. Particules in-world
        addRenderableWidget(
            Button.builder(Component.literal(if (CobblemonModConfig.inWorldParticles) "§aACTIVÉ" else "§7DÉSACTIVÉ")) {
                CobblemonModConfig.inWorldParticles = !CobblemonModConfig.inWorldParticles
                init()
            }.bounds(btnX, y, btnW, btnH).build()
        )
        y += rowH

        // 5. Talent Caché Uniquement
        addRenderableWidget(
            Button.builder(Component.literal(if (CobblemonModConfig.filterOnlyHiddenAbility) "§dOUI" else "§7NON")) {
                CobblemonModConfig.filterOnlyHiddenAbility = !CobblemonModConfig.filterOnlyHiddenAbility
                init()
            }.bounds(btnX, y, btnW, btnH).build()
        )
        y += rowH

        // 6. Seuil IVs parfaits (0 à 6)
        val ivText = when (CobblemonModConfig.filterMinPerfectIvs) {
            0 -> "Tous"
            6 -> "6 IVs (Parfait)"
            else -> "${CobblemonModConfig.filterMinPerfectIvs}+ IVs"
        }
        addRenderableWidget(
            Button.builder(Component.literal(ivText)) {
                CobblemonModConfig.filterMinPerfectIvs = (CobblemonModConfig.filterMinPerfectIvs + 1) % 7
                init()
            }.bounds(btnX, y, btnW, btnH).build()
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
            Button.builder(Component.literal(sizeText)) {
                CobblemonModConfig.filterSize = CobblemonModConfig.filterSize.next()
                init()
            }.bounds(btnX, y, btnW, btnH).build()
        )
        y += rowH

        // 8. Vider la Watchlist
        val clearBtn = Button.builder(Component.literal("🗑 Vider")) {
            PokemonTrackingManager.clearWatchlist()
            minecraft?.player?.displayClientMessage(Component.literal("§eWatchlist vidée."), true)
            init()
        }.bounds(btnX, y, btnW, btnH).build()
        clearBtn.tooltip = Tooltip.create(Component.literal("Retire tous les Pokémon de la liste personnalisée"))
        addRenderableWidget(clearBtn)
    }

    private fun initScanWidgets(modalX: Int, contentY: Int, modalW: Int) {
        val actionBtnW = 200
        val actionBtnH = 22
        val actionBtnX = modalX + (modalW - actionBtnW) / 2
        var y = contentY + 110

        // Bouton de bascule Mode Approfondi
        val deepBtnW = 160
        val deepBtnX = modalX + (modalW - deepBtnW) / 2
        val deepBtn = Button.builder(Component.literal(if (forceDeepScan) "§a✔ Scan approfondi" else "§7✕ Scan incrémental")) {
            forceDeepScan = !forceDeepScan
            init()
        }.bounds(deepBtnX, contentY + 76, deepBtnW, 18).build()
        deepBtn.tooltip = Tooltip.create(Component.literal("Si activé, force l'analyse intégrale de tous les fichiers de région sur disque."))
        addRenderableWidget(deepBtn)

        if (GlobalScanService.isScanning) {
            val cancelBtn = Button.builder(Component.literal("✕ Interrompre le Scan")) {
                GlobalScanService.cancelScan()
                init()
            }.bounds(actionBtnX, y, actionBtnW, actionBtnH).build()
            addRenderableWidget(cancelBtn)
        } else {
            val scanBtn = Button.builder(Component.literal("🔄 Lancer un Scan Global")) {
                GlobalScanService.startScan(forceRefresh = forceDeepScan, onProgressUpdate = {
                    // Update live
                }, onComplete = {
                    // Complete
                })
                init()
            }.bounds(actionBtnX, y, actionBtnW, actionBtnH).build()
            scanBtn.tooltip = Tooltip.create(Component.literal("Scanne l'ensemble des chunks explorés pour mettre à jour la base de biomes et sous-sols."))
            addRenderableWidget(scanBtn)

            y += actionBtnH + 10

            val wipeBtnW = 160
            val wipeBtnX = modalX + (modalW - wipeBtnW) / 2
            val wipeBtn = Button.builder(Component.literal("🗑 Vider le cache mémoire")) {
                GlobalScanService.clearCache()
                minecraft?.player?.displayClientMessage(Component.literal("§6Cache de biomes réinitialisé."), true)
                init()
            }.bounds(wipeBtnX, y, wipeBtnW, 18).build()
            wipeBtn.tooltip = Tooltip.create(Component.literal("Efface les données de biomes en mémoire pour forcer une réévaluation propre."))
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
        // 1. Fond sombre moderne et opaque (AUCUN renderBackground pour éviter l'application du shader menu_blur)
        guiGraphics.fill(0, 0, width, height, 0xF00E121B.toInt())

        val modalW = min(560, width - 24)
        val modalH = min(360, height - 32)
        val modalX = (width - modalW) / 2
        val modalY = (height - modalH) / 2

        // 2. Fenêtre modale opaque et nette
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
        guiGraphics.drawString(font, "§6§lPARAMÈTRES & DIAGNOSTIC DU MOD§r", modalX + 10, modalY + 8, 0xFFFFAA00.toInt(), false)
        val verText = "§7Cobblemon & JourneyMap • v1.6.1"
        val verW = font.width(verText)
        guiGraphics.drawString(font, verText, modalX + modalW - verW - 10, modalY + 8, 0xFFAAAAAA.toInt(), false)

        val contentY = modalY + 28 + 20 + 8

        when (currentTab) {
            SettingsTab.OPTIONS -> renderOptions(guiGraphics, modalX, contentY, modalW)
            SettingsTab.GLOBAL_SCAN -> renderScan(guiGraphics, modalX, contentY, modalW)
            SettingsTab.INFO -> renderInfo(guiGraphics, modalX, contentY, modalW)
        }
    }

    private fun renderOptions(guiGraphics: GuiGraphics, modalX: Int, contentY: Int, modalW: Int) {
        val rowH = 24
        var y = contentY + 8

        val labels = listOf(
            "Bandeau HUD de capture & scan" to "Affiche la notification dynamique en haut de l'écran",
            "Effets sonores du bandeau HUD" to "Joue le jingle Pokédex lors des captures et scans",
            "Surbrillance in-world des cibles" to "Entoure les Pokémon ciblés d'un halo lumineux lumineux",
            "Particules in-world autour des cibles" to "Fait scintiller les Pokémon recherchés dans le monde",
            "Radar : Talents Cachés uniquement" to "Filtre uniquement les spécimens avec leur talent rare",
            "Radar : Seuil minimum d'IVs parfaits" to "Cible les Pokémon ayant au moins X statistiques à 31",
            "Radar : Filtre de taille Cobblemon" to "Recherche spécifiquement les tailles rares, micro ou géant",
            "Liste personnalisée (Watchlist)" to "${PokemonTrackingManager.customWatchlist.size} espèces actuellement enregistrées"
        )

        for ((title, desc) in labels) {
            guiGraphics.drawString(font, "§f$title", modalX + 14, y - 2, 0xFFFFFFFF.toInt(), false)
            guiGraphics.drawString(font, "§8$desc", modalX + 14, y + 8, 0xFF888888.toInt(), false)
            y += rowH
        }
    }

    private fun renderScan(guiGraphics: GuiGraphics, modalX: Int, contentY: Int, modalW: Int) {
        var y = contentY + 6
        guiGraphics.drawString(font, "§e§lCartographie & Analyse des Fichiers Régions§r", modalX + 14, y, 0xFFFFAA00.toInt(), false)
        y += 14
        guiGraphics.drawString(font, "§7Scanne les fichiers de régions Anvil (.mca) et caches JourneyMap pour", modalX + 14, y, 0xFFCCCCCC.toInt(), false)
        y += 10
        guiGraphics.drawString(font, "§7répertorier les biomes de surface et les cavités (Lush Caves, Deep Dark...).", modalX + 14, y, 0xFFCCCCCC.toInt(), false)

        y += 20
        // Compteurs de chunks en cache
        val mc = minecraft
        val dim = mc?.level?.dimension() ?: Level.OVERWORLD
        val surfaceChunks = ChunkBiomeTagCache.getChunkProfilesForDimension(dim).size
        val subProfiles = ChunkBiomeTagCache.getAllSubterraneanProfiles(dim)
        val subCount = subProfiles.values.sumOf { it.size }

        val statusBoxX = modalX + 14
        val statusBoxW = modalW - 28
        val statusBoxH = 68
        guiGraphics.fill(statusBoxX, y, statusBoxX + statusBoxW, y + statusBoxH, 0xFF1B2230.toInt())
        guiGraphics.fill(statusBoxX, y, statusBoxX + statusBoxW, y + 1, 0xFF2C394F.toInt())
        guiGraphics.fill(statusBoxX, y + statusBoxH - 1, statusBoxX + statusBoxW, y + statusBoxH, 0xFF2C394F.toInt())
        guiGraphics.fill(statusBoxX, y, statusBoxX + 1, y + statusBoxH, 0xFF2C394F.toInt())
        guiGraphics.fill(statusBoxX + statusBoxW - 1, y, statusBoxX + statusBoxW, y + statusBoxH, 0xFF2C394F.toInt())

        guiGraphics.drawString(font, "§b📍 Dimension : §f${dim.location()}", statusBoxX + 8, y + 6, 0xFFFFFFFF.toInt(), false)
        guiGraphics.drawString(font, "§a✔ Chunks surface en mémoire : §f$surfaceChunks", statusBoxX + 8, y + 18, 0xFFFFFFFF.toInt(), false)
        guiGraphics.drawString(font, "§6⛏ Chunks sous-sol (Lush, Deep Dark) : §f$subCount", statusBoxX + 8, y + 30, 0xFFFFFFFF.toInt(), false)

        val scanMsg = "§7Statut : §e${GlobalScanService.statusMessage}"
        guiGraphics.drawString(font, scanMsg, statusBoxX + 8, y + 46, 0xFFFFAA00.toInt(), false)

        // Barre de progression
        if (GlobalScanService.isScanning || GlobalScanService.progressPercent > 0) {
            val barY = y + statusBoxH + 8
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

        val infoItems = listOf(
            "Identifiant du Mod" to "cobblemon_jei",
            "Version" to "1.6.1 (Minecraft 1.21.1)",
            "Auteur" to "K0bus",
            "Intégration JourneyMap" to if (jmConnected) "§aConnectée (API v2 active)" else "§cNon détectée",
            "Monde / Session active" to "§e$worldId",
            "Dimension courante" to "${dim.location()}",
            "Espèces Cobblemon chargées" to "${PokemonSpecies.species.size} espèces (${snapshot.allSpawns.size} règles de spawn)",
            "Progression Pokédex" to "§a${stats.caughtCount} capturés§7 / §e${stats.encounteredCount} vus§7 sur §f${stats.totalSpecies}",
            "Fichier de cache disque" to "${cacheFile.name} (${if (cacheFile.exists()) "${cacheFile.length() / 1024} Ko" else "non créé"})"
        )

        for ((label, value) in infoItems) {
            guiGraphics.drawString(font, "§6• $label :", modalX + 16, y, 0xFFFFAA00.toInt(), false)
            guiGraphics.drawString(font, "§f$value", modalX + 175, y, 0xFFFFFFFF.toInt(), false)
            y += 18
        }
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }
}
