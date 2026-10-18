package fr.k0bus.cobblemon_jei.leaderboard

import net.minecraft.client.resources.PlayerSkin

/**
 * Représente l'entrée d'un joueur dans le classement d'une catégorie,
 * enrichie avec le nombre de Pokémon capturés et d'entrées vues dans le Pokédex.
 */
data class LeaderboardPlayerEntry(
    val rank: Int,
    val username: String,
    val score: Int,
    val isLocalPlayer: Boolean,
    var skin: PlayerSkin? = null,
    val caughtCount: Int = 0,
    val seenCount: Int = 0,
    val shiniesCount: Int = 0
)
