package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.CobblemonItems
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Catégorie JEI pour les apparitions de Pokémon Cobblemon.
 * Affiche l'ingrédient Pokémon, ses biomes, météo, tranches horaires, raretés et dimensions.
 */
class PokemonSpawnRecipeCategory(
    private val guiHelper: IGuiHelper
) : IRecipeCategory<PokemonSpawnRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<PokemonSpawnRecipe> = RecipeType.create(
            "cobblemon_jei",
            "pokemon_spawns",
            PokemonSpawnRecipe::class.java
        )

        const val WIDTH = 176
        const val HEIGHT = 130
    }

    override fun getRecipeType(): RecipeType<PokemonSpawnRecipe> = RECIPE_TYPE

    override fun getTitle(): Component {
        return Component.translatable("gui.cobblemon_jei.category.pokemon_spawns")
    }

    override fun getBackground(): IDrawable {
        return guiHelper.createBlankDrawable(WIDTH, HEIGHT)
    }

    override fun getIcon(): IDrawable {
        val stack = try {
            ItemStack(CobblemonItems.POKE_BALL)
        } catch (_: Throwable) {
            ItemStack(Items.COMPASS)
        }
        return guiHelper.createDrawableItemStack(stack)
    }

    override fun setRecipe(
        builder: IRecipeLayoutBuilder,
        recipe: PokemonSpawnRecipe,
        focuses: IFocusGroup
    ) {
        // Emplacement de l'ingrédient Pokémon en haut à gauche (OUTPUT = recherchable via R pour Recipe)
        builder.addSlot(RecipeIngredientRole.OUTPUT, 7, 7)
            .addIngredient(PokemonSpeciesIngredient.TYPE, recipe.pokemonIngredient)
    }

    override fun draw(
        recipe: PokemonSpawnRecipe,
        recipeSlotsView: IRecipeSlotsView,
        guiGraphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        val font = Minecraft.getInstance().font

        // 1. Fond général élégant thème sombre
        guiGraphics.fill(0, 0, WIDTH, HEIGHT, 0xFF141519.toInt())
        guiGraphics.fill(1, 1, WIDTH - 1, HEIGHT - 1, 0xFF1C1E24.toInt())

        // Cadre de l'en-tête
        guiGraphics.fill(2, 2, WIDTH - 2, 28, 0xFF242630.toInt())

        // Cadre du slot Pokémon
        guiGraphics.fill(6, 6, 24, 24, 0xFF0E0F12.toInt())
        guiGraphics.fill(5, 5, 25, 6, 0xFF3D4050.toInt())
        guiGraphics.fill(5, 24, 25, 25, 0xFF3D4050.toInt())
        guiGraphics.fill(5, 5, 6, 25, 0xFF3D4050.toInt())
        guiGraphics.fill(24, 5, 25, 25, 0xFF3D4050.toInt())

        // 2. En-tête : Numéro de Dex + Nom du Pokémon
        val dexStr = String.format("#%04d", recipe.species.nationalPokedexNumber)
        val nameText = recipe.species.translatedName.string
        val headerTitle = "$dexStr $nameText"
        guiGraphics.drawString(font, headerTitle, 28, 6, 0xFFFFFFFF.toInt(), false)

        // Badges des types élémentaires
        var typeX = 28
        val primaryTypeName = recipe.form.primaryType.displayName.string.uppercase()
        val primaryColor = getElementColor(recipe.form.primaryType.name)
        val badgeW1 = font.width(primaryTypeName) + 6
        guiGraphics.fill(typeX, 17, typeX + badgeW1, 25, primaryColor)
        guiGraphics.drawString(font, primaryTypeName, typeX + 3, 18, 0xFFFFFFFF.toInt(), false)
        typeX += badgeW1 + 4

        recipe.form.secondaryType?.let { secType ->
            val secTypeName = secType.displayName.string.uppercase()
            val secColor = getElementColor(secType.name)
            val badgeW2 = font.width(secTypeName) + 6
            guiGraphics.fill(typeX, 17, typeX + badgeW2, 25, secColor)
            guiGraphics.drawString(font, secTypeName, typeX + 3, 18, 0xFFFFFFFF.toInt(), false)
        }

        // 3. Contenu principal
        if (!recipe.isNaturalSpawn) {
            drawNoSpawnCard(guiGraphics, font)
        } else {
            drawSpawnDetails(guiGraphics, font, recipe)
        }
    }

    private fun drawSpawnDetails(
        guiGraphics: GuiGraphics,
        font: net.minecraft.client.gui.Font,
        recipe: PokemonSpawnRecipe
    ) {
        // Ligne 1 (y = 28) : Rareté & Poids
        val rarityComp = formatBucket(recipe.bucket)
        val rarityColor = getBucketColor(recipe.bucket)
        val weightText = String.format(java.util.Locale.ROOT, "%.1f", recipe.weight)
        val rarityLabel = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.rarity_label").string
        guiGraphics.drawString(font, rarityLabel, 8, 28, 0xFFAAAAAA.toInt(), false)
        val weightLabel = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.weight_label", weightText).string
        val rarityFull = "${rarityComp.string} $weightLabel"
        guiGraphics.drawString(font, rarityFull, 8 + font.width(rarityLabel) + 4, 28, rarityColor, false)

        // Ligne 2 (y = 39) : Monde & Contexte/Position
        val dimLabel = formatDimension(recipe.dimensions)
        val worldLabel = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.world_label").string
        guiGraphics.drawString(font, worldLabel, 8, 39, 0xFFAAAAAA.toInt(), false)
        val worldStartX = 8 + font.width(worldLabel) + 4
        guiGraphics.drawString(font, dimLabel, worldStartX, 39, 0xFF1DD1A1.toInt(), false)

        val contextLabel = formatContext(recipe.context)
        val dimWidth = worldStartX + font.width(dimLabel)
        val posX = (dimWidth + 8).coerceIn(88, 115)
        if (posX + font.width(contextLabel) <= WIDTH - 8) {
            guiGraphics.drawString(font, "•", posX - 5, 39, 0xFF666666.toInt(), false)
            guiGraphics.drawString(font, contextLabel, posX, 39, 0xFF54A0FF.toInt(), false)
        }

        // Ligne 3 (y = 50) : Niveaux
        val levelsLabel = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.levels_label").string
        guiGraphics.drawString(font, levelsLabel, 8, 50, 0xFFAAAAAA.toInt(), false)
        val levelsRange = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.level_range", recipe.levelMin, recipe.levelMax).string
        guiGraphics.drawString(font, levelsRange, 8 + font.width(levelsLabel) + 4, 50, 0xFFE0E0E0.toInt(), false)

        // Ligne 4 (y = 61) : Heure & Météo
        val timeLabel = formatTime(recipe.timeRange)
        val weatherLabel = formatWeather(recipe.weather)
        val timeTitle = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.time_label").string
        guiGraphics.drawString(font, timeTitle, 8, 61, 0xFFAAAAAA.toInt(), false)
        val timeStartX = 8 + font.width(timeTitle) + 4
        guiGraphics.drawString(font, timeLabel.string, timeStartX, 61, 0xFFFEEA3A.toInt(), false)

        val timeTotalWidth = timeStartX + font.width(timeLabel.string)
        val weatherTitle = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.weather_label").string
        val weatherX = (timeTotalWidth + 8).coerceIn(86, 108)
        if (weatherX + font.width("$weatherTitle ${weatherLabel.string}") <= WIDTH - 6) {
            guiGraphics.drawString(font, "•", weatherX - 5, 61, 0xFF666666.toInt(), false)
            guiGraphics.drawString(font, weatherTitle, weatherX, 61, 0xFFAAAAAA.toInt(), false)
            guiGraphics.drawString(font, weatherLabel.string, weatherX + font.width(weatherTitle) + 4, 61, 0xFF48DBFB.toInt(), false)
        }

        // 4. Bloc des biomes en bas (y = 73 à 124)
        val biomeBoxY = 73
        guiGraphics.fill(6, biomeBoxY, WIDTH - 6, HEIGHT - 6, 0xFF181920.toInt())
        guiGraphics.fill(5, biomeBoxY - 1, WIDTH - 5, biomeBoxY, 0xFF2F3240.toInt())

        val biomesTitle = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.biomes").string
        guiGraphics.drawString(font, biomesTitle, 10, biomeBoxY + 3, 0xFFFF9F43.toInt(), false)

        if (recipe.biomes.isEmpty()) {
            val allBiomes = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.all_biomes").string
            guiGraphics.drawString(font, allBiomes, 10, biomeBoxY + 15, 0xFF888888.toInt(), false)
        } else {
            val friendlyBiomes = recipe.biomes.map { formatBiomeName(it) }
            val firstLine = friendlyBiomes.take(3).joinToString(", ")
            guiGraphics.drawString(font, firstLine, 10, biomeBoxY + 15, 0xFFE0E0E0.toInt(), false)

            if (friendlyBiomes.size > 3) {
                val remaining = friendlyBiomes.size - 3
                val moreText = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.biomes.more", remaining).string
                guiGraphics.drawString(font, moreText, 10, biomeBoxY + 27, 0xFF888888.toInt(), false)
            } else if (friendlyBiomes.size > 1) {
                val secondLine = friendlyBiomes.drop(3).take(3).joinToString(", ")
                if (secondLine.isNotEmpty()) {
                    guiGraphics.drawString(font, secondLine, 10, biomeBoxY + 27, 0xFFE0E0E0.toInt(), false)
                }
            }
        }
    }

    private fun drawNoSpawnCard(guiGraphics: GuiGraphics, font: net.minecraft.client.gui.Font) {
        val startY = 36
        // Cadre d'avertissement teinté
        guiGraphics.fill(6, startY, WIDTH - 6, HEIGHT - 6, 0x22FFA502.toInt())
        guiGraphics.fill(5, startY - 1, WIDTH - 5, HEIGHT - 5, 0x55FFA502.toInt())

        val warn = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.no_spawns.warn").string
        guiGraphics.drawString(font, warn, 12, startY + 6, 0xFFFFA502.toInt(), false)

        val desc = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.no_spawns.desc").string
        guiGraphics.drawString(font, desc, 12, startY + 18, 0xFFCCCCCC.toInt(), false)

        val obtain = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.no_spawns.obtain").string
        guiGraphics.drawString(font, obtain, 12, startY + 32, 0xFFFFFFFF.toInt(), false)

        val evo = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.no_spawns.evolution").string
        guiGraphics.drawString(font, evo, 16, startY + 43, 0xFFAAAAAA.toInt(), false)

        val breed = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.no_spawns.breeding").string
        guiGraphics.drawString(font, breed, 16, startY + 54, 0xFFAAAAAA.toInt(), false)

        val spec = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.no_spawns.special").string
        guiGraphics.drawString(font, spec, 16, startY + 65, 0xFFAAAAAA.toInt(), false)

        val quest = Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.no_spawns.quest").string
        guiGraphics.drawString(font, quest, 16, startY + 76, 0xFFAAAAAA.toInt(), false)
    }

    override fun getTooltipStrings(
        recipe: PokemonSpawnRecipe,
        recipeSlotsView: IRecipeSlotsView,
        mouseX: Double,
        mouseY: Double
    ): List<Component> {
        val tooltips = mutableListOf<Component>()

        // Tooltip complet si survol de la zone des biomes (y entre 73 et 124)
        if (recipe.isNaturalSpawn && mouseY >= 73 && mouseY <= 124 && mouseX >= 6 && mouseX <= (WIDTH - 6)) {
            if (recipe.biomes.isNotEmpty()) {
                tooltips.add(
                    Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.tooltip_biomes")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                )
                for (biome in recipe.biomes) {
                    val isTag = biome.startsWith("#")
                    val prefix = if (isTag) " • [Tag] " else " • "
                    val style = if (isTag) ChatFormatting.YELLOW else ChatFormatting.AQUA
                    tooltips.add(Component.literal(prefix + formatBiomeName(biome)).withStyle(style))
                }
            }
            if (recipe.antiBiomes.isNotEmpty()) {
                tooltips.add(
                    Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.tooltip_anti_biomes")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                )
                for (anti in recipe.antiBiomes) {
                    tooltips.add(Component.literal(" ✗ $anti").withStyle(ChatFormatting.RED))
                }
            }
            if (recipe.structures.isNotEmpty()) {
                tooltips.add(
                    Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.tooltip_structures")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                )
                for (struct in recipe.structures) {
                    tooltips.add(Component.literal(" 🏛 $struct").withStyle(ChatFormatting.LIGHT_PURPLE))
                }
            }
        }

        return tooltips
    }

    // --- Helpers de formatage ---

    private fun formatBucket(bucket: String): Component {
        return when (bucket.lowercase()) {
            "common" -> Component.translatable("cobblemon_jei.bucket.common")
            "uncommon" -> Component.translatable("cobblemon_jei.bucket.uncommon")
            "rare" -> Component.translatable("cobblemon_jei.bucket.rare")
            "ultra-rare" -> Component.translatable("cobblemon_jei.bucket.ultra-rare")
            else -> Component.literal(bucket.replaceFirstChar { it.uppercase() })
        }
    }

    private fun getBucketColor(bucket: String): Int {
        return when (bucket.lowercase()) {
            "common" -> 0xFF2ED573.toInt()
            "uncommon" -> 0xFF1E90FF.toInt()
            "rare" -> 0xFFFFA502.toInt()
            "ultra-rare" -> 0xFFFF4757.toInt()
            else -> 0xFFE0E0E0.toInt()
        }
    }

    private fun formatContext(ctx: String): String {
        return when (ctx.lowercase()) {
            "grounded" -> "Sol"
            "submerged" -> "Sous l'eau"
            "surface" -> "Surface eau"
            "air" -> "Air / Vol"
            "seafloor" -> "Fond marin"
            else -> ctx.replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatTime(time: String): Component {
        return when (time.lowercase()) {
            "day" -> Component.translatable("cobblemon_jei.time.day")
            "night" -> Component.translatable("cobblemon_jei.time.night")
            "dawn" -> Component.translatable("cobblemon_jei.time.dawn")
            "dusk" -> Component.translatable("cobblemon_jei.time.dusk")
            else -> Component.translatable("cobblemon_jei.time.any")
        }
    }

    private fun formatWeather(weather: String): Component {
        return when (weather.lowercase()) {
            "clear" -> Component.translatable("cobblemon_jei.weather.clear")
            "rain" -> Component.translatable("cobblemon_jei.weather.rain")
            "thundering" -> Component.translatable("cobblemon_jei.weather.thundering")
            else -> Component.translatable("cobblemon_jei.weather.any")
        }
    }

    private fun formatDimension(dimensions: Set<String>): String {
        if (dimensions.isEmpty()) return Component.translatable("gui.cobblemon_jei.category.pokemon_spawns.all_worlds").string
        return dimensions.joinToString(", ") { dim ->
            val clean = if (dim.contains(":")) dim.substringAfter(":") else dim
            when (clean.lowercase()) {
                "overworld" -> "Overworld"
                "the_nether", "nether" -> "Nether"
                "the_end", "end" -> "The End"
                "the_aether", "aether" -> "L'Aether"
                else -> clean.replace("_", " ").replaceFirstChar { it.uppercase() }
            }
        }
    }

    private fun formatBiomeName(raw: String): String {
        val clean = raw.removePrefix("#").substringAfter(":")
        return when (clean) {
            "is_overworld" -> "Overworld (Global)"
            "is_freshwater" -> "Eau douce"
            "is_hills" -> "Collines"
            "is_forest" -> "Forêt"
            "is_plains" -> "Plaines"
            "is_jungle" -> "Jungle"
            "is_desert" -> "Désert"
            "is_savanna" -> "Savane"
            "is_taiga" -> "Taïga"
            "is_mountain" -> "Montagne"
            "is_ocean" -> "Océan"
            "is_freezing" -> "Grand froid"
            "is_nether" -> "Nether (Global)"
            "is_end" -> "L'End (Global)"
            "is_aether" -> "Aether (Global)"
            "nether/is_basalt" -> "Nether / Deltas basalte"
            "nether/is_wasteland" -> "Nether / Terres désolées"
            "nether/is_crimson" -> "Nether / Forêt carmin"
            "nether/is_warped" -> "Nether / Forêt biscornue"
            "nether/is_soul_sand" -> "Nether / Vallée âmes"
            "nether/is_forest" -> "Nether / Forêts"
            "is_volcanic" -> "Volcanique"
            "is_spooky" -> "Sinistre"
            else -> clean.replace("_", " ").replaceFirstChar { it.uppercase() }
        }
    }

    private fun getElementColor(typeName: String): Int {
        return when (typeName.lowercase()) {
            "fire" -> 0xFFEB4D4B.toInt()
            "water" -> 0xFF3867D6.toInt()
            "grass" -> 0xFF20BF6B.toInt()
            "electric" -> 0xFFF7B731.toInt()
            "ice" -> 0xFF45AAF2.toInt()
            "fighting" -> 0xFFB71540.toInt()
            "poison" -> 0xFF8854D0.toInt()
            "ground" -> 0xFFE1B12C.toInt()
            "flying" -> 0xFF9980FA.toInt()
            "psychic" -> 0xFFED4C67.toInt()
            "bug" -> 0xFF26DE81.toInt()
            "rock" -> 0xFF778CA3.toInt()
            "ghost" -> 0xFF4B4B4B.toInt()
            "dragon" -> 0xFF3B3B98.toInt()
            "dark" -> 0xFF2C3A47.toInt()
            "steel" -> 0xFF718093.toInt()
            "fairy" -> 0xFFFDA7DF.toInt()
            else -> 0xFF747D8C.toInt()
        }
    }
}
