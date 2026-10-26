package fr.k0bus.cobblemon_jei.domain

import fr.k0bus.cobblemon_jei.application.expedition.ExpeditionPlannerService
import fr.k0bus.cobblemon_jei.data.repository.PokemonDataRepository
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import fr.k0bus.cobblemon_jei.domain.model.*
import fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager
import fr.k0bus.cobblemon_jei.journeymap.BiomeTagProfile
import fr.k0bus.cobblemon_jei.journeymap.ChunkBiomeTagCache
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExpeditionPlannerServiceTest {

    private val overworld = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
    )

    private val bulbasaur = ResourceLocation.fromNamespaceAndPath("cobblemon", "bulbasaur")
    private val squirtle = ResourceLocation.fromNamespaceAndPath("cobblemon", "squirtle")

    @BeforeEach
    fun setup() {
        WorldSessionManager.startNewSession(overworld, customId = "test_expedition")
        ChunkBiomeTagCache.clearAll()
    }

    @AfterEach
    fun teardown() {
        WorldSessionManager.endSession()
        ChunkBiomeTagCache.clearAll()
    }

    @Test
    fun `test empty targets returns empty plan`() {
        val plan = ExpeditionPlannerService.createPlan("Test Empty", emptyList(), overworld)
        assertTrue(plan.rankedZones.isEmpty())
        assertTrue(plan.targets.isEmpty())
    }

    @Test
    fun `test zone clustering and ranking by target count`() {
        // Préparation du repository avec des données de spawn simulées
        val bulbaEntry = SpawnEntryInfo(
            speciesId = bulbasaur,
            speciesName = "bulbasaur",
            formName = "normal",
            bucket = "common",
            weight = 10.0f,
            condition = SpawnConditionInfo(
                biomes = setOf("#cobblemon:is_jungle", "minecraft:jungle"),
                dimensions = setOf(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            ),
            confidence = SpawnDataConfidence.EXACT_DATA
        )

        val squirtleEntry = SpawnEntryInfo(
            speciesId = squirtle,
            speciesName = "squirtle",
            formName = "normal",
            bucket = "uncommon",
            weight = 5.0f,
            condition = SpawnConditionInfo(
                biomes = setOf("#cobblemon:is_jungle", "#cobblemon:is_freshwater"),
                dimensions = setOf(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            ),
            confidence = SpawnDataConfidence.EXACT_DATA
        )

        val snapshot = PokemonDataRepository.getSnapshot().copy(
            allSpawns = listOf(bulbaEntry, squirtleEntry)
        )
        // Injecte dans PokemonDataRepository via reflection ou reload si nécessaire
        val field = PokemonDataRepository::class.java.getDeclaredField("snapshotRef")
        field.isAccessible = true
        (field.get(PokemonDataRepository) as java.util.concurrent.atomic.AtomicReference<Any>).set(snapshot)

        // Injection manuelle de profils de biomes dans ChunkBiomeTagCache
        val jungleProfile = BiomeTagProfile(
            biomeId = "minecraft:jungle",
            biomePath = "jungle",
            tags = setOf("is_jungle", "#cobblemon:is_jungle"),
            isTags = setOf("is_jungle")
        )

        val desertProfile = BiomeTagProfile(
            biomeId = "minecraft:desert",
            biomePath = "desert",
            tags = setOf("is_desert"),
            isTags = setOf("is_desert")
        )

        val chunk1 = ChunkPos(0, 0)
        val chunk2 = ChunkPos(1, 0)
        val chunkDesert = ChunkPos(50, 50)

        val cacheField = ChunkBiomeTagCache::class.java.getDeclaredField("chunkProfileCache")
        cacheField.isAccessible = true
        val cache = cacheField.get(ChunkBiomeTagCache) as java.util.concurrent.ConcurrentHashMap<Pair<ResourceKey<Level>, ChunkPos>, BiomeTagProfile>
        cache[Pair(overworld, chunk1)] = jungleProfile
        cache[Pair(overworld, chunk2)] = jungleProfile
        cache[Pair(overworld, chunkDesert)] = desertProfile

        // Planification pour Bulbizarre et Carapuce
        val targets = listOf(PokemonTarget(bulbasaur), PokemonTarget(squirtle))
        val plan = ExpeditionPlannerService.createPlan("Jungle Safari", targets, overworld)

        assertFalse(plan.rankedZones.isEmpty())
        val topZone = plan.rankedZones.first()

        // La zone de jungle doit contenir les 2 cibles compatibles
        assertEquals(2, topZone.matchingTargets.size)
        assertTrue(topZone.sampleChunks.contains(chunk1))
        assertTrue(topZone.sampleChunks.contains(chunk2))
        assertFalse(topZone.sampleChunks.contains(chunkDesert))
        assertTrue(topZone.score > 15.0)
    }

    @Test
    fun `test accept suggestion adds active goals`() {
        val dummyZone = fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionZone(
            id = "test_zone",
            name = "Jungle Équatoriale",
            dimension = overworld.location(),
            centerPos = net.minecraft.core.BlockPos(0, 64, 0),
            sampleChunks = setOf(net.minecraft.world.level.ChunkPos(0, 0)),
            matchingTargets = emptyList(),
            score = 25.0,
            distanceToPlayer = 50.0
        )

        val suggestion = fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionSuggestion(
            id = "sugg_starter",
            title = "Expédition Starters",
            subtitle = "2 Pokémon compatibles",
            description = "Bulbizarre et Carapuce",
            type = fr.k0bus.cobblemon_jei.domain.expedition.SuggestionType.POKEDEX_OPPORTUNITY,
            targets = listOf(PokemonTarget(bulbasaur), PokemonTarget(squirtle)),
            primaryZone = dummyZone
        )

        fr.k0bus.cobblemon_jei.application.expedition.ExpeditionSuggestionEngine.acceptSuggestion(suggestion)

        val activeGoals = fr.k0bus.cobblemon_jei.application.goals.GoalService.getActiveGoals()
        assertEquals(2, activeGoals.size)
        assertTrue(activeGoals.any { it.target.speciesId == bulbasaur })
        assertTrue(activeGoals.any { it.target.speciesId == squirtle })
    }

    @Test
    fun `test broad biome filters like is_overworld are ignored`() {
        assertTrue(ExpeditionPlannerService.isBroadBiomeFilter("#minecraft:is_overworld"))
        assertTrue(ExpeditionPlannerService.isBroadBiomeFilter("is_overworld"))
        assertTrue(ExpeditionPlannerService.isBroadBiomeFilter("#cobblemon:is_overworld"))
        assertTrue(ExpeditionPlannerService.isBroadBiomeFilter("minecraft:the_nether"))
        assertFalse(ExpeditionPlannerService.isBroadBiomeFilter("minecraft:forest"))
        assertFalse(ExpeditionPlannerService.isBroadBiomeFilter("#cobblemon:is_jungle"))
    }

    @Test
    fun `test adjacent chunks of different biomes are strictly separated into distinct zones`() {
        val pidgey = ResourceLocation.fromNamespaceAndPath("cobblemon", "pidgey")

        val bulbaEntry = SpawnEntryInfo(
            speciesId = bulbasaur,
            speciesName = "bulbasaur",
            formName = "normal",
            bucket = "common",
            weight = 10.0f,
            condition = SpawnConditionInfo(
                biomes = setOf("#cobblemon:is_jungle", "minecraft:jungle"),
                dimensions = setOf(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            ),
            confidence = SpawnDataConfidence.EXACT_DATA
        )

        val pidgeyEntry = SpawnEntryInfo(
            speciesId = pidgey,
            speciesName = "pidgey",
            formName = "normal",
            bucket = "common",
            weight = 10.0f,
            condition = SpawnConditionInfo(
                biomes = setOf("#cobblemon:is_plains", "minecraft:plains"),
                dimensions = setOf(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            ),
            confidence = SpawnDataConfidence.EXACT_DATA
        )

        val snapshot = PokemonDataRepository.getSnapshot().copy(
            allSpawns = listOf(bulbaEntry, pidgeyEntry)
        )
        val field = PokemonDataRepository::class.java.getDeclaredField("snapshotRef")
        field.isAccessible = true
        (field.get(PokemonDataRepository) as java.util.concurrent.atomic.AtomicReference<Any>).set(snapshot)

        val jungleProfile = BiomeTagProfile(
            biomeId = "minecraft:jungle",
            biomePath = "jungle",
            tags = setOf("is_jungle", "#cobblemon:is_jungle"),
            isTags = setOf("is_jungle")
        )
        val plainsProfile = BiomeTagProfile(
            biomeId = "minecraft:plains",
            biomePath = "plains",
            tags = setOf("is_plains", "#cobblemon:is_plains"),
            isTags = setOf("is_plains")
        )

        val chunkJungle = ChunkPos(0, 0)
        val chunkPlains = ChunkPos(1, 0) // Immédiatement adjacent à chunkJungle !

        val cacheField = ChunkBiomeTagCache::class.java.getDeclaredField("chunkProfileCache")
        cacheField.isAccessible = true
        val cache = cacheField.get(ChunkBiomeTagCache) as java.util.concurrent.ConcurrentHashMap<Pair<ResourceKey<Level>, ChunkPos>, BiomeTagProfile>
        cache[Pair(overworld, chunkJungle)] = jungleProfile
        cache[Pair(overworld, chunkPlains)] = plainsProfile

        val targets = listOf(PokemonTarget(bulbasaur), PokemonTarget(pidgey))
        val plan = ExpeditionPlannerService.createPlan("Multi Biome", targets, overworld)

        // Doit créer 2 zones distinctes (une pour Jungle et une pour Plaines), SANS les fusionner !
        assertEquals(2, plan.rankedZones.size)
        val zoneNames = plan.rankedZones.map { it.name }
        assertTrue(zoneNames.contains("Jungle"))
        assertTrue(zoneNames.contains("Plaines"))

        val jungleZone = plan.rankedZones.first { it.name == "Jungle" }
        assertEquals(1, jungleZone.sampleChunks.size)
        assertTrue(jungleZone.sampleChunks.contains(chunkJungle))
        assertFalse(jungleZone.sampleChunks.contains(chunkPlains))
        assertEquals(1, jungleZone.matchingTargets.size)
        assertEquals(bulbasaur, jungleZone.matchingTargets.first().target.speciesId)

        val plainsZone = plan.rankedZones.first { it.name == "Plaines" }
        assertEquals(1, plainsZone.sampleChunks.size)
        assertTrue(plainsZone.sampleChunks.contains(chunkPlains))
        assertFalse(plainsZone.sampleChunks.contains(chunkJungle))
        assertEquals(1, plainsZone.matchingTargets.size)
        assertEquals(pidgey, plainsZone.matchingTargets.first().target.speciesId)
    }

    @Test
    fun `test subterranean biomes such as deep dark and lush caves form distinct underground zones with correct Y levels`() {
        val deino = ResourceLocation.fromNamespaceAndPath("cobblemon", "deino")
        val woobat = ResourceLocation.fromNamespaceAndPath("cobblemon", "woobat")

        val deinoEntry = SpawnEntryInfo(
            speciesId = deino,
            speciesName = "deino",
            formName = "normal",
            bucket = "rare",
            weight = 5.0f,
            condition = SpawnConditionInfo(
                biomes = setOf("#cobblemon:is_deep_dark", "minecraft:deep_dark"),
                dimensions = setOf(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            ),
            confidence = SpawnDataConfidence.EXACT_DATA
        )

        val woobatEntry = SpawnEntryInfo(
            speciesId = woobat,
            speciesName = "woobat",
            formName = "normal",
            bucket = "common",
            weight = 10.0f,
            condition = SpawnConditionInfo(
                biomes = setOf("#cobblemon:is_lush", "minecraft:lush_caves"),
                dimensions = setOf(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            ),
            confidence = SpawnDataConfidence.EXACT_DATA
        )

        val snapshot = PokemonDataRepository.getSnapshot().copy(
            allSpawns = listOf(deinoEntry, woobatEntry)
        )
        val field = PokemonDataRepository::class.java.getDeclaredField("snapshotRef")
        field.isAccessible = true
        (field.get(PokemonDataRepository) as java.util.concurrent.atomic.AtomicReference<Any>).set(snapshot)

        val chunk = ChunkPos(0, 0)

        // Profil de surface : Plaines
        val plainsProfile = BiomeTagProfile(
            biomeId = "minecraft:plains",
            biomePath = "plains",
            tags = setOf("is_plains"),
            isTags = setOf("is_plains")
        )
        ChunkBiomeTagCache.putChunkProfile(overworld, chunk, plainsProfile)

        // Profil sous-terrain 1 : Lush Caves à Y = -20
        val lushProfile = BiomeTagProfile(
            biomeId = "minecraft:lush_caves",
            biomePath = "lush_caves",
            tags = setOf("is_lush", "#cobblemon:is_lush", "caves"),
            isTags = setOf("is_lush", "caves")
        )
        ChunkBiomeTagCache.putSubterraneanProfile(overworld, chunk, -20, lushProfile)

        // Profil sous-terrain 2 : Deep Dark à Y = -45
        val deepDarkProfile = BiomeTagProfile(
            biomeId = "minecraft:deep_dark",
            biomePath = "deep_dark",
            tags = setOf("is_deep_dark", "#cobblemon:is_deep_dark", "ancient_city", "is_ancient_city"),
            isTags = setOf("is_deep_dark", "ancient_city")
        )
        ChunkBiomeTagCache.putSubterraneanProfile(overworld, chunk, -45, deepDarkProfile)

        val targets = listOf(PokemonTarget(deino), PokemonTarget(woobat))
        val plan = ExpeditionPlannerService.createPlan("Underground Expeditions", targets, overworld)

        // Doit créer 2 zones distinctes pour les 2 strates sous-terraines
        assertEquals(2, plan.rankedZones.size)

        val deepDarkZone = plan.rankedZones.firstOrNull { it.yLevel == -45 }
        assertNotNull(deepDarkZone)
        assertTrue(deepDarkZone!!.isSubterranean)
        assertEquals(-45, deepDarkZone.centerPos.y)
        assertTrue(deepDarkZone.name.contains("Deep Dark") || deepDarkZone.name.contains("Ténèbres Profondes"))
        assertTrue(deepDarkZone.name.contains("[Sous-sol Y=-45]"))
        assertEquals(1, deepDarkZone.matchingTargets.size)
        assertEquals(deino, deepDarkZone.matchingTargets.first().target.speciesId)

        val lushZone = plan.rankedZones.firstOrNull { it.yLevel == -20 }
        assertNotNull(lushZone)
        assertTrue(lushZone!!.isSubterranean)
        assertEquals(-20, lushZone.centerPos.y)
        assertTrue(lushZone.name.contains("Lush Caves") || lushZone.name.contains("Grottes Végétalisées"))
        assertTrue(lushZone.name.contains("[Sous-sol Y=-20]"))
        assertEquals(1, lushZone.matchingTargets.size)
        assertEquals(woobat, lushZone.matchingTargets.first().target.speciesId)
    }

    @Test
    fun `test monument pokemon like groudon are strictly excluded from expeditions`() {
        val groudonId = ResourceLocation.fromNamespaceAndPath("cobblemon", "groudon")

        // Entrée de Groudon simulée : monument légendaire (Origin Tomb) sans spawn naturel sauvage
        val groudonEntry = SpawnEntryInfo(
            speciesId = groudonId,
            speciesName = "groudon",
            formName = "normal",
            isNaturalSpawn = false,
            isMonumentSpawn = true,
            monumentStructureId = "legendarymonuments:origin_tomb",
            condition = SpawnConditionInfo(
                biomes = emptySet(),
                dimensions = setOf(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            ),
            confidence = SpawnDataConfidence.EXACT_DATA
        )

        val bulbaEntry = SpawnEntryInfo(
            speciesId = bulbasaur,
            speciesName = "bulbasaur",
            formName = "normal",
            isNaturalSpawn = true,
            isMonumentSpawn = false,
            bucket = "common",
            weight = 10.0f,
            condition = SpawnConditionInfo(
                biomes = setOf("#cobblemon:is_jungle", "minecraft:jungle"),
                dimensions = setOf(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            ),
            confidence = SpawnDataConfidence.EXACT_DATA
        )

        val snapshot = PokemonDataRepository.getSnapshot().copy(
            allSpawns = listOf(groudonEntry, bulbaEntry)
        )
        val field = PokemonDataRepository::class.java.getDeclaredField("snapshotRef")
        field.isAccessible = true
        (field.get(PokemonDataRepository) as java.util.concurrent.atomic.AtomicReference<Any>).set(snapshot)

        val jungleProfile = BiomeTagProfile(
            biomeId = "minecraft:jungle",
            biomePath = "jungle",
            tags = setOf("is_jungle", "#cobblemon:is_jungle"),
            isTags = setOf("is_jungle")
        )
        val chunk1 = ChunkPos(0, 0)
        ChunkBiomeTagCache.putChunkProfile(overworld, chunk1, jungleProfile)

        // Cas 1 : Si seulement Groudon est demandé, aucune expédition n'est planifiée
        val planGroudonOnly = ExpeditionPlannerService.createPlan("Groudon Seul", listOf(PokemonTarget(groudonId)), overworld)
        assertTrue(planGroudonOnly.rankedZones.isEmpty())
        assertTrue(planGroudonOnly.targets.isEmpty())

        // Cas 2 : Si Groudon et Bulbizarre sont demandés, SEUL Bulbizarre est intégré
        val planMixed = ExpeditionPlannerService.createPlan("Mixte", listOf(PokemonTarget(groudonId), PokemonTarget(bulbasaur)), overworld)
        assertEquals(1, planMixed.rankedZones.size)
        assertEquals(1, planMixed.targets.size)
        assertEquals(bulbasaur, planMixed.targets.first().speciesId)

        val zone = planMixed.rankedZones.first()
        assertEquals(1, zone.matchingTargets.size)
        assertEquals(bulbasaur, zone.matchingTargets.first().target.speciesId)
        assertFalse(zone.matchingTargets.any { it.target.speciesId == groudonId })
    }

    @Test
    fun `test pokemon spawns with special conditions like saccharine trees or pc are excluded from expeditions`() {
        val munchlaxId = ResourceLocation.fromNamespaceAndPath("cobblemon", "munchlax")
        val porygonId = ResourceLocation.fromNamespaceAndPath("cobblemon", "porygon")

        // Munchlax 1 : Spawn sauvage normal en forêt
        val munchlaxWild = SpawnEntryInfo(
            speciesId = munchlaxId,
            speciesName = "munchlax",
            formName = "normal",
            isNaturalSpawn = true,
            isMonumentSpawn = false,
            bucket = "rare",
            condition = SpawnConditionInfo(
                biomes = setOf("#cobblemon:is_forest", "minecraft:forest")
            )
        )

        // Munchlax 2 : Spawn spécial partout autour des arbres saccharine (arbres à miel)
        val munchlaxSpecialTree = SpawnEntryInfo(
            speciesId = munchlaxId,
            speciesName = "munchlax",
            formName = "normal",
            isNaturalSpawn = true,
            isMonumentSpawn = false,
            bucket = "uncommon",
            condition = SpawnConditionInfo(
                biomes = setOf("#cobblemon:is_overworld"),
                neededNearbyBlocks = setOf("#cobblemon:saccharine_trees")
            )
        )

        // Porygon 1 : Ne spawn que près d'un PC (aucune apparition sauvage naturelle)
        val porygonSpecialPc = SpawnEntryInfo(
            speciesId = porygonId,
            speciesName = "porygon",
            formName = "normal",
            isNaturalSpawn = true,
            isMonumentSpawn = false,
            bucket = "uncommon",
            condition = SpawnConditionInfo(
                biomes = setOf("#cobblemon:is_overworld"),
                neededNearbyBlocks = setOf("cobblemon:pc")
            )
        )

        val snapshot = PokemonDataRepository.getSnapshot().copy(
            allSpawns = listOf(munchlaxWild, munchlaxSpecialTree, porygonSpecialPc)
        )
        val field = PokemonDataRepository::class.java.getDeclaredField("snapshotRef")
        field.isAccessible = true
        (field.get(PokemonDataRepository) as java.util.concurrent.atomic.AtomicReference<Any>).set(snapshot)

        // 1. Porygon qui n'a qu'un spawn avec PC ne doit PAS être éligible aux expéditions
        assertFalse(ExpeditionPlannerService.isSpeciesEligibleForExpedition("porygon", snapshot))

        // 2. Goinfrex qui a un vrai spawn sauvage en forêt est éligible
        assertTrue(ExpeditionPlannerService.isSpeciesEligibleForExpedition("munchlax", snapshot))

        // 3. Planification avec Munchlax et Porygon dans un monde contenant de la Jungle et de la Forêt
        val jungleProfile = BiomeTagProfile("minecraft:jungle", "jungle", setOf("is_jungle", "#cobblemon:is_jungle", "#cobblemon:is_overworld"), setOf("is_jungle"))
        val forestProfile = BiomeTagProfile("minecraft:forest", "forest", setOf("is_forest", "#cobblemon:is_forest", "#cobblemon:is_overworld"), setOf("is_forest"))

        val chunkJungle = ChunkPos(0, 0)
        val chunkForest = ChunkPos(1, 0)
        ChunkBiomeTagCache.putChunkProfile(overworld, chunkJungle, jungleProfile)
        ChunkBiomeTagCache.putChunkProfile(overworld, chunkForest, forestProfile)

        val plan = ExpeditionPlannerService.createPlan("Test Goinfrex", listOf(PokemonTarget(munchlaxId), PokemonTarget(porygonId)), overworld)

        // Porygon doit être ignoré, seul Munchlax doit être dans les cibles
        assertEquals(1, plan.targets.size)
        assertEquals(munchlaxId, plan.targets.first().speciesId)

        // Munchlax ne doit matcher QUE la forêt (spawn 1), et PAS la jungle (même si spawn 2 avait is_overworld)
        assertEquals(1, plan.rankedZones.size)
        val zone = plan.rankedZones.first()
        assertTrue(zone.sampleChunks.contains(chunkForest))
        assertFalse(zone.sampleChunks.contains(chunkJungle))
    }

    @Test
    fun `test expedition zone clusters all compatible chunks in region without artificial 18 chunk cap`() {
        val bulbaEntry = SpawnEntryInfo(
            speciesId = bulbasaur,
            speciesName = "bulbasaur",
            formName = "normal",
            bucket = "common",
            weight = 10.0f,
            condition = SpawnConditionInfo(
                biomes = setOf("#cobblemon:is_jungle", "minecraft:jungle", "minecraft:sparse_jungle"),
                dimensions = setOf(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            ),
            confidence = SpawnDataConfidence.EXACT_DATA
        )

        val snapshot = PokemonDataRepository.getSnapshot().copy(
            allSpawns = listOf(bulbaEntry)
        )
        val field = PokemonDataRepository::class.java.getDeclaredField("snapshotRef")
        field.isAccessible = true
        (field.get(PokemonDataRepository) as java.util.concurrent.atomic.AtomicReference<Any>).set(snapshot)

        val jungleProfile = BiomeTagProfile("minecraft:jungle", "jungle", setOf("is_jungle", "#cobblemon:is_jungle"), setOf("is_jungle"))
        val sparseJungleProfile = BiomeTagProfile("minecraft:sparse_jungle", "sparse_jungle", setOf("is_jungle", "#cobblemon:is_jungle"), setOf("is_jungle"))

        // Créer un vaste massif continu de 35 chunks (7x5) mélangeant Jungle et Jungle clairsemée
        val expectedChunks = mutableSetOf<ChunkPos>()
        for (x in 0 until 7) {
            for (z in 0 until 5) {
                val pos = ChunkPos(x, z)
                expectedChunks.add(pos)
                val prof = if ((x + z) % 2 == 0) jungleProfile else sparseJungleProfile
                ChunkBiomeTagCache.putChunkProfile(overworld, pos, prof)
            }
        }
        assertEquals(35, expectedChunks.size)

        val plan = ExpeditionPlannerService.createPlan("Safari Massif Jungle", listOf(PokemonTarget(bulbasaur)), overworld)

        // Toutes les 35 parcelles compatibles de la région doivent être regroupées dans une seule zone d'expédition
        assertEquals(1, plan.rankedZones.size)
        val zone = plan.rankedZones.first()
        assertEquals(35, zone.sampleChunks.size)
        assertEquals(expectedChunks, zone.sampleChunks)
    }
}

