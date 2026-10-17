package fr.k0bus.cobblemon_jei

import fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader
import fr.k0bus.cobblemon_jei.ingredient.PokemonIngredientHelper
import fr.k0bus.cobblemon_jei.ingredient.PokemonIngredientRenderer
import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import fr.k0bus.cobblemon_jei.recipe.PokemonEvolutionRecipeCategory
import fr.k0bus.cobblemon_jei.recipe.PokemonSpawnRecipeCategory
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.registration.IModIngredientRegistration
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory

/**
 * Point d'entrée principal du plugin JEI pour Cobblemon.
 * Enregistre l'ingrédient personnalisé [PokemonSpeciesIngredient],
 * la catégorie de spawn [PokemonSpawnRecipeCategory] et les données associées.
 */
@JeiPlugin
class CobblemonJeiPlugin : IModPlugin {

    companion object {
        const val MOD_ID = "cobblemon_jei"
        val PLUGIN_UID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, "jei_plugin")
        private val logger = LoggerFactory.getLogger("CobblemonJEI/Plugin")
    }

    override fun getPluginUid(): ResourceLocation = PLUGIN_UID

    /**
     * Enregistre le type d'ingrédient [PokemonSpeciesIngredient] dans JEI,
     * permettant la recherche directe de chaque Pokémon par nom, numéro de Pokédex et types.
     */
    override fun registerIngredients(registration: IModIngredientRegistration) {
        // Précharge l'ensemble des espèces Cobblemon pour garantir leur présence même si aucun monde n'est ouvert
        fr.k0bus.cobblemon_jei.data.PokemonEvolutionDataLoader.ensureSpeciesLoaded()

        val ingredients = mutableListOf<PokemonSpeciesIngredient>()

        for (species in PokemonSpecies.species) {
            // Enregistrement de la forme standard
            ingredients.add(PokemonSpeciesIngredient(species, species.standardForm))

            // Enregistrement des formes alternatives (régionales, méga, etc.)
            for (form in species.forms) {
                if (form != species.standardForm) {
                    ingredients.add(PokemonSpeciesIngredient(species, form))
                }
            }
        }

        logger.info("Cobblemon JEI: Enregistrement de ${ingredients.size} Pokémon (espèces et formes) dans JEI.")

        registration.register(
            PokemonSpeciesIngredient.TYPE,
            ingredients,
            PokemonIngredientHelper(),
            PokemonIngredientRenderer()
        )
    }

    /**
     * Enregistre les catégories de recette pour les apparitions, évolutions, interactions de Pokémon
     * et le travail au pâturage (si Cobbleworkers est installé).
     */
    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        val guiHelper = registration.jeiHelpers.guiHelper
        val categories = mutableListOf<mezz.jei.api.recipe.category.IRecipeCategory<*>>(
            PokemonSpawnRecipeCategory(guiHelper),
            PokemonEvolutionRecipeCategory(guiHelper),
            fr.k0bus.cobblemon_jei.recipe.PokemonInteractionRecipeCategory(guiHelper)
        )

        if (fr.k0bus.cobblemon_jei.compat.cobbleworkers.CobbleworkersCompat.isLoaded) {
            categories.add(fr.k0bus.cobblemon_jei.recipe.PokemonPastureRecipeCategory(guiHelper))
        }

        registration.addRecipeCategories(*categories.toTypedArray())
    }

    /**
     * Charge et enregistre les données de spawn, d'évolution, d'interactions et de pâturage (Cobbleworkers).
     */
    override fun registerRecipes(registration: IRecipeRegistration) {
        val spawnRecipes = PokemonSpawnDataLoader.loadSpawnRecipes()
        logger.info("Cobblemon JEI: Enregistrement de ${spawnRecipes.size} recettes d'apparition dans JEI.")
        registration.addRecipes(PokemonSpawnRecipeCategory.RECIPE_TYPE, spawnRecipes)

        val evolutionRecipes = fr.k0bus.cobblemon_jei.data.PokemonEvolutionDataLoader.loadEvolutionRecipes()
        logger.info("Cobblemon JEI: Enregistrement de ${evolutionRecipes.size} recettes d'évolution dans JEI.")
        registration.addRecipes(PokemonEvolutionRecipeCategory.RECIPE_TYPE, evolutionRecipes)

        val interactionRecipes = fr.k0bus.cobblemon_jei.data.PokemonInteractionDataLoader.loadInteractionRecipes()
        logger.info("Cobblemon JEI: Enregistrement de ${interactionRecipes.size} recettes d'interactions Pokémon dans JEI.")
        registration.addRecipes(fr.k0bus.cobblemon_jei.recipe.PokemonInteractionRecipeCategory.RECIPE_TYPE, interactionRecipes)

        if (fr.k0bus.cobblemon_jei.compat.cobbleworkers.CobbleworkersCompat.isLoaded) {
            val pastureRecipes = fr.k0bus.cobblemon_jei.data.CobbleworkersJobDataLoader.loadPastureRecipes()
            logger.info("Cobblemon JEI: Enregistrement de ${pastureRecipes.size} métiers de pâturage (Cobbleworkers) dans JEI.")
            registration.addRecipes(fr.k0bus.cobblemon_jei.recipe.PokemonPastureRecipeCategory.RECIPE_TYPE, pastureRecipes)
        }
    }

    /**
     * Déclare les catalyseurs de recette dans JEI.
     */
    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        // Catalyseurs pour les spawns (boussole et Poké Ball)
        registration.addRecipeCatalyst(
            ItemStack(Items.COMPASS),
            PokemonSpawnRecipeCategory.RECIPE_TYPE
        )
        try {
            registration.addRecipeCatalyst(
                ItemStack(CobblemonItems.POKE_BALL),
                PokemonSpawnRecipeCategory.RECIPE_TYPE
            )
        } catch (_: Throwable) {}

        // Catalyseurs pour les interactions directes (cisailles, seau, brosse)
        registration.addRecipeCatalyst(
            ItemStack(Items.SHEARS),
            fr.k0bus.cobblemon_jei.recipe.PokemonInteractionRecipeCategory.RECIPE_TYPE
        )
        registration.addRecipeCatalyst(
            ItemStack(Items.BUCKET),
            fr.k0bus.cobblemon_jei.recipe.PokemonInteractionRecipeCategory.RECIPE_TYPE
        )
        registration.addRecipeCatalyst(
            ItemStack(Items.BRUSH),
            fr.k0bus.cobblemon_jei.recipe.PokemonInteractionRecipeCategory.RECIPE_TYPE
        )

        // Catalyseur pour les recettes de pâturage Cobbleworkers (Le bloc de pâturage)
        if (fr.k0bus.cobblemon_jei.compat.cobbleworkers.CobbleworkersCompat.isLoaded) {
            try {
                registration.addRecipeCatalyst(
                    ItemStack(com.cobblemon.mod.common.CobblemonBlocks.PASTURE),
                    fr.k0bus.cobblemon_jei.recipe.PokemonPastureRecipeCategory.RECIPE_TYPE
                )
            } catch (_: Throwable) {}
        }
    }
}
