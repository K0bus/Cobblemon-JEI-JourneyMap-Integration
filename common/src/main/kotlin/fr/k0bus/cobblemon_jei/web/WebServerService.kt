package fr.k0bus.cobblemon_jei.web

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import fr.k0bus.cobblemon_jei.config.CobblemonModConfig
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Micro-serveur HTTP et temps réel embarqué pour Cobblemon JEI.
 * Fournit une interface web moderne ("Modern Glasses") pour écran secondaire.
 */
object WebServerService {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/WebServer")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private var server: HttpServer? = null
    private var sseHandler: SseEventsHandler? = null
    private var executor: ExecutorService? = null

    var isRunning: Boolean = false
        private set

    var activePort: Int = 8420
        private set

    /**
     * Démarre le micro-serveur HTTP de manière asynchrone sur le port configuré
     * (avec fallback automatique si le port est occupé).
     */
    @Synchronized
    fun start(requestedPort: Int = CobblemonModConfig.webServerPort) {
        if (isRunning) return
        if (!CobblemonModConfig.webServerEnabled) {
            logger.info("Cobblemon JEI: Le serveur web compagnon est désactivé dans la configuration.")
            return
        }

        Thread({
            var port = requestedPort
            var attempts = 0
            val maxAttempts = 10
            var bound = false

            while (!bound && attempts < maxAttempts) {
                try {
                    val address = InetSocketAddress("0.0.0.0", port)
                    val s = HttpServer.create(address, 0)

                    // Initialisation du thread pool (Virtual Threads Java 21)
                    val exec = try {
                        val method = Executors::class.java.getMethod("newVirtualThreadPerTaskExecutor")
                        method.invoke(null) as ExecutorService
                    } catch (_: Throwable) {
                        Executors.newFixedThreadPool(4) { r ->
                            Thread(r, "CobblemonJEI-WebWorker").apply { isDaemon = true }
                        }
                    }

                    s.executor = exec
                    executor = exec

                    val sse = SseEventsHandler()
                    sseHandler = sse

                    // 1. Point d'entrée SSE pour flux temps réel
                    s.createContext("/api/events", sse)

                    // 2. Endpoints API REST
                    registerRestEndpoints(s)

                    // 3. Fichiers statiques web
                    s.createContext("/", StaticAssetHandler())

                    s.start()
                    server = s
                    activePort = port
                    isRunning = true
                    bound = true
                    logger.info("Cobblemon JEI: Micro-serveur web démarré avec succès sur http://localhost:{}", port)
                } catch (e: java.net.BindException) {
                    logger.warn("Cobblemon JEI: Le port {} est déjà utilisé, tentative sur {}", port, port + 1)
                    port++
                    attempts++
                } catch (e: Throwable) {
                    logger.error("Cobblemon JEI: Échec du démarrage du micro-serveur web : {}", e.message, e)
                    break
                }
            }
        }, "CobblemonJEI-WebServer-Init").start()
    }

    /**
     * Enregistre l'ensemble des routes REST de l'API.
     */
    private fun registerRestEndpoints(s: HttpServer) {
        // Statut global du joueur
        s.createContext("/api/status") { exchange ->
            handleGet(exchange) { WebDataService.getPlayerStatus() }
        }

        // Pokédex global (liste résumée)
        s.createContext("/api/pokedex") { exchange ->
            handleGet(exchange) { WebDataService.getPokedexList() }
        }

        // Détail d'une espèce
        s.createContext("/api/species") { exchange ->
            val path = exchange.requestURI.path.removePrefix("/api/species").removePrefix("/")
            if (path.isBlank()) {
                sendError(exchange, 400, "Species ID or name required in path (e.g. /api/species/pikachu)")
            } else {
                val detail = WebDataService.getSpeciesDetail(path)
                if (detail != null) {
                    sendJson(exchange, 200, detail)
                } else {
                    sendError(exchange, 404, "Species not found: $path")
                }
            }
        }

        // 1. Évolutions en attente (Prêtes / Faisables avec le PC)
        s.createContext("/api/evolutions/pending") { exchange ->
            handleGet(exchange) { WebDataService.getPendingEvolutionGoals() }
        }

        // 2. Évolutions par pierres et objets d'interaction
        s.createContext("/api/evolutions/items") { exchange ->
            handleGet(exchange) { WebDataService.getItemEvolutionGoals() }
        }

        // 3. Génération inférieure & reproduction (Bébés / formes de base manquantes)
        s.createContext("/api/evolutions/origins") { exchange ->
            handleGet(exchange) { WebDataService.getMissingOriginGoals() }
        }

        // 4. Fossiles & restauration
        s.createContext("/api/evolutions/fossils") { exchange ->
            handleGet(exchange) { WebDataService.getFossilGoals() }
        }

        // 5. Pokémon légendaires & sanctuaires
        s.createContext("/api/evolutions/legendaries") { exchange ->
            handleGet(exchange) { WebDataService.getLegendaryGoals() }
        }

        // Équipe active (6 Pokémon)
        s.createContext("/api/party") { exchange ->
            handleGet(exchange) { WebDataService.getParty() }
        }

        // Boîtes PC
        s.createContext("/api/pc") { exchange ->
            handleGet(exchange) { WebDataService.getPcBoxes() }
        }

        // Entités détectées par le radar
        s.createContext("/api/radar") { exchange ->
            handleGet(exchange) { WebDataService.getRadarEntities() }
        }

        // Action : Déclencher le suivi d'un Pokémon
        s.createContext("/api/action/track") { exchange ->
            if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
                sendError(exchange, 405, "Method Not Allowed")
                return@createContext
            }
            try {
                val reader = InputStreamReader(exchange.requestBody, StandardCharsets.UTF_8)
                val bodyMap = gson.fromJson(reader, Map::class.java) as? Map<*, *>
                val speciesName = bodyMap?.get("species")?.toString()
                if (!speciesName.isNullOrBlank()) {
                    val sp = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(speciesName.lowercase())
                    if (sp != null) {
                        fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.trackSingle(sp)
                        sendJson(exchange, 200, mapOf("status" to "ok", "tracking" to sp.translatedName.string))
                    } else {
                        sendError(exchange, 404, "Species not found: $speciesName")
                    }
                } else {
                    sendError(exchange, 400, "Missing 'species' parameter")
                }
            } catch (e: Throwable) {
                sendError(exchange, 500, "Error: ${e.message}")
            }
        }
    }

    private fun handleGet(exchange: HttpExchange, provider: () -> Any) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendError(exchange, 405, "Method Not Allowed")
            return
        }
        try {
            val result = provider()
            sendJson(exchange, 200, result)
        } catch (e: Throwable) {
            logger.warn("Erreur traitement GET {}: {}", exchange.requestURI.path, e.message, e)
            sendError(exchange, 500, "Internal Server Error: ${e.message}")
        }
    }

    private fun sendJson(exchange: HttpExchange, statusCode: Int, data: Any) {
        val json = gson.toJson(data)
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val headers = exchange.responseHeaders
        headers.set("Content-Type", "application/json; charset=UTF-8")
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        headers.set("Access-Control-Allow-Headers", "Content-Type")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendError(exchange: HttpExchange, statusCode: Int, message: String) {
        sendJson(exchange, statusCode, mapOf("error" to message, "status" to statusCode))
    }

    /**
     * Diffuse un événement temps réel à tous les clients web connectés via SSE.
     */
    fun broadcast(eventType: String, payload: Any) {
        if (!isRunning) return
        try {
            sseHandler?.broadcast(eventType, payload)
        } catch (e: Throwable) {
            logger.debug("Erreur broadcast SSE : {}", e.message)
        }
    }

    /**
     * Arrête proprement le micro-serveur.
     */
    @Synchronized
    fun stop() {
        if (!isRunning) return
        logger.info("Cobblemon JEI: Arrêt du micro-serveur web...")
        try {
            sseHandler?.close()
            server?.stop(0)
            executor?.shutdownNow()
        } catch (e: Throwable) {
            logger.warn("Erreur lors de l'arrêt du serveur web: {}", e.message)
        } finally {
            server = null
            sseHandler = null
            executor = null
            isRunning = false
        }
    }
}
