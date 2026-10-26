package fr.k0bus.cobblemon_jei.domain.session

import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Token d'annulation pour les tâches asynchrones liées au cycle de vie d'un monde.
 */
class CancellationToken(
    val sessionId: String,
    val dimension: ResourceKey<Level>? = null
) {
    private val cancelled = AtomicBoolean(false)

    val isCancelled: Boolean
        get() = cancelled.get() || (sessionId != "untracked_session" && !WorldSessionManager.isSessionValid(sessionId))

    fun cancel() {
        cancelled.set(true)
    }
}

/**
 * Gestionnaire centralisé du cycle de vie de la session de monde active.
 *
 * Assure que les tâches d'arrière-plan (scans de disque, caches géométriques)
 * sont invalidées et arrêtées dès que le joueur quitte un monde ou change de dimension.
 */
object WorldSessionManager {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/SessionManager")

    private val currentSessionId = AtomicReference<String?>(null)
    private val currentDimensionKey = AtomicReference<ResourceKey<Level>?>(null)
    private val sessionEpoch = AtomicLong(0)
    private val listeners = CopyOnWriteArrayList<SessionLifecycleListener>()
    private val activeTokens = CopyOnWriteArrayList<CancellationToken>()

    val activeSessionId: String?
        get() = currentSessionId.get()

    val currentDimension: ResourceKey<Level>?
        get() = currentDimensionKey.get()

    val isSessionActive: Boolean
        get() = currentSessionId.get() != null

    /**
     * Vérifie si un identifiant de session est toujours celui de la session active.
     */
    fun isSessionValid(sessionId: String): Boolean {
        return currentSessionId.get() == sessionId
    }

    /**
     * Résout un identifiant stable et déterministe pour le monde solo ou serveur multijoueur actuel.
     * Permet la persistance des caches et objectifs entre les relancements du jeu.
     */
    fun resolveCurrentWorldId(): String {
        val mc = try { net.minecraft.client.Minecraft.getInstance() } catch (_: Throwable) { null } ?: return "offline_world"
        val server = mc.singleplayerServer
        if (server != null) {
            val levelName = try { server.worldData.levelName } catch (_: Throwable) { "singleplayer" }
            val clean = levelName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            return "sp_$clean"
        }
        val serverData = mc.currentServer
        if (serverData != null) {
            val cleanIp = serverData.ip.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            return "mp_$cleanIp"
        }
        val level = mc.level
        if (level != null) {
            val dim = level.dimension().location().toString().replace(Regex("[^a-zA-Z0-9._-]"), "_")
            return "world_$dim"
        }
        return "default_world"
    }

    /**
     * Démarre une nouvelle session (ex: connexion à un monde solo ou serveur multijoueur).
     */
    @Synchronized
    fun startNewSession(dimension: ResourceKey<Level>? = null, customId: String? = null): String {
        endSession()

        val newId = customId ?: resolveCurrentWorldId()
        sessionEpoch.incrementAndGet()
        currentSessionId.set(newId)
        currentDimensionKey.set(dimension)

        logger.info("Cobblemon JEI: Démarrage d'une nouvelle session de monde [$newId] (Dimension: $dimension)")

        for (listener in listeners) {
            try {
                listener.onSessionStarted(newId, dimension)
            } catch (t: Throwable) {
                logger.error("Erreur lors de la notification onSessionStarted", t)
            }
        }

        return newId
    }

    /**
     * Met à jour la dimension courante et annule les tokens associés à l'ancienne dimension.
     */
    @Synchronized
    fun onDimensionChanged(newDimension: ResourceKey<Level>) {
        val sessionId = currentSessionId.get() ?: return
        val oldDimension = currentDimensionKey.get()
        if (oldDimension == newDimension) return

        currentDimensionKey.set(newDimension)
        logger.info("Cobblemon JEI: Changement de dimension dans la session [$sessionId] : $oldDimension -> $newDimension")

        // Annuler les tokens attachés à l'ancienne dimension
        activeTokens.removeIf { token ->
            if (token.dimension != null && token.dimension != newDimension) {
                token.cancel()
                true
            } else {
                false
            }
        }

        for (listener in listeners) {
            try {
                listener.onDimensionChanged(sessionId, newDimension)
            } catch (t: Throwable) {
                logger.error("Erreur lors de la notification onDimensionChanged", t)
            }
        }
    }

    /**
     * Termine la session courante, annule tous les tokens et notifie les écouteurs.
     */
    @Synchronized
    fun endSession() {
        val oldId = currentSessionId.getAndSet(null) ?: return
        currentDimensionKey.set(null)

        logger.info("Cobblemon JEI: Fin de la session de monde [$oldId]. Annulation de tous les travaux en cours.")

        for (token in activeTokens) {
            token.cancel()
        }
        activeTokens.clear()

        for (listener in listeners) {
            try {
                listener.onSessionEnded(oldId)
            } catch (t: Throwable) {
                logger.error("Erreur lors de la notification onSessionEnded", t)
            }
        }
    }

    /**
     * Crée un token d'annulation rattaché à la session courante (et optionnellement à une dimension).
     */
    fun createCancellationToken(dimension: ResourceKey<Level>? = null): CancellationToken {
        var sessionId = currentSessionId.get()
        if (sessionId == null) {
            sessionId = startNewSession(dimension)
        }
        val token = CancellationToken(sessionId, dimension)
        activeTokens.add(token)
        return token
    }

    /**
     * Enregistre un écouteur de cycle de vie.
     */
    fun addListener(listener: SessionLifecycleListener) {
        listeners.add(listener)
    }

    /**
     * Retire un écouteur de cycle de vie.
     */
    fun removeListener(listener: SessionLifecycleListener) {
        listeners.remove(listener)
    }
}
