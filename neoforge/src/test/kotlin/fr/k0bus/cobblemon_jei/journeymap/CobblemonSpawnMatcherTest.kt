package fr.k0bus.cobblemon_jei.journeymap

import fr.k0bus.cobblemon_jei.recipe.PokemonSpawnRecipe
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.biome.Biome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.Optional

class CobblemonSpawnMatcherTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            try {
                net.minecraft.SharedConstants.tryDetectVersion()
                net.minecraft.server.Bootstrap.bootStrap()
            } catch (_: Throwable) {}
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createMockHolder(id: String, tags: Set<String> = emptySet()): Holder<Biome> {
        val resKey = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(id))
        return Proxy.newProxyInstance(
            Holder::class.java.classLoader,
            arrayOf(Holder::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "unwrapKey" -> Optional.of(resKey)
                "tags" -> tags.stream().map { TagKey.create(Registries.BIOME, ResourceLocation.parse(it.removePrefix("#"))) }
                "hashCode" -> resKey.hashCode()
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "MockHolder($id)"
                else -> null
            }
        } as Holder<Biome>
    }

    private fun createDummySpeciesAndForm(name: String): Pair<com.cobblemon.mod.common.pokemon.Species, com.cobblemon.mod.common.pokemon.FormData> {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val sp = unsafe.allocateInstance(com.cobblemon.mod.common.pokemon.Species::class.java) as com.cobblemon.mod.common.pokemon.Species
        sp.name = name
        val form = unsafe.allocateInstance(com.cobblemon.mod.common.pokemon.FormData::class.java) as com.cobblemon.mod.common.pokemon.FormData
        return Pair(sp, form)
    }

    @Test
    fun `test matches direct biome id`() {
        val (pikachu, form) = createDummySpeciesAndForm("pikachu")
        val recipe = PokemonSpawnRecipe(
            species = pikachu,
            form = form,
            bucket = "common",
            biomes = setOf("minecraft:plains")
        )

        val plainsHolder = createMockHolder("minecraft:plains")
        val desertHolder = createMockHolder("minecraft:desert")

        assertTrue(CobblemonSpawnMatcher.matchesRecipe(plainsHolder, recipe))
        assertFalse(CobblemonSpawnMatcher.matchesRecipe(desertHolder, recipe))
    }

    @Test
    fun `test matches biome tag`() {
        val (pikachu, form) = createDummySpeciesAndForm("pikachu")
        val recipe = PokemonSpawnRecipe(
            species = pikachu,
            form = form,
            bucket = "uncommon",
            biomes = setOf("#minecraft:is_forest")
        )

        val forestHolder = createMockHolder("minecraft:birch_forest", setOf("#minecraft:is_forest"))
        val plainsHolder = createMockHolder("minecraft:plains")

        assertTrue(CobblemonSpawnMatcher.matchesRecipe(forestHolder, recipe))
        assertFalse(CobblemonSpawnMatcher.matchesRecipe(plainsHolder, recipe))
    }

    @Test
    fun `test anti-biome takes precedence over allowed biomes`() {
        val (pikachu, form) = createDummySpeciesAndForm("pikachu")
        val recipe = PokemonSpawnRecipe(
            species = pikachu,
            form = form,
            bucket = "rare",
            biomes = setOf("#minecraft:is_forest"),
            antiBiomes = setOf("minecraft:dark_forest")
        )

        val birchForest = createMockHolder("minecraft:birch_forest", setOf("#minecraft:is_forest"))
        val darkForest = createMockHolder("minecraft:dark_forest", setOf("#minecraft:is_forest"))

        assertTrue(CobblemonSpawnMatcher.matchesRecipe(birchForest, recipe))
        assertFalse(CobblemonSpawnMatcher.matchesRecipe(darkForest, recipe), "L'anti-biome doit exclure la dark forest")
    }

    @Test
    fun `test group chunks by rarity`() {
        val (pikachu, form) = createDummySpeciesAndForm("pikachu")
        val commonRecipe = PokemonSpawnRecipe(
            species = pikachu,
            form = form,
            bucket = "common",
            biomes = setOf("minecraft:plains")
        )
        val rareRecipe = PokemonSpawnRecipe(
            species = pikachu,
            form = form,
            bucket = "rare",
            biomes = setOf("minecraft:badlands")
        )

        val chunkBiomes = mapOf(
            ChunkPos(0, 0) to createMockHolder("minecraft:plains"),
            ChunkPos(1, 0) to createMockHolder("minecraft:plains"),
            ChunkPos(5, 5) to createMockHolder("minecraft:badlands"),
            ChunkPos(9, 9) to createMockHolder("minecraft:nether_wastes") // Incompatible
        )

        val grouped = CobblemonSpawnMatcher.groupChunksByRarity(chunkBiomes, listOf(commonRecipe, rareRecipe))

        assertEquals(2, grouped.size)
        assertEquals(setOf(ChunkPos(0, 0), ChunkPos(1, 0)), grouped[SpawnRarity.COMMON])
        assertEquals(setOf(ChunkPos(5, 5)), grouped[SpawnRarity.RARE])
        assertNull(grouped[SpawnRarity.UNCOMMON])
        assertNull(grouped[SpawnRarity.ULTRA_RARE])
    }

    @Test
    fun `test dimension inference for Nether, Aether, End and Overworld`() {
        val slugmaBiomes = setOf("#cobblemon:nether/is_basalt", "#cobblemon:nether/is_wasteland")
        val slugmaDims = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), slugmaBiomes)
        assertEquals(setOf("minecraft:the_nether"), slugmaDims)

        val rioluAetherBiomes = setOf("#aether:is_aether")
        val rioluDims = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), rioluAetherBiomes)
        assertEquals(setOf("aether:the_aether"), rioluDims)

        val rioluMountainBiomes = setOf("#cobblemon:is_mountain")
        val mountainDims = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), rioluMountainBiomes)
        assertEquals(setOf("minecraft:overworld"), mountainDims)

        val endBiomes = setOf("#minecraft:is_end")
        val endDims = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), endBiomes)
        assertEquals(setOf("minecraft:the_end"), endDims)
    }

    @Test
    fun `test nether and aether recipe matching`() {
        val (slugma, form) = createDummySpeciesAndForm("slugma")
        val netherRecipe = PokemonSpawnRecipe(
            species = slugma,
            form = form,
            bucket = "common",
            dimensions = setOf("minecraft:the_nether"),
            biomes = setOf("#cobblemon:nether/is_basalt")
        )

        val basaltDeltas = createMockHolder("minecraft:basalt_deltas")
        val plains = createMockHolder("minecraft:plains")

        assertTrue(CobblemonSpawnMatcher.matchesRecipe(basaltDeltas, netherRecipe))
        assertFalse(CobblemonSpawnMatcher.matchesRecipe(plains, netherRecipe))

        val (riolu, rioluForm) = createDummySpeciesAndForm("riolu")
        val aetherRecipe = PokemonSpawnRecipe(
            species = riolu,
            form = rioluForm,
            bucket = "rare",
            dimensions = setOf("aether:the_aether"),
            biomes = setOf("#aether:is_aether")
        )

        val skyrootForest = createMockHolder("aether:skyroot_forest")
        assertTrue(CobblemonSpawnMatcher.matchesRecipe(skyrootForest, aetherRecipe))
        assertFalse(CobblemonSpawnMatcher.matchesRecipe(plains, aetherRecipe))
    }

    @Test
    fun `test scolipede lush biomes infer overworld and not the end`() {
        val lushBiomes = setOf("#cobblemon:is_lush")
        val lushDims = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), lushBiomes)
        assertEquals(setOf("minecraft:overworld"), lushDims)

        val (scolipede, form) = createDummySpeciesAndForm("scolipede")
        val overworldRecipe = PokemonSpawnRecipe(
            species = scolipede,
            form = form,
            bucket = "common",
            dimensions = setOf("minecraft:overworld"),
            biomes = setOf("#cobblemon:is_lush")
        )
        val netherRecipe = PokemonSpawnRecipe(
            species = scolipede,
            form = form,
            bucket = "common",
            dimensions = setOf("minecraft:the_nether"),
            biomes = setOf("#cobblemon:nether/is_forest")
        )

        assertTrue(CobblemonSpawnMatcher.isRecipeInDimension(overworldRecipe, net.minecraft.world.level.Level.OVERWORLD))
        assertFalse(CobblemonSpawnMatcher.isRecipeInDimension(overworldRecipe, net.minecraft.world.level.Level.END))
        assertFalse(CobblemonSpawnMatcher.isRecipeInDimension(overworldRecipe, net.minecraft.world.level.Level.NETHER))

        assertTrue(CobblemonSpawnMatcher.isRecipeInDimension(netherRecipe, net.minecraft.world.level.Level.NETHER))
        assertFalse(CobblemonSpawnMatcher.isRecipeInDimension(netherRecipe, net.minecraft.world.level.Level.END))
        assertFalse(CobblemonSpawnMatcher.isRecipeInDimension(netherRecipe, net.minecraft.world.level.Level.OVERWORLD))

        // Empty dimensions should default strictly to Overworld, never End
        val emptyDimRecipe = PokemonSpawnRecipe(
            species = scolipede,
            form = form,
            bucket = "common",
            dimensions = emptySet()
        )
        assertTrue(CobblemonSpawnMatcher.isRecipeInDimension(emptyDimRecipe, net.minecraft.world.level.Level.OVERWORLD))
        assertFalse(CobblemonSpawnMatcher.isRecipeInDimension(emptyDimRecipe, net.minecraft.world.level.Level.END))
    }

    @Test
    fun `test custom nether and end mod biomes are correctly partitioned`() {
        // Biomes Nether customisés
        val bopChasm = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), setOf("biomesoplenty:crystalline_chasm"))
        assertEquals(setOf("minecraft:the_nether"), bopChasm)

        val bygDesert = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), setOf("byg:warped_desert"))
        assertEquals(setOf("minecraft:the_nether"), bygDesert)

        val incendium = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), setOf("incendium:ash_barrens"))
        assertEquals(setOf("minecraft:the_nether"), incendium)

        // Biomes End customisés
        val nullscape = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), setOf("nullscape:crystal_peaks"))
        assertEquals(setOf("minecraft:the_end"), nullscape)

        val betterEnd = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), setOf("betterend:foggy_mushroomland"))
        assertEquals(setOf("minecraft:the_end"), betterEnd)

        // Biomes Overworld provenant de mods
        val terralith = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), setOf("terralith:emerald_peaks"))
        assertEquals(setOf("minecraft:overworld"), terralith)

        val bopGlade = fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader.inferDimensions(emptySet(), setOf("biomesoplenty:aspen_glade"))
        assertEquals(setOf("minecraft:overworld"), bopGlade)

        // Test de correspondance matchesRecipe pour l'End avec un biome Nullscape
        val (beldum, beldumForm) = createDummySpeciesAndForm("beldum")
        val endRecipe = PokemonSpawnRecipe(
            species = beldum,
            form = beldumForm,
            bucket = "uncommon",
            dimensions = setOf("minecraft:the_end"),
            biomes = setOf("#cobblemon:is_end")
        )
        val nullscapeHolder = createMockHolder("nullscape:crystal_peaks")
        assertTrue(CobblemonSpawnMatcher.matchesRecipe(nullscapeHolder, endRecipe))
    }
}
