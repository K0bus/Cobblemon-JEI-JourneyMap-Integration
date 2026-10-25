package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.CobblemonItems
import mezz.jei.api.constants.VanillaTypes
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
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Catégorie JEI pour les évolutions de Pokémon dans Cobblemon.
 * Affiche le Pokémon d'origine, le Pokémon résultant, l'objet nécessaire (Pierre foudre, objet tenu, etc.)
 * et le résumé textuel des conditions d'évolution (niveau, bonheur, météo, etc.).
 */
@Suppress("DEPRECATION")
class PokemonEvolutionRecipeCategory(
    private val guiHelper: IGuiHelper
) : IRecipeCategory<PokemonEvolutionRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<PokemonEvolutionRecipe> = RecipeType.create(
            "cobblemon_jei",
            "pokemon_evolutions",
            PokemonEvolutionRecipe::class.java
        )

        const val WIDTH = 176
        const val HEIGHT = 108
    }

    override fun getRecipeType(): RecipeType<PokemonEvolutionRecipe> = RECIPE_TYPE

    override fun getTitle(): Component {
        return Component.translatable("gui.cobblemon_jei.category.pokemon_evolutions")
    }

    override fun getBackground(): IDrawable {
        return guiHelper.createBlankDrawable(WIDTH, HEIGHT)
    }

    override fun getIcon(): IDrawable {
        val stack = try {
            ItemStack(CobblemonItems.THUNDER_STONE)
        } catch (_: Throwable) {
            ItemStack(Items.AMETHYST_SHARD)
        }
        return guiHelper.createDrawableItemStack(stack)
    }

    override fun setRecipe(
        builder: IRecipeLayoutBuilder,
        recipe: PokemonEvolutionRecipe,
        focuses: IFocusGroup
    ) {
        // Slot 1 : Pokémon de départ (Sous-évolution)
        builder.addSlot(RecipeIngredientRole.INPUT, 16, 20)
            .setBackground(guiHelper.getSlotDrawable(), -1, -1)
            .addIngredient(PokemonSpeciesIngredient.TYPE, recipe.inputIngredient)

        // Slot 2 : Objet d'évolution (si requis : Pierre Foudre, objet tenu, etc.)
        if (recipe.requiredItems.isNotEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 80, 10)
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addIngredients(VanillaTypes.ITEM_STACK, recipe.requiredItems)
        }

        // Slot 3 : Pokémon évolué (Résultat)
        builder.addSlot(RecipeIngredientRole.OUTPUT, 142, 20)
            .setBackground(guiHelper.getSlotDrawable(), -1, -1)
            .addIngredient(PokemonSpeciesIngredient.TYPE, recipe.outputIngredient)
    }

    override fun draw(
        recipe: PokemonEvolutionRecipe,
        recipeSlotsView: IRecipeSlotsView,
        guiGraphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        val font = Minecraft.getInstance().font

        // 1. Fond sombre élégant
        guiGraphics.fill(0, 0, WIDTH, HEIGHT, 0xFF141519.toInt())
        guiGraphics.fill(1, 1, WIDTH - 1, HEIGHT - 1, 0xFF1C1E24.toInt())

        // 2. Cadres des slots Pokémon (Origine à 15, 19 et Résultat à 141, 19)
        drawSlotBorder(guiGraphics, 15, 19, 0xFF3D4050.toInt())
        drawSlotBorder(guiGraphics, 141, 19, 0xFF2ED573.toInt())

        // 3. Cadre central de l'objet si requis (à 79, 9)
        if (recipe.requiredItems.isNotEmpty()) {
            drawSlotBorder(guiGraphics, 79, 9, 0xFFE1B12C.toInt())
        }

        // 4. Flèche d'évolution centrale stylisée
        val arrowY = if (recipe.requiredItems.isNotEmpty()) 32 else 24
        guiGraphics.fill(60, arrowY + 3, 110, arrowY + 5, 0xFF54A0FF.toInt())
        guiGraphics.fill(108, arrowY + 1, 114, arrowY + 7, 0xFF54A0FF.toInt())
        guiGraphics.fill(113, arrowY + 2, 116, arrowY + 6, 0xFF54A0FF.toInt())

        // 5. Noms des deux Pokémon au-dessus de leurs slots respectifs
        val srcName = recipe.sourceSpecies.translatedName.string
        val dstName = recipe.targetSpecies.translatedName.string

        val srcW = font.width(srcName)
        val dstW = font.width(dstName)

        val srcX = (24 - srcW / 2).coerceIn(4, 70)
        val dstX = (150 - dstW / 2).coerceIn(100, WIDTH - dstW - 4)

        guiGraphics.drawString(font, srcName, srcX, 7, 0xFFCCCCCC.toInt(), false)
        guiGraphics.drawString(font, dstName, dstX, 7, 0xFF2ED573.toInt(), false)

        // 6. Carte récapitulative des conditions (Y: 52 à 103)
        val cardY = 52
        guiGraphics.fill(6, cardY, WIDTH - 6, HEIGHT - 5, 0xFF181920.toInt())
        guiGraphics.fill(5, cardY - 1, WIDTH - 5, cardY, 0xFF2F3240.toInt())

        // Libellé de condition
        val variantTitle = Component.translatable("gui.cobblemon_jei.category.pokemon_evolutions.condition_title").string
        guiGraphics.drawString(font, variantTitle, 10, cardY + 6, 0xFFFF9F43.toInt(), false)

        val titleWidth = font.width(variantTitle) + 4
        val maxSummaryWidth = WIDTH - 16 - titleWidth
        val summaryText = recipe.requirementsSummary
        val displaySummary = if (font.width(summaryText) > maxSummaryWidth) {
            font.plainSubstrByWidth(summaryText, maxSummaryWidth - 6) + "…"
        } else {
            summaryText
        }
        guiGraphics.drawString(font, displaySummary, 10 + titleWidth, cardY + 6, 0xFFFFFFFF.toInt(), false)

        // Lignes secondaires
        if (recipe.additionalConditions.isNotEmpty()) {
            val subText = "• ${recipe.additionalConditions.first()}"
            guiGraphics.drawString(font, font.plainSubstrByWidth(subText, WIDTH - 20), 10, cardY + 20, 0xFFAAAAAA.toInt(), false)

            if (recipe.additionalConditions.size > 1) {
                val moreText = Component.translatable(
                    "gui.cobblemon_jei.category.pokemon_evolutions.more_conditions",
                    recipe.additionalConditions.size - 1
                ).string
                guiGraphics.drawString(font, font.plainSubstrByWidth(moreText, WIDTH - 20), 10, cardY + 32, 0xFF888888.toInt(), false)
            } else {
                val hint = Component.translatable("gui.cobblemon_jei.category.pokemon_evolutions.hint_hover").string
                guiGraphics.drawString(font, font.plainSubstrByWidth(hint, WIDTH - 20), 10, cardY + 32, 0xFF666666.toInt(), false)
            }
        } else if (recipe.requiredItems.isNotEmpty()) {
            val itemName = recipe.requiredItems.first().hoverName.string
            val line2 = Component.translatable("gui.cobblemon_jei.category.pokemon_evolutions.item_needed", itemName).string
            guiGraphics.drawString(font, font.plainSubstrByWidth(line2, WIDTH - 20), 10, cardY + 20, 0xFFE0E0E0.toInt(), false)

            val hint = Component.translatable("gui.cobblemon_jei.category.pokemon_evolutions.hint_hover").string
            guiGraphics.drawString(font, font.plainSubstrByWidth(hint, WIDTH - 20), 10, cardY + 32, 0xFF666666.toInt(), false)
        } else {
            val directLine = Component.translatable("gui.cobblemon_jei.category.pokemon_evolutions.direct_level").string
            guiGraphics.drawString(font, font.plainSubstrByWidth(directLine, WIDTH - 20), 10, cardY + 20, 0xFF888888.toInt(), false)

            val hint = Component.translatable("gui.cobblemon_jei.category.pokemon_evolutions.hint_hover").string
            guiGraphics.drawString(font, font.plainSubstrByWidth(hint, WIDTH - 20), 10, cardY + 32, 0xFF666666.toInt(), false)
        }
    }

    override fun getTooltipStrings(
        recipe: PokemonEvolutionRecipe,
        recipeSlotsView: IRecipeSlotsView,
        mouseX: Double,
        mouseY: Double
    ): List<Component> {
        val tooltips = mutableListOf<Component>()

        // Tooltip complet si survol de la carte inférieure
        if (mouseY >= 52 && mouseY <= (HEIGHT - 5) && mouseX >= 6 && mouseX <= (WIDTH - 6)) {
            tooltips.add(
                Component.translatable("gui.cobblemon_jei.category.pokemon_evolutions.tooltip_title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            )
            tooltips.add(
                Component.translatable("gui.cobblemon_jei.category.pokemon_evolutions.tooltip_type", recipe.evolutionVariant)
                    .withStyle(ChatFormatting.AQUA)
            )

            if (recipe.requiredItems.isNotEmpty()) {
                val itemNames = recipe.requiredItems.joinToString(", ") { it.hoverName.string }
                tooltips.add(
                    Component.translatable("gui.cobblemon_jei.category.pokemon_evolutions.tooltip_item", itemNames)
                        .withStyle(ChatFormatting.YELLOW)
                )
            }

            for (cond in recipe.additionalConditions) {
                tooltips.add(Component.literal(" • $cond").withStyle(ChatFormatting.GRAY))
            }
        }

        return tooltips
    }

    private fun drawSlotBorder(guiGraphics: GuiGraphics, x: Int, y: Int, borderColor: Int) {
        guiGraphics.fill(x, y, x + 18, y + 1, borderColor)
        guiGraphics.fill(x, y + 17, x + 18, y + 18, borderColor)
        guiGraphics.fill(x, y + 1, x + 1, y + 17, borderColor)
        guiGraphics.fill(x + 17, y + 1, x + 18, y + 17, borderColor)
    }
}
