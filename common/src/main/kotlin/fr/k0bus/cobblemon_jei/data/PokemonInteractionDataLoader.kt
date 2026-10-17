package fr.k0bus.cobblemon_jei.data

import fr.k0bus.cobblemon_jei.recipe.PokemonInteractionDrop
import fr.k0bus.cobblemon_jei.recipe.PokemonInteractionRecipe
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory
import java.io.InputStreamReader

/**
 * Chargeur de données pour les interactions directes avec les Pokémon (Cobblemon).
 * Tonte de Moumouton, traite d'Écrémeuh, brossage, bouteilles, etc.
 */
object PokemonInteractionDataLoader {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/InteractionLoader")
    private var cachedRecipes: List<PokemonInteractionRecipe>? = null

    @Synchronized
    fun loadInteractionRecipes(): List<PokemonInteractionRecipe> {
        cachedRecipes?.let { return it }

        PokemonEvolutionDataLoader.ensureSpeciesLoaded()

        val recipes = mutableListOf<PokemonInteractionRecipe>()
        val seenSignatures = mutableSetOf<String>()

        CobblemonDataScanner.scanDataResources("pokemon_interactions") { name, stream ->
            try {
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    val rootJson = JsonParser.parseReader(reader).asJsonObject
                    processInteractionJson(rootJson, recipes, seenSignatures)
                }
            } catch (e: Throwable) {
                logger.debug("Erreur lecture interaction {}: {}", name, e.message)
            }
        }

        val sortedRecipes = recipes.sortedWith(
            compareBy<PokemonInteractionRecipe> { it.species.nationalPokedexNumber }
                .thenBy { it.species.name }
                .thenBy { it.actionTranslationKey }
        )

        cachedRecipes = sortedRecipes
        logger.info("Cobblemon JEI: ${sortedRecipes.size} interactions Pokémon chargées avec succès.")
        return sortedRecipes
    }

    private fun processInteractionJson(
        rootJson: JsonObject,
        recipes: MutableList<PokemonInteractionRecipe>,
        seenSignatures: MutableSet<String>
    ) {
        val targets = mutableListOf<String>()
        if (rootJson.has("requirements") && rootJson.get("requirements").isJsonArray) {
            for (reqElem in rootJson.getAsJsonArray("requirements")) {
                if (reqElem.isJsonObject) {
                    val reqObj = reqElem.asJsonObject
                    if (reqObj.get("variant")?.asString == "properties" && reqObj.has("target")) {
                        targets.add(reqObj.get("target").asString)
                    }
                }
            }
        }

        if (targets.isEmpty()) return
        val interactionsArray = rootJson.getAsJsonArray("interactions") ?: return

        for (targetStr in targets) {
            val parsedProps = try { PokemonProperties.parse(targetStr) } catch (_: Throwable) { null }
            val speciesName = parsedProps?.species ?: targetStr.split(" ").firstOrNull() ?: continue
            val species = findSpecies(speciesName) ?: continue

            val form = if (parsedProps?.form != null) {
                species.forms.firstOrNull { it.name.equals(parsedProps.form, ignoreCase = true) } ?: species.standardForm
            } else {
                species.standardForm
            }

            val baseConditions = mutableListOf<String>()
            parsedProps?.gender?.let { gender ->
                when (gender.name.lowercase()) {
                    "female" -> baseConditions.add("gui.cobblemon_jei.condition.female_only")
                    "male" -> baseConditions.add("gui.cobblemon_jei.condition.male_only")
                    else -> {}
                }
            }

            for (interElem in interactionsArray) {
                if (!interElem.isJsonObject) continue
                val interObj = interElem.asJsonObject

                val grouping = interObj.get("grouping")?.asString ?: "cobblemon:interaction"
                val actionTranslationKey = getActionTranslationKey(grouping)

                val requiredTools = mutableListOf<ItemStack>()
                val interactionConditions = baseConditions.toMutableList()

                if (interObj.has("requirements") && interObj.get("requirements").isJsonArray) {
                    for (reqElem in interObj.getAsJsonArray("requirements")) {
                        if (!reqElem.isJsonObject) continue
                        val reqObj = reqElem.asJsonObject
                        val variant = reqObj.get("variant")?.asString
                        if (variant == "owner_held_item" && reqObj.has("itemCondition")) {
                            val icElem = reqObj.get("itemCondition")
                            val condStr = if (icElem.isJsonPrimitive) icElem.asString else if (icElem.isJsonObject && icElem.asJsonObject.has("item")) icElem.asJsonObject.get("item").asString else null
                            if (condStr != null) {
                                resolveItemCondition(condStr, requiredTools)
                            }
                        }
                    }
                }

                val dropOutputs = mutableListOf<PokemonInteractionDrop>()
                if (interObj.has("effects") && interObj.get("effects").isJsonArray) {
                    for (effElem in interObj.getAsJsonArray("effects")) {
                        if (!effElem.isJsonObject) continue
                        val effObj = effElem.asJsonObject
                        val effVariant = effObj.get("variant")?.asString
                        if (effVariant == "give_item" || effVariant == "drop_item") {
                            val itemLocStr = effObj.get("item")?.asString ?: continue
                            val itemLoc = ResourceLocation.tryParse(itemLocStr) ?: continue
                            val itemHolder = BuiltInRegistries.ITEM.getOptional(itemLoc)
                            if (itemHolder.isPresent && itemHolder.get() != Items.AIR) {
                                val (minAmt, maxAmt) = parseAmount(effObj.get("amount"))
                                dropOutputs.add(
                                    PokemonInteractionDrop(
                                        itemStack = ItemStack(itemHolder.get()),
                                        minCount = minAmt,
                                        maxCount = maxAmt
                                    )
                                )
                            }
                        }
                    }
                }

                val cooldownTicks = interObj.get("cooldown")?.asString?.toIntOrNull()
                    ?: (if (interObj.get("cooldown")?.isJsonPrimitive == true && interObj.get("cooldown").asJsonPrimitive.isNumber) interObj.get("cooldown").asInt else 0)

                if (dropOutputs.isNotEmpty()) {
                    val signature = "${species.resourceIdentifier}|$grouping|${dropOutputs.joinToString { it.itemStack.item.toString() }}"
                    if (seenSignatures.add(signature)) {
                        recipes.add(
                            PokemonInteractionRecipe(
                                species = species,
                                form = form,
                                actionTranslationKey = actionTranslationKey,
                                requiredTools = requiredTools.distinctBy { it.item },
                                outputs = dropOutputs,
                                conditions = interactionConditions,
                                cooldownTicks = cooldownTicks
                            )
                        )
                    }
                }
            }
        }
    }

    private fun findSpecies(name: String): Species? {
        val clean = name.lowercase().trim()
        return PokemonSpecies.getByName(clean)
            ?: PokemonSpecies.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", clean))
            ?: PokemonSpecies.species.firstOrNull {
                it.resourceIdentifier.path.equals(clean, ignoreCase = true) ||
                        it.name.equals(clean, ignoreCase = true)
            }
    }

    private fun getActionTranslationKey(grouping: String): String {
        return when (grouping) {
            "cobblemon:brush" -> "gui.cobblemon_jei.action.brush"
            "cobblemon:milking" -> "gui.cobblemon_jei.action.milking"
            "cobblemon:shears" -> "gui.cobblemon_jei.action.shears"
            "cobblemon:bone_meal" -> "gui.cobblemon_jei.action.fertilize"
            "cobblemon:bucket" -> "gui.cobblemon_jei.action.bucket"
            "cobblemon:glass_bottle" -> "gui.cobblemon_jei.action.bottle"
            else -> "gui.cobblemon_jei.action.manual"
        }
    }

    private fun resolveItemCondition(conditionStr: String, output: MutableList<ItemStack>) {
        try {
            if (conditionStr.startsWith("#")) {
                val tagLoc = ResourceLocation.parse(conditionStr.removePrefix("#"))
                val tagKey = TagKey.create(Registries.ITEM, tagLoc)
                val tagOpt = BuiltInRegistries.ITEM.getTag(tagKey)
                if (tagOpt.isPresent && tagOpt.get().size() > 0) {
                    tagOpt.get().forEach { output.add(ItemStack(it.value())) }
                } else {
                    when {
                        conditionStr.contains("brush") -> output.add(ItemStack(Items.BRUSH))
                        conditionStr.contains("fertilizer") || conditionStr.contains("bone_meal") -> output.add(ItemStack(Items.BONE_MEAL))
                        conditionStr.contains("shear") -> output.add(ItemStack(Items.SHEARS))
                    }
                }
            } else {
                val loc = ResourceLocation.parse(conditionStr)
                BuiltInRegistries.ITEM.getOptional(loc).ifPresent { item ->
                    if (item != Items.AIR) output.add(ItemStack(item))
                }
            }
        } catch (_: Throwable) {
            when {
                conditionStr.contains("bucket") -> output.add(ItemStack(Items.BUCKET))
                conditionStr.contains("bottle") -> output.add(ItemStack(Items.GLASS_BOTTLE))
                conditionStr.contains("brush") -> output.add(ItemStack(Items.BRUSH))
                conditionStr.contains("shear") -> output.add(ItemStack(Items.SHEARS))
            }
        }
    }

    private fun parseAmount(amountElem: com.google.gson.JsonElement?): Pair<Int, Int> {
        if (amountElem == null || amountElem.isJsonNull) return Pair(1, 1)
        if (amountElem.isJsonPrimitive) {
            val prim = amountElem.asJsonPrimitive
            if (prim.isNumber) {
                val v = prim.asInt
                return Pair(v, v)
            }
            val str = prim.asString
            if (str.contains("-")) {
                val parts = str.split("-")
                val min = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
                val max = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: min
                return Pair(min, max)
            }
            val single = str.toIntOrNull() ?: 1
            return Pair(single, single)
        }
        return Pair(1, 1)
    }
}
