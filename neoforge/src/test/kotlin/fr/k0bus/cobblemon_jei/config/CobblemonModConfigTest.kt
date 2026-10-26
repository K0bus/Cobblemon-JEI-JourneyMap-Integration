package fr.k0bus.cobblemon_jei.config

import fr.k0bus.cobblemon_jei.application.scan.GlobalScanService
import fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager
import fr.k0bus.cobblemon_jei.pokedex.SizeFilter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CobblemonModConfigTest {

    @Test
    fun `test mod config toggles and sync with advanced filter`() {
        CobblemonModConfig.hudNotificationsEnabled = true
        assertTrue(CobblemonModConfig.hudNotificationsEnabled)

        CobblemonModConfig.hudNotificationsEnabled = false
        assertFalse(CobblemonModConfig.hudNotificationsEnabled)

        CobblemonModConfig.hudSoundEnabled = true
        assertTrue(CobblemonModConfig.hudSoundEnabled)

        CobblemonModConfig.inWorldGlowing = true
        assertTrue(PokemonTrackingManager.advancedFilter.inWorldGlowing)

        CobblemonModConfig.inWorldParticles = false
        assertFalse(PokemonTrackingManager.advancedFilter.inWorldParticles)

        CobblemonModConfig.filterOnlyHiddenAbility = true
        assertTrue(PokemonTrackingManager.advancedFilter.onlyHiddenAbility)

        CobblemonModConfig.filterMinPerfectIvs = 4
        assertEquals(4, PokemonTrackingManager.advancedFilter.minPerfectIvs)

        CobblemonModConfig.filterSize = SizeFilter.ONLY_GIGANTIC
        assertEquals(SizeFilter.ONLY_GIGANTIC, PokemonTrackingManager.advancedFilter.sizeFilter)
    }

    @Test
    fun `test global scan service initial state and clear cache`() {
        assertFalse(GlobalScanService.isScanning)
        GlobalScanService.clearCache()
        assertEquals(0, GlobalScanService.scannedChunks)
        assertEquals(0, GlobalScanService.progressPercent)
        assertEquals("Cache mémoire réinitialisé.", GlobalScanService.statusMessage)
    }
}
