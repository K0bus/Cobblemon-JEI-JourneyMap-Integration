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
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Catégorie JEI pour les métiers de travail au pâturage du mod Cobbleworkers.
 * Affiche le travail effectué par les Pokémon assignés au bloc de pâturage :
 * - Les Pokémon éligibles (par type, talent, move ou espèce)
 * - Le bloc ou équipement cible dans l'enclos
 * - Les ressources produites et le temps de recharge (cooldown).
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
        const val HEIGHT = 118
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
            ItemStack(Items.HAY_BLOCK)
        }
        return guiHelper.createDrawableItemStack(stack)
    }

    override fun setRecipe(
        builder: IRecipeLayoutBuilder,
        recipe: PokemonPastureRecipe,
        focuses: IFocusGroup
    ) {
        val pokeSlotX = 22
        val targetSlotX = 52

        // Slot 1 : Pokémon éligibles en INPUT (recherchable via 'U' sur le Pokémon)
        val pokeSlot = builder.addSlot(RecipeIngredientRole.INPUT, pokeSlotX, 32)
        for (poke in recipe.eligiblePokemon) {
            pokeSlot.addIngredient(PokemonSpeciesIngredient.TYPE, poke)
        }

        // Slot 2 : Cible / Bloc requis dans l'enclos en INPUT
        builder.addSlot(RecipeIngredientRole.INPUT, targetSlotX, 32)
            .addIngredients(VanillaTypes.ITEM_STACK, recipe.targetInputs)

        // Slot 3+ : Objets produits en OUTPUT (recherchables via 'R' sur les items)
        val outputs = recipe.outputs
        val outputCount = outputs.size

        for (i in outputs.indices) {
            val (slotX, slotY) = calculateOutputSlotPosition(i, outputCount)
            val slotStacks = outputs[i]
            if (slotStacks.isNotEmpty()) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, slotX, slotY)
                    .addItemStacks(slotStacks)
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

        // 1. Fond général thème sombre
        guiGraphics.fill(0, 0, WIDTH, HEIGHT, 0xFF141519.toInt())
        guiGraphics.fill(1, 1, WIDTH - 1, HEIGHT - 1, 0xFF1C1E24.toInt())

        // 2. Bandeau supérieur (Y: 2 à 20)
        guiGraphics.fill(2, 2, WIDTH - 2, 20, 0xFF242630.toInt())

        // Badge de cooldown à droite (ex: "90s" ou "Continu")
        val badgeText = if (recipe.isContinuous) {
            Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.continuous").string
        } else {
            "${recipe.cooldownSeconds}s"
        }
        val badgeW = font.width(badgeText)
        val badgeX = WIDTH - badgeW - 10
        val badgeColor = if (recipe.isContinuous) 0xFF2ED573.toInt() else 0xFFFFA502.toInt()
        guiGraphics.fill(badgeX - 4, 3, WIDTH - 4, 18, 0xFF101216.toInt())
        guiGraphics.drawString(font, badgeText, badgeX - 1, 6, badgeColor, false)

        // Nom du métier à gauche
        val titleText = recipe.jobTitle.string
        val maxTitleWidth = badgeX - 12
        val displayTitle = if (font.width(titleText) > maxTitleWidth) {
            font.plainSubstrByWidth(titleText, maxTitleWidth - 6) + "…"
        } else {
            titleText
        }
        guiGraphics.drawString(font, displayTitle, 6, 6, 0xFFFFFFFF.toInt(), false)

        // 3. Cadres des slots
        val pokeSlotX = 22
        val targetSlotX = 52

        drawSlotBorder(guiGraphics, pokeSlotX - 1, 31, 0xFF3D4050.toInt())
        drawSlotBorder(guiGraphics, targetSlotX - 1, 31, 0xFFE1B12C.toInt())

        // Flèche centrale stylisée vers la droite (X: 79 à 95, Y: 40)
        guiGraphics.fill(79, 40, 91, 42, 0xFF54A0FF.toInt())
        guiGraphics.fill(89, 38, 93, 44, 0xFF54A0FF.toInt())
        guiGraphics.fill(92, 39, 95, 43, 0xFF54A0FF.toInt())

        // Cadres pour les slots de sortie
        val outputCount = recipe.outputs.size
        for (i in 0 until outputCount) {
            val (slotX, slotY) = calculateOutputSlotPosition(i, outputCount)
            drawSlotBorder(guiGraphics, slotX - 1, slotY - 1, 0xFF2ED573.toInt())
        }

        // 4. Carte récapitulative inférieure (Y: 66 à 113)
        val cardY = 66
        guiGraphics.fill(4, cardY, WIDTH - 4, HEIGHT - 5, 0xFF181920.toInt())
        guiGraphics.fill(4, cardY - 1, WIDTH - 4, cardY, 0xFF2F3240.toInt())

        // Ligne 1 : Prérequis du métier
        val line1 = Component.translatable(
            "gui.cobblemon_jei.category.pokemon_pasture.requirements",
            recipe.requirementsDescription
        ).string
        guiGraphics.drawString(font, font.plainSubstrByWidth(line1, WIDTH - 16), 8, cardY + 4, 0xFFFFBE76.toInt(), false)

        // Ligne 2 : Description de l'action
        val line2 = recipe.actionDescription.string
        guiGraphics.drawString(font, font.plainSubstrByWidth(line2, WIDTH - 16), 8, cardY + 16, 0xFFE0E0E0.toInt(), false)

        // Ligne 3 : Temps de recharge et note
        val line3 = if (recipe.isContinuous) {
            Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.desc_continuous").string
        } else {
            Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.desc_cooldown", recipe.cooldownSeconds).string
        }
        guiGraphics.drawString(font, font.plainSubstrByWidth(line3, WIDTH - 16), 8, cardY + 28, 0xFFAAAAAA.toInt(), false)

        // Ligne 4 : Mention mod
        val line4 = Component.translatable("gui.cobblemon_jei.category.pokemon_pasture.mod_footer").string
        guiGraphics.drawString(font, font.plainSubstrByWidth(line4, WIDTH - 16), 8, cardY + 38, 0xFF747D8C.toInt(), false)
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
            total <= 1 -> Pair(126, 32)
            total == 2 -> Pair(115 + index * 24, 32)
            total <= 4 -> {
                val col = index % 2
                val row = index / 2
                Pair(115 + col * 24, 23 + row * 20)
            }
            total <= 6 -> {
                val col = index % 3
                val row = index / 3
                Pair(106 + col * 20, 23 + row * 20)
            }
            else -> {
                val col = index % 4
                val row = (index / 4).coerceAtMost(1)
                Pair(102 + col * 18, 23 + row * 20)
            }
        }
    }
}
