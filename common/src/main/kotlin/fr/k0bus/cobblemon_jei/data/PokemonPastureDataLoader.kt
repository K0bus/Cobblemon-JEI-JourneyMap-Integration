package fr.k0bus.cobblemon_jei.data

import fr.k0bus.cobblemon_jei.recipe.PastureDropItem
import fr.k0bus.cobblemon_jei.recipe.PastureHarvestType
import fr.k0bus.cobblemon_jei.recipe.PokemonPastureRecipe
import com.cobblemon.mod.common.api.drop.ItemDropEntry
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.FormData
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
 * Chargeur de données pour la production de ressources au pâturage.
 *
 * Combine deux sources de données officielles :
 * 1. Les interactions de Pokémon (brossage, traite au seau/bouteille, tonte, fertilisation).
 * 2. Les tables de drops naturelles des Pokémon (butin passif généré au pâturage).
 */
object PokemonPastureDataLoader {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/PastureLoader")
    private var cachedRecipes: List<PokemonPastureRecipe>? = null

    /**
     * Charge l'ensemble des recettes de pâturage (interactions manuelles + productions passives).
     */
    @Synchronized
    fun loadPastureRecipes(): List<PokemonPastureRecipe> {
        cachedRecipes?.let { return it }

        PokemonEvolutionDataLoader.ensureSpeciesLoaded()

        val recipes = mutableListOf<PokemonPastureRecipe>()
        val seenSignatures = mutableSetOf<String>()

        // 1. Chargement des interactions manuelles (brossage, traite, etc.)
        loadInteractions(recipes, seenSignatures)

        // 2. Chargement des drops passifs de pâturage (DropTable des Pokémon)
        loadPassiveDrops(recipes, seenSignatures)

        // Tri ergonomique par numéro de Pokédex puis type de récolte puis clé de l'action
        val sortedRecipes = recipes.sortedWith(
            compareBy<PokemonPastureRecipe> { it.species.nationalPokedexNumber }
                .thenBy { it.species.name }
                .thenBy { it.harvestType }
                .thenBy { it.actionTranslationKey }
        )

        cachedRecipes = sortedRecipes
        logger.info("Cobblemon JEI: ${sortedRecipes.size} recettes de pâturage chargées avec succès.")
        return sortedRecipes
    }

    /**
     * Scanne les fichiers de 'pokemon_interactions' pour créer les recettes d'interactions manuelles.
     */
    private fun loadInteractions(
        recipes: MutableList<PokemonPastureRecipe>,
        seenSignatures: MutableSet<String>
    ) {
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
    }

    private fun processInteractionJson(
        rootJson: JsonObject,
        recipes: MutableList<PokemonPastureRecipe>,
        seenSignatures: MutableSet<String>
    ) {
        // Extraction des cibles Pokémon (ex: "miltank", "bouffalant gender=female", "oricorio form=baile")
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

            // Conditions supplémentaires issues de la cible (ex: sexe)
            val baseConditions = mutableListOf<String>()
            parsedProps?.gender?.let { gender ->
                when (gender.name.lowercase()) {
                    "female" -> baseConditions.add("Femelle uniquement")
                    "male" -> baseConditions.add("Mâle uniquement")
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

                // Analyse des prérequis de l'interaction (ex: outil tenu par le joueur)
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

                // Extraction des objets donnés ou droppés
                val dropOutputs = mutableListOf<PastureDropItem>()
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
                                    PastureDropItem(
                                        itemStack = ItemStack(itemHolder.get()),
                                        chancePercentage = 100.0f,
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
                        val foodData = PokemonFoodResolver.resolveFood(species, form)
                        recipes.add(
                            PokemonPastureRecipe(
                                species = species,
                                form = form,
                                harvestType = PastureHarvestType.INTERACTION,
                                actionTranslationKey = actionTranslationKey,
                                requiredTools = requiredTools.distinctBy { it.item },
                                foodItems = foodData.foodItems,
                                specificFoodNames = foodData.specificFoodNames,
                                outputs = dropOutputs,
                                conditions = interactionConditions,
                                cooldownTicks = cooldownTicks,
                                notes = null
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Parcourt les espèces et formes enregistrées pour extraire leurs drops passifs (DropTable).
     */
    private fun loadPassiveDrops(
        recipes: MutableList<PokemonPastureRecipe>,
        seenSignatures: MutableSet<String>
    ) {
        val allSpecies = PokemonSpecies.species

        for (species in allSpecies) {
            val formsToProcess = mutableListOf<FormData>()
            formsToProcess.add(species.standardForm)
            for (f in species.forms) {
                if (f != species.standardForm) {
                    formsToProcess.add(f)
                }
            }

            for (form in formsToProcess) {
                val dropTable = try { form.drops } catch (_: Throwable) { null } ?: continue
                if (dropTable.entries.isEmpty()) continue

                val dropItems = mutableListOf<PastureDropItem>()

                for (entry in dropTable.entries) {
                    if (entry is ItemDropEntry) {
                        val itemHolder = BuiltInRegistries.ITEM.getOptional(entry.item)
                        if (itemHolder.isPresent && itemHolder.get() != Items.AIR) {
                            val min = entry.quantityRange?.first ?: entry.quantity
                            val max = entry.quantityRange?.last ?: entry.quantity
                            val pct = entry.percentage.coerceIn(0.1f, 100.0f)

                            dropItems.add(
                                PastureDropItem(
                                    itemStack = ItemStack(itemHolder.get()),
                                    chancePercentage = pct,
                                    minCount = min,
                                    maxCount = max
                                )
                            )
                        }
                    }
                }

                if (dropItems.isNotEmpty()) {
                    val signature = "passive|${species.resourceIdentifier}|${form.name}|${dropItems.joinToString { it.itemStack.item.toString() }}"
                    if (seenSignatures.add(signature)) {
                        val foodData = PokemonFoodResolver.resolveFood(species, form)
                        recipes.add(
                            PokemonPastureRecipe(
                                species = species,
                                form = form,
                                harvestType = PastureHarvestType.PASSIVE_DROP,
                                actionTranslationKey = "cobblemon_jei.action.passive",
                                requiredTools = emptyList(),
                                foodItems = foodData.foodItems,
                                specificFoodNames = foodData.specificFoodNames,
                                outputs = dropItems,
                                conditions = emptyList(),
                                notes = null
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
            "cobblemon:brush" -> "cobblemon_jei.action.brush"
            "cobblemon:milking" -> "cobblemon_jei.action.milking"
            "cobblemon:shears" -> "cobblemon_jei.action.shears"
            "cobblemon:bone_meal" -> "cobblemon_jei.action.fertilize"
            "cobblemon:bucket" -> "cobblemon_jei.action.bucket"
            "cobblemon:glass_bottle" -> "cobblemon_jei.action.bottle"
            else -> "cobblemon_jei.action.manual"
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
                    // Fallbacks si le tag est encore vide ou personnalisé
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
            // Fallback de sécurité selon le nom
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
