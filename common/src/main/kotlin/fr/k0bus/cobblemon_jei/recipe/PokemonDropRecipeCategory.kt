package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
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
 * Catégorie JEI pour les butins lâchés par les Pokémon sauvages à leur mort/défaite.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class PokemonDropRecipeCategory(
    private val guiHelper: IGuiHelper
) : IRecipeCategory<PokemonDropRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<PokemonDropRecipe> = RecipeType.create(
            "cobblemon_jei",
            "pokemon_drops",
            PokemonDropRecipe::class.java
        )

        const val WIDTH = 176
        const val HEIGHT = 112
    }

    override fun getRecipeType(): RecipeType<PokemonDropRecipe> = RECIPE_TYPE

    override fun getTitle(): Component {
        return Component.translatable("gui.cobblemon_jei.category.pokemon_drops")
    }

    override fun getBackground(): IDrawable {
        return guiHelper.createBlankDrawable(WIDTH, HEIGHT)
    }

    override fun getIcon(): IDrawable {
        return guiHelper.createDrawableItemStack(ItemStack(Items.DIAMOND_SWORD))
    }

    override fun setRecipe(
        builder: IRecipeLayoutBuilder,
        recipe: PokemonDropRecipe,
        focuses: IFocusGroup
    ) {
        // Slot 1 : Pokémon en INPUT (recherchable via 'U' sur le Pokémon)
        builder.addSlot(RecipeIngredientRole.INPUT, 14, 30)
            .setBackground(guiHelper.getSlotDrawable(), -1, -1)
            .addIngredient(PokemonSpeciesIngredient.TYPE, recipe.pokemonIngredient)

        // Slots OUTPUT : Butins lâchés à la défaite
        val drops = recipe.drops
        val total = drops.size

        for (i in drops.indices) {
            val drop = drops[i]
            val (slotX, slotY) = calculateDropSlotPosition(i, total)

            val slotBuilder = builder.addSlot(RecipeIngredientRole.OUTPUT, slotX, slotY)
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addItemStack(drop.itemStack)

            slotBuilder.addTooltipCallback { _, tooltip ->
                tooltip.add(
                    Component.translatable(
                        "gui.cobblemon_jei.category.pokemon_drops.chance",
                        drop.percentageDisplay
                    ).withStyle(getPercentageChatFormatting(drop.percentage))
                )
                tooltip.add(
                    Component.translatable(
                        "gui.cobblemon_jei.category.pokemon_drops.quantity",
                        drop.quantityDisplay
                    ).withStyle(ChatFormatting.GOLD)
                )
                if (drop.isEvolutionItem) {
                    tooltip.add(
                        Component.translatable("gui.cobblemon_jei.category.pokemon_drops.evolution_item")
                            .withStyle(ChatFormatting.LIGHT_PURPLE)
                    )
                }
                tooltip.add(
                    Component.translatable("gui.cobblemon_jei.category.pokemon_drops.how_to")
                        .withStyle(ChatFormatting.GRAY)
                )
            }
        }
    }

    override fun draw(
        recipe: PokemonDropRecipe,
        recipeSlotsView: IRecipeSlotsView,
        guiGraphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        val font = Minecraft.getInstance().font

        // 1. Fond général thème sombre
        guiGraphics.fill(0, 0, WIDTH, HEIGHT, 0xFF141519.toInt())
        guiGraphics.fill(1, 1, WIDTH - 1, HEIGHT - 1, 0xFF1C1E24.toInt())

        // 2. Bandeau supérieur (Y: 2 à 20)
        guiGraphics.fill(2, 2, WIDTH - 2, 20, 0xFF242630.toInt())

        // Badge nombre de tirages à droite
        val badgeText = Component.translatable("gui.cobblemon_jei.category.pokemon_drops.rolls", recipe.rollsDisplay).string
        val badgeW = font.width(badgeText)
        val badgeX = WIDTH - badgeW - 10
        guiGraphics.fill(badgeX - 4, 3, WIDTH - 4, 18, 0xFF101216.toInt())
        guiGraphics.drawString(font, badgeText, badgeX - 1, 6, 0xFFFF9F43.toInt(), false)

        // Nom du Pokémon à gauche
        val dexStr = String.format("#%04d", recipe.species.nationalPokedexNumber)
        val nameStr = recipe.species.translatedName.string
        val formStr = recipe.formName?.let { " ($it)" } ?: ""
        val fullTitle = "$dexStr $nameStr$formStr"
        guiGraphics.drawString(font, font.plainSubstrByWidth(fullTitle, badgeX - 10), 6, 6, 0xFFFFFFFF.toInt(), false)

        // 3. Cadre du slot Pokémon
        drawSlotBorder(guiGraphics, 13, 29, 0xFF54A0FF.toInt())

        // Flèche / épée stylisée vers la droite (X: 35 à 47, Y: 38)
        guiGraphics.fill(35, 38, 43, 40, 0xFFEE5253.toInt())
        guiGraphics.fill(41, 36, 45, 42, 0xFFEE5253.toInt())
        guiGraphics.fill(44, 37, 47, 41, 0xFFEE5253.toInt())

        // 4. Cadres et étiquettes pour chaque drop
        val drops = recipe.drops
        val total = drops.size

        for (i in drops.indices) {
            val drop = drops[i]
            val (slotX, slotY) = calculateDropSlotPosition(i, total)
            val borderColor = getPercentageColor(drop.percentage)

            drawSlotBorder(guiGraphics, slotX - 1, slotY - 1, borderColor)

            if (total <= 2 || total == 4) {
                // Affichage texte à droite du slot
                val textX = slotX + 21
                guiGraphics.drawString(font, drop.percentageDisplay, textX, slotY + 1, borderColor, false)
                guiGraphics.drawString(font, drop.quantityDisplay, textX, slotY + 10, 0xFFA4B0BE.toInt(), false)
            } else if (total == 3) {
                // Affichage texte sous le slot
                val pctX = slotX + 9 - font.width(drop.percentageDisplay) / 2
                val qtyX = slotX + 9 - font.width(drop.quantityDisplay) / 2
                guiGraphics.drawString(font, drop.percentageDisplay, pctX, slotY + 20, borderColor, false)
                guiGraphics.drawString(font, drop.quantityDisplay, qtyX, slotY + 29, 0xFFA4B0BE.toInt(), false)
            } else {
                // 5 drops : texte compact sous ou à côté
                val pctX = slotX + 9 - font.width(drop.percentageDisplay) / 2
                guiGraphics.drawString(font, drop.percentageDisplay, pctX, slotY + 19, borderColor, false)
            }
        }

        // 5. Carte inférieure d'informations (Y: 72 à HEIGHT - 5)
        val cardY = 72
        guiGraphics.fill(4, cardY, WIDTH - 4, HEIGHT - 5, 0xFF181920.toInt())
        guiGraphics.fill(4, cardY - 1, WIDTH - 4, cardY, 0xFF2F3240.toInt())

        val line1 = Component.translatable("gui.cobblemon_jei.category.pokemon_drops.rolls_desc", recipe.rollsDisplay).string
        guiGraphics.drawString(font, font.plainSubstrByWidth(line1, WIDTH - 16), 8, cardY + 4, 0xFFFFBE76.toInt(), false)

        val line2 = Component.translatable("gui.cobblemon_jei.category.pokemon_drops.how_to").string
        guiGraphics.drawString(font, font.plainSubstrByWidth(line2, WIDTH - 16), 8, cardY + 15, 0xFFE0E0E0.toInt(), false)

        val line3 = Component.translatable("gui.cobblemon_jei.category.pokemon_drops.looting_hint").string
        guiGraphics.drawString(font, font.plainSubstrByWidth(line3, WIDTH - 16), 8, cardY + 25, 0xFF747D8C.toInt(), false)
    }

    private fun drawSlotBorder(guiGraphics: GuiGraphics, x: Int, y: Int, borderColor: Int) {
        guiGraphics.fill(x, y, x + 18, y + 1, borderColor)
        guiGraphics.fill(x, y + 17, x + 18, y + 18, borderColor)
        guiGraphics.fill(x, y + 1, x + 1, y + 17, borderColor)
        guiGraphics.fill(x + 17, y + 1, x + 18, y + 17, borderColor)
    }

    private fun calculateDropSlotPosition(index: Int, total: Int): Pair<Int, Int> {
        return when (total) {
            1 -> Pair(92, 30)
            2 -> Pair(58 + index * 58, 30)
            3 -> Pair(56 + index * 40, 24)
            4 -> {
                val col = index % 2
                val row = index / 2
                Pair(58 + col * 58, 24 + row * 22)
            }
            5 -> {
                if (index < 3) {
                    Pair(56 + index * 40, 23)
                } else {
                    Pair(76 + (index - 3) * 40, 48)
                }
            }
            else -> {
                val col = index % 3
                val row = index / 3
                Pair(56 + col * 40, 23 + row * 24)
            }
        }
    }

    private fun getPercentageColor(percentage: Float): Int {
        return when {
            percentage >= 100f -> 0xFF2ED573.toInt() // Vert vif
            percentage >= 20f -> 0xFF54A0FF.toInt()  // Bleu cyan
            percentage >= 5f -> 0xFFFECA57.toInt()   // Jaune doré
            else -> 0xFFFF6B6B.toInt()               // Rouge / Rare
        }
    }

    private fun getPercentageChatFormatting(percentage: Float): ChatFormatting {
        return when {
            percentage >= 100f -> ChatFormatting.GREEN
            percentage >= 20f -> ChatFormatting.AQUA
            percentage >= 5f -> ChatFormatting.YELLOW
            else -> ChatFormatting.RED
        }
    }
}
