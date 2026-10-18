package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Type d'événement affiché par le bandeau HUD.
 */
enum class NotificationType {
    CAPTURED,
    SCANNED
}

/**
 * HUD de notification dynamique apparaissant en haut de l'écran lors de la capture
 * ou de l'analyse (Pokédex scanner) d'un Pokémon.
 *
 * Affiche :
 * - Le modèle 3D du Pokémon (avec ses variantes et état shiny)
 * - Le message "Capturé ! XXX (Forme XXX)" ou "Analysé ! XXX (Forme XXX)"
 * - Les compteurs de progression (Capturés, Vus, Total)
 * - La barre bicolore exacte du Pokédex (Vert = Capturé, Orange = Vu uniquement)
 */
object PokedexNotificationHud {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/NotificationHud")

    private data class ActiveNotification(
        val type: NotificationType,
        val species: Species,
        val form: FormData?,
        val shiny: Boolean,
        val stack: ItemStack,
        val stats: PokedexTrackerStats,
        val startTime: Long,
        val durationMs: Long = 5500L
    )

    private var currentNotification: ActiveNotification? = null
    private val recentNotifications = mutableMapOf<String, Long>()

    // Snapshot client du Pokédex pour détecter les nouveaux enregistrements
    private var isSnapshotInitialized = false
    private val clientKnowledgeSnapshot = mutableMapOf<ResourceLocation, PokedexEntryProgress>()
    private var tickCounter = 0

    /**
     * Initialisation des écouteurs d'événements Cobblemon.
     */
    fun init() {
        try {
            CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
                notify(
                    NotificationType.CAPTURED,
                    event.pokemon.species,
                    event.pokemon.form,
                    event.pokemon.shiny,
                    event.player.uuid
                )
            }

            CobblemonEvents.POKEMON_SCANNED.subscribe { event ->
                val p = event.scannedPokemonEntityData.pokemon
                notify(
                    NotificationType.SCANNED,
                    p.species,
                    p.form,
                    p.shiny,
                    event.player.uuid
                )
            }

            CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe { event ->
                val p = event.dataSource.pokemon
                val type = if (event.knowledge == PokedexEntryProgress.CAUGHT) NotificationType.CAPTURED else NotificationType.SCANNED
                notify(
                    type,
                    p.species,
                    p.form,
                    p.shiny,
                    event.playerUUID
                )
            }

            logger.info("Cobblemon JEI: PokedexNotificationHud initialisé avec succès.")
        } catch (e: Throwable) {
            logger.warn("Cobblemon JEI: Erreur lors de l'abonnement aux événements de capture/scan: {}", e.message)
        }
    }

    /**
     * Émet une notification si elle est destinée au joueur local et n'est pas un doublon récent.
     */
    fun notify(
        type: NotificationType,
        species: Species,
        form: FormData?,
        shiny: Boolean,
        targetPlayerUuid: UUID? = null
    ) {
        val mc = Minecraft.getInstance()
        val localPlayer = mc.player ?: return
        if (targetPlayerUuid != null && targetPlayerUuid != localPlayer.uuid) {
            return
        }

        mc.execute {
            showNotification(type, species, form, shiny)
        }
    }

    private fun showNotification(
        type: NotificationType,
        species: Species,
        form: FormData?,
        shiny: Boolean
    ) {
        val now = System.currentTimeMillis()
        val dedupeKey = "${type}_${species.resourceIdentifier}_${form?.name ?: ""}_$shiny"
        val lastTime = recentNotifications[dedupeKey] ?: 0L
        if (now - lastTime < 3500L) {
            return
        }
        recentNotifications[dedupeKey] = now

        // Nettoyage périodique du cache de déduplication
        recentNotifications.entries.removeIf { now - it.value > 10000L }

        // Actualisation des statistiques globales du Pokédex
        PokemonTrackerService.refresh()
        val stats = PokemonTrackerService.getStats()

        // Création de l'ItemStack 3D avec propagation complète des aspects
        val targetForm = form ?: species.standardForm
        val aspects = mutableSetOf<String>()
        aspects.addAll(targetForm.aspects)
        if (shiny) {
            aspects.add("shiny")
        }

        val stack = try {
            PokemonItem.from(species, aspects, 1)
        } catch (_: Throwable) {
            ItemStack.EMPTY
        }

        currentNotification = ActiveNotification(
            type = type,
            species = species,
            form = form,
            shiny = shiny,
            stack = stack,
            stats = stats,
            startTime = now
        )

        // Effet sonore immersif
        try {
            val sound = when (type) {
                NotificationType.CAPTURED -> CobblemonSounds.POKEDEX_SCAN_REGISTER_POKEMON
                NotificationType.SCANNED -> CobblemonSounds.POKEDEX_SCAN_REGISTER_ASPECT
            }
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, 1.0F))
        } catch (_: Throwable) {}
    }

    /**
     * Vérification périodique du Pokédex et de l'équipe client pour capturer les événements réseau en multijoueur.
     */
    fun tick() {
        tickCounter++
        if (tickCounter % 10 != 0) return

        val mc = Minecraft.getInstance()
        if (mc.player == null) {
            isSnapshotInitialized = false
            clientKnowledgeSnapshot.clear()
            return
        }

        try {
            val dexData = CobblemonClient.clientPokedexData

            if (!isSnapshotInitialized) {
                // Première capture de référence pour éviter les faux positifs lors de la connexion
                dexData.speciesRecords.forEach { (resId, rec) ->
                    clientKnowledgeSnapshot[resId] = rec.getKnowledge()
                }
                isSnapshotInitialized = true
                return
            }

            // Détection des nouveaux enregistrements dans le Pokédex client
            for ((resId, rec) in dexData.speciesRecords) {
                val currentKnowledge = rec.getKnowledge()
                val prevKnowledge = clientKnowledgeSnapshot[resId]

                if (currentKnowledge != PokedexEntryProgress.NONE && (prevKnowledge == null || currentKnowledge.ordinal > prevKnowledge.ordinal)) {
                    clientKnowledgeSnapshot[resId] = currentKnowledge
                    val sp = PokemonSpecies.getByIdentifier(resId)
                    if (sp != null) {
                        val type = if (currentKnowledge == PokedexEntryProgress.CAUGHT) NotificationType.CAPTURED else NotificationType.SCANNED
                        notify(type, sp, null, false, mc.player?.uuid)
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Rendu graphique du bandeau HUD au sommet de l'écran.
     */
    fun render(guiGraphics: GuiGraphics, partialTick: DeltaTracker? = null) {
        val notif = currentNotification ?: return
        val now = System.currentTimeMillis()
        val elapsed = now - notif.startTime

        if (elapsed > notif.durationMs) {
            currentNotification = null
            return
        }

        val mc = Minecraft.getInstance()
        val font = mc.font
        val screenW = guiGraphics.guiWidth()

        val bannerW = 270
        val bannerH = 48
        val bannerX = (screenW - bannerW) / 2

        // Animation d'entrée et de sortie fluide (Glissement vertical)
        val slideInMs = 350.0
        val slideOutMs = 450.0
        val hiddenY = -bannerH - 6
        val targetY = 12

        val currentY = when {
            elapsed < slideInMs -> {
                val t = elapsed / slideInMs
                val ease = 1.0 - (1.0 - t).pow(3.0)
                (hiddenY + (targetY - hiddenY) * ease).toInt()
            }
            elapsed > notif.durationMs - slideOutMs -> {
                val t = (elapsed - (notif.durationMs - slideOutMs)) / slideOutMs
                val ease = t.pow(3.0)
                (targetY + (hiddenY - targetY) * ease).toInt()
            }
            else -> targetY
        }

        val accentColor = when (notif.type) {
            NotificationType.CAPTURED -> 0xFF2ECC71.toInt() // Vert Émeraude
            NotificationType.SCANNED -> 0xFFE67E22.toInt()  // Orange Ambré
        }

        // 1. Fond du bandeau sombre moderne (Glassmorphism / Card)
        guiGraphics.fill(bannerX, currentY, bannerX + bannerW, currentY + bannerH, 0xF212151D.toInt())

        // 2. Bordures extérieures
        guiGraphics.fill(bannerX, currentY + bannerH - 1, bannerX + bannerW, currentY + bannerH, 0xFF2A3144.toInt())
        guiGraphics.fill(bannerX, currentY, bannerX + 1, currentY + bannerH, 0xFF2A3144.toInt())
        guiGraphics.fill(bannerX + bannerW - 1, currentY, bannerX + bannerW, currentY + bannerH, 0xFF2A3144.toInt())

        // 3. Barre d'accentuation en haut (2px)
        guiGraphics.fill(bannerX, currentY, bannerX + bannerW, currentY + 2, accentColor)

        // 4. Cadre 3D pour le Pokémon (à gauche)
        val boxX = bannerX + 5
        val boxY = currentY + 5
        val boxSize = 38
        guiGraphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0xFF0B0D12.toInt())
        guiGraphics.fill(boxX, boxY, boxX + boxSize, boxY + 1, 0xFF1F2432.toInt())
        guiGraphics.fill(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, 0xFF1F2432.toInt())
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxSize, 0xFF1F2432.toInt())
        guiGraphics.fill(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, 0xFF1F2432.toInt())

        if (!notif.stack.isEmpty) {
            val pose = guiGraphics.pose()
            pose.pushPose()
            val scale = 2.2f
            val renderSize = 16f * scale
            val modelX = boxX + (boxSize - renderSize) / 2f
            val modelY = boxY + (boxSize - renderSize) / 2f
            pose.translate(modelX, modelY, 0f)
            pose.scale(scale, scale, 1.0f)
            guiGraphics.renderItem(notif.stack, 0, 0)
            pose.popPose()
        }

        // 5. Zone de texte et barre de progression (à droite)
        val infoX = bannerX + 48
        val infoW = bannerW - 54

        // Ligne 1 : Titre de l'action + Nom du Pokémon + Forme + Shiny
        val speciesName = notif.species.translatedName.string
        val formPart = if (notif.form != null && notif.form != notif.species.standardForm) {
            " §7(${PokemonTrackerService.getFormDisplayName(notif.form, notif.species)})§r"
        } else ""
        val shinyStar = if (notif.shiny) " §e★§r" else ""

        val actionText = when (notif.type) {
            NotificationType.CAPTURED -> "§a§l✔ ${Component.translatable("gui.cobblemon_jei.notification.caught").string}§r"
            NotificationType.SCANNED -> "§6§l👁 ${Component.translatable("gui.cobblemon_jei.notification.scanned").string}§r"
        }
        val title = "$actionText §f$speciesName§r$formPart$shinyStar"
        guiGraphics.drawString(font, title, infoX, currentY + 6, 0xFFFFFFFF.toInt(), false)

        // Ligne 2 : Chiffres de progression Pokédex
        val stats = notif.stats
        val total = max(1, stats.totalSpecies)
        val caught = stats.caughtCount
        val seenOnly = stats.encounteredCount
        val totalSeen = caught + seenOnly
        val caughtPct = (caught.toDouble() / total) * 100.0

        val line2 = "§a● $caught§r  §8|§r  §6👁 $seenOnly§r  §8|§r  §7Total: §f$total §8(§e${String.format("%.1f", caughtPct)}%§8)§r"
        guiGraphics.drawString(font, line2, infoX, currentY + 18, 0xFFFFFFFF.toInt(), false)

        // Ligne 3 : Barre de progression bicolore Pokédex (Vert = Capturé, Orange = Vu uniquement)
        val barX = infoX
        val barY = currentY + 32
        val barW = infoW - 4
        val barH = 5

        guiGraphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF282D3C.toInt())
        guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF141720.toInt())

        var caughtW = ((caught.toDouble() / total) * barW).toInt()
        var encW = ((seenOnly.toDouble() / total) * barW).toInt()

        if (caught > 0 && caughtW == 0) caughtW = 1
        if (seenOnly > 0 && encW == 0) encW = 1
        if (caughtW + encW > barW) {
            encW = max(0, barW - caughtW)
        }

        // Segment vert (Capturé)
        if (caughtW > 0) {
            guiGraphics.fill(barX, barY, barX + caughtW, barY + barH, 0xFF2ECC71.toInt())
            guiGraphics.fill(barX, barY, barX + caughtW, barY + 1, 0xFF55FF55.toInt())
        }

        // Segment orange (Vu uniquement)
        if (encW > 0) {
            val encStartX = barX + caughtW
            guiGraphics.fill(encStartX, barY, encStartX + encW, barY + barH, 0xFFE67E22.toInt())
            guiGraphics.fill(encStartX, barY, encStartX + encW, barY + 1, 0xFFFFAA00.toInt())
        }
    }
}
