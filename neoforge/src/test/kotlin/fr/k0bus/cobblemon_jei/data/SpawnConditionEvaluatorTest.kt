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
}
