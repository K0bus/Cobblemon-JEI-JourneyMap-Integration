package fr.k0bus.cobblemon_jei.data

import fr.k0bus.cobblemon_jei.recipe.PokemonDropItem
import fr.k0bus.cobblemon_jei.recipe.PokemonDropRecipe
import com.cobblemon.mod.common.api.drop.EvolutionItemDropEntry
import com.cobblemon.mod.common.api.drop.ItemDropEntry
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory

/**
 * Chargeur de données pour les butins lâchés par les Pokémon à leur mort / défaite (Cobblemon).
 */
object PokemonDropDataLoader {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/DropLoader")
    private var cachedRecipes: List<PokemonDropRecipe>? = null

    @Synchronized
    fun loadDropRecipes(): List<PokemonDropRecipe> {
        cachedRecipes?.let { return it }

        PokemonEvolutionDataLoader.ensureSpeciesLoaded()

        val recipes = mutableListOf<PokemonDropRecipe>()
        val seenRecipes = mutableSetOf<String>()

        for (species in PokemonSpecies.species) {
            val forms = species.forms
            for (form in forms) {
                val dropTable = form.drops
                val entries = dropTable.entries
                if (entries.isEmpty()) continue

                val dropItems = mutableListOf<PokemonDropItem>()

                for (entry in entries) {
                    if (entry !is ItemDropEntry) continue

                    val itemRes = entry.item
                    val item = BuiltInRegistries.ITEM.get(itemRes)
                    if (item == Items.AIR) continue

                    val itemStack = ItemStack(item)
                    try {
                        val components = entry.components
                        if (components != null && !components.isEmpty) {
                            itemStack.applyComponents(components)
                        }
                    } catch (_: Throwable) {}

                    val percentage = entry.percentage
                    val qRange = entry.quantityRange
                    val minCount = qRange?.first ?: entry.quantity.coerceAtLeast(1)
                    val maxCount = qRange?.last ?: minCount

                    val isEvo = entry is EvolutionItemDropEntry

                    dropItems.add(
                        PokemonDropItem(
                            itemStack = itemStack,
                            percentage = percentage,
                            minCount = minCount,
                            maxCount = maxCount,
                            isEvolutionItem = isEvo
                        )
                    )
                }

                if (dropItems.isEmpty()) continue

                // Dédoublonnage : évite les doublons pour les formes cosmétiques ayant exactement les mêmes drops
                val formKey = form.name.trim().lowercase()
                val dropsSignature = dropItems.joinToString(";") { "${it.itemStack.item}:${it.percentage}:${it.minCount}-${it.maxCount}" }
                val recipeKey = "${species.showdownId()}:$formKey:$dropsSignature"

                if (!seenRecipes.add(recipeKey)) continue

                val amountRange = dropTable.amount
                val minRolls = amountRange.first
                val maxRolls = amountRange.last

                recipes.add(
                    PokemonDropRecipe(
                        species = species,
                        form = form,
                        drops = dropItems,
                        minRolls = minRolls,
                        maxRolls = maxRolls
                    )
                )
            }
        }

        val sorted = recipes.sortedWith(
            compareBy<PokemonDropRecipe> { it.species.nationalPokedexNumber }
                .thenBy { it.species.name }
                .thenBy { it.form.name }
        )

        cachedRecipes = sorted
        logger.info("Cobblemon JEI: ${sorted.size} recettes de butins Pokémon chargées avec succès.")
        return sorted
    }
}
