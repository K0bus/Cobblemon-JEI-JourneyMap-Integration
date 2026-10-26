package fr.k0bus.cobblemon_jei.config

import fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager
import fr.k0bus.cobblemon_jei.pokedex.SizeFilter

/**
 * Configuration centrale et options utilisateur pour le mod Cobblemon & JourneyMap Integration.
 */
object CobblemonModConfig {

    // Notifications & Alertes visuelles
    var hudNotificationsEnabled: Boolean = true
    var hudSoundEnabled: Boolean = true

    // JourneyMap & Repères
    var autoWaypointOnTracking: Boolean = false

    // Radar & Affichage in-world
    var inWorldGlowing: Boolean
        get() = PokemonTrackingManager.advancedFilter.inWorldGlowing
        set(value) {
            PokemonTrackingManager.advancedFilter.inWorldGlowing = value
            PokemonTrackingManager.saveConfig()
        }

    var inWorldParticles: Boolean
        get() = PokemonTrackingManager.advancedFilter.inWorldParticles
        set(value) {
            PokemonTrackingManager.advancedFilter.inWorldParticles = value
            PokemonTrackingManager.saveConfig()
        }

    var filterOnlyHiddenAbility: Boolean
        get() = PokemonTrackingManager.advancedFilter.onlyHiddenAbility
        set(value) {
            PokemonTrackingManager.advancedFilter.onlyHiddenAbility = value
            PokemonTrackingManager.saveConfig()
        }

    var filterMinPerfectIvs: Int
        get() = PokemonTrackingManager.advancedFilter.minPerfectIvs
        set(value) {
            PokemonTrackingManager.advancedFilter.minPerfectIvs = value.coerceIn(0, 6)
            PokemonTrackingManager.saveConfig()
        }

    var filterSize: SizeFilter
        get() = PokemonTrackingManager.advancedFilter.sizeFilter
        set(value) {
            PokemonTrackingManager.advancedFilter.sizeFilter = value
            PokemonTrackingManager.saveConfig()
        }
}
