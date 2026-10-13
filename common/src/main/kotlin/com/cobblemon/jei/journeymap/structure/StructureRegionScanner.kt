package com.cobblemon.jei.journeymap.structure

import com.cobblemon.jei.journeymap.JourneyMapDiskScanner
import com.cobblemon.jei.journeymap.structure.DynamicStructureRegistryService.StructureType
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * Scanner asynchrone ultra-performant des structures de régions (.mca) et chunks mémoire.
 *
 * Scanne directement les données NBT d'en-têtes de structures (`structures.starts`)
 * enregistrées dans les fichiers de région Anvil (.mca) sans charger de chunks en mémoire Minecraft.
 *
 * Exécution 100% asynchrone sur un thread pool dédié : ZÉRO saccade sur l'interface ou le rendu JourneyMap.
 */
object StructureRegionScanner {

    private val logger = LoggerFactory.getLogger("CobblemonJM/StructureRegionScanner")

    // Pool dédié aux tâches d'E/S disque et décodage NBT
    private val diskExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "CobblemonJM-StructureDiskWorker").apply { isDaemon = true }
    }

    // Cache des fichiers de régions déjà scannés (chemin absolu -> timestamp de modification)
    private val scannedFilesCache = ConcurrentHashMap<String, Long>()

    // Cache des positions brutes de structures découvertes (Dim + ID -> Set<BlockPos>)
    val discoveredStructuresCache = ConcurrentHashMap<Pair<ResourceKey<Level>, ResourceLocation>, MutableSet<BlockPos>>()

    /**
     * Déclenche un scan asynchrone complet des régions explorées pour la dimension actuelle.
     */
    fun scanExploredStructuresAsync(
        dimension: ResourceKey<Level>,
        onStructureDiscovered: (ResourceLocation, BlockPos, IntArray?) -> Unit
    ): CompletableFuture<Int> {
        val mc = Minecraft.getInstance()
        return CompletableFuture.supplyAsync({
            var count = 0
            try {
                val regionFiles = findRegionFiles(dimension, mc)
                logger.info("Cobblemon JM: Analyse asynchrone des structures dans ${regionFiles.size} fichiers de région...")

                for (file in regionFiles) {
                    val lastMod = file.lastModified()
                    val prevMod = scannedFilesCache[file.absolutePath]
                    if (prevMod != null && prevMod == lastMod) {
                        continue // Déjà analysé et non modifié
                    }

                    val foundInFile = scanSingleRegionFile(file, dimension) { structId, pos, bb ->
                        onStructureDiscovered(structId, pos, bb)
                        count++
                    }
                    scannedFilesCache[file.absolutePath] = lastMod
                }
            } catch (t: Throwable) {
                logger.error("Cobblemon JM: Erreur lors du scan asynchrone des structures : {}", t.message)
            }
            count
        }, diskExecutor)
    }

    /**
     * Lit un fichier de région Anvil (.mca) et extrait les structures débutant dans chaque chunk.
     */
    private fun scanSingleRegionFile(
        file: File,
        dimension: ResourceKey<Level>,
        consumer: (ResourceLocation, BlockPos, IntArray?) -> Unit
    ): Int {
        var found = 0
        if (!file.exists() || file.length() < 8192) return 0

        try {
            RandomAccessFile(file, "r").use { raf ->
                // Lecture de la table des 1024 offsets de chunks (4096 octets)
                val headerBytes = ByteArray(4096)
                raf.readFully(headerBytes)

                for (chunkIndex in 0 until 1024) {
                    val offsetIdx = chunkIndex * 4
                    val b0 = headerBytes[offsetIdx].toInt() and 0xFF
                    val b1 = headerBytes[offsetIdx + 1].toInt() and 0xFF
                    val b2 = headerBytes[offsetIdx + 2].toInt() and 0xFF
                    val sectorCount = headerBytes[offsetIdx + 3].toInt() and 0xFF

                    val sectorOffset = (b0 shl 16) or (b1 shl 8) or b2
                    if (sectorOffset == 0 || sectorCount == 0) continue

                    raf.seek(sectorOffset * 4096L)
                    val length = raf.readInt()
                    if (length <= 1 || length > 1024 * 1024 * 8) continue // Protection taille anormale

                    val compressionType = raf.readByte().toInt()
                    val chunkData = ByteArray(length - 1)
                    raf.readFully(chunkData)

                    val inStream = when (compressionType) {
                        1 -> GZIPInputStream(ByteArrayInputStream(chunkData))
                        2 -> InflaterInputStream(ByteArrayInputStream(chunkData))
                        else -> ByteArrayInputStream(chunkData)
                    }

                    val tag = try {
                        NbtIo.read(DataInputStream(inStream), NbtAccounter.unlimitedHeap())
                    } catch (_: Throwable) {
                        null
                    } ?: continue

                    // Extraction du conteneur de structures
                    if (!tag.contains("structures", 10)) continue
                    val structuresTag = tag.getCompound("structures")
                    if (!structuresTag.contains("starts", 10)) continue
                    val startsTag = structuresTag.getCompound("starts")

                    for (structKey in startsTag.allKeys) {
                        val startTag = startsTag.getCompound(structKey)
                        val idStr = startTag.getString("id")
                        if (idStr == "INVALID") continue

                        val structId = try {
                            ResourceLocation.parse(structKey)
                        } catch (_: Throwable) {
                            continue
                        }

                        // Filtre dynamique : seuls les fossiles ou légendaires sont retenus
                        val meta = DynamicStructureRegistryService.getMetadata(structId)
                        if (meta.type == StructureType.OTHER) continue

                        val bb = if (startTag.contains("BB", 11)) startTag.getIntArray("BB") else null
                        val chunkX = startTag.getInt("ChunkX")
                        val chunkZ = startTag.getInt("ChunkZ")

                        val pos = if (bb != null && bb.size >= 6) {
                            BlockPos((bb[0] + bb[3]) / 2, (bb[1] + bb[4]) / 2, (bb[2] + bb[5]) / 2)
                        } else {
                            BlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8)
                        }

                        val cacheKey = Pair(dimension, structId)
                        val set = discoveredStructuresCache.computeIfAbsent(cacheKey) { ConcurrentHashMap.newKeySet() }
                        if (set.add(pos)) {
                            consumer(structId, pos, bb)
                            found++
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return found
    }

    /**
     * Recherche les fichiers .mca de la dimension actuelle sur le disque.
     */
    private fun findRegionFiles(dimension: ResourceKey<Level>, mc: Minecraft): List<File> {
        val files = mutableListOf<File>()

        // 1. Sauvegardes solo
        val server = mc.singleplayerServer
        if (server != null) {
            try {
                val worldPath = server.getWorldPath(LevelResource.ROOT)
                val dimNamespace = dimension.location().namespace
                val dimPath = dimension.location().path

                val candidates = when (dimension) {
                    Level.OVERWORLD -> listOf(worldPath.resolve("region"))
                    Level.NETHER -> listOf(
                        worldPath.resolve("DIM-1/region"),
                        worldPath.resolve("dimensions/minecraft/the_nether/region")
                    )
                    Level.END -> listOf(
                        worldPath.resolve("DIM1/region"),
                        worldPath.resolve("dimensions/minecraft/the_end/region")
                    )
                    else -> listOf(
                        worldPath.resolve("dimensions/$dimNamespace/$dimPath/region")
                    )
                }

                for (cand in candidates) {
                    val dir = cand.toFile()
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles { f -> f.name.endsWith(".mca") }?.let { files.addAll(it) }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 2. Dossiers JourneyMap
        try {
            val dimDir = JourneyMapDiskScanner.findDimensionDirectory(dimension, mc, null)
            if (dimDir != null && dimDir.exists()) {
                dimDir.walkTopDown().maxDepth(3).forEach { f ->
                    if (f.name.endsWith(".mca") || f.name.endsWith(".jmr")) {
                        files.add(f)
                    }
                }
            }
        } catch (_: Throwable) {}

        return files.distinctBy { it.absolutePath }
    }

    /**
     * Hook appelé lors du chargement d'un chunk en jeu pour affiner dynamiquement
     * les piédestaux et blocs de fouilles suspects.
     */
    fun onChunkLoaded(chunk: LevelChunk) {
        val level = chunk.level ?: return
        val dimension = level.dimension()

        // 1. Recherche de piédestal
        val pedestalPos = LegendaryStructureHandler.findPedestalBlockInChunk(chunk)
        if (pedestalPos != null) {
            StructureOverlayManager.refinePedestalPosition(dimension, chunk.pos, pedestalPos)
        }

        // 2. Recherche de blocs suspects archéologiques
        val suspiciousPos = FossilStructureHandler.scanSuspiciousBlocksInChunk(chunk)
        if (suspiciousPos != null) {
            StructureOverlayManager.refineFossilSitePosition(dimension, chunk.pos, suspiciousPos)
        }
    }

    /**
     * Vide les caches lors d'une déconnexion ou changement de monde.
     */
    fun clearCache() {
        scannedFilesCache.clear()
        discoveredStructuresCache.clear()
    }
}
