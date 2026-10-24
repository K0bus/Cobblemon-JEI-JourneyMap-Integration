package fr.k0bus.cobblemon_jei.journeymap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PokemonRadarAltitudeTest {

    @Test
    fun testVerticalDirection() {
        // Dans la tolérance (+/- 3 blocs) -> même niveau
        assertEquals(PokemonRadarService.VerticalDirection.SAME_LEVEL, PokemonRadarService.getVerticalDirection(0))
        assertEquals(PokemonRadarService.VerticalDirection.SAME_LEVEL, PokemonRadarService.getVerticalDirection(1))
        assertEquals(PokemonRadarService.VerticalDirection.SAME_LEVEL, PokemonRadarService.getVerticalDirection(-1))
        assertEquals(PokemonRadarService.VerticalDirection.SAME_LEVEL, PokemonRadarService.getVerticalDirection(3))
        assertEquals(PokemonRadarService.VerticalDirection.SAME_LEVEL, PokemonRadarService.getVerticalDirection(-3))

        // Au-dessus (+4 blocs et plus)
        assertEquals(PokemonRadarService.VerticalDirection.ABOVE, PokemonRadarService.getVerticalDirection(4))
        assertEquals(PokemonRadarService.VerticalDirection.ABOVE, PokemonRadarService.getVerticalDirection(20))
        assertEquals(PokemonRadarService.VerticalDirection.ABOVE, PokemonRadarService.getVerticalDirection(60))

        // En-dessous (-4 blocs et moins)
        assertEquals(PokemonRadarService.VerticalDirection.BELOW, PokemonRadarService.getVerticalDirection(-4))
        assertEquals(PokemonRadarService.VerticalDirection.BELOW, PokemonRadarService.getVerticalDirection(-25))
        assertEquals(PokemonRadarService.VerticalDirection.BELOW, PokemonRadarService.getVerticalDirection(-60))
    }

    @Test
    fun testAltitudeTintAndOpacityGraduation() {
        // Niveau identique -> pas d'assombrissement, opacité maximale
        val (colorZero, opZero) = PokemonRadarService.computeAltitudeTintAndOpacity(0)
        assertEquals(0xFFFFFF, colorZero)
        assertEquals(1.0f, opZero)

        val (colorTol, opTol) = PokemonRadarService.computeAltitudeTintAndOpacity(3)
        assertEquals(0xFFFFFF, colorTol)
        assertEquals(1.0f, opTol)

        // À distance intermédiaire (ex: 20 blocs)
        val (colorMid, opMid) = PokemonRadarService.computeAltitudeTintAndOpacity(20)
        assertTrue(colorMid < 0xFFFFFF, "La couleur devrait être grisée à +20 blocs")
        assertTrue(opMid < 1.0f, "L'opacité devrait être réduite à +20 blocs")

        // À distance maximale (64 blocs)
        val (colorMax, opMax) = PokemonRadarService.computeAltitudeTintAndOpacity(64)
        val expectedGray = 85
        val expectedMaxColor = (expectedGray shl 16) or (expectedGray shl 8) or expectedGray
        assertEquals(expectedMaxColor, colorMax)
        assertEquals(0.55f, opMax)

        // Symétrie vers le bas
        val (colorNeg, opNeg) = PokemonRadarService.computeAltitudeTintAndOpacity(-64)
        assertEquals(expectedMaxColor, colorNeg)
        assertEquals(0.55f, opNeg)

        // Plus l'écart grandit, plus la couleur diminue (grisée) et l'opacité diminue
        val (color10, op10) = PokemonRadarService.computeAltitudeTintAndOpacity(10)
        val (color40, op40) = PokemonRadarService.computeAltitudeTintAndOpacity(40)
        assertTrue(color10 > color40, "La teinte à 10 blocs doit être plus lumineuse qu'à 40 blocs")
        assertTrue(op10 > op40, "L'opacité à 10 blocs doit être plus élevée qu'à 40 blocs")
    }

    @Test
    fun testRadarMapImageSelection() {
        // Normal
        val normalSame = PokemonRadarService.createRadarMapImage(isShiny = false, deltaY = 0)
        assertEquals(PokemonRadarService.ICON_NORMAL_LOC, normalSame.imageLocation)
        assertEquals(0xFFFFFF, normalSame.color and 0xFFFFFF)
        assertEquals(1.0f, normalSame.opacity)

        val normalUp = PokemonRadarService.createRadarMapImage(isShiny = false, deltaY = 15)
        assertEquals(PokemonRadarService.ICON_NORMAL_UP_LOC, normalUp.imageLocation)
        assertTrue((normalUp.color and 0xFFFFFF) < 0xFFFFFF)

        val normalDown = PokemonRadarService.createRadarMapImage(isShiny = false, deltaY = -15)
        assertEquals(PokemonRadarService.ICON_NORMAL_DOWN_LOC, normalDown.imageLocation)
        assertTrue((normalDown.color and 0xFFFFFF) < 0xFFFFFF)

        // Shiny
        val shinySame = PokemonRadarService.createRadarMapImage(isShiny = true, deltaY = 0)
        assertEquals(PokemonRadarService.ICON_SHINY_LOC, shinySame.imageLocation)

        val shinyUp = PokemonRadarService.createRadarMapImage(isShiny = true, deltaY = 25)
        assertEquals(PokemonRadarService.ICON_SHINY_UP_LOC, shinyUp.imageLocation)

        val shinyDown = PokemonRadarService.createRadarMapImage(isShiny = true, deltaY = -25)
        assertEquals(PokemonRadarService.ICON_SHINY_DOWN_LOC, shinyDown.imageLocation)
    }
}
