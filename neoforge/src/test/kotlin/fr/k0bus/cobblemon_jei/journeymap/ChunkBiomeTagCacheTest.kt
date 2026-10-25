package fr.k0bus.cobblemon_jei.journeymap

import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.Optional

class ChunkBiomeTagCacheTest {

    @BeforeEach
    fun setUp() {
        ChunkBiomeTagCache.clearAll()
    }

    private fun createMockHolder(id: String, tags: Set<String> = emptySet()): Holder<Biome> {
        val resKey = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(id))
        return Proxy.newProxyInstance(
            Holder::class.java.classLoader,
            arrayOf(Holder::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "unwrapKey" -> Optional.of(resKey)
                "tags" -> tags.stream().map { TagKey.create(Registries.BIOME, ResourceLocation.parse(it.removePrefix("#"))) }
                "hashCode" -> resKey.hashCode()
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "MockHolder($id)"
                else -> null
            }
        } as Holder<Biome>
    }

    @Test
    fun testProfileCreationAndIsTagsExtraction() {
        val plains = createMockHolder("minecraft:plains", setOf("#minecraft:is_plains", "#cobblemon:is_overworld"))
        val profile = ChunkBiomeTagCache.getOrCreateProfile(plains)

        assertEquals("minecraft:plains", profile.biomeId)
        assertEquals("plains", profile.biomePath)
        assertTrue(profile.matchesTagOrId("is_plains"))
        assertTrue(profile.matchesTagOrId("#minecraft:is_plains"))
        assertTrue(profile.matchesTagOrId("minecraft:is_overworld"))
        assertFalse(profile.matchesTagOrId("is_ocean"))
    }

    @Test
    fun testSyntheticNetherTags() {
        val basaltDeltas = createMockHolder("minecraft:basalt_deltas")
        val profile = ChunkBiomeTagCache.getOrCreateProfile(basaltDeltas)

        assertTrue(profile.matchesTagOrId("is_nether"))
        assertTrue(profile.matchesTagOrId("cobblemon:nether/is_basalt"))
        assertTrue(profile.matchesTagOrId("is_volcanic"))
    }

    @Test
    fun testChunkProfileCaching() {
        val forest = createMockHolder("minecraft:forest", setOf("#minecraft:is_forest"))
        val chunkPos = ChunkPos(10, 20)

        assertNull(ChunkBiomeTagCache.getChunkProfile(Level.OVERWORLD, chunkPos))
        val stored = ChunkBiomeTagCache.putChunkProfile(Level.OVERWORLD, chunkPos, forest)
        assertNotNull(stored)

        val retrieved = ChunkBiomeTagCache.getChunkProfile(Level.OVERWORLD, chunkPos)
        assertSame(stored, retrieved)
        assertTrue(ChunkBiomeTagCache.hasCachedChunksForDimension(Level.OVERWORLD))
        assertEquals(1, ChunkBiomeTagCache.getChunkProfilesForDimension(Level.OVERWORLD).size)
    }

    @Test
    fun testCobblemonTagCrossNamespaceMatching() {
        // Un biome Minecraft avec un tag vanilla minecraft:is_forest DOIT matcher #cobblemon:is_forest
        val birchForest = createMockHolder("minecraft:birch_forest", setOf("#minecraft:is_forest"))
        val profile = ChunkBiomeTagCache.getOrCreateProfile(birchForest)

        assertTrue(profile.matchesTagOrId("#cobblemon:is_forest"))
        assertTrue(profile.matchesTagOrId("cobblemon:is_forest"))
        assertTrue(profile.matchesTagOrId("#minecraft:is_forest"))
        assertTrue(profile.matchesTagOrId("is_forest"))
        assertTrue(profile.matchesAny(setOf("#cobblemon:is_forest", "#cobblemon:is_jungle")))
    }

    @Test
    fun testSyntheticEnrichmentForVanillaBiomesWithoutTags() {
        // plains n'a aucun tag is_* vanilla, mais doit matcher #cobblemon:is_plains grâce à l'enrichissement
        val plainBiome = createMockHolder("minecraft:plains")
        val profile = ChunkBiomeTagCache.getOrCreateProfile(plainBiome)

        assertTrue(profile.matchesTagOrId("#cobblemon:is_plains"))
        assertTrue(profile.matchesTagOrId("cobblemon:is_plains"))
        assertTrue(profile.matchesTagOrId("is_plains"))
        assertTrue(profile.matchesTagOrId("is_overworld"))

        // jungle doit matcher #cobblemon:is_jungle
        val jungleBiome = createMockHolder("minecraft:jungle")
        val jungleProfile = ChunkBiomeTagCache.getOrCreateProfile(jungleBiome)
        assertTrue(jungleProfile.matchesTagOrId("#cobblemon:is_jungle"))
        assertTrue(jungleProfile.matchesTagOrId("is_lush"))
    }
}
