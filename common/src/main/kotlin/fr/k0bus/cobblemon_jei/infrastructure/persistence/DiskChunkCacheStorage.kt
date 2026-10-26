package fr.k0bus.cobblemon_jei.infrastructure.persistence

import fr.k0bus.cobblemon_jei.journeymap.ChunkBiomeTagCache
import fr.k0bus.cobblemon_jei.journeymap.JourneyMapDiskScanner
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import org.slf4j.LoggerFactory
import java.io.*
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Service de persistance sur disque pour le cache des chunks scannés par JourneyMap.
 *
 * Élimine le besoin de rescanner les régions à chaque relancement du jeu en stockant
 * les correspondances (ChunkPos -> BiomeId) dans un fichier binaire compressé GZIP par monde et dimension.
 */
object DiskChunkCacheStorage {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/DiskChunkCache")
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CobblemonJEI-ChunkCacheIO").apply { isDaemon = true }
    }

    private const val MAGIC_HEADER = 0x434A4D43 // "CJMC" = Cobblemon JourneyMap Chunk
    private const val FORMAT_VERSION = 2

    fun getCacheFile(worldId: String, dimension: ResourceKey<Level>): File {
        val mc = try { Minecraft.getInstance() } catch (_: Throwable) { null }
        val baseDir = mc?.gameDirectory ?: File(".")
        val dir = File(baseDir, "cobblemon_jei/cache/$worldId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val safeDim = dimension.location().toString().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(dir, "$safeDim.dat")
    }

    /**
     * Sauvegarde asynchrone des chunks scannés vers le disque compressé en GZIP.
     */
    fun saveCacheAsync(worldId: String, dimension: ResourceKey<Level>, chunkBiomes: Map<ChunkPos, String>) {
        if (chunkBiomes.isEmpty()) return
        ioExecutor.submit {
            try {
                saveCacheSync(worldId, dimension, chunkBiomes)
            } catch (t: Throwable) {
                logger.warn("Cobblemon JEI: Échec de la sauvegarde du cache de chunks pour $dimension: ${t.message}")
            }
        }
    }

    /**
     * Sauvegarde synchrone sur disque avec dictionnaire pour compacité maximale.
     * Persiste les biomes de surface ET les biomes sous-terrains (Lush Caves, Deep Dark, etc.).
     */
    fun saveCacheSync(worldId: String, dimension: ResourceKey<Level>, chunkBiomes: Map<ChunkPos, String>) {
        if (chunkBiomes.isEmpty()) return
        val targetFile = getCacheFile(worldId, dimension)
        val tempFile = File(targetFile.parentFile, targetFile.name + ".tmp")

        // 1. Dictionnaire des biomes uniques de surface
        val uniqueBiomes = chunkBiomes.values.distinct().sorted()
        val biomeToIndex = uniqueBiomes.mapIndexed { idx, id -> id to idx }.toMap()

        // 2. Profils sous-terrains en cache
        val subMap = ChunkBiomeTagCache.getAllSubterraneanProfiles(dimension)
        val uniqueSubBiomes = subMap.values.flatten().map { it.profile.biomeId }.distinct().sorted()
        val subBiomeToIndex = uniqueSubBiomes.mapIndexed { idx, id -> id to idx }.toMap()

        DataOutputStream(BufferedOutputStream(GZIPOutputStream(FileOutputStream(tempFile)))).use { out ->
            out.writeInt(MAGIC_HEADER)
            out.writeInt(FORMAT_VERSION)

            // Écriture du dictionnaire de biomes de surface
            out.writeInt(uniqueBiomes.size)
            for (biomeId in uniqueBiomes) {
                out.writeUTF(biomeId)
            }

            // Écriture de chaque chunk de surface
            out.writeInt(chunkBiomes.size)
            for ((pos, biomeId) in chunkBiomes) {
                out.writeInt(pos.x)
                out.writeInt(pos.z)
                val idx = biomeToIndex[biomeId] ?: 0
                out.writeShort(idx)
            }

            // Écriture du dictionnaire des biomes sous-terrains
            out.writeInt(uniqueSubBiomes.size)
            for (biomeId in uniqueSubBiomes) {
                out.writeUTF(biomeId)
            }

            // Écriture de chaque entrée sous-terraine
            val totalSubEntries = subMap.values.sumOf { it.size }
            out.writeInt(totalSubEntries)
            for ((pos, profiles) in subMap) {
                for (sub in profiles) {
                    out.writeInt(pos.x)
                    out.writeInt(pos.z)
                    out.writeShort(sub.yLevel)
                    val idx = subBiomeToIndex[sub.profile.biomeId] ?: 0
                    out.writeShort(idx)
                }
            }
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)
        logger.info("Cobblemon JEI: Cache disque persisté pour ${dimension.location()} (${chunkBiomes.size} surface, ${subMap.size} sous-terrains).")
    }

    /**
     * Charge le cache de chunks depuis le disque et l'injecte directement dans [ChunkBiomeTagCache] et [JourneyMapDiskScanner].
     * Compatible avec le format v1 (surface seule) et le format v2 (surface + sous-sol).
     * @return Nombre de chunks restaurés.
     */
    fun loadIntoCache(worldId: String, dimension: ResourceKey<Level>): Int {
        val file = getCacheFile(worldId, dimension)
        if (!file.exists() || file.length() <= 8) {
            return 0
        }

        val startTime = System.currentTimeMillis()
        var loadedCount = 0

        try {
            DataInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(file)))).use { input ->
                val magic = input.readInt()
                if (magic != MAGIC_HEADER) {
                    logger.warn("Cobblemon JEI: En-tête invalide dans le cache de chunks pour ${dimension.location()}.")
                    return 0
                }
                val version = input.readInt()
                if (version != 1 && version != 2) {
                    logger.warn("Cobblemon JEI: Version de cache incompatible ($version vs $FORMAT_VERSION).")
                    return 0
                }

                // 1. Dictionnaire de surface
                val dictSize = input.readInt()
                val dictionary = ArrayList<String>(dictSize)
                for (i in 0 until dictSize) {
                    dictionary.add(input.readUTF())
                }

                val chunkCount = input.readInt()
                val mc = try { Minecraft.getInstance() } catch (_: Throwable) { null }
                val biomeRegistry = mc?.level?.registryAccess()?.registry(Registries.BIOME)?.orElse(null)

                for (i in 0 until chunkCount) {
                    val cx = input.readInt()
                    val cz = input.readInt()
                    val bIdx = input.readShort().toInt()
                    val biomeId = if (bIdx in 0 until dictionary.size) dictionary[bIdx] else "minecraft:plains"
                    val pos = ChunkPos(cx, cz)

                    // Injecter dans ChunkBiomeTagCache
                    val profile = ChunkBiomeTagCache.getOrCreateProfileById(biomeId, biomeRegistry)
                    ChunkBiomeTagCache.putChunkProfile(dimension, pos, profile)

                    // Injecter dans JourneyMapDiskScanner si le Holder de biome est accessible
                    if (biomeRegistry != null) {
                        try {
                            val resLoc = ResourceLocation.parse(biomeId)
                            val holder = biomeRegistry.getHolder(ResourceKey.create(Registries.BIOME, resLoc)).orElse(null)
                            if (holder != null) {
                                JourneyMapDiskScanner.putCachedChunk(dimension, pos, holder)
                            }
                        } catch (_: Throwable) {}
                    }
                    loadedCount++
                }

                // 2. Restauration des biomes sous-terrains si format v2+
                if (version >= 2) {
                    val subDictSize = input.readInt()
                    val subDictionary = ArrayList<String>(subDictSize)
                    for (i in 0 until subDictSize) {
                        subDictionary.add(input.readUTF())
                    }

                    val subCount = input.readInt()
                    for (i in 0 until subCount) {
                        val cx = input.readInt()
                        val cz = input.readInt()
                        val yLevel = input.readShort().toInt()
                        val bIdx = input.readShort().toInt()
                        val biomeId = if (bIdx in 0 until subDictionary.size) subDictionary[bIdx] else "minecraft:lush_caves"
                        val pos = ChunkPos(cx, cz)

                        val profile = ChunkBiomeTagCache.getOrCreateProfileById(biomeId, biomeRegistry)
                        ChunkBiomeTagCache.putSubterraneanProfile(dimension, pos, yLevel, profile)
                    }
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            logger.info("Cobblemon JEI: Restauration instantanée de $loadedCount chunks depuis le cache disque pour ${dimension.location()} en ${elapsed}ms.")
            return loadedCount
        } catch (t: Throwable) {
            logger.warn("Cobblemon JEI: Erreur lors de la lecture du cache disque pour ${dimension.location()}: ${t.message}")
            return 0
        }
    }
}
