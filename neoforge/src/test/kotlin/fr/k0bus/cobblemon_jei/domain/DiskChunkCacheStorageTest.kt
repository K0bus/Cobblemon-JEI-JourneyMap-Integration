package fr.k0bus.cobblemon_jei.domain

import fr.k0bus.cobblemon_jei.infrastructure.persistence.DiskChunkCacheStorage
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
import java.io.File

class DiskChunkCacheStorageTest {

    private val overworld = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
    )

    private val testWorldId = "test_world_cache_persist"

    @BeforeEach
    fun setup() {
        ChunkBiomeTagCache.clearAll()
        cleanupFile()
    }

    @AfterEach
    fun teardown() {
        ChunkBiomeTagCache.clearAll()
        cleanupFile()
    }

    private fun cleanupFile() {
        val file = DiskChunkCacheStorage.getCacheFile(testWorldId, overworld)
        if (file.exists()) {
            file.delete()
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        if (tmp.exists()) {
            tmp.delete()
        }
    }

    @Test
    fun `test save and load chunk cache persists accurately`() {
        val chunkMap = mapOf(
            ChunkPos(10, 20) to "minecraft:plains",
            ChunkPos(11, 20) to "minecraft:plains",
            ChunkPos(50, -30) to "minecraft:desert",
            ChunkPos(51, -30) to "minecraft:desert",
            ChunkPos(-100, 200) to "minecraft:jungle"
        )

        // Sauvegarde synchrone
        DiskChunkCacheStorage.saveCacheSync(testWorldId, overworld, chunkMap)

        val file = DiskChunkCacheStorage.getCacheFile(testWorldId, overworld)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)

        // Restauration dans le cache
        val count = DiskChunkCacheStorage.loadIntoCache(testWorldId, overworld)
        assertEquals(5, count)

        // Vérification de la présence dans ChunkBiomeTagCache
        val profiles = ChunkBiomeTagCache.getChunkProfilesForDimension(overworld)
        assertEquals(5, profiles.size)

        val plainsChunk = profiles[ChunkPos(10, 20)]
        assertNotNull(plainsChunk)
        assertEquals("minecraft:plains", plainsChunk?.biomeId)
        assertTrue(plainsChunk?.matchesAny(setOf("is_plains")) == true)

        val jungleChunk = profiles[ChunkPos(-100, 200)]
        assertNotNull(jungleChunk)
        assertEquals("minecraft:jungle", jungleChunk?.biomeId)
        assertTrue(jungleChunk?.matchesAny(setOf("is_jungle")) == true)
    }

    @Test
    fun `test load non existing cache returns zero`() {
        val count = DiskChunkCacheStorage.loadIntoCache("non_existent_world", overworld)
        assertEquals(0, count)
    }
}
