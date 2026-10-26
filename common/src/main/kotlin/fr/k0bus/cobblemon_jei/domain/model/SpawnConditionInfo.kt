package fr.k0bus.cobblemon_jei.domain.model

import net.minecraft.resources.ResourceLocation

/**
 * Modèle de domaine normalisé représentant l'ensemble des conditions environnementales
 * requises pour l'apparition d'un Pokémon.
 */
data class SpawnConditionInfo(
    val timeRange: String = "any", // day, night, dawn, dusk, any
    val weather: String = "any",   // clear, rain, thundering, any
    val dimensions: Set<ResourceLocation> = emptySet(),
    val biomes: Set<String> = emptySet(),
    val antiBiomes: Set<String> = emptySet(),
    val canSeeSky: Boolean? = null,
    val minSkyLight: Int? = null,
    val maxSkyLight: Int? = null,
    val minLight: Int? = null,
    val maxLight: Int? = null,
    val minY: Int? = null,
    val maxY: Int? = null,
    val neededBaseBlocks: Set<String> = emptySet(),
    val neededNearbyBlocks: Set<String> = emptySet(),
    val moonPhase: Int? = null,
    val isSlimeChunk: Boolean? = null,
    val minLureLevel: Int? = null,
    val maxLureLevel: Int? = null,
    val rodType: String? = null,
    val bait: String? = null,
    val structures: Set<String> = emptySet(),
    val antiStructures: Set<String> = emptySet(),
    val context: String = "grounded"
)
