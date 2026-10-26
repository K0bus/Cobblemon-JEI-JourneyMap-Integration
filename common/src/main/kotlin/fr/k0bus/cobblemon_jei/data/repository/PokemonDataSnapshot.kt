package fr.k0bus.cobblemon_jei.data.repository

import fr.k0bus.cobblemon_jei.domain.model.EvolutionInfo
import fr.k0bus.cobblemon_jei.domain.model.PokemonSpeciesInfo
import fr.k0bus.cobblemon_jei.domain.model.SpawnEntryInfo
import net.minecraft.resources.ResourceLocation

/**
 * Snapshot immuable représentant l'état consolidé des données Cobblemon
 * (espèces, formes, apparitions, évolutions) à un instant T.
 */
data class PokemonDataSnapshot(
    val speciesById: Map<ResourceLocation, PokemonSpeciesInfo>,
    val speciesByName: Map<String, PokemonSpeciesInfo>,
    val allSpawns: List<SpawnEntryInfo>,
    val allEvolutions: List<EvolutionInfo>,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    /**
     * Recherche une espèce par son identifiant ResourceLocation (ex: "cobblemon:charizard").
     */
    fun getSpecies(id: ResourceLocation): PokemonSpeciesInfo? = speciesById[id]

    /**
     * Recherche une espèce par son nom interne (insensible à la casse).
     */
    fun getSpeciesByName(name: String): PokemonSpeciesInfo? = speciesByName[name.lowercase()]

    /**
     * Recherche toutes les entrées d'apparition pour une espèce (et forme optionnelle).
     */
    fun getSpawnsForSpecies(speciesName: String, formName: String? = null): List<SpawnEntryInfo> {
        return allSpawns.filter { entry ->
            entry.speciesName.equals(speciesName, ignoreCase = true) &&
                    (formName == null || entry.isMonumentSpawn || entry.formName.equals(formName, ignoreCase = true) || entry.formName.isEmpty())
        }
    }

    /**
     * Recherche toutes les évolutions partant d'une espèce (source).
     */
    fun getEvolutionsForSpecies(speciesName: String, formName: String? = null): List<EvolutionInfo> {
        return allEvolutions.filter { evo ->
            evo.sourceSpeciesName.equals(speciesName, ignoreCase = true) &&
                    (formName == null || evo.sourceFormName.equals(formName, ignoreCase = true))
        }
    }

    /**
     * Filtre les entrées d'apparition compatibles avec une dimension et un biome donnés.
     */
    fun getSpawnsForDimensionAndBiome(dimension: String, biome: String): List<SpawnEntryInfo> {
        val dimNorm = dimension.lowercase()
        val biomeNorm = biome.lowercase()
        return allSpawns.filter { entry ->
            val dimMatches = entry.condition.dimensions.isEmpty() ||
                    entry.condition.dimensions.any { it.toString().equals(dimNorm, ignoreCase = true) || it.path.equals(dimNorm, ignoreCase = true) }
            val biomeMatches = entry.condition.biomes.isEmpty() ||
                    entry.condition.biomes.any { it.equals(biomeNorm, ignoreCase = true) || it.removePrefix("#").equals(biomeNorm, ignoreCase = true) }
            val antiBiomeMatches = entry.condition.antiBiomes.any { it.equals(biomeNorm, ignoreCase = true) }

            dimMatches && biomeMatches && !antiBiomeMatches
        }
    }

    /**
     * Calcule le niveau de confiance global des données de spawn pour une espèce.
     */
    fun getSpawnConfidence(speciesName: String): fr.k0bus.cobblemon_jei.domain.model.SpawnDataConfidence {
        val spawns = getSpawnsForSpecies(speciesName)
        if (spawns.isEmpty()) return fr.k0bus.cobblemon_jei.domain.model.SpawnDataConfidence.UNKNOWN
        return spawns.minByOrNull { it.confidence.ordinal }?.confidence ?: fr.k0bus.cobblemon_jei.domain.model.SpawnDataConfidence.EXACT_DATA
    }

    companion object {
        val EMPTY = PokemonDataSnapshot(
            speciesById = emptyMap(),
            speciesByName = emptyMap(),
            allSpawns = emptyList(),
            allEvolutions = emptyList()
        )
    }
}
