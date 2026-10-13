package com.cobblemon.jei.journeymap

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import journeymap.api.v2.client.IClientAPI

/**
 * Façade de rétrocompatibilité pour [SpawnOverlayManager].
 * Redirige toutes les opérations vers le gestionnaire unifié de fusion et de gestion des overlays de rareté.
 */
object CobblemonOverlayManager {

    val isOverlayActive: Boolean
        get() = SpawnOverlayManager.isOverlayActive

    val activeSpecies: Species?
        get() = SpawnOverlayManager.activeSpecies

    val activeForm: FormData?
        get() = SpawnOverlayManager.activeForm

    val scanProgress: SpawnOverlayManager.ScanProgress
        get() = SpawnOverlayManager.scanProgress

    fun initialize(api: IClientAPI) {
        SpawnOverlayManager.initialize(api)
    }

    fun setOverlay(species: Species?, form: FormData? = null) {
        SpawnOverlayManager.setOverlay(species, form)
    }

    fun clearOverlay() {
        SpawnOverlayManager.clearOverlay()
    }

    fun getElementColor(typeName: String): Int {
        return SpawnOverlayManager.getElementColor(typeName)
    }
}
