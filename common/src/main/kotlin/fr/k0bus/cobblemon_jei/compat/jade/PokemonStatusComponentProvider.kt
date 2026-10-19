package fr.k0bus.cobblemon_jei.compat.jade

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import fr.k0bus.cobblemon_jei.pokedex.PokemonTrackerService
import fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import snownee.jade.api.EntityAccessor
import snownee.jade.api.IEntityComponentProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.config.IPluginConfig

/**
 * Fournisseur de composants Jade pour afficher le statut Pokédex et les stocks PC/Équipe
 * en regardant un Pokémon dans le monde.
 *
 * Affiche :
 * - Statut Pokédex de l'espèce : Vu, Capturé, Non découvert
 * - Statut de la forme : Forme capturée, Forme vue, Forme manquante
 * - Statut chromatique si shiny
 * - Quantité totale possédée dans le PC et dans l'équipe
 * - Badge si le Pokémon est ciblé par le radar actif
 */
class PokemonStatusComponentProvider private constructor() : IEntityComponentProvider {

    override fun getUid(): ResourceLocation = UID

    override fun appendTooltip(tooltip: ITooltip, accessor: EntityAccessor, config: IPluginConfig) {
        val entity = accessor.entity as? PokemonEntity ?: return
        val pokemon = entity.pokemon
        val species = pokemon.species
        val form = pokemon.form
        val isShiny = pokemon.shiny

        // 1. Données du Pokédex client
        val dexData = try { CobblemonClient.clientPokedexData } catch (_: Throwable) { null }
        val specRecord = dexData?.speciesRecords?.get(species.resourceIdentifier)
        val speciesProgress = specRecord?.getKnowledge() ?: PokedexEntryProgress.NONE

        // 2. Données des stocks possédés par le joueur (PC et Équipe)
        val allTracked = PokemonTrackerService.getAllTracked()
        val tracked = allTracked.find {
            try {
                it.species.resourceIdentifier == species.resourceIdentifier
            } catch (_: Throwable) {
                it.species.name.equals(species.name, ignoreCase = true)
            }
        }
        val ownedInstances = tracked?.ownedInstances ?: emptyList()
        val totalOwned = ownedInstances.size
        val partyOwned = ownedInstances.count { it.isParty }
        val pcOwned = ownedInstances.count { !it.isParty }
        val formOwned = ownedInstances.count { PokemonTrackerService.isFormMatch(it.pokemon, form, species) }

        // Statut Pokédex de l'espèce
        val isCaught = speciesProgress == PokedexEntryProgress.CAUGHT || totalOwned > 0
        val isSeen = speciesProgress == PokedexEntryProgress.ENCOUNTERED

        val speciesStatusComp = when {
            isCaught -> Component.translatable("jade.cobblemon_jei.status_caught").withStyle(ChatFormatting.GREEN)
            isSeen -> Component.translatable("jade.cobblemon_jei.status_seen").withStyle(ChatFormatting.GOLD)
            else -> Component.translatable("jade.cobblemon_jei.status_missing").withStyle(ChatFormatting.RED)
        }

        // Statut de la Forme spécifique
        val formName = form.name.lowercase().ifBlank { "normal" }
        val formRecord = specRecord?.getFormRecord(formName)
            ?: specRecord?.getFormRecord(form.name.lowercase())
            ?: specRecord?.getFormRecord("")

        val isFormCaught = formOwned > 0 || (formRecord != null && formRecord.knowledge == PokedexEntryProgress.CAUGHT)
        val isFormSeen = formRecord != null && formRecord.knowledge == PokedexEntryProgress.ENCOUNTERED

        val formStatusComp = when {
            isFormCaught -> Component.translatable("jade.cobblemon_jei.form_caught").withStyle(ChatFormatting.GREEN)
            isFormSeen -> Component.translatable("jade.cobblemon_jei.form_seen").withStyle(ChatFormatting.GOLD)
            else -> Component.translatable("jade.cobblemon_jei.form_missing").withStyle(ChatFormatting.RED)
        }

        // Ligne 1 : Statut Pokédex global + Forme
        val line1 = Component.translatable("jade.cobblemon_jei.status_title", speciesStatusComp)
            .append(Component.literal(" §8• ").append(formStatusComp))
        tooltip.add(line1)

        // Statut Shiny si le spécimen observé est chromatique
        if (isShiny) {
            val isShinyCaught = ownedInstances.any { it.pokemon.shiny && PokemonTrackerService.isFormMatch(it.pokemon, form, species) }
                || (formRecord != null && formRecord.hasSeenShinyState(true) && formRecord.knowledge == PokedexEntryProgress.CAUGHT)
            val shinyComp = if (isShinyCaught) {
                Component.translatable("jade.cobblemon_jei.shiny_caught").withStyle(ChatFormatting.YELLOW)
            } else {
                Component.translatable("jade.cobblemon_jei.shiny_missing").withStyle(ChatFormatting.GOLD)
            }
            tooltip.add(Component.literal("§6★ ").append(shinyComp))
        }

        // Ligne 2 : Quantité présente dans le PC et dans l'équipe
        val line2 = when {
            totalOwned == 0 -> Component.translatable("jade.cobblemon_jei.stock_none").withStyle(ChatFormatting.GRAY)
            partyOwned > 0 && pcOwned > 0 -> {
                val base = Component.translatable("jade.cobblemon_jei.stock_party_and_pc", totalOwned, partyOwned, pcOwned)
                    .withStyle(ChatFormatting.AQUA)
                if (species.forms.size > 1 && formOwned != totalOwned) {
                    base.append(Component.literal(" §7(").append(Component.translatable("jade.cobblemon_jei.stock_this_form", formOwned)).append(")"))
                } else base
            }
            partyOwned > 0 -> {
                val base = Component.translatable("jade.cobblemon_jei.stock_party_only", totalOwned, partyOwned)
                    .withStyle(ChatFormatting.AQUA)
                if (species.forms.size > 1 && formOwned != totalOwned) {
                    base.append(Component.literal(" §7(").append(Component.translatable("jade.cobblemon_jei.stock_this_form", formOwned)).append(")"))
                } else base
            }
            else -> {
                val base = Component.translatable("jade.cobblemon_jei.stock_pc_only", totalOwned, pcOwned)
                    .withStyle(ChatFormatting.AQUA)
                if (species.forms.size > 1 && formOwned != totalOwned) {
                    base.append(Component.literal(" §7(").append(Component.translatable("jade.cobblemon_jei.stock_this_form", formOwned)).append(")"))
                } else base
            }
        }
        tooltip.add(line2)

        // Ligne 3 : Cible du Radar traquée
        if (PokemonTrackingManager.isTarget(entity)) {
            tooltip.add(Component.translatable("jade.cobblemon_jei.target_tracked").withStyle(ChatFormatting.YELLOW))
        }
    }

    companion object {
        val UID: ResourceLocation = CobblemonJadePlugin.id("pokemon_status")
        val INSTANCE = PokemonStatusComponentProvider()
    }
}
