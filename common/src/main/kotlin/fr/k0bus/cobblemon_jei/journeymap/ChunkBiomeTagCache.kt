package fr.k0bus.cobblemon_jei.journeymap

import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

/**
 * Profil léger précalculé des tags et identifiants d'un biome.
 * Résout et indexe une seule fois les tags officiels Minecraft et Cobblemon (notamment `is_*`),
 * évitant ainsi de requêter répétitivement les registres ou de manipuler des chaînes de caractères.
 */
data class BiomeTagProfile(
    val biomeId: String,
    val biomePath: String,
    val tags: Set<String>,
    val isTags: Set<String>
) {
    /**
     * Vérifie rapidement si un tag ou identifiant est satisfait.
     * Supporte les formats :
     * - "minecraft:plains", "plains"
     * - "#cobblemon:is_forest", "cobblemon:is_forest", "is_forest", "#is_forest"
     */
    fun matchesTagOrId(tagOrId: String): Boolean {
        if (tagOrId == biomeId || tagOrId == biomePath) return true
        val clean = tagOrId.removePrefix("#")
        val tagPath = if (clean.contains(":")) clean.substringAfter(":") else clean

        return tags.contains(clean) || tags.contains(tagOrId) || tags.contains(tagPath) ||
                isTags.contains(clean) || isTags.contains(tagOrId) || isTags.contains(tagPath)
    }

    /**
     * Vérifie si le biome correspond à au moins une entrée d'un ensemble de tags/biomes.
     */
    fun matchesAny(candidates: Set<String>): Boolean {
        if (candidates.isEmpty()) return false
        if (candidates.contains(biomeId) || candidates.contains(biomePath)) return true
        for (c in candidates) {
            if (matchesTagOrId(c)) return true
        }
        return false
    }
}

/**
 * Gestionnaire de cache haute performance pour les profils de biomes et les tags `is_*` par chunk.
 * Permet une vérification en O(1) sans recalcul de probabilités de spawn.
 */
object ChunkBiomeTagCache {

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

    // Cache des profils par Holder<Biome> (1 instance par biome unique rencontré)
    private val biomeProfileCache = ConcurrentHashMap<Holder<Biome>, BiomeTagProfile>()

    // Cache des profils résolus par ID de biome (pour restauration disque sans Holder immédiat)
    private val profileByIdCache = ConcurrentHashMap<String, BiomeTagProfile>()

    // Cache d'association (Dimension + ChunkPos) -> BiomeTagProfile
    private val chunkProfileCache = ConcurrentHashMap<Pair<ResourceKey<Level>, ChunkPos>, BiomeTagProfile>()

    val NO_MATCH = Any()

    // Cache d'évaluation de spawn par (Species.name + Form.name + BiomeTagProfile.biomeId) -> BiomeMatchResult?
    private val spawnMatchEvaluationCache = ConcurrentHashMap<String, Any>()

    /**
     * Résout ou extrait le profil précalculé d'un [Holder<Biome>].
     */
    fun getOrCreateProfile(holder: Holder<Biome>): BiomeTagProfile {
        return biomeProfileCache.computeIfAbsent(holder) { h ->
            val biomeId = try {
                h.unwrapKey().map { it.location().toString() }.orElse("")
            } catch (_: Throwable) { "" }
            val biomePath = if (biomeId.contains(":")) biomeId.substringAfter(":") else biomeId

            val allTags: MutableSet<String> = try {
                h.tags()
                    .map { it.location().toString() }
                    .collect(Collectors.toSet())
            } catch (_: Throwable) {
                mutableSetOf()
            }

            val isTagSet = mutableSetOf<String>()

            // Indexe tous les tags directs commençant par is_
            for (t in allTags.toList()) {
                val clean = t.removePrefix("#")
                val p = if (clean.contains(":")) clean.substringAfter(":") else clean
                if (p.startsWith("is_")) {
                    addSyntheticTag(allTags, isTagSet, p)
                }
            }

            enrichBiomeTags(biomePath, biomeId, allTags, isTagSet)

            // Ajoute aussi les versions avec préfixe '#' dans allTags
            val prefixed = allTags.map { "#$it" }.toSet()
            allTags.addAll(prefixed)

            BiomeTagProfile(
                biomeId = biomeId,
                biomePath = biomePath,
                tags = allTags,
                isTags = isTagSet
            )
        }
    }

    /**
     * Résout ou reconstruit un profil directement depuis son identifiant (ex: minecraft:plains).
     */
    fun getOrCreateProfileById(biomeId: String, biomeRegistry: net.minecraft.core.Registry<Biome>? = null): BiomeTagProfile {
        if (biomeRegistry != null) {
            try {
                val loc = net.minecraft.resources.ResourceLocation.parse(biomeId)
                val holder = biomeRegistry.getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, loc)).orElse(null)
                if (holder != null) {
                    return getOrCreateProfile(holder)
                }
            } catch (_: Throwable) {}
        }

        return profileByIdCache.computeIfAbsent(biomeId) { id ->
            val cleanId = if (id.startsWith("#")) id.substring(1) else id
            val path = if (cleanId.contains(":")) cleanId.substringAfter(":") else cleanId
            val allTags = mutableSetOf(cleanId, "#$cleanId", path, "#$path")
            val isTagSet = mutableSetOf<String>()

            enrichBiomeTags(path, cleanId, allTags, isTagSet)

            val prefixed = allTags.map { "#$it" }.toSet()
            allTags.addAll(prefixed)

            BiomeTagProfile(
                biomeId = cleanId,
                biomePath = path,
                tags = allTags,
                isTags = isTagSet
            )
        }
    }

    private fun enrichBiomeTags(
        path: String,
        biomeId: String,
        allTags: MutableSet<String>,
        isTagSet: MutableSet<String>
    ) {

            val hasNetherTag = allTags.any { it.contains("is_nether") || it.contains("in_nether") }
            val isNether = biomeId.contains("nether") || biomeId in NETHER_BIOMES ||
                    biomeId == "biomesoplenty:crystalline_chasm" || biomeId == "byg:warped_desert" ||
                    biomeId.startsWith("incendium:") || biomeId.startsWith("betternether:") ||
                    hasNetherTag

            val hasEndTag = allTags.any { it.contains("is_end") || it.contains("in_the_end") }
            val isEnd = biomeId.contains("the_end") || biomeId in END_BIOMES ||
                    biomeId.startsWith("nullscape:") || biomeId.startsWith("betterend:") ||
                    hasEndTag

            val hasAetherTag = allTags.any { it.contains("is_aether") }
            val isAether = biomeId.startsWith("aether:") || biomeId.contains("aether") || hasAetherTag

            // Enrichissement dimensionnel
            if (isNether) {
                addSyntheticTag(allTags, isTagSet, "is_nether")
                if (biomeId.contains("basalt")) {
                    addCustomTag(allTags, isTagSet, "cobblemon:nether/is_basalt")
                    addSyntheticTag(allTags, isTagSet, "is_volcanic")
                }
                if (biomeId.contains("waste") || biomeId == "minecraft:nether_wastes") {
                    addCustomTag(allTags, isTagSet, "cobblemon:nether/is_wasteland")
                }
                if (biomeId.contains("crimson")) {
                    addCustomTag(allTags, isTagSet, "cobblemon:nether/is_crimson")
                    addCustomTag(allTags, isTagSet, "cobblemon:nether/is_forest")
                }
                if (biomeId.contains("warped") || biomeId == "byg:warped_desert") {
                    addCustomTag(allTags, isTagSet, "cobblemon:nether/is_warped")
                    addCustomTag(allTags, isTagSet, "cobblemon:nether/is_fungus")
                }
                if (biomeId.contains("soul")) {
                    addCustomTag(allTags, isTagSet, "cobblemon:nether/is_soul_sand")
                }
            }
            if (isAether) {
                addCustomTag(allTags, isTagSet, "aether:is_aether")
                addSyntheticTag(allTags, isTagSet, "is_aether")
            }
            if (isEnd) {
                addSyntheticTag(allTags, isTagSet, "is_end")
            }
            if (!isNether && !isEnd && !isAether) {
                addSyntheticTag(allTags, isTagSet, "is_overworld")
            }

            // Enrichissement synthétique des biomes de l'Overworld selon leurs IDs/noms

            // Plaines & Prairies
            if (path.contains("plains") || path == "meadow" || path.contains("prairie") ||
                path.contains("pasture") || path.contains("grassland") || path.contains("clearing")) {
                addSyntheticTag(allTags, isTagSet, "is_plains")
                addSyntheticTag(allTags, isTagSet, "is_grassland")
            }

            // Forêts & Bois
            if (path.contains("forest") || path.contains("wood") || path.contains("grove") ||
                path.contains("orchard") || path.contains("taiga")) {
                addSyntheticTag(allTags, isTagSet, "is_forest")
                if (path.contains("birch")) addSyntheticTag(allTags, isTagSet, "is_birch_forest")
                if (path.contains("dark")) {
                    addSyntheticTag(allTags, isTagSet, "is_dark_forest")
                    addSyntheticTag(allTags, isTagSet, "is_spooky")
                }
            }

            // Jungles & Bambous
            if (path.contains("jungle") || path.contains("bamboo") || path.contains("rainforest")) {
                addSyntheticTag(allTags, isTagSet, "is_jungle")
                if (path.contains("bamboo")) addSyntheticTag(allTags, isTagSet, "is_bamboo")
            }

            // Déserts & Terres arides
            if (path.contains("desert") || path.contains("dune") || path.contains("oasis")) {
                addSyntheticTag(allTags, isTagSet, "is_desert")
                addSyntheticTag(allTags, isTagSet, "is_arid")
                addSyntheticTag(allTags, isTagSet, "is_sandy")
            }

            // Savanes
            if (path.contains("savanna") || path.contains("steppe") || path.contains("outback")) {
                addSyntheticTag(allTags, isTagSet, "is_savanna")
                addSyntheticTag(allTags, isTagSet, "is_arid")
            }

            // Badlands & Mesa
            if (path.contains("badlands") || path.contains("mesa") || path.contains("canyon")) {
                addSyntheticTag(allTags, isTagSet, "is_badlands")
                addSyntheticTag(allTags, isTagSet, "is_arid")
                addSyntheticTag(allTags, isTagSet, "is_sandy")
            }

            // Marais
            if (path.contains("swamp") || path.contains("marsh") || path.contains("bog") ||
                path.contains("bayou") || path.contains("mangrove")) {
                addSyntheticTag(allTags, isTagSet, "is_swamp")
                addSyntheticTag(allTags, isTagSet, "is_freshwater")
            }

            // Taïga & Neige / Grand Froid
            if (path.contains("taiga")) {
                addSyntheticTag(allTags, isTagSet, "is_taiga")
            }
            if (path.contains("snow") || path.contains("ice") || path.contains("frozen") ||
                path.contains("glacial") || path.contains("frost") || path.contains("tundra")) {
                addSyntheticTag(allTags, isTagSet, "is_snowy")
                addSyntheticTag(allTags, isTagSet, "is_freezing")
                addSyntheticTag(allTags, isTagSet, "is_cold")
            }

            // Montagnes & Sommets
            if (path.contains("mountain") || path.contains("peak") || path.contains("slope") ||
                path.contains("cliff") || path.contains("hills") || path.contains("highlands") || path == "meadow") {
                addSyntheticTag(allTags, isTagSet, "is_mountain")
                addSyntheticTag(allTags, isTagSet, "is_peak")
                addSyntheticTag(allTags, isTagSet, "is_highlands")
                addSyntheticTag(allTags, isTagSet, "is_hills")
            }

            // Océans & Mers
            if (path.contains("ocean") || path.contains("sea") || path.contains("reef") ||
                path.contains("trench") || path.contains("abyss")) {
                addSyntheticTag(allTags, isTagSet, "is_ocean")
                if (path.contains("deep")) addSyntheticTag(allTags, isTagSet, "is_deep_ocean")
                if (path.contains("warm") || path.contains("lukewarm")) addSyntheticTag(allTags, isTagSet, "is_warm_ocean")
                if (path.contains("cold")) addSyntheticTag(allTags, isTagSet, "is_cold_ocean")
                if (path.contains("frozen")) addSyntheticTag(allTags, isTagSet, "is_frozen_ocean")
            }

            // Rivières & Eau douce
            if (path.contains("river") || path.contains("stream") || path.contains("creek") || path.contains("lake")) {
                addSyntheticTag(allTags, isTagSet, "is_river")
                addSyntheticTag(allTags, isTagSet, "is_freshwater")
            }

            // Plages & Côtes
            if (path.contains("beach") || path.contains("shore") || path.contains("coast")) {
                addSyntheticTag(allTags, isTagSet, "is_beach")
                addSyntheticTag(allTags, isTagSet, "is_coast")
            }

            // Grottes & Souterrain
            if (path.contains("cave") || path.contains("underground") || path == "lush_caves" ||
                path == "dripstone_caves" || path == "deep_dark") {
                addSyntheticTag(allTags, isTagSet, "is_cave")
                addSyntheticTag(allTags, isTagSet, "is_underground")
                if (path.contains("lush")) addSyntheticTag(allTags, isTagSet, "is_lush")
                if (path.contains("dripstone")) addSyntheticTag(allTags, isTagSet, "is_dripstone")
                if (path.contains("deep_dark")) {
                    addSyntheticTag(allTags, isTagSet, "is_deep_dark")
                    addSyntheticTag(allTags, isTagSet, "deep_dark")
                    addSyntheticTag(allTags, isTagSet, "ancient_city")
                    addSyntheticTag(allTags, isTagSet, "is_ancient_city")
                    addSyntheticTag(allTags, isTagSet, "is_spooky")
                }
            }

            // Cerisiers & Champignons
            if (path.contains("cherry") || path.contains("sakura")) {
                addSyntheticTag(allTags, isTagSet, "is_cherry_blossom")
                addSyntheticTag(allTags, isTagSet, "is_floral")
                addSyntheticTag(allTags, isTagSet, "is_magical")
            }
            if (path.contains("mushroom")) {
                addSyntheticTag(allTags, isTagSet, "is_mushroom")
                addSyntheticTag(allTags, isTagSet, "is_magical")
            }
    }

    fun isSubterraneanProfile(profile: BiomeTagProfile): Boolean {
        val path = profile.biomePath.lowercase()
        return path == "lush_caves" || path == "dripstone_caves" || path == "deep_dark" ||
                path.contains("cave") || path.contains("underground") ||
                profile.isTags.contains("is_cave") || profile.isTags.contains("is_lush") ||
                profile.isTags.contains("is_dripstone") || profile.isTags.contains("is_deep_dark") ||
                profile.tags.contains("is_cave")
    }

    private fun addCustomTag(allTags: MutableSet<String>, isTagSet: MutableSet<String>, fullTag: String) {
        val clean = fullTag.removePrefix("#")
        allTags.add(clean)
        allTags.add("#$clean")
        val suffix = if (clean.contains(":")) clean.substringAfter(":") else clean
        allTags.add(suffix)
        allTags.add("#$suffix")
        if (suffix.startsWith("is_")) {
            isTagSet.add(clean)
            isTagSet.add("#$clean")
            isTagSet.add(suffix)
            isTagSet.add("#$suffix")
        }
    }

    private fun addSyntheticTag(allTags: MutableSet<String>, isTagSet: MutableSet<String>, tagSuffix: String) {
        val clean = tagSuffix.removePrefix("#")
        val suffix = if (clean.contains(":")) clean.substringAfter(":") else clean
        addCustomTag(allTags, isTagSet, "minecraft:$suffix")
        addCustomTag(allTags, isTagSet, "cobblemon:$suffix")
        addCustomTag(allTags, isTagSet, "c:$suffix")
        addCustomTag(allTags, isTagSet, suffix)
    }

    data class SubterraneanProfileEntry(
        val yLevel: Int,
        val profile: BiomeTagProfile
    )

    private val subterraneanProfileCache = ConcurrentHashMap<Pair<ResourceKey<Level>, ChunkPos>, java.util.concurrent.CopyOnWriteArrayList<SubterraneanProfileEntry>>()

    fun putSubterraneanProfile(
        dimension: ResourceKey<Level>,
        chunkPos: ChunkPos,
        yLevel: Int,
        holder: Holder<Biome>
    ): BiomeTagProfile {
        val profile = getOrCreateProfile(holder)
        return putSubterraneanProfile(dimension, chunkPos, yLevel, profile)
    }

    fun putSubterraneanProfile(
        dimension: ResourceKey<Level>,
        chunkPos: ChunkPos,
        yLevel: Int,
        profile: BiomeTagProfile
    ): BiomeTagProfile {
        val list = subterraneanProfileCache.computeIfAbsent(Pair(dimension, chunkPos)) { java.util.concurrent.CopyOnWriteArrayList() }
        if (list.none { it.profile.biomePath == profile.biomePath && it.yLevel == yLevel }) {
            list.add(SubterraneanProfileEntry(yLevel, profile))
        }
        return profile
    }

    fun getSubterraneanProfiles(
        dimension: ResourceKey<Level>,
        chunkPos: ChunkPos
    ): List<SubterraneanProfileEntry> {
        return subterraneanProfileCache[Pair(dimension, chunkPos)] ?: emptyList()
    }

    fun getAllSubterraneanProfiles(
        dimension: ResourceKey<Level>
    ): Map<ChunkPos, List<SubterraneanProfileEntry>> {
        val result = mutableMapOf<ChunkPos, List<SubterraneanProfileEntry>>()
        for ((key, list) in subterraneanProfileCache) {
            if (key.first == dimension && list.isNotEmpty()) {
                result[key.second] = list.toList()
            }
        }
        return result
    }

    /**
     * Associe un chunk à son profil de biome dans le cache.
     */
    fun putChunkProfile(dimension: ResourceKey<Level>, chunkPos: ChunkPos, holder: Holder<Biome>): BiomeTagProfile {
        val profile = getOrCreateProfile(holder)
        chunkProfileCache[Pair(dimension, chunkPos)] = profile
        return profile
    }

    fun putChunkProfile(dimension: ResourceKey<Level>, chunkPos: ChunkPos, profile: BiomeTagProfile): BiomeTagProfile {
        chunkProfileCache[Pair(dimension, chunkPos)] = profile
        return profile
    }

    /**
     * Récupère le profil de biome d'un chunk si déjà présent dans le cache.
     */
    fun getChunkProfile(dimension: ResourceKey<Level>, chunkPos: ChunkPos): BiomeTagProfile? {
        return chunkProfileCache[Pair(dimension, chunkPos)]
    }

    /**
     * Retourne tous les profils de chunks en cache pour une dimension donnée.
     */
    fun getChunkProfilesForDimension(dimension: ResourceKey<Level>): Map<ChunkPos, BiomeTagProfile> {
        val result = mutableMapOf<ChunkPos, BiomeTagProfile>()
        for ((key, profile) in chunkProfileCache) {
            if (key.first == dimension) {
                result[key.second] = profile
            }
        }
        return result
    }

    /**
     * Vérifie si des chunks sont déjà indexés en cache pour cette dimension.
     */
    fun hasCachedChunksForDimension(dimension: ResourceKey<Level>): Boolean {
        return chunkProfileCache.keys.any { it.first == dimension }
    }

    /**
     * Clé de cache pour les évaluations d'éligibilité de spawn.
     */
    fun getCachedMatchResult(cacheKey: String): Any? {
        return spawnMatchEvaluationCache[cacheKey]
    }

    fun putCachedMatchResult(cacheKey: String, result: Any?) {
        spawnMatchEvaluationCache[cacheKey] = result ?: NO_MATCH
    }

    /**
     * Réinitialise les caches d'évaluation (ex: rechargement des données de spawn).
     */
    fun clearEvaluationCache() {
        spawnMatchEvaluationCache.clear()
    }

    /**
     * Réinitialise l'intégralité du cache (ex: déconnexion ou changement de monde).
     */
    fun clearAll() {
        biomeProfileCache.clear()
        profileByIdCache.clear()
        chunkProfileCache.clear()
        subterraneanProfileCache.clear()
        spawnMatchEvaluationCache.clear()
    }
}
