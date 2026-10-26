package fr.k0bus.cobblemon_jei.journeymap

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Moteur de rendu et d'effets visuels 3D dans le monde client pour Cobblemon.
 * Gère :
 * 1. L'émission d'auras de particules (dorées pour les Shiny, cyan pour les cibles de radar).
 * 2. La surbrillance de contour (Glowing client) pour repérer immédiatement les cibles à travers le relief.
 */
object InWorldVisualEngine {

    // Entités actuellement en surbrillance (Glowing)
    private val activeGlowingUuids = ConcurrentHashMap.newKeySet<UUID>()

    // Distance maximale pour le glowing et les particules
    const val MAX_GLOWING_DISTANCE_SQ = 48.0 * 48.0
    const val MAX_PARTICLE_DISTANCE_SQ = 64.0 * 64.0

    /**
     * Traite les effets visuels pour toutes les entités détectées lors du tick de scan.
     */
    fun processVisuals(
        level: Level,
        player: Player,
        trackedEntities: Collection<PokemonEntity>
    ) {
        val playerPos = player.position()
        val filter = PokemonTrackingManager.advancedFilter
        val shouldGlow = filter.inWorldGlowing
        val shouldSpawnParticles = filter.inWorldParticles

        val currentGlowCandidates = mutableSetOf<UUID>()

        for (entity in trackedEntities) {
            if (!entity.isAlive || entity.isRemoved) continue

            val uuid = entity.uuid
            val distSq = entity.distanceToSqr(playerPos.x, playerPos.y, playerPos.z)
            val pokemon = entity.pokemon
            val isShiny = pokemon.shiny
            val isTarget = PokemonTrackingManager.isTarget(entity)

            // 1. Surbrillance Glowing Client
            if (shouldGlow && (isShiny || isTarget) && distSq <= MAX_GLOWING_DISTANCE_SQ) {
                currentGlowCandidates.add(uuid)
                if (!entity.hasGlowingTag()) {
                    try {
                        entity.setGlowingTag(true)
                        activeGlowingUuids.add(uuid)
                    } catch (_: Throwable) {}
                }
            }

            // 2. Émission d'auras de particules
            if (shouldSpawnParticles && (isShiny || isTarget) && distSq <= MAX_PARTICLE_DISTANCE_SQ) {
                spawnEntityParticles(level, entity, isShiny)
            }
        }

        // Nettoyage du glowing pour les entités qui ne sont plus éligibles ou hors de portée
        val toClean = activeGlowingUuids.filter { !currentGlowCandidates.contains(it) }
        for (deadUuid in toClean) {
            activeGlowingUuids.remove(deadUuid)
        }
    }

    /**
     * Fait apparaître un nuage d'étincelles autour du Pokémon.
     */
    private fun spawnEntityParticles(level: Level, entity: PokemonEntity, isShiny: Boolean) {
        val pos = entity.position()
        val width = entity.bbWidth.coerceAtLeast(0.4f).toDouble()
        val height = entity.bbHeight.coerceAtLeast(0.4f).toDouble()

        val particleCount = if (isShiny) 6 else 3
        val particleType = if (isShiny) ParticleTypes.WAX_ON else ParticleTypes.GLOW

        for (i in 0 until particleCount) {
            val ox = (level.random.nextDouble() - 0.5) * (width + 0.3)
            val oy = level.random.nextDouble() * (height + 0.2)
            val oz = (level.random.nextDouble() - 0.5) * (width + 0.3)

            val vx = (level.random.nextDouble() - 0.5) * 0.05
            val vy = level.random.nextDouble() * 0.05 + 0.02
            val vz = (level.random.nextDouble() - 0.5) * 0.05

            level.addParticle(particleType, pos.x + ox, pos.y + oy, pos.z + oz, vx, vy, vz)
        }

        // Particules spéciales scintillantes supplémentaires pour les Shiny
        if (isShiny && level.random.nextBoolean()) {
            level.addParticle(
                ParticleTypes.END_ROD,
                pos.x + (level.random.nextDouble() - 0.5) * width,
                pos.y + height + 0.1,
                pos.z + (level.random.nextDouble() - 0.5) * width,
                0.0, 0.04, 0.0
            )
        }
    }

    /**
     * Réinitialise tous les états visuels (déconnexion ou changement de dimension).
     */
    fun clearVisuals() {
        activeGlowingUuids.clear()
    }
}
