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
        const val HEIGHT = 104
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
        builder.addSlot(RecipeIngredientRole.INPUT, 16, 38)
            .addIngredient(PokemonSpeciesIngredient.TYPE, recipe.pokemonIngredient)

        // Slot 2 : Outil requis en INPUT ou Bloc de Pâturage en CATALYST
        if (recipe.requiredTools.isNotEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 58, 38)
                .addIngredients(VanillaTypes.ITEM_STACK, recipe.requiredTools)
        } else {
            val pastureStack = try { ItemStack(CobblemonBlocks.PASTURE) } catch (_: Throwable) { ItemStack(Items.HAY_BLOCK) }
            builder.addSlot(RecipeIngredientRole.CATALYST, 58, 38)
                .addItemStack(pastureStack)
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

        // 2. Bandeau supérieur (En-tête)
        guiGraphics.fill(2, 2, WIDTH - 2, 24, 0xFF242630.toInt())

        // Numéro de Pokédex et nom du Pokémon
        val dexStr = String.format("#%04d", recipe.species.nationalPokedexNumber)
        val nameStr = recipe.species.translatedName.string
        val headerTitle = "$dexStr $nameStr"
        guiGraphics.drawString(font, headerTitle, 6, 6, 0xFFFFFFFF.toInt(), false)

        // Badge de type d'action à droite de l'en-tête
        val badgeColor = recipe.harvestType.badgeColor
        val actionText = recipe.actionName
        val actionW = font.width(actionText)
        val badgeX = WIDTH - actionW - 10
        guiGraphics.fill(badgeX - 4, 4, WIDTH - 6, 18, 0xFF101216.toInt())
        guiGraphics.drawString(font, actionText, badgeX - 2, 7, badgeColor, false)

        // 3. Cadre des slots
        // Slot Pokémon (15, 37 à 34, 56)
        drawSlotBorder(guiGraphics, 15, 37, 0xFF3D4050.toInt())

        // Slot Outil / Pâturage (57, 37 à 76, 56)
        val toolBorderColor = if (recipe.requiredTools.isNotEmpty()) 0xFFE1B12C.toInt() else 0xFF3D4050.toInt()
        drawSlotBorder(guiGraphics, 57, 37, toolBorderColor)

        // Libellés au-dessus des slots d'entrée
        guiGraphics.drawString(font, "Pokémon", 12, 27, 0xFF999999.toInt(), false)
        val toolLabel = if (recipe.requiredTools.isNotEmpty()) "Outil" else "Pâturage"
        guiGraphics.drawString(font, toolLabel, 55, 27, 0xFF999999.toInt(), false)

        // Flèche centrale stylisée vers la droite (X: 79 à 99, Y: 46)
        guiGraphics.fill(80, 46, 97, 48, 0xFF54A0FF.toInt())
        guiGraphics.fill(95, 44, 99, 50, 0xFF54A0FF.toInt())
        guiGraphics.fill(98, 45, 101, 49, 0xFF54A0FF.toInt())

        // Libellé au-dessus des sorties
        guiGraphics.drawString(font, "Butins", 108, 27, 0xFF999999.toInt(), false)

        // Cadres pour les slots de sortie
        val outputCount = recipe.outputs.size
        for (i in 0 until outputCount) {
            val (slotX, slotY) = calculateOutputSlotPosition(i, outputCount)
            drawSlotBorder(guiGraphics, slotX - 1, slotY - 1, 0xFF2ED573.toInt())
        }

        // 4. Carte récapitulative inférieure (Y: 72 à 98)
        val cardY = 72
        guiGraphics.fill(4, cardY, WIDTH - 4, HEIGHT - 5, 0xFF181920.toInt())
        guiGraphics.fill(4, cardY - 1, WIDTH - 4, cardY, 0xFF2F3240.toInt())

        // Ligne 1 : Résumé de l'action
        if (recipe.harvestType == PastureHarvestType.INTERACTION) {
            val toolNames = if (recipe.requiredTools.isNotEmpty()) {
                recipe.requiredTools.joinToString(", ") { it.hoverName.string }
            } else "Clic droit"
            val line1 = "Action : ${recipe.actionName} avec $toolNames"
            val truncated1 = font.plainSubstrByWidth(line1, WIDTH - 16)
            guiGraphics.drawString(font, truncated1, 8, cardY + 5, 0xFFFFBE76.toInt(), false)
        } else {
            val line1 = "Mode : Production continue au pâturage"
            guiGraphics.drawString(font, line1, 8, cardY + 5, 0xFF7BED9F.toInt(), false)
        }

        // Ligne 2 : Détails / Conditions
        val line2 = when {
            recipe.conditions.isNotEmpty() -> "Condition : ${recipe.conditions.joinToString(" • ")}"
            recipe.harvestType == PastureHarvestType.INTERACTION -> "Interaction directe sur le Pokémon placé en enclos."
            else -> "Loot lâché périodiquement lorsque le Pokémon paît."
        }
        val truncated2 = font.plainSubstrByWidth(line2, WIDTH - 16)
        guiGraphics.drawString(font, truncated2, 8, cardY + 16, 0xFFAAAAAA.toInt(), false)
    }

    override fun getTooltipStrings(
        recipe: PokemonPastureRecipe,
        recipeSlotsView: IRecipeSlotsView,
        mouseX: Double,
        mouseY: Double
    ): List<Component> {
        val tooltips = mutableListOf<Component>()

        // Tooltip sur la zone d'en-tête ou de description
        if (mouseY in 72.0..(HEIGHT - 5.0) && mouseX in 4.0..(WIDTH - 4.0)) {
            val title = recipe.harvestType.tooltipTitle
            tooltips.add(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            tooltips.add(Component.literal("• Action : ${recipe.actionName}").withStyle(ChatFormatting.AQUA))

            if (recipe.requiredTools.isNotEmpty()) {
                val names = recipe.requiredTools.joinToString(", ") { it.hoverName.string }
                tooltips.add(Component.literal("• Outil requis : $names").withStyle(ChatFormatting.YELLOW))
            } else {
                tooltips.add(Component.literal("• Aucun outil requis (Production automatique)").withStyle(ChatFormatting.GRAY))
            }

            for (cond in recipe.conditions) {
                tooltips.add(Component.literal("• Condition : $cond").withStyle(ChatFormatting.RED))
            }

            recipe.notes?.let {
                tooltips.add(Component.literal("• Note : $it").withStyle(ChatFormatting.DARK_GRAY))
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
            total <= 1 -> Pair(126, 38)
            total == 2 -> Pair(114 + index * 24, 38)
            total <= 4 -> {
                val col = index % 2
                val row = index / 2
                Pair(114 + col * 24, 28 + row * 22)
            }
            total <= 6 -> {
                val col = index % 3
                val row = index / 3
                Pair(106 + col * 22, 28 + row * 22)
            }
            else -> {
                val col = index % 3
                val row = (index / 3).coerceAtMost(1)
                Pair(106 + col * 22, 28 + row * 22)
            }
        }
    }
}
