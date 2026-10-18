package fr.k0bus.cobblemon_jei.ingredient

import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import mezz.jei.api.ingredients.IIngredientType
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * Wrapper d'ingrédient JEI représentant une espèce et une forme de Pokémon spécifiques de Cobblemon.
 */
data class PokemonSpeciesIngredient(
    val species: Species,
    val form: FormData = species.standardForm
) {
    /**
     * Identifiant unique de l'espèce (ex: "cobblemon:pikachu").
     */
    val id: ResourceLocation
        get() = try {
            species.resourceIdentifier
        } catch (_: Throwable) { null } ?: ResourceLocation.fromNamespaceAndPath("cobblemon", species.name.lowercase().replace(" ", "_"))

    /**
     * Nom d'affichage traduit ou formaté avec la forme.
     */
    val displayName: Component
        get() = species.translatedName

    /**
     * Numéro national du Pokédex.
     */
    val nationalDexNumber: Int
        get() = species.nationalPokedexNumber

    /**
     * Génération du Pokémon déduite du numéro de Pokédex national.
     */
    val generation: Int
        get() = when (nationalDexNumber) {
            in 1..151 -> 1
            in 152..251 -> 2
            in 252..386 -> 3
            in 387..493 -> 4
            in 494..649 -> 5
            in 650..721 -> 6
            in 722..809 -> 7
            in 810..905 -> 8
            in 906..1025 -> 9
            else -> 1
        }

    /**
     * Types élémentaires de la forme.
     */
    val primaryType: ElementalType
        get() = form.primaryType

    val secondaryType: ElementalType?
        get() = form.secondaryType

    /**
     * Instance de rendu mise en cache sous forme d'ItemStack Minecraft
     * générée par l'utilitaire natif [PokemonItem.from].
     */
    val itemStack: ItemStack by lazy {
        try {
            val aspects = mutableSetOf<String>()
            aspects.addAll(form.aspects)
            PokemonItem.from(species, aspects, 1)
        } catch (_: Throwable) {
            ItemStack.EMPTY
        }
    }

    companion object {
        /**
         * Définition du type d'ingrédient pour l'API JEI.
         */
        val TYPE: IIngredientType<PokemonSpeciesIngredient> = IIngredientType { PokemonSpeciesIngredient::class.java }
    }
}
