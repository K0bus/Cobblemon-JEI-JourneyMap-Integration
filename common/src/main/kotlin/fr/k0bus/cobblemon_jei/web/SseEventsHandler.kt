package fr.k0bus.cobblemon_jei.web

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Gestionnaire du point de terminaison SSE (Server-Sent Events) `/api/events`.
 * Maintient les flux ouverts et diffuse les événements temps réel aux clients connectés.
 */
class SseEventsHandler : HttpHandler, AutoCloseable {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/SSE")
    private val activeStreams = CopyOnWriteArrayList<OutputStream>()
    private val gson: Gson = GsonBuilder().create()
    private val heartbeatExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "CobblemonJEI-SSE-Heartbeat").apply { isDaemon = true }
    }

    init {
        // Envoi d'un battement de cœur (ping) toutes les 15 secondes pour éviter le timeout des navigateurs
        heartbeatExecutor.scheduleAtFixedRate({
            broadcastComment("ping")
        }, 15, 15, TimeUnit.SECONDS)
    }

    override fun handle(exchange: HttpExchange) {
        val method = exchange.requestMethod
        if (!method.equals("GET", ignoreCase = true)) {
            exchange.sendResponseHeaders(405, -1)
            exchange.close()
            return
        }

        val headers = exchange.responseHeaders
        headers.set("Content-Type", "text/event-stream; charset=UTF-8")
        headers.set("Cache-Control", "no-cache, no-transform")
        headers.set("Connection", "keep-alive")
        headers.set("Access-Control-Allow-Origin", "*")

        // 0 indique un flux en streaming chunked / indéterminé
        exchange.sendResponseHeaders(200, 0)
        val os = exchange.responseBody
        activeStreams.add(os)
        logger.debug("Cobblemon JEI: Nouveau client SSE connecté. Total clients: {}", activeStreams.size)

        // Message initial de bienvenue pour confirmer la connexion
        try {
            val welcomeMsg = "event: connected\ndata: {\"status\":\"ok\",\"timestamp\":${System.currentTimeMillis()}}\n\n"
            os.write(welcomeMsg.toByteArray(StandardCharsets.UTF_8))
            os.flush()
        } catch (e: Throwable) {
            activeStreams.remove(os)
            exchange.close()
        }
    }

    /**
     * Diffuse un événement nommé avec une charge utile JSON à tous les clients connectés.
     */
    fun broadcast(eventType: String, data: Any) {
        if (activeStreams.isEmpty()) return

        val json = try { gson.toJson(data) } catch (e: Throwable) { "{}" }
        val message = "event: $eventType\ndata: $json\n\n"
        val bytes = message.toByteArray(StandardCharsets.UTF_8)

        val toRemove = mutableListOf<OutputStream>()
        for (os in activeStreams) {
            try {
                os.write(bytes)
                os.flush()
            } catch (e: Throwable) {
                toRemove.add(os)
            }
        }
        if (toRemove.isNotEmpty()) {
            activeStreams.removeAll(toRemove)
            logger.debug("Cobblemon JEI: {} flux SSE fermés. Total restant: {}", toRemove.size, activeStreams.size)
        }
    }

    /**
     * Envoie un commentaire SSE `: ping\n\n` pour maintenir la connexion active.
     */
    private fun broadcastComment(comment: String) {
        if (activeStreams.isEmpty()) return
        val bytes = ": $comment\n\n".toByteArray(StandardCharsets.UTF_8)
        val toRemove = mutableListOf<OutputStream>()
        for (os in activeStreams) {
            try {
                os.write(bytes)
                os.flush()
            } catch (e: Throwable) {
                toRemove.add(os)
            }
        }
        if (toRemove.isNotEmpty()) {
            activeStreams.removeAll(toRemove)
        }
    }

    override fun close() {
        heartbeatExecutor.shutdownNow()
        for (os in activeStreams) {
            try { os.close() } catch (_: Throwable) {}
        }
        activeStreams.clear()
    }
}
