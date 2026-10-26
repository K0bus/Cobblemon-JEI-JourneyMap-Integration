package fr.k0bus.cobblemon_jei.pokedex

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RadarAdvancedFilterTest {

    @Test
    fun testSizeFilterLogic() {
        val micro = 0.65f
        val normal = 1.0f
        val giant = 1.4f

        assertTrue(SizeFilter.ANY.matches(micro))
        assertTrue(SizeFilter.ANY.matches(normal))
        assertTrue(SizeFilter.ANY.matches(giant))

        assertTrue(SizeFilter.ONLY_MICROSCOPIC.matches(micro))
        assertFalse(SizeFilter.ONLY_MICROSCOPIC.matches(normal))
        assertFalse(SizeFilter.ONLY_MICROSCOPIC.matches(giant))

        assertFalse(SizeFilter.ONLY_GIGANTIC.matches(micro))
        assertFalse(SizeFilter.ONLY_GIGANTIC.matches(normal))
        assertTrue(SizeFilter.ONLY_GIGANTIC.matches(giant))

        assertTrue(SizeFilter.RARE_ONLY.matches(micro))
        assertFalse(SizeFilter.RARE_ONLY.matches(normal))
        assertTrue(SizeFilter.RARE_ONLY.matches(giant))
    }

    @Test
    fun testJsonSerialization() {
        val filter = RadarAdvancedFilter(
            onlyHiddenAbility = true,
            minPerfectIvs = 3,
            sizeFilter = SizeFilter.ONLY_MICROSCOPIC,
            targetNature = "jolly",
            inWorldGlowing = true,
            inWorldParticles = false
        )

        val json = filter.toJson()
        val deserialized = RadarAdvancedFilter()
        deserialized.fromJson(json)

        assertTrue(deserialized.onlyHiddenAbility)
        assertEquals(3, deserialized.minPerfectIvs)
        assertEquals(SizeFilter.ONLY_MICROSCOPIC, deserialized.sizeFilter)
        assertEquals("jolly", deserialized.targetNature)
        assertTrue(deserialized.inWorldGlowing)
        assertFalse(deserialized.inWorldParticles)
        assertTrue(deserialized.hasAnyFilter())
    }

    @Test
    fun testResetFilters() {
        val filter = RadarAdvancedFilter(
            onlyHiddenAbility = true,
            minPerfectIvs = 5,
            sizeFilter = SizeFilter.RARE_ONLY,
            targetNature = "adamant"
        )
        assertTrue(filter.hasAnyFilter())

        filter.resetFilters()
        assertFalse(filter.onlyHiddenAbility)
        assertEquals(0, filter.minPerfectIvs)
        assertEquals(SizeFilter.ANY, filter.sizeFilter)
        assertNull(filter.targetNature)
        assertFalse(filter.hasAnyFilter())
    }
}
