package fr.k0bus.cobblemon_jei.data

import fr.k0bus.cobblemon_jei.compat.cobbleworkers.CobbleworkersCompat
import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import fr.k0bus.cobblemon_jei.recipe.PokemonPastureRecipe
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory
import java.io.InputStreamReader

/**
 * Chargeur de données pour les métiers de travail au pâturage du mod Cobbleworkers.
 */
object CobbleworkersJobDataLoader {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/CobbleworkersLoader")
    private var cachedRecipes: List<PokemonPastureRecipe>? = null

    @Synchronized
    fun loadPastureRecipes(): List<PokemonPastureRecipe> {
        if (!CobbleworkersCompat.isLoaded) return emptyList()

        cachedRecipes?.let { return it }

        PokemonEvolutionDataLoader.ensureSpeciesLoaded()

        val rawJobs = mutableMapOf<String, JobData>()

        // 1. Scanne les fichiers jobs JSON de Cobbleworkers
        CobblemonDataScanner.scanDataResources("jobs") { name, stream ->
            try {
                if (name.contains("cobbleworkers", ignoreCase = true) || name.contains("/jobs/")) {
                    InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                        val obj = JsonParser.parseReader(reader).asJsonObject
                        val id = obj.get("id")?.asString ?: return@use
                        val cooldown = obj.get("cooldown")?.asInt ?: 0

                        val reqObj = obj.getAsJsonObject("requirements")
                        val types = reqObj?.getAsJsonArray("types")?.map { it.asString }
                        val species = reqObj?.getAsJsonArray("species")?.map { it.asString }
                        val abilities = reqObj?.getAsJsonArray("abilities")?.map { it.asString }
                        val moves = reqObj?.getAsJsonArray("moves")?.map { it.asString }

                        rawJobs[id] = JobData(
                            id = id,
                            cooldown = cooldown,
                            types = types,
                            species = species,
                            abilities = abilities,
                            moves = moves
                        )
                    }
                }
            } catch (e: Throwable) {
                logger.debug("Erreur lecture job {}: {}", name, e.message)
            }
        }

        // Si aucun fichier n'a été scanné (ex: jar en classpath sans data explicite), on injecte les 28 définitions standard de Cobbleworkers
        if (rawJobs.isEmpty()) {
            populateDefaultJobs(rawJobs)
        }

        val recipes = mutableListOf<PokemonPastureRecipe>()

        for ((id, job) in rawJobs) {
            val eligible = findEligiblePokemon(job.species, job.types, job.abilities, job.moves)
            if (eligible.isEmpty()) continue

            val cleanName = id.substringAfter(":")
            val targets = resolveTargets(cleanName)
            val outputs = resolveOutputs(cleanName)
            val reqDesc = formatRequirements(job)

            recipes.add(
                PokemonPastureRecipe(
                    jobId = id,
                    jobTitleKey = "gui.cobblemon_jei.job.$cleanName",
                    eligiblePokemon = eligible,
                    targetInputs = targets,
                    outputs = outputs,
                    cooldownSeconds = job.cooldown,
                    requirementsDescription = reqDesc,
                    actionDescriptionKey = "gui.cobblemon_jei.job.$cleanName.desc"
                )
            )
        }

        val sorted = recipes.sortedBy { it.jobTitleKey }
        cachedRecipes = sorted
        logger.info("Cobblemon JEI: ${sorted.size} métiers Cobbleworkers chargés pour le pâturage.")
        return sorted
    }

    private data class JobData(
        val id: String,
        val cooldown: Int,
        val types: List<String>?,
        val species: List<String>?,
        val abilities: List<String>?,
        val moves: List<String>?
    )

    private fun findEligiblePokemon(
        speciesReq: List<String>?,
        typesReq: List<String>?,
        abilitiesReq: List<String>?,
        movesReq: List<String>?
    ): List<PokemonSpeciesIngredient> {
        val results = mutableListOf<PokemonSpeciesIngredient>()

        for (species in PokemonSpecies.species) {
            val forms = mutableListOf(species.standardForm)
            for (f in species.forms) {
                if (f != species.standardForm) forms.add(f)
            }

            for (form in forms) {
                var eligible = false

                if (speciesReq != null && speciesReq.isNotEmpty()) {
                    if (speciesReq.any { req ->
                            species.resourceIdentifier.path.equals(req, ignoreCase = true) ||
                                    species.name.equals(req, ignoreCase = true)
                        }) {
                        eligible = true
                    }
                }

                if (typesReq != null && typesReq.isNotEmpty()) {
                    if (typesReq.any { reqType ->
                            form.types.any { it.name.equals(reqType, ignoreCase = true) }
                        }) {
                        eligible = true
                    }
                }

                if (abilitiesReq != null && abilitiesReq.isNotEmpty()) {
                    if (abilitiesReq.any { reqAbility ->
                            form.abilities.any { pot -> pot.template.name.equals(reqAbility, ignoreCase = true) }
                        }) {
                        eligible = true
                    }
                }

                if (movesReq != null && movesReq.isNotEmpty()) {
                    if (movesReq.any { reqMove ->
                            form.moves.getAllLegalMoves().any { move -> move.name.equals(reqMove, ignoreCase = true) }
                        }) {
                        eligible = true
                    }
                }

                if (eligible) {
                    results.add(PokemonSpeciesIngredient(species, form))
                }
            }
        }
        return results
    }

    private fun formatRequirements(job: JobData): String {
        val parts = mutableListOf<String>()
        job.types?.let { if (it.isNotEmpty()) parts.add("Type : " + it.joinToString(", ") { t -> t.replaceFirstChar { c -> c.uppercase() } }) }
        job.species?.let { if (it.isNotEmpty()) parts.add("Espèce : " + it.joinToString(", ") { s -> s.replaceFirstChar { c -> c.uppercase() } }) }
        job.abilities?.let { if (it.isNotEmpty()) parts.add("Talent : " + it.joinToString(", ") { a -> a.replaceFirstChar { c -> c.uppercase() } }) }
        job.moves?.let { if (it.isNotEmpty()) parts.add("Capacité : " + it.joinToString(", ") { m -> m.replaceFirstChar { c -> c.uppercase() } }) }
        return if (parts.isNotEmpty()) parts.joinToString(" • ") else "Aucun"
    }

    private fun resolveTargets(cleanName: String): List<ItemStack> {
        return when (cleanName) {
            "amethyst_harvester" -> listOf(ItemStack(Items.AMETHYST_CLUSTER))
            "apricorn_harvester" -> listOf(safeItem { CobblemonItems.BLACK_APRICORN })
            "archaeologist" -> listOf(ItemStack(Items.SUSPICIOUS_SAND), ItemStack(Items.SUSPICIOUS_GRAVEL))
            "berry_harvester", "bush_harvester" -> listOf(ItemStack(Items.SWEET_BERRIES), safeItem { CobblemonItems.ORAN_BERRY })
            "black_tumblestone_harvester" -> listOf(safeItem { CobblemonItems.BLACK_TUMBLESTONE })
            "tumblestone_harvester" -> listOf(safeItem { CobblemonItems.TUMBLESTONE })
            "sky_tumblestone_harvester" -> listOf(safeItem { CobblemonItems.SKY_TUMBLESTONE })
            "brewing_stand_fuel_generator" -> listOf(ItemStack(Items.BREWING_STAND))
            "crops_harvester" -> listOf(ItemStack(Items.WHEAT), ItemStack(Items.CARROT), ItemStack(Items.POTATO), ItemStack(Items.BEETROOT))
            "dive_looter" -> listOf(ItemStack(Items.WATER_BUCKET))
            "extinguisher" -> listOf(ItemStack(Items.FLINT_AND_STEEL))
            "fishing_looter" -> listOf(ItemStack(Items.FISHING_ROD))
            "fletcher" -> listOf(ItemStack(Items.ARROW))
            "fuel_generator" -> listOf(ItemStack(Items.FURNACE), ItemStack(Items.BLAST_FURNACE), ItemStack(Items.SMOKER))
            "healer" -> listOf(ItemStack(Items.POTION))
            "hearty_grains_harvester" -> listOf(safeItem { CobblemonItems.MEDICINAL_LEEK })
            "honey_collection", "honey_generation" -> listOf(ItemStack(Items.BEEHIVE), ItemStack(Items.BEE_NEST))
            "irrigator" -> listOf(ItemStack(Items.FARMLAND), ItemStack(Items.WOODEN_HOE))
            "item_gatherer" -> listOf(ItemStack(Items.CHEST))
            "lava_generator", "water_generator", "powder_snow_generator" -> listOf(ItemStack(Items.CAULDRON))
            "mint_harvester" -> listOf(safeItem { CobblemonItems.ADAMANT_MINT })
            "netherwart_harvester" -> listOf(ItemStack(Items.NETHER_WART))
            "pickup_looter" -> listOf(ItemStack(Items.CHEST))
            "rain_dancer" -> listOf(ItemStack(Items.CLOCK))
            else -> listOf(ItemStack(Items.OAK_LOG))
        }
    }

    private fun singleSlot(vararg stacks: ItemStack): List<List<ItemStack>> = stacks.map { listOf(it) }

    private fun resolveOutputs(cleanName: String): List<List<ItemStack>> {
        return when (cleanName) {
            "amethyst_harvester" -> listOf(listOf(ItemStack(Items.AMETHYST_SHARD, 4)))
            "apricorn_harvester" -> singleSlot(
                safeItem { CobblemonItems.RED_APRICORN },
                safeItem { CobblemonItems.BLUE_APRICORN },
                safeItem { CobblemonItems.YELLOW_APRICORN },
                safeItem { CobblemonItems.GREEN_APRICORN },
                safeItem { CobblemonItems.BLACK_APRICORN },
                safeItem { CobblemonItems.WHITE_APRICORN },
                safeItem { CobblemonItems.PINK_APRICORN }
            )
            "archaeologist" -> listOf(
                // Slot 1 : Minerais bruts
                listOf(
                    ItemStack(Items.RAW_IRON, 2),
                    ItemStack(Items.RAW_COPPER, 4),
                    ItemStack(Items.RAW_GOLD, 2),
                    ItemStack(Items.COAL, 4),
                    ItemStack(Items.CHARCOAL, 4)
                ),
                // Slot 2 : Os & Trésors
                listOf(
                    ItemStack(Items.BONE, 2),
                    ItemStack(Items.SNIFFER_EGG),
                    ItemStack(Items.EMERALD)
                ),
                // Slot 3 : Fossiles Cobblemon
                listOf(
                    safeItem { CobblemonItems.ARMOR_FOSSIL },
                    safeItem { CobblemonItems.CLAW_FOSSIL },
                    safeItem { CobblemonItems.COVER_FOSSIL },
                    safeItem { CobblemonItems.DOME_FOSSIL },
                    safeItem { CobblemonItems.HELIX_FOSSIL },
                    safeItem { CobblemonItems.JAW_FOSSIL },
                    safeItem { CobblemonItems.OLD_AMBER_FOSSIL },
                    safeItem { CobblemonItems.PLUME_FOSSIL },
                    safeItem { CobblemonItems.ROOT_FOSSIL },
                    safeItem { CobblemonItems.SAIL_FOSSIL },
                    safeItem { CobblemonItems.SKULL_FOSSIL },
                    safeItem { CobblemonItems.FOSSILIZED_BIRD },
                    safeItem { CobblemonItems.FOSSILIZED_DINO },
                    safeItem { CobblemonItems.FOSSILIZED_DRAKE },
                    safeItem { CobblemonItems.FOSSILIZED_FISH }
                ),
                // Slot 4 : Objets tenus & roches antiques
                listOf(
                    safeItem { CobblemonItems.BLACK_AUGURITE },
                    safeItem { CobblemonItems.PEAT_BLOCK },
                    safeItem { CobblemonItems.KINGS_ROCK },
                    safeItem { CobblemonItems.EVERSTONE },
                    safeItem { CobblemonItems.RAZOR_CLAW },
                    safeItem { CobblemonItems.RAZOR_FANG },
                    safeItem { CobblemonItems.PRISM_SCALE }
                )
            )
            "berry_harvester" -> singleSlot(
                safeItem { CobblemonItems.ORAN_BERRY },
                safeItem { CobblemonItems.SITRUS_BERRY },
                safeItem { CobblemonItems.PECHA_BERRY },
                safeItem { CobblemonItems.CHERI_BERRY },
                safeItem { CobblemonItems.RAWST_BERRY },
                safeItem { CobblemonItems.CHESTO_BERRY }
            )
            "bush_harvester" -> listOf(listOf(ItemStack(Items.SWEET_BERRIES, 3)))
            "black_tumblestone_harvester" -> listOf(listOf(safeItem { CobblemonItems.BLACK_TUMBLESTONE }))
            "tumblestone_harvester" -> listOf(listOf(safeItem { CobblemonItems.TUMBLESTONE }))
            "sky_tumblestone_harvester" -> listOf(listOf(safeItem { CobblemonItems.SKY_TUMBLESTONE }))
            "brewing_stand_fuel_generator" -> listOf(listOf(ItemStack(Items.BLAZE_POWDER)))
            "crops_harvester" -> singleSlot(
                ItemStack(Items.WHEAT),
                ItemStack(Items.CARROT),
                ItemStack(Items.POTATO),
                ItemStack(Items.BEETROOT)
            )
            "dive_looter" -> listOf(
                // Slot 1 : Minerais précieux
                listOf(
                    ItemStack(Items.GOLD_INGOT),
                    ItemStack(Items.EMERALD)
                ),
                // Slot 2 : Joyaux aquatiques & Pierre Eau
                listOf(
                    safeItem { CobblemonItems.WATER_GEM },
                    safeItem { CobblemonItems.DARK_GEM },
                    safeItem { CobblemonItems.GHOST_GEM },
                    safeItem { CobblemonItems.DRAGON_GEM },
                    safeItem { CobblemonItems.WATER_STONE }
                ),
                // Slot 3 : Balls aquatiques
                listOf(
                    safeItem { CobblemonItems.NET_BALL },
                    safeItem { CobblemonItems.DIVE_BALL },
                    safeItem { CobblemonItems.LURE_BALL }
                ),
                // Slot 4 : Trésors marins
                listOf(
                    ItemStack(Items.NAUTILUS_SHELL),
                    ItemStack(Items.SPYGLASS),
                    ItemStack(Items.COMPASS),
                    ItemStack(Items.FISHING_ROD)
                ),
                // Slot 5 : Pièce Relique
                listOf(safeItem { CobblemonItems.RELIC_COIN })
            )
            "extinguisher" -> listOf(listOf(ItemStack(Items.WATER_BUCKET)))
            "fishing_looter" -> listOf(
                // Slot 1 : Poissons
                listOf(
                    ItemStack(Items.COD),
                    ItemStack(Items.SALMON),
                    ItemStack(Items.TROPICAL_FISH),
                    ItemStack(Items.PUFFERFISH)
                ),
                // Slot 2 : Trésors de pêche
                listOf(
                    ItemStack(Items.NAUTILUS_SHELL),
                    ItemStack(Items.SADDLE),
                    ItemStack(Items.NAME_TAG),
                    ItemStack(Items.BOW),
                    ItemStack(Items.FISHING_ROD)
                ),
                // Slot 3 : Déchets & Ingrédients
                listOf(
                    ItemStack(Items.LILY_PAD),
                    ItemStack(Items.LEATHER),
                    ItemStack(Items.STRING),
                    ItemStack(Items.BONE),
                    ItemStack(Items.BOWL)
                )
            )
            "fletcher" -> listOf(listOf(ItemStack(Items.TIPPED_ARROW), ItemStack(Items.ARROW)))
            "fuel_generator" -> listOf(listOf(ItemStack(Items.COAL, 2), ItemStack(Items.CHARCOAL, 2)))
            "healer" -> listOf(listOf(ItemStack(Items.GOLDEN_APPLE)))
            "hearty_grains_harvester" -> listOf(listOf(safeItem { CobblemonItems.MEDICINAL_LEEK }))
            "honey_collection" -> singleSlot(ItemStack(Items.HONEY_BOTTLE), ItemStack(Items.HONEYCOMB))
            "honey_generation" -> listOf(listOf(ItemStack(Items.HONEYCOMB)))
            "irrigator" -> listOf(listOf(ItemStack(Items.WATER_BUCKET)))
            "item_gatherer" -> listOf(listOf(ItemStack(Items.HOPPER)))
            "lava_generator" -> listOf(listOf(ItemStack(Items.LAVA_BUCKET)))
            "powder_snow_generator" -> listOf(listOf(ItemStack(Items.POWDER_SNOW_BUCKET)))
            "water_generator" -> listOf(listOf(ItemStack(Items.WATER_BUCKET)))
            "mint_harvester" -> listOf(
                listOf(
                    safeItem { CobblemonItems.ADAMANT_MINT },
                    safeItem { CobblemonItems.BRAVE_MINT },
                    safeItem { CobblemonItems.BOLD_MINT },
                    safeItem { CobblemonItems.IMPISH_MINT }
                ),
                listOf(
                    safeItem { CobblemonItems.MODEST_MINT },
                    safeItem { CobblemonItems.QUIET_MINT },
                    safeItem { CobblemonItems.CALM_MINT },
                    safeItem { CobblemonItems.CAREFUL_MINT }
                ),
                listOf(
                    safeItem { CobblemonItems.TIMID_MINT },
                    safeItem { CobblemonItems.JOLLY_MINT },
                    safeItem { CobblemonItems.HASTY_MINT },
                    safeItem { CobblemonItems.NAIVE_MINT }
                ),
                listOf(
                    safeItem { CobblemonItems.SERIOUS_MINT }
                )
            )
            "netherwart_harvester" -> listOf(listOf(ItemStack(Items.NETHER_WART, 3)))
            "pickup_looter" -> listOf(
                // 1. Poké Balls (any_common_pokeball)
                listOf(
                    safeItem { CobblemonItems.POKE_BALL },
                    safeItem { CobblemonItems.GREAT_BALL },
                    safeItem { CobblemonItems.ULTRA_BALL }
                ),
                // 2. Pierres d'évolution (any_evo_stone)
                listOf(
                    safeItem { CobblemonItems.FIRE_STONE },
                    safeItem { CobblemonItems.WATER_STONE },
                    safeItem { CobblemonItems.THUNDER_STONE },
                    safeItem { CobblemonItems.LEAF_STONE },
                    safeItem { CobblemonItems.MOON_STONE },
                    safeItem { CobblemonItems.SUN_STONE },
                    safeItem { CobblemonItems.DUSK_STONE },
                    safeItem { CobblemonItems.DAWN_STONE },
                    safeItem { CobblemonItems.SHINY_STONE },
                    safeItem { CobblemonItems.ICE_STONE }
                ),
                // 3. Bonbons d'Expérience & Super Bonbon (any_exp_candy)
                listOf(
                    safeItem { CobblemonItems.EXPERIENCE_CANDY_XS },
                    safeItem { CobblemonItems.EXPERIENCE_CANDY_S },
                    safeItem { CobblemonItems.EXPERIENCE_CANDY_M },
                    safeItem { CobblemonItems.EXPERIENCE_CANDY_L },
                    safeItem { CobblemonItems.EXPERIENCE_CANDY_XL },
                    safeItem { CobblemonItems.RARE_CANDY }
                ),
                // 4. Joyaux élémentaires (any_type_gem)
                listOf(
                    safeItem { CobblemonItems.FIRE_GEM },
                    safeItem { CobblemonItems.WATER_GEM },
                    safeItem { CobblemonItems.GRASS_GEM },
                    safeItem { CobblemonItems.ELECTRIC_GEM },
                    safeItem { CobblemonItems.ICE_GEM },
                    safeItem { CobblemonItems.DRAGON_GEM },
                    safeItem { CobblemonItems.DARK_GEM },
                    safeItem { CobblemonItems.GHOST_GEM }
                ),
                // 5. Graines de Noigrumes (any_apricorn_seed)
                listOf(
                    safeItem { CobblemonItems.RED_APRICORN_SEED },
                    safeItem { CobblemonItems.YELLOW_APRICORN_SEED },
                    safeItem { CobblemonItems.GREEN_APRICORN_SEED },
                    safeItem { CobblemonItems.BLUE_APRICORN_SEED },
                    safeItem { CobblemonItems.PINK_APRICORN_SEED },
                    safeItem { CobblemonItems.BLACK_APRICORN_SEED },
                    safeItem { CobblemonItems.WHITE_APRICORN_SEED }
                ),
                // 6. Soins / Remèdes naturels (any_natural_heal_item)
                listOf(
                    safeItem { CobblemonItems.MEDICINAL_LEEK },
                    safeItem { CobblemonItems.VIVICHOKE_SEEDS },
                    safeItem { CobblemonItems.ENERGY_ROOT },
                    safeItem { CobblemonItems.REVIVAL_HERB }
                ),
                // 7. Consommables Centres Pokémon (village_pokecenters)
                listOf(
                    safeItem { CobblemonItems.POTION },
                    safeItem { CobblemonItems.SUPER_POTION },
                    safeItem { CobblemonItems.HYPER_POTION },
                    safeItem { CobblemonItems.MAX_POTION },
                    safeItem { CobblemonItems.FULL_RESTORE }
                ),
                // 8. Objets antiques d'Hisui (any_ancient_held_item)
                listOf(
                    safeItem { CobblemonItems.BLACK_AUGURITE },
                    safeItem { CobblemonItems.PEAT_BLOCK }
                )
            )
            "rain_dancer" -> listOf(listOf(ItemStack(Items.WATER_BUCKET)))
            else -> listOf(listOf(ItemStack(Items.APPLE)))
        }
    }

    private fun safeItem(provider: () -> net.minecraft.world.item.Item): ItemStack {
        return try {
            ItemStack(provider())
        } catch (_: Throwable) {
            ItemStack(Items.APPLE)
        }
    }

    private fun populateDefaultJobs(map: MutableMap<String, JobData>) {
        map["cobbleworkers:amethyst_harvester"] = JobData("cobbleworkers:amethyst_harvester", 0, listOf("rock"), null, null, null)
        map["cobbleworkers:apricorn_harvester"] = JobData("cobbleworkers:apricorn_harvester", 0, listOf("bug"), null, null, null)
        map["cobbleworkers:archaeologist"] = JobData("cobbleworkers:archaeologist", 240, listOf("ground"), null, null, null)
        map["cobbleworkers:berry_harvester"] = JobData("cobbleworkers:berry_harvester", 0, listOf("grass"), null, null, null)
        map["cobbleworkers:black_tumblestone_harvester"] = JobData("cobbleworkers:black_tumblestone_harvester", 0, listOf("steel"), null, null, null)
        map["cobbleworkers:brewing_stand_fuel_generator"] = JobData("cobbleworkers:brewing_stand_fuel_generator", 80, listOf("dragon"), null, null, null)
        map["cobbleworkers:bush_harvester"] = JobData("cobbleworkers:bush_harvester", 0, listOf("grass"), null, null, null)
        map["cobbleworkers:crops_harvester"] = JobData("cobbleworkers:crops_harvester", 0, listOf("grass"), null, null, null)
        map["cobbleworkers:dive_looter"] = JobData("cobbleworkers:dive_looter", 210, null, null, null, listOf("dive"))
        map["cobbleworkers:extinguisher"] = JobData("cobbleworkers:extinguisher", 0, listOf("water"), null, null, null)
        map["cobbleworkers:fishing_looter"] = JobData("cobbleworkers:fishing_looter", 90, listOf("water"), null, null, null)
        map["cobbleworkers:fletcher"] = JobData("cobbleworkers:fletcher", 0, listOf("poison"), null, null, null)
        map["cobbleworkers:fuel_generator"] = JobData("cobbleworkers:fuel_generator", 80, listOf("fire"), null, null, null)
        map["cobbleworkers:healer"] = JobData("cobbleworkers:healer", 30, null, null, null, listOf("wish", "softboiled", "moonlight", "recover", "roost", "healbell", "aromatherapy", "synthesis", "rest", "lifedew"))
        map["cobbleworkers:hearty_grains_harvester"] = JobData("cobbleworkers:hearty_grains_harvester", 0, listOf("grass"), null, null, null)
        map["cobbleworkers:honey_collection"] = JobData("cobbleworkers:honey_collection", 0, null, listOf("combee", "vespiquen"), null, null)
        map["cobbleworkers:honey_generation"] = JobData("cobbleworkers:honey_generation", 120, null, listOf("combee", "vespiquen"), null, null)
        map["cobbleworkers:irrigator"] = JobData("cobbleworkers:irrigator", 0, listOf("water"), null, null, null)
        map["cobbleworkers:item_gatherer"] = JobData("cobbleworkers:item_gatherer", 0, listOf("psychic"), null, null, null)
        map["cobbleworkers:lava_generator"] = JobData("cobbleworkers:lava_generator", 90, listOf("fire"), null, null, null)
        map["cobbleworkers:mint_harvester"] = JobData("cobbleworkers:mint_harvester", 0, listOf("fairy"), null, null, null)
        map["cobbleworkers:netherwart_harvester"] = JobData("cobbleworkers:netherwart_harvester", 0, listOf("ghost"), null, null, null)
        map["cobbleworkers:pickup_looter"] = JobData("cobbleworkers:pickup_looter", 210, null, null, listOf("pickup"), null)
        map["cobbleworkers:powder_snow_generator"] = JobData("cobbleworkers:powder_snow_generator", 90, listOf("ice"), null, null, null)
        map["cobbleworkers:rain_dancer"] = JobData("cobbleworkers:rain_dancer", 600, null, listOf("slowpoke"), null, null)
        map["cobbleworkers:sky_tumblestone_harvester"] = JobData("cobbleworkers:sky_tumblestone_harvester", 0, listOf("steel"), null, null, null)
        map["cobbleworkers:tumblestone_harvester"] = JobData("cobbleworkers:tumblestone_harvester", 0, listOf("steel"), null, null, null)
        map["cobbleworkers:water_generator"] = JobData("cobbleworkers:water_generator", 90, listOf("water"), null, null, null)
    }
}
