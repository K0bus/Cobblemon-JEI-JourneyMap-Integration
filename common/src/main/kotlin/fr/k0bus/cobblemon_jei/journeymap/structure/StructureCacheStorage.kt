package fr.k0bus.cobblemon_jei.journeymap.structure

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestionnaire de persistance asynchrone pour le cache des structures et monuments découverts.
 * Sauvegarde les coordonnées exactes et métadonnées dans `<gameDir>/config/cobblemon_structures_cache.json`.
 */
object StructureCacheStorage {

    private val logger = LoggerFactory.getLogger("CobblemonJM/StructureCache")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private fun getStorageFile(): File? {
        return try {
            val gameDir = Minecraft.getInstance().gameDirectory ?: return null
            val configDir = File(gameDir, "config")
            if (!configDir.exists()) configDir.mkdirs()
            File(configDir, "cobblemon_structures_cache.json")
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Charge les structures découvertes sauvegardées pour une dimension donnée.
     */
    fun loadCache(): Map<Pair<ResourceKey<Level>, BlockPos>, StructureOverlayManager.DiscoveredStructure> {
        val file = getStorageFile() ?: return emptyMap()
        if (!file.exists()) return emptyMap()

        val result = mutableMapOf<Pair<ResourceKey<Level>, BlockPos>, StructureOverlayManager.DiscoveredStructure>()

        try {
            val json = file.readText(Charsets.UTF_8)
            val root = JsonParser.parseString(json).asJsonObject
            if (root.has("structures")) {
                val array = root.getAsJsonArray("structures")
                for (elem in array) {
                    val obj = elem.asJsonObject
                    val dimLoc = ResourceLocation.parse(obj.get("dimension").asString)
                    val dimKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimLoc)

                    val posX = obj.get("posX").asInt
                    val posY = obj.get("posY").asInt
                    val posZ = obj.get("posZ").asInt
                    val pos = BlockPos(posX, posY, posZ)

                    val id = ResourceLocation.parse(obj.get("id").asString)
                    val displayName = obj.get("displayName").asString
                    val targetPoke = if (obj.has("targetPokemon")) obj.get("targetPokemon").asString else null

                    val pedX = if (obj.has("pedX")) obj.get("pedX").asInt else null
                    val pedY = if (obj.has("pedY")) obj.get("pedY").asInt else null
                    val pedZ = if (obj.has("pedZ")) obj.get("pedZ").asInt else null
                    val pedestalPos = if (pedX != null && pedY != null && pedZ != null) BlockPos(pedX, pedY, pedZ) else null

                    val metadata = DynamicStructureRegistryService.StructureMetadata(
                        id = id,
                        displayName = displayName,
                        type = DynamicStructureRegistryService.StructureType.LEGENDARY,
                        targetPokemonName = targetPoke,
                        iconLocation = StructureOverlayManager.LEGENDARY_STAR_ICON
                    )

                    val discovered = StructureOverlayManager.DiscoveredStructure(
                        id = id,
                        pos = pos,
                        dimension = dimKey,
                        metadata = metadata,
                        pedestalPos = pedestalPos
                    )

                    result[Pair(dimKey, pos)] = discovered
                }
            }
            logger.info("Cobblemon JM: ${result.size} structures restaurées depuis le cache disque.")
        } catch (e: Throwable) {
            logger.debug("Cobblemon JM: Erreur lecture cache structures: {}", e.message)
        }

        return result
    }

    /**
     * Sauvegarde de façon 100% asynchrone les structures découvertes sur disque.
     */
    fun saveCacheAsync(structures: Map<Pair<ResourceKey<Level>, BlockPos>, StructureOverlayManager.DiscoveredStructure>) {
        CompletableFuture.runAsync {
            try {
                val file = getStorageFile() ?: return@runAsync
                val root = JsonObject()
                val array = JsonArray()

                for ((_, s) in structures) {
                    val obj = JsonObject()
                    obj.addProperty("id", s.id.toString())
                    obj.addProperty("dimension", s.dimension.location().toString())
                    obj.addProperty("posX", s.pos.x)
                    obj.addProperty("posY", s.pos.y)
                    obj.addProperty("posZ", s.pos.z)
                    obj.addProperty("displayName", s.metadata.displayName)
                    s.metadata.targetPokemonName?.let { obj.addProperty("targetPokemon", it) }
                    s.pedestalPos?.let { p ->
                        obj.addProperty("pedX", p.x)
                        obj.addProperty("pedY", p.y)
                        obj.addProperty("pedZ", p.z)
                    }
                    array.add(obj)
                }

                root.add("structures", array)
                file.writeText(gson.toJson(root), Charsets.UTF_8)
            } catch (e: Throwable) {
                logger.debug("Cobblemon JM: Erreur écriture cache structures: {}", e.message)
            }
        }
    }
}
