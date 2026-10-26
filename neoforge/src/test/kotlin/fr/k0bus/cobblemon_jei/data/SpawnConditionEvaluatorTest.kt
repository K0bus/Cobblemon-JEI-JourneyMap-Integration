package fr.k0bus.cobblemon_jei.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpawnConditionEvaluatorTest {

    @Test
    fun testTimeRangeCalculation() {
        assertEquals("dawn", SpawnConditionEvaluator.getTimeRange(23500))
        assertEquals("dawn", SpawnConditionEvaluator.getTimeRange(500))
        assertEquals("day", SpawnConditionEvaluator.getTimeRange(6000))
        assertEquals("dusk", SpawnConditionEvaluator.getTimeRange(12500))
        assertEquals("night", SpawnConditionEvaluator.getTimeRange(18000))
    }

    @Test
    fun testMatchesTime() {
        assertTrue(SpawnConditionEvaluator.matchesTime("any", "night"))
        assertTrue(SpawnConditionEvaluator.matchesTime("night", "night"))
        assertTrue(SpawnConditionEvaluator.matchesTime("night", "dusk"))
        assertTrue(SpawnConditionEvaluator.matchesTime("day", "day"))
        assertTrue(SpawnConditionEvaluator.matchesTime("day", "dawn"))
        assertFalse(SpawnConditionEvaluator.matchesTime("day", "night"))
    }

    @Test
    fun testMatchesWeather() {
        assertTrue(SpawnConditionEvaluator.matchesWeather("any", isRaining = false, isThundering = false))
        assertTrue(SpawnConditionEvaluator.matchesWeather("rain", isRaining = true, isThundering = false))
        assertTrue(SpawnConditionEvaluator.matchesWeather("clear", isRaining = false, isThundering = false))
        assertFalse(SpawnConditionEvaluator.matchesWeather("rain", isRaining = false, isThundering = false))
        assertFalse(SpawnConditionEvaluator.matchesWeather("clear", isRaining = true, isThundering = false))
    }

    @Test
    fun testRemainingTimeCalculation() {
        // En plein jour (t=6000), attente de la nuit (t=13000) -> 7000 ticks = 350s = 5m 50s
        val descNight = SpawnConditionEvaluator.getRemainingTimeDescription(6000, "night")
        assertEquals("5m 50s", descNight)

        // De nuit (t=18000), attente du jour (t=1000) -> 7000 ticks = 350s = 5m 50s
        val descDay = SpawnConditionEvaluator.getRemainingTimeDescription(18000, "day")
        assertEquals("5m 50s", descDay)

        // Attente 'any' -> null
        assertNull(SpawnConditionEvaluator.getRemainingTimeDescription(6000, "any"))
    }
}
