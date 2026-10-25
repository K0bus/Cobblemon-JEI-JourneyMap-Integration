package fr.k0bus.cobblemon_jei.journeymap

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RadarCompassServiceTest {

    @Test
    fun testRelativeDirectionArrow() {
        val playerPos = BlockPos(0, 64, 0)

        // Joueur regarde vers le Nord (Yaw = 180° dans Minecraft, Z négatif est le Nord)
        // Cible au Nord (0, 64, -50) -> doit afficher la flèche devant (⬆)
        val northTarget = BlockPos(0, 64, -50)
        val arrowNorth = RadarCompassService.getRelativeDirectionArrow(180f, northTarget, playerPos)
        assertEquals("⬆", arrowNorth)

        // Cible au Sud (0, 64, 50) -> doit afficher derrière (⬇)
        val southTarget = BlockPos(0, 64, 50)
        val arrowSouth = RadarCompassService.getRelativeDirectionArrow(180f, southTarget, playerPos)
        assertEquals("⬇", arrowSouth)

        // Cible à l'Est (50, 64, 0) -> doit afficher à droite (➡)
        val eastTarget = BlockPos(50, 64, 0)
        val arrowEast = RadarCompassService.getRelativeDirectionArrow(180f, eastTarget, playerPos)
        assertEquals("➡", arrowEast)

        // Cible à l'Ouest (-50, 64, 0) -> doit afficher à gauche (⬅)
        val westTarget = BlockPos(-50, 64, 0)
        val arrowWest = RadarCompassService.getRelativeDirectionArrow(180f, westTarget, playerPos)
        assertEquals("⬅", arrowWest)
    }
}
