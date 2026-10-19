package fr.k0bus.cobblemon_jei.recipe

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class PokemonSpawnRecipeTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            try {
                net.minecraft.SharedConstants.tryDetectVersion()
                net.minecraft.server.Bootstrap.bootStrap()
            } catch (_: Throwable) {}
        }

        fun createDummySpeciesAndForm(name: String): Pair<Species, FormData> {
            val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val unsafe = unsafeField.get(null) as sun.misc.Unsafe
            val sp = unsafe.allocateInstance(Species::class.java) as Species
            sp.name = name
            sp.resourceIdentifier = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", name.lowercase())
            val form = unsafe.allocateInstance(FormData::class.java) as FormData
            form.species = sp
            val nameField = FormData::class.java.getDeclaredField("name").apply { isAccessible = true }
            nameField.set(form, "normal")
            return Pair(sp, form)
        }
    }

    @Test
    fun `test PokemonSpawnRecipe default and explicit values`() {
        val (sp, form) = createDummySpeciesAndForm("Rattata")
        val recipe = PokemonSpawnRecipe(
            species = sp,
            form = form,
            bucket = "common",
            weight = 10.0f,
            levelMin = 5,
            levelMax = 15,
            context = "grounded",
            biomes = setOf("minecraft:plains"),
            antiBiomes = emptySet(),
            dimensions = setOf("minecraft:overworld"),
            timeRange = "day",
            weather = "clear",
            canSeeSky = true,
            minSkyLight = 10,
            maxSkyLight = 15,
            minY = -10,
            maxY = 80,
            neededBaseBlocks = setOf("minecraft:grass_block"),
            neededNearbyBlocks = setOf("minecraft:water"),
            minLight = 0,
            maxLight = 7,
            moonPhase = 0,
            isSlimeChunk = true,
            minLureLevel = 2,
            maxLureLevel = 5,
            rodType = "super_rod",
            bait = "minecraft:glowstone_dust",
            structures = setOf("minecraft:village"),
            antiStructures = setOf("minecraft:fortress")
        )

        assertEquals("Rattata", recipe.species.name)
        assertEquals(true, recipe.canSeeSky)
        assertEquals(10, recipe.minSkyLight)
        assertEquals(15, recipe.maxSkyLight)
        assertEquals(-10, recipe.minY)
        assertEquals(80, recipe.maxY)
        assertTrue(recipe.neededBaseBlocks.contains("minecraft:grass_block"))
        assertTrue(recipe.neededNearbyBlocks.contains("minecraft:water"))
        assertEquals(0, recipe.minLight)
        assertEquals(7, recipe.maxLight)
        assertEquals(0, recipe.moonPhase)
        assertEquals(true, recipe.isSlimeChunk)
        assertEquals(2, recipe.minLureLevel)
        assertEquals("super_rod", recipe.rodType)
        assertEquals("minecraft:glowstone_dust", recipe.bait)
        assertTrue(recipe.structures.contains("minecraft:village"))
        assertTrue(recipe.antiStructures.contains("minecraft:fortress"))
        assertTrue(recipe.isNaturalSpawn)
    }

    @Test
    fun `test PokemonSpawnDataLoader aggregateRawEntries merges matching conditions and separates altitude rules`() {
        val (sp, form) = createDummySpeciesAndForm("Gimmighoul")

        // 2 entrées souterraines compatibles (doivent fusionner leurs biomes)
        val entry1 = PokemonSpawnDataLoader.RawSpawnEntry(
            species = sp,
            form = form,
            bucket = "uncommon",
            weight = 5.0f,
            levelMin = 10,
            levelMax = 20,
            context = "grounded",
            biomes = setOf("minecraft:dripstone_caves"),
            antiBiomes = emptySet(),
            dimensions = setOf("minecraft:overworld"),
            timeRange = "any",
            weather = "any",
            canSeeSky = false,
            minSkyLight = null,
            maxSkyLight = 0,
            minY = -64,
            maxY = 20,
            neededBaseBlocks = setOf("minecraft:deepslate"),
            neededNearbyBlocks = setOf("minecraft:chest"),
            minLight = null,
            maxLight = 4,
            moonPhase = null,
            isSlimeChunk = false,
            minLureLevel = null,
            maxLureLevel = null,
            rodType = null,
            bait = null,
            structures = setOf("minecraft:mineshaft"),
            antiStructures = emptySet()
        )

        val entry2 = PokemonSpawnDataLoader.RawSpawnEntry(
            species = sp,
            form = form,
            bucket = "uncommon",
            weight = 5.0f,
            levelMin = 10,
            levelMax = 20,
            context = "grounded",
            biomes = setOf("minecraft:lush_caves"),
            antiBiomes = emptySet(),
            dimensions = setOf("minecraft:overworld"),
            timeRange = "any",
            weather = "any",
            canSeeSky = false,
            minSkyLight = null,
            maxSkyLight = 0,
            minY = -64,
            maxY = 20,
            neededBaseBlocks = setOf("minecraft:deepslate"),
            neededNearbyBlocks = setOf("minecraft:moss_block"),
            minLight = null,
            maxLight = 4,
            moonPhase = null,
            isSlimeChunk = false,
            minLureLevel = null,
            maxLureLevel = null,
            rodType = null,
            bait = null,
            structures = setOf("minecraft:stronghold"),
            antiStructures = setOf("minecraft:ocean_monument")
        )

        // 1 entrée en surface avec un minY différent (ne doit PAS fusionner avec les entrées souterraines)
        val entry3 = PokemonSpawnDataLoader.RawSpawnEntry(
            species = sp,
            form = form,
            bucket = "uncommon",
            weight = 5.0f,
            levelMin = 10,
            levelMax = 20,
            context = "grounded",
            biomes = setOf("minecraft:plains"),
            antiBiomes = emptySet(),
            dimensions = setOf("minecraft:overworld"),
            timeRange = "any",
            weather = "any",
            canSeeSky = true,
            minSkyLight = 10,
            maxSkyLight = 15,
            minY = 64,
            maxY = 120,
            neededBaseBlocks = emptySet(),
            neededNearbyBlocks = emptySet(),
            minLight = null,
            maxLight = null,
            moonPhase = null,
            isSlimeChunk = false,
            minLureLevel = null,
            maxLureLevel = null,
            rodType = null,
            bait = null,
            structures = emptySet(),
            antiStructures = emptySet()
        )

        val aggregated = PokemonSpawnDataLoader.aggregateRawEntries(listOf(entry1, entry2, entry3))

        // On doit obtenir exactement 2 recettes : une pour le groupe souterrain et une pour la surface
        assertEquals(2, aggregated.size)

        val caveRecipe = aggregated.firstOrNull { it.minY == -64 && it.maxY == 20 }
        val surfaceRecipe = aggregated.firstOrNull { it.minY == 64 && it.maxY == 120 }

        assertTrue(caveRecipe != null, "La recette souterraine doit exister")
        assertTrue(surfaceRecipe != null, "La recette de surface doit exister")

        // Vérification de la fusion souterraine
        assertEquals(setOf("minecraft:dripstone_caves", "minecraft:lush_caves"), caveRecipe!!.biomes)
        assertEquals(setOf("minecraft:chest", "minecraft:moss_block"), caveRecipe.neededNearbyBlocks)
        assertEquals(setOf("minecraft:mineshaft", "minecraft:stronghold"), caveRecipe.structures)
        assertEquals(setOf("minecraft:ocean_monument"), caveRecipe.antiStructures)
        assertEquals(setOf("minecraft:deepslate"), caveRecipe.neededBaseBlocks)
        assertEquals(false, caveRecipe.canSeeSky)

        // Vérification de la recette de surface
        assertEquals(setOf("minecraft:plains"), surfaceRecipe!!.biomes)
        assertEquals(true, surfaceRecipe.canSeeSky)
        assertEquals(64, surfaceRecipe.minY)
        assertEquals(120, surfaceRecipe.maxY)
    }
}
