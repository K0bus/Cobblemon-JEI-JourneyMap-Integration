package fr.k0bus.cobblemon_jei.journeymap

import fr.k0bus.cobblemon_jei.data.SpawnConditionEvaluator
import fr.k0bus.cobblemon_jei.data.SpawnEligibilityStatus
import fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager
import fr.k0bus.cobblemon_jei.pokedex.TrackingMode
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.ChunkPos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Service de boussole et de guidage pour le radar Cobblemon.
 * Calcule l'angle relatif (flèche) et la distance vers :
 * 1. L'entité vivante traquée la plus proche (si présente dans le monde).
 * 2. Si le joueur est dans la zone : signale qu'il y est et évalue si le spawn est possible maintenant.
 * 3. Si hors zone : le chunk éligible exploré le plus proche.
 */
object RadarCompassService {

    data class CompassGuidance(
        val targetName: String,
        val targetPos: BlockPos,
        val distanceBlocks: Int,
        val relativeY: Int,
        val arrowSymbol: String,
        val isPhysicallyPresent: Boolean,
        val isInZone: Boolean = false,
        val canSpawnNow: Boolean = false,
        val waitingReason: String? = null,
        val extraBadges: List<String> = emptyList()
    )

    /**
     * Calcule le symbole fléché (8 directions) par rapport à l'orientation du joueur.
     */
    fun getRelativeDirectionArrow(playerYaw: Float, targetPos: BlockPos, playerPos: BlockPos): String {
        val dx = targetPos.x - playerPos.x
        val dz = targetPos.z - playerPos.z

        // Angle en degrés dans le repère Minecraft (Z vers le sud, X vers l'est)
        val angleToTargetRad = atan2(dz.toDouble(), dx.toDouble())
        var targetAngleDeg = Math.toDegrees(angleToTargetRad) - 90.0

        // Différence par rapport au regard du joueur
        var diff = targetAngleDeg - playerYaw
        diff = Mth.wrapDegrees(diff)

        // Conversion en 8 secteurs de 45°
        return when {
            diff in -22.5..22.5 -> "⬆"
            diff in 22.5..67.5 -> "↗"
            diff in 67.5..112.5 -> "➡"
            diff in 112.5..157.5 -> "↘"
            diff in -67.5..-22.5 -> "↖"
            diff in -112.5..-67.5 -> "⬅"
            diff in -157.5..-112.5 -> "↙"
            else -> "⬇"
        }
    }

    /**
     * Résout l'état actuel de guidage pour la cible en cours.
     */
    fun getCurrentGuidance(): CompassGuidance? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        val level = mc.level
        val playerBlockPos = player.blockPosition()
        val playerYaw = player.yRot

        val overlaySpecies = CobblemonOverlayManager.activeSpecies
        val overlayForm = CobblemonOverlayManager.activeForm
        val trackingTarget = PokemonTrackingManager.targetSpecies
        val targetSpecies = trackingTarget ?: overlaySpecies

        // 1. Recherche d'une entité physique détectée par le radar
        val entities = PokemonRadarService.getTrackedEntities()
        if (entities.isNotEmpty()) {
            var closestDistSq = Double.MAX_VALUE
            var closestEntity: PokemonRadarService.DetectedPokemon? = null

            for (detected in entities.values) {
                val isTarget = when {
                    targetSpecies != null -> detected.species == targetSpecies
                    PokemonTrackingManager.currentMode == TrackingMode.ALL_UNCAUGHT -> true
                    PokemonTrackingManager.currentMode == TrackingMode.EVOLUTION_LINE -> PokemonTrackingManager.evolutionFamily.contains(detected.species)
                    PokemonTrackingManager.currentMode == TrackingMode.CUSTOM_LIST -> PokemonTrackingManager.customWatchlist.contains(detected.species)
                    else -> false
                }

                if (isTarget) {
                    val distSq = detected.blockPos.distSqr(playerBlockPos)
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq
                        closestEntity = detected
                    }
                }
            }

            if (closestEntity != null) {
                val dist = sqrt(closestDistSq).toInt()
                val arrow = getRelativeDirectionArrow(playerYaw, closestEntity.blockPos, playerBlockPos)
                val relY = closestEntity.blockPos.y - playerBlockPos.y
                val badges = mutableListOf<String>()
                if (closestEntity.isShiny) badges.add("§6★ Shiny")
                if (closestEntity.hasHiddenAbility) badges.add("§d[HA]")
                if (closestEntity.perfectIvsCount == 6) badges.add("§6[6x31]")
                else if (closestEntity.perfectIvsCount >= 3) badges.add("§e[${closestEntity.perfectIvsCount}x31]")
                if (closestEntity.scaleModifier <= 0.75f) badges.add("§b[Micro]")
                else if (closestEntity.scaleModifier >= 1.25f) badges.add("§6[Colossal]")

                return CompassGuidance(
                    targetName = closestEntity.species.translatedName.string,
                    targetPos = closestEntity.blockPos,
                    distanceBlocks = dist,
                    relativeY = relY,
                    arrowSymbol = arrow,
                    isPhysicallyPresent = true,
                    isInZone = true,
                    canSpawnNow = true,
                    extraBadges = badges
                )
            }
        }

        // 2. Si aucune entité n'est encore apparue mais qu'une cible est définie
        if (targetSpecies != null && level != null) {
            val eval = SpawnConditionEvaluator.evaluate(player, level, targetSpecies, overlayForm)
            val playerChunk = ChunkPos(playerBlockPos)
            val isEligibleChunk = SpawnOverlayManager.activeEligibleChunks.contains(playerChunk)
            val isInZone = isEligibleChunk ||
                    eval.status == SpawnEligibilityStatus.AVAILABLE_NOW ||
                    eval.status == SpawnEligibilityStatus.ENVIRONMENT_MATCH

            // Si le joueur se trouve déjà dans la zone
            if (isInZone) {
                val canSpawn = (eval.status == SpawnEligibilityStatus.AVAILABLE_NOW)
                val reason = eval.blockingReasons.firstOrNull()
                return CompassGuidance(
                    targetName = targetSpecies.translatedName.string,
                    targetPos = playerBlockPos,
                    distanceBlocks = 0,
                    relativeY = 0,
                    arrowSymbol = if (canSpawn) "✔" else "⏳",
                    isPhysicallyPresent = false,
                    isInZone = true,
                    canSpawnNow = canSpawn,
                    waitingReason = reason
                )
            }

            // Si hors zone : guidage vers le chunk éligible le plus proche
            val closestEligibleChunk = SpawnOverlayManager.findClosestEligibleChunk(playerBlockPos)
            if (closestEligibleChunk != null) {
                val targetCenter = BlockPos(
                    (closestEligibleChunk.x shl 4) + 8,
                    playerBlockPos.y,
                    (closestEligibleChunk.z shl 4) + 8
                )
                val dist = sqrt(targetCenter.distSqr(playerBlockPos)).toInt()
                val arrow = getRelativeDirectionArrow(playerYaw, targetCenter, playerBlockPos)
                return CompassGuidance(
                    targetName = targetSpecies.translatedName.string,
                    targetPos = targetCenter,
                    distanceBlocks = dist,
                    relativeY = 0,
                    arrowSymbol = arrow,
                    isPhysicallyPresent = false,
                    isInZone = false,
                    canSpawnNow = false
                )
            }
        }

        return null
    }
}
