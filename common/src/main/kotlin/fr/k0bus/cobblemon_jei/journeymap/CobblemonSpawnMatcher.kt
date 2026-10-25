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
 * Optimisé avec [ChunkBiomeTagCache] pour une résolution en temps constant O(1).
 */
object CobblemonSpawnMatcher {

    /**
     * Résultat de correspondance pour un biome donné.
     */
    data class BiomeMatchResult(
        val rarity: SpawnRarity,
        val matchingRecipes: List<PokemonSpawnRecipe>
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
     * Teste si un profil de tags de biome satisfait les conditions d'une recette en O(1).
     */
    fun matchesProfile(profile: BiomeTagProfile, recipe: PokemonSpawnRecipe): Boolean {
        // 1. Anti-biomes (exclusions prioritaires)
        if (recipe.antiBiomes.isNotEmpty() && profile.matchesAny(recipe.antiBiomes)) {
            return false
        }

        // 2. Si aucune restriction de biome n'est définie, le biome est accepté
        if (recipe.biomes.isEmpty()) {
            return true
        }

        // 3. Correspondance directe par ID ou par tag (incluant is_*)
        return profile.matchesAny(recipe.biomes)
    }

    /**
     * Teste si un holder de biome satisfait les conditions d'une recette.
     */
    fun matchesRecipe(holder: Holder<Biome>, recipe: PokemonSpawnRecipe): Boolean {
        val profile = ChunkBiomeTagCache.getOrCreateProfile(holder)
        return matchesProfile(profile, recipe)
    }

    /**
     * Évalue un profil de biome face à une liste de recettes et retourne la rareté maximale applicable.
     */
    fun evaluateProfile(profile: BiomeTagProfile, recipes: List<PokemonSpawnRecipe>): BiomeMatchResult? {
        val matching = recipes.filter { matchesProfile(profile, it) }
        if (matching.isEmpty()) return null

        val bestRarity = matching.map { SpawnRarity.fromBucketOrWeight(it.bucket, it.weight) }
            .maxByOrNull { it.rank } ?: SpawnRarity.COMMON

        return BiomeMatchResult(bestRarity, matching)
    }

    /**
     * Évalue un biome face à une liste de recettes et retourne la rareté maximale applicable.
     */
    fun evaluateBiome(holder: Holder<Biome>, recipes: List<PokemonSpawnRecipe>): BiomeMatchResult? {
        val profile = ChunkBiomeTagCache.getOrCreateProfile(holder)
        return evaluateProfile(profile, recipes)
    }

    /**
     * Groupe l'ensemble des profils de chunks explorés par palier de rareté [SpawnRarity].
     * Utilise le cache de profils et le cache d'évaluation O(1) [ChunkBiomeTagCache].
     */
    fun groupChunksByProfile(
        chunkProfiles: Map<ChunkPos, BiomeTagProfile>,
        recipes: List<PokemonSpawnRecipe>,
        speciesName: String = "",
        formName: String = ""
    ): Map<SpawnRarity, Set<ChunkPos>> {
        if (recipes.isEmpty() || chunkProfiles.isEmpty()) return emptyMap()

        val profileEvaluationCache = mutableMapOf<BiomeTagProfile, BiomeMatchResult?>()
        val result = mutableMapOf<SpawnRarity, MutableSet<ChunkPos>>()

        for ((chunkPos, profile) in chunkProfiles) {
            val match = profileEvaluationCache.computeIfAbsent(profile) { p ->
                val cacheKey = if (speciesName.isNotEmpty()) "$speciesName:$formName:${p.biomeId}" else null
                if (cacheKey != null) {
                    val cached = ChunkBiomeTagCache.getCachedMatchResult(cacheKey)
                    if (cached === ChunkBiomeTagCache.NO_MATCH) return@computeIfAbsent null
                    if (cached is BiomeMatchResult) return@computeIfAbsent cached
                }
                val evaluated = evaluateProfile(p, recipes)
                if (cacheKey != null) {
                    ChunkBiomeTagCache.putCachedMatchResult(cacheKey, evaluated)
                }
                evaluated
            } ?: continue

            result.computeIfAbsent(match.rarity) { mutableSetOf() }.add(chunkPos)
        }

        return result
    }

    /**
     * Groupe l'ensemble des chunks explorés par palier de rareté [SpawnRarity].
     * Utilise le cache de profils [ChunkBiomeTagCache] pour éliminer les recalculs.
     */
    fun groupChunksByRarity(
        chunkBiomes: Map<ChunkPos, Holder<Biome>>,
        recipes: List<PokemonSpawnRecipe>
    ): Map<SpawnRarity, Set<ChunkPos>> {
        if (recipes.isEmpty() || chunkBiomes.isEmpty()) return emptyMap()
        val profiles = chunkBiomes.mapValues { ChunkBiomeTagCache.getOrCreateProfile(it.value) }
        val first = recipes.firstOrNull()
        val speciesName = try { first?.species?.name ?: "" } catch (_: Throwable) { "" }
        val formName = try { first?.form?.name ?: "" } catch (_: Throwable) { "" }
        return groupChunksByProfile(profiles, recipes, speciesName, formName)
    }
}
