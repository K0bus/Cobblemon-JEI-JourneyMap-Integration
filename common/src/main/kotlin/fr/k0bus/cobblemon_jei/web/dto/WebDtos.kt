package fr.k0bus.cobblemon_jei.web.dto

/**
 * Données de statut général du client Minecraft et du joueur.
 */
data class PlayerStatusDto(
    val playerName: String,
    val dimension: String,
    val biome: String,
    val posX: Int,
    val posY: Int,
    val posZ: Int,
    val pokedexCaught: Int,
    val pokedexEncountered: Int,
    val pokedexTotal: Int,
    val pokedexPercentage: Float,
    val partyCount: Int
)

/**
 * Résumé d'un Pokémon pour l'affichage en grille ou liste du Pokédex.
 */
data class PokemonSummaryDto(
    val nationalDexNumber: Int,
    val speciesId: String,
    val name: String,
    val displayName: String,
    val primaryType: String,
    val secondaryType: String?,
    val generation: Int,
    val status: String, // "CAUGHT", "ENCOUNTERED", "MISSING"
    val formsCount: Int,
    val ownedCount: Int,
    val hasPendingEvolution: Boolean,
    val isLegendary: Boolean
)

/**
 * Détail d'une statistique de base.
 */
data class StatInfoDto(
    val name: String,
    val value: Int,
    val evYield: Int = 0
)

/**
 * Détail d'une capacité / move.
 */
data class MoveInfoDto(
    val id: String,
    val displayName: String,
    val type: String,
    val power: Int?,
    val accuracy: Int?,
    val pp: Int,
    val maxPp: Int,
    val currentPp: Int? = null
)

/**
 * Forme alternative d'un Pokémon.
 */
data class FormInfoDto(
    val name: String,
    val displayName: String,
    val primaryType: String,
    val secondaryType: String?,
    val aspects: List<String>
)

/**
 * Talent (Ability).
 */
data class AbilityInfoDto(
    val name: String,
    val displayName: String,
    val isHidden: Boolean,
    val description: String = ""
)

/**
 * Étape dans l'arbre d'évolution.
 */
data class EvolutionNodeDto(
    val sourceSpeciesId: String,
    val sourceName: String,
    val targetSpeciesId: String,
    val targetName: String,
    val requirementsSummary: String,
    val requiredLevel: Int? = null,
    val requiredItem: String? = null,
    val isItemTriggered: Boolean = false
)

/**
 * Butin (Drop).
 */
data class DropInfoDto(
    val itemId: String,
    val displayName: String,
    val minCount: Int,
    val maxCount: Int,
    val chancePercentage: Float
)

/**
 * Zone de spawn sauvage.
 */
data class SpawnZoneDto(
    val biomes: List<String>,
    val dimensions: List<String>,
    val timeRange: String?,
    val weather: String?,
    val minLevel: Int,
    val maxLevel: Int,
    val weight: Float,
    val rarityBucket: String
)

/**
 * Recette de Poké Snack optimisée.
 */
data class PokeSnackDto(
    val optimalTaste: String,
    val recipeItems: List<String>,
    val description: String
)

/**
 * Job Cobbleworkers au pâturage.
 */
data class PastureJobDto(
    val jobName: String,
    val description: String
)

/**
 * Fiche détaillée complète d'une espèce.
 */
data class SpeciesDetailDto(
    val nationalDexNumber: Int,
    val speciesId: String,
    val name: String,
    val displayName: String,
    val primaryType: String,
    val secondaryType: String?,
    val generation: Int,
    val status: String,
    val height: Float,
    val weight: Float,
    val catchRate: Int,
    val baseFriendship: Int,
    val baseStats: List<StatInfoDto>,
    val totalStats: Int,
    val abilities: List<AbilityInfoDto>,
    val forms: List<FormInfoDto>,
    val evolutions: List<EvolutionNodeDto>,
    val drops: List<DropInfoDto>,
    val spawns: List<SpawnZoneDto>,
    val pokeSnack: PokeSnackDto?,
    val pastureJob: PastureJobDto?,
    val ownedInstances: List<OwnedPokemonDto>
)

/**
 * Informations sur un Pokémon possédé par le joueur (PC ou Équipe).
 */
data class OwnedPokemonDto(
    val uuid: String,
    val speciesId: String,
    val speciesName: String,
    val nationalDexNumber: Int,
    val displayName: String,
    val primaryType: String = "normal",
    val secondaryType: String? = null,
    val level: Int,
    val location: String, // ex: "Équipe (Slot 1)", "PC : Boîte 1"
    val isParty: Boolean,
    val boxIndex: Int,
    val slotIndex: Int,
    val isShiny: Boolean,
    val gender: String,
    val ability: String,
    val nature: String,
    val heldItem: String?,
    val currentHp: Int,
    val maxHp: Int,
    val ivs: Map<String, Int>,
    val evs: Map<String, Int>,
    val moves: List<MoveInfoDto>
)

/**
 * Objectif d'évolution (Évolution en attente / par objet).
 */
data class EvolutionGoalDto(
    val sourceSpeciesId: String,
    val sourceSpeciesName: String,
    val sourceName: String,
    val sourceDexNumber: Int,
    val targetSpeciesId: String,
    val targetSpeciesName: String,
    val targetName: String,
    val targetDexNumber: Int,
    val feasibility: String, // "READY_TO_EVOLVE", "CAN_EVOLVE_WITH_PC", "MISSING_BASE_POKEMON"
    val requirementsSummary: String,
    val requiredItems: List<String>,
    val missingItems: List<String>,
    val isItemOwned: Boolean,
    val minLevel: Int?,
    val isLevelMet: Boolean,
    val minFriendship: Int?,
    val isFriendshipMet: Boolean,
    val ownedLocationSummary: String?, // ex: "PC : Boîte 2, Slot 3"
    val ownedInstancesCount: Int,
    val targetStatus: String = "MISSING"
)

/**
 * Objectif de génération inférieure / reproduction.
 */
data class BreedingOriginDto(
    val baseSpeciesId: String,
    val baseSpeciesName: String,
    val baseName: String,
    val baseDexNumber: Int,
    val isBaby: Boolean,
    val targetStatus: String,
    val candidateParents: List<String>,
    val ownedParentsSummary: String,
    val isReadyToBreed: Boolean,
    val requiredIncense: String?
)

/**
 * Objectif de restauration de fossile.
 */
data class FossilGoalDto(
    val speciesId: String,
    val speciesName: String,
    val nationalDexNumber: Int,
    val fossilName: String,
    val heldFossilCount: Int,
    val isReadyToRevive: Boolean,
    val isCaught: Boolean,
    val isEncountered: Boolean,
    val associatedStructures: List<String>
)

/**
 * Pokémon Légendaire / Fabuleux.
 */
data class LegendaryGoalDto(
    val speciesId: String,
    val speciesName: String,
    val nationalDexNumber: Int,
    val status: String,
    val isCoveredByMonuments: Boolean,
    val monumentStructure: String? = null
)

/**
 * Données d'une boîte du PC.
 */
data class PcBoxDto(
    val boxIndex: Int,
    val boxName: String,
    val totalSlots: Int = 30,
    val occupiedSlots: Int,
    val pokemons: List<OwnedPokemonDto?>
)

/**
 * Entité radar détectée à proximité.
 */
data class RadarEntityDto(
    val speciesId: String,
    val displayName: String,
    val nationalDexNumber: Int,
    val level: Int,
    val distance: Double,
    val relativeYaw: Float,
    val altitudeDiff: Int,
    val isShiny: Boolean,
    val perfectIvsCount: Int,
    val hasHiddenAbility: Boolean,
    val status: String // "CAUGHT", "ENCOUNTERED", "MISSING"
)

/**
 * Événement temps réel SSE.
 */
data class RealtimeEventDto(
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: Any? = null
)
