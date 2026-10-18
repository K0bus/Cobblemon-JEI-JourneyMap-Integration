package fr.k0bus.cobblemon_jei.compat.rct

import net.minecraft.resources.ResourceLocation

/**
 * Statut d'un dresseur dans l'arbre de progression du joueur.
 */
enum class RctProgressionStatus {
    DEFEATED,   // Déjà battu par le joueur (Vert)
    AVAILABLE,  // Débloqué / Actuel objectif à battre (Or pulsant)
    LOCKED      // Verrouillé / Prérequis non remplis (Gris)
}

/**
 * Définition d'une série de progression Radical Cobblemon Trainers.
 */
data class RctSeries(
    val id: String,
    val title: String,
    val description: String = "",
    val difficulty: Int = 5,
    val initialLevelCap: Int = 15,
    val relativeLevelCap: Int = 0,
    val requiredSeries: List<List<String>> = emptyList(),
    val hideTrainerIdentities: Boolean = false
)

/**
 * Définition d'un dresseur adverse RCT.
 */
data class RctTrainer(
    val id: String,
    val name: String,
    val identity: String = "",
    val type: String = "normal",
    val battleFormat: String = "GEN_9_SINGLES",
    val battleTheme: String = "battle.pvn.default",
    val bag: List<RctBagItem> = emptyList(),
    val team: List<RctPokemon> = emptyList(),
    val relativeLevelCap: Int? = null,
    val computedLevelCap: Int = 15,
    val requiredDefeats: List<List<String>> = emptyList(),
    val requiredSeries: List<List<String>> = emptyList(),
    val series: List<String> = emptyList(),
    val optional: Boolean = false,
    val maxTrainerWins: Int = -1,
    val maxTrainerDefeats: Int = 1,
    val signatureItem: String? = null,
    val biomeTagWhitelist: List<String> = emptyList(),
    val biomeTagBlacklist: List<String> = emptyList(),
    val spawnWeightFactor: Float = 1.0f,
    val textureLocation: ResourceLocation = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png")
)

/**
 * Représentation d'un Pokémon de l'équipe du dresseur.
 */
data class RctPokemon(
    val species: String,
    val nickname: String? = null,
    val gender: String = "GENDERLESS",
    val level: Int = 1,
    val nature: String? = null,
    val ability: String? = null,
    val moveset: List<String> = emptyList(),
    val shiny: Boolean = false,
    val heldItem: String? = null,
    val aspects: List<String> = emptyList(),
    val hp: Int = 0,
    val atk: Int = 0,
    val def: Int = 0,
    val spa: Int = 0,
    val spd: Int = 0,
    val spe: Int = 0
)

/**
 * Objet d'inventaire de combat (Potion, Baie, etc.).
 */
data class RctBagItem(
    val item: String,
    val quantity: Int = 1
)

/**
 * Nœud graphique positionné dans l'arbre de progression 2D.
 */
data class RctTreeNode(
    val trainer: RctTrainer,
    var rank: Int = 0,
    var row: Int = 0,
    var x: Double = 0.0,
    var y: Double = 0.0,
    val width: Double = 140.0,
    val height: Double = 56.0,
    var status: RctProgressionStatus = RctProgressionStatus.LOCKED,
    val predecessorIds: MutableSet<String> = mutableSetOf(),
    val successorIds: MutableSet<String> = mutableSetOf()
)

/**
 * Ligne de liaison orientée entre un dresseur prérequis et un dresseur débloqué.
 */
data class RctTreeEdge(
    val fromNode: RctTreeNode,
    val toNode: RctTreeNode,
    val isComplete: Boolean,
    val isReady: Boolean
)

/**
 * Ensemble complet de l'arbre calculé pour une série.
 */
data class RctTreeGraph(
    val series: RctSeries,
    val nodes: List<RctTreeNode>,
    val edges: List<RctTreeEdge>,
    val totalWidth: Double,
    val totalHeight: Double,
    val defeatedCount: Int,
    val totalCount: Int,
    val playerLevelCap: Int
)
