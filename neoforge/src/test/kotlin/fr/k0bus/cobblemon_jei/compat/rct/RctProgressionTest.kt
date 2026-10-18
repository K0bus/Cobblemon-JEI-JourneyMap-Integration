package fr.k0bus.cobblemon_jei.compat.rct

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RctProgressionTest {

    @BeforeEach
    fun setup() {
        RctDataLoader.reload()
        RctProgressionService.refresh()
    }

    @Test
    fun testDataLoaderLoadsSeries() {
        val allSeries = RctDataLoader.getAllSeries()
        assertFalse(allSeries.isEmpty(), "Au moins une série de progression doit être chargée")

        val radicalRed = RctDataLoader.getSeries("radicalred")
        assertNotNull(radicalRed, "La série Radical Red doit être présente")
        assertEquals(15, radicalRed!!.initialLevelCap, "Le cap initial de Radical Red doit être 15")
    }

    @Test
    fun testTrainersLoadedForRadicalRed() {
        val trainers = RctDataLoader.getTrainersForSeries("radicalred")
        assertFalse(trainers.isEmpty(), "Radical Red doit contenir des dresseurs")

        val brock = trainers.firstOrNull { it.id == "leader_brock_019e" }
        assertNotNull(brock, "Pierre (Brock) doit être présent dans Radical Red")
        assertEquals(15, brock!!.computedLevelCap, "Le cap de Brock doit être 15")
        assertTrue(brock.requiredDefeats.isEmpty(), "Brock est le premier champion sans prérequis")
        assertFalse(brock.team.isEmpty(), "Brock doit posséder une équipe de Pokémon")
    }

    @Test
    fun testProgressionGraphLayoutAndStatus() {
        // Reset la progression sur Radical Red
        RctProgressionService.resetProgress("radicalred")

        val graph = RctProgressionService.buildTreeGraph("radicalred")
        assertNotNull(graph)
        assertTrue(graph.nodes.isNotEmpty(), "Le graphe doit contenir des nœuds")

        val brockNode = graph.nodes.firstOrNull { it.trainer.id == "leader_brock_019e" }
        assertNotNull(brockNode)
        assertEquals(RctProgressionStatus.AVAILABLE, brockNode!!.status, "Brock doit être disponible au début")
        assertEquals(0, brockNode.rank, "Brock doit être au rang 0")

        val mistyNode = graph.nodes.firstOrNull { it.trainer.id == "leader_misty_019f" }
        assertNotNull(mistyNode)
        assertEquals(RctProgressionStatus.LOCKED, mistyNode!!.status, "Ondine doit être verrouillée tant que Brock n'est pas battu")
        assertTrue(mistyNode.rank > brockNode.rank, "Ondine doit être à un rang supérieur à Brock")

        // Le cap initial du joueur doit être 15
        assertEquals(15, graph.playerLevelCap)

        // Simule la victoire contre Brock
        RctProgressionService.setTrainerDefeatedForTesting("leader_brock_019e", true)
        val graphAfterBrock = RctProgressionService.buildTreeGraph("radicalred")

        val brockAfter = graphAfterBrock.nodes.first { it.trainer.id == "leader_brock_019e" }
        assertEquals(RctProgressionStatus.DEFEATED, brockAfter.status, "Brock doit être marqué comme vaincu")

        val mistyAfter = graphAfterBrock.nodes.first { it.trainer.id == "leader_misty_019f" }
        assertEquals(RctProgressionStatus.AVAILABLE, mistyAfter.status, "Ondine doit maintenant être débloquée")

        // Cap du joueur après Brock (doit monter au niveau de Brock ou suivant)
        assertTrue(graphAfterBrock.playerLevelCap >= 15)
        assertEquals(1, graphAfterBrock.defeatedCount)
    }

    @Test
    fun testEdgeConnectivity() {
        val graph = RctProgressionService.buildTreeGraph("radicalred")
        val brockToMistyEdge = graph.edges.firstOrNull {
            it.fromNode.trainer.id == "leader_brock_019e" && it.toNode.trainer.id == "leader_misty_019f"
        }
        assertNotNull(brockToMistyEdge, "Une arête orientée doit exister entre Brock et Ondine")
    }

    @Test
    fun testMultipleSeriesSwitching() {
        val unboundGraph = RctProgressionService.buildTreeGraph("unbound")
        assertNotNull(unboundGraph)
        assertEquals("unbound", unboundGraph.series.id)
        assertTrue(unboundGraph.nodes.any { it.trainer.id == "unbound_mirskle" })

        val bdspGraph = RctProgressionService.buildTreeGraph("bdsp")
        assertNotNull(bdspGraph)
        assertEquals("bdsp", bdspGraph.series.id)
        assertTrue(bdspGraph.nodes.any { it.trainer.id == "bdsp_roark" })
    }
}
