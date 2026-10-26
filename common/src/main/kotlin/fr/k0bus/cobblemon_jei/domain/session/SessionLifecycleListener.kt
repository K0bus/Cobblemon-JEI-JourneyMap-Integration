package fr.k0bus.cobblemon_jei.domain.session

import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level

/**
 * Interface d'écoute des changements de cycle de vie d'une session de monde client.
 */
interface SessionLifecycleListener {
    /**
     * Déclenché lorsqu'une nouvelle session de monde démarre (connexion ou chargement).
     */
    fun onSessionStarted(sessionId: String, initialDimension: ResourceKey<Level>?) {}

    /**
     * Déclenché lorsque le joueur change de dimension.
     */
    fun onDimensionChanged(sessionId: String, newDimension: ResourceKey<Level>) {}

    /**
     * Déclenché lorsque la session se termine (déconnexion ou sortie du monde).
     */
    fun onSessionEnded(sessionId: String) {}
}
