package fr.k0bus.cobblemon_jei.leaderboard

import net.minecraft.ChatFormatting

/**
 * Catégories de classement correspondant aux objectifs scoreboard de CobblemonSB.
 */
enum class CobblemonLeaderboardCategory(
    val objectiveName: String,
    val aliases: List<String>,
    val displayName: String,
    val shortName: String,
    val unit: String,
    val iconEmoji: String,
    val color: ChatFormatting
) {
    POKEDEX(
        "cobble_pokedex",
        listOf("cobble_pokedex", "cobble_dex", "pokedex", "cobbledex", "dex"),
        "Entrées Pokédex",
        "Pokédex",
        "Espèces",
        "📖",
        ChatFormatting.GREEN
    ),
    CAPTURES(
        "cobble_captured",
        listOf("cobble_captured", "cobble_captures", "captures", "cobble_capture"),
        "Pokémon Capturés",
        "Captures",
        "Captures",
        "🔴",
        ChatFormatting.RED
    ),
    SHINIES(
        "cobble_shinies",
        listOf("cobble_shinies", "cobble_shiny", "shinies", "shiny"),
        "Pokémon Chromatiques",
        "Shinies",
        "Shinies",
        "✨",
        ChatFormatting.GOLD
    ),
    CAP_LEVEL(
        "cobble_cap_level",
        listOf("cobble_cap_level", "cobble_level", "cobble_cap", "cap_level", "levels", "level"),
        "Niveau Max / Cap",
        "Niveau",
        "Niv.",
        "👑",
        ChatFormatting.LIGHT_PURPLE
    );

    val allObjectiveNames: List<String>
        get() = (listOf(objectiveName) + aliases).distinct()

    companion object {
        fun fromObjective(name: String): CobblemonLeaderboardCategory? {
            return entries.firstOrNull { cat ->
                cat.allObjectiveNames.any { it.equals(name, ignoreCase = true) }
            }
        }
    }
}
