package fr.k0bus.cobblemon_jei.journeymap

import fr.k0bus.cobblemon_jei.domain.session.SessionLifecycleListener
import fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager
import journeymap.api.v2.client.IClientAPI
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.QuartPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.LoggerFactory
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.Method

/**
 * Lecteur asynchrone des données de régions et du cache disque JourneyMap.
 * Scanne les fichiers de régions (Anvil .mca, caches JourneyMap .jmc et tuiles .png)
 * pour extraire les biomes de surface sans forcer le chargement de chunks Minecraft en RAM.
 */
object JourneyMapDiskScanner {

    private val logger = LoggerFactory.getLogger("CobblemonJM/DiskScanner")

    init {
        WorldSessionManager.addListener(object : SessionLifecycleListener {
            override fun onSessionStarted(sessionId: String, dimension: ResourceKey<Level>?) {
                val dim = dimension ?: Level.OVERWORLD
                val loaded = fr.k0bus.cobblemon_jei.infrastructure.persistence.DiskChunkCacheStorage.loadIntoCache(sessionId, dim)
                if (loaded > 0) {
                    logger.info("Cobblemon JM: Pré-chargement automatique de $loaded chunks depuis le cache disque pour $sessionId.")
                }
            }

            override fun onSessionEnded(sessionId: String) {
                clearCache()
                logger.info("Cobblemon JM: Session $sessionId terminée, cache mémoire réinitialisé.")
            }
        })
    }

    // Cache persistant en mémoire des biomes explorés (dimension + ChunkPos -> Holder<Biome>)
    val globalBiomeCache = java.util.concurrent.ConcurrentHashMap<Pair<ResourceKey<Level>, ChunkPos>, Holder<Biome>>()

    // Réflexion JourneyMap pour accès à JMChunkCache à l'exécution sans dépendance directe
    private var jmChunkCacheInstance: Any? = null
    private var jmChunkCacheGetMethod: Method? = null
    private var jmSnapshotGetBiomePosMethod: Method? = null
    private var jmSnapshotGetBiomeMethod: Method? = null
    private var jmSnapshotGetHeightMethod: Method? = null
    private var jmSnapshotGetHeightCoordsMethod: Method? = null
    private var jmSnapshotGetSectionsMethod: Method? = null
    private var jmReflectionInitialized = false

    private fun initJourneyMapReflection() {
        if (jmReflectionInitialized) return
        jmReflectionInitialized = true
        try {
            val cacheClass = Class.forName("journeymap.common.chunk.JMChunkCache")
            val instanceField = cacheClass.getField("INSTANCE")
            val instance = instanceField.get(null)
            val getMethod = cacheClass.getMethod("get", ChunkPos::class.java, Level::class.java)

            val snapshotClass = Class.forName("journeymap.common.chunk.JMChunkSnapshot")
            try {
                jmSnapshotGetBiomePosMethod = snapshotClass.getMethod("getBiomeHolder", BlockPos::class.java)
            } catch (_: Throwable) {}
            try {
                jmSnapshotGetBiomeMethod = snapshotClass.getMethod("getBiomeHolder", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            } catch (_: Throwable) {}
            try {
                jmSnapshotGetHeightCoordsMethod = snapshotClass.getMethod("getHeight", Heightmap.Types::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            } catch (_: Throwable) {}
            try {
                jmSnapshotGetHeightMethod = snapshotClass.getMethod("getHeight", BlockPos::class.java)
            } catch (_: Throwable) {}
            try {
                jmSnapshotGetSectionsMethod = snapshotClass.getMethod("getSections")
            } catch (_: Throwable) {}

            jmChunkCacheInstance = instance
            jmChunkCacheGetMethod = getMethod
            logger.info("Cobblemon JM: Pont de réflexion JourneyMap ChunkCache initialisé avec succès.")
        } catch (_: Throwable) {
            logger.debug("Cobblemon JM: JMChunkCache non disponible directement, utilisation des générateurs natifs.")
        }
    }

    /**
     * Vide le cache mémoire global des biomes.
     */
    fun clearCache() {
        globalBiomeCache.clear()
        ChunkBiomeTagCache.clearAll()
    }

    /**
     * Retourne l'ensemble des chunks déjà présents dans le cache mémoire pour la dimension cible.
     */
    fun getCachedChunksForDimension(currentDim: ResourceKey<Level>): Map<ChunkPos, Holder<Biome>> {
        val result = mutableMapOf<ChunkPos, Holder<Biome>>()
        for ((key, biome) in globalBiomeCache) {
            if (key.first == currentDim) {
                result[key.second] = biome
            }
        }
        return result
    }

    fun putCachedChunk(dimension: ResourceKey<Level>, chunkPos: ChunkPos, holder: Holder<Biome>) {
        globalBiomeCache[Pair(dimension, chunkPos)] = holder
    }

    /**
     * Scanne l'ensemble des régions explorées et associe chaque chunk éligible à son biome.
     * Priorise les régions autour du joueur et parallélise les régions distantes.
     *
     * @param currentDim Dimension cible.
     * @param mc Instance Minecraft cliente.
     * @param playerY Altitude Y du joueur pour l'échantillonnage de surface.
     * @param isCancelled Callback d'annulation pour arrêter immédiatement le scan.
     * @param clientApi Référence à l'API client JourneyMap si disponible.
     * @param onProgress Callback de progression optionnel pour streaming visuel.
     * @return Map associant chaque [ChunkPos] exploré à son [Holder] de biome.
     */
    fun scanExploredChunks(
        currentDim: ResourceKey<Level>,
        mc: Minecraft,
        playerY: Int,
        isCancelled: () -> Boolean,
        clientApi: IClientAPI? = null,
        onProgress: ((completedRegions: Int, totalRegions: Int, chunkMap: Map<ChunkPos, Holder<Biome>>) -> Unit)? = null,
        forceRefresh: Boolean = false
    ): Map<ChunkPos, Holder<Biome>> {
        var cachedForDim = getCachedChunksForDimension(currentDim)
        if (cachedForDim.isEmpty()) {
            val worldId = fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager.activeSessionId
                ?: fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager.resolveCurrentWorldId()
            val restored = fr.k0bus.cobblemon_jei.infrastructure.persistence.DiskChunkCacheStorage.loadIntoCache(worldId, currentDim)
            if (restored > 0) {
                cachedForDim = getCachedChunksForDimension(currentDim)
            }
        }
        if (!forceRefresh && cachedForDim.size > 100) {
            logger.info("Cobblemon JM: Utilisation directe du cache de ${cachedForDim.size} chunks pour ${currentDim.location()}.")
            for ((pos, holder) in cachedForDim) {
                if (ChunkBiomeTagCache.getChunkProfile(currentDim, pos) == null) {
                    ChunkBiomeTagCache.putChunkProfile(currentDim, pos, holder)
                }
            }
            onProgress?.invoke(1, 1, cachedForDim)
            return cachedForDim
        }

        initJourneyMapReflection()

        val exploredRegions = findExploredRegions(currentDim, mc, clientApi)
        logger.info("Cobblemon JM: Début du scan de ${exploredRegions.size} régions explorées pour la dimension ${currentDim.location()}.")

        // 1. Initialisation du générateur mathématique si serveur solo disponible
        val server = mc.singleplayerServer
        val serverLevel = server?.getLevel(currentDim)
        val biomeSource = serverLevel?.chunkSource?.generator?.biomeSource
        val sampler = serverLevel?.chunkSource?.randomState()?.sampler()

        val clientLevel = mc.level
        val dimDir = findDimensionDirectory(currentDim, mc, clientApi)
        val jmcDir = dimDir?.resolve("chunk_cache")

        val player = mc.player
        val pRegionX = player?.let { (it.blockPosition().x shr 4) shr 5 } ?: 0
        val pRegionZ = player?.let { (it.blockPosition().z shr 4) shr 5 } ?: 0

        // Tri des régions : les plus proches du joueur d'abord
        val sortedRegions = exploredRegions.sortedBy { (rx, rz) ->
            val dx = rx - pRegionX
            val dz = rz - pRegionZ
            dx * dx + dz * dz
        }

        val totalRegions = sortedRegions.size
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val lastProgressNotify = java.util.concurrent.atomic.AtomicLong(0)

        val sessionToken = WorldSessionManager.createCancellationToken(currentDim)
        val isTaskCancelled: () -> Boolean = { isCancelled() || sessionToken.isCancelled }

        val result = java.util.concurrent.ConcurrentHashMap<ChunkPos, Holder<Biome>>()
        result.putAll(getCachedChunksForDimension(currentDim))

        fun notifyProgress(force: Boolean = false) {
            if (onProgress == null || isTaskCancelled()) return
            val now = System.currentTimeMillis()
            if (force || now - lastProgressNotify.get() > 400 || completedCount.get() >= totalRegions) {
                lastProgressNotify.set(now)
                onProgress(completedCount.get(), totalRegions, HashMap(result))
            }
        }

        val (nearbyRegions, distantRegions) = sortedRegions.partition { (rx, rz) ->
            kotlin.math.abs(rx - pRegionX) <= 1 && kotlin.math.abs(rz - pRegionZ) <= 1
        }

        fun scanRegion(region: Pair<Int, Int>) {
            if (isTaskCancelled()) return
            val (rx, rz) = region
            val startChunkX = rx * 32
            val startChunkZ = rz * 32
            val recordedChunks = getRecordedChunksInRegion(rx, rz, dimDir, jmcDir)

            for (localX in 0 until 32) {
                for (localZ in 0 until 32) {
                    if (isTaskCancelled()) return
                    val cx = startChunkX + localX
                    val cz = startChunkZ + localZ
                    val chunkPos = ChunkPos(cx, cz)

                    if (recordedChunks != null && !recordedChunks.contains(chunkPos)) {
                        continue
                    }

                    val cacheKey = Pair(currentDim, chunkPos)
                    val cachedBiome = globalBiomeCache[cacheKey]
                    if (cachedBiome != null) {
                        result[chunkPos] = cachedBiome
                        if (ChunkBiomeTagCache.getChunkProfile(currentDim, chunkPos) == null) {
                            ChunkBiomeTagCache.putChunkProfile(currentDim, chunkPos, cachedBiome)
                        }
                        continue
                    }

                    val centerX = cx * 16 + 8
                    val centerZ = cz * 16 + 8

                    val biomeHolder = extractBiome(
                        chunkPos = chunkPos,
                        centerX = centerX,
                        centerZ = centerZ,
                        playerY = playerY,
                        biomeSource = biomeSource,
                        sampler = sampler,
                        clientLevel = clientLevel
                    )

                    if (biomeHolder != null) {
                        result[chunkPos] = biomeHolder
                        globalBiomeCache[cacheKey] = biomeHolder
                        ChunkBiomeTagCache.putChunkProfile(currentDim, chunkPos, biomeHolder)
                    }

                    if (currentDim == Level.OVERWORLD) {
                        extractSubterraneanBiomes(
                            chunkPos = chunkPos,
                            centerX = centerX,
                            centerZ = centerZ,
                            biomeSource = biomeSource,
                            sampler = sampler,
                            clientLevel = clientLevel,
                            currentDim = currentDim
                        )
                    }
                }
            }
            completedCount.incrementAndGet()
            notifyProgress()
        }

        // Étape 1 : Régions proches exécutées en priorité
        for (region in nearbyRegions) {
            if (isTaskCancelled()) return emptyMap()
            scanRegion(region)
        }

        notifyProgress(force = true)

        // Étape 2 : Régions lointaines parallélisées
        if (distantRegions.isNotEmpty() && !isTaskCancelled()) {
            val parallelism = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 4)
            val customPool = java.util.concurrent.ForkJoinPool(parallelism)
            try {
                customPool.submit(Runnable {
                    distantRegions.parallelStream().forEach { region ->
                        if (!isTaskCancelled()) {
                            scanRegion(region)
                        }
                    }
                }).get()
            } catch (_: Exception) {
            } finally {
                customPool.shutdown()
            }
        }

        notifyProgress(force = true)

        logger.info("Cobblemon JM: Scan terminé : ${result.size} chunks cartographiés extraits.")

        // Persistance asynchrone sur disque
        try {
            val worldId = fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager.activeSessionId
                ?: fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager.resolveCurrentWorldId()
            val chunkBiomeIdMap = result.mapValues {
                it.value.unwrapKey().map { k -> k.location().toString() }.orElse("")
            }.filterValues { it.isNotEmpty() }
            fr.k0bus.cobblemon_jei.infrastructure.persistence.DiskChunkCacheStorage.saveCacheAsync(worldId, currentDim, chunkBiomeIdMap)
        } catch (t: Throwable) {
            logger.warn("Cobblemon JM: Erreur lors de la sauvegarde du cache de chunks: ${t.message}")
        }
        return result
    }

    /**
     * Échantillonne le biome de surface selon le moteur vision de loin :
     * 1. Générateur de bruit solo (moteur vision de loin, ultra-rapide, 0 I/O, couvre 100% de la carte explorée)
     * 2. Cache JourneyMap disque / mémoire via réflexion (JMChunkCache)
     * 3. Fallback ClientLevel
     */
    private fun extractBiome(
        chunkPos: ChunkPos,
        centerX: Int,
        centerZ: Int,
        playerY: Int,
        biomeSource: net.minecraft.world.level.biome.BiomeSource?,
        sampler: net.minecraft.world.level.biome.Climate.Sampler?,
        clientLevel: net.minecraft.client.multiplayer.ClientLevel?
    ): Holder<Biome>? {
        val currentDim = clientLevel?.dimension()
        val sampleY = when (currentDim) {
            Level.NETHER -> playerY.coerceIn(32, 90)
            Level.END -> playerY.coerceIn(50, 80)
            Level.OVERWORLD -> playerY.coerceIn(64, 96)
            else -> playerY.coerceIn(60, 96) // Dimensions moddées (Aether, etc.)
        }

        // Stratégie 1 : Générateur de bruit solo (moteur vision de loin ultra-rapide, 0 I/O)
        if (biomeSource != null && sampler != null) {
            val quartX = QuartPos.fromBlock(centerX)
            val quartY = QuartPos.fromBlock(sampleY)
            val quartZ = QuartPos.fromBlock(centerZ)
            return biomeSource.getNoiseBiome(quartX, quartY, quartZ, sampler)
        }

        // Stratégie 2 : Cache JourneyMap (JMChunkCache)
        val jmCache = jmChunkCacheInstance
        val getMethod = jmChunkCacheGetMethod
        val getBiomeCoordsMethod = jmSnapshotGetBiomeMethod
        val getBiomePosMethod = jmSnapshotGetBiomePosMethod
        val getHeightCoordsMethod = jmSnapshotGetHeightCoordsMethod
        val getHeightPosMethod = jmSnapshotGetHeightMethod
        val getSectionsMethod = jmSnapshotGetSectionsMethod

        if (jmCache != null && getMethod != null && clientLevel != null) {
            try {
                val snapshot = getMethod.invoke(jmCache, chunkPos, clientLevel)
                if (snapshot != null) {
                    val localX = centerX and 15
                    val localZ = centerZ and 15

                    // 1. Détection de la véritable altitude de surface du relief (0 allocation)
                    val surfaceY = try {
                        if (getHeightCoordsMethod != null) {
                            val h = getHeightCoordsMethod.invoke(snapshot, Heightmap.Types.WORLD_SURFACE, localX, localZ) as? Int
                            if (h != null && h > -64 && h < 320) h else null
                        } else if (getHeightPosMethod != null) {
                            val h = getHeightPosMethod.invoke(snapshot, BlockPos(centerX, 0, centerZ)) as? Int
                            if (h != null && h > -64 && h < 320) h else null
                        } else null
                    } catch (_: Throwable) { null }

                    val targetY = surfaceY ?: sampleY

                    // 2. Échantillonnage direct ultra-rapide au niveau du sol (0 allocation)
                    val directHolder = try {
                        if (getBiomeCoordsMethod != null) {
                            @Suppress("UNCHECKED_CAST")
                            getBiomeCoordsMethod.invoke(snapshot, localX, targetY, localZ) as? Holder<Biome>
                        } else if (getBiomePosMethod != null) {
                            @Suppress("UNCHECKED_CAST")
                            getBiomePosMethod.invoke(snapshot, BlockPos(centerX, targetY, centerZ)) as? Holder<Biome>
                        } else null
                    } catch (_: Throwable) { null }

                    if (directHolder != null) {
                        return directHolder
                    }

                    // 3. Fallback rapide : test de la section immédiatement inférieure (bloc sous feuillage)
                    val secondY = targetY - 1
                    if (secondY > -64) {
                        val subHolder = try {
                            if (getBiomeCoordsMethod != null) {
                                @Suppress("UNCHECKED_CAST")
                                getBiomeCoordsMethod.invoke(snapshot, localX, secondY, localZ) as? Holder<Biome>
                            } else if (getBiomePosMethod != null) {
                                @Suppress("UNCHECKED_CAST")
                                getBiomePosMethod.invoke(snapshot, BlockPos(centerX, secondY, centerZ)) as? Holder<Biome>
                            } else null
                        } catch (_: Throwable) { null }

                        if (subHolder != null) {
                            return subHolder
                        }
                    }

                    // 4. Balayage descendant par pas de section (16 blocs)
                    for (stepY in (targetY - 16) downTo -48 step 16) {
                        val stepHolder = try {
                            if (getBiomeCoordsMethod != null) {
                                @Suppress("UNCHECKED_CAST")
                                getBiomeCoordsMethod.invoke(snapshot, localX, stepY, localZ) as? Holder<Biome>
                            } else if (getBiomePosMethod != null) {
                                @Suppress("UNCHECKED_CAST")
                                getBiomePosMethod.invoke(snapshot, BlockPos(centerX, stepY, centerZ)) as? Holder<Biome>
                            } else null
                        } catch (_: Throwable) { null }

                        if (stepHolder != null) {
                            return stepHolder
                        }
                    }

                    // 5. Repli ultime : lecture directe depuis la première section non-nulle de snapshot.sections
                    if (getSectionsMethod != null) {
                        try {
                            val sections = getSectionsMethod.invoke(snapshot) as? Array<*>
                            if (sections != null) {
                                for (i in sections.indices.reversed()) {
                                    val sec = sections[i] as? LevelChunkSection ?: continue
                                    val holder = sec.getNoiseBiome(localX shr 2, 2, localZ shr 2)
                                    if (holder != null) {
                                        return holder
                                    }
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {}
        }

        // Stratégie 3 : ClientLevel si disponible
        if (clientLevel != null) {
            try {
                if (clientLevel.hasChunk(chunkPos.x, chunkPos.z)) {
                    val actualSurfaceY = try {
                        clientLevel.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ)
                    } catch (_: Throwable) { sampleY }
                    val samplePos = BlockPos(centerX, actualSurfaceY, centerZ)
                    return clientLevel.getBiome(samplePos)
                }
            } catch (_: Throwable) {}
        }

        return null
    }

    private val SUBTERRANEAN_Y_LEVELS = intArrayOf(16, -20, -45)

    private fun extractSubterraneanBiomes(
        chunkPos: ChunkPos,
        centerX: Int,
        centerZ: Int,
        biomeSource: net.minecraft.world.level.biome.BiomeSource?,
        sampler: net.minecraft.world.level.biome.Climate.Sampler?,
        clientLevel: net.minecraft.client.multiplayer.ClientLevel?,
        currentDim: ResourceKey<Level>
    ) {
        val quartX = QuartPos.fromBlock(centerX)
        val quartZ = QuartPos.fromBlock(centerZ)

        for (y in SUBTERRANEAN_Y_LEVELS) {
            val quartY = QuartPos.fromBlock(y)
            val caveHolder = if (biomeSource != null && sampler != null) {
                biomeSource.getNoiseBiome(quartX, quartY, quartZ, sampler)
            } else {
                clientLevel?.getNoiseBiome(quartX, quartY, quartZ)
            }

            if (caveHolder != null) {
                val profile = ChunkBiomeTagCache.getOrCreateProfile(caveHolder)
                if (ChunkBiomeTagCache.isSubterraneanProfile(profile)) {
                    ChunkBiomeTagCache.putSubterraneanProfile(currentDim, chunkPos, y, profile)
                }
            }
        }
    }

    /**
     * Recherche tous les fichiers de région explorés (disque solo + cache JourneyMap).
     */
    fun findExploredRegions(
        currentDim: ResourceKey<Level>,
        mc: Minecraft,
        clientApi: IClientAPI?
    ): Set<Pair<Int, Int>> {
        val regions = mutableSetOf<Pair<Int, Int>>()

        // 1. Source Solo : Dossier saves/{monde}/region/
        val server = mc.singleplayerServer
        if (server != null) {
            try {
                val worldPath = server.getWorldPath(LevelResource.ROOT)
                val dimNamespace = currentDim.location().namespace
                val dimPath = currentDim.location().path

                val candidates = when (currentDim) {
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

                val mcaRegex = Regex("""r\.(-?\d+)\.(-?\d+)\.mca""")
                for (cand in candidates) {
                    val regionDir = cand.toFile()
                    if (regionDir.exists() && regionDir.isDirectory) {
                        regionDir.listFiles { f -> f.name.endsWith(".mca") }?.forEach { f ->
                            val match = mcaRegex.matchEntire(f.name)
                            if (match != null) {
                                val rx = match.groupValues[1].toInt()
                                val rz = match.groupValues[2].toInt()
                                regions.add(Pair(rx, rz))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.debug("Cobblemon JM: Erreur lecture régions solo : {}", e.message)
            }
        }

        // 2. Source JourneyMap : Dossiers de tuiles (day, night, topo, biome, chunk_cache)
        try {
            val dimDir = findDimensionDirectory(currentDim, mc, clientApi)
            if (dimDir != null && dimDir.exists()) {
                val tileRegex = Regex("""(-?\d+)[.,](-?\d+)\.png""")
                val jmcRegex = Regex("""c\.(-?\d+)\.(-?\d+)\.jmc""")
                val mcaRegex = Regex("""r\.(-?\d+)\.(-?\d+)\.(?:mca|jmr)""")

                dimDir.walkTopDown().maxDepth(3).forEach { file ->
                    val name = file.name
                    val tileMatch = tileRegex.matchEntire(name)
                    if (tileMatch != null) {
                        regions.add(Pair(tileMatch.groupValues[1].toInt(), tileMatch.groupValues[2].toInt()))
                    } else {
                        val jmcMatch = jmcRegex.matchEntire(name)
                        if (jmcMatch != null) {
                            regions.add(Pair(jmcMatch.groupValues[1].toInt(), jmcMatch.groupValues[2].toInt()))
                        } else {
                            val mcaMatch = mcaRegex.matchEntire(name)
                            if (mcaMatch != null) {
                                regions.add(Pair(mcaMatch.groupValues[1].toInt(), mcaMatch.groupValues[2].toInt()))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Cobblemon JM: Erreur lecture cache JourneyMap : {}", e.message)
        }

        // 3. Proximité locale du joueur (couverture garantie dans la zone active)
        val player = mc.player
        if (player != null) {
            val pRegionX = (player.blockPosition().x shr 4) shr 5
            val pRegionZ = (player.blockPosition().z shr 4) shr 5
            val radius = if (regions.isEmpty()) 4 else 1
            for (drx in -radius..radius) {
                for (drz in -radius..radius) {
                    regions.add(Pair(pRegionX + drx, pRegionZ + drz))
                }
            }
        }

        return regions
    }

    /**
     * Localise le sous-dossier de la dimension actuelle dans journeymap/data/.
     */
    fun findDimensionDirectory(
        currentDim: ResourceKey<Level>,
        mc: Minecraft,
        clientApi: IClientAPI?
    ): File? {
        val rootDataDir = clientApi?.getDataPath("cobblemon_jei")?.parentFile?.parentFile
            ?: File(mc.gameDirectory, "journeymap/data")

        if (!rootDataDir.exists()) return null

        val dimName = currentDim.location().path.lowercase()
        val dimFull = currentDim.location().toString().replace(':', '~').lowercase()
        val dimNamespace = currentDim.location().namespace.lowercase()

        // Parcours des dossiers de mondes (sp ou mp)
        val worldDirs = rootDataDir.walkTopDown().maxDepth(3).filter { it.isDirectory }.toList()
        for (dir in worldDirs) {
            val dirName = dir.name.lowercase()
            if (dirName == dimName || dirName == dimFull ||
                (dimName == "overworld" && (dirName == "dim0" || dirName == "0")) ||
                (dimName == "the_nether" && (dirName == "dim-1" || dirName == "-1" || dirName == "nether")) ||
                (dimName == "the_end" && (dirName == "dim1" || dirName == "1" || dirName == "end")) ||
                (dirName == "${dimNamespace}_$dimName") ||
                (dirName.endsWith("~$dimName")) ||
                (dirName.endsWith("_$dimName"))
            ) {
                return dir
            }
        }

        return null
    }

    /**
     * Extrait les chunks précisément enregistrés dans un fichier de région JourneyMap (.jmc)
     * en lisant la table d'index d'en-tête (1024 entrées).
     */
    private fun getRecordedChunksInRegion(
        rx: Int,
        rz: Int,
        dimDir: File?,
        jmcDir: File?
    ): Set<ChunkPos>? {
        if (jmcDir == null || !jmcDir.exists()) return null

        val jmcFile = File(jmcDir, "c.$rx.$rz.jmc")
        if (!jmcFile.exists() || jmcFile.length() < 8208) return null

        val chunks = mutableSetOf<ChunkPos>()
        try {
            RandomAccessFile(jmcFile, "r").use { raf ->
                // Table d'index des longueurs : 1024 entiers de 4 octets à l'offset 4112
                raf.seek(4112)
                val buffer = ByteArray(4096)
                raf.readFully(buffer)

                val startChunkX = rx * 32
                val startChunkZ = rz * 32

                for (idx in 0 until 1024) {
                    val bOffset = idx * 4
                    val length = ((buffer[bOffset].toInt() and 0xFF) shl 24) or
                            ((buffer[bOffset + 1].toInt() and 0xFF) shl 16) or
                            ((buffer[bOffset + 2].toInt() and 0xFF) shl 8) or
                            (buffer[bOffset + 3].toInt() and 0xFF)

                    if (length > 0) {
                        val localX = idx % 32
                        val localZ = idx / 32
                        chunks.add(ChunkPos(startChunkX + localX, startChunkZ + localZ))
                    }
                }
            }
            return chunks
        } catch (_: Exception) {
            return null
        }
    }
}
