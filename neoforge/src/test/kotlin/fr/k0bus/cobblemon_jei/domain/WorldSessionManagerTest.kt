package fr.k0bus.cobblemon_jei.domain

import fr.k0bus.cobblemon_jei.domain.session.SessionLifecycleListener
import fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager
import fr.k0bus.cobblemon_jei.journeymap.JourneyMapDiskScanner
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WorldSessionManagerTest {

    private val overworld = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
    private val theNether = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"))

    @BeforeEach
    fun reset() {
        WorldSessionManager.endSession()
        JourneyMapDiskScanner.clearCache()
    }

    @Test
    fun `test session lifecycle and token cancellation on session end`() {
        var startedCalled = false
        var endedCalled = false

        val listener = object : SessionLifecycleListener {
            override fun onSessionStarted(sessionId: String, initialDimension: ResourceKey<Level>?) {
                startedCalled = true
            }

            override fun onSessionEnded(sessionId: String) {
                endedCalled = true
            }
        }

        WorldSessionManager.addListener(listener)
        try {
            val sessionId = WorldSessionManager.startNewSession(overworld)
            assertTrue(startedCalled)
            assertTrue(WorldSessionManager.isSessionActive)
            assertTrue(WorldSessionManager.isSessionValid(sessionId))

            val token = WorldSessionManager.createCancellationToken(overworld)
            assertFalse(token.isCancelled)

            WorldSessionManager.endSession()
            assertTrue(endedCalled)
            assertFalse(WorldSessionManager.isSessionActive)
            assertFalse(WorldSessionManager.isSessionValid(sessionId))
            assertTrue(token.isCancelled)
        } finally {
            WorldSessionManager.removeListener(listener)
        }
    }

    @Test
    fun `test dimension change cancels tokens of previous dimension`() {
        val sessionId = WorldSessionManager.startNewSession(overworld)
        val overworldToken = WorldSessionManager.createCancellationToken(overworld)
        val globalToken = WorldSessionManager.createCancellationToken(null)

        assertFalse(overworldToken.isCancelled)
        assertFalse(globalToken.isCancelled)

        WorldSessionManager.onDimensionChanged(theNether)

        assertTrue(overworldToken.isCancelled, "Token de l'ancienne dimension doit être annulé")
        assertFalse(globalToken.isCancelled, "Token sans dimension explicite reste valide pour la session")
        assertEquals(theNether, WorldSessionManager.currentDimension)
    }

    @Test
    fun `test session end clears JourneyMapDiskScanner cache`() {
        WorldSessionManager.startNewSession(overworld)

        // Simuler un chunk en cache
        val chunk = ChunkPos(10, 20)
        // Utiliser la clé Pair(overworld, chunk)
        val cacheKey = Pair(overworld, chunk)
        // Dummy mock non-null
        @Suppress("UNCHECKED_CAST")
        val dummyBiomeHolder = java.lang.reflect.Proxy.newProxyInstance(
            net.minecraft.core.Holder::class.java.classLoader,
            arrayOf(net.minecraft.core.Holder::class.java)
        ) { _, _, _ -> null } as net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>

        JourneyMapDiskScanner.globalBiomeCache[cacheKey] = dummyBiomeHolder
        assertEquals(1, JourneyMapDiskScanner.globalBiomeCache.size)

        WorldSessionManager.endSession()

        assertEquals(0, JourneyMapDiskScanner.globalBiomeCache.size, "Le cache JourneyMapDiskScanner doit être vidé à la fin de la session")
    }
}
