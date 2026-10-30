package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.domain.expedition.ExpeditionSuggestion
import fr.k0bus.cobblemon_jei.recipe.PokemonEvolutionRecipe
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * Onglets principaux du Pokédex.
 */
enum class MainTab(val label: String, val iconRes: ResourceLocation) {
    POKEDEX("Pokédex", PokedexIcons.POKEDEX_ICON),
    EVOLUTIONS("Évolution & Complétion", PokedexIcons.EVOLUTION_ICON),
    EXPEDITIONS("Expéditions", PokedexIcons.EXPEDITION_ICON)
}

enum class PokedexSubTab(val label: String) {
    ALL("Global"),
    MISSING("Manquants")
}

enum class EvolutionSubTab(val label: String) {
    PENDING_EVOLUTIONS("Évolutions en attente"),
    ITEM_EVOLUTIONS("Pierres & Objets"),
    MISSING_ORIGINS("Origines & Repro"),
    FOSSILS("Fossiles"),
    LEGENDARIES("Légendaires")
}

enum class Tab(val labelKey: String) {
    ALL("gui.cobblemon_jei.pokedex.tab.all"),
    MISSING("gui.cobblemon_jei.pokedex.tab.missing"),
    EVOLUTIONS("gui.cobblemon_jei.pokedex.tab.evolutions")
}

/**
 * Données d'affichage du contenu gauche du Pokédex.
 */
data class LeftContentData(
    val mainTab: MainTab,
    val pokedexSubTab: PokedexSubTab,
    val evolutionSubTab: EvolutionSubTab,
    val selectedTracked: TrackedPokemon?,
    val selectedGoal: EvolutionGoal? = null,
    val isExpeditionLoading: Boolean = false,
    val allTracked: List<TrackedPokemon>,
    val filteredTracked: List<TrackedPokemon>,
    val filteredGoals: List<EvolutionGoal>,
    val filteredItemGoals: List<EvolutionGoal>,
    val filteredBreedingOrigins: List<BreedingOriginGoal>,
    val filteredFossilGoals: List<FossilGoal>,
    val filteredLegendaries: List<TrackedPokemon>,
    val expeditionSuggestions: List<ExpeditionSuggestion>
)

/**
 * Statut d'un Pokémon dans le Pokédex du joueur.
 */
enum class PokedexStatus {
    CAUGHT,      // Déjà capturé / enregistré ou possédé
    ENCOUNTERED, // Vu / combattu dans la nature
    MISSING      // Non découvert / manquant
}

/**
 * Onglets du panneau droit d'inspection d'un Pokémon.
 */
enum class RightPanelTab(val label: String, val tooltip: String) {
    DESCRIPTION("Infos", "Description & Morphologie"),
    FORMS("Formes", "Formes & Variantes régionales"),
    ABILITIES("Talents", "Talents & Capacités spéciales"),
    STATS("Stats", "Statistiques de base & EV"),
    EVOLUTIONS("Évos", "Lignée évolutive (Source & Destination)"),
    DROPS("Butin", "Objets lâchés à la défaite"),
    PASTURE("Pâturage", "Métiers au pâturage (Cobbleworkers)"),
    SPAWNS("Zones", "Zones d'apparition sauvage")
}

/**
 * Sous-catégories de la fiche d'apparition (Zones) d'un Pokémon.
 */
enum class SpawnSubCategory(val label: String, val tooltip: String) {
    NATURAL("🌲 Naturel", "Zones et conditions d'apparition sauvage"),
    POKE_SNACK("🍪 Poké Snack", "Assaisonnements optimaux et recette pour attirer ce Pokémon")
}

/**
 * Faisabilité d'une évolution pour obtenir un Pokémon manquant.
 */
enum class EvolutionFeasibility {
    READY_TO_EVOLVE,          // Pokémon possédé dans PC/Équipe + objets possédés + niveau atteint !
    CAN_EVOLVE_WITH_PC,       // Pokémon possédé dans PC/Équipe, mais objets ou niveaux manquants
    MISSING_BASE_POKEMON      // Le joueur ne possède pas encore le Pokémon de base
}

/**
 * Statut de la capacité requise pour l'évolution.
 */
enum class MoveRequirementStatus {
    NOT_REQUIRED,       // Aucune capacité requise
    KNOWN,              // Connaît déjà la capacité activement
    RELEARNABLE,        // Peut être réapprise immédiatement dans le menu
    LEVEL_UP,           // S'apprend à un niveau supérieur
    TM_IN_INVENTORY,    // CT possédée dans l'inventaire
    TM_MISSING,         // CT requise mais absente de l'inventaire
    CANNOT_LEARN        // Ne peut pas apprendre cette capacité
}

/**
 * Informations sur un Pokémon possédé par le joueur (dans l'équipe ou le PC).
 */
data class OwnedPokemonInfo(
    val pokemon: Pokemon,
    val location: String,
    val isParty: Boolean,
    val boxIndex: Int = -1
) {
    val level: Int get() = pokemon.level
    val friendship: Int get() = pokemon.friendship
    val heldItem: ItemStack get() = pokemon.heldItem()
    val gender: Gender get() = pokemon.gender
}

/**
 * Données de suivi pour une espèce de Pokémon donnée.
 */
data class TrackedPokemon(
    val species: Species,
    val status: PokedexStatus,
    val ownedInstances: List<OwnedPokemonInfo>
) {
    val nationalDexNumber: Int get() = species.nationalPokedexNumber
    val isCaught: Boolean get() = status == PokedexStatus.CAUGHT
    val isEncountered: Boolean get() = status == PokedexStatus.ENCOUNTERED
    val isMissing: Boolean get() = status == PokedexStatus.MISSING
    val ownedCount: Int get() = ownedInstances.size
}

/**
 * Représente un objectif d'évolution permettant d'obtenir un Pokémon manquant.
 */
data class EvolutionGoal(
    val sourceSpecies: Species,
    val sourceForm: FormData,
    val targetSpecies: Species,
    val targetForm: FormData,
    val recipe: PokemonEvolutionRecipe,
    val feasibility: EvolutionFeasibility,
    val ownedSourcePokemons: List<OwnedPokemonInfo>,
    val requiredItems: List<ItemStack>,
    val missingItems: List<ItemStack>,
    val minLevel: Int?,
    val isLevelMet: Boolean,
    val minFriendship: Int?,
    val isFriendshipMet: Boolean,
    val requirementsSummary: String,
    val additionalConditions: List<String>,
    val targetStatus: PokedexStatus,
    val requiredGender: Gender? = null,
    val hasGenderMatch: Boolean = true,
    val requiredForm: FormData? = null,
    val hasFormMatch: Boolean = true,
    val requiredMove: String? = null,
    val requiredMoveType: String? = null,
    val moveStatus: MoveRequirementStatus = MoveRequirementStatus.NOT_REQUIRED,
    val moveLearnLevel: Int? = null,
    val stepsRequired: Int? = null,
    val useMoveCount: Int? = null,
    val specialActionSummary: String? = null
) {
    val hasSourceInPC: Boolean get() = hasFormMatch && ownedSourcePokemons.isNotEmpty()
    val hasAllRequiredItems: Boolean get() = missingItems.isEmpty()

    val sourceLocationSummary: String get() = if (ownedSourcePokemons.isEmpty()) {
        "Non possédé"
    } else {
        val count = ownedSourcePokemons.size
        val firstLoc = ownedSourcePokemons.first().location
        if (count == 1) firstLoc else "$firstLoc (+${count - 1})"
    }
}

/**
 * Objectif de reproduction pour obtenir une forme de base / origine manquante au Pokédex.
 */
data class BreedingOriginGoal(
    val baseSpecies: Species,
    val targetStatus: PokedexStatus,
    val isBaby: Boolean,
    val candidateParents: List<Species>,
    val ownedParents: List<OwnedPokemonInfo>,
    val caughtParents: List<Species>,
    val requiredIncense: String? = null,
    val isMultipleOrigins: Boolean = candidateParents.size > 1,
    val isReadyToBreed: Boolean = ownedParents.isNotEmpty()
) {
    val nationalDexNumber: Int get() = baseSpecies.nationalPokedexNumber
    val parentSummary: String get() = if (ownedParents.isNotEmpty()) {
        val first = ownedParents.first()
        val name = first.pokemon.species.translatedName.string
        val loc = first.location
        if (ownedParents.size == 1) "$name ($loc)" else "$name ($loc) +${ownedParents.size - 1}"
    } else if (caughtParents.isNotEmpty()) {
        "Capturé : " + caughtParents.joinToString(", ") { it.translatedName.string }
    } else {
        candidateParents.joinToString(" / ") { it.translatedName.string }
    }
}

/**
 * Statistiques globales du Pokédex du joueur.
 */
data class PokedexTrackerStats(
    val totalSpecies: Int,
    val caughtCount: Int,
    val encounteredCount: Int,
    val missingCount: Int,
    val readyEvolutionsCount: Int,
    val pcEvolutionsCount: Int
) {
    val completionPercent: Float get() = if (totalSpecies > 0) (caughtCount.toFloat() / totalSpecies.toFloat()) * 100f else 0f
}

/**
 * Modèle de données pour les cartes d'objectifs de réanimation de fossiles.
 */
data class FossilGoal(
    val recipe: fr.k0bus.cobblemon_jei.data.PokemonFossilDataLoader.FossilRecipeInfo,
    val targetSpecies: Species?,
    val tracked: TrackedPokemon?,
    val isReadyToRevive: Boolean,
    val heldFossilCount: Int,
    val isCaught: Boolean,
    val isEncountered: Boolean
)
