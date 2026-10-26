package fr.k0bus.cobblemon_jei.infrastructure.persistence

import com.google.gson.*
import fr.k0bus.cobblemon_jei.domain.goals.ExplorationGoal
import fr.k0bus.cobblemon_jei.domain.goals.GoalStatus
import fr.k0bus.cobblemon_jei.domain.goals.GoalType
import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

/**
 * Persistance locale en JSON des objectifs d'exploration par session de monde / serveur.
 */
class JsonGoalStorage(
    private val baseDirProvider: () -> File = {
        try {
            Minecraft.getInstance().gameDirectory ?: File(".")
        } catch (_: Throwable) {
            File(".")
        }
    }
) {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/JsonGoalStorage")
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(ResourceLocation::class.java, object : JsonSerializer<ResourceLocation>, JsonDeserializer<ResourceLocation> {
            override fun serialize(src: ResourceLocation, typeOfSrc: java.lang.reflect.Type, context: JsonSerializationContext): JsonElement {
                return JsonPrimitive(src.toString())
            }

            override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: JsonDeserializationContext): ResourceLocation {
                return ResourceLocation.parse(json.asString)
            }
        })
        .registerTypeAdapter(UUID::class.java, object : JsonSerializer<UUID>, JsonDeserializer<UUID> {
            override fun serialize(src: UUID, typeOfSrc: java.lang.reflect.Type, context: JsonSerializationContext): JsonElement {
                return JsonPrimitive(src.toString())
            }

            override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: JsonDeserializationContext): UUID {
                return UUID.fromString(json.asString)
            }
        })
        .create()

    private fun getStorageDir(): File {
        val dir = File(baseDirProvider(), "cobblemon_jei/goals")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeWorldId(worldId: String): String {
        return worldId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64).ifEmpty { "default" }
    }

    private fun getFileForWorld(worldId: String): File {
        val safeName = sanitizeWorldId(worldId)
        return File(getStorageDir(), "$safeName.json")
    }

    /**
     * Charge la liste des objectifs sauvegardés pour le monde spécifié.
     */
    fun loadGoals(worldId: String): List<ExplorationGoal> {
        val file = getFileForWorld(worldId)
        if (!file.exists()) return emptyList()

        return try {
            file.reader().use { reader ->
                val jsonArray = JsonParser.parseReader(reader).asJsonArray
                val list = mutableListOf<ExplorationGoal>()
                for (elem in jsonArray) {
                    try {
                        val obj = elem.asJsonObject
                        val id = UUID.fromString(obj.get("id").asString)
                        val targetObj = obj.getAsJsonObject("target")
                        val speciesId = ResourceLocation.parse(targetObj.get("speciesId").asString)
                        val formId = if (targetObj.has("formId") && !targetObj.get("formId").isJsonNull) {
                            ResourceLocation.parse(targetObj.get("formId").asString)
                        } else null
                        val shiny = targetObj.has("shiny") && targetObj.get("shiny").asBoolean
                        val customLabel = if (targetObj.has("customLabel") && !targetObj.get("customLabel").isJsonNull) {
                            targetObj.get("customLabel").asString
                        } else null

                        val goalType = try {
                            GoalType.valueOf(obj.get("goalType").asString)
                        } catch (_: Throwable) {
                            GoalType.CAPTURE
                        }

                        val priority = if (obj.has("priority")) obj.get("priority").asInt else 0
                        val status = try {
                            GoalStatus.valueOf(obj.get("status").asString)
                        } catch (_: Throwable) {
                            GoalStatus.ACTIVE
                        }

                        val createdAt = if (obj.has("createdAt")) obj.get("createdAt").asLong else System.currentTimeMillis()
                        val completedAt = if (obj.has("completedAt") && !obj.get("completedAt").isJsonNull) {
                            obj.get("completedAt").asLong
                        } else null

                        list.add(
                            ExplorationGoal(
                                id = id,
                                target = PokemonTarget(
                                    speciesId = speciesId,
                                    formId = formId,
                                    shiny = shiny,
                                    customLabel = customLabel
                                ),
                                goalType = goalType,
                                priority = priority,
                                status = status,
                                createdAt = createdAt,
                                completedAt = completedAt
                            )
                        )
                    } catch (e: Throwable) {
                        logger.warn("Impossible de désérialiser un objectif: {}", e.message)
                    }
                }
                list
            }
        } catch (e: Throwable) {
            logger.error("Erreur lors de la lecture des objectifs pour $worldId: {}", e.message)
            emptyList()
        }
    }

    /**
     * Sauvegarde atomique de la liste des objectifs pour le monde spécifié.
     */
    fun saveGoals(worldId: String, goals: List<ExplorationGoal>) {
        val file = getFileForWorld(worldId)
        val tempFile = File(file.parentFile, "${file.name}.tmp")

        try {
            tempFile.writer().use { writer ->
                gson.toJson(goals, writer)
            }
            if (file.exists()) {
                file.delete()
            }
            tempFile.renameTo(file)
        } catch (e: Throwable) {
            logger.error("Erreur lors de la sauvegarde atomique des objectifs pour $worldId: {}", e.message)
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
