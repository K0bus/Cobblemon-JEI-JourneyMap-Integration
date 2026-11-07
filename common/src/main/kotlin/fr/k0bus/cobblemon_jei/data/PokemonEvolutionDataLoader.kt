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
import net.minecraft.network.chat.Component
import fr.k0bus.cobblemon_jei.domain.session.SessionLifecycleListener
import fr.k0bus.cobblemon_jei.domain.session.WorldSessionManager
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
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

    init {
        WorldSessionManager.addListener(object : SessionLifecycleListener {
            override fun onSessionStarted(sessionId: String, initialDimension: ResourceKey<Level>?) {
                clearCache()
            }
            override fun onSessionEnded(sessionId: String) {
                clearCache()
            }
        })
    }

    // Cache local d'espèces pour garantir la résolution même si le serveur n'est pas encore initialisé
    private val localSpeciesById = mutableMapOf<String, Species>()
    private val localSpeciesByName = mutableMapOf<String, Species>()
    private val rawSpeciesJsonMap = mutableMapOf<String, String>()
    private var hasPreloadedSpecies = false
    private var cachedSpeciesCount: Int = 0

    /**
     * S'assure que toutes les définitions d'espèces Cobblemon sont préchargées en mémoire.
     * Cette fonction peut être appelée dès le démarrage de JEI (registerIngredients) pour
     * garantir que [PokemonSpecies.species] n'est pas vide avant qu'un monde ne soit ouvert.
     */
    @Synchronized
    fun ensureSpeciesLoaded() {
        val cobblemonSpecies = try { PokemonSpecies.species.toList() } catch (_: Throwable) { emptyList() }
        if (cobblemonSpecies.isNotEmpty() && cobblemonSpecies.size > localSpeciesById.size) {
            for (sp in cobblemonSpecies) {
                indexSpeciesLocally(sp, sp.resourceIdentifier.path.lowercase())
            }
        }

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
                    try { parsedSpecies.resourceIdentifier = resLoc } catch (_: Throwable) {}
                    val spName = try { parsedSpecies.name } catch (_: Throwable) { "" }
                    if (spName.isNotBlank()) {
                        try {
                            parsedSpecies.initialize()
                        } catch (_: Throwable) {}
                        parsedMap[resLoc] = parsedSpecies
                        indexSpeciesLocally(parsedSpecies, cleanFileId)
                    }
                }
            } catch (e: Throwable) {
                logger.debug("Erreur lecture JSON espèce {}: {}", name, e.message)
            }
        }

        // Si PokemonSpecies est vide (car le client est au menu principal), on injecte nos espèces préchargées
        try {
            if (PokemonSpecies.species.isEmpty() && parsedMap.isNotEmpty()) {
                PokemonSpecies.reload(parsedMap)
                logger.info("Cobblemon JEI: ${parsedMap.size} espèces enregistrées dans PokemonSpecies.reload.")
            }
        } catch (e: Throwable) {
            logger.debug("Cobblemon JEI: PokemonSpecies indisponible: {}", e.message)
        }

        // Indexe également toutes les espèces déjà présentes dans PokemonSpecies
        try {
            for (sp in PokemonSpecies.species) {
                indexSpeciesLocally(sp, sp.resourceIdentifier.path.lowercase())
            }
        } catch (_: Throwable) {}

        if (parsedMap.isNotEmpty() || cobblemonSpecies.isNotEmpty()) {
            hasPreloadedSpecies = true
        }
        logger.info("Cobblemon JEI: Index d'espèces prêt avec ${localSpeciesById.size} entrées uniques.")
    }

    private fun indexSpeciesLocally(sp: Species, fileId: String) {
        val spName = try { sp.name } catch (_: Throwable) { "" }
        if (spName.isBlank()) return
        val path = try { sp.resourceIdentifier.path.lowercase() } catch (_: Throwable) { fileId }
        localSpeciesById[path] = sp
        localSpeciesById[fileId] = sp
        localSpeciesByName[spName.lowercase()] = sp

        val clean = spName.lowercase().replace(" ", "").replace("'", "").replace(".", "").replace("-", "")
        localSpeciesByName[clean] = sp

        try {
            val trans = sp.translatedName.string.lowercase()
            if (trans.isNotBlank()) {
                localSpeciesByName[trans] = sp
            }
        } catch (_: Throwable) {}
    }

    /**
     * Recherche une espèce par son nom ou son identifiant.
     */
    fun findSpeciesByName(name: String): Species? {
        ensureSpeciesLoaded()
        val q = name.trim().lowercase()
        val pathOnly = if (q.contains(":")) q.substringAfter(":") else q

        localSpeciesById[q]?.let { return it }
        localSpeciesById[pathOnly]?.let { return it }
        localSpeciesByName[q]?.let { return it }
        localSpeciesByName[pathOnly]?.let { return it }

        val clean = pathOnly.replace(" ", "").replace("'", "").replace(".", "").replace("-", "")
        localSpeciesByName[clean]?.let { return it }
        localSpeciesById[clean]?.let { return it }

        try {
            PokemonSpecies.getByName(clean)?.let { return it }
        } catch (_: Throwable) {}
        try {
            PokemonSpecies.getByName(pathOnly)?.let { return it }
        } catch (_: Throwable) {}
        try {
            if (ResourceLocation.tryParse(q) != null) {
                PokemonSpecies.getByIdentifier(ResourceLocation.parse(q))?.let { return it }
            }
        } catch (_: Throwable) {}
        try {
            PokemonSpecies.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", clean))?.let { return it }
        } catch (_: Throwable) {}
        return null
    }

    /**
     * Retourne l'ensemble de toutes les espèces connues (officielles Cobblemon et scannées localement).
     */
    fun getAllSpecies(): Collection<Species> {
        ensureSpeciesLoaded()
        val all = LinkedHashSet<Species>()
        try {
            all.addAll(PokemonSpecies.species)
        } catch (_: Throwable) {}
        all.addAll(localSpeciesById.values)
        return all
    }

    @Synchronized
    fun clearCache() {
        cachedEvolutions = null
        cachedSpeciesCount = 0
    }

    /**
     * Charge et retourne l'ensemble des recettes d'évolution de Cobblemon.
     */
    @Synchronized
    fun loadEvolutionRecipes(): List<PokemonEvolutionRecipe> {
        val currentSpeciesCount = try { PokemonSpecies.species.size } catch (_: Throwable) { 0 }
        val cached = cachedEvolutions
        if (cached != null && cached.isNotEmpty() && currentSpeciesCount <= cachedSpeciesCount) {
            return cached
        }

        ensureSpeciesLoaded()

        val recipes = mutableListOf<PokemonEvolutionRecipe>()
        val seenKeys = mutableSetOf<String>()

        logger.info("Cobblemon JEI: Début de l'extraction des recettes d'évolution (espèces en mémoire: {})...", currentSpeciesCount)

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
        if (recipes.isNotEmpty() || currentSpeciesCount > 0) {
            cachedEvolutions = recipes
            cachedSpeciesCount = currentSpeciesCount
        }
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
            "item_interact" -> Component.translatable("gui.cobblemon_jei.evo.variant.item").string
            "trade" -> Component.translatable("gui.cobblemon_jei.evo.variant.trade").string
            "level_up" -> Component.translatable("gui.cobblemon_jei.evo.variant.level_up").string
            else -> Component.translatable("gui.cobblemon_jei.evo.variant.evolution").string
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
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.trade_partner", partnerSpecies.translatedName.string).string)
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
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.friendship", amt).string)
                        }
                        "properties" -> {
                            val target = if (reqObj.has("target")) reqObj.get("target").asString else ""
                            if (target.contains("gender=female", ignoreCase = true)) {
                                requiredGender = Gender.FEMALE
                                conditions.add(Component.translatable("gui.cobblemon_jei.evo.gender_female").string)
                            } else if (target.contains("gender=male", ignoreCase = true)) {
                                requiredGender = Gender.MALE
                                conditions.add(Component.translatable("gui.cobblemon_jei.evo.gender_male").string)
                            } else if (target.contains("nature=", ignoreCase = true)) {
                                val nat = target.substringAfter("nature=").substringBefore(" ")
                                conditions.add(Component.translatable("gui.cobblemon_jei.evo.nature", nat).string)
                            } else if (target.contains("gimmighoul_coins=", ignoreCase = true)) {
                                val coins = target.substringAfter("gimmighoul_coins=").substringBefore(" ")
                                conditions.add(Component.translatable("gui.cobblemon_jei.evo.gimmighoul_coins", coins).string)
                            } else {
                                conditions.add(Component.translatable("gui.cobblemon_jei.evo.property", target).string)
                            }
                        }
                        "has_move" -> {
                            val mv = reqObj.get("move")?.asString?.lowercase() ?: ""
                            requiredMove = mv
                            val moveName = Moves.getByName(mv)?.displayName?.string ?: mv.replace("_", " ").replaceFirstChar { it.uppercase() }
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.known_move", moveName).string)
                        }
                        "has_move_type" -> {
                            val type = reqObj.get("type")?.asString?.lowercase() ?: ""
                            requiredMoveType = type
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.move_type", type.replaceFirstChar { it.uppercase() }).string)
                        }
                        "use_move" -> {
                            val mv = reqObj.get("move")?.asString?.lowercase() ?: ""
                            val count = if (reqObj.has("amount")) reqObj.get("amount").asInt else 20
                            requiredMove = mv
                            useMoveCount = count
                            val moveName = Moves.getByName(mv)?.displayName?.string ?: mv.replace("_", " ").replaceFirstChar { it.uppercase() }
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.use_move", moveName, count).string)
                        }
                        "blocks_traveled" -> {
                            val steps = if (reqObj.has("amount")) reqObj.get("amount").asInt else 1000
                            stepsAmount = steps
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.walk_steps", steps).string)
                        }
                        "battle_critical_hits" -> {
                            val hits = if (reqObj.has("amount")) reqObj.get("amount").asInt else 3
                            criticalHits = hits
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.crit_hits", hits).string)
                        }
                        "damage_taken" -> {
                            val dmg = if (reqObj.has("amount")) reqObj.get("amount").asInt else 49
                            damageTaken = dmg
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.damage_taken", dmg).string)
                        }
                        "recoil" -> {
                            val rec = if (reqObj.has("amount")) reqObj.get("amount").asInt else 294
                            recoilDamage = rec
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.recoil", rec).string)
                        }
                        "defeat" -> {
                            val amt = if (reqObj.has("amount")) reqObj.get("amount").asInt else 3
                            val target = if (reqObj.has("target")) reqObj.get("target").asString else "Adversaires"
                            defeatAmount = amt
                            defeatTarget = target
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.defeat", amt, target).string)
                        }
                        "party_member" -> {
                            val target = if (reqObj.has("target")) reqObj.get("target").asString else ""
                            partyMember = target
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.in_party", target).string)
                        }
                        "held_item" -> {
                            if (reqObj.has("itemCondition")) {
                                val ic = reqObj.get("itemCondition")
                                val itemStr = if (ic.isJsonPrimitive) ic.asString else if (ic.isJsonObject && ic.asJsonObject.has("item")) ic.asJsonObject.get("item").asString else null
                                if (itemStr != null) {
                                    resolveItemString(itemStr, requiredItems)
                                }
                            }
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.held_item").string)
                        }
                        "time_range" -> {
                            val range = if (reqObj.has("range")) reqObj.get("range").asString else ""
                            val timeDesc = when (range.lowercase()) {
                                "day" -> Component.translatable("gui.cobblemon_jei.evo.time_day").string
                                "night" -> Component.translatable("gui.cobblemon_jei.evo.time_night").string
                                "dawn" -> Component.translatable("gui.cobblemon_jei.evo.time_dawn").string
                                "dusk" -> Component.translatable("gui.cobblemon_jei.evo.time_dusk").string
                                else -> range
                            }
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.time", timeDesc).string)
                        }
                        "weather" -> {
                            val isThunder = reqObj.get("isThundering")?.asBoolean == true
                            val isRain = reqObj.get("isRaining")?.asBoolean == true
                            val wDesc = if (isThunder) Component.translatable("gui.cobblemon_jei.evo.weather_thunder").string else if (isRain) Component.translatable("gui.cobblemon_jei.evo.weather_rain").string else Component.translatable("gui.cobblemon_jei.evo.weather_specific").string
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.weather", wDesc).string)
                        }
                        "biome" -> {
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.specific_biome").string)
                        }
                        "structure" -> {
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.specific_structure").string)
                        }
                        "moon_phase" -> {
                            val phase = if (reqObj.has("moonPhase")) reqObj.get("moonPhase").asString else ""
                            moonPhase = phase
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.moon_phase", phase).string)
                        }
                        "stat_compare" -> {
                            val high = if (reqObj.has("highStat")) reqObj.get("highStat").asString else ""
                            val low = if (reqObj.has("lowStat")) reqObj.get("lowStat").asString else ""
                            statCompare = "$high > $low"
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.stat_compare", "$high > $low").string)
                        }
                        "stat_equal" -> {
                            val s1 = if (reqObj.has("statOne")) reqObj.get("statOne").asString else ""
                            val s2 = if (reqObj.has("statTwo")) reqObj.get("statTwo").asString else ""
                            statCompare = "$s1 = $s2"
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.stat_equal", "$s1 = $s2").string)
                        }
                        else -> {}
                    }
                }
            }
        }

        // Cas échange : câble de liaison ou échange de joueur
        if (variant == "trade") {
            if (conditions.none { it.startsWith("Échange") }) {
                conditions.add(Component.translatable("gui.cobblemon_jei.evo.trade_or_cable").string)
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
            summaryParts.add(if (requiredGender == Gender.FEMALE) Component.translatable("gui.cobblemon_jei.evo.gender_female").string else Component.translatable("gui.cobblemon_jei.evo.gender_male").string)
        }
        if (minLevel != null && minLevel > 1) {
            summaryParts.add(Component.translatable("gui.cobblemon_jei.evo.level", minLevel).string)
        }
        val distinctItems = requiredItems.distinctBy { it.item }
        if (distinctItems.isNotEmpty()) {
            val itemNames = distinctItems.map { it.hoverName.string }.distinct()
            summaryParts.add(itemNames.joinToString(", "))
        }
        if (stepsAmount != null) {
            summaryParts.add(Component.translatable("gui.cobblemon_jei.evo.walk_steps", stepsAmount).string)
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
                        form.evolutions
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
        val targetSpeciesName = resultProps.species ?: resultProps.originalString?.split(" ")?.firstOrNull() ?: return null
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
                conditions.add(Component.translatable("gui.cobblemon_jei.evo.trade_or_cable").string)
                if (requiredItems.isEmpty()) {
                    try {
                        requiredItems.add(ItemStack(CobblemonItems.LINK_CABLE))
                    } catch (_: Throwable) {}
                }
            }
            is LevelUpEvolution -> {
                variantLabel = Component.translatable("gui.cobblemon_jei.evo.variant.level_up").string
            }
            else -> {
                variantLabel = Component.translatable("gui.cobblemon_jei.evo.variant.special").string
            }
        }

        for (req in evolution.requirements) {
            when (req) {
                is LevelRequirement -> {
                    minLevel = req.minLevel
                }
                is FriendshipRequirement -> {
                    minFriendship = req.amount
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.friendship", req.amount).string)
                }
                is PokemonPropertiesRequirement -> {
                    val targetProps = req.target
                    val g = targetProps.gender
                    if (g != null) {
                        requiredGender = g
                        conditions.add(if (g == Gender.FEMALE) Component.translatable("gui.cobblemon_jei.evo.gender_female").string else Component.translatable("gui.cobblemon_jei.evo.gender_male").string)
                    }
                    val orig = targetProps.originalString
                    if (!orig.isNullOrBlank()) {
                        if (orig.contains("gender=female", true)) {
                            requiredGender = Gender.FEMALE
                            if (conditions.none { it.contains("Femelle") }) conditions.add(Component.translatable("gui.cobblemon_jei.evo.gender_female").string)
                        } else if (orig.contains("gender=male", true)) {
                            requiredGender = Gender.MALE
                            if (conditions.none { it.contains("Mâle") }) conditions.add(Component.translatable("gui.cobblemon_jei.evo.gender_male").string)
                        } else if (orig.contains("nature=", true)) {
                            val nat = orig.substringAfter("nature=").substringBefore(" ")
                            conditions.add(Component.translatable("gui.cobblemon_jei.evo.nature", nat).string)
                        }
                    }
                }
                is MoveSetRequirement -> {
                    val mv = req.move
                    requiredMove = mv.name.lowercase()
                    val moveName = mv.displayName.string
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.known_move", moveName).string)
                }
                is MoveTypeRequirement -> {
                    val t = req.type.name.lowercase()
                    requiredMoveType = t
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.move_type", t.replaceFirstChar { it.uppercase() }).string)
                }
                is UseMoveRequirement -> {
                    val mv = req.move
                    val count = req.amount
                    requiredMove = mv.name.lowercase()
                    useMoveCount = count
                    val moveName = mv.displayName.string
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.use_move", moveName, count).string)
                }
                is BlocksTraveledRequirement -> {
                    val steps = req.amount
                    stepsAmount = steps
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.walk_steps", steps).string)
                }
                is BattleCriticalHitsRequirement -> {
                    val hits = req.amount
                    criticalHits = hits
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.crit_hits", hits).string)
                }
                is DamageTakenRequirement -> {
                    val dmg = req.amount
                    damageTaken = dmg
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.damage_taken", dmg).string)
                }
                is RecoilRequirement -> {
                    val rec = req.amount
                    recoilDamage = rec
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.recoil", rec).string)
                }
                is DefeatRequirement -> {
                    val amt = req.amount
                    val target = req.target.originalString
                    defeatAmount = amt
                    defeatTarget = target
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.defeat", amt, target).string)
                }
                is PartyMemberRequirement -> {
                    val target = req.target.originalString
                    partyMember = target
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.in_party", target).string)
                }
                is StatCompareRequirement -> {
                    statCompare = Component.translatable("gui.cobblemon_jei.evo.stat_compare", "").string
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.stat_compare", "").string)
                }
                is StatEqualRequirement -> {
                    statCompare = Component.translatable("gui.cobblemon_jei.evo.stat_equal", "").string
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.stat_equal", "").string)
                }
                is MoonPhaseRequirement -> {
                    val phase = req.moonPhase.name
                    moonPhase = phase
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.moon_phase", phase).string)
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
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.held_item").string)
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
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.time", timeDesc).string)
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
                    conditions.add(Component.translatable("gui.cobblemon_jei.evo.specific_biome").string)
                }
                else -> {}
            }
        }

        val summaryParts = mutableListOf<String>()
        if (requiredGender != null) {
            summaryParts.add(if (requiredGender == Gender.FEMALE) Component.translatable("gui.cobblemon_jei.evo.gender_female").string else Component.translatable("gui.cobblemon_jei.evo.gender_male").string)
        }
        if (minLevel != null && minLevel > 1) {
            summaryParts.add(Component.translatable("gui.cobblemon_jei.evo.level", minLevel).string)
        }

        val distinctItems = requiredItems.distinctBy { it.item }
        if (distinctItems.isNotEmpty()) {
            val itemNames = distinctItems.map { it.hoverName.string }.distinct()
            summaryParts.add(itemNames.joinToString(", "))
        }
        if (stepsAmount != null) {
            summaryParts.add(Component.translatable("gui.cobblemon_jei.evo.walk_steps", stepsAmount).string)
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
