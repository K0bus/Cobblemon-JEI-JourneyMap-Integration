package fr.k0bus.cobblemon_jei.data

import fr.k0bus.cobblemon_jei.recipe.PokemonSpawnRecipe
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.util.zip.ZipFile

/**
 * Service d'extraction, parsing et agrégation des données d'apparition (spawns) de Cobblemon.
 * Lit les fichiers JSON issus de [spawn_pool_world] depuis les JARs des mods,
 * les datapacks et le serveur local, puis indexe les conditions pour JEI et JourneyMap.
 */
object PokemonSpawnDataLoader {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/SpawnLoader")
    private var cachedRecipes: List<PokemonSpawnRecipe>? = null

    /**
     * Charge et retourne l'ensemble des recettes d'apparition consolidées.
     */
    fun loadSpawnRecipes(): List<PokemonSpawnRecipe> {
        cachedRecipes?.let { return it }

        PokemonEvolutionDataLoader.ensureSpeciesLoaded()

        val rawRecipes = mutableListOf<RawSpawnEntry>()

        logger.info("Cobblemon JEI: Début du chargement des données de spawn.")

        CobblemonDataScanner.scanDataResources("spawn_pool_world") { name, stream ->
            parseJsonStream(stream, name, rawRecipes)
        }

        logger.info("Cobblemon JEI: ${rawRecipes.size} règles brutes de spawn extraites.")

        // Agrégation des règles similaires par Pokémon et conditions communes
        val aggregatedRecipes = aggregateRawEntries(rawRecipes)

        // Ajout des fiches explicatives pour les Pokémon sans aucun spawn sauvage
        val finalRecipes = addMissingNoSpawnSpecies(aggregatedRecipes)

        cachedRecipes = finalRecipes
        logger.info("Cobblemon JEI: ${finalRecipes.size} fiches d'apparition prêtes.")
        return finalRecipes
    }

    /**
     * Recherche les fiches de spawn pour une espèce donnée (et forme optionnelle).
     */
    fun getSpawnRecipesForSpecies(species: Species, form: FormData? = null): List<PokemonSpawnRecipe> {
        val all = loadSpawnRecipes()
        return all.filter { recipe ->
            recipe.species.name.equals(species.name, ignoreCase = true) &&
                    (form == null || recipe.form.name.equals(form.name, ignoreCase = true))
        }
    }

    /**
     * Extrait les règles de spawn depuis le JAR d'une classe de référence.
     */
    private fun loadFromCodeSource(clazz: Class<*>, output: MutableList<RawSpawnEntry>, loadedSources: MutableSet<String>) {
        try {
            val location = clazz.protectionDomain?.codeSource?.location ?: return
            val uri = location.toURI()
            val file = File(uri)
            if (file.exists()) {
                if (file.isFile && (file.name.endsWith(".jar") || file.name.endsWith(".zip"))) {
                    scanZipFile(file, output, loadedSources)
                } else if (file.isDirectory) {
                    scanDirectory(file, output, loadedSources)
                }
            }
        } catch (e: Exception) {
            logger.debug("Impossible d'extraire les spawns via CodeSource: {}", e.message)
        }
    }

    /**
     * Parcourt le dossier mods/ du jeu pour trouver tout fichier jar contenant des spawns.
     */
    private fun loadFromModsDirectory(output: MutableList<RawSpawnEntry>, loadedSources: MutableSet<String>) {
        try {
            val gameDir = Minecraft.getInstance().gameDirectory
            val modsDir = File(gameDir, "mods")
            if (modsDir.exists() && modsDir.isDirectory) {
                val jarFiles = modsDir.listFiles { f -> f.isFile && f.name.endsWith(".jar") } ?: return
                for (jar in jarFiles) {
                    scanZipFile(jar, output, loadedSources)
                }
            }
        } catch (e: Exception) {
            logger.debug("Impossible de scanner le dossier mods: {}", e.message)
        }
    }

    /**
     * Extrait les fichiers depuis un fichier Zip/Jar.
     */
    private fun scanZipFile(jarFile: File, output: MutableList<RawSpawnEntry>, loadedSources: MutableSet<String>) {
        try {
            ZipFile(jarFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (!entry.isDirectory && name.contains("spawn_pool_world/") && name.endsWith(".json")) {
                        if (loadedSources.add(name)) {
                            zip.getInputStream(entry).use { stream ->
                                parseJsonStream(stream, name, output)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Erreur lors de la lecture du jar {}: {}", jarFile.name, e.message)
        }
    }

    /**
     * Parcourt un dossier de développement local (si décompressé).
     */
    private fun scanDirectory(dir: File, output: MutableList<RawSpawnEntry>, loadedSources: MutableSet<String>) {
        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile && file.name.endsWith(".json")) {
                    val relPath = file.relativeTo(dir).path.replace('\\', '/')
                    if (relPath.contains("spawn_pool_world/")) {
                        if (loadedSources.add(relPath)) {
                            file.inputStream().use { stream ->
                                parseJsonStream(stream, relPath, output)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Erreur lors du scan du dossier {}: {}", dir.name, e.message)
        }
    }

    /**
     * Tente de lire depuis le ResourceManager du serveur solo si disponible.
     */
    private fun loadFromServerResourceManager(output: MutableList<RawSpawnEntry>, loadedSources: MutableSet<String>) {
        try {
            val server = Minecraft.getInstance().singleplayerServer ?: return
            val resManager = server.resourceManager
            val resources = resManager.listResources("spawn_pool_world") { loc -> loc.path.endsWith(".json") }
            for ((loc, res) in resources) {
                if (loadedSources.add(loc.toString())) {
                    res.open().use { stream ->
                        parseJsonStream(stream, loc.toString(), output)
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Tente de lire depuis le ResourceManager client.
     */
    private fun loadFromClientResourceManager(output: MutableList<RawSpawnEntry>, loadedSources: MutableSet<String>) {
        try {
            val resManager = Minecraft.getInstance().resourceManager
            val resources = resManager.listResources("spawn_pool_world") { loc -> loc.path.endsWith(".json") }
            for ((loc, res) in resources) {
                if (loadedSources.add(loc.toString())) {
                    res.open().use { stream ->
                        parseJsonStream(stream, loc.toString(), output)
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun parseJsonStream(stream: InputStream, identifier: String, output: MutableList<RawSpawnEntry>) {
        try {
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                val json = JsonParser.parseReader(reader).asJsonObject
                parseSpawnPoolFile(json, identifier, output)
            }
        } catch (e: Exception) {
            logger.debug("Erreur parsing spawn pool {}: {}", identifier, e.message)
        }
    }

    internal fun parseSpawnPoolFile(json: JsonObject, sourceId: String, output: MutableList<RawSpawnEntry>) {
        val enabled = json.get("enabled")?.asBoolean ?: true
        if (!enabled) return

        val spawnsArray = json.getAsJsonArray("spawns") ?: return
        for (element in spawnsArray) {
            if (!element.isJsonObject) continue
            val entryObj = element.asJsonObject

            val bucket = entryObj.get("bucket")?.asString ?: "common"
            val weight = entryObj.get("weight")?.asFloat ?: 10.0f
            val levelStr = entryObj.get("level")?.asString ?: entryObj.get("levelRange")?.asString ?: "1-100"
            val (minLvl, maxLvl) = parseLevelRange(levelStr)

            val context = entryObj.get("spawnablePositionType")?.asString
                ?: entryObj.get("context")?.asString
                ?: "grounded"

            val condObj = entryObj.getAsJsonObject("condition")
            val biomes = parseStringList(condObj, "biomes")
            val rawDimensions = parseStringList(condObj, "dimensions")
            val dimensions = inferDimensions(rawDimensions, biomes)
            val timeRange = condObj?.get("timeRange")?.asString ?: "any"

            val isRaining = condObj?.get("isRaining")?.asBoolean
            val isThundering = condObj?.get("isThundering")?.asBoolean
            val weather = when {
                isThundering == true -> "thundering"
                isRaining == true -> "rain"
                isRaining == false -> "clear"
                else -> "any"
            }

            val canSeeSky = condObj?.get("canSeeSky")?.asBoolean
            val minSkyLight = condObj?.get("minSkyLight")?.asInt
            val maxSkyLight = condObj?.get("maxSkyLight")?.asInt
            val minY = condObj?.get("minY")?.asInt
            val maxY = condObj?.get("maxY")?.asInt
            val neededBaseBlocks = parseStringList(condObj, "neededBaseBlocks")
            val neededNearbyBlocks = parseStringList(condObj, "neededNearbyBlocks")
            val minLight = condObj?.get("minLight")?.asInt
            val maxLight = condObj?.get("maxLight")?.asInt
            val moonPhase = condObj?.get("moonPhase")?.asInt
            val isSlimeChunk = condObj?.get("isSlimeChunk")?.asBoolean
            val minLureLevel = condObj?.get("minLureLevel")?.asInt
            val maxLureLevel = condObj?.get("maxLureLevel")?.asInt
            val rodType = condObj?.get("rodType")?.asString
            val bait = condObj?.get("bait")?.asString
            val structures = parseStringList(condObj, "structures")

            val antiCondObj = entryObj.getAsJsonObject("anticondition")
            val antiBiomes = parseStringList(antiCondObj, "biomes")
            val antiStructures = parseStringList(antiCondObj, "structures")

            // Cas 1 : Spawns standard de type "pokemon"
            val pokemonSpec = entryObj.get("pokemon")?.asString
            if (pokemonSpec != null) {
                addSpawnEntry(
                    pokemonSpec = pokemonSpec,
                    bucket = bucket,
                    weight = weight,
                    minLvl = minLvl,
                    maxLvl = maxLvl,
                    context = context,
                    biomes = biomes,
                    antiBiomes = antiBiomes,
                    dimensions = dimensions,
                    timeRange = timeRange,
                    weather = weather,
                    canSeeSky = canSeeSky,
                    minSkyLight = minSkyLight,
                    maxSkyLight = maxSkyLight,
                    minY = minY,
                    maxY = maxY,
                    neededBaseBlocks = neededBaseBlocks,
                    neededNearbyBlocks = neededNearbyBlocks,
                    minLight = minLight,
                    maxLight = maxLight,
                    moonPhase = moonPhase,
                    isSlimeChunk = isSlimeChunk,
                    minLureLevel = minLureLevel,
                    maxLureLevel = maxLureLevel,
                    rodType = rodType,
                    bait = bait,
                    structures = structures,
                    antiStructures = antiStructures,
                    output = output
                )
            }

            // Cas 2 : Spawns de meutes / troupeaux ("herdablePokemon")
            val herdArray = entryObj.getAsJsonArray("herdablePokemon")
            if (herdArray != null) {
                for (herdElem in herdArray) {
                    if (!herdElem.isJsonObject) continue
                    val herdObj = herdElem.asJsonObject
                    val herdPokemon = herdObj.get("pokemon")?.asString ?: continue
                    val herdLevelStr = herdObj.get("levelRange")?.asString ?: levelStr
                    val (hMin, hMax) = parseLevelRange(herdLevelStr)
                    val herdWeight = herdObj.get("weight")?.asFloat ?: weight

                    addSpawnEntry(
                        pokemonSpec = herdPokemon,
                        bucket = bucket,
                        weight = herdWeight,
                        minLvl = hMin,
                        maxLvl = hMax,
                        context = context,
                        biomes = biomes,
                        antiBiomes = antiBiomes,
                        dimensions = dimensions,
                        timeRange = timeRange,
                        weather = weather,
                        canSeeSky = canSeeSky,
                        minSkyLight = minSkyLight,
                        maxSkyLight = maxSkyLight,
                        minY = minY,
                        maxY = maxY,
                        neededBaseBlocks = neededBaseBlocks,
                        neededNearbyBlocks = neededNearbyBlocks,
                        minLight = minLight,
                        maxLight = maxLight,
                        moonPhase = moonPhase,
                        isSlimeChunk = isSlimeChunk,
                        minLureLevel = minLureLevel,
                        maxLureLevel = maxLureLevel,
                        rodType = rodType,
                        bait = bait,
                        structures = structures,
                        antiStructures = antiStructures,
                        output = output
                    )
                }
            }
        }
    }

    private fun addSpawnEntry(
        pokemonSpec: String,
        bucket: String,
        weight: Float,
        minLvl: Int,
        maxLvl: Int,
        context: String,
        biomes: Set<String>,
        antiBiomes: Set<String>,
        dimensions: Set<String>,
        timeRange: String,
        weather: String,
        canSeeSky: Boolean?,
        minSkyLight: Int?,
        maxSkyLight: Int?,
        minY: Int?,
        maxY: Int?,
        neededBaseBlocks: Set<String>,
        neededNearbyBlocks: Set<String>,
        minLight: Int?,
        maxLight: Int?,
        moonPhase: Int?,
        isSlimeChunk: Boolean?,
        minLureLevel: Int?,
        maxLureLevel: Int?,
        rodType: String?,
        bait: String?,
        structures: Set<String>,
        antiStructures: Set<String>,
        output: MutableList<RawSpawnEntry>
    ) {
        val (species, form) = resolveSpeciesAndForm(pokemonSpec)
        if (species != null) {
            output.add(
                RawSpawnEntry(
                    species = species,
                    form = form ?: species.standardForm,
                    bucket = bucket,
                    weight = weight,
                    levelMin = minLvl,
                    levelMax = maxLvl,
                    context = context,
                    biomes = biomes,
                    antiBiomes = antiBiomes,
                    dimensions = dimensions,
                    timeRange = timeRange,
                    weather = weather,
                    canSeeSky = canSeeSky,
                    minSkyLight = minSkyLight,
                    maxSkyLight = maxSkyLight,
                    minY = minY,
                    maxY = maxY,
                    neededBaseBlocks = neededBaseBlocks,
                    neededNearbyBlocks = neededNearbyBlocks,
                    minLight = minLight,
                    maxLight = maxLight,
                    moonPhase = moonPhase,
                    isSlimeChunk = isSlimeChunk,
                    minLureLevel = minLureLevel,
                    maxLureLevel = maxLureLevel,
                    rodType = rodType,
                    bait = bait,
                    structures = structures,
                    antiStructures = antiStructures
                )
            )
        }
    }

    /**
     * Résout l'espèce et la forme depuis un pokemon spec string
     * en utilisant le parser Cobblemon [PokemonProperties].
     */
    fun resolveSpeciesAndForm(pokemonSpec: String): Pair<Species?, FormData?> {
        val trimmed = pokemonSpec.trim()
        val parsedProps = try {
            PokemonProperties.Companion.parse(trimmed)
        } catch (_: Throwable) {
            null
        }

        val speciesName = parsedProps?.species
            ?: trimmed.split(" ", "_", "-").firstOrNull()
            ?: return Pair(null, null)

        val species = PokemonSpecies.getByName(speciesName.lowercase())
            ?: PokemonEvolutionDataLoader.findSpeciesByName(speciesName)
            ?: return Pair(null, null)

        // Résolution de la forme spécifique si présente
        val targetFormName = parsedProps?.form
        val targetAspects = parsedProps?.aspects ?: emptySet()

        val matchedForm = species.forms.firstOrNull { form ->
            if (targetFormName != null && form.name.equals(targetFormName, ignoreCase = true)) {
                true
            } else if (targetAspects.isNotEmpty() && form.aspects.any { it.equals(targetAspects.first(), ignoreCase = true) }) {
                true
            } else {
                false
            }
        }

        return Pair(species, matchedForm ?: species.standardForm)
    }

    private fun parseLevelRange(levelStr: String): Pair<Int, Int> {
        val trimmed = levelStr.trim()
        if (trimmed.contains("-")) {
            val parts = trimmed.split("-")
            val min = parts.getOrNull(0)?.toIntOrNull() ?: 1
            val max = parts.getOrNull(1)?.toIntOrNull() ?: min
            return Pair(min, max)
        }
        val single = trimmed.toIntOrNull() ?: 1
        return Pair(single, single)
    }

    private fun parseStringList(parent: JsonObject?, key: String): Set<String> {
        if (parent == null || !parent.has(key)) return emptySet()
        val element = parent.get(key)
        val result = mutableSetOf<String>()
        if (element.isJsonArray) {
            for (item in element.asJsonArray) {
                result.add(item.asString)
            }
        } else if (element.isJsonPrimitive) {
            result.add(element.asString)
        }
        return result
    }

    /**
     * Résout l'ensemble des dimensions cibles d'une règle de spawn.
     * Si la condition ne spécifie pas explicitement "dimensions", on déduit la dimension
     * à partir des balises ou identifiants de biomes (ex: #cobblemon:nether/..., #aether:is_aether).
     */
    fun inferDimensions(explicitDimensions: Set<String>, biomes: Set<String>): Set<String> {
        if (explicitDimensions.isNotEmpty()) {
            return explicitDimensions.map { dim ->
                if (!dim.contains(":")) "minecraft:$dim" else dim
            }.toSet()
        }

        val deduced = mutableSetOf<String>()

        for (biome in biomes) {
            val raw = if (biome.startsWith("#")) biome.substring(1) else biome
            val lower = raw.lowercase()
            val ns = if (lower.contains(":")) lower.substringBefore(":") else "minecraft"

            when {
                // --- NETHER (Vanilla, tags, et mods comme Biomes O' Plenty, BYG, Incendium, BetterNether) ---
                lower.contains("nether") ||
                lower == "biomesoplenty:crystalline_chasm" ||
                lower == "byg:warped_desert" ||
                lower.startsWith("incendium:") ||
                lower.startsWith("betternether:") ||
                lower.contains("in_nether") -> {
                    deduced.add("minecraft:the_nether")
                }

                // --- THE END (Vanilla, tags, et mods comme Nullscape, BetterEnd) ---
                lower.contains("the_end") ||
                lower.contains("/end") ||
                lower.contains("is_end") ||
                lower.contains("in_the_end") ||
                lower.startsWith("minecraft:end_") ||
                lower == "minecraft:the_end" ||
                lower.startsWith("nullscape:") ||
                lower.startsWith("betterend:") -> {
                    deduced.add("minecraft:the_end")
                }

                // --- AUTRES DIMENSIONS MODDÉES ---
                lower.contains("aether") -> deduced.add("aether:the_aether")
                lower.contains("the_bumblezone") || lower.startsWith("the_bumblezone:") -> deduced.add("the_bumblezone:the_bumblezone")
                lower.contains("twilightforest") || lower.contains("twilight_forest") -> deduced.add("twilightforest:twilight_forest")
                lower.contains("undergarden") -> deduced.add("undergarden:undergarden")
                lower.contains("distortion_world") -> deduced.add("legendarymonuments:distortion_world")
                lower.contains("allthemodium:other") || lower.contains("the_other") -> deduced.add("allthemodium:the_other")

                // --- OVERWORLD (Vanilla, Terralith, Biomes O' Plenty surface, Regions Unexplored, etc.) ---
                else -> deduced.add("minecraft:overworld")
            }
        }

        if (deduced.isEmpty()) {
            deduced.add("minecraft:overworld")
        }

        return deduced
    }

    /**
     * Agrège les entrées partageant des conditions similaires pour fusionner les biomes.
     */
    internal fun aggregateRawEntries(rawEntries: List<RawSpawnEntry>): List<PokemonSpawnRecipe> {
        data class GroupKey(
            val species: Species,
            val form: FormData,
            val bucket: String,
            val levelMin: Int,
            val levelMax: Int,
            val context: String,
            val timeRange: String,
            val weather: String,
            val dimensions: Set<String>,
            val minY: Int?,
            val maxY: Int?,
            val neededBaseBlocks: Set<String>,
            val isSlimeChunk: Boolean?
        )

        val groups = rawEntries.groupBy { entry ->
            GroupKey(
                species = entry.species,
                form = entry.form,
                bucket = entry.bucket,
                levelMin = entry.levelMin,
                levelMax = entry.levelMax,
                context = entry.context,
                timeRange = entry.timeRange,
                weather = entry.weather,
                dimensions = entry.dimensions,
                minY = entry.minY,
                maxY = entry.maxY,
                neededBaseBlocks = entry.neededBaseBlocks,
                isSlimeChunk = entry.isSlimeChunk
            )
        }

        val recipes = mutableListOf<PokemonSpawnRecipe>()
        for ((key, entries) in groups) {
            val mergedBiomes = entries.flatMap { it.biomes }.toSet()
            val mergedAntiBiomes = entries.flatMap { it.antiBiomes }.toSet()
            val mergedStructures = entries.flatMap { it.structures }.toSet()
            val mergedAntiStructures = entries.flatMap { it.antiStructures }.toSet()
            val mergedNearbyBlocks = entries.flatMap { it.neededNearbyBlocks }.toSet()
            val maxWeight = entries.maxOfOrNull { it.weight } ?: 10.0f
            val canSeeSky = entries.firstNotNullOfOrNull { it.canSeeSky }
            val minSkyLight = entries.firstNotNullOfOrNull { it.minSkyLight }
            val maxSkyLight = entries.firstNotNullOfOrNull { it.maxSkyLight }
            val minLight = entries.firstNotNullOfOrNull { it.minLight }
            val maxLight = entries.firstNotNullOfOrNull { it.maxLight }
            val moonPhase = entries.firstNotNullOfOrNull { it.moonPhase }
            val minLureLevel = entries.firstNotNullOfOrNull { it.minLureLevel }
            val maxLureLevel = entries.firstNotNullOfOrNull { it.maxLureLevel }
            val rodType = entries.firstNotNullOfOrNull { it.rodType }
            val bait = entries.firstNotNullOfOrNull { it.bait }

            recipes.add(
                PokemonSpawnRecipe(
                    species = key.species,
                    form = key.form,
                    isNaturalSpawn = true,
                    bucket = key.bucket,
                    weight = maxWeight,
                    levelMin = key.levelMin,
                    levelMax = key.levelMax,
                    context = key.context,
                    biomes = mergedBiomes,
                    antiBiomes = mergedAntiBiomes,
                    dimensions = key.dimensions,
                    timeRange = key.timeRange,
                    weather = key.weather,
                    canSeeSky = canSeeSky,
                    minSkyLight = minSkyLight,
                    maxSkyLight = maxSkyLight,
                    minY = key.minY,
                    maxY = key.maxY,
                    neededBaseBlocks = key.neededBaseBlocks,
                    neededNearbyBlocks = mergedNearbyBlocks,
                    minLight = minLight,
                    maxLight = maxLight,
                    moonPhase = moonPhase,
                    isSlimeChunk = key.isSlimeChunk,
                    minLureLevel = minLureLevel,
                    maxLureLevel = maxLureLevel,
                    rodType = rodType,
                    bait = bait,
                    structures = mergedStructures,
                    antiStructures = mergedAntiStructures
                )
            )
        }
        return recipes
    }

    /**
     * Pour toute espèce/forme sans apparition sauvage naturelle, ajoute une fiche
     * informative explicative (légendaires, évolutions uniquement, fossiles, etc.).
     */
    private fun addMissingNoSpawnSpecies(existingRecipes: List<PokemonSpawnRecipe>): List<PokemonSpawnRecipe> {
        val result = existingRecipes.toMutableList()
        val spawnedSpeciesForms = existingRecipes.map { Pair(it.species.name, it.form.name) }.toSet()

        for (species in PokemonSpecies.species) {
            // Forme standard
            if (!spawnedSpeciesForms.contains(Pair(species.name, species.standardForm.name))) {
                result.add(
                    PokemonSpawnRecipe(
                        species = species,
                        form = species.standardForm,
                        isNaturalSpawn = false,
                        bucket = "none",
                        notes = "Ce Pokémon ne possède aucun point d'apparition naturel configuré."
                    )
                )
            }

            // Autres formes notables
            for (form in species.forms) {
                if (form != species.standardForm && !spawnedSpeciesForms.contains(Pair(species.name, form.name))) {
                    result.add(
                        PokemonSpawnRecipe(
                            species = species,
                            form = form,
                            isNaturalSpawn = false,
                            bucket = "none",
                            notes = "Cette forme spécifique n'apparaît pas à l'état sauvage."
                        )
                    )
                }
            }
        }
        return result
    }

    internal data class RawSpawnEntry(
        val species: Species,
        val form: FormData,
        val bucket: String,
        val weight: Float,
        val levelMin: Int,
        val levelMax: Int,
        val context: String,
        val biomes: Set<String>,
        val antiBiomes: Set<String>,
        val dimensions: Set<String>,
        val timeRange: String,
        val weather: String,
        val canSeeSky: Boolean?,
        val minSkyLight: Int?,
        val maxSkyLight: Int?,
        val minY: Int?,
        val maxY: Int?,
        val neededBaseBlocks: Set<String>,
        val neededNearbyBlocks: Set<String>,
        val minLight: Int?,
        val maxLight: Int?,
        val moonPhase: Int?,
        val isSlimeChunk: Boolean?,
        val minLureLevel: Int?,
        val maxLureLevel: Int?,
        val rodType: String?,
        val bait: String?,
        val structures: Set<String>,
        val antiStructures: Set<String>
    )
}
