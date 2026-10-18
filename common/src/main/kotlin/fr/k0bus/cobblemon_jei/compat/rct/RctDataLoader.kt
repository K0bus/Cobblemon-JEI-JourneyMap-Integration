package fr.k0bus.cobblemon_jei.compat.rct

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.k0bus.cobblemon_jei.data.CobblemonDataScanner
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.io.InputStreamReader

private data class RawMobConfig(
    val type: String = "normal",
    val relativeLevelCap: Int? = null,
    val optional: Boolean = false,
    val maxTrainerWins: Int = -1,
    val maxTrainerDefeats: Int = 1,
    val series: List<String> = emptyList(),
    val requiredDefeats: List<List<String>> = emptyList(),
    val requiredSeries: List<List<String>> = emptyList()
)

/**
 * Chargeur et parseur de données pour Radical Cobblemon Trainers (RCT).
 * Extrait les séries, les configurations de dresseurs (mobs) et les équipes.
 */
object RctDataLoader {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/RctLoader")

    private val seriesCache = mutableMapOf<String, RctSeries>()
    private val trainersCache = mutableMapOf<String, RctTrainer>()
    private val textureCache = mutableMapOf<String, ResourceLocation>()

    private var isLoaded = false

    @Synchronized
    fun ensureLoaded() {
        if (isLoaded) return
        reload()
    }

    @Synchronized
    fun reload() {
        seriesCache.clear()
        trainersCache.clear()
        textureCache.clear()

        // 1. Scan et chargement des mobs (configurations des contraintes de duel)
        val singleMobConfigs = mutableMapOf<String, RawMobConfig>()
        val groupMobConfigs = mutableMapOf<String, RawMobConfig>()
        var defaultMobConfig: RawMobConfig? = null

        scanMobConfigs(singleMobConfigs, groupMobConfigs) { cfg ->
            defaultMobConfig = cfg
        }

        // 2. Scan et chargement des séries
        scanSeries()

        // 3. Scan et chargement des équipes et fiches des dresseurs
        scanTrainers(singleMobConfigs, groupMobConfigs, defaultMobConfig)

        // 4. Fallback si aucun fichier RCT n'a été découvert (ex: test en dev sans le jar complet)
        if (seriesCache.isEmpty()) {
            loadDefaultFallbackSeries()
        }

        isLoaded = true
        logger.info("Cobblemon JEI: ${seriesCache.size} séries et ${trainersCache.size} dresseurs RCT chargés.")
    }

    fun hasAnySeries(): Boolean {
        ensureLoaded()
        return seriesCache.isNotEmpty()
    }

    fun getAllSeries(): List<RctSeries> {
        ensureLoaded()
        return seriesCache.values.toList()
    }

    fun getSeries(seriesId: String): RctSeries? {
        ensureLoaded()
        return seriesCache[seriesId]
    }

    fun getAllTrainers(): List<RctTrainer> {
        ensureLoaded()
        return trainersCache.values.toList()
    }

    fun getTrainer(trainerId: String): RctTrainer? {
        ensureLoaded()
        return trainersCache[trainerId]
    }

    fun getTrainersForSeries(seriesId: String): List<RctTrainer> {
        ensureLoaded()
        return trainersCache.values.filter { trainer ->
            trainer.series.isEmpty() || trainer.series.contains(seriesId)
        }
    }

    /**
     * Scanne les fichiers de series dans data/rctmod/series.
     */
    private fun scanSeries() {
        try {
            val rm = Minecraft.getInstance().resourceManager
            if (rm != null) {
                val resMap = rm.listResources("series") { it.namespace == "rctmod" && it.path.endsWith(".json") }
                for ((loc, res) in resMap) {
                    try {
                        val id = loc.path.removePrefix("series/").removeSuffix(".json").substringAfterLast('/')
                        res.open().use { stream ->
                            parseSeriesJson(id, stream)
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}

        CobblemonDataScanner.scanDataResources("series") { path, stream ->
            try {
                if (path.contains("rctmod", ignoreCase = true) || path.contains("/series/")) {
                    val id = path.substringAfterLast('/').removeSuffix(".json")
                    parseSeriesJson(id, stream)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun parseSeriesJson(id: String, stream: java.io.InputStream) {
        if (seriesCache.containsKey(id)) return
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            val obj = JsonParser.parseReader(reader).asJsonObject
            val title = obj.get("title")?.asString ?: id.replaceFirstChar { it.uppercase() }
            val description = obj.get("description")?.asString ?: ""
            val difficulty = obj.get("difficulty")?.asInt ?: 5
            val initialLevelCap = obj.get("initialLevelCap")?.asInt ?: 15
            val relativeLevelCap = obj.get("relativeLevelCap")?.asInt ?: 0
            val hideTrainerIdentities = obj.get("hideTrainerIdentities")?.asBoolean ?: false

            val reqSeries = mutableListOf<List<String>>()
            obj.getAsJsonArray("requiredSeries")?.forEach { innerArr ->
                if (innerArr is JsonArray) {
                    reqSeries.add(innerArr.mapNotNull { it.asString })
                }
            }

            seriesCache[id] = RctSeries(
                id = id,
                title = title,
                description = description,
                difficulty = difficulty,
                initialLevelCap = initialLevelCap,
                relativeLevelCap = relativeLevelCap,
                requiredSeries = reqSeries,
                hideTrainerIdentities = hideTrainerIdentities
            )
        }
    }

    /**
     * Scanne les configurations de mobs (`mobs/trainers/single/`, `groups/`, `default.json`).
     */
    private fun scanMobConfigs(
        singleMap: MutableMap<String, RawMobConfig>,
        groupMap: MutableMap<String, RawMobConfig>,
        onDefault: (RawMobConfig) -> Unit
    ) {
        CobblemonDataScanner.scanDataResources("mobs") { path, stream ->
            try {
                val cleanPath = path.replace('\\', '/')
                if (!cleanPath.endsWith(".json")) return@scanDataResources

                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    val obj = JsonParser.parseReader(reader).asJsonObject
                    val config = parseRawMobConfig(obj)

                    if (cleanPath.endsWith("default.json")) {
                        onDefault(config)
                    } else if (cleanPath.contains("/single/")) {
                        val id = cleanPath.substringAfterLast('/').removeSuffix(".json")
                        singleMap[id] = config
                    } else if (cleanPath.contains("/groups/")) {
                        val group = cleanPath.substringAfterLast('/').removeSuffix(".json")
                        groupMap[group] = config
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun parseRawMobConfig(obj: JsonObject): RawMobConfig {
        val type = obj.get("type")?.asString ?: "normal"
        val relativeLevelCap = obj.get("relativeLevelCap")?.asInt
        val optional = obj.get("optional")?.asBoolean ?: false
        val maxWins = obj.get("maxTrainerWins")?.asInt ?: -1
        val maxDefeats = obj.get("maxTrainerDefeats")?.asInt ?: 1

        val series = mutableListOf<String>()
        obj.getAsJsonArray("series")?.forEach { series.add(it.asString) }

        val reqDefeats = mutableListOf<List<String>>()
        obj.getAsJsonArray("requiredDefeats")?.forEach { inner ->
            if (inner is JsonArray) {
                reqDefeats.add(inner.mapNotNull { it.asString })
            }
        }

        val reqSeries = mutableListOf<List<String>>()
        obj.getAsJsonArray("requiredSeries")?.forEach { inner ->
            if (inner is JsonArray) {
                reqSeries.add(inner.mapNotNull { it.asString })
            }
        }

        return RawMobConfig(
            type = type,
            relativeLevelCap = relativeLevelCap,
            optional = optional,
            maxTrainerWins = maxWins,
            maxTrainerDefeats = maxDefeats,
            series = series,
            requiredDefeats = reqDefeats,
            requiredSeries = reqSeries
        )
    }

    /**
     * Scanne les fiches d'equipes des dresseurs dans data/rctmod/trainers.
     */
    private fun scanTrainers(
        singleConfigs: Map<String, RawMobConfig>,
        groupConfigs: Map<String, RawMobConfig>,
        defaultConfig: RawMobConfig?
    ) {
        CobblemonDataScanner.scanDataResources("trainers") { path, stream ->
            try {
                val cleanPath = path.replace('\\', '/')
                if (!cleanPath.endsWith(".json")) return@scanDataResources
                if (cleanPath.contains("/mobs/")) return@scanDataResources

                val trainerId = cleanPath.substringAfterLast('/').removeSuffix(".json")
                if (trainersCache.containsKey(trainerId)) return@scanDataResources

                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    val obj = JsonParser.parseReader(reader).asJsonObject
                    val name = obj.get("name")?.asString ?: trainerId.replace('_', ' ')
                    val identity = obj.get("identity")?.asString ?: name
                    val battleFormat = obj.get("battleFormat")?.asString ?: "GEN_9_SINGLES"
                    val battleTheme = obj.get("battleTheme")?.asString ?: "battle.pvn.default"

                    val bag = mutableListOf<RctBagItem>()
                    obj.getAsJsonArray("bag")?.forEach { itemElem ->
                        if (itemElem is JsonObject) {
                            val item = itemElem.get("item")?.asString ?: "cobblemon:potion"
                            val qty = itemElem.get("quantity")?.asInt ?: 1
                            bag.add(RctBagItem(item, qty))
                        }
                    }

                    val team = mutableListOf<RctPokemon>()
                    obj.getAsJsonArray("team")?.forEach { pElem ->
                        if (pElem is JsonObject) {
                            val species = pElem.get("species")?.asString ?: return@forEach
                            val nickname = pElem.get("nickname")?.asString
                            val gender = pElem.get("gender")?.asString ?: "GENDERLESS"
                            val level = pElem.get("level")?.asInt ?: 1
                            val nature = pElem.get("nature")?.asString
                            val ability = pElem.get("ability")?.asString
                            val shiny = pElem.get("shiny")?.asBoolean ?: false
                            val heldItem = pElem.get("heldItem")?.asString

                            val moveset = mutableListOf<String>()
                            pElem.getAsJsonArray("moveset")?.forEach { moveset.add(it.asString) }

                            val aspects = mutableListOf<String>()
                            pElem.getAsJsonArray("aspects")?.forEach { aspects.add(it.asString) }

                            team.add(
                                RctPokemon(
                                    species = species,
                                    nickname = nickname,
                                    gender = gender,
                                    level = level,
                                    nature = nature,
                                    ability = ability,
                                    moveset = moveset,
                                    shiny = shiny,
                                    heldItem = heldItem,
                                    aspects = aspects
                                )
                            )
                        }
                    }

                    val mobConfig = singleConfigs[trainerId]
                        ?: findBestGroupMobConfig(trainerId, groupConfigs)
                        ?: defaultConfig
                        ?: RawMobConfig()

                    val maxPokeLevel = team.maxOfOrNull { it.level } ?: 1
                    val compLevelCap = maxOf(1, maxPokeLevel + (mobConfig.relativeLevelCap ?: 0))
                    val texture = resolveTextureLocation(trainerId)

                    trainersCache[trainerId] = RctTrainer(
                        id = trainerId,
                        name = name,
                        identity = identity,
                        type = mobConfig.type,
                        battleFormat = battleFormat,
                        battleTheme = battleTheme,
                        bag = bag,
                        team = team,
                        relativeLevelCap = mobConfig.relativeLevelCap,
                        computedLevelCap = compLevelCap,
                        requiredDefeats = mobConfig.requiredDefeats,
                        requiredSeries = mobConfig.requiredSeries,
                        series = mobConfig.series,
                        optional = mobConfig.optional,
                        maxTrainerWins = mobConfig.maxTrainerWins,
                        maxTrainerDefeats = mobConfig.maxTrainerDefeats,
                        textureLocation = texture
                    )
                }
            } catch (_: Throwable) {}
        }
    }

    private fun findBestGroupMobConfig(trainerId: String, groups: Map<String, RawMobConfig>): RawMobConfig? {
        var bestMatch: String? = null
        for (groupName in groups.keys) {
            if (trainerId.startsWith(groupName)) {
                if (bestMatch == null || groupName.length > bestMatch.length) {
                    bestMatch = groupName
                }
            }
        }
        return bestMatch?.let { groups[it] }
    }

    /**
     * Résout la texture de skin du dresseur dans `assets/rctmod/textures/trainers/...`.
     */
    fun resolveTextureLocation(trainerId: String): ResourceLocation {
        textureCache[trainerId]?.let { return it }

        val rm = try { Minecraft.getInstance().resourceManager } catch (_: Throwable) { null }

        // 1. Single texture
        val singleLoc = ResourceLocation.fromNamespaceAndPath("rctmod", "textures/trainers/single/$trainerId.png")
        if (rm != null && rm.getResource(singleLoc).isPresent) {
            textureCache[trainerId] = singleLoc
            return singleLoc
        }

        // 2. Groupes par préfixes décroissants
        val parts = trainerId.split("_")
        for (i in parts.size - 1 downTo 1) {
            val groupPrefix = parts.subList(0, i).joinToString("_")
            val groupLoc = ResourceLocation.fromNamespaceAndPath("rctmod", "textures/trainers/groups/$groupPrefix.png")
            if (rm != null && rm.getResource(groupLoc).isPresent) {
                textureCache[trainerId] = groupLoc
                return groupLoc
            }
        }

        // 3. Texture par défaut RCT
        val defaultRct = ResourceLocation.fromNamespaceAndPath("rctmod", "textures/trainers/default.png")
        if (rm != null && rm.getResource(defaultRct).isPresent) {
            textureCache[trainerId] = defaultRct
            return defaultRct
        }

        // 4. Fallback Minecraft Steve standard
        val steve = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png")
        textureCache[trainerId] = steve
        return steve
    }

    /**
     * Crée des séries d'authentiques démos Pokémon complètes si aucun fichier RCT n'est sur le disque.
     */
    private fun loadDefaultFallbackSeries() {
        val sRadicalRed = RctSeries(
            id = "radicalred",
            title = "Radical Red (v3.02)",
            description = "Progression fidèle du célèbre hackrom Pokémon Radical Red avec Gym Leaders et Conseil des 4.",
            difficulty = 8,
            initialLevelCap = 15,
            relativeLevelCap = 0
        )
        seriesCache[sRadicalRed.id] = sRadicalRed

        addFallbackTrainer(
            id = "leader_brock_019e",
            name = "Pierre (Brock)",
            type = "leader",
            series = listOf("radicalred"),
            cap = 15,
            req = emptyList(),
            optional = false,
            team = listOf(
                RctPokemon("geodude", level = 12, moveset = listOf("rockthrow", "tackle")),
                RctPokemon("vulpix", level = 13, moveset = listOf("ember", "quickattack")),
                RctPokemon("onix", level = 14, moveset = listOf("rocktomb", "bind", "slam"), heldItem = "sitrus_berry")
            )
        )

        addFallbackTrainer(
            id = "rival_cerulean",
            name = "Rival Blue (Azuria)",
            type = "rival",
            series = listOf("radicalred"),
            cap = 24,
            req = listOf(listOf("leader_brock_019e")),
            optional = true,
            team = listOf(
                RctPokemon("pidgeotto", level = 22),
                RctPokemon("kadabra", level = 23),
                RctPokemon("charmeleon", level = 24)
            )
        )

        addFallbackTrainer(
            id = "leader_misty_019f",
            name = "Ondine (Misty)",
            type = "leader",
            series = listOf("radicalred"),
            cap = 27,
            req = listOf(listOf("leader_brock_019e")),
            optional = false,
            team = listOf(
                RctPokemon("frogadier", level = 25, ability = "protean"),
                RctPokemon("togetic", level = 26),
                RctPokemon("starmie", level = 27, heldItem = "life_orb", moveset = listOf("scald", "psyshock", "recover"))
            )
        )

        addFallbackTrainer(
            id = "leader_surge_01a0",
            name = "Major Bob (Lt. Surge)",
            type = "leader",
            series = listOf("radicalred"),
            cap = 36,
            req = listOf(listOf("leader_misty_019f")),
            optional = false,
            team = listOf(
                RctPokemon("emolga", level = 34),
                RctPokemon("vikavolt", level = 35),
                RctPokemon("raichu", level = 36, aspects = listOf("alolan"), heldItem = "focus_sash")
            )
        )

        addFallbackTrainer(
            id = "leader_erika_01a1",
            name = "Érika",
            type = "leader",
            series = listOf("radicalred"),
            cap = 44,
            req = listOf(listOf("leader_surge_01a0")),
            optional = false,
            team = listOf(
                RctPokemon("bellossom", level = 43),
                RctPokemon("rillaboom", level = 44, ability = "grassy_surge"),
                RctPokemon("venusaur", level = 44, heldItem = "venusaurite")
            )
        )

        addFallbackTrainer(
            id = "boss_giovanni_silph",
            name = "Giovanni (Silph Co.)",
            type = "boss",
            series = listOf("radicalred"),
            cap = 48,
            req = listOf(listOf("leader_erika_01a1")),
            optional = false,
            team = listOf(
                RctPokemon("kangaskhan", level = 47),
                RctPokemon("nidoking", level = 48, ability = "sheer_force", heldItem = "life_orb")
            )
        )

        addFallbackTrainer(
            id = "leader_koga_01a2",
            name = "Koga",
            type = "leader",
            series = listOf("radicalred"),
            cap = 50,
            req = listOf(listOf("boss_giovanni_silph")),
            optional = false,
            team = listOf(
                RctPokemon("weezing", level = 49, aspects = listOf("galarian")),
                RctPokemon("greninja", level = 50),
                RctPokemon("drapion", level = 50)
            )
        )

        addFallbackTrainer(
            id = "leader_sabrina_01a3",
            name = "Morgane (Sabrina)",
            type = "leader",
            series = listOf("radicalred"),
            cap = 58,
            req = listOf(listOf("leader_koga_01a2")),
            optional = false,
            team = listOf(
                RctPokemon("gardevoir", level = 57),
                RctPokemon("alakazam", level = 58, heldItem = "alakazite")
            )
        )

        addFallbackTrainer(
            id = "leader_blaine_01a4",
            name = "Auguste (Blaine)",
            type = "leader",
            series = listOf("radicalred"),
            cap = 68,
            req = listOf(listOf("leader_sabrina_01a3")),
            optional = false,
            team = listOf(
                RctPokemon("torkoal", level = 67, ability = "drought"),
                RctPokemon("charizard", level = 68, heldItem = "charizardite_y")
            )
        )

        addFallbackTrainer(
            id = "leader_giovanni_gym",
            name = "Giovanni (Arène de Jadielle)",
            type = "leader",
            series = listOf("radicalred"),
            cap = 74,
            req = listOf(listOf("leader_blaine_01a4")),
            optional = false,
            team = listOf(
                RctPokemon("hippowdon", level = 73, ability = "sand_stream"),
                RctPokemon("excadrill", level = 74),
                RctPokemon("mewtwo", level = 74, aspects = listOf("armored"))
            )
        )

        addFallbackTrainer(
            id = "e4_lorelei",
            name = "Olga (Lorelei) - Conseil 4",
            type = "elite_four",
            series = listOf("radicalred"),
            cap = 80,
            req = listOf(listOf("leader_giovanni_gym")),
            optional = false,
            team = listOf(
                RctPokemon("ninetales", level = 79, aspects = listOf("alolan")),
                RctPokemon("lapras", level = 80)
            )
        )

        addFallbackTrainer(
            id = "champion_blue",
            name = "Maître Blue (Champion)",
            type = "champion",
            series = listOf("radicalred"),
            cap = 85,
            req = listOf(listOf("e4_lorelei")),
            optional = false,
            team = listOf(
                RctPokemon("tyranitar", level = 84),
                RctPokemon("garchomp", level = 84),
                RctPokemon("blastoise", level = 85, heldItem = "blastoisinite")
            )
        )

        // Série Unbound
        val sUnbound = RctSeries(
            id = "unbound",
            title = "Pokémon Unbound (v2.03)",
            description = "L'aventure épique de la région de Borrius avec son système de caps et arènes spécialisées.",
            difficulty = 9,
            initialLevelCap = 20
        )
        seriesCache[sUnbound.id] = sUnbound

        addFallbackTrainer(
            id = "unbound_mirskle",
            name = "Mirskle (Cinder Volcano)",
            type = "leader",
            series = listOf("unbound"),
            cap = 20,
            req = emptyList(),
            optional = false,
            team = listOf(
                RctPokemon("magby", level = 18),
                RctPokemon("houndour", level = 20)
            )
        )

        addFallbackTrainer(
            id = "unbound_vega",
            name = "Vega (Crater Town)",
            type = "leader",
            series = listOf("unbound"),
            cap = 26,
            req = listOf(listOf("unbound_mirskle")),
            optional = false,
            team = listOf(
                RctPokemon("pawniard", level = 24),
                RctPokemon("absol", level = 26)
            )
        )

        addFallbackTrainer(
            id = "unbound_alice",
            name = "Alice (Dresco Town)",
            type = "leader",
            series = listOf("unbound"),
            cap = 34,
            req = listOf(listOf("unbound_vega")),
            optional = false,
            team = listOf(
                RctPokemon("corvisquire", level = 32),
                RctPokemon("gliscor", level = 34)
            )
        )

        // Série BDSP
        val sBdsp = RctSeries(
            id = "bdsp",
            title = "Sinnoh (BDSP)",
            description = "La ligue Pokémon classique de Sinnoh avec Cynthia et les 8 Champions d'Arène.",
            difficulty = 6,
            initialLevelCap = 16
        )
        seriesCache[sBdsp.id] = sBdsp

        addFallbackTrainer(
            id = "bdsp_roark",
            name = "Pierrick (Roark)",
            type = "leader",
            series = listOf("bdsp"),
            cap = 16,
            req = emptyList(),
            optional = false,
            team = listOf(
                RctPokemon("geodude", level = 14),
                RctPokemon("cranidos", level = 16)
            )
        )

        addFallbackTrainer(
            id = "bdsp_gardenia",
            name = "Flo (Gardenia)",
            type = "leader",
            series = listOf("bdsp"),
            cap = 22,
            req = listOf(listOf("bdsp_roark")),
            optional = false,
            team = listOf(
                RctPokemon("cherubi", level = 20),
                RctPokemon("roserade", level = 22)
            )
        )

        addFallbackTrainer(
            id = "bdsp_cynthia",
            name = "Cynthia (Championne Sinnoh)",
            type = "champion",
            series = listOf("bdsp"),
            cap = 66,
            req = listOf(listOf("bdsp_gardenia")),
            optional = false,
            team = listOf(
                RctPokemon("spiritomb", level = 61),
                RctPokemon("milotic", level = 63, heldItem = "flame_orb"),
                RctPokemon("lucario", level = 63),
                RctPokemon("garchomp", level = 66, heldItem = "yache_berry")
            )
        )
    }

    private fun addFallbackTrainer(
        id: String,
        name: String,
        type: String,
        series: List<String>,
        cap: Int,
        req: List<List<String>>,
        optional: Boolean,
        team: List<RctPokemon>
    ) {
        trainersCache[id] = RctTrainer(
            id = id,
            name = name,
            identity = id,
            type = type,
            computedLevelCap = cap,
            requiredDefeats = req,
            series = series,
            optional = optional,
            team = team,
            textureLocation = resolveTextureLocation(id)
        )
    }
}
