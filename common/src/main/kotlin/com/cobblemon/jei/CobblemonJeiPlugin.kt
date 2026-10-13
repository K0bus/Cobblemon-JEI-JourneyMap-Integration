package com.cobblemon.jei

import com.cobblemon.jei.data.PokemonSpawnDataLoader
import com.cobblemon.jei.ingredient.PokemonIngredientHelper
import com.cobblemon.jei.ingredient.PokemonIngredientRenderer
import com.cobblemon.jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.jei.recipe.PokemonEvolutionRecipeCategory
import com.cobblemon.jei.recipe.PokemonSpawnRecipeCategory
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
        com.cobblemon.jei.data.PokemonEvolutionDataLoader.ensureSpeciesLoaded()

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
     * Enregistre les catégories de recette pour les apparitions et évolutions de Pokémon.
     */
    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        val guiHelper = registration.jeiHelpers.guiHelper
        registration.addRecipeCategories(
            PokemonSpawnRecipeCategory(guiHelper),
            PokemonEvolutionRecipeCategory(guiHelper)
        )
    }

    /**
     * Charge et enregistre les données de spawn et d'évolution de chaque Pokémon pour JEI.
     */
    override fun registerRecipes(registration: IRecipeRegistration) {
        val spawnRecipes = PokemonSpawnDataLoader.loadSpawnRecipes()
        logger.info("Cobblemon JEI: Enregistrement de ${spawnRecipes.size} recettes d'apparition dans JEI.")
        registration.addRecipes(PokemonSpawnRecipeCategory.RECIPE_TYPE, spawnRecipes)

        val evolutionRecipes = com.cobblemon.jei.data.PokemonEvolutionDataLoader.loadEvolutionRecipes()
        logger.info("Cobblemon JEI: Enregistrement de ${evolutionRecipes.size} recettes d'évolution dans JEI.")
        registration.addRecipes(PokemonEvolutionRecipeCategory.RECIPE_TYPE, evolutionRecipes)
    }

    /**
     * Déclare les catalyseurs de recette dans JEI.
     */
    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        // Catalyseurs pour les spawns (boussole et Poké Ball permettent d'ouvrir la catégorie globale des apparitions)
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

        // Note: Aucun catalyseur n'est enregistré pour PokemonEvolutionRecipeCategory.
        // Les objets d'évolution (pierres, câble de liaison, etc.) sont des ingrédients INPUT du slot 2.
        // Les enregistrer en tant que catalyseurs ferait afficher TOUTES les évolutions du jeu lors du "Use",
        // alors que nous voulons n'afficher QUE les évolutions qui requièrent cet objet spécifique.
    }
}
