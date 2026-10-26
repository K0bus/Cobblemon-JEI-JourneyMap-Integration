package fr.k0bus.cobblemon_jei.domain.model

import net.minecraft.resources.ResourceLocation

/**
 * Entrée d'apparition normalisée d'un Pokémon, totalement indépendante de JEI ou JourneyMap.
 */
data class SpawnEntryInfo(
    val speciesId: ResourceLocation,
    val speciesName: String,
    val formName: String = "normal",
    val isNaturalSpawn: Boolean = true,
    val bucket: String = "common", // common, uncommon, rare, ultra-rare
    val weight: Float = 0f,
    val levelMin: Int = 1,
    val levelMax: Int = 100,
    val condition: SpawnConditionInfo = SpawnConditionInfo(),
    val confidence: SpawnDataConfidence = SpawnDataConfidence.EXACT_DATA,
    val sourceId: ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon", "spawn_pool_world"),
    val notes: String = "",
    val isMonumentSpawn: Boolean = false,
    val monumentStructureId: String? = null,
    val monumentPedestalBlock: String? = null
)
