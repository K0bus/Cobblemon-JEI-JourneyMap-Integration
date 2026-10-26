package fr.k0bus.cobblemon_jei.domain.model

import com.cobblemon.mod.common.api.types.ElementalType

/**
 * Modèle de domaine représentant les détails d'une forme d'un Pokémon.
 */
data class PokemonFormInfo(
    val formName: String,
    val aspects: List<String> = emptyList(),
    val primaryType: ElementalType? = null,
    val secondaryType: ElementalType? = null,
    val isStandard: Boolean = false
)
