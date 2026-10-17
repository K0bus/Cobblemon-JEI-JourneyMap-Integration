package fr.k0bus.cobblemon_jei.data

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Données d'alimentation d'un Pokémon pour le maintien de sa satiété au pâturage.
 */
data class SpeciesFoodData(
    val foodItems: List<ItemStack>,
    val specificFoodNames: List<String>
)

/**
 * Résolveur d'aliments et de régime pour les Pokémon au pâturage.
 *
 * Dans Cobblemon, la satiété du Pokémon (> 0) est obligatoire pour qu'il produise
 * ses butins périodiques ou qu'il puisse être récolté.
 *
 * Ce résolveur identifie :
 * 1. Les régimes spécifiques de l'espèce ou de sa forme (ex: Lingot de fer pour Galekid,
 *    Améthyste/Diamant pour Ténéfix, Graines pour Roucool).
 * 2. Les aliments universels acceptés par les Pokémon (Baies Cobblemon, friandises,
 *    pommes, carottes et nourritures courantes).
 */
object PokemonFoodResolver {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/FoodResolver")
    private val cache = ConcurrentHashMap<String, SpeciesFoodData>()

    /**
     * Liste des aliments universels Cobblemon et Minecraft (baies, jus, pommes, etc.).
     */
    private val universalFoods: List<ItemStack> by lazy {
        buildUniversalFoods()
    }

    /**
     * Résout les aliments acceptés pour une espèce et une forme donnée.
     * Place les aliments spécifiques du Pokémon en tête de liste.
     */
    fun resolveFood(species: Species, form: FormData): SpeciesFoodData {
        val cacheKey = "${species.resourceIdentifier}|${form.name}"
        cache[cacheKey]?.let { return it }

        val specificConditions = mutableListOf<String>()

        // 1. Détection des préférences spécifiques dans form.behaviour ou species.behaviour
        try {
            val formBehavior = try { form.behaviour } catch (_: Throwable) { null }
            val itemInteractForm = try { formBehavior?.itemInteract } catch (_: Throwable) { null }
            if (itemInteractForm != null) {
                specificConditions.addAll(extractDesiredConditions(itemInteractForm))
            }

            if (specificConditions.isEmpty()) {
                val speciesBehavior = try { species.behaviour } catch (_: Throwable) { null }
                val itemInteractSpecies = try { speciesBehavior?.itemInteract } catch (_: Throwable) { null }
                if (itemInteractSpecies != null) {
                    specificConditions.addAll(extractDesiredConditions(itemInteractSpecies))
                }
            }
        } catch (e: Throwable) {
            logger.debug("Impossible d'extraire le régime de {}: {}", species.name, e.message)
        }

        // 2. Résolution des items spécifiques
        val specificStacks = mutableListOf<ItemStack>()
        for (cond in specificConditions) {
            val resolved = resolveItemCondition(cond)
            specificStacks.addAll(resolved)
        }

        val distinctSpecific = specificStacks.distinctBy { it.item }
        val specificNames = distinctSpecific.map { it.hoverName.string }

        // 3. Combinaison avec les nourritures universelles (spécifiques en tête)
        val combinedItems = mutableListOf<ItemStack>()
        combinedItems.addAll(distinctSpecific)

        for (u in universalFoods) {
            if (combinedItems.none { it.item == u.item }) {
                combinedItems.add(u)
            }
        }

        val result = SpeciesFoodData(
            foodItems = combinedItems,
            specificFoodNames = specificNames
        )
        cache[cacheKey] = result
        return result
    }

    private fun extractDesiredConditions(itemBehavior: Any): List<String> {
        val conditions = mutableListOf<String>()
        try {
            val field = itemBehavior.javaClass.getDeclaredField("desiredItems").apply { isAccessible = true }
            val list = field.get(itemBehavior) as? Collection<*> ?: return emptyList()

            for (elem in list) {
                if (elem == null) continue
                // elem est un ObtainableItem dont le getter ou champ 'item' renvoie la condition
                val itemCond = try {
                    val f = elem.javaClass.getDeclaredField("item").apply { isAccessible = true }
                    f.get(elem)
                } catch (_: Throwable) {
                    null
                } ?: elem

                val condStr = itemCond.toString().trim()
                if (condStr.isNotEmpty() && !condStr.startsWith("com.cobblemon")) {
                    conditions.add(condStr)
                }
            }
        } catch (_: Throwable) {}
        return conditions
    }

    private fun resolveItemCondition(conditionStr: String): List<ItemStack> {
        val output = mutableListOf<ItemStack>()
        try {
            if (conditionStr.startsWith("#")) {
                val tagLoc = ResourceLocation.parse(conditionStr.removePrefix("#"))
                val tagKey = TagKey.create(Registries.ITEM, tagLoc)
                val tagOpt = BuiltInRegistries.ITEM.getTag(tagKey)
                if (tagOpt.isPresent && tagOpt.get().size() > 0) {
                    tagOpt.get().forEach { output.add(ItemStack(it.value())) }
                } else {
                    if (conditionStr.contains("chicken_food")) {
                        output.add(ItemStack(Items.WHEAT_SEEDS))
                        output.add(ItemStack(Items.MELON_SEEDS))
                        output.add(ItemStack(Items.PUMPKIN_SEEDS))
                        output.add(ItemStack(Items.BEETROOT_SEEDS))
                    }
                }
            } else {
                val loc = ResourceLocation.parse(conditionStr)
                BuiltInRegistries.ITEM.getOptional(loc).ifPresent { item ->
                    if (item != Items.AIR) output.add(ItemStack(item))
                }
            }
        } catch (_: Throwable) {}
        return output
    }

    private fun buildUniversalFoods(): List<ItemStack> {
        val list = mutableListOf<ItemStack>()

        // 1. Baies officielles Cobblemon
        try {
            val berries = CobblemonItems.berries()
            for ((_, berry) in berries) {
                list.add(ItemStack(berry))
            }
        } catch (_: Throwable) {
            val berryFallbacks = listOf(
                CobblemonItems.ORAN_BERRY,
                CobblemonItems.SITRUS_BERRY,
                CobblemonItems.PECHA_BERRY,
                CobblemonItems.LEPPA_BERRY,
                CobblemonItems.RAZZ_BERRY,
                CobblemonItems.LUM_BERRY
            )
            berryFallbacks.forEach { list.add(ItemStack(it)) }
        }

        // 2. Friandises Cobblemon
        try {
            list.add(ItemStack(CobblemonItems.BERRY_JUICE))
            list.add(ItemStack(CobblemonItems.HEALTH_MOCHI))
            list.add(ItemStack(CobblemonItems.FRESH_START_MOCHI))
        } catch (_: Throwable) {}

        // 3. Aliments Minecraft courants
        val vanillaFoods = listOf(
            Items.APPLE,
            Items.GOLDEN_APPLE,
            Items.SWEET_BERRIES,
            Items.GLOW_BERRIES,
            Items.CARROT,
            Items.GOLDEN_CARROT,
            Items.MELON_SLICE,
            Items.BREAD
        )
        vanillaFoods.forEach { list.add(ItemStack(it)) }

        return list.distinctBy { it.item }
    }
}
