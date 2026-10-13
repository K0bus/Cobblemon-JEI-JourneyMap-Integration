package com.cobblemon.jei.journeymap

import net.minecraft.world.level.ChunkPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChunkPolygonMergerTest {

    @Test
    fun `test empty chunks returns empty list`() {
        val result = ChunkPolygonMerger.mergeChunks(emptySet(), 64)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test single chunk merges into one polygon with 4 points`() {
        val chunk = ChunkPos(0, 0)
        val result = ChunkPolygonMerger.mergeChunks(setOf(chunk), 64)

        assertEquals(1, result.size)
        val poly = result.first()
        val points = poly.hull.points
        assertEquals(4, points.size)

        // Doit couvrir de x=0..16 et z=0..16
        val xCoords = points.map { it.x }.toSet()
        val zCoords = points.map { it.z }.toSet()
        assertEquals(setOf(0, 16), xCoords)
        assertEquals(setOf(0, 16), zCoords)
        assertTrue(poly.holes == null || poly.holes!!.isEmpty())
    }

    @Test
    fun `test 2x2 contiguous chunks simplify collinear edges into 4 corners`() {
        // 4 chunks contigus formant un carré 32x32 blocs
        val chunks = setOf(
            ChunkPos(0, 0),
            ChunkPos(1, 0),
            ChunkPos(0, 1),
            ChunkPos(1, 1)
        )

        val result = ChunkPolygonMerger.mergeChunks(chunks, 64)
        assertEquals(1, result.size)

        val poly = result.first()
        val points = poly.hull.points
        // Grâce à la simplification colinéaire, il ne doit rester que les 4 coins extérieurs
        assertEquals(4, points.size, "Les arêtes intermédiaires partagées doivent être simplifiées")

        val xCoords = points.map { it.x }.toSet()
        val zCoords = points.map { it.z }.toSet()
        assertEquals(setOf(0, 32), xCoords)
        assertEquals(setOf(0, 32), zCoords)
    }

    @Test
    fun `test donut shape creates outer hull with hole`() {
        // Carré 3x3 avec le chunk central (1, 1) manquant
        val donutChunks = mutableSetOf<ChunkPos>()
        for (x in 0..2) {
            for (z in 0..2) {
                if (x != 1 || z != 1) {
                    donutChunks.add(ChunkPos(x, z))
                }
            }
        }

        val result = ChunkPolygonMerger.mergeChunks(donutChunks, 64)
        assertEquals(1, result.size)

        val poly = result.first()
        // Hull extérieur
        assertEquals(4, poly.hull.points.size)
        // Trou intérieur
        assertNotNull(poly.holes)
        assertEquals(1, poly.holes!!.size)
        assertEquals(4, poly.holes!!.first().points.size)
    }

    @Test
    fun `test two disjoint islands create two separate polygons`() {
        val island1 = setOf(ChunkPos(0, 0), ChunkPos(1, 0))
        val island2 = setOf(ChunkPos(10, 10), ChunkPos(10, 11))
        val allChunks = island1 + island2

        val result = ChunkPolygonMerger.mergeChunks(allChunks, 64)
        assertEquals(2, result.size)
    }
}
