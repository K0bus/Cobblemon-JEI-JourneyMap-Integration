package fr.k0bus.cobblemon_jei.pokedex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PokedexFilterTest {

    @Test
    fun `test PokedexFilterState initial state is not active`() {
        val state = PokedexFilterState()
        assertFalse(state.isActive())
        assertEquals(0, state.activeCount())
    }

    @Test
    fun `test PokedexFilterState tracks active filters and resets correctly`() {
        val state = PokedexFilterState()

        state.selectedTypes.add("electric")
        state.selectedRarity = SpawnRarityFilter.RARE
        state.selectedBiome = CobblemonBiomeFilter.FOREST
        state.selectedGen = GenerationFilter.GEN_1

        assertTrue(state.isActive())
        assertEquals(4, state.activeCount())

        state.reset()
        assertFalse(state.isActive())
        assertEquals(0, state.activeCount())
        assertEquals(SpawnRarityFilter.ALL, state.selectedRarity)
        assertEquals(CobblemonBiomeFilter.ALL, state.selectedBiome)
        assertEquals(GenerationFilter.ALL, state.selectedGen)
        assertEquals(PokedexSortOrder.DEX_ASC, state.selectedSort)
        assertTrue(state.selectedTypes.isEmpty())
    }

    @Test
    fun `test generation ranges are valid and continuous`() {
        assertEquals(1..151, GenerationFilter.GEN_1.range)
        assertEquals(152..251, GenerationFilter.GEN_2.range)
        assertEquals(252..386, GenerationFilter.GEN_3.range)
        assertEquals(387..493, GenerationFilter.GEN_4.range)
        assertEquals(494..649, GenerationFilter.GEN_5.range)
        assertEquals(650..721, GenerationFilter.GEN_6.range)
        assertEquals(722..809, GenerationFilter.GEN_7.range)
        assertEquals(810..905, GenerationFilter.GEN_8.range)
        assertEquals(906..2000, GenerationFilter.GEN_9.range)
    }

    @Test
    fun `test biome keywords match expected tags`() {
        val forestMeta = PokedexFilterManager.SpeciesSpawnMetadata(
            rarities = emptySet(),
            biomes = setOf("#cobblemon:is_forest", "minecraft:birch_forest"),
            dimensions = setOf("minecraft:overworld"),
            hasWildSpawns = true
        )

        assertTrue(PokedexFilterManager.matchesBiome(forestMeta, CobblemonBiomeFilter.ALL))
        assertTrue(PokedexFilterManager.matchesBiome(forestMeta, CobblemonBiomeFilter.FOREST))
        assertFalse(PokedexFilterManager.matchesBiome(forestMeta, CobblemonBiomeFilter.DESERT))
    }

    @Test
    fun `test all elemental types are registered`() {
        assertEquals(18, PokedexFilterManager.ALL_TYPES.size)
        val typeIds = PokedexFilterManager.ALL_TYPES.map { it.id }.toSet()
        assertTrue(typeIds.contains("fire"))
        assertTrue(typeIds.contains("water"))
        assertTrue(typeIds.contains("grass"))
        assertTrue(typeIds.contains("electric"))
        assertTrue(typeIds.contains("fairy"))
        assertTrue(typeIds.contains("dragon"))
    }

    @Test
    fun `test PokedexSortOrder options`() {
        val orders = PokedexSortOrder.values()
        assertEquals(6, orders.size)
        assertTrue(orders.contains(PokedexSortOrder.DEX_ASC))
        assertTrue(orders.contains(PokedexSortOrder.DEX_DESC))
        assertTrue(orders.contains(PokedexSortOrder.NAME_ASC))
        assertTrue(orders.contains(PokedexSortOrder.NAME_DESC))
        assertTrue(orders.contains(PokedexSortOrder.BST_DESC))
        assertTrue(orders.contains(PokedexSortOrder.BST_ASC))
    }

    @Test
    fun `test SpawnRarityFilter options`() {
        val filters = SpawnRarityFilter.values()
        assertEquals(6, filters.size)
        assertTrue(filters.contains(SpawnRarityFilter.ALL))
        assertTrue(filters.contains(SpawnRarityFilter.COMMON))
        assertTrue(filters.contains(SpawnRarityFilter.UNCOMMON))
        assertTrue(filters.contains(SpawnRarityFilter.RARE))
        assertTrue(filters.contains(SpawnRarityFilter.ULTRA_RARE))
        assertTrue(filters.contains(SpawnRarityFilter.NO_WILD_SPAWN))
    }

    @Test
    fun `test UTF8 biome search normalization with and without accents`() {
        fun normalize(text: String): String {
            val decomposed = java.text.Normalizer.normalize(text.trim().lowercase(), java.text.Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{M}"), "").replace("œ", "oe").replace("æ", "ae")
        }

        fun searchBiomes(query: String): List<CobblemonBiomeFilter> {
            val norm = normalize(query)
            if (norm.isEmpty()) return CobblemonBiomeFilter.values().toList()
            return CobblemonBiomeFilter.values().filter { biome ->
                normalize(biome.displayName).contains(norm) ||
                biome.id.lowercase().contains(norm) ||
                biome.tagKeywords.any { normalize(it).contains(norm) }
            }
        }

        // Accents insensitive
        val matchesForet = searchBiomes("foret")
        assertTrue(matchesForet.contains(CobblemonBiomeFilter.FOREST))

        val matchesForetAccented = searchBiomes("forêt")
        assertTrue(matchesForetAccented.contains(CobblemonBiomeFilter.FOREST))

        val matchesSpeleo = searchBiomes("speleo")
        assertTrue(matchesSpeleo.contains(CobblemonBiomeFilter.DRIPSTONE))

        val matchesSpeleoAccented = searchBiomes("spéléothèmes")
        assertTrue(matchesSpeleoAccented.contains(CobblemonBiomeFilter.DRIPSTONE))

        val matchesOcean = searchBiomes("ocean")
        assertTrue(matchesOcean.contains(CobblemonBiomeFilter.OCEAN))

        val matchesOceanAccented = searchBiomes("océan")
        assertTrue(matchesOceanAccented.contains(CobblemonBiomeFilter.OCEAN))

        // Tag matching
        val matchesBirch = searchBiomes("birch")
        assertTrue(matchesBirch.contains(CobblemonBiomeFilter.FOREST))
    }

    @Test
    fun `test FilterCount computations on empty list`() {
        val biomeCounts = PokedexFilterManager.computeBiomeCounts(emptyList())
        assertEquals(CobblemonBiomeFilter.values().size, biomeCounts.size)
        for (b in CobblemonBiomeFilter.values()) {
            assertEquals(0, biomeCounts[b]?.total)
            assertEquals(0, biomeCounts[b]?.missing)
        }

        val rarityCounts = PokedexFilterManager.computeRarityCounts(emptyList())
        assertEquals(SpawnRarityFilter.values().size, rarityCounts.size)
        for (r in SpawnRarityFilter.values()) {
            assertEquals(0, rarityCounts[r]?.total)
            assertEquals(0, rarityCounts[r]?.missing)
        }

        val genCounts = PokedexFilterManager.computeGenCounts(emptyList())
        assertEquals(GenerationFilter.values().size, genCounts.size)
        for (g in GenerationFilter.values()) {
            assertEquals(0, genCounts[g]?.total)
            assertEquals(0, genCounts[g]?.missing)
        }
    }
}
