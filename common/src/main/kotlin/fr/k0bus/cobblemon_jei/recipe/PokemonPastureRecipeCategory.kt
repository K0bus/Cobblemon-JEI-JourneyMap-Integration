package fr.k0bus.cobblemon_jei.recipe

import fr.k0bus.cobblemon_jei.ingredient.PokemonSpeciesIngredient
import com.cobblemon.mod.common.CobblemonBlocks
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
 * Catégorie JEI pour la production de ressources au pâturage dans Cobblemon.
 * Affiche le Pokémon requis, l'outil nécessaire (ou le bloc de pâturage),
 * ainsi que l'ensemble des butins et ressources générés (quantités et pourcentages).
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class PokemonPastureRecipeCategory(
    private val guiHelper: IGuiHelper
) : IRecipeCategory<PokemonPastureRecipe> {

    companion object {
        val RECIPE_TYPE: RecipeType<PokemonPastureRecipe> = RecipeType.create(
            "cobblemon_jei",
            "pokemon_pasture",
            PokemonPastureRecipe::class.java
        )

        const val WIDTH = 176
        const val HEIGHT = 128
    }

    override fun getRecipeType(): RecipeType<PokemonPastureRecipe> = RECIPE_TYPE

    override fun getTitle(): Component {
        return Component.translatable("gui.cobblemon_jei.category.pokemon_pasture")
    }

    override fun getBackground(): IDrawable {
        return guiHelper.createBlankDrawable(WIDTH, HEIGHT)
    }

    override fun getIcon(): IDrawable {
        val stack = try {
            ItemStack(CobblemonBlocks.PASTURE)
        } catch (_: Throwable) {
            ItemStack(Items.SHEARS)
        }
        return guiHelper.createDrawableItemStack(stack)
    }

    override fun setRecipe(
        builder: IRecipeLayoutBuilder,
        recipe: PokemonPastureRecipe,
        focuses: IFocusGroup
    ) {
        // Slot 1 : Pokémon en INPUT (recherchable via 'U' sur le Pokémon)
        builder.addSlot(RecipeIngredientRole.INPUT, 12, 34)
            .addIngredient(PokemonSpeciesIngredient.TYPE, recipe.pokemonIngredient)

        // Slot 2 : Nourriture acceptée en INPUT (recherchable via 'U' sur la nourriture)
        val foodSlot = builder.addSlot(RecipeIngredientRole.INPUT, 38, 34)
            .addIngredients(VanillaTypes.ITEM_STACK, recipe.foodItems)

        foodSlot.addTooltipCallback { _, tooltip ->
            tooltip.add(
                Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.food_title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            )
            tooltip.add(
                Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.food_desc")
                    .withStyle(ChatFormatting.YELLOW)
            )
            if (recipe.specificFoodNames.isNotEmpty()) {
                tooltip.add(
                    Component.translatable(
                        "gui.cobblemon_jei.category.pokemon_pasture.food_specific",
                        recipe.specificFoodNames.joinToString(", ")
                    ).withStyle(ChatFormatting.AQUA)
                )
            }
            tooltip.add(
                Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.food_universal")
                    .withStyle(ChatFormatting.GRAY)
            )
        }

        // Slot 3 : Outil requis en INPUT ou Bloc de Pâturage en CATALYST
        if (recipe.requiredTools.isNotEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 64, 34)
                .addIngredients(VanillaTypes.ITEM_STACK, recipe.requiredTools)
        } else {
            val pastureStack = try { ItemStack(CobblemonBlocks.PASTURE) } catch (_: Throwable) { ItemStack(Items.HAY_BLOCK) }
            val pastureSlot = builder.addSlot(RecipeIngredientRole.CATALYST, 64, 34)
                .addItemStack(pastureStack)
            pastureSlot.addTooltipCallback { _, tooltip ->
                tooltip.add(
                    Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.tooltip.pasture_block")
                        .withStyle(ChatFormatting.AQUA)
                )
            }
        }

        // Slots OUTPUT : Ressources produites (recherchables via 'R' sur les items)
        val outputs = recipe.outputs
        val outputCount = outputs.size

        for (i in outputs.indices) {
            val dropItem = outputs[i]
            val (slotX, slotY) = calculateOutputSlotPosition(i, outputCount)

            val slotBuilder = builder.addSlot(RecipeIngredientRole.OUTPUT, slotX, slotY)
                .addItemStack(dropItem.itemStack)

            // Infobulle détaillée sur chaque slot de butin
            slotBuilder.addTooltipCallback { _, tooltip ->
                tooltip.add(
                    Component.translatable(
                        "gui.cobblemon_jei.category.pokemon_pasture.amount",
                        dropItem.quantityDisplay
                    ).withStyle(ChatFormatting.GOLD)
                )
                tooltip.add(
                    Component.translatable(
                        "gui.cobblemon_jei.category.pokemon_pasture.chance",
                        dropItem.chanceDisplay
                    ).withStyle(ChatFormatting.AQUA)
                )
                for (cond in dropItem.conditions) {
                    tooltip.add(Component.literal("• $cond").withStyle(ChatFormatting.GRAY))
                }
            }
        }
    }

    override fun draw(
        recipe: PokemonPastureRecipe,
        recipeSlotsView: IRecipeSlotsView,
        guiGraphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        val font = Minecraft.getInstance().font

        // 1. Fond général élégant thème sombre
        guiGraphics.fill(0, 0, WIDTH, HEIGHT, 0xFF141519.toInt())
        guiGraphics.fill(1, 1, WIDTH - 1, HEIGHT - 1, 0xFF1C1E24.toInt())

        // 2. Bandeau supérieur (En-tête Y: 2 à 20)
        guiGraphics.fill(2, 2, WIDTH - 2, 20, 0xFF242630.toInt())

        // Badge de type d'action à droite (court : "Passif" / "Manuel")
        val badgeText = recipe.harvestType.getBadgeComponent().string
        val badgeW = font.width(badgeText)
        val badgeX = WIDTH - badgeW - 10
        guiGraphics.fill(badgeX - 4, 3, WIDTH - 4, 18, 0xFF101216.toInt())
        guiGraphics.drawString(font, badgeText, badgeX - 1, 6, recipe.harvestType.badgeColor, false)

        // Numéro de Pokédex et nom du Pokémon à gauche (tronqué proprement avant le badge)
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

        // 3. Libellés au-dessus des slots (Y: 23)
        val inputsLabel = Component.translatable("gui.cobblemon_jei.label.inputs").string
        val inputsLabelW = font.width(inputsLabel)
        guiGraphics.drawString(font, inputsLabel, 47 - inputsLabelW / 2, 23, 0xFF999999.toInt(), false)

        val dropsLabel = Component.translatable("gui.cobblemon_jei.label.drops").string
        val dropsLabelW = font.width(dropsLabel)
        guiGraphics.drawString(font, dropsLabel, 138 - dropsLabelW / 2, 23, 0xFF999999.toInt(), false)

        // Cadres des slots d'entrée
        drawSlotBorder(guiGraphics, 11, 33, 0xFF3D4050.toInt())
        drawSlotBorder(guiGraphics, 37, 33, 0xFFFFA502.toInt())
        val toolBorderColor = if (recipe.requiredTools.isNotEmpty()) 0xFFE1B12C.toInt() else 0xFF3D4050.toInt()
        drawSlotBorder(guiGraphics, 63, 33, toolBorderColor)

        // Flèche centrale stylisée vers la droite (X: 85 à 101, Y: 42)
        guiGraphics.fill(85, 42, 97, 44, 0xFF54A0FF.toInt())
        guiGraphics.fill(95, 40, 99, 46, 0xFF54A0FF.toInt())
        guiGraphics.fill(98, 41, 101, 45, 0xFF54A0FF.toInt())

        // Cadres pour les slots de sortie
        val outputCount = recipe.outputs.size
        for (i in 0 until outputCount) {
            val (slotX, slotY) = calculateOutputSlotPosition(i, outputCount)
            drawSlotBorder(guiGraphics, slotX - 1, slotY - 1, 0xFF2ED573.toInt())
        }

        // 4. Carte récapitulative inférieure (Y: 76 à 122)
        val cardY = 76
        guiGraphics.fill(4, cardY, WIDTH - 4, HEIGHT - 5, 0xFF181920.toInt())
        guiGraphics.fill(4, cardY - 1, WIDTH - 4, cardY, 0xFF2F3240.toInt())

        if (recipe.harvestType == PastureHarvestType.INTERACTION) {
            val actionStr = recipe.actionName.string
            val line1 = Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.action_label", actionStr).string
            guiGraphics.drawString(font, font.plainSubstrByWidth(line1, WIDTH - 16), 8, cardY + 4, 0xFFFFBE76.toInt(), false)

            val toolNames = if (recipe.requiredTools.isNotEmpty()) {
                recipe.requiredTools.joinToString(", ") { it.hoverName.string }
            } else "-"
            val line2 = Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.tool_required", toolNames).string
            guiGraphics.drawString(font, font.plainSubstrByWidth(line2, WIDTH - 16), 8, cardY + 14, 0xFFE0E0E0.toInt(), false)

            val line3 = if (recipe.estimatedMinutes > 0.0) {
                Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.interval", recipe.displayIntervalMinutes).string
            } else {
                Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.interaction_direct").string
            }
            guiGraphics.drawString(font, font.plainSubstrByWidth(line3, WIDTH - 16), 8, cardY + 24, 0xFFAAAAAA.toInt(), false)

            val line4 = Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.food_line").string
            guiGraphics.drawString(font, font.plainSubstrByWidth(line4, WIDTH - 16), 8, cardY + 34, 0xFFFFA502.toInt(), false)
        } else {
            val line1 = Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.mode_passive").string
            guiGraphics.drawString(font, font.plainSubstrByWidth(line1, WIDTH - 16), 8, cardY + 4, 0xFF7BED9F.toInt(), false)

            val line2 = Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.interval", recipe.displayIntervalMinutes).string
            guiGraphics.drawString(font, font.plainSubstrByWidth(line2, WIDTH - 16), 8, cardY + 14, 0xFFFFD32A.toInt(), false)

            val line3 = if (recipe.conditions.isNotEmpty()) {
                Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.condition", recipe.conditions.joinToString(" • ")).string
            } else {
                Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.hint_hover").string
            }
            guiGraphics.drawString(font, font.plainSubstrByWidth(line3, WIDTH - 16), 8, cardY + 24, 0xFF888888.toInt(), false)

            val line4 = Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.food_line").string
            guiGraphics.drawString(font, font.plainSubstrByWidth(line4, WIDTH - 16), 8, cardY + 34, 0xFFFFA502.toInt(), false)
        }
    }

    override fun getTooltipStrings(
        recipe: PokemonPastureRecipe,
        recipeSlotsView: IRecipeSlotsView,
        mouseX: Double,
        mouseY: Double
    ): List<Component> {
        val tooltips = mutableListOf<Component>()

        // Tooltip sur la zone inférieure récapitulative
        if (mouseY in 76.0..(HEIGHT - 5.0) && mouseX in 4.0..(WIDTH - 4.0)) {
            val titleComp = recipe.harvestType.getTooltipTitleComponent()
            tooltips.add(titleComp.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            tooltips.add(
                Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.tooltip.action", recipe.actionName)
                    .withStyle(ChatFormatting.AQUA)
            )

            if (recipe.harvestType == PastureHarvestType.PASSIVE_DROP || recipe.estimatedMinutes > 0.0) {
                tooltips.add(
                    Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.tooltip.interval", recipe.displayIntervalMinutes)
                        .withStyle(ChatFormatting.GREEN)
                )
            }

            if (recipe.requiredTools.isNotEmpty()) {
                val names = recipe.requiredTools.joinToString(", ") { it.hoverName.string }
                tooltips.add(
                    Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.tooltip.tool", names)
                        .withStyle(ChatFormatting.YELLOW)
                )
            } else {
                tooltips.add(
                    Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.tooltip.no_tool")
                        .withStyle(ChatFormatting.GRAY)
                )
            }

            for (cond in recipe.conditions) {
                tooltips.add(
                    Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.tooltip.condition", cond)
                        .withStyle(ChatFormatting.RED)
                )
            }

            val foodLine = Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.food_line")
            tooltips.add(foodLine.copy().withStyle(ChatFormatting.GOLD))

            if (recipe.specificFoodNames.isNotEmpty()) {
                tooltips.add(
                    Component.translatable(
                        "gui.cobblemon_jei.category.pokemon_pasture.food_specific",
                        recipe.specificFoodNames.joinToString(", ")
                    ).withStyle(ChatFormatting.AQUA)
                )
            }

            recipe.notes?.let {
                tooltips.add(
                    Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.tooltip.note", it)
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
            }
        }

        return tooltips
    }

    private fun drawSlotBorder(guiGraphics: GuiGraphics, x: Int, y: Int, borderColor: Int) {
        guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF0E0F12.toInt())
        guiGraphics.fill(x, y, x + 18, y + 1, borderColor)
        guiGraphics.fill(x, y + 17, x + 18, y + 18, borderColor)
        guiGraphics.fill(x, y, x + 1, y + 18, borderColor)
        guiGraphics.fill(x + 17, y, x + 18, y + 18, borderColor)
    }

    private fun calculateOutputSlotPosition(index: Int, total: Int): Pair<Int, Int> {
        return when {
            total <= 1 -> Pair(136, 34)
            total == 2 -> Pair(122 + index * 24, 34)
            total <= 4 -> {
                val col = index % 2
                val row = index / 2
                Pair(122 + col * 24, 25 + row * 22)
            }
            total <= 6 -> {
                val col = index % 3
                val row = index / 3
                Pair(110 + col * 22, 25 + row * 22)
            }
            else -> {
                val col = index % 3
                val row = (index / 3).coerceAtMost(1)
                Pair(110 + col * 22, 25 + row * 22)
            }
        }
    }
}
