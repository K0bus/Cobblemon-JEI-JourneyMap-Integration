package fr.k0bus.cobblemon_jei.pokedex

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

/**
 * Enregistrement des touches et raccourcis clavier pour Cobblemon JEI & Pokédex Tracker.
 */
object CobblemonKeyBindings {

    const val KEY_CATEGORY = "key.categories.cobblemon_jei"

    /**
     * Touche bindable pour ouvrir l'écran complet du Pokédex & Tracker d'évolutions.
     * Par défaut assignée à la touche 'P' (ou 'O' si conflit).
     */
    val OPEN_POKEDEX_KEY: KeyMapping = KeyMapping(
        "key.cobblemon_jei.pokedex",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_P,
        KEY_CATEGORY
    )

    /**
     * Touche bindable pour afficher/masquer ou cycler les classements du scoreboard custom HUD.
     * Par défaut assignée à la touche 'F8'.
     */
    val TOGGLE_LEADERBOARD_HUD_KEY: KeyMapping = KeyMapping(
        "key.cobblemon_jei.toggle_leaderboard_hud",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_F8,
        KEY_CATEGORY
    )
}
