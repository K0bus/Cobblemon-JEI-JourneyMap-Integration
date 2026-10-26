package fr.k0bus.cobblemon_jei.domain

import fr.k0bus.cobblemon_jei.data.repository.PokemonDataRepository
import fr.k0bus.cobblemon_jei.data.repository.PokemonDataSnapshot
import fr.k0bus.cobblemon_jei.domain.model.*
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PokemonDataRepositoryTest {

    @Test
    fun `test PokemonDataSnapshot query methods and filtering`() {
        val pikachuId = ResourceLocation.fromNamespaceAndPath("cobblemon", "pikachu")
        val raichuId = ResourceLocation.fromNamespaceAndPath("cobblemon", "raichu")

        val pikachuSpawn = SpawnEntryInfo(
            speciesId = pikachuId,
            speciesName = "Pikachu",
            formName = "normal",
            isNaturalSpawn = true,
            bucket = "common",
            weight = 10.0f,
            condition = SpawnConditionInfo(
                dimensions = setOf(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")),
                biomes = setOf("minecraft:forest", "minecraft:plains")
            ),
            confidence = SpawnDataConfidence.EXACT_DATA
        )

        val pikachuEvolution = EvolutionInfo(
            sourceSpeciesId = pikachuId,
            sourceSpeciesName = "Pikachu",
            targetSpeciesId = raichuId,
            targetSpeciesName = "Raichu",
            evolutionVariant = "item_interact",
            requirementsSummary = "Pierre Foudre"
        )

        val pikachuInfo = PokemonSpeciesInfo(
            id = pikachuId,
            name = "Pikachu",
            displayName = Component.literal("Pikachu"),
            nationalDexNumber = 25,
            generation = 1,
            forms = listOf(PokemonFormInfo(formName = "normal", isStandard = true)),
            evolutions = listOf(pikachuEvolution),
            spawnEntries = listOf(pikachuSpawn)
        )

        val snapshot = PokemonDataSnapshot(
            speciesById = mapOf(pikachuId to pikachuInfo),
            speciesByName = mapOf("pikachu" to pikachuInfo),
            allSpawns = listOf(pikachuSpawn),
            allEvolutions = listOf(pikachuEvolution)
        )

        // Recherche par ID et nom
        assertEquals(pikachuInfo, snapshot.getSpecies(pikachuId))
        assertEquals(pikachuInfo, snapshot.getSpeciesByName("Pikachu"))
        assertEquals(pikachuInfo, snapshot.getSpeciesByName("pikachu"))
        assertNull(snapshot.getSpeciesByName("unknown_pokemon"))

        // Recherche d'apparitions
        val spawns = snapshot.getSpawnsForSpecies("Pikachu")
        assertEquals(1, spawns.size)
        assertEquals("common", spawns[0].bucket)
        assertEquals(SpawnDataConfidence.EXACT_DATA, spawns[0].confidence)

        // Recherche d'évolutions
        val evos = snapshot.getEvolutionsForSpecies("Pikachu")
        assertEquals(1, evos.size)
        assertEquals("Raichu", evos[0].targetSpeciesName)

        // Filtrage dimension & biome
        val forestSpawns = snapshot.getSpawnsForDimensionAndBiome("minecraft:overworld", "minecraft:forest")
        assertEquals(1, forestSpawns.size)

        val desertSpawns = snapshot.getSpawnsForDimensionAndBiome("minecraft:overworld", "minecraft:desert")
        assertEquals(0, desertSpawns.size)

        val netherSpawns = snapshot.getSpawnsForDimensionAndBiome("minecraft:the_nether", "minecraft:forest")
        assertEquals(0, netherSpawns.size)
    }

    @Test
    fun `test PokemonDataRepository loads non-empty snapshot`() {
        val snapshot = PokemonDataRepository.getSnapshot()
        assertNotNull(snapshot)
        assertTrue(snapshot.createdAtMillis > 0)
    }
}
