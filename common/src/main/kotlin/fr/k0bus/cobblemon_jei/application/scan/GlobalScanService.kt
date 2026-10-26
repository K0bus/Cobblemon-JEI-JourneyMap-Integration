package fr.k0bus.cobblemon_jei.application.scan

import fr.k0bus.cobblemon_jei.journeymap.ChunkBiomeTagCache
import fr.k0bus.cobblemon_jei.journeymap.CobblemonJourneyMapPlugin
import fr.k0bus.cobblemon_jei.journeymap.JourneyMapDiskScanner
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Service gérant le lancement, le suivi en direct et l'annulation d'un scan global de la carte JourneyMap.
 * Permet d'analyser tous les fichiers régions et caches JourneyMap en arrière-plan sans figer le jeu.
 */
object GlobalScanService {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/GlobalScan")

    var isScanning: Boolean = false
        private set
    var progressPercent: Int = 0
        private set
    var completedRegions: Int = 0
        private set
    var totalRegions: Int = 0
        private set
    var scannedChunks: Int = 0
        private set
    var statusMessage: String = "Prêt pour une analyse globale"
        private set

    private val cancelFlag = AtomicBoolean(false)

    /**
     * Lance le scan complet des régions cartographiées en arrière-plan.
     */
    fun startScan(
        forceRefresh: Boolean = true,
        onProgressUpdate: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        if (isScanning) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val currentDim = level.dimension()
        val playerY = mc.player?.blockY ?: 64

        isScanning = true
        cancelFlag.set(false)
        progressPercent = 0
        completedRegions = 0
        totalRegions = 0
        scannedChunks = 0
        statusMessage = "Démarrage de l'analyse des régions cartographiées..."

        logger.info("Cobblemon JEI: Lancement manuel d'un scan global (forceRefresh=$forceRefresh) pour ${currentDim.location()}...")

        CompletableFuture.runAsync({
            try {
                val api = CobblemonJourneyMapPlugin.clientApi
                val result = JourneyMapDiskScanner.scanExploredChunks(
                    currentDim = currentDim,
                    mc = mc,
                    playerY = playerY,
                    isCancelled = { cancelFlag.get() },
                    clientApi = api,
                    onProgress = { completed, total, chunkMap ->
                        completedRegions = completed
                        totalRegions = total
                        scannedChunks = chunkMap.size
                        progressPercent = if (total > 0) (completed * 100 / total).coerceIn(0, 99) else 0
                        statusMessage = "Analyse en cours : $completed/$total régions ($scannedChunks chunks)"
                        onProgressUpdate?.invoke()
                    },
                    forceRefresh = forceRefresh
                )

                if (cancelFlag.get()) {
                    statusMessage = "Scan interrompu par l'utilisateur."
                    mc.player?.displayClientMessage(
                        Component.literal("§c✕ [Cobblemon JEI] Scan global interrompu."),
                        false
                    )
                } else {
                    scannedChunks = result.size
                    progressPercent = 100
                    statusMessage = "Scan terminé avec succès : ${result.size} chunks cartographiés et persistés !"
                    mc.player?.displayClientMessage(
                        Component.literal("§a✔ [Cobblemon JEI] Scan global terminé : §f${result.size} chunks cartographiés."),
                        false
                    )
                }
            } catch (t: Throwable) {
                logger.error("Cobblemon JEI: Erreur lors du scan global: ${t.message}", t)
                statusMessage = "Erreur pendant le scan : ${t.message}"
            } finally {
                isScanning = false
                onComplete?.invoke()
            }
        })
    }

    /**
     * Interrompt le scan en cours.
     */
    fun cancelScan() {
        if (isScanning) {
            cancelFlag.set(true)
            statusMessage = "Interruption en cours..."
        }
    }

    /**
     * Réinitialise complètement le cache mémoire et force un futur re-scan.
     */
    fun clearCache() {
        cancelScan()
        JourneyMapDiskScanner.clearCache()
        ChunkBiomeTagCache.clearAll()
        scannedChunks = 0
        progressPercent = 0
        statusMessage = "Cache mémoire réinitialisé."
    }
}
