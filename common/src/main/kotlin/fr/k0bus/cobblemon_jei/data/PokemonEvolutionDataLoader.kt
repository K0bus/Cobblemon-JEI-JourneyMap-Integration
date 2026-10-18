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
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.pokemon.Gender
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
                                val key = getRecipeDeduplicationKey(recipe)
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
                                            val key = getRecipeDeduplicationKey(recipe)
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

    private fun getRecipeDeduplicationKey(recipe: PokemonEvolutionRecipe): String {
        val src = try { recipe.sourceSpecies.resourceIdentifier.path.lowercase() } catch (_: Throwable) { recipe.sourceSpecies.name.lowercase() }
        val srcForm = recipe.sourceForm.name.lowercase()
        val tgt = try { recipe.targetSpecies.resourceIdentifier.path.lowercase() } catch (_: Throwable) { recipe.targetSpecies.name.lowercase() }
        val tgtForm = recipe.targetForm.name.lowercase()
        val items = recipe.requiredItems.mapNotNull {
            try { BuiltInRegistries.ITEM.getKey(it.item).toString() } catch (_: Throwable) { null }
        }.sorted().joinToString(",")
        return "$src:$srcForm->$tgt:$tgtForm:g=${recipe.requiredGender}:m=${recipe.requiredMove}:mt=${recipe.requiredMoveType}:l=${recipe.minLevel}:f=${recipe.minFriendship}:s=${recipe.stepsAmount}:u=${recipe.useMoveCount}:items=$items"
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

        var requiredGender: Gender? = null
        var requiredMove: String? = null
        var requiredMoveType: String? = null
        var useMoveCount: Int? = null
        var stepsAmount: Int? = null
        var criticalHits: Int? = null
        var damageTaken: Int? = null
        var recoilDamage: Int? = null
        var defeatTarget: String? = null
        var defeatAmount: Int? = null
        var partyMember: String? = null
        var statCompare: String? = null
        var moonPhase: String? = null
        var minLevel: Int? = null
        var minFriendship: Int? = null

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
                            minFriendship = amt
                            conditions.add("Bonheur ≥ $amt")
                        }
                        "properties" -> {
                            val target = if (reqObj.has("target")) reqObj.get("target").asString else ""
                            if (target.contains("gender=female", ignoreCase = true)) {
                                requiredGender = Gender.FEMALE
                                conditions.add("Sexe requis : Femelle ♀")
                            } else if (target.contains("gender=male", ignoreCase = true)) {
                                requiredGender = Gender.MALE
                                conditions.add("Sexe requis : Mâle ♂")
                            } else if (target.contains("nature=", ignoreCase = true)) {
                                val nat = target.substringAfter("nature=").substringBefore(" ")
                                conditions.add("Nature : $nat")
                            } else if (target.contains("gimmighoul_coins=", ignoreCase = true)) {
                                val coins = target.substringAfter("gimmighoul_coins=").substringBefore(" ")
                                conditions.add("Pièces Gimmighoul : $coins")
                            } else {
                                conditions.add("Propriété : $target")
                            }
                        }
                        "has_move" -> {
                            val mv = reqObj.get("move")?.asString?.lowercase() ?: ""
                            requiredMove = mv
                            val moveName = Moves.getByName(mv)?.displayName?.string ?: mv.replace("_", " ").replaceFirstChar { it.uppercase() }
                            conditions.add("Capacité connue : $moveName")
                        }
                        "has_move_type" -> {
                            val type = reqObj.get("type")?.asString?.lowercase() ?: ""
                            requiredMoveType = type
                            conditions.add("Capacité de type : ${type.replaceFirstChar { it.uppercase() }}")
                        }
                        "use_move" -> {
                            val mv = reqObj.get("move")?.asString?.lowercase() ?: ""
                            val count = if (reqObj.has("amount")) reqObj.get("amount").asInt else 20
                            requiredMove = mv
                            useMoveCount = count
                            val moveName = Moves.getByName(mv)?.displayName?.string ?: mv.replace("_", " ").replaceFirstChar { it.uppercase() }
                            conditions.add("Utiliser $moveName $count fois")
                        }
                        "blocks_traveled" -> {
                            val steps = if (reqObj.has("amount")) reqObj.get("amount").asInt else 1000
                            stepsAmount = steps
                            conditions.add("Marcher $steps pas")
                        }
                        "battle_critical_hits" -> {
                            val hits = if (reqObj.has("amount")) reqObj.get("amount").asInt else 3
                            criticalHits = hits
                            conditions.add("Infliger $hits coups critiques en 1 combat")
                        }
                        "damage_taken" -> {
                            val dmg = if (reqObj.has("amount")) reqObj.get("amount").asInt else 49
                            damageTaken = dmg
                            conditions.add("Subir ≥ $dmg dégâts sans tomber K.O.")
                        }
                        "recoil" -> {
                            val rec = if (reqObj.has("amount")) reqObj.get("amount").asInt else 294
                            recoilDamage = rec
                            conditions.add("Subir ≥ $rec dégâts de recul")
                        }
                        "defeat" -> {
                            val amt = if (reqObj.has("amount")) reqObj.get("amount").asInt else 3
                            val target = if (reqObj.has("target")) reqObj.get("target").asString else "Adversaires"
                            defeatAmount = amt
                            defeatTarget = target
                            conditions.add("Vaincre $amt $target")
                        }
                        "party_member" -> {
                            val target = if (reqObj.has("target")) reqObj.get("target").asString else ""
                            partyMember = target
                            conditions.add("Dans l'équipe : $target")
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
                        "biome" -> {
                            conditions.add("Lieu / Biome spécifique")
                        }
                        "structure" -> {
                            conditions.add("Structure spécifique")
                        }
                        "moon_phase" -> {
                            val phase = if (reqObj.has("moonPhase")) reqObj.get("moonPhase").asString else ""
                            moonPhase = phase
                            conditions.add("Phase de lune : $phase")
                        }
                        "stat_compare" -> {
                            val high = if (reqObj.has("highStat")) reqObj.get("highStat").asString else ""
                            val low = if (reqObj.has("lowStat")) reqObj.get("lowStat").asString else ""
                            statCompare = "$high > $low"
                            conditions.add("Statistique : $high > $low")
                        }
                        "stat_equal" -> {
                            val s1 = if (reqObj.has("statOne")) reqObj.get("statOne").asString else ""
                            val s2 = if (reqObj.has("statTwo")) reqObj.get("statTwo").asString else ""
                            statCompare = "$s1 = $s2"
                            conditions.add("Statistique : $s1 = $s2")
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
        if (requiredGender != null) {
            summaryParts.add(if (requiredGender == Gender.FEMALE) "Femelle ♀" else "Mâle ♂")
        }
        if (minLevel != null && minLevel > 1) {
            summaryParts.add("Niveau ≥ $minLevel")
        }
        val distinctItems = requiredItems.distinctBy { it.item }
        if (distinctItems.isNotEmpty()) {
            val itemNames = distinctItems.map { it.hoverName.string }.distinct()
            summaryParts.add(itemNames.joinToString(", "))
        }
        if (stepsAmount != null) {
            summaryParts.add("$stepsAmount pas")
        }
        if (useMoveCount != null && requiredMove != null) {
            val mvName = Moves.getByName(requiredMove)?.displayName?.string ?: requiredMove
            summaryParts.add("$mvName ($useMoveCount x)")
        } else if (requiredMove != null) {
            val mvName = Moves.getByName(requiredMove)?.displayName?.string ?: requiredMove
            summaryParts.add(mvName)
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
            additionalConditions = conditions,
            requiredGender = requiredGender,
            requiredMove = requiredMove,
            requiredMoveType = requiredMoveType,
            useMoveCount = useMoveCount,
            stepsAmount = stepsAmount,
            criticalHits = criticalHits,
            damageTaken = damageTaken,
            recoilDamage = recoilDamage,
            defeatTarget = defeatTarget,
            defeatAmount = defeatAmount,
            partyMember = partyMember,
            statCompare = statCompare,
            moonPhase = moonPhase,
            minLevel = minLevel,
            minFriendship = minFriendship
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
                                val key = getRecipeDeduplicationKey(recipe)
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

        var requiredGender: Gender? = null
        var requiredMove: String? = null
        var requiredMoveType: String? = null
        var useMoveCount: Int? = null
        var stepsAmount: Int? = null
        var criticalHits: Int? = null
        var damageTaken: Int? = null
        var recoilDamage: Int? = null
        var defeatTarget: String? = null
        var defeatAmount: Int? = null
        var partyMember: String? = null
        var statCompare: String? = null
        var moonPhase: String? = null
        var minLevel: Int? = null
        var minFriendship: Int? = null

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

        for (req in evolution.requirements) {
            when (req) {
                is LevelRequirement -> {
                    minLevel = req.minLevel
                }
                is FriendshipRequirement -> {
                    minFriendship = req.amount
                    conditions.add("Bonheur ≥ ${req.amount}")
                }
                is PokemonPropertiesRequirement -> {
                    val targetProps = req.target
                    val g = targetProps.gender
                    if (g != null) {
                        requiredGender = g
                        conditions.add(if (g == Gender.FEMALE) "Sexe requis : Femelle ♀" else "Sexe requis : Mâle ♂")
                    }
                    val orig = targetProps.originalString
                    if (!orig.isNullOrBlank()) {
                        if (orig.contains("gender=female", true)) {
                            requiredGender = Gender.FEMALE
                            if (conditions.none { it.contains("Femelle") }) conditions.add("Sexe requis : Femelle ♀")
                        } else if (orig.contains("gender=male", true)) {
                            requiredGender = Gender.MALE
                            if (conditions.none { it.contains("Mâle") }) conditions.add("Sexe requis : Mâle ♂")
                        } else if (orig.contains("nature=", true)) {
                            val nat = orig.substringAfter("nature=").substringBefore(" ")
                            conditions.add("Nature : $nat")
                        }
                    }
                }
                is MoveSetRequirement -> {
                    val mv = req.move
                    requiredMove = mv.name.lowercase()
                    val moveName = mv.displayName.string
                    conditions.add("Capacité connue : $moveName")
                }
                is MoveTypeRequirement -> {
                    val t = req.type.name.lowercase()
                    requiredMoveType = t
                    conditions.add("Capacité de type : ${t.replaceFirstChar { it.uppercase() }}")
                }
                is UseMoveRequirement -> {
                    val mv = req.move
                    val count = req.amount
                    requiredMove = mv.name.lowercase()
                    useMoveCount = count
                    val moveName = mv.displayName.string
                    conditions.add("Utiliser $moveName $count fois")
                }
                is BlocksTraveledRequirement -> {
                    val steps = req.amount
                    stepsAmount = steps
                    conditions.add("Marcher $steps pas")
                }
                is BattleCriticalHitsRequirement -> {
                    val hits = req.amount
                    criticalHits = hits
                    conditions.add("Infliger $hits coups critiques en 1 combat")
                }
                is DamageTakenRequirement -> {
                    val dmg = req.amount
                    damageTaken = dmg
                    conditions.add("Subir ≥ $dmg dégâts sans tomber K.O.")
                }
                is RecoilRequirement -> {
                    val rec = req.amount
                    recoilDamage = rec
                    conditions.add("Subir ≥ $rec dégâts de recul")
                }
                is DefeatRequirement -> {
                    val amt = req.amount
                    val target = req.target.originalString
                    defeatAmount = amt
                    defeatTarget = target
                    conditions.add("Vaincre $amt $target")
                }
                is PartyMemberRequirement -> {
                    val target = req.target.originalString
                    partyMember = target
                    conditions.add("Dans l'équipe : $target")
                }
                is StatCompareRequirement -> {
                    statCompare = "Statistiques comparées"
                    conditions.add("Comparaison de statistiques")
                }
                is StatEqualRequirement -> {
                    statCompare = "Statistiques égales"
                    conditions.add("Statistiques égales")
                }
                is MoonPhaseRequirement -> {
                    val phase = req.moonPhase.name
                    moonPhase = phase
                    conditions.add("Phase de lune : $phase")
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
                is BiomeRequirement -> {
                    conditions.add("Lieu / Biome spécifique")
                }
                else -> {}
            }
        }

        val summaryParts = mutableListOf<String>()
        if (requiredGender != null) {
            summaryParts.add(if (requiredGender == Gender.FEMALE) "Femelle ♀" else "Mâle ♂")
        }
        if (minLevel != null && minLevel > 1) {
            summaryParts.add("Niveau ≥ $minLevel")
        }

        val distinctItems = requiredItems.distinctBy { it.item }
        if (distinctItems.isNotEmpty()) {
            val itemNames = distinctItems.map { it.hoverName.string }.distinct()
            summaryParts.add(itemNames.joinToString(", "))
        }
        if (stepsAmount != null) {
            summaryParts.add("$stepsAmount pas")
        }
        if (useMoveCount != null && requiredMove != null) {
            val mvName = Moves.getByName(requiredMove)?.displayName?.string ?: requiredMove
            summaryParts.add("$mvName ($useMoveCount x)")
        } else if (requiredMove != null) {
            val mvName = Moves.getByName(requiredMove)?.displayName?.string ?: requiredMove
            summaryParts.add(mvName)
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
            additionalConditions = conditions,
            requiredGender = requiredGender,
            requiredMove = requiredMove,
            requiredMoveType = requiredMoveType,
            useMoveCount = useMoveCount,
            stepsAmount = stepsAmount,
            criticalHits = criticalHits,
            damageTaken = damageTaken,
            recoilDamage = recoilDamage,
            defeatTarget = defeatTarget,
            defeatAmount = defeatAmount,
            partyMember = partyMember,
            statCompare = statCompare,
            moonPhase = moonPhase,
            minLevel = minLevel,
            minFriendship = minFriendship
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
