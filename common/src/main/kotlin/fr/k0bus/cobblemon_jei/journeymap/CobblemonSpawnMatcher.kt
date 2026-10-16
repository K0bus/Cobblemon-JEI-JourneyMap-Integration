package fr.k0bus.cobblemon_jei.journeymap

import fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader
import fr.k0bus.cobblemon_jei.recipe.PokemonSpawnRecipe
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome

/**
 * Moteur de validation des biomes éligibles et résolution du palier de rareté Cobblemon.
 * Filtre les règles de spawn Cobblemon, associe les biomes explorés et partitionne
 * les chunks en groupes homogènes par rareté [SpawnRarity].
 */
object CobblemonSpawnMatcher {

    /**
     * Résultat de correspondance pour un biome donné.
     */
    data class BiomeMatchResult(
        val rarity: SpawnRarity,
        val matchingRecipes: List<PokemonSpawnRecipe>
    )

    private val NETHER_BIOMES = setOf(
        "minecraft:nether_wastes",
        "minecraft:basalt_deltas",
        "minecraft:crimson_forest",
        "minecraft:warped_forest",
        "minecraft:soul_sand_valley"
    )

    private val END_BIOMES = setOf(
        "minecraft:the_end",
        "minecraft:end_highlands",
        "minecraft:end_midlands",
        "minecraft:small_end_islands",
        "minecraft:end_barrens"
    )

    /**
     * Teste si une recette de spawn est valide pour une dimension donnée.
     */
    fun isRecipeInDimension(recipe: PokemonSpawnRecipe, dimension: ResourceKey<Level>): Boolean {
        if (!recipe.isNaturalSpawn) return false
        val dimString = dimension.location().toString()
        val dimNamespace = dimension.location().namespace
        val dimPath = dimension.location().path

        if (recipe.dimensions.isEmpty()) {
            return dimension == Level.OVERWORLD
        }

        return recipe.dimensions.any { d ->
            d == dimString ||
            (dimNamespace == "minecraft" && (
                (dimPath == "the_nether" && (d.contains("nether") || d == "minecraft:the_nether")) ||
                (dimPath == "the_end" && (d.contains("the_end") || d.contains("/end") || d.contains("is_end") || d == "minecraft:the_end")) ||
                (dimPath == "overworld" && (d.contains("overworld") || d == "minecraft:overworld"))
            )) ||
            (dimNamespace != "minecraft" && (
                d.contains(dimNamespace, ignoreCase = true) ||
                d.contains(dimPath, ignoreCase = true)
            ))
        }
    }

    /**
     * Récupère les recettes de spawn valides pour une espèce et une dimension données.
     */
    fun getValidRecipes(
        species: Species,
        form: FormData? = null,
        dimension: ResourceKey<Level>
    ): List<PokemonSpawnRecipe> {
        val allRecipes = PokemonSpawnDataLoader.getSpawnRecipesForSpecies(species, form)
        return allRecipes.filter { recipe -> isRecipeInDimension(recipe, dimension) }
    }

    /**
     * Teste si un holder de biome satisfait les conditions d'une recette.
     */
    fun matchesRecipe(holder: Holder<Biome>, recipe: PokemonSpawnRecipe): Boolean {
        val biomeId = holder.unwrapKey().map { it.location().toString() }.orElse("")
        val biomePath = if (biomeId.contains(":")) biomeId.substringAfter(":") else biomeId

        val tagLocations = holder.tags().map { it.location().toString() }.toList().toMutableSet()

        // Enrichissement synthétique des balises
        if (biomeId.contains("nether") || biomeId in NETHER_BIOMES) {
            tagLocations.add("minecraft:is_nether")
            tagLocations.add("cobblemon:is_nether")
            if (biomeId.contains("basalt")) {
                tagLocations.add("cobblemon:nether/is_basalt")
                tagLocations.add("cobblemon:is_volcanic")
            }
            if (biomeId.contains("waste") || biomeId == "minecraft:nether_wastes") {
                tagLocations.add("cobblemon:nether/is_wasteland")
            }
            if (biomeId.contains("crimson")) {
                tagLocations.add("cobblemon:nether/is_crimson")
                tagLocations.add("cobblemon:nether/is_forest")
            }
            if (biomeId.contains("warped")) {
                tagLocations.add("cobblemon:nether/is_warped")
                tagLocations.add("cobblemon:nether/is_fungus")
            }
            if (biomeId.contains("soul")) {
                tagLocations.add("cobblemon:nether/is_soul_sand")
            }
        }
        if (biomeId.startsWith("aether:") || biomeId.contains("aether")) {
            tagLocations.add("aether:is_aether")
        }
        if (biomeId.contains("the_end") || biomeId in END_BIOMES) {
            tagLocations.add("minecraft:is_end")
            tagLocations.add("cobblemon:is_end")
        }
        if (!biomeId.contains("nether") && !biomeId.contains("end") && !biomeId.startsWith("aether:") && biomeId !in NETHER_BIOMES && biomeId !in END_BIOMES) {
            tagLocations.add("minecraft:is_overworld")
            tagLocations.add("cobblemon:is_overworld")
        }

        val prefixedTags = tagLocations.map { "#$it" }.toSet()

        // 1. Anti-biomes (exclusions prioritaires)
        if (recipe.antiBiomes.isNotEmpty()) {
            if (recipe.antiBiomes.contains(biomeId) ||
                recipe.antiBiomes.contains(biomePath) ||
                recipe.antiBiomes.any { b ->
                    prefixedTags.contains(b) || tagLocations.contains(b.removePrefix("#"))
                }
            ) {
                return false
            }
        }

        // 2. Si aucune restriction de biome n'est définie, le biome est accepté
        if (recipe.biomes.isEmpty()) {
            return true
        }

        // 3. Correspondance directe par ID ou par tag
        if (recipe.biomes.contains(biomeId) || recipe.biomes.contains(biomePath)) return true
        return recipe.biomes.any { b ->
            prefixedTags.contains(b) || tagLocations.contains(b.removePrefix("#"))
        }
    }

    /**
     * Évalue un biome face à une liste de recettes et retourne la rareté maximale applicable.
     */
    fun evaluateBiome(holder: Holder<Biome>, recipes: List<PokemonSpawnRecipe>): BiomeMatchResult? {
        val matching = recipes.filter { matchesRecipe(holder, it) }
        if (matching.isEmpty()) return null

        // En cas de règles multiples (ex. rare de nuit, commun de jour), on sélectionne
        // le palier de rareté le plus élevé pour mettre en valeur les spawns notables
        val bestRarity = matching.map { SpawnRarity.fromBucketOrWeight(it.bucket, it.weight) }
            .maxByOrNull { it.rank } ?: SpawnRarity.COMMON

        return BiomeMatchResult(bestRarity, matching)
    }

    /**
     * Groupe l'ensemble des chunks explorés par palier de rareté [SpawnRarity].
     *
     * @param chunkBiomes Association des positions de chunk avec leur biome de surface respectif.
     * @param recipes Règles de spawn actives.
     * @return Map associant chaque [SpawnRarity] au set des [ChunkPos] éligibles.
     */
    fun groupChunksByRarity(
        chunkBiomes: Map<ChunkPos, Holder<Biome>>,
        recipes: List<PokemonSpawnRecipe>
    ): Map<SpawnRarity, Set<ChunkPos>> {
        if (recipes.isEmpty() || chunkBiomes.isEmpty()) return emptyMap()

        // Cache des résultats par Holder<Biome> pour éviter de réévaluer le matching sur des milliers de chunks
        val biomeEvaluationCache = mutableMapOf<Holder<Biome>, BiomeMatchResult?>()
        val result = mutableMapOf<SpawnRarity, MutableSet<ChunkPos>>()

        for ((chunkPos, biomeHolder) in chunkBiomes) {
            val match = biomeEvaluationCache.computeIfAbsent(biomeHolder) { holder ->
                evaluateBiome(holder, recipes)
            } ?: continue

            result.computeIfAbsent(match.rarity) { mutableSetOf() }.add(chunkPos)
        }

        return result
    }
}
