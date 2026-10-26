package fr.k0bus.cobblemon_jei.domain.model

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * Modèle de domaine normalisé représentant une branche d'évolution.
 */
data class EvolutionInfo(
    val sourceSpeciesId: ResourceLocation,
    val sourceSpeciesName: String,
    val sourceFormName: String = "normal",
    val targetSpeciesId: ResourceLocation,
    val targetSpeciesName: String,
    val targetFormName: String = "normal",
    val requiredItems: List<ItemStack> = emptyList(),
    val evolutionVariant: String = "level_up",
    val requirementsSummary: String = "",
    val additionalConditions: List<String> = emptyList(),
    val requiredGender: String? = null,
    val requiredMove: String? = null,
    val requiredMoveType: String? = null,
    val useMoveCount: Int? = null,
    val stepsAmount: Int? = null,
    val criticalHits: Int? = null,
    val damageTaken: Int? = null,
    val recoilDamage: Int? = null,
    val defeatTarget: String? = null,
    val defeatAmount: Int? = null,
    val partyMember: String? = null,
    val statCompare: String? = null,
    val moonPhase: String? = null,
    val minLevel: Int? = null,
    val minFriendship: Int? = null
)
