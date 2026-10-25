package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
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
 * Catégorie JEI pour les interactions directes avec les Pokémon dans Cobblemon
 * (Tonte avec cisailles, traite au seau, brossage, etc.).
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class PokemonInteractionRecipeCategory(
    private val guiHelper: IGuiHelper
) : IRecipeCategory<PokemonInteractionRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<PokemonInteractionRecipe> = RecipeType.create(
            "cobblemon_jei",
            "pokemon_interaction",
            PokemonInteractionRecipe::class.java
        )

        const val WIDTH = 176
        const val HEIGHT = 110
    }

    override fun getRecipeType(): RecipeType<PokemonInteractionRecipe> = RECIPE_TYPE

    override fun getTitle(): Component {
        return Component.translatable("gui.cobblemon_jei.category.pokemon_interaction")
    }

    override fun getBackground(): IDrawable {
        return guiHelper.createBlankDrawable(WIDTH, HEIGHT)
    }

    override fun getIcon(): IDrawable {
        return guiHelper.createDrawableItemStack(ItemStack(Items.SHEARS))
    }

    override fun setRecipe(
        builder: IRecipeLayoutBuilder,
        recipe: PokemonInteractionRecipe,
        focuses: IFocusGroup
    ) {
        val hasTool = recipe.requiredTools.isNotEmpty()
        val pokeSlotX = if (hasTool) 26 else 42
        val toolSlotX = 58

        // Slot 1 : Pokémon en INPUT (recherchable via 'U' sur le Pokémon)
        builder.addSlot(RecipeIngredientRole.INPUT, pokeSlotX, 30)
            .setBackground(guiHelper.getSlotDrawable(), -1, -1)
            .addIngredient(PokemonSpeciesIngredient.TYPE, recipe.pokemonIngredient)

        // Slot 2 : Outil requis en INPUT (recherchable via 'U' sur les cisailles, le seau, etc.)
        if (hasTool) {
            builder.addSlot(RecipeIngredientRole.INPUT, toolSlotX, 30)
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addIngredients(VanillaTypes.ITEM_STACK, recipe.requiredTools)
        }

        // Slots OUTPUT : Ressources récoltées
        val outputs = recipe.outputs
        val outputCount = outputs.size

        for (i in outputs.indices) {
            val drop = outputs[i]
            val slotX = if (outputCount == 1) 126 else 115 + i * 22
            val slotY = 30

            val slotBuilder = builder.addSlot(RecipeIngredientRole.OUTPUT, slotX, slotY)
                .setBackground(guiHelper.getSlotDrawable(), -1, -1)
                .addItemStack(drop.itemStack)

            slotBuilder.addTooltipCallback { _, tooltip ->
                tooltip.add(
                    Component.translatable(
                        "gui.cobblemon_jei.category.pokemon_interaction.amount",
                        drop.quantityDisplay
                    ).withStyle(ChatFormatting.GOLD)
                )
            }
        }
    }

    override fun draw(
        recipe: PokemonInteractionRecipe,
        recipeSlotsView: IRecipeSlotsView,
        guiGraphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        val font = Minecraft.getInstance().font

        // Fond général sombre
        guiGraphics.fill(0, 0, WIDTH, HEIGHT, 0xFF141519.toInt())
        guiGraphics.fill(1, 1, WIDTH - 1, HEIGHT - 1, 0xFF1C1E24.toInt())

        // Bandeau supérieur (Y: 2 à 20)
        guiGraphics.fill(2, 2, WIDTH - 2, 20, 0xFF242630.toInt())

        // Badge d'action à droite
        val badgeText = recipe.actionName.string
        val badgeW = font.width(badgeText)
        val badgeX = WIDTH - badgeW - 10
        guiGraphics.fill(badgeX - 4, 3, WIDTH - 4, 18, 0xFF101216.toInt())
        guiGraphics.drawString(font, badgeText, badgeX - 1, 6, 0xFFFF9F43.toInt(), false)

        // Nom du Pokémon à gauche
        val dexStr = String.format("#%04d", recipe.species.nationalPokedexNumber)
        val nameStr = recipe.species.translatedName.string
        val headerTitle = "$dexStr $nameStr"
        val maxHeaderWidth = badgeX - 12
        val displayHeader = if (font.width(headerTitle) > maxHeaderWidth) {
            font.plainSubstrByWidth(headerTitle, maxHeaderWidth - 6) + "…"
        } else {
            headerTitle
        }
        guiGraphics.drawString(font, displayHeader, 6, 6, 0xFFFFFFFF.toInt(), false)

        // Cadres des slots d'entrée
        val hasTool = recipe.requiredTools.isNotEmpty()
        val pokeSlotX = if (hasTool) 26 else 42
        val toolSlotX = 58

        drawSlotBorder(guiGraphics, pokeSlotX - 1, 29, 0xFF3D4050.toInt())
        if (hasTool) {
            drawSlotBorder(guiGraphics, toolSlotX - 1, 29, 0xFFE1B12C.toInt())
        }

        // Flèche centrale stylisée vers la droite (X: 85 à 101, Y: 38)
        guiGraphics.fill(85, 38, 97, 40, 0xFF54A0FF.toInt())
        guiGraphics.fill(95, 36, 99, 42, 0xFF54A0FF.toInt())
        guiGraphics.fill(98, 37, 101, 41, 0xFF54A0FF.toInt())

        // Cadres pour les sorties
        val outputs = recipe.outputs
        for (i in outputs.indices) {
            val slotX = if (outputs.size == 1) 126 else 115 + i * 22
            drawSlotBorder(guiGraphics, slotX - 1, 29, 0xFF2ED573.toInt())
        }

        // Carte récapitulative inférieure (Y: 62 à 105)
        val cardY = 62
        guiGraphics.fill(4, cardY, WIDTH - 4, HEIGHT - 5, 0xFF181920.toInt())
        guiGraphics.fill(4, cardY - 1, WIDTH - 4, cardY, 0xFF2F3240.toInt())

        val line1 = Component.translatable(
            "gui.cobblemon_jei.category.pokemon_interaction.how_to",
            if (hasTool) recipe.requiredTools.first().hoverName.string else "-"
        ).string
        guiGraphics.drawString(font, font.plainSubstrByWidth(line1, WIDTH - 16), 8, cardY + 4, 0xFFFFBE76.toInt(), false)

        val line2 = if (recipe.cooldownSeconds > 0) {
            Component.translatable("gui.cobblemon_jei.category.pokemon_interaction.cooldown", recipe.cooldownSeconds).string
        } else {
            Component.translatable("gui.cobblemon_jei.category.pokemon_interaction.instant").string
        }
        guiGraphics.drawString(font, font.plainSubstrByWidth(line2, WIDTH - 16), 8, cardY + 16, 0xFFAAAAAA.toInt(), false)

        if (recipe.conditions.isNotEmpty()) {
            val condText = recipe.conditions.joinToString(" • ") { Component.translatable(it).string }
            val line3 = Component.translatable("gui.cobblemon_jei.category.pokemon_interaction.condition", condText).string
            guiGraphics.drawString(font, font.plainSubstrByWidth(line3, WIDTH - 16), 8, cardY + 28, 0xFFFF7675.toInt(), false)
        }
    }

    private fun drawSlotBorder(guiGraphics: GuiGraphics, x: Int, y: Int, borderColor: Int) {
        guiGraphics.fill(x, y, x + 18, y + 1, borderColor)
        guiGraphics.fill(x, y + 17, x + 18, y + 18, borderColor)
        guiGraphics.fill(x, y + 1, x + 1, y + 17, borderColor)
        guiGraphics.fill(x + 17, y + 1, x + 18, y + 17, borderColor)
    }
}
