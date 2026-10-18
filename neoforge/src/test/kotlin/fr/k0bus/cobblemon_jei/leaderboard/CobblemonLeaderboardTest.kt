package fr.k0bus.cobblemon_jei.leaderboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CobblemonLeaderboardTest {

    @Test
    fun `test CobblemonLeaderboardCategory objectives mapping`() {
        assertEquals("cobble_pokedex", CobblemonLeaderboardCategory.POKEDEX.objectiveName)
        assertEquals("cobble_shinies", CobblemonLeaderboardCategory.SHINIES.objectiveName)
        assertEquals("cobble_captured", CobblemonLeaderboardCategory.CAPTURES.objectiveName)
        assertEquals("cobble_cap_level", CobblemonLeaderboardCategory.CAP_LEVEL.objectiveName)

        assertEquals(CobblemonLeaderboardCategory.POKEDEX, CobblemonLeaderboardCategory.fromObjective("cobble_pokedex"))
        assertEquals(CobblemonLeaderboardCategory.POKEDEX, CobblemonLeaderboardCategory.fromObjective("pokedex"))
        assertEquals(CobblemonLeaderboardCategory.SHINIES, CobblemonLeaderboardCategory.fromObjective("cobble_shinies"))
        assertEquals(CobblemonLeaderboardCategory.SHINIES, CobblemonLeaderboardCategory.fromObjective("shinies"))
        assertEquals(CobblemonLeaderboardCategory.CAPTURES, CobblemonLeaderboardCategory.fromObjective("cobble_captured"))
        assertEquals(CobblemonLeaderboardCategory.CAPTURES, CobblemonLeaderboardCategory.fromObjective("captures"))
        assertEquals(CobblemonLeaderboardCategory.CAP_LEVEL, CobblemonLeaderboardCategory.fromObjective("cobble_cap_level"))
        assertEquals(CobblemonLeaderboardCategory.CAP_LEVEL, CobblemonLeaderboardCategory.fromObjective("levels"))
        assertNull(CobblemonLeaderboardCategory.fromObjective("unknown_objective"))
    }

    @Test
    fun `test LeaderboardPlayerEntry properties`() {
        val entry1 = LeaderboardPlayerEntry(
            rank = 1,
            username = "AshKetchum",
            score = 151,
            isLocalPlayer = false
        )
        val entry2 = LeaderboardPlayerEntry(
            rank = 2,
            username = "PlayerLocal",
            score = 80,
            isLocalPlayer = true
        )

        assertEquals(1, entry1.rank)
        assertEquals("AshKetchum", entry1.username)
        assertEquals(151, entry1.score)
        assertFalse(entry1.isLocalPlayer)

        assertEquals(2, entry2.rank)
        assertEquals("PlayerLocal", entry2.username)
        assertEquals(80, entry2.score)
        assertTrue(entry2.isLocalPlayer)
    }

    @Test
    fun `test isAvailable returns false without active Minecraft level`() {
        // En environnement de test unitaire headless (sans client Minecraft actif),
        // isAvailable doit retourner false de manière sécurisée sans lever d'exception.
        val available = CobblemonScoreboardService.isAvailable()
        assertFalse(available, "Scoreboard ne doit pas être disponible sans monde Minecraft actif")

        val categories = CobblemonScoreboardService.getAvailableCategories()
        assertTrue(categories.isEmpty())

        val entries = CobblemonScoreboardService.getEntries(CobblemonLeaderboardCategory.POKEDEX)
        assertTrue(entries.isEmpty())
    }
}
