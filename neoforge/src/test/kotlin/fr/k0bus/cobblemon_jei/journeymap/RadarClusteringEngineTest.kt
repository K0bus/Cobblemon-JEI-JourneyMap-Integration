package fr.k0bus.cobblemon_jei.journeymap

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class RadarClusteringEngineTest {

    private fun createDummySpecies(name: String): Species {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val sp = unsafe.allocateInstance(Species::class.java) as Species
        val nameField = Species::class.java.getDeclaredField("name").apply { isAccessible = true }
        nameField.set(sp, name)
        return sp
    }

    private fun createDetected(
        species: Species,
        isShiny: Boolean,
        pos: BlockPos
    ): PokemonRadarService.DetectedPokemon {
        return PokemonRadarService.DetectedPokemon(
            entityId = 1,
            uuid = UUID.randomUUID(),
            species = species,
            form = FormData(),
            isShiny = isShiny,
            level = 10,
            isWild = true,
            blockPos = pos
        )
    }

    @Test
    fun testCommonPokemonClusteredWhenClose() {
        val pidgey = createDummySpecies("pidgey")
        val a = createDetected(pidgey, isShiny = false, pos = BlockPos(100, 64, 100))
        val b = createDetected(pidgey, isShiny = false, pos = BlockPos(103, 64, 102))

        val clusters = RadarClusteringEngine.clusterEntities(listOf(a, b))
        assertEquals(1, clusters.size, "Deux Pidgey à 4 blocs doivent être groupés dans 1 cluster")
        assertEquals(2, clusters[0].count)
    }

    @Test
    fun testShinyPokemonNeverClustered() {
        val pidgey = createDummySpecies("pidgey")
        val normal = createDetected(pidgey, isShiny = false, pos = BlockPos(100, 64, 100))
        val shiny = createDetected(pidgey, isShiny = true, pos = BlockPos(101, 64, 100))

        val clusters = RadarClusteringEngine.clusterEntities(listOf(normal, shiny))
        assertEquals(2, clusters.size, "Le Shiny ne doit jamais être groupé avec un Pokémon ordinaire")
        assertTrue(clusters.any { it.representative.isShiny && it.count == 1 })
    }

    @Test
    fun testFarPokemonNotClustered() {
        val pidgey = createDummySpecies("pidgey")
        val a = createDetected(pidgey, isShiny = false, pos = BlockPos(100, 64, 100))
        val b = createDetected(pidgey, isShiny = false, pos = BlockPos(130, 64, 100))

        val clusters = RadarClusteringEngine.clusterEntities(listOf(a, b))
        assertEquals(2, clusters.size, "Deux Pidgey à 30 blocs ne doivent pas être groupés")
    }
}
