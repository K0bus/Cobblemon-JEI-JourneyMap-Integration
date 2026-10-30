package fr.k0bus.cobblemon_jei.data

import com.cobblemon.mod.common.api.fossil.Fossils
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.JsonParser
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Chargeur et gestionnaire centralisé des recettes de résurrection de fossiles Cobblemon.
 * Scanne dynamiquement les définitions JSON issues des datapacks et de Cobblemon (data/cobblemon/fossils),
 * tout en exploitant le registre officiel [Fossils] à l'exécution.
 * Associe chaque fossile à son espèce résultante, ses structures archéologiques de fouille
 * et ses conditions de reconstitution via la machine de résurrection.
 */
object PokemonFossilDataLoader {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/FossilDataLoader")

    data class FossilRecipeInfo(
        val id: ResourceLocation,
        val resultSpeciesName: String,
        val resultSpecies: Species?,
        val resultForm: FormData?,
        val requiredFossilItemIds: List<String>,
        val requiredFossilStacks: List<ItemStack>,
        val fossilDisplayName: String,
        val associatedStructures: List<String>,
        val associatedStructureDisplayNames: List<String>
    ) {
        val nationalDexNumber: Int get() = resultSpecies?.nationalPokedexNumber ?: 9999
        val speciesDisplayName: String get() = resultSpecies?.translatedName?.string ?: resultSpeciesName.replaceFirstChar { it.uppercase() }
    }

    private val cachedRecipes = mutableListOf<FossilRecipeInfo>()
    private val recipeByResultSpecies = mutableMapOf<String, FossilRecipeInfo>()
    private var isLoaded = false

    // Noms français formatés des fossiles
    val FOSSIL_NAMES_FR = mapOf(
        "helix_fossil" to "Fossile Nautile",
        "dome_fossil" to "Fossile Dôme",
        "old_amber_fossil" to "Vieil Ambre",
        "root_fossil" to "Fossile Racine",
        "claw_fossil" to "Fossile Griffe",
        "skull_fossil" to "Fossile Crâne",
        "armor_fossil" to "Fossile Armure",
        "cover_fossil" to "Fossile Plaque",
        "plume_fossil" to "Fossile Plume",
        "jaw_fossil" to "Fossile Mâchoire",
        "sail_fossil" to "Fossile Nageoire",
        "fossilized_bird" to "Oiseau fossilisé",
        "fossilized_fish" to "Poisson fossilisé",
        "fossilized_drake" to "Dragon fossilisé",
        "fossilized_dino" to "Dino fossilisé"
    )

    // Noms lisibles des structures de fouilles préhistoriques
    val STRUCTURE_DISPLAY_NAMES_FR = mapOf(
        "oak_tree" to "Chêne Préhistorique",
        "spruce_tree" to "Épicéa Préhistorique",
        "birch_tree" to "Bouleau Préhistorique",
        "sandy_den" to "Repaire Sableux Préhistorique",
        "hydrothermal_vents" to "Cheminées Hydrothermales",
        "vibrant_hydrothermal_vents" to "Cheminées Hydrothermales Vives",
        "rooted_pit" to "Fosse Enracinée",
        "dripstone_oasis" to "Oasis de Spéléothèmes",
        "mossy_pond" to "Étang Moussu Préhistorique",
        "mud_pit" to "Fosse Boueuse Préhistorique",
        "sunscorched_den" to "Repaire Calciné",
        "sunscorched_remains" to "Vestiges Calcinés",
        "submerged_spike" to "Pointe Submergée",
        "submerged_impact" to "Cratère Submergé",
        "underwater_fissure" to "Fissure Sous-marine",
        "eroded_pillar" to "Pilier Érodé",
        "lush_den" to "Repaire Luxuriant",
        "frozen_pond" to "Étang Gelé",
        "powdered_deposit" to "Gisement Poudreux",
        "suspicious_mound" to "Tertre Suspect",
        "enhydro_agate" to "Agate Enhydre",
        "frozen_spike" to "Pic Gelé",
        "preserved_skeleton" to "Squelette Préhistorique Préservé"
    )

    // Mappage des structures archéologiques Cobblemon vers les items de fossiles trouvables
    private val STRUCTURE_TO_FOSSILS = mapOf(
        "oak_tree" to listOf("old_amber_fossil"),
        "spruce_tree" to listOf("old_amber_fossil"),
        "birch_tree" to listOf("plume_fossil"),
        "sandy_den" to listOf("helix_fossil"),
        "hydrothermal_vents" to listOf("dome_fossil"),
        "rooted_pit" to listOf("root_fossil"),
        "dripstone_oasis" to listOf("claw_fossil"),
        "mossy_pond" to listOf("claw_fossil"),
        "mud_pit" to listOf("skull_fossil"),
        "sunscorched_den" to listOf("armor_fossil"),
        "submerged_spike" to listOf("cover_fossil"),
        "underwater_fissure" to listOf("cover_fossil"),
        "vibrant_hydrothermal_vents" to listOf("cover_fossil"),
        "eroded_pillar" to listOf("jaw_fossil"),
        "lush_den" to listOf("jaw_fossil"),
        "frozen_pond" to listOf("sail_fossil"),
        "powdered_deposit" to listOf("sail_fossil"),
        "suspicious_mound" to listOf("fossilized_bird"),
        "enhydro_agate" to listOf("fossilized_fish"),
        "submerged_impact" to listOf("fossilized_fish"),
        "sunscorched_remains" to listOf("fossilized_drake"),
        "frozen_spike" to listOf("fossilized_dino"),
        "preserved_skeleton" to listOf("fossilized_bird", "fossilized_fish", "fossilized_drake", "fossilized_dino")
    )

    // Recettes natives de secours garantissant 100% de disponibilité
    private val DEFAULT_FOSSIL_RECIPES = listOf(
        Pair("omanyte", listOf("cobblemon:helix_fossil")),
        Pair("kabuto", listOf("cobblemon:dome_fossil")),
        Pair("aerodactyl", listOf("cobblemon:old_amber_fossil")),
        Pair("lileep", listOf("cobblemon:root_fossil")),
        Pair("anorith", listOf("cobblemon:claw_fossil")),
        Pair("cranidos", listOf("cobblemon:skull_fossil")),
        Pair("shieldon", listOf("cobblemon:armor_fossil")),
        Pair("tirtouga", listOf("cobblemon:cover_fossil")),
        Pair("archen", listOf("cobblemon:plume_fossil")),
        Pair("tyrunt", listOf("cobblemon:jaw_fossil")),
        Pair("amaura", listOf("cobblemon:sail_fossil")),
        Pair("dracozolt", listOf("cobblemon:fossilized_bird", "cobblemon:fossilized_drake")),
        Pair("arctozolt", listOf("cobblemon:fossilized_bird", "cobblemon:fossilized_dino")),
        Pair("dracovish", listOf("cobblemon:fossilized_fish", "cobblemon:fossilized_drake")),
        Pair("arctovish", listOf("cobblemon:fossilized_fish", "cobblemon:fossilized_dino"))
    )

    /**
     * S'assure que les recettes de fossiles sont chargées et consolidées.
     */
    @Synchronized
    fun ensureLoaded() {
        if (isLoaded && cachedRecipes.isNotEmpty()) return

        PokemonEvolutionDataLoader.ensureSpeciesLoaded()
        val collectedRecipes = mutableListOf<FossilRecipeInfo>()
        val seenSpecies = mutableSetOf<String>()

        // 1. Scan direct des ressources JSON (data/cobblemon/fossils)
        try {
            CobblemonDataScanner.scanDataResources("fossils") { filePath, stream ->
                try {
                    val normPath = filePath.replace('\\', '/')
                    if (normPath.contains("/worldgen/") || normPath.contains("/structure/") || normPath.contains("/tags/")) {
                        return@scanDataResources
                    }

                    val jsonText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JsonParser.parseString(jsonText).asJsonObject
                    val fileId = File(filePath).nameWithoutExtension.lowercase()

                    val resultStr = if (json.has("result")) json.get("result").asString else fileId
                    val resultClean = resultStr.split(" ")[0].lowercase()

                    val fossilItemIds = mutableListOf<String>()
                    if (json.has("fossils") && json.get("fossils").isJsonArray) {
                        for (elem in json.getAsJsonArray("fossils")) {
                            if (elem.isJsonPrimitive) {
                                fossilItemIds.add(elem.asString.lowercase())
                            }
                        }
                    }

                    if (fossilItemIds.isNotEmpty() && !seenSpecies.contains(resultClean)) {
                        val recipe = buildFossilRecipeInfo(
                            ResourceLocation.fromNamespaceAndPath("cobblemon", fileId),
                            resultClean,
                            fossilItemIds
                        )
                        seenSpecies.add(resultClean)
                        collectedRecipes.add(recipe)
                    }
                } catch (e: Throwable) {
                    logger.debug("Erreur parsing fichier fossile {}: {}", filePath, e.message)
                }
            }
        } catch (e: Throwable) {
            logger.debug("Erreur scanDataResources fossils: {}", e.message)
        }

        // 2. Inspection du registre officiel Fossils.all() si disponible
        try {
            val allFossils = Fossils.all()
            for (fossil in allFossils) {
                val spName = try { fossil.result.species?.lowercase() ?: "" } catch (_: Throwable) { "" }
                if (spName.isNotBlank() && !seenSpecies.contains(spName)) {
                    val defaultEntry = DEFAULT_FOSSIL_RECIPES.firstOrNull { it.first == spName }
                    val items = defaultEntry?.second ?: emptyList()

                    if (items.isNotEmpty()) {
                        val recipe = buildFossilRecipeInfo(fossil.identifier, spName, items)
                        seenSpecies.add(spName)
                        collectedRecipes.add(recipe)
                    }
                }
            }
        } catch (_: Throwable) {}

        // 3. Fallback complet sur les 15 recettes natives Cobblemon si non détectées
        for ((spName, itemIds) in DEFAULT_FOSSIL_RECIPES) {
            if (!seenSpecies.contains(spName)) {
                val recipe = buildFossilRecipeInfo(
                    ResourceLocation.fromNamespaceAndPath("cobblemon", spName),
                    spName,
                    itemIds
                )
                seenSpecies.add(spName)
                collectedRecipes.add(recipe)
            }
        }

        // Tri par numéro Pokédex national
        collectedRecipes.sortBy { it.nationalDexNumber }

        cachedRecipes.clear()
        cachedRecipes.addAll(collectedRecipes)

        recipeByResultSpecies.clear()
        for (r in cachedRecipes) {
            recipeByResultSpecies[r.resultSpeciesName.lowercase()] = r
            r.resultSpecies?.let {
                recipeByResultSpecies[it.name.lowercase()] = r
                val clean = it.name.lowercase().replace(" ", "").replace("-", "")
                recipeByResultSpecies[clean] = r
            }
        }

        isLoaded = true
        logger.info("Cobblemon JEI: ${cachedRecipes.size} recettes de résurrection de fossiles prêtes.")
    }

    private fun buildFossilRecipeInfo(
        id: ResourceLocation,
        resultSpeciesName: String,
        fossilItemIds: List<String>
    ): FossilRecipeInfo {
        val species = PokemonEvolutionDataLoader.findSpeciesByName(resultSpeciesName)
        val form = species?.standardForm

        val stacks = mutableListOf<ItemStack>()
        val displayNames = mutableListOf<String>()

        for (itemId in fossilItemIds) {
            val cleanId = if (itemId.contains(":")) itemId.substringAfter(":") else itemId
            val itemLoc = if (itemId.contains(":")) ResourceLocation.parse(itemId) else ResourceLocation.fromNamespaceAndPath("cobblemon", itemId)
            val item = try { BuiltInRegistries.ITEM.get(itemLoc) } catch (_: Throwable) { null }

            if (item != null && item != Items.AIR) {
                try { stacks.add(ItemStack(item)) } catch (_: Throwable) {}
            } else {
                val fallbackStack = getCobblemonItemFallback(cleanId)
                if (!fallbackStack.isEmpty) stacks.add(fallbackStack)
            }

            val frName = FOSSIL_NAMES_FR[cleanId] ?: cleanId.replace('_', ' ').replaceFirstChar { it.uppercase() }
            displayNames.add(frName)
        }

        // Recherche des structures archéologiques associées à ces items
        val associatedStructs = mutableListOf<String>()
        val associatedNames = mutableListOf<String>()

        for (itemId in fossilItemIds) {
            val cleanId = if (itemId.contains(":")) itemId.substringAfter(":") else itemId
            for ((structKey, fossilList) in STRUCTURE_TO_FOSSILS) {
                if (fossilList.contains(cleanId)) {
                    if (!associatedStructs.contains(structKey)) {
                        associatedStructs.add(structKey)
                        val nameFr = STRUCTURE_DISPLAY_NAMES_FR[structKey] ?: structKey.replace('_', ' ').replaceFirstChar { it.uppercase() }
                        associatedNames.add(nameFr)
                    }
                }
            }
        }

        val fossilTitle = displayNames.joinToString(" + ")

        return FossilRecipeInfo(
            id = id,
            resultSpeciesName = resultSpeciesName,
            resultSpecies = species,
            resultForm = form,
            requiredFossilItemIds = fossilItemIds,
            requiredFossilStacks = stacks,
            fossilDisplayName = fossilTitle,
            associatedStructures = associatedStructs,
            associatedStructureDisplayNames = associatedNames
        )
    }

    private fun getCobblemonItemFallback(cleanId: String): ItemStack {
        return try {
            val itemLoc = ResourceLocation.fromNamespaceAndPath("cobblemon", cleanId)
            val item = BuiltInRegistries.ITEM.get(itemLoc)
            if (item != Items.AIR) ItemStack(item) else ItemStack.EMPTY
        } catch (_: Throwable) {
            ItemStack.EMPTY
        }
    }

    /**
     * Retourne la recette de résurrection directe pour une espèce donnée, ou null si elle n'est pas ressuscitée directement.
     */
    fun getFossilRecipeForSpecies(species: Species): FossilRecipeInfo? {
        ensureLoaded()
        val key = species.name.lowercase()
        return recipeByResultSpecies[key] ?: recipeByResultSpecies[species.resourceIdentifier.path.lowercase()]
    }

    /**
     * Détermine si une espèce (ou l'une de ses pré-évolutions) provient initialement d'un fossile.
     * Exemple : Amonistar ➔ Amonita ➔ Fossile Nautile.
     * @return La paire (Espèce de base ressuscitée, Recette de fossile) ou null.
     */
    fun getFossilLineageOrigin(species: Species): Pair<Species, FossilRecipeInfo>? {
        ensureLoaded()
        // Vérification directe
        val direct = getFossilRecipeForSpecies(species)
        if (direct != null) return Pair(species, direct)

        // Parcours récursif des pré-évolutions
        var current: Species? = species
        val visited = mutableSetOf<String>()

        while (current != null) {
            val curKey = current.name.lowercase()
            if (!visited.add(curKey)) break

            val recipe = recipeByResultSpecies[curKey]
            if (recipe != null) {
                return Pair(current, recipe)
            }

            // Remonte la pré-évolution
            val preEvo = try { current.preEvolution } catch (_: Throwable) { null }
            if (preEvo != null) {
                current = preEvo.species
            } else {
                val preRecipe = PokemonEvolutionDataLoader.loadEvolutionRecipes().firstOrNull {
                    it.targetSpecies.name.equals(curKey, ignoreCase = true)
                }
                current = preRecipe?.sourceSpecies
            }
        }

        return null
    }

    /**
     * Vérifie si une espèce fait partie d'une lignée préhistorique issue d'un fossile.
     */
    fun isFossilLineage(species: Species): Boolean {
        return getFossilLineageOrigin(species) != null
    }

    /**
     * Retourne toutes les recettes de résurrection enregistrées.
     */
    fun getAllFossilRecipes(): List<FossilRecipeInfo> {
        ensureLoaded()
        return cachedRecipes
    }

    /**
     * Vérifie si le joueur possède dans son inventaire les objets de fossiles requis pour une recette donnée.
     */
    fun isPlayerHoldingFossils(player: Player?, info: FossilRecipeInfo): Boolean {
        if (player == null) return false
        val inventory = player.inventory ?: return false

        for (reqStack in info.requiredFossilStacks) {
            val reqItem = reqStack.item
            var count = 0
            for (slotStack in inventory.items) {
                if (slotStack.item == reqItem) {
                    count += slotStack.count
                }
            }
            if (count < 1) return false
        }
        return true
    }

    /**
     * Compte le nombre d'exemplaires d'un fossile détenus dans l'inventaire du joueur.
     */
    fun getPlayerFossilCount(player: Player?, fossilItemId: String): Int {
        if (player == null) return 0
        val cleanId = if (fossilItemId.contains(":")) fossilItemId.substringAfter(":") else fossilItemId
        val itemLoc = if (fossilItemId.contains(":")) ResourceLocation.parse(fossilItemId) else ResourceLocation.fromNamespaceAndPath("cobblemon", fossilItemId)
        val item = BuiltInRegistries.ITEM.get(itemLoc)
        if (item == Items.AIR) return 0

        var count = 0
        for (slotStack in player.inventory.items) {
            if (slotStack.item == item) {
                count += slotStack.count
            }
        }
        return count
    }
}
