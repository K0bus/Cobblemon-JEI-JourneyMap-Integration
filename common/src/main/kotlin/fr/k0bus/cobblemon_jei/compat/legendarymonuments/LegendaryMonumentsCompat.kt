package fr.k0bus.cobblemon_jei.compat.legendarymonuments

import net.minecraft.resources.ResourceLocation
import java.util.Locale

/**
 * Service de compatibilité dédié à l'extension [Cobblemon: Legendary Monuments].
 *
 * Centralise :
 * 1. La détection dynamique de la présence du mod (NeoForge / Fabric).
 * 2. Le catalogue officiel des Pokémon légendaires et fabuleux régis par ce mod.
 * 3. La déduction de l'espèce de Pokémon et du nom de structure depuis un identifiant de bloc ou de structure.
 */
object LegendaryMonumentsCompat {

    const val MOD_ID = "legendarymonuments"

    /**
     * Mots-clés caractérisant un bloc d'invocation de monument légendaire.
     */
    val MONUMENT_BLOCK_KEYWORDS = listOf(
        "pedestal", "shrine", "altar", "cocoon", "jukebox", "statue",
        "urn", "tablet", "summon", "boss_summon", "lock", "footprint"
    )

    /**
     * Liste exhaustive des noms d'espèces de Pokémon légendaires/mythiques pris en charge par Legendary Monuments.
     */
    val COVERED_LEGENDARY_SPECIES: Set<String> = setOf(
        "zacian", "zamazenta",
        "cobalion", "virizion", "terrakion", "keldeo",
        "moltres", "articuno", "zapdos",
        "raikou", "entei", "suicune",
        "cosmog", "cosmoem", "solgaleo", "lunala",
        "regirock", "registeel", "regice", "regieleki", "regidrago", "regigigas",
        "eternatus",
        "reshiram", "zekrom", "kyurem",
        "latias", "latios",
        "hoopa",
        "ho-oh", "lugia",
        "cresselia", "darkrai", "heatran", "celebi",
        "chien-pao", "chi-yu", "ting-lu", "wo-chien",
        "mew", "mewtwo",
        "azelf", "uxie", "mesprit",
        "palkia", "dialga", "giratina", "arceus",
        "victini",
        "yveltal", "xerneas", "zygarde",
        "marshadow",
        "meloetta",
        "spectrier", "glastrier", "calyrex",
        "enamorus"
    )

    /**
     * Structures phares connues de Legendary Monuments pour affichage élégant.
     */
    data class MonumentInfo(
        val speciesName: String,
        val structureDisplayName: String,
        val defaultDimension: String = "minecraft:overworld",
        val primaryPedestalBlock: String
    )

    val KNOWN_MONUMENTS: Map<String, MonumentInfo> = mapOf(
        "zacian" to MonumentInfo("zacian", "Sword Temple", "minecraft:overworld", "zacian_pedestal"),
        "zamazenta" to MonumentInfo("zamazenta", "Shield Temple", "minecraft:overworld", "zamazenta_pedestal"),
        "dialga" to MonumentInfo("dialga", "Spear Pillar", "minecraft:overworld", "dialga_pedestal"),
        "palkia" to MonumentInfo("palkia", "Spear Pillar", "minecraft:overworld", "palkia_pedestal"),
        "giratina" to MonumentInfo("giratina", "Turnback Cave / Distortion World", "legendarymonuments:distortion_world", "giratina_pedestal"),
        "arceus" to MonumentInfo("arceus", "Final Island", "minecraft:overworld", "arceus_pedestal"),
        "reshiram" to MonumentInfo("reshiram", "Dragonspiral Tower", "minecraft:overworld", "reshiram_pedestal"),
        "zekrom" to MonumentInfo("zekrom", "Dragonspiral Tower", "minecraft:overworld", "zekrom_pedestal"),
        "kyurem" to MonumentInfo("kyurem", "Kyurem's Cave", "minecraft:overworld", "kyurem_pedestal"),
        "lugia" to MonumentInfo("lugia", "Lugia Temple", "minecraft:overworld", "lugia_pedestal"),
        "ho-oh" to MonumentInfo("ho-oh", "Bell Tower", "minecraft:overworld", "hooh_pedestal"),
        "raikou" to MonumentInfo("raikou", "Burned Tower", "minecraft:overworld", "raikou_pedestal"),
        "entei" to MonumentInfo("entei", "Burned Tower", "minecraft:overworld", "entei_pedestal"),
        "suicune" to MonumentInfo("suicune", "Burned Tower", "minecraft:overworld", "suicune_pedestal"),
        "eternatus" to MonumentInfo("eternatus", "Eternatus Cocoon", "minecraft:overworld", "eternatus_cocoon"),
        "hoopa" to MonumentInfo("hoopa", "Hoopa Pyramid", "minecraft:overworld", "hoopa_shrine"),
        "latias" to MonumentInfo("latias", "Southern Island", "minecraft:overworld", "latias_pedestal"),
        "latios" to MonumentInfo("latios", "Southern Island", "minecraft:overworld", "latios_pedestal"),
        "regirock" to MonumentInfo("regirock", "Rock Peak Chamber", "minecraft:overworld", "regirock_pedestal"),
        "registeel" to MonumentInfo("registeel", "Iron Chamber", "minecraft:overworld", "registeel_pedestal"),
        "regice" to MonumentInfo("regice", "Iceberg Chamber", "minecraft:overworld", "regice_pedestal"),
        "regieleki" to MonumentInfo("regieleki", "DragoEleki Temple", "minecraft:overworld", "regieleki_pedestal"),
        "regidrago" to MonumentInfo("regidrago", "DragoEleki Temple", "minecraft:overworld", "regidrago_pedestal"),
        "regigigas" to MonumentInfo("regigigas", "Snowpoint Temple", "minecraft:overworld", "regigigas_pedestal"),
        "meloetta" to MonumentInfo("meloetta", "Amphitheater", "minecraft:overworld", "meloetta_jukebox"),
        "yveltal" to MonumentInfo("yveltal", "Yveltal Cocoon", "minecraft:overworld", "yveltal_cocoon"),
        "xerneas" to MonumentInfo("xerneas", "Tree of Life", "minecraft:overworld", "xerneas_tree"),
        "calyrex" to MonumentInfo("calyrex", "Crown Shrine", "minecraft:overworld", "crown_shrine"),
        "spectrier" to MonumentInfo("spectrier", "Crown Shrine", "minecraft:overworld", "crown_shrine"),
        "glastrier" to MonumentInfo("glastrier", "Crown Shrine", "minecraft:overworld", "crown_shrine"),
        "chien-pao" to MonumentInfo("chien-pao", "Icescourge Shrine", "minecraft:overworld", "chien_pao_shrine"),
        "chi-yu" to MonumentInfo("chi-yu", "Firescourge Shrine", "minecraft:overworld", "chi_yu_shrine"),
        "ting-lu" to MonumentInfo("ting-lu", "Groundblight Shrine", "minecraft:overworld", "ting_lu_shrine"),
        "wo-chien" to MonumentInfo("wo-chien", "Grasswither Shrine", "minecraft:overworld", "wo_chien_shrine"),
        "cobalion" to MonumentInfo("cobalion", "Empreinte de Cobaltium", "minecraft:overworld", "cobalion_footprint"),
        "terrakion" to MonumentInfo("terrakion", "Empreinte de Terrakium", "minecraft:overworld", "terrakion_footprint"),
        "virizion" to MonumentInfo("virizion", "Empreinte de Viridium", "minecraft:overworld", "virizion_footprint"),
        "keldeo" to MonumentInfo("keldeo", "Empreinte de Keldeo", "minecraft:overworld", "keldeo_footprint"),
        "articuno" to MonumentInfo("articuno", "Sanctuaire d'Artikodin", "minecraft:overworld", "articuno_shrine"),
        "zapdos" to MonumentInfo("zapdos", "Sanctuaire d'Élector", "minecraft:overworld", "zapdos_shrine"),
        "moltres" to MonumentInfo("moltres", "Sanctuaire de Sulfura", "minecraft:overworld", "moltres_shrine"),
        "mewtwo" to MonumentInfo("mewtwo", "Cerulean Cave", "minecraft:overworld", "mewtwo_pedestal"),
        "mew" to MonumentInfo("mew", "Faraway Island", "minecraft:overworld", "mew_pedestal"),
        "solgaleo" to MonumentInfo("solgaleo", "Altar of the Sunne", "minecraft:overworld", "solgaleo_pedestal"),
        "lunala" to MonumentInfo("lunala", "Altar of the Moone", "minecraft:overworld", "lunala_pedestal"),
        "cosmog" to MonumentInfo("cosmog", "Altar of the Sunne", "minecraft:overworld", "cosmog_pedestal"),
        "cosmoem" to MonumentInfo("cosmoem", "Altar of the Moone", "minecraft:overworld", "cosmoem_pedestal"),
        "uxie" to MonumentInfo("uxie", "Lake Acuity", "minecraft:overworld", "uxie_pedestal"),
        "mesprit" to MonumentInfo("mesprit", "Lake Verity", "minecraft:overworld", "mesprit_pedestal"),
        "azelf" to MonumentInfo("azelf", "Lake Valor", "minecraft:overworld", "azelf_pedestal"),
        "celebi" to MonumentInfo("celebi", "Ilex Shrine", "minecraft:overworld", "celebi_shrine"),
        "darkrai" to MonumentInfo("darkrai", "Newmoon Island", "minecraft:overworld", "darkrai_pedestal"),
        "cresselia" to MonumentInfo("cresselia", "Fullmoon Island", "minecraft:overworld", "cresselia_pedestal"),
        "heatran" to MonumentInfo("heatran", "Stark Mountain", "minecraft:overworld", "heatran_pedestal"),
        "victini" to MonumentInfo("victini", "Liberty Garden", "minecraft:overworld", "victini_pedestal"),
        "zygarde" to MonumentInfo("zygarde", "Terminus Cave", "minecraft:overworld", "zygarde_altar"),
        "enamorus" to MonumentInfo("enamorus", "Hisuian Altar", "minecraft:overworld", "enamorus_pedestal"),
        "marshadow" to MonumentInfo("marshadow", "Mount Tensei", "minecraft:overworld", "marshadow_pedestal")
    )

    /**
     * Résout le nom d'affichage précis d'une structure ou empreinte.
     */
    fun resolveStructureName(blockId: ResourceLocation, speciesName: String?): String {
        val isFootprint = isFootprintBlock(blockId)
        val species = speciesName?.let {
            try { com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(it) } catch (_: Throwable) { null }
        }
        val pokeDisplayName = species?.translatedName?.string ?: speciesName?.replaceFirstChar { it.uppercase() }

        // 1. Dictionnaire des structures officielles connues
        val known = speciesName?.let { KNOWN_MONUMENTS[it.lowercase()] }
        if (known != null) {
            return known.structureDisplayName
        }

        // 2. Si c'est une empreinte
        if (isFootprint) {
            return if (pokeDisplayName != null) "Empreinte de $pokeDisplayName" else "Empreinte Légendaire"
        }

        // 3. Déduction élégante depuis le nom de bloc (ex: zacian_pedestal -> "Sanctuaire de Zacian")
        if (pokeDisplayName != null) {
            val path = blockId.path.lowercase()
            val type = when {
                path.contains("pedestal") -> "Piédestal"
                path.contains("shrine") -> "Sanctuaire"
                path.contains("altar") -> "Autel"
                path.contains("cocoon") -> "Cocon"
                path.contains("jukebox") -> "Jukebox"
                path.contains("temple") -> "Temple"
                else -> "Monument"
            }
            return "$type de $pokeDisplayName"
        }

        // 4. Nettoyage de l'identifiant brut du bloc (ex: "legendarymonuments:sword_temple" -> "Sword Temple")
        return blockId.path
            .replace('_', ' ')
            .split(' ')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Indique si le mod [legendarymonuments] est actuellement chargé dans l'environnement de jeu.
     */
    val isModLoaded: Boolean by lazy {
        // 1. Détection NeoForge
        try {
            val modListClass = Class.forName("net.neoforged.fml.ModList")
            val getMethod = modListClass.getMethod("get")
            val modList = getMethod.invoke(null)
            if (modList != null) {
                val isLoadedMethod = modList.javaClass.getMethod("isLoaded", String::class.java)
                val loaded = isLoadedMethod.invoke(modList, MOD_ID) as? Boolean
                if (loaded == true) return@lazy true
            }
        } catch (_: Throwable) {}

        // 2. Détection Fabric
        try {
            val fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader")
            val getInstanceMethod = fabricLoaderClass.getMethod("getInstance")
            val loader = getInstanceMethod.invoke(null)
            if (loader != null) {
                val isModLoadedMethod = loader.javaClass.getMethod("isModLoaded", String::class.java)
                val loaded = isModLoadedMethod.invoke(loader, MOD_ID) as? Boolean
                if (loaded == true) return@lazy true
            }
        } catch (_: Throwable) {}

        false
    }

    /**
     * Vérifie si un bloc correspond à un monument ou piédestal de Legendary Monuments.
     */
    fun isMonumentBlock(blockId: ResourceLocation): Boolean {
        val ns = blockId.namespace.lowercase(Locale.ROOT)
        val path = blockId.path.lowercase(Locale.ROOT)

        if (ns == MOD_ID) {
            // Dans le mod, les blocs significatifs sont les piédestaux, sanctuaires, cocons, autels, etc.
            return MONUMENT_BLOCK_KEYWORDS.any { path.contains(it) } ||
                    COVERED_LEGENDARY_SPECIES.any { path.contains(it.replace("-", "_")) }
        }

        // Support d'autres mods / datapacks partenaires
        return (path.endsWith("_pedestal") || path.endsWith("_shrine") || path.endsWith("_altar") || path.endsWith("_cocoon")) &&
                COVERED_LEGENDARY_SPECIES.any { path.contains(it.replace("-", "_")) }
    }

    /**
     * Tente de déduire le nom d'espèce de Pokémon à partir d'un identifiant de bloc.
     */
    fun extractSpeciesNameFromBlock(blockId: ResourceLocation): String? {
        val path = blockId.path.lowercase(Locale.ROOT).replace("-", "_")
        for (species in COVERED_LEGENDARY_SPECIES) {
            val sNormalized = species.lowercase(Locale.ROOT).replace("-", "_")
            if (path.contains(sNormalized)) {
                return species
            }
        }
        return null
    }

    /**
     * Modes de filtrage pour le tracking de Legendary Monuments.
     */
    enum class MonumentTrackingMode(
        val id: String,
        val translationKey: String,
        val shortLabel: String,
        val icon: String
    ) {
        MAJOR_ONLY("major_only", "gui.cobblemon_jm.monuments.mode.major", "Monuments", "🏛"),
        FOOTPRINTS_ONLY("footprints_only", "gui.cobblemon_jm.monuments.mode.footprints", "Empreintes", "🐾"),
        ALL("all", "gui.cobblemon_jm.monuments.mode.all", "Tous", "🏛+🐾"),
        DISABLED("disabled", "gui.cobblemon_jm.monuments.mode.disabled", "Désactivé", "⛔");

        fun next(): MonumentTrackingMode = entries[(ordinal + 1) % entries.size]
    }

    /**
     * Vérifie si un bloc correspond à une empreinte de pas (ex: Swords of Justice).
     */
    fun isFootprintBlock(blockId: ResourceLocation): Boolean {
        val path = blockId.path.lowercase(Locale.ROOT)
        return path.contains("footprint")
    }

    /**
     * Vérifie si un bloc correspond à un monument majeur (piédestal, sanctuaire, cocon, autel, etc.),
     * en excluant les simples empreintes au sol.
     */
    fun isMajorMonumentBlock(blockId: ResourceLocation): Boolean {
        return isMonumentBlock(blockId) && !isFootprintBlock(blockId)
    }

    /**
     * Détermine si un bloc doit être suivi selon le mode de tracking sélectionné.
     */
    fun matchesTrackingMode(blockId: ResourceLocation, mode: MonumentTrackingMode): Boolean {
        return when (mode) {
            MonumentTrackingMode.DISABLED -> false
            MonumentTrackingMode.ALL -> isMonumentBlock(blockId)
            MonumentTrackingMode.MAJOR_ONLY -> isMajorMonumentBlock(blockId)
            MonumentTrackingMode.FOOTPRINTS_ONLY -> isFootprintBlock(blockId)
        }
    }

    /**
     * Vérifie si une espèce de Pokémon donnée est gérée par Legendary Monuments.
     */
    fun isCoveredSpecies(speciesName: String): Boolean {
        val normalized = speciesName.lowercase(Locale.ROOT).trim()
        return COVERED_LEGENDARY_SPECIES.contains(normalized) ||
                COVERED_LEGENDARY_SPECIES.contains(normalized.replace(" ", "-")) ||
                COVERED_LEGENDARY_SPECIES.contains(normalized.replace(" ", "_"))
    }
}

