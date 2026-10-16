package fr.k0bus.cobblemon_jei.data

import fr.k0bus.cobblemon_jei.recipe.PokemonEvolutionRecipe
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution
import com.cobblemon.mod.common.pokemon.requirements.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.StringReader

/**
 * Chargeur et processeur de toutes les branches d'évolution de Pokémon configurées dans Cobblemon.
 * Scanne directement les définitions JSON issues des JARs de Cobblemon, des dossiers mods,
 * des datapacks et du serveur local afin de garantir le chargement de 100% des évolutions (700+)
 * et la résolution exacte de leurs objets requis (pierres d'évolution, câbles de liaison, objets tenus).
 */
object PokemonEvolutionDataLoader {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/EvolutionLoader")
    private var cachedEvolutions: List<PokemonEvolutionRecipe>? = null

    // Cache local d'espèces pour garantir la résolution même si le serveur n'est pas encore initialisé
    private val localSpeciesById = mutableMapOf<String, Species>()
    private val localSpeciesByName = mutableMapOf<String, Species>()
    private val rawSpeciesJsonMap = mutableMapOf<String, String>()
    private var hasPreloadedSpecies = false

    /**
     * S'assure que toutes les définitions d'espèces Cobblemon sont préchargées en mémoire.
     * Cette fonction peut être appelée dès le démarrage de JEI (registerIngredients) pour
     * garantir que [PokemonSpecies.species] n'est pas vide avant qu'un monde ne soit ouvert.
     */
    @Synchronized
    fun ensureSpeciesLoaded() {
        if (hasPreloadedSpecies) return

        logger.info("Cobblemon JEI: Préchargement des espèces Cobblemon depuis les fichiers de données...")

        val parsedMap = mutableMapOf<ResourceLocation, Species>()

        CobblemonDataScanner.scanDataResources("species") { name, stream ->
            try {
                val jsonText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                rawSpeciesJsonMap[name] = jsonText
                val cleanFileId = File(name).nameWithoutExtension.lowercase()

                val resLoc = ResourceLocation.fromNamespaceAndPath("cobblemon", cleanFileId)

                // Tentative de parsing via le parser officiel Cobblemon
                var parsedSpecies: Species? = null
                try {
                    parsedSpecies = PokemonSpecies.parse(BufferedReader(StringReader(jsonText)), resLoc)
                } catch (_: Throwable) {}

                if (parsedSpecies != null) {
                    parsedMap[resLoc] = parsedSpecies
                    indexSpeciesLocally(parsedSpecies, cleanFileId)
                }
            } catch (e: Throwable) {
                logger.debug("Erreur lecture JSON espèce {}: {}", name, e.message)
            }
        }

        // Si PokemonSpecies est vide (car le client est au menu principal), on injecte nos espèces préchargées
        if (PokemonSpecies.species.isEmpty() && parsedMap.isNotEmpty()) {
            try {
                PokemonSpecies.reload(parsedMap)
                logger.info("Cobblemon JEI: ${parsedMap.size} espèces enregistrées dans PokemonSpecies.reload.")
            } catch (e: Throwable) {
                logger.warn("Impossible d'injecter les espèces dans PokemonSpecies: {}", e.message)
            }
        }

        // Indexe également toutes les espèces déjà présentes dans PokemonSpecies
        try {
            for (sp in PokemonSpecies.species) {
                indexSpeciesLocally(sp, sp.resourceIdentifier.path.lowercase())
            }
        } catch (_: Throwable) {}

        hasPreloadedSpecies = true
        logger.info("Cobblemon JEI: Index d'espèces prêt avec ${localSpeciesById.size} entrées uniques.")
    }

    private fun indexSpeciesLocally(sp: Species, fileId: String) {
        val path = try { sp.resourceIdentifier.path.lowercase() } catch (_: Throwable) { fileId }
        localSpeciesById[path] = sp
        localSpeciesById[fileId] = sp
        localSpeciesByName[sp.name.lowercase()] = sp

        val clean = sp.name.lowercase().replace(" ", "").replace("'", "").replace(".", "").replace("-", "")
        localSpeciesByName[clean] = sp

        try {
            localSpeciesByName[sp.translatedName.string.lowercase()] = sp
        } catch (_: Throwable) {}
    }

    /**
     * Recherche une espèce par son nom ou son identifiant.
     */
    fun findSpeciesByName(name: String): Species? {
        ensureSpeciesLoaded()
        val q = name.trim().lowercase()
        localSpeciesById[q]?.let { return it }
        localSpeciesByName[q]?.let { return it }

        val clean = q.replace(" ", "").replace("'", "").replace(".", "").replace("-", "")
        localSpeciesByName[clean]?.let { return it }
        localSpeciesById[clean]?.let { return it }

        try {
            PokemonSpecies.getByName(clean)?.let { return it }
        } catch (_: Throwable) {}
        try {
            PokemonSpecies.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", clean))?.let { return it }
        } catch (_: Throwable) {}
        return null
    }

    /**
     * Charge et retourne l'ensemble des recettes d'évolution de Cobblemon.
     */
    fun loadEvolutionRecipes(): List<PokemonEvolutionRecipe> {
        cachedEvolutions?.let { return it }

        ensureSpeciesLoaded()

        val recipes = mutableListOf<PokemonEvolutionRecipe>()
        val seenKeys = mutableSetOf<String>()

        logger.info("Cobblemon JEI: Début de l'extraction des recettes d'évolution...")

        // 1. Parsing depuis les fichiers JSON bruts collectés
        for ((filePath, jsonText) in rawSpeciesJsonMap) {
            try {
                val json = JsonParser.parseString(jsonText).asJsonObject
                val fileId = File(filePath).nameWithoutExtension.lowercase()
                val spName = if (json.has("name")) json.get("name").asString else ""
                val sourceSpecies = findSpeciesByName(fileId) ?: findSpeciesByName(spName) ?: continue

                // Forme standard (évolutions à la racine)
                if (json.has("evolutions") && json.get("evolutions").isJsonArray) {
                    val evos = json.getAsJsonArray("evolutions")
                    for (elem in evos) {
                        if (elem.isJsonObject) {
                            val recipe = parseRawEvolutionJson(sourceSpecies, sourceSpecies.standardForm, elem.asJsonObject)
                            if (recipe != null) {
                                val key = "${recipe.sourceSpecies.name}:${recipe.sourceForm.name}->${recipe.targetSpecies.name}:${recipe.targetForm.name}:${recipe.requirementsSummary}"
                                if (seenKeys.add(key)) {
                                    recipes.add(recipe)
                                }
                            }
                        }
                    }
                }

                // Formes secondaires
                if (json.has("forms") && json.get("forms").isJsonArray) {
                    val forms = json.getAsJsonArray("forms")
                    for (formElem in forms) {
                        if (formElem.isJsonObject) {
                            val formObj = formElem.asJsonObject
                            val formName = if (formObj.has("name")) formObj.get("name").asString else ""
                            val sourceForm = sourceSpecies.forms.firstOrNull { it.name.equals(formName, ignoreCase = true) } ?: sourceSpecies.standardForm

                            if (formObj.has("evolutions") && formObj.get("evolutions").isJsonArray) {
                                val formEvos = formObj.getAsJsonArray("evolutions")
                                for (elem in formEvos) {
                                    if (elem.isJsonObject) {
                                        val recipe = parseRawEvolutionJson(sourceSpecies, sourceForm, elem.asJsonObject)
                                        if (recipe != null) {
                                            val key = "${recipe.sourceSpecies.name}:${recipe.sourceForm.name}->${recipe.targetSpecies.name}:${recipe.targetForm.name}:${recipe.requirementsSummary}"
                                            if (seenKeys.add(key)) {
                                                recipes.add(recipe)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                logger.debug("Erreur parsing évolution JSON pour {}: {}", filePath, e.message)
            }
        }

        // 2. Inspection des objets Species en mémoire (couvre les évolutions des objets Species complets)
        loadFromInMemorySpecies(recipes, seenKeys)

        logger.info("Cobblemon JEI: ${recipes.size} recettes d'évolution consolidées prêtes pour JEI.")
        cachedEvolutions = recipes
        return recipes
    }

    private fun parseRawEvolutionJson(
        sourceSpecies: Species,
        sourceForm: FormData,
        evoObj: JsonObject
    ): PokemonEvolutionRecipe? {
        val resultStr = if (evoObj.has("result")) evoObj.get("result").asString else return null
        val targetQuery = resultStr.split(" ")[0].lowercase()
        val targetSpecies = findSpeciesByName(targetQuery) ?: return null

        val targetForm = if (resultStr.contains(" ")) {
            val qualifier = resultStr.substringAfter(" ").lowercase()
            targetSpecies.forms.firstOrNull { f ->
                f.name.equals(qualifier, ignoreCase = true) ||
                        f.aspects.any { it.equals(qualifier, ignoreCase = true) }
            } ?: targetSpecies.standardForm
        } else {
            targetSpecies.standardForm
        }

        val variant = if (evoObj.has("variant")) evoObj.get("variant").asString.lowercase() else "level_up"
        val variantLabel = when (variant) {
            "item_interact" -> "Utilisation d'un objet"
            "trade" -> "Échange"
            "level_up" -> "Montée de niveau"
            else -> "Évolution"
        }

        val requiredItems = mutableListOf<ItemStack>()
        val conditions = mutableListOf<String>()

        // Contexte d'objet requis (ItemInteractionEvolution ou partenaire d'échange)
        if (evoObj.has("requiredContext")) {
            val rc = evoObj.get("requiredContext")
            val itemStr = if (rc.isJsonPrimitive) rc.asString else if (rc.isJsonObject && rc.asJsonObject.has("item")) rc.asJsonObject.get("item").asString else null
            if (itemStr != null) {
                val partnerSpecies = findSpeciesByName(itemStr)
                if (partnerSpecies != null) {
                    conditions.add("Échange contre ${partnerSpecies.translatedName.string}")
                } else {
                    resolveItemString(itemStr, requiredItems)
                }
            }
        }

        // Exigences (Requirements)
        var minLevel: Int? = null
        if (evoObj.has("requirements") && evoObj.get("requirements").isJsonArray) {
            val reqs = evoObj.getAsJsonArray("requirements")
            for (reqElem in reqs) {
                if (reqElem.isJsonObject) {
                    val reqObj = reqElem.asJsonObject
                    when (reqObj.get("variant")?.asString?.lowercase()) {
                        "level" -> {
                            val lvl = if (reqObj.has("minLevel")) reqObj.get("minLevel").asInt else 1
                            if (lvl > 1) minLevel = lvl
                        }
                        "friendship" -> {
                            val amt = if (reqObj.has("amount")) reqObj.get("amount").asInt else 160
                            conditions.add("Bonheur ≥ $amt")
                        }
                        "held_item" -> {
                            if (reqObj.has("itemCondition")) {
                                val ic = reqObj.get("itemCondition")
                                val itemStr = if (ic.isJsonPrimitive) ic.asString else if (ic.isJsonObject && ic.asJsonObject.has("item")) ic.asJsonObject.get("item").asString else null
                                if (itemStr != null) {
                                    resolveItemString(itemStr, requiredItems)
                                }
                            }
                            conditions.add("Objet tenu requis")
                        }
                        "time_range" -> {
                            val range = if (reqObj.has("range")) reqObj.get("range").asString else ""
                            val timeDesc = when (range.lowercase()) {
                                "day" -> "Jour"
                                "night" -> "Nuit"
                                "dawn" -> "Aube"
                                "dusk" -> "Crépuscule"
                                else -> range
                            }
                            conditions.add("Période : $timeDesc")
                        }
                        "weather" -> {
                            val isThunder = reqObj.get("isThundering")?.asBoolean == true
                            val isRain = reqObj.get("isRaining")?.asBoolean == true
                            val wDesc = if (isThunder) "Orage" else if (isRain) "Pluie" else "Météo spécifique"
                            conditions.add("Météo : $wDesc")
                        }
                        "has_move" -> {
                            val move = reqObj.get("move")?.asString?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Spécifique"
                            conditions.add("Capacité connue : $move")
                        }
                        "has_move_type" -> {
                            val type = reqObj.get("type")?.asString?.replaceFirstChar { it.uppercase() } ?: ""
                            conditions.add("Capacité de type $type")
                        }
                        "biome" -> {
                            conditions.add("Lieu / Biome spécifique")
                        }
                        "structure" -> {
                            conditions.add("Structure spécifique")
                        }
                        "moon_phase" -> {
                            conditions.add("Phase de lune spécifique")
                        }
                        "stat_compare" -> {
                            conditions.add("Comparaison de statistiques")
                        }
                        else -> {}
                    }
                }
            }
        }

        // Cas échange : câble de liaison ou échange de joueur
        if (variant == "trade") {
            if (conditions.none { it.startsWith("Échange") }) {
                conditions.add("Échange ou Câble de liaison")
            }
            if (requiredItems.isEmpty() && conditions.none { it.startsWith("Échange contre") }) {
                try {
                    requiredItems.add(ItemStack(CobblemonItems.LINK_CABLE))
                } catch (_: Throwable) {}
            }
        }

        // Résumé textuel principal
        val summaryParts = mutableListOf<String>()
        if (minLevel != null && minLevel > 1) {
            summaryParts.add("Niveau ≥ $minLevel")
        }
        val distinctItems = requiredItems.distinctBy { it.item }
        if (distinctItems.isNotEmpty()) {
            val itemNames = distinctItems.map { it.hoverName.string }.distinct()
            summaryParts.add(itemNames.joinToString(", "))
        }
        if (summaryParts.isEmpty()) {
            if (conditions.isNotEmpty()) {
                summaryParts.add(conditions.first())
            } else {
                summaryParts.add(variantLabel)
            }
        }

        val fullSummary = summaryParts.joinToString(" + ")

        return PokemonEvolutionRecipe(
            sourceSpecies = sourceSpecies,
            sourceForm = sourceForm,
            targetSpecies = targetSpecies,
            targetForm = targetForm,
            requiredItems = distinctItems,
            evolutionVariant = variantLabel,
            requirementsSummary = fullSummary,
            additionalConditions = conditions
        )
    }

    private fun loadFromInMemorySpecies(
        recipes: MutableList<PokemonEvolutionRecipe>,
        seenKeys: MutableSet<String>
    ) {
        try {
            val speciesToProcess = mutableSetOf<Species>()
            speciesToProcess.addAll(PokemonSpecies.species)
            speciesToProcess.addAll(localSpeciesById.values)

            for (species in speciesToProcess) {
                val formsToProcess = mutableListOf(species.standardForm)
                formsToProcess.addAll(species.forms.filter { it != species.standardForm })

                for (form in formsToProcess) {
                    val evolutions = if (form == species.standardForm) {
                        if (form.evolutions.isNotEmpty()) form.evolutions else species.evolutions
                    } else {
                        if (form.evolutions.isNotEmpty()) form.evolutions else species.evolutions
                    }

                    for (evo in evolutions) {
                        try {
                            val recipe = processInMemoryEvolution(species, form, evo)
                            if (recipe != null) {
                                val key = "${recipe.sourceSpecies.name}:${recipe.sourceForm.name}->${recipe.targetSpecies.name}:${recipe.targetForm.name}:${recipe.requirementsSummary}"
                                if (seenKeys.add(key)) {
                                    recipes.add(recipe)
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Scan espèces en mémoire exception: {}", e.message)
        }
    }

    private fun processInMemoryEvolution(
        sourceSpecies: Species,
        sourceForm: FormData,
        evolution: Evolution
    ): PokemonEvolutionRecipe? {
        val resultProps = evolution.result
        val targetSpeciesName = resultProps.species ?: return null
        val targetSpecies = findSpeciesByName(targetSpeciesName) ?: return null

        val targetFormName = resultProps.form
        val targetAspects = resultProps.aspects
        val targetForm = targetSpecies.forms.firstOrNull { f ->
            if (targetFormName != null && f.name.equals(targetFormName, ignoreCase = true)) true
            else if (targetAspects.isNotEmpty() && f.aspects.any { it.equals(targetAspects.first(), ignoreCase = true) }) true
            else false
        } ?: targetSpecies.standardForm

        val requiredItems = mutableListOf<ItemStack>()
        val conditions = mutableListOf<String>()
        var variantLabel = "Évolution"

        when (evolution) {
            is ItemInteractionEvolution -> {
                variantLabel = "Utilisation d'un objet"
                val itemPredicate = evolution.requiredContext
                val itemsOpt = itemPredicate?.items()
                if (itemsOpt != null && itemsOpt.isPresent) {
                    val holderSet = itemsOpt.get()
                    holderSet.forEach { holder ->
                        requiredItems.add(ItemStack(holder.value()))
                    }
                    holderSet.unwrapKey().ifPresent { tagKey ->
                        BuiltInRegistries.ITEM.getTag(tagKey).ifPresent { tagHolders ->
                            tagHolders.forEach { holder ->
                                requiredItems.add(ItemStack(holder.value()))
                            }
                        }
                    }
                }
            }
            is TradeEvolution -> {
                variantLabel = "Échange"
                conditions.add("Échange ou Câble de liaison")
                if (requiredItems.isEmpty()) {
                    try {
                        requiredItems.add(ItemStack(CobblemonItems.LINK_CABLE))
                    } catch (_: Throwable) {}
                }
            }
            is LevelUpEvolution -> {
                variantLabel = "Montée de niveau"
            }
            else -> {
                variantLabel = "Spécial"
            }
        }

        var minLevel: Int? = null
        for (req in evolution.requirements) {
            when (req) {
                is LevelRequirement -> {
                    minLevel = req.minLevel
                }
                is FriendshipRequirement -> {
                    conditions.add("Bonheur ≥ ${req.amount}")
                }
                is HeldItemRequirement -> {
                    val heldOpt = req.itemCondition?.items()
                    if (heldOpt != null && heldOpt.isPresent) {
                        val holderSet = heldOpt.get()
                        holderSet.forEach { h ->
                            requiredItems.add(ItemStack(h.value()))
                        }
                        holderSet.unwrapKey().ifPresent { tagKey ->
                            BuiltInRegistries.ITEM.getTag(tagKey).ifPresent { tagHolders ->
                                tagHolders.forEach { holder ->
                                    requiredItems.add(ItemStack(holder.value()))
                                }
                            }
                        }
                    }
                    conditions.add("Objet tenu requis")
                }
                is TimeRangeRequirement -> {
                    val rawStr = req.range?.toString()?.lowercase() ?: ""
                    val timeDesc = when {
                        rawStr.contains("day") -> "Jour"
                        rawStr.contains("night") -> "Nuit"
                        rawStr.contains("dawn") -> "Aube"
                        rawStr.contains("dusk") -> "Crépuscule"
                        else -> "Période spécifique"
                    }
                    conditions.add("Période : $timeDesc")
                }
                is WeatherRequirement -> {
                    val wName = when {
                        req.isThundering == true -> "Orage"
                        req.isRaining == true -> "Pluie"
                        else -> "Météo spécifique"
                    }
                    conditions.add("Météo : $wName")
                }
                is MoveSetRequirement -> {
                    val moveName = req.move?.displayName?.string ?: "Spécifique"
                    conditions.add("Capacité connue : $moveName")
                }
                is BiomeRequirement -> {
                    conditions.add("Lieu / Biome spécifique")
                }
                else -> {}
            }
        }

        val summaryParts = mutableListOf<String>()
        if (minLevel != null && minLevel > 1) {
            summaryParts.add("Niveau ≥ $minLevel")
        }

        val distinctItems = requiredItems.distinctBy { it.item }
        if (distinctItems.isNotEmpty()) {
            val itemNames = distinctItems.map { it.hoverName.string }.distinct()
            summaryParts.add(itemNames.joinToString(", "))
        }

        if (summaryParts.isEmpty()) {
            if (conditions.isNotEmpty()) {
                summaryParts.add(conditions.first())
            } else {
                summaryParts.add(variantLabel)
            }
        }

        val fullSummary = summaryParts.joinToString(" + ")

        return PokemonEvolutionRecipe(
            sourceSpecies = sourceSpecies,
            sourceForm = sourceForm,
            targetSpecies = targetSpecies,
            targetForm = targetForm,
            requiredItems = distinctItems,
            evolutionVariant = variantLabel,
            requirementsSummary = fullSummary,
            additionalConditions = conditions
        )
    }

    private fun resolveItemString(itemStr: String, output: MutableList<ItemStack>) {
        try {
            if (itemStr.startsWith("#")) {
                val tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(itemStr.removePrefix("#")))
                BuiltInRegistries.ITEM.getTag(tagKey).ifPresent { tagHolders ->
                    tagHolders.forEach { output.add(ItemStack(it.value())) }
                }
            } else {
                val loc = ResourceLocation.parse(itemStr)
                BuiltInRegistries.ITEM.getOptional(loc).ifPresent { item ->
                    output.add(ItemStack(item))
                }
            }
        } catch (_: Throwable) {}
    }
}
