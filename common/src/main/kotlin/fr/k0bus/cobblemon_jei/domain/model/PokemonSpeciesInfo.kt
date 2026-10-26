package fr.k0bus.cobblemon_jei.domain.model

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Modèle de domaine représentant une espèce complète de Pokémon, avec ses formes,
 * ses évolutions et ses entrées d'apparition associées.
 */
data class PokemonSpeciesInfo(
    val id: ResourceLocation,
    val name: String,
    val displayName: Component,
    val nationalDexNumber: Int,
    val generation: Int,
    val forms: List<PokemonFormInfo> = emptyList(),
    val evolutions: List<EvolutionInfo> = emptyList(),
    val spawnEntries: List<SpawnEntryInfo> = emptyList()
) {
    /**
     * Recherche la forme standard de l'espèce ou la première disponible.
     */
    val standardForm: PokemonFormInfo?
        get() = forms.firstOrNull { it.isStandard } ?: forms.firstOrNull()

    /**
     * Recherche une forme spécifique par son nom (ex: "alolan", "hisui", "galar").
     */
    fun findForm(formName: String): PokemonFormInfo? {
        return forms.firstOrNull { it.formName.equals(formName, ignoreCase = true) }
    }
}
