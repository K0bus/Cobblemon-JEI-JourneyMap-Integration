package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PokemonTrackingManagerTest {

    private lateinit var pikachu: Species
    private lateinit var raichu: Species
    private lateinit var charmander: Species

    private fun createDummySpecies(name: String): Species {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val sp = unsafe.allocateInstance(Species::class.java) as Species
        sp.name = name
        try {
            sp.resourceIdentifier = ResourceLocation.fromNamespaceAndPath("cobblemon", name)
        } catch (_: Throwable) {}
        return sp
    }

    @BeforeEach
    fun setUp() {
        PokemonTrackingManager.stopTracking(notifyPlayer = false)
        PokemonTrackingManager.clearWatchlist()

        pikachu = createDummySpecies("pikachu")
        raichu = createDummySpecies("raichu")
        charmander = createDummySpecies("charmander")
    }

    @AfterEach
    fun tearDown() {
        PokemonTrackingManager.stopTracking(notifyPlayer = false)
        PokemonTrackingManager.clearWatchlist()
    }

    @Test
    fun `test initial state is OFF`() {
        assertEquals(TrackingMode.OFF, PokemonTrackingManager.currentMode)
        assertFalse(PokemonTrackingManager.isTrackingActive)
        assertNull(PokemonTrackingManager.targetSpecies)
        assertTrue(PokemonTrackingManager.customWatchlist.isEmpty())
    }

    @Test
    fun `test single target tracking`() {
        PokemonTrackingManager.trackSingle(pikachu, notifyPlayer = false)

        assertEquals(TrackingMode.SINGLE, PokemonTrackingManager.currentMode)
        assertTrue(PokemonTrackingManager.isTrackingActive)
        assertEquals(pikachu, PokemonTrackingManager.targetSpecies)

        assertTrue(PokemonTrackingManager.isTarget(pikachu))
        assertFalse(PokemonTrackingManager.isTarget(raichu))
        assertFalse(PokemonTrackingManager.isTarget(charmander))
    }

    @Test
    fun `test custom watchlist management`() {
        PokemonTrackingManager.addToWatchlist(pikachu, notifyPlayer = false)
        PokemonTrackingManager.addToWatchlist(charmander, notifyPlayer = false)

        assertTrue(PokemonTrackingManager.isInWatchlist(pikachu))
        assertTrue(PokemonTrackingManager.isInWatchlist(charmander))
        assertFalse(PokemonTrackingManager.isInWatchlist(raichu))
        assertEquals(2, PokemonTrackingManager.customWatchlist.size)

        // Toggle removes charmander
        val toggleResult1 = PokemonTrackingManager.toggleWatchlist(charmander, notifyPlayer = false)
        assertFalse(toggleResult1)
        assertFalse(PokemonTrackingManager.isInWatchlist(charmander))
        assertEquals(1, PokemonTrackingManager.customWatchlist.size)

        // Toggle re-adds charmander
        val toggleResult2 = PokemonTrackingManager.toggleWatchlist(charmander, notifyPlayer = false)
        assertTrue(toggleResult2)
        assertTrue(PokemonTrackingManager.isInWatchlist(charmander))

        // Set custom list mode
        PokemonTrackingManager.trackCustomList(notifyPlayer = false)
        assertEquals(TrackingMode.CUSTOM_LIST, PokemonTrackingManager.currentMode)
        assertTrue(PokemonTrackingManager.isTarget(pikachu))
        assertTrue(PokemonTrackingManager.isTarget(charmander))
        assertFalse(PokemonTrackingManager.isTarget(raichu))

        PokemonTrackingManager.removeFromWatchlist(pikachu, notifyPlayer = false)
        assertFalse(PokemonTrackingManager.isTarget(pikachu))
    }

    @Test
    fun `test all uncaught mode sets active mode`() {
        PokemonTrackingManager.trackAllUncaught(notifyPlayer = false)
        assertEquals(TrackingMode.ALL_UNCAUGHT, PokemonTrackingManager.currentMode)
        assertTrue(PokemonTrackingManager.isTrackingActive)
    }

    @Test
    fun `test clear watchlist stops tracking if in custom list mode`() {
        PokemonTrackingManager.addToWatchlist(pikachu, notifyPlayer = false)
        PokemonTrackingManager.trackCustomList(notifyPlayer = false)
        assertEquals(TrackingMode.CUSTOM_LIST, PokemonTrackingManager.currentMode)

        PokemonTrackingManager.clearWatchlist()
        assertTrue(PokemonTrackingManager.customWatchlist.isEmpty())
        assertEquals(TrackingMode.OFF, PokemonTrackingManager.currentMode)
        assertFalse(PokemonTrackingManager.isTrackingActive)
    }

    @Test
    fun `test track custom list when empty does nothing`() {
        PokemonTrackingManager.clearWatchlist()
        PokemonTrackingManager.trackCustomList(notifyPlayer = false)
        assertEquals(TrackingMode.OFF, PokemonTrackingManager.currentMode)
    }

    @Test
    fun `test stop tracking clears targets and mode`() {
        PokemonTrackingManager.trackSingle(pikachu, notifyPlayer = false)
        assertTrue(PokemonTrackingManager.isTrackingActive)

        PokemonTrackingManager.stopTracking(notifyPlayer = false)
        assertEquals(TrackingMode.OFF, PokemonTrackingManager.currentMode)
        assertFalse(PokemonTrackingManager.isTrackingActive)
        assertNull(PokemonTrackingManager.targetSpecies)
    }
}
