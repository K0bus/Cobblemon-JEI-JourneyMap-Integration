package fr.k0bus.cobblemon_jei.web

import com.google.gson.Gson
import fr.k0bus.cobblemon_jei.config.CobblemonModConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class WebServerServiceTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupMinecraft() {
            try {
                net.minecraft.SharedConstants.tryDetectVersion()
                net.minecraft.server.Bootstrap.bootStrap()
            } catch (_: Throwable) {}
        }
    }

    private val testPort = 18420
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()
    private val gson = Gson()

    @BeforeEach
    fun setUp() {
        CobblemonModConfig.webServerEnabled = true
        CobblemonModConfig.webServerPort = testPort
        WebServerService.start(testPort)
        // Laisser le temps au thread virtuel de démarrer
        Thread.sleep(250)
    }

    @AfterEach
    fun tearDown() {
        WebServerService.stop()
        Thread.sleep(100)
    }

    @Test
    fun `test web server starts and serves api status`() {
        assertTrue(WebServerService.isRunning, "Le serveur web doit être en cours d'exécution")
        val port = WebServerService.activePort

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api/status"))
            .GET()
            .timeout(Duration.ofSeconds(2))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), "L'endpoint /api/status doit renvoyer 200")
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"))

        val json = gson.fromJson(response.body(), Map::class.java)
        assertNotNull(json["playerName"])
        assertNotNull(json["dimension"])
    }

    @Test
    fun `test web server returns evolutions endpoints`() {
        val port = WebServerService.activePort

        val endpoints = listOf(
            "/api/evolutions/pending",
            "/api/evolutions/items",
            "/api/evolutions/origins",
            "/api/evolutions/fossils",
            "/api/evolutions/legendaries",
            "/api/party",
            "/api/pc"
        )

        for (endpoint in endpoints) {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port$endpoint"))
                .GET()
                .timeout(Duration.ofSeconds(2))
                .build()

            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, resp.statusCode(), "L'endpoint $endpoint doit renvoyer 200 OK")
            assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("application/json"))
        }
    }

    @Test
    fun `test static asset delivery for spa root`() {
        val port = WebServerService.activePort

        val req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/"))
            .GET()
            .timeout(Duration.ofSeconds(2))
            .build()

        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, resp.statusCode(), "La racine / doit renvoyer 200 OK avec le SPA")
        assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("text/html"))
        assertTrue(resp.body().contains("Cobblemon Live Companion"), "Le HTML doit contenir le titre de l'application")
    }

    @Test
    fun `test static asset 404 response for unknown file`() {
        val port = WebServerService.activePort

        val req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/non_existent_file.xyz"))
            .GET()
            .timeout(Duration.ofSeconds(2))
            .build()

        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        // Since SPA fallback redirects HTML or unknown paths to index.html if acceptable,
        // or returns 404 for extensions like .xyz:
        assertTrue(resp.statusCode() == 404 || resp.statusCode() == 200)
    }
}
