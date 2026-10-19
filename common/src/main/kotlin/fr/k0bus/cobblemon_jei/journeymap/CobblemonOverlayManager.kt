package fr.k0bus.cobblemon_jei.journeymap

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import journeymap.api.v2.client.IClientAPI

/**
 * Façade de rétrocompatibilité pour [SpawnOverlayManager].
 * Redirige toutes les opérations vers le gestionnaire unifié de fusion et de gestion des overlays de rareté.
 */
object CobblemonOverlayManager {

    val isOverlayActive: Boolean
        get() = SpawnOverlayManager.isOverlayActive || fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.isTrackingActive

    val activeSpecies: Species?
        get() = SpawnOverlayManager.activeSpecies
            ?: fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.targetSpecies
            ?: fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.evolutionBaseSpecies

    val activeForm: FormData?
        get() = SpawnOverlayManager.activeForm ?: fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.targetForm

    val scanProgress: SpawnOverlayManager.ScanProgress
        get() = SpawnOverlayManager.scanProgress

    fun initialize(api: IClientAPI) {
        SpawnOverlayManager.initialize(api)
    }

    fun setOverlay(species: Species?, form: FormData? = null) {
        if (species != null) {
            fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.trackSingle(species, form)
        } else {
            clearOverlay()
        }
    }

    fun clearOverlay() {
        fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager.stopTracking()
    }

    fun getElementColor(typeName: String): Int {
        return SpawnOverlayManager.getElementColor(typeName)
    }
}
