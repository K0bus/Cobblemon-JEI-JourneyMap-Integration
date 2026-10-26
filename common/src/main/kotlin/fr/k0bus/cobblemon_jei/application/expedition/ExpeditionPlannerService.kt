package fr.k0bus.cobblemon_jei.application.expedition

import fr.k0bus.cobblemon_jei.data.repository.PokemonDataRepository
import fr.k0bus.cobblemon_jei.data.repository.PokemonDataSnapshot
import fr.k0bus.cobblemon_jei.domain.expedition.*
import fr.k0bus.cobblemon_jei.application.goals.GoalService
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import fr.k0bus.cobblemon_jei.domain.model.SpawnConditionInfo
import fr.k0bus.cobblemon_jei.domain.model.SpawnEntryInfo
import fr.k0bus.cobblemon_jei.journeymap.ChunkBiomeTagCache
import fr.k0bus.cobblemon_jei.recipe.PokemonSpawnRecipe
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.sqrt

/**
 * Service de calcul et de planification spatiale des expéditions de capture.
 *
 * Analyse les conditions de spawn des Pokémon ciblés et identifie les zones
 * géographiques explorées offrant le meilleur rendement de découverte.
 */
object ExpeditionPlannerService {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/ExpeditionPlanner")

    private val BROAD_BIOME_PATTERNS = setOf(
        "is_overworld", "in_overworld", "overworld",
        "is_nether", "in_nether", "nether", "the_nether",
        "is_end", "in_the_end", "is_in_the_end", "the_end",
        "is_aether", "in_aether", "aether"
    )

    fun isBroadBiomeFilter(tagOrId: String): Boolean {
        val clean = tagOrId.removePrefix("#").lowercase()
        val path = clean.substringAfter(':')
        return BROAD_BIOME_PATTERNS.contains(path) || path.endsWith("/is_overworld") || path.endsWith("/overworld")
    }

    /**
     * Détermine si une entrée d'apparition nécessite des éléments particuliers
     * (arbres à miel/saccharine, paratonnerres, PC, minerais spécifiques, structures, pêche, slime chunk, etc.)
     * qui ne peuvent pas être déduits de la simple exploration géographique d'un biome sauvage.
     */
    fun hasSpecialSpawnRequirement(entry: SpawnEntryInfo): Boolean {
        if (!entry.isNaturalSpawn || entry.isMonumentSpawn) return true
        return hasSpecialSpawnRequirement(entry.condition)
    }

    fun hasSpecialSpawnRequirement(condition: SpawnConditionInfo): Boolean {
        // 1. Blocs proches requis spécifiques (arbres saccharine / miel, paratonnerres, PC, minerais, récoltes, laines, etc.)
        if (condition.neededNearbyBlocks.isNotEmpty()) {
            val nonFluid = condition.neededNearbyBlocks.filterNot { b ->
                val clean = b.removePrefix("#").lowercase()
                clean.endsWith("water") || clean.endsWith("lava") ||
                        clean.endsWith("seagrass") || clean.endsWith("kelp_plant") ||
                        clean.endsWith("lily_pad")
            }
            if (nonFluid.isNotEmpty()) return true
        }

        // 2. Structures de génération requises (villages, manoirs, pyramides, avant-postes, donjons, etc.)
        if (condition.structures.isNotEmpty()) return true

        // 3. Blocs de base artificiels requis (béton, quartz, etc.)
        if (condition.neededBaseBlocks.isNotEmpty()) {
            val nonNaturalBase = condition.neededBaseBlocks.filterNot { b ->
                val clean = b.removePrefix("#").lowercase()
                clean.contains("sand") || clean.contains("dirt") || clean.contains("grass") ||
                        clean.contains("stone") || clean.contains("gravel") || clean.contains("mud") ||
                        clean.contains("clay") || clean.contains("snow") || clean.contains("ice")
            }
            if (nonNaturalBase.isNotEmpty()) return true
        }

        // 4. Conditions spécifiques (pêche, appâts, slime chunks)
        if (condition.rodType != null || condition.bait != null) return true
        if (condition.isSlimeChunk == true) return true

        return false
    }

    fun hasSpecialSpawnRequirement(recipe: PokemonSpawnRecipe): Boolean {
        if (!recipe.isNaturalSpawn || recipe.isMonumentSpawn) return true
        if (recipe.neededNearbyBlocks.isNotEmpty()) {
            val nonFluid = recipe.neededNearbyBlocks.filterNot { b ->
                val clean = b.removePrefix("#").lowercase()
                clean.endsWith("water") || clean.endsWith("lava") ||
                        clean.endsWith("seagrass") || clean.endsWith("kelp_plant") ||
                        clean.endsWith("lily_pad")
            }
            if (nonFluid.isNotEmpty()) return true
        }
        if (recipe.structures.isNotEmpty()) return true
        if (recipe.neededBaseBlocks.isNotEmpty()) {
            val nonNaturalBase = recipe.neededBaseBlocks.filterNot { b ->
                val clean = b.removePrefix("#").lowercase()
                clean.contains("sand") || clean.contains("dirt") || clean.contains("grass") ||
                        clean.contains("stone") || clean.contains("gravel") || clean.contains("mud") ||
                        clean.contains("clay") || clean.contains("snow") || clean.contains("ice")
            }
            if (nonNaturalBase.isNotEmpty()) return true
        }
        if (recipe.rodType != null || recipe.bait != null) return true
        if (recipe.isSlimeChunk == true) return true
        return false
    }

    /**
     * Vérifie si une espèce possède au moins un spawn sauvage naturel valide pour une expédition géographique
     * (non issu d'un monument, sans bloc/structure particulier, et ciblant au moins un biome concret).
     */
    fun isSpeciesEligibleForExpedition(speciesName: String, snapshot: PokemonDataSnapshot): Boolean {
        val normName = speciesName.lowercase()
        if (fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat.isCoveredSpecies(normName)) {
            return false
        }
        val spawns = snapshot.getSpawnsForSpecies(normName)
        val wildSpawns = spawns.filter { s ->
            s.isNaturalSpawn && !s.isMonumentSpawn && !hasSpecialSpawnRequirement(s)
        }
        if (wildSpawns.isEmpty()) return false
        return wildSpawns.any { s -> s.condition.biomes.any { b -> !isBroadBiomeFilter(b) } }
    }

    fun getFriendlyBiomeName(biomePath: String): String {
        return when (biomePath.lowercase()) {
            "plains" -> "Plaines"
            "sunflower_plains" -> "Plaines de Tournesols"
            "forest" -> "Forêt"
            "flower_forest" -> "Forêt Fleurie"
            "birch_forest" -> "Forêt de Bouleaux"
            "old_growth_birch_forest" -> "Forêt de Bouleaux Anciens"
            "dark_forest" -> "Forêt Sombre"
            "taiga" -> "Taïga"
            "old_growth_pine_taiga" -> "Taïga de Pins Anciens"
            "old_growth_spruce_taiga" -> "Taïga d'Épicéas Anciens"
            "snowy_taiga" -> "Taïga Enneigée"
            "jungle" -> "Jungle"
            "bamboo_jungle" -> "Jungle de Bambou"
            "sparse_jungle" -> "Jungle Clairsemée"
            "desert" -> "Désert"
            "badlands" -> "Badlands"
            "eroded_badlands" -> "Badlands Érodées"
            "wooded_badlands" -> "Badlands Boisées"
            "swamp" -> "Marais"
            "mangrove_swamp" -> "Mangrove"
            "savanna" -> "Savane"
            "savanna_plateau" -> "Plateau de Savane"
            "windswept_savanna" -> "Savane Venteuse"
            "windswept_hills" -> "Collines Venteuses"
            "windswept_gravelly_hills" -> "Collines Venteuses Gravillonneuses"
            "windswept_forest" -> "Forêt Venteuse"
            "meadow" -> "Prairie"
            "cherry_grove" -> "Bosquet de Cerisiers"
            "grove" -> "Bosquet"
            "snowy_slopes" -> "Pentes Enneigées"
            "jagged_peaks" -> "Pics Déchiquetés"
            "frozen_peaks" -> "Pics Gelés"
            "stony_peaks" -> "Pics Rocheux"
            "river" -> "Rivière"
            "frozen_river" -> "Rivière Gelée"
            "beach" -> "Plage"
            "snowy_beach" -> "Plage Enneigée"
            "stony_shore" -> "Rivage Rocheux"
            "warm_ocean" -> "Océan Chaud"
            "lukewarm_ocean" -> "Océan Tiède"
            "deep_lukewarm_ocean" -> "Océan Tiède Profond"
            "ocean" -> "Océan"
            "deep_ocean" -> "Océan Profond"
            "cold_ocean" -> "Océan Froid"
            "deep_cold_ocean" -> "Océan Froid Profond"
            "frozen_ocean" -> "Océan Gelé"
            "deep_frozen_ocean" -> "Océan Gelé Profond"
            "dripstone_caves" -> "Grottes de Spéléothèmes (Dripstone Caves)"
            "lush_caves" -> "Grottes Végétalisées (Lush Caves)"
            "deep_dark" -> "Ténèbres Profondes (Deep Dark & Cité Antique)"
            else -> biomePath.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        }
    }

    val PRIMARY_REGIONAL_TAGS = setOf(
        "is_jungle", "is_forest", "is_taiga", "is_plains",
        "is_badlands", "is_savanna", "is_desert", "is_swamp",
        "is_ocean", "is_mountain", "is_river", "is_beach",
        "is_nether", "is_end"
    )

    fun getBiomeFamily(biomePath: String): String {
        val p = biomePath.lowercase()
        return when {
            p.contains("bamboo_jungle") || p.contains("sparse_jungle") || p.contains("jungle") -> "jungle"
            p.contains("flower_forest") || p.contains("birch_forest") || p.contains("dark_forest") || p.contains("forest") -> "forest"
            p.contains("snowy_taiga") || p.contains("pine_taiga") || p.contains("spruce_taiga") || p.contains("taiga") -> "taiga"
            p.contains("sunflower_plains") || p.contains("snowy_plains") || p.contains("meadow") || p.contains("plains") -> "plains"
            p.contains("eroded_badlands") || p.contains("wooded_badlands") || p.contains("badlands") || p.contains("mesa") -> "badlands"
            p.contains("windswept_savanna") || p.contains("savanna") -> "savanna"
            p.contains("desert") -> "desert"
            p.contains("mangrove") || p.contains("swamp") -> "swamp"
            p.contains("deep_ocean") || p.contains("frozen_ocean") || p.contains("lukewarm_ocean") || p.contains("cold_ocean") || p.contains("ocean") -> "ocean"
            p.contains("frozen_river") || p.contains("river") -> "river"
            p.contains("beach") || p.contains("stony_shore") -> "beach"
            p.contains("jagged_peaks") || p.contains("frozen_peaks") || p.contains("stony_peaks") || p.contains("snowy_slopes") || p.contains("cherry_grove") || p.contains("grove") || p.contains("mountain") || p.contains("peaks") -> "mountain"
            p.contains("lush_caves") -> "lush_caves"
            p.contains("dripstone_caves") -> "dripstone_caves"
            p.contains("deep_dark") -> "deep_dark"
            p.contains("nether_wastes") || p.contains("soul_sand_valley") || p.contains("crimson_forest") || p.contains("warped_forest") || p.contains("basalt_deltas") -> "nether"
            p.contains("the_end") || p.contains("end_highlands") || p.contains("end_midlands") || p.contains("small_end_islands") || p.contains("end_barrens") -> "end"
            else -> p
        }
    }

    fun isSameBiomeRegion(
        seedProfile: fr.k0bus.cobblemon_jei.journeymap.BiomeTagProfile?,
        neighborProfile: fr.k0bus.cobblemon_jei.journeymap.BiomeTagProfile?
    ): Boolean {
        if (seedProfile == null || neighborProfile == null) return false
        val seedId = seedProfile.biomeId.ifEmpty { seedProfile.biomePath }
        val neighborId = neighborProfile.biomeId.ifEmpty { neighborProfile.biomePath }
        if (seedId.isNotEmpty() && seedId == neighborId) return true

        val seedFamily = getBiomeFamily(seedProfile.biomePath.ifEmpty { seedProfile.biomeId })
        val neighborFamily = getBiomeFamily(neighborProfile.biomePath.ifEmpty { neighborProfile.biomeId })
        if (seedFamily.isNotEmpty() && seedFamily == neighborFamily && seedFamily != "unknown") return true

        val sharedTags = seedProfile.isTags.intersect(neighborProfile.isTags)
        if (sharedTags.any { it in PRIMARY_REGIONAL_TAGS }) {
            return true
        }

        return false
    }

    /**
     * Calcule un plan d'expédition pour l'ensemble des objectifs actuellement actifs.
     */
    fun planForActiveGoals(name: String = "Expédition Objectifs"): ExpeditionPlan {
        val activeTargets = GoalService.getActiveTargets()
        return createPlan(name, activeTargets)
    }

    /**
     * Calcule un plan d'expédition complet pour une liste arbitraire de cibles.
     */
    fun createPlan(
        name: String,
        targets: List<PokemonTarget>,
        dimensionKey: ResourceKey<Level>? = null
    ): ExpeditionPlan {
        val mc = try { Minecraft.getInstance() } catch (_: Throwable) { null }
        val player = mc?.player
        val level = mc?.level

        val dim = dimensionKey ?: level?.dimension() ?: ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
        )

        val playerPos = player?.blockPosition() ?: BlockPos.ZERO

        if (targets.isEmpty()) {
            return ExpeditionPlan(
                name = name,
                targets = emptyList(),
                rankedZones = emptyList()
            )
        }

        val snapshot = PokemonDataRepository.getSnapshot()
        var profiles = ChunkBiomeTagCache.getChunkProfilesForDimension(dim)

        // 1. Tenter la restauration depuis le cache disque si aucun chunk en mémoire
        if (profiles.isEmpty()) {
            val worldId = fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager.activeSessionId
                ?: fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager.resolveCurrentWorldId()
            val restored = fr.k0bus.cobblemon_jei.infrastructure.persistence.DiskChunkCacheStorage.loadIntoCache(worldId, dim)
            if (restored > 0) {
                profiles = ChunkBiomeTagCache.getChunkProfilesForDimension(dim)
            }
        }

        // 2. Si toujours aucun chunk en cache (ex: nouveau monde sans scan préalable),
        // échantillonner les chunks chargés autour du joueur pour permettre des expéditions locales immédiates
        if (profiles.isEmpty() && level != null && player != null) {
            val playerChunk = player.chunkPosition()
            val caveYLevels = intArrayOf(16, -20, -45)
            for (dx in -8..8) {
                for (dz in -8..8) {
                    val p = ChunkPos(playerChunk.x + dx, playerChunk.z + dz)
                    val sampleBlockX = p.minBlockX + 8
                    val sampleBlockZ = p.minBlockZ + 8
                    val holder = level.getNoiseBiome(sampleBlockX shr 2, player.blockY shr 2, sampleBlockZ shr 2)
                    ChunkBiomeTagCache.putChunkProfile(dim, p, holder)

                    if (dim == Level.OVERWORLD) {
                        for (cY in caveYLevels) {
                            val caveHolder = level.getNoiseBiome(sampleBlockX shr 2, cY shr 2, sampleBlockZ shr 2)
                            val caveProfile = ChunkBiomeTagCache.getOrCreateProfile(caveHolder)
                            if (ChunkBiomeTagCache.isSubterraneanProfile(caveProfile)) {
                                ChunkBiomeTagCache.putSubterraneanProfile(dim, p, cY, caveProfile)
                            }
                        }
                    }
                }
            }
            profiles = ChunkBiomeTagCache.getChunkProfilesForDimension(dim)
        }

        // 3. Filtrer les cibles éligibles aux expéditions :
        // Un Pokémon régi par un monument légendaire, nécessitant des conditions particulières
        // (arbres à miel/saccharine, PC, paratonnerre, structures) ou n'ayant AUCUN spawn sauvage naturel
        // ne doit JAMAIS être intégré aux expéditions géographiques.
        val eligibleTargets = targets.filter { target ->
            val speciesName = target.speciesId.path.lowercase()
            isSpeciesEligibleForExpedition(speciesName, snapshot)
        }

        if (eligibleTargets.isEmpty()) {
            return ExpeditionPlan(
                name = name,
                targets = emptyList(),
                rankedZones = emptyList()
            )
        }

        // 4. Préparer les conditions de spawn pour chaque cible éligible (spawns sauvages purs)
        val targetSpawnData = eligibleTargets.map { target ->
            val speciesName = target.speciesId.path.lowercase()
            val spawns = snapshot.getSpawnsForSpecies(speciesName).filter {
                it.isNaturalSpawn && !it.isMonumentSpawn && !hasSpecialSpawnRequirement(it)
            }
            val allBiomes = spawns.flatMap { it.condition.biomes }.toSet()
            // Filtrer les filtres massifs de dimensions globales (#is_overworld etc.)
            val biomes = allBiomes.filterNot { isBroadBiomeFilter(it) }.toSet()
            val antiBiomes = spawns.flatMap { it.condition.antiBiomes }.toSet()
            val weathers = spawns.mapNotNull { it.condition.weather }.toSet()
            val times = spawns.mapNotNull { it.condition.timeRange }.toSet()
            val confidence = snapshot.getSpawnConfidence(speciesName).name
            val rarityBucket = spawns.firstOrNull()?.bucket ?: "common"

            TargetSpawnCriteria(
                target = target,
                biomes = biomes,
                antiBiomes = antiBiomes,
                weatherReq = weathers.firstOrNull(),
                timeReq = times.firstOrNull(),
                confidence = confidence,
                rarity = rarityBucket
            )
        }

        // 5. Rassembler tous les profils candidats : surface ET sous-terrains (Lush Caves, Deep Dark, etc.)
        val candidateProfiles = mutableMapOf<CandidateKey, fr.k0bus.cobblemon_jei.journeymap.BiomeTagProfile>()
        for ((chunkPos, profile) in profiles) {
            candidateProfiles[CandidateKey(chunkPos, isSubterranean = false, yLevel = null)] = profile
        }

        val subterraneanProfiles = ChunkBiomeTagCache.getAllSubterraneanProfiles(dim)
        for ((chunkPos, subList) in subterraneanProfiles) {
            for (sub in subList) {
                candidateProfiles[CandidateKey(chunkPos, isSubterranean = true, yLevel = sub.yLevel)] = sub.profile
            }
        }

        // 6. Évaluer chaque profil candidat pour les cibles compatibles
        val matchingCandidates = mutableMapOf<CandidateKey, MutableList<ZoneTargetMatch>>()

        for ((candKey, profile) in candidateProfiles) {
            for (criteria in targetSpawnData) {
                // Ne match que si le Pokémon a des biomes spécifiques définis et que le chunk y répond
                val matchesBiome = criteria.biomes.isNotEmpty() &&
                        profile.matchesAny(criteria.biomes) &&
                        !profile.matchesAny(criteria.antiBiomes)

                if (matchesBiome) {
                    val list = matchingCandidates.getOrPut(candKey) { mutableListOf() }
                    list.add(
                        ZoneTargetMatch(
                            target = criteria.target,
                            spawnConfidence = criteria.confidence,
                            requiredWeather = criteria.weatherReq,
                            requiredTime = criteria.timeReq,
                            isPrimaryBiome = true
                        )
                    )
                }
            }
        }

        // Si aucun chunk exploré ne correspond ou cache vide, renvoyer un plan vide
        if (matchingCandidates.isEmpty()) {
            return ExpeditionPlan(
                name = name,
                targets = eligibleTargets,
                rankedZones = emptyList()
            )
        }

        // 7. Regroupement spatial des candidats en zones régionales compactes (surface et sous-sol séparés)
        val zones = clusterChunksIntoZones(dim, matchingCandidates, playerPos, candidateProfiles)

        // 8. Tri par score décroissant
        val sortedZones = zones.sortedByDescending { it.score }

        return ExpeditionPlan(
            id = UUID.randomUUID(),
            name = name,
            targets = eligibleTargets,
            rankedZones = sortedZones
        )
    }

    data class CandidateKey(
        val chunkPos: ChunkPos,
        val isSubterranean: Boolean = false,
        val yLevel: Int? = null
    )

    private data class TargetSpawnCriteria(
        val target: PokemonTarget,
        val biomes: Set<String>,
        val antiBiomes: Set<String>,
        val weatherReq: String?,
        val timeReq: String?,
        val confidence: String,
        val rarity: String = "common"
    )

    private fun clusterChunksIntoZones(
        dimension: ResourceKey<Level>,
        chunkMatches: Map<CandidateKey, List<ZoneTargetMatch>>,
        playerPos: BlockPos,
        profiles: Map<CandidateKey, fr.k0bus.cobblemon_jei.journeymap.BiomeTagProfile>
    ): List<ExpeditionZone> {
        val visited = mutableSetOf<CandidateKey>()
        val zones = mutableListOf<ExpeditionZone>()
        var zoneIndex = 1

        // Trier les chunks germes par nombre de cibles décroissant (meilleurs foyers en premier)
        val sortedSeedChunks = chunkMatches.entries.sortedByDescending { it.value.size }

        for ((startKey, _) in sortedSeedChunks) {
            if (visited.contains(startKey)) continue

            val seedProfile = profiles[startKey] ?: continue
            val seedBiome = seedProfile.biomeId.ifEmpty { seedProfile.biomePath }
            if (seedBiome.isEmpty()) {
                visited.add(startKey)
                continue
            }

            val isSub = startKey.isSubterranean
            val yLvl = startKey.yLevel

            val clusterKeys = mutableSetOf<CandidateKey>()
            val queue = ArrayDeque<CandidateKey>()
            queue.add(startKey)
            visited.add(startKey)

            val maxRadiusChunks = 64 // Rayon de 64 chunks (~1024 blocs depuis le foyer de la zone)
            val maxChunksPerZone = 2000

            while (queue.isNotEmpty() && clusterKeys.size < maxChunksPerZone) {
                val current = queue.removeFirst()
                clusterKeys.add(current)

                // Voisins proches (dans un rayon de 2 chunks pour relier les petites discontinuités ou rivières)
                for (dx in -2..2) {
                    for (dz in -2..2) {
                        if (dx == 0 && dz == 0) continue
                        val neighborPos = ChunkPos(current.chunkPos.x + dx, current.chunkPos.z + dz)
                        val neighborKey = CandidateKey(neighborPos, isSubterranean = isSub, yLevel = yLvl)
                        val distToSeed = kotlin.math.max(
                            kotlin.math.abs(neighborPos.x - startKey.chunkPos.x),
                            kotlin.math.abs(neighborPos.z - startKey.chunkPos.z)
                        )
                        if (distToSeed <= maxRadiusChunks && chunkMatches.containsKey(neighborKey) && !visited.contains(neighborKey)) {
                            // Rattacher les candidats compatibles appartenant à la même région écologique et même strate
                            val neighborProfile = profiles[neighborKey]
                            if (isSameBiomeRegion(seedProfile, neighborProfile)) {
                                visited.add(neighborKey)
                                queue.add(neighborKey)
                            }
                        }
                    }
                }
            }

            // Agréger les cibles distinctes de la zone
            val allMatches = clusterKeys.flatMap { chunkMatches[it] ?: emptyList() }
            val distinctTargets = allMatches.distinctBy { it.target.speciesId }
            if (distinctTargets.isEmpty()) continue

            // Calcul du centre de la zone (avec altitude souterraine précise si applicable)
            val sampleChunks = clusterKeys.map { it.chunkPos }.toSet()
            val avgChunkX = sampleChunks.map { it.x }.average().toInt()
            val avgChunkZ = sampleChunks.map { it.z }.average().toInt()
            val centerBlockX = (avgChunkX shl 4) + 8
            val centerBlockZ = (avgChunkZ shl 4) + 8
            val centerBlockY = yLvl ?: playerPos.y
            val centerPos = BlockPos(centerBlockX, centerBlockY, centerBlockZ)

            val dx = (centerBlockX - playerPos.x).toDouble()
            val dz = (centerBlockZ - playerPos.z).toDouble()
            val distance = sqrt(dx * dx + dz * dz)

            val weatherReqs = distinctTargets.mapNotNull { it.requiredWeather }.toSet()
            val timeReqs = distinctTargets.mapNotNull { it.requiredTime }.toSet()

            // Biome dominant pour le nommage géographique précis
            val dominantBiomePath = clusterKeys.mapNotNull { profiles[it]?.biomePath }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "territoire"
            val friendlyBiomeName = getFriendlyBiomeName(dominantBiomePath)

            // Calcul du score affiné :
            val targetScore = (distinctTargets.size * 15.0).coerceAtMost(60.0)
            val rarityBonus = distinctTargets.sumOf {
                when (it.spawnConfidence) {
                    "ultra-rare", "rare" -> 15.0
                    "uncommon" -> 8.0
                    else -> 4.0
                }
            }.coerceAtMost(25.0)
            val densityBonus = (clusterKeys.size.coerceAtMost(30) * 0.8)
            val distancePenalty = if (distance > 250.0) ((distance - 250.0) / 45.0).coerceAtMost(25.0) else 0.0

            val totalScore = (targetScore + rarityBonus + densityBonus - distancePenalty).coerceIn(20.0, 98.0)

            val zoneName = if (isSub && yLvl != null) "$friendlyBiomeName [Sous-sol Y=$yLvl]" else friendlyBiomeName
            val zoneId = if (isSub && yLvl != null) "zone_${zoneIndex}_${dominantBiomePath}_sub_${yLvl}" else "zone_${zoneIndex}_${dominantBiomePath}"

            zones.add(
                ExpeditionZone(
                    id = zoneId,
                    name = zoneName,
                    dimension = dimension.location(),
                    centerPos = centerPos,
                    sampleChunks = sampleChunks,
                    matchingTargets = distinctTargets,
                    score = totalScore,
                    distanceToPlayer = distance,
                    weatherRequirements = weatherReqs,
                    timeRequirements = timeReqs,
                    isSubterranean = isSub,
                    yLevel = yLvl
                )
            )

            zoneIndex++
        }

        return zones
    }
}
