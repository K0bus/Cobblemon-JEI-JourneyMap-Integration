package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.data.PokemonEvolutionDataLoader
import fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader
import fr.k0bus.cobblemon_jei.journeymap.SpawnRarity
import fr.k0bus.cobblemon_jei.recipe.PokemonSpawnRecipe

/**
 * Modèle de filtrage par niveau de rareté Cobblemon.
 */
enum class SpawnRarityFilter(val id: String, val displayName: String, val formatCode: String, val rarity: SpawnRarity?) {
    ALL("all", "Toutes les raretés", "§f", null),
    COMMON("common", "Commun", "§a", SpawnRarity.COMMON),
    UNCOMMON("uncommon", "Peu commun", "§b", SpawnRarity.UNCOMMON),
    RARE("rare", "Rare", "§d", SpawnRarity.RARE),
    ULTRA_RARE("ultra_rare", "Ultra-Rare", "§6", SpawnRarity.ULTRA_RARE),
    NO_WILD_SPAWN("no_wild", "Sans spawn sauvage (Évo/Dons)", "§7", null)
}

/**
 * Modèle de filtrage par catégories de biomes officielles Cobblemon.
 */
enum class CobblemonBiomeFilter(
    val id: String,
    val icon: String,
    val displayName: String,
    val tagKeywords: List<String>
) {
    ALL("all", "🌐", "Tous les biomes", emptyList()),
    OVERWORLD("overworld", "🌍", "Overworld (Global)", listOf("is_overworld")),
    PLAINS("plains", "🌾", "Plaines & Prairies", listOf("is_plains", "plains", "meadow")),
    FOREST("forest", "🌲", "Forêts & Bois", listOf("is_forest", "forest", "birch", "dark_forest")),
    DESERT("desert", "🏜️", "Déserts", listOf("is_desert", "desert")),
    JUNGLE("jungle", "🌴", "Jungle & Bambous", listOf("is_jungle", "jungle", "bamboo")),
    SAVANNA("savanna", "🦁", "Savane", listOf("is_savanna", "savanna")),
    TAIGA("taiga", "🌲", "Taïga & Pins", listOf("is_taiga", "taiga", "old_growth")),
    MOUNTAIN("mountain", "🏔️", "Montagnes & Pics", listOf("is_mountain", "mountain", "peaks", "slopes", "jagged")),
    OCEAN("ocean", "🌊", "Océans & Mers", listOf("is_ocean", "ocean")),
    FRESHWATER("freshwater", "💧", "Rivières & Eau douce", listOf("is_freshwater", "river")),
    SWAMP("swamp", "🌿", "Marais & Mangroves", listOf("is_swamp", "swamp", "mangrove")),
    BEACH("beach", "🏖️", "Plages & Rivages", listOf("is_beach", "beach", "stony_shore")),
    BADLANDS("badlands", "🏜️", "Badlands & Mesa", listOf("is_badlands", "badlands", "mesa")),
    FREEZING("freezing", "❄️", "Grand froid & Neige", listOf("is_freezing", "is_snowy", "snowy", "ice", "frozen")),
    CAVE("cave", "🕳️", "Grottes & Souterrain", listOf("is_cave", "is_underground", "cave")),
    LUSH_CAVES("lush", "🌿", "Grottes luxuriantes", listOf("is_lush", "lush_caves")),
    DRIPSTONE("dripstone", "🪨", "Spéléothèmes", listOf("is_dripstone", "dripstone_caves")),
    DEEP_DARK("deep_dark", "👁️", "Tréfonds sombres", listOf("is_deep_dark", "deep_dark")),
    NETHER("nether", "🔥", "Nether", listOf("is_nether", "nether")),
    END("end", "🌌", "L'End", listOf("is_end", "end")),
    VOLCANIC("volcanic", "🌋", "Volcanique & Basalte", listOf("is_volcanic", "volcanic", "basalt")),
    SPOOKY("spooky", "👻", "Sinistre & Âmes", listOf("is_spooky", "soul_sand", "dark_forest")),
    MAGICAL("magical", "✨", "Magique & Cerisiers", listOf("is_magical", "cherry_grove", "mushroom"))
}

/**
 * Modèle de filtrage par génération (région).
 */
enum class GenerationFilter(
    val id: String,
    val displayName: String,
    val range: IntRange?
) {
    ALL("all", "Toutes les générations", null),
    GEN_1("gen1", "Gen 1 • Kanto", 1..151),
    GEN_2("gen2", "Gen 2 • Johto", 152..251),
    GEN_3("gen3", "Gen 3 • Hoenn", 252..386),
    GEN_4("gen4", "Gen 4 • Sinnoh", 387..493),
    GEN_5("gen5", "Gen 5 • Unys", 494..649),
    GEN_6("gen6", "Gen 6 • Kalos", 650..721),
    GEN_7("gen7", "Gen 7 • Alola", 722..809),
    GEN_8("gen8", "Gen 8 • Galar & Hisui", 810..905),
    GEN_9("gen9", "Gen 9 • Paldea", 906..2000)
}

/**
 * Modèle de filtrage par statut Pokédex.
 */
enum class StatusFilter(val id: String, val displayName: String) {
    ALL("all", "Tous les statuts"),
    CAUGHT("caught", "Capturés uniquement"),
    ENCOUNTERED("encountered", "Vus uniquement"),
    MISSING("missing", "Manquants uniquement")
}

/**
 * Modèle de filtrage par potentiel d'évolution.
 */
enum class EvolutionFilter(val id: String, val displayName: String) {
    ALL("all", "Tous les stades"),
    CAN_EVOLVE("can_evolve", "Peut encore évoluer"),
    FULLY_EVOLVED("fully_evolved", "Forme finale (Ne peut plus évoluer)"),
    NO_EVOLUTION("no_evolution", "Sans évolution (Pokémon unique)")
}

/**
 * Modèle d'ordre et de tri des résultats.
 */
enum class PokedexSortOrder(val id: String, val displayName: String) {
    DEX_ASC("dex_asc", "Numéro Pokédex (1 → 1025)"),
    DEX_DESC("dex_desc", "Numéro Pokédex inversé (1025 → 1)"),
    NAME_ASC("name_asc", "Nom alphabétique (A → Z)"),
    NAME_DESC("name_desc", "Nom alphabétique (Z → A)"),
    BST_DESC("bst_desc", "Total Stats de base (Fort → Faible)"),
    BST_ASC("bst_asc", "Total Stats de base (Faible → Fort)")
}

/**
 * Catégories d'onglets du panneau modal de filtrage.
 */
enum class FilterCategory(val label: String, val icon: String) {
    TYPE("Types", "⚡"),
    RARITY("Rareté", "⭐"),
    BIOME("Biomes", "🌍"),
    GEN("Générations", "📜"),
    STATUS("Statuts & Évo", "📊"),
    SORT("Tri & Ordre", "⇅")
}

/**
 * Définition d'un type élémentaire pour le sélecteur.
 */
data class PokemonTypeOption(val id: String, val displayName: String, val color: Int)

/**
 * État consolidé de tous les filtres actifs dans le Pokédex.
 */
data class PokedexFilterState(
    var selectedTypes: MutableSet<String> = mutableSetOf(),
    var selectedRarity: SpawnRarityFilter = SpawnRarityFilter.ALL,
    var selectedBiome: CobblemonBiomeFilter = CobblemonBiomeFilter.ALL,
    var selectedGen: GenerationFilter = GenerationFilter.ALL,
    var selectedStatus: StatusFilter = StatusFilter.ALL,
    var selectedEvolution: EvolutionFilter = EvolutionFilter.ALL,
    var selectedSort: PokedexSortOrder = PokedexSortOrder.DEX_ASC
) {
    fun isActive(): Boolean {
        return selectedTypes.isNotEmpty() ||
                selectedRarity != SpawnRarityFilter.ALL ||
                selectedBiome != CobblemonBiomeFilter.ALL ||
                selectedGen != GenerationFilter.ALL ||
                selectedStatus != StatusFilter.ALL ||
                selectedEvolution != EvolutionFilter.ALL ||
                selectedSort != PokedexSortOrder.DEX_ASC
    }

    fun activeCount(): Int {
        var count = 0
        if (selectedTypes.isNotEmpty()) count += selectedTypes.size
        if (selectedRarity != SpawnRarityFilter.ALL) count++
        if (selectedBiome != CobblemonBiomeFilter.ALL) count++
        if (selectedGen != GenerationFilter.ALL) count++
        if (selectedStatus != StatusFilter.ALL) count++
        if (selectedEvolution != EvolutionFilter.ALL) count++
        if (selectedSort != PokedexSortOrder.DEX_ASC) count++
        return count
    }

    fun reset() {
        selectedTypes.clear()
        selectedRarity = SpawnRarityFilter.ALL
        selectedBiome = CobblemonBiomeFilter.ALL
        selectedGen = GenerationFilter.ALL
        selectedStatus = StatusFilter.ALL
        selectedEvolution = EvolutionFilter.ALL
        selectedSort = PokedexSortOrder.DEX_ASC
    }
}

/**
 * Service d'indexation, de filtrage et de tri pour le Pokédex.
 */
object PokedexFilterManager {

    val ALL_TYPES: List<PokemonTypeOption> = listOf(
        PokemonTypeOption("normal", "Normal", 0xFFA8A878.toInt()),
        PokemonTypeOption("fire", "Feu", 0xFFEB4D4B.toInt()),
        PokemonTypeOption("water", "Eau", 0xFF3867D6.toInt()),
        PokemonTypeOption("grass", "Plante", 0xFF20BF6B.toInt()),
        PokemonTypeOption("electric", "Électrik", 0xFFF7B731.toInt()),
        PokemonTypeOption("ice", "Glace", 0xFF45AAF2.toInt()),
        PokemonTypeOption("fighting", "Combat", 0xFFB71540.toInt()),
        PokemonTypeOption("poison", "Poison", 0xFF8854D0.toInt()),
        PokemonTypeOption("ground", "Sol", 0xFFE1B12C.toInt()),
        PokemonTypeOption("flying", "Vol", 0xFF9980FA.toInt()),
        PokemonTypeOption("psychic", "Psy", 0xFFED4C67.toInt()),
        PokemonTypeOption("bug", "Insecte", 0xFF26DE81.toInt()),
        PokemonTypeOption("rock", "Roche", 0xFF778CA3.toInt()),
        PokemonTypeOption("ghost", "Spectre", 0xFF57606F.toInt()),
        PokemonTypeOption("dragon", "Dragon", 0xFF3B3B98.toInt()),
        PokemonTypeOption("dark", "Ténèbres", 0xFF2F3542.toInt()),
        PokemonTypeOption("steel", "Acier", 0xFF718093.toInt()),
        PokemonTypeOption("fairy", "Fée", 0xFFFDA7DF.toInt())
    )

    data class SpeciesSpawnMetadata(
        val rarities: Set<SpawnRarity>,
        val biomes: Set<String>,
        val dimensions: Set<String>,
        val hasWildSpawns: Boolean
    )

    data class EvolutionMetadata(
        val canEvolve: Boolean,
        val isEvolutionOfSomething: Boolean
    )

    private var spawnMetadataCache: Map<String, SpeciesSpawnMetadata>? = null
    private var evolutionMetadataCache: Map<String, EvolutionMetadata>? = null
    private val bstCache = mutableMapOf<String, Int>()

    fun getSpawnMetadata(species: Species): SpeciesSpawnMetadata {
        var cache = spawnMetadataCache
        if (cache == null) {
            val allRecipes = PokemonSpawnDataLoader.loadSpawnRecipes()
            val map = mutableMapOf<String, MutableList<PokemonSpawnRecipe>>()
            for (recipe in allRecipes) {
                val key = recipe.species.name.lowercase()
                map.getOrPut(key) { mutableListOf() }.add(recipe)
            }
            val built = mutableMapOf<String, SpeciesSpawnMetadata>()
            for ((key, recipes) in map) {
                val naturalRecipes = recipes.filter { it.isNaturalSpawn }
                val rarities = naturalRecipes.map { SpawnRarity.fromBucketOrWeight(it.bucket, it.weight) }.toSet()
                val biomes = naturalRecipes.flatMap { it.biomes }.toSet()
                val dimensions = naturalRecipes.flatMap { it.dimensions }.toSet()
                built[key] = SpeciesSpawnMetadata(
                    rarities = rarities,
                    biomes = biomes,
                    dimensions = dimensions,
                    hasWildSpawns = naturalRecipes.isNotEmpty()
                )
            }
            spawnMetadataCache = built
            cache = built
        }
        return cache[species.name.lowercase()] ?: SpeciesSpawnMetadata(emptySet(), emptySet(), emptySet(), false)
    }

    fun getEvolutionMetadata(species: Species): EvolutionMetadata {
        var cache = evolutionMetadataCache
        if (cache == null) {
            val recipes = PokemonEvolutionDataLoader.loadEvolutionRecipes()
            val sources = recipes.map { it.sourceSpecies.name.lowercase() }.toSet()
            val targets = recipes.map { it.targetSpecies.name.lowercase() }.toSet()

            val built = mutableMapOf<String, EvolutionMetadata>()
            for (name in (sources + targets)) {
                built[name] = EvolutionMetadata(
                    canEvolve = sources.contains(name),
                    isEvolutionOfSomething = targets.contains(name)
                )
            }
            evolutionMetadataCache = built
            cache = built
        }
        val key = species.name.lowercase()
        return cache[key] ?: EvolutionMetadata(canEvolve = false, isEvolutionOfSomething = false)
    }

    fun getBaseStatTotal(species: Species): Int {
        val key = species.name.lowercase()
        return bstCache.computeIfAbsent(key) {
            val bStats = species.standardForm.baseStats
            (bStats[Stats.HP] ?: 0) +
                    (bStats[Stats.ATTACK] ?: 0) +
                    (bStats[Stats.DEFENCE] ?: 0) +
                    (bStats[Stats.SPECIAL_ATTACK] ?: 0) +
                    (bStats[Stats.SPECIAL_DEFENCE] ?: 0) +
                    (bStats[Stats.SPEED] ?: 0)
        }
    }

    fun matches(item: TrackedPokemon, state: PokedexFilterState): Boolean {
        // 1. Types
        if (state.selectedTypes.isNotEmpty()) {
            if (!matchesTypes(item.species, state.selectedTypes)) return false
        }

        // 2. Rareté
        if (state.selectedRarity != SpawnRarityFilter.ALL) {
            val spawnMeta = getSpawnMetadata(item.species)
            when (state.selectedRarity) {
                SpawnRarityFilter.NO_WILD_SPAWN -> {
                    if (spawnMeta.hasWildSpawns) return false
                }
                else -> {
                    val targetRarity = state.selectedRarity.rarity
                    if (targetRarity == null || !spawnMeta.rarities.contains(targetRarity)) return false
                }
            }
        }

        // 3. Biome
        if (state.selectedBiome != CobblemonBiomeFilter.ALL) {
            val spawnMeta = getSpawnMetadata(item.species)
            if (!matchesBiome(spawnMeta, state.selectedBiome)) return false
        }

        // 4. Génération
        if (state.selectedGen != GenerationFilter.ALL) {
            val range = state.selectedGen.range
            if (range != null && item.nationalDexNumber !in range) return false
        }

        // 5. Statut Pokédex
        if (state.selectedStatus != StatusFilter.ALL) {
            val ok = when (state.selectedStatus) {
                StatusFilter.ALL -> true
                StatusFilter.CAUGHT -> item.isCaught
                StatusFilter.ENCOUNTERED -> item.isEncountered
                StatusFilter.MISSING -> item.isMissing
            }
            if (!ok) return false
        }

        // 6. Évolution
        if (state.selectedEvolution != EvolutionFilter.ALL) {
            val evo = getEvolutionMetadata(item.species)
            val ok = when (state.selectedEvolution) {
                EvolutionFilter.ALL -> true
                EvolutionFilter.CAN_EVOLVE -> evo.canEvolve
                EvolutionFilter.FULLY_EVOLVED -> !evo.canEvolve && evo.isEvolutionOfSomething
                EvolutionFilter.NO_EVOLUTION -> !evo.canEvolve && !evo.isEvolutionOfSomething
            }
            if (!ok) return false
        }

        return true
    }

    fun matchesTypes(species: Species, selectedTypes: Set<String>): Boolean {
        if (selectedTypes.isEmpty()) return true
        val p1 = species.standardForm.primaryType.name.lowercase()
        val s1 = species.standardForm.secondaryType?.name?.lowercase()
        if (selectedTypes.contains(p1) || (s1 != null && selectedTypes.contains(s1))) return true

        for (form in species.forms) {
            val fp = form.primaryType.name.lowercase()
            val fs = form.secondaryType?.name?.lowercase()
            if (selectedTypes.contains(fp) || (fs != null && selectedTypes.contains(fs))) return true
        }
        return false
    }

    fun matchesBiome(metadata: SpeciesSpawnMetadata, filter: CobblemonBiomeFilter): Boolean {
        if (filter == CobblemonBiomeFilter.ALL) return true
        if (!metadata.hasWildSpawns) return false

        // Spécificités de dimension
        if (filter == CobblemonBiomeFilter.OVERWORLD && metadata.dimensions.any { it.contains("overworld") }) {
            if (metadata.biomes.isEmpty()) return true
        }
        if (filter == CobblemonBiomeFilter.NETHER && metadata.dimensions.any { it.contains("nether") }) {
            if (metadata.biomes.isEmpty()) return true
        }
        if (filter == CobblemonBiomeFilter.END && metadata.dimensions.any { it.contains("end") }) {
            if (metadata.biomes.isEmpty()) return true
        }

        return metadata.biomes.any { b ->
            val clean = b.removePrefix("#").substringAfter(":").lowercase()
            filter.tagKeywords.any { kw -> clean.contains(kw) }
        }
    }

    fun sort(items: List<TrackedPokemon>, sortOrder: PokedexSortOrder): List<TrackedPokemon> {
        return when (sortOrder) {
            PokedexSortOrder.DEX_ASC -> items.sortedBy { it.nationalDexNumber }
            PokedexSortOrder.DEX_DESC -> items.sortedByDescending { it.nationalDexNumber }
            PokedexSortOrder.NAME_ASC -> items.sortedBy { it.species.translatedName.string.lowercase() }
            PokedexSortOrder.NAME_DESC -> items.sortedByDescending { it.species.translatedName.string.lowercase() }
            PokedexSortOrder.BST_DESC -> items.sortedByDescending { getBaseStatTotal(it.species) }
            PokedexSortOrder.BST_ASC -> items.sortedBy { getBaseStatTotal(it.species) }
        }
    }
}
