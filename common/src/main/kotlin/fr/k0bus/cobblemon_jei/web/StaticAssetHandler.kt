package fr.k0bus.cobblemon_jei.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Gestionnaire des fichiers statiques de l'application web packagée dans le JAR sous
 * `assets/cobblemon_jei/web/`. Supporte le routage SPA (fallback vers index.html).
 */
class StaticAssetHandler(
    private val basePath: String = "/assets/cobblemon_jei/web"
) : HttpHandler {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/StaticAsset")

    override fun handle(exchange: HttpExchange) {
        val method = exchange.requestMethod
        if (!method.equals("GET", ignoreCase = true) && !method.equals("HEAD", ignoreCase = true)) {
            exchange.sendResponseHeaders(405, -1)
            exchange.close()
            return
        }

        var path = exchange.requestURI.path
        if (path.isEmpty() || path == "/") {
            path = "/index.html"
        }

        // Sécurisation anti-path traversal
        val cleanPath = path.replace("..", "").replace("//", "/")
        val resourcePath = "$basePath$cleanPath"

        var stream = javaClass.getResourceAsStream(resourcePath)
        var finalPath = cleanPath

        // Mode SPA : si la ressource n'existe pas et n'a pas d'extension de fichier, fallback sur index.html
        if (stream == null && !cleanPath.contains(".")) {
            finalPath = "/index.html"
            stream = javaClass.getResourceAsStream("$basePath/index.html")
        }

        if (stream == null) {
            val notFoundBody = "<h1>404 Not Found</h1><p>Web asset not found.</p>"
            val bytes = notFoundBody.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
            exchange.sendResponseHeaders(404, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            return
        }

        val contentType = getContentType(finalPath)
        val headers = exchange.responseHeaders
        headers.set("Content-Type", contentType)

        // Cache control : pas de cache pour index.html pour permettre les mises à jour, cache long pour les assets hachés
        if (finalPath.endsWith("index.html")) {
            headers.set("Cache-Control", "no-cache, no-store, must-revalidate")
        } else {
            headers.set("Cache-Control", "public, max-age=86400")
        }
        headers.set("Access-Control-Allow-Origin", "*")

        try {
            val bytes = stream.readAllBytes()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            if (!method.equals("HEAD", ignoreCase = true)) {
                exchange.responseBody.use { it.write(bytes) }
            } else {
                exchange.responseBody.close()
            }
        } catch (e: Throwable) {
            logger.debug("Erreur lors de la livraison de l'asset statique {}: {}", finalPath, e.message)
            exchange.close()
        } finally {
            try { stream.close() } catch (_: Throwable) {}
        }
    }

    private fun getContentType(path: String): String {
        return when {
            path.endsWith(".html", ignoreCase = true) -> "text/html; charset=UTF-8"
            path.endsWith(".js", ignoreCase = true) || path.endsWith(".mjs", ignoreCase = true) -> "application/javascript; charset=UTF-8"
            path.endsWith(".css", ignoreCase = true) -> "text/css; charset=UTF-8"
            path.endsWith(".json", ignoreCase = true) -> "application/json; charset=UTF-8"
            path.endsWith(".png", ignoreCase = true) -> "image/png"
            path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            path.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
            path.endsWith(".ico", ignoreCase = true) -> "image/x-icon"
            path.endsWith(".woff2", ignoreCase = true) -> "font/woff2"
            path.endsWith(".woff", ignoreCase = true) -> "font/woff"
            path.endsWith(".ttf", ignoreCase = true) -> "font/ttf"
            else -> "application/octet-stream"
        }
    }
}
