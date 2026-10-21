package fr.k0bus.cobblemon_jei.data

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.berry.Berries
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.item.berry.BerryItem
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Recommandation d'un assaisonnement pour le Poké Snack.
 */
data class SeasoningRecommendation(
    val slotNumber: Int,
    val itemStack: ItemStack,
    val itemId: String,
    val title: String,
    val roleDescription: String,
    val howToCreate: HowToCreate,
    val whereToFind: WhereToFind
)

sealed class HowToCreate {
    data class BerryMutation(
        val parent1: ItemStack,
        val parent2: ItemStack,
        val description: String
    ) : HowToCreate()

    data class Crafting(
        val recipeDescription: String,
        val ingredients: List<ItemStack>
    ) : HowToCreate()

    data class NonCraftable(
        val reason: String
    ) : HowToCreate()
}

data class WhereToFind(
    val naturalBiomes: List<String>,
    val mobDrops: List<MobDropInfo>,
    val specialLocations: List<String>
)

data class MobDropInfo(
    val speciesName: String,
    val percentage: Float
)

data class PokeSnackAdvice(
    val targetSpecies: Species,
    val targetForm: FormData,
    val hasWildSpawns: Boolean,
    val baseRarity: String?,
    val fallbackPreEvolution: Species?,
    val seasonings: List<SeasoningRecommendation>,
    val potBaseIngredients: List<ItemStack>,
    val explanationSummary: String
)

/**
 * Service de calcul et de résolution des assaisonnements optimaux de Poké Snack.
 *
 * Dans Cobblemon, la cuisson d'un Poké Snack dans un Chaudron de feu de camp prend
 * 4 ingrédients de base fixes et 3 emplacements d'assaisonnement (Seasoning Slots).
 *
 * Ce résolveur calcule mathématiquement la meilleure combinaison de 3 assaisonnements
 * selon les types élémentaires, les rendements d'EV, les groupes d'œufs et la rareté
 * de la cible, et fournit au joueur l'origine exacte (biomes, butins, mutations, crafts)
 * pour chaque ingrédient.
 */
object PokemonPokeSnackResolver {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/PokeSnackResolver")
    private val adviceCache = ConcurrentHashMap<String, PokeSnackAdvice>()
    private val mutationCache = ConcurrentHashMap<String, List<Pair<ItemStack, ItemStack>>>()

    // 1. Baies ciblées par Type élémentaire (multiplient le poids d'apparition par 10)
    private val typeBerries = mapOf(
        "normal" to "cobblemon:chilan_berry",
        "fire" to "cobblemon:occa_berry",
        "water" to "cobblemon:passho_berry",
        "grass" to "cobblemon:rindo_berry",
        "electric" to "cobblemon:wacan_berry",
        "ice" to "cobblemon:yache_berry",
        "fighting" to "cobblemon:chople_berry",
        "poison" to "cobblemon:kebia_berry",
        "ground" to "cobblemon:shuca_berry",
        "flying" to "cobblemon:coba_berry",
        "psychic" to "cobblemon:payapa_berry",
        "bug" to "cobblemon:tanga_berry",
        "rock" to "cobblemon:charti_berry",
        "ghost" to "cobblemon:kasib_berry",
        "dragon" to "cobblemon:haban_berry",
        "steel" to "cobblemon:babiri_berry",
        "dark" to "cobblemon:colbur_berry",
        "fairy" to "cobblemon:roseli_berry"
    )

    // 2. Baies ciblées par Groupe d'Œuf (multiplient le poids d'apparition par 10)
    private val eggGroupBerries = listOf(
        EggGroup.DRAGON to "cobblemon:lum_berry",
        EggGroup.MONSTER to "cobblemon:lum_berry",
        EggGroup.FAIRY to "cobblemon:cheri_berry",
        EggGroup.GRASS to "cobblemon:cheri_berry",
        EggGroup.HUMAN_LIKE to "cobblemon:chesto_berry",
        EggGroup.FLYING to "cobblemon:chesto_berry",
        EggGroup.WATER_1 to "cobblemon:aspear_berry",
        EggGroup.WATER_2 to "cobblemon:aspear_berry",
        EggGroup.WATER_3 to "cobblemon:pecha_berry",
        EggGroup.BUG to "cobblemon:pecha_berry",
        EggGroup.MINERAL to "cobblemon:persim_berry",
        EggGroup.AMORPHOUS to "cobblemon:persim_berry",
        EggGroup.FIELD to "cobblemon:rawst_berry"
    )

    // 3. Baies ciblées par Rendement d'EV (agissent comme un filtre strict éliminant les rivaux sans cet EV)
    private val evBerries = mapOf(
        "hp" to "cobblemon:pomeg_berry",
        "attack" to "cobblemon:kelpsy_berry",
        "atk" to "cobblemon:kelpsy_berry",
        "defence" to "cobblemon:qualot_berry",
        "defense" to "cobblemon:qualot_berry",
        "def" to "cobblemon:qualot_berry",
        "special_attack" to "cobblemon:hondew_berry",
        "spa" to "cobblemon:hondew_berry",
        "special_defence" to "cobblemon:grepa_berry",
        "special_defense" to "cobblemon:grepa_berry",
        "spd" to "cobblemon:grepa_berry",
        "speed" to "cobblemon:tamato_berry",
        "spe" to "cobblemon:tamato_berry"
    )

    // 4. Ingrédients de base fixes du Poké Snack dans le Chaudron
    val baseIngredients: List<ItemStack> by lazy {
        val milk = ItemStack(Items.MILK_BUCKET, 3)
        val honey = ItemStack(Items.HONEY_BOTTLE, 2)
        val vivichoke = try {
            val itm = BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:vivichoke"))
            if (itm != Items.AIR) ItemStack(itm, 1) else ItemStack(CobblemonItems.VIVICHOKE, 1)
        } catch (_: Throwable) {
            ItemStack(Items.CARROT, 1)
        }
        val grains = try {
            val itm = BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:hearty_grains"))
            if (itm != Items.AIR) ItemStack(itm, 3) else ItemStack(CobblemonItems.HEARTY_GRAINS, 3)
        } catch (_: Throwable) {
            ItemStack(Items.WHEAT, 3)
        }
        listOf(milk, honey, vivichoke, grains)
    }

    /**
     * Résout l'ensemble des conseils Poké Snack pour une espèce et une forme donnée.
     */
    fun resolvePokeSnack(species: Species, form: FormData): PokeSnackAdvice {
        val cacheKey = "${species.resourceIdentifier}|${form.name}"
        adviceCache[cacheKey]?.let { return it }

        val spawns = PokemonSpawnDataLoader.getSpawnRecipesForSpecies(species)
        val hasWildSpawns = spawns.isNotEmpty()

        // Détermine la rareté de base la plus élevée dans la nature
        val baseRarity = if (hasWildSpawns) {
            when {
                spawns.any { it.bucket.equals("ultra-rare", ignoreCase = true) } -> "ultra-rare"
                spawns.any { it.bucket.equals("rare", ignoreCase = true) } -> "rare"
                spawns.any { it.bucket.equals("uncommon", ignoreCase = true) } -> "uncommon"
                else -> "common"
            }
        } else null

        // Recherche d'une sous-évolution ayant des spawns sauvages si l'espèce actuelle n'en a pas
        val fallbackPreEvolution = if (!hasWildSpawns) {
            findSpawningPreEvolution(species)
        } else null

        // Types du Pokémon
        val primaryType = form.primaryType ?: species.standardForm.primaryType
        val secondaryType = form.secondaryType ?: species.standardForm.secondaryType

        // Top EV yield
        val topEvStat = findTopEvYield(form)

        // Définition des 3 assaisonnements
        val chosenSeasonings = mutableListOf<SeasoningRecommendation>()

        // --- SLOT 1 : Ciblage Type Principal (Multiplicateur x10) ---
        val slot1Id = typeBerries[primaryType.name.lowercase()] ?: "cobblemon:oran_berry"
        val slot1Desc = "Type ${formatTypeName(primaryType)} : Multiplie par 10 les apparitions"
        chosenSeasonings.add(createRecommendation(1, slot1Id, "Ciblage Type Principal", slot1Desc))

        // --- SLOT 2 : Ciblage Type Secondaire ou Groupe d'Œuf ---
        if (secondaryType != null && typeBerries.containsKey(secondaryType.name.lowercase())) {
            val slot2Id = typeBerries[secondaryType.name.lowercase()]!!
            val slot2Desc = "Type ${formatTypeName(secondaryType)} : Multiplie par 10 les apparitions"
            chosenSeasonings.add(createRecommendation(2, slot2Id, "Ciblage Type Secondaire", slot2Desc))
        } else {
            // Pas de type secondaire : tenter un groupe d'œuf compatible
            val eggGroupBerry = form.eggGroups.firstNotNullOfOrNull { eg ->
                eggGroupBerries.firstOrNull { it.first == eg }?.let { it.second to eg }
            }

            if (eggGroupBerry != null) {
                val (berryId, group) = eggGroupBerry
                val slot2Desc = "Groupe d'œuf ${formatEggGroupName(group)} : Multiplie par 10 les apparitions"
                chosenSeasonings.add(createRecommendation(2, berryId, "Ciblage Groupe d'Œuf", slot2Desc))
            } else if (topEvStat != null && evBerries.containsKey(topEvStat.first.identifier.path.lowercase())) {
                val berryId = evBerries[topEvStat.first.identifier.path.lowercase()]!!
                val slot2Desc = "Filtre EV ${topEvStat.first.displayName.string} : Élimine les rivaux sans cet EV"
                chosenSeasonings.add(createRecommendation(2, berryId, "Filtre EV Sélectif", slot2Desc))
            } else {
                chosenSeasonings.add(createRecommendation(2, "cobblemon:starf_berry", "Boosteur Chromatique", "Rerolls Shiny ×4 pour maximiser les apparitions rares"))
            }
        }

        // --- SLOT 3 : Boosteur de Rareté OU Filtre EV OU Chromatique ---
        if (baseRarity == "ultra-rare" || baseRarity == "rare") {
            // Pour les Pokémon rares/ultra-rares, l'enchanted golden apple augmente le tier de rareté de +10
            chosenSeasonings.add(
                createRecommendation(
                    3,
                    "minecraft:enchanted_golden_apple",
                    "Élévation de Rareté & Shiny",
                    "Niveau de leurre +10 (Attire les Ultra-rares) & Rerolls Shiny ×9"
                )
            )
        } else if (chosenSeasonings.none { it.itemId in evBerries.values } && topEvStat != null && evBerries.containsKey(topEvStat.first.identifier.path.lowercase())) {
            val berryId = evBerries[topEvStat.first.identifier.path.lowercase()]!!
            val slot3Desc = "Filtre EV ${topEvStat.first.displayName.string} : Élimine les Pokémon sauvages sans cet EV"
            chosenSeasonings.add(createRecommendation(3, berryId, "Filtre EV Éliminatoire", slot3Desc))
        } else {
            // Alternative de valeur : Baie Starf (Shiny boost) ou Baie Lum / Chesto
            chosenSeasonings.add(
                createRecommendation(
                    3,
                    "cobblemon:starf_berry",
                    "Boosteur Chromatique",
                    "Rerolls Shiny ×4 pour maximiser les variantes chromatiques"
                )
            )
        }

        val explanation = if (hasWildSpawns) {
            "Cette composition cumule les multiplicateurs de type et de groupe d'œuf (jusqu'à ×100) avec une élévation de rareté ou un filtre d'EV strict."
        } else if (fallbackPreEvolution != null) {
            "Ce Pokémon ne spawn pas naturellement. Utilisez ces assaisonnements pour attirer sa sous-évolution ${fallbackPreEvolution.name} puis faites-la évoluer."
        } else {
            "Ce Pokémon n'apparaît pas dans la nature (obtenu par fossile, œuf ou événement)."
        }

        val result = PokeSnackAdvice(
            targetSpecies = species,
            targetForm = form,
            hasWildSpawns = hasWildSpawns,
            baseRarity = baseRarity,
            fallbackPreEvolution = fallbackPreEvolution,
            seasonings = chosenSeasonings,
            potBaseIngredients = baseIngredients,
            explanationSummary = explanation
        )

        adviceCache[cacheKey] = result
        return result
    }

    private fun createRecommendation(
        slot: Int,
        itemId: String,
        title: String,
        roleDesc: String
    ): SeasoningRecommendation {
        val loc = ResourceLocation.parse(itemId)
        val item = BuiltInRegistries.ITEM.get(loc)
        val stack = if (item != Items.AIR) ItemStack(item) else ItemStack(Items.BARRIER)

        val howToCreate = buildHowToCreate(loc, stack)
        val whereToFind = buildWhereToFind(loc, stack)

        return SeasoningRecommendation(
            slotNumber = slot,
            itemStack = stack,
            itemId = itemId,
            title = title,
            roleDescription = roleDesc,
            howToCreate = howToCreate,
            whereToFind = whereToFind
        )
    }

    private fun buildHowToCreate(loc: ResourceLocation, stack: ItemStack): HowToCreate {
        val idStr = loc.toString()

        // 1. Items Minecraft fabriquables
        when (idStr) {
            "minecraft:golden_apple" -> return HowToCreate.Crafting(
                "8 Lingots d'or + 1 Pomme",
                listOf(ItemStack(Items.GOLD_INGOT, 8), ItemStack(Items.APPLE))
            )
            "minecraft:golden_carrot" -> return HowToCreate.Crafting(
                "8 Pépites d'or + 1 Carotte",
                listOf(ItemStack(Items.GOLD_NUGGET, 8), ItemStack(Items.CARROT))
            )
            "minecraft:glistering_melon_slice" -> return HowToCreate.Crafting(
                "8 Pépites d'or + 1 Tranche de melon",
                listOf(ItemStack(Items.GOLD_NUGGET, 8), ItemStack(Items.MELON_SLICE))
            )
            "minecraft:enchanted_golden_apple" -> return HowToCreate.NonCraftable(
                "Non fabricable en établi (Loot rare uniquement)"
            )
        }

        // 2. Recherche de mutation de baie (Cobblemon)
        val mutations = findMutationsForBerry(loc)
        if (mutations.isNotEmpty()) {
            val best = mutations.first()
            val p1Name = best.first.hoverName.string
            val p2Name = best.second.hoverName.string
            return HowToCreate.BerryMutation(
                parent1 = best.first,
                parent2 = best.second,
                description = "Croiser $p1Name + $p2Name sur terre labourée"
            )
        }

        // 3. Baies de base non issues de croisements
        if (idStr.startsWith("cobblemon:") && idStr.endsWith("_berry")) {
            return HowToCreate.NonCraftable("Baie sauvage naturelle (Arbres à baies dans la nature)")
        }

        return HowToCreate.NonCraftable("Récolte ou cueillette dans la nature")
    }

    private fun buildWhereToFind(loc: ResourceLocation, stack: ItemStack): WhereToFind {
        val idStr = loc.toString()
        val naturalBiomes = mutableListOf<String>()
        val mobDrops = mutableListOf<MobDropInfo>()
        val specialLocations = mutableListOf<String>()

        // 1. Biomes pour les baies Cobblemon
        try {
            val berryObj = Berries.getByIdentifier(loc) ?: (stack.item as? BerryItem)?.berry()
            val tags = berryObj?.preferredBiomeTags ?: emptyList()
            for (tag in tags) {
                val tagStr = tag.location.path
                val friendlyName = formatBiomeTag(tagStr)
                if (friendlyName != null && !naturalBiomes.contains(friendlyName)) {
                    naturalBiomes.add(friendlyName)
                }
            }
        } catch (_: Throwable) {}

        // Fallbacks biomes connus pour baies clés
        if (naturalBiomes.isEmpty()) {
            when (idStr) {
                "cobblemon:occa_berry" -> naturalBiomes.addAll(listOf("Désert", "Savane", "Badlands / Mesa"))
                "cobblemon:passho_berry" -> naturalBiomes.addAll(listOf("Océans", "Plages", "Rivières"))
                "cobblemon:rindo_berry" -> naturalBiomes.addAll(listOf("Forêts", "Jungle", "Marais"))
                "cobblemon:wacan_berry" -> naturalBiomes.addAll(listOf("Savane", "Plaines"))
                "cobblemon:yache_berry" -> naturalBiomes.addAll(listOf("Taïga enneigée", "Pics gelés", "Toundra"))
                "cobblemon:chople_berry" -> naturalBiomes.addAll(listOf("Montagnes", "Plateaux"))
                "cobblemon:haban_berry" -> naturalBiomes.addAll(listOf("Jungle", "Montagnes", "Forêt féerique"))
                "cobblemon:coba_berry" -> naturalBiomes.addAll(listOf("Collines venteuses", "Plaines"))
                "cobblemon:kasib_berry" -> naturalBiomes.addAll(listOf("Forêt sombre", "Marais"))
                "cobblemon:shuca_berry" -> naturalBiomes.addAll(listOf("Désert", "Badlands"))
                "cobblemon:babiri_berry" -> naturalBiomes.addAll(listOf("Montagnes", "Cavernes"))
                "cobblemon:roseli_berry" -> naturalBiomes.addAll(listOf("Forêt de fleurs", "Plaines"))
                "cobblemon:chilan_berry" -> naturalBiomes.addAll(listOf("Plaines", "Forêts tempérées"))
                "cobblemon:lum_berry" -> naturalBiomes.addAll(listOf("Plaines", "Forêts"))
                "cobblemon:starf_berry" -> naturalBiomes.addAll(listOf("Forêt féerique / Magique", "Îles célestes"))
                "minecraft:sweet_berries" -> naturalBiomes.addAll(listOf("Taïga", "Taïga enneigée"))
                "minecraft:glow_berries" -> naturalBiomes.addAll(listOf("Grottes luxuriantes (Lush Caves)"))
                "minecraft:apple" -> naturalBiomes.addAll(listOf("Feuillage des chênes", "Forêts"))
            }
        }

        // 2. Butins lâchés par les Pokémon
        try {
            val recipes = PokemonDropDataLoader.loadDropRecipes()
            val matching = recipes.flatMap { r ->
                r.drops.filter { it.itemStack.item == stack.item }.map { r.species to it.percentage }
            }.groupBy({ it.first }, { it.second })
                .mapValues { (_, pcts) -> pcts.maxOrNull() ?: 0f }
                .toList()
                .sortedByDescending { it.second }
                .take(3)

            for ((sp, pct) in matching) {
                mobDrops.add(MobDropInfo(sp.name, pct))
            }
        } catch (_: Throwable) {}

        // 3. Emplacements spéciaux (Structures & Coffres)
        when (idStr) {
            "minecraft:enchanted_golden_apple" -> specialLocations.addAll(
                listOf(
                    "Anciennes cités (Deep Dark)",
                    "Bastions des Piglins",
                    "Forteresses du Nether",
                    "Mines abandonnées",
                    "Donjons & Temples du désert"
                )
            )
            "minecraft:golden_apple" -> specialLocations.addAll(
                listOf("Donjons", "Bastions", "Villages", "Temples du désert", "Ruines océaniques")
            )
            "minecraft:golden_carrot" -> specialLocations.addAll(
                listOf("Échange avec Villageois Fermier (Maître)", "Coffres de vestiges")
            )
        }

        return WhereToFind(naturalBiomes, mobDrops, specialLocations)
    }

    private fun findMutationsForBerry(loc: ResourceLocation): List<Pair<ItemStack, ItemStack>> {
        val key = loc.toString()
        mutationCache[key]?.let { return it }

        val list = mutableListOf<Pair<ItemStack, ItemStack>>()
        try {
            val berries = CobblemonItems.berries()
            for ((_, berryItem) in berries) {
                val b = berryItem.berry() ?: continue
                val bStack = ItemStack(berryItem)
                for ((partnerLoc, resLoc) in b.mutations) {
                    if (resLoc == loc) {
                        val partnerItem = BuiltInRegistries.ITEM.get(partnerLoc)
                        if (partnerItem != Items.AIR) {
                            val partnerStack = ItemStack(partnerItem)
                            val alreadyExists = list.any { (p1, p2) ->
                                (p1.item == bStack.item && p2.item == partnerStack.item) ||
                                (p1.item == partnerStack.item && p2.item == bStack.item)
                            }
                            if (!alreadyExists) {
                                list.add(bStack to partnerStack)
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logger.debug("Erreur analyse mutations pour {}: {}", loc, e.message)
        }

        mutationCache[key] = list
        return list
    }

    private fun findTopEvYield(form: FormData): Pair<Stat, Int>? {
        return try {
            form.evYield.entries
                .filter { it.value > 0 }
                .maxByOrNull { it.value }
                ?.toPair()
        } catch (_: Throwable) {
            null
        }
    }

    private fun findSpawningPreEvolution(species: Species): Species? {
        var cur = try { species.preEvolution?.species } catch (_: Throwable) { null }
        while (cur != null) {
            if (PokemonSpawnDataLoader.getSpawnRecipesForSpecies(cur).isNotEmpty()) {
                return cur
            }
            cur = try { cur.preEvolution?.species } catch (_: Throwable) { null }
        }
        return null
    }

    private fun formatTypeName(type: ElementalType): String {
        return type.displayName.string.ifBlank { type.name.replaceFirstChar(Char::titlecase) }
    }

    private fun formatEggGroupName(group: EggGroup): String {
        return when (group) {
            EggGroup.MONSTER -> "Monstre"
            EggGroup.WATER_1 -> "Aquatique 1"
            EggGroup.BUG -> "Insectoïde"
            EggGroup.FLYING -> "Aérien / Vol"
            EggGroup.FIELD -> "Terrestre / Champ"
            EggGroup.FAIRY -> "Féerique"
            EggGroup.GRASS -> "Végétal"
            EggGroup.HUMAN_LIKE -> "Humanoïde"
            EggGroup.WATER_3 -> "Aquatique 3"
            EggGroup.MINERAL -> "Minéral"
            EggGroup.AMORPHOUS -> "Amorphe"
            EggGroup.WATER_2 -> "Aquatique 2"
            EggGroup.DRAGON -> "Draconique"
            else -> group.name.replace("_", " ").lowercase().replaceFirstChar(Char::titlecase)
        }
    }

    private fun formatBiomeTag(tag: String): String? {
        return when (tag.removePrefix("cobblemon:").removePrefix("is_")) {
            "jungle" -> "Jungle"
            "mountain", "mountains", "peaks" -> "Montagne"
            "forest" -> "Forêt"
            "plains" -> "Plaines"
            "swamp" -> "Marais"
            "desert" -> "Désert"
            "taiga" -> "Taïga"
            "ocean" -> "Océan / Côtier"
            "badlands", "mesa" -> "Badlands / Mesa"
            "savanna" -> "Savane"
            "tundra", "snowy" -> "Toundra / Neige"
            "magical" -> "Forêt Féerique"
            "volcanic" -> "Volcanique"
            "sky" -> "Îles célestes"
            else -> null
        }
    }
}
