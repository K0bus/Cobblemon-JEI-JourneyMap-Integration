package fr.k0bus.cobblemon_jei.leaderboard

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * Catégories de classement correspondant aux objectifs scoreboard de CobblemonSB.
 */
enum class CobblemonLeaderboardCategory(
    val objectiveName: String,
    val aliases: List<String>,
    private val defaultDisplayName: String,
    private val defaultShortName: String,
    private val defaultUnit: String,
    val iconEmoji: String,
    val color: ChatFormatting,
    val translationKey: String
) {
    POKEDEX(
        "cobble_pokedex",
        listOf("cobble_pokedex", "cobble_dex", "pokedex", "cobbledex", "dex"),
        "Entrées Pokédex",
        "Pokédex",
        "Espèces",
        "📖",
        ChatFormatting.GREEN,
        "gui.cobblemon_jei.leaderboard.cat.pokedex"
    ),
    CAPTURES(
        "cobble_captured",
        listOf("cobble_captured", "cobble_captures", "captures", "cobble_capture"),
        "Pokémon Capturés",
        "Captures",
        "Captures",
        "🔴",
        ChatFormatting.RED,
        "gui.cobblemon_jei.leaderboard.cat.captures"
    ),
    SHINIES(
        "cobble_shinies",
        listOf("cobble_shinies", "cobble_shiny", "shinies", "shiny"),
        "Pokémon Chromatiques",
        "Shinies",
        "Shinies",
        "✨",
        ChatFormatting.GOLD,
        "gui.cobblemon_jei.leaderboard.cat.shinies"
    ),
    CAP_LEVEL(
        "cobble_cap_level",
        listOf("cobble_cap_level", "cobble_level", "cobble_cap", "cap_level", "levels", "level"),
        "Niveau Max / Cap",
        "Niveau",
        "Niv.",
        "👑",
        ChatFormatting.LIGHT_PURPLE,
        "gui.cobblemon_jei.leaderboard.cat.cap_level"
    );

    val displayName: String
        get() {
            val trans = Component.translatable(translationKey).string
            return if (trans == translationKey) defaultDisplayName else trans
        }

    val shortName: String
        get() {
            val key = "${translationKey}_short"
            val trans = Component.translatable(key).string
            return if (trans == key) defaultShortName else trans
        }

    val unit: String
        get() {
            val key = "${translationKey}_unit"
            val trans = Component.translatable(key).string
            return if (trans == key) defaultUnit else trans
        }

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
