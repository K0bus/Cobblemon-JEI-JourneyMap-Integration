package fr.k0bus.cobblemon_jei.ingredient

import mezz.jei.api.ingredients.IIngredientRenderer
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag

/**
 * Implémentation de [IIngredientRenderer] pour les Pokémon dans la grille d'ingrédients de JEI.
 * Affiche le sprite/modèle 2D propre du Pokémon et son tooltip complet (nom, numéro national, types).
 */
class PokemonIngredientRenderer : IIngredientRenderer<PokemonSpeciesIngredient> {

    override fun render(guiGraphics: GuiGraphics, ingredient: PokemonSpeciesIngredient) {
        val stack = ingredient.itemStack
        if (!stack.isEmpty) {
            guiGraphics.renderItem(stack, 0, 0)
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0)
        } else {
            // Rendu de secours élégant au cas où le modèle ou l'itemStack ne peut pas être généré
            renderFallbackIcon(guiGraphics, ingredient)
        }
    }

    override fun getTooltip(
        ingredient: PokemonSpeciesIngredient,
        tooltipFlag: TooltipFlag
    ): List<Component> {
        val tooltip = mutableListOf<Component>()

        // 1. Titre : [#0025] Pikachu (Alola)
        val dexPrefix = String.format("#%04d ", ingredient.species.nationalPokedexNumber)
        val title = Component.literal(dexPrefix)
            .withStyle(ChatFormatting.GOLD)
            .append(ingredient.species.translatedName.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE))

        if (ingredient.form.name.isNotBlank() && ingredient.form.name != "normal") {
            val formName = ingredient.form.name.replaceFirstChar { it.uppercase() }
            title.append(Component.literal(" ($formName)").withStyle(ChatFormatting.GRAY))
        }
        tooltip.add(title)

        // 2. Types élémentaires avec code couleur
        val typeLine = Component.literal("Type: ").withStyle(ChatFormatting.DARK_GRAY)
            .append(ingredient.primaryType.displayName.copy().withStyle(getTypeColor(ingredient.primaryType.name)))

        val sec = ingredient.secondaryType
        if (sec != null) {
            typeLine.append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
                .append(sec.displayName.copy().withStyle(getTypeColor(sec.name)))
        }
        tooltip.add(typeLine)

        // 3. Génération
        tooltip.add(
            Component.literal("Génération ${ingredient.generation}")
                .withStyle(ChatFormatting.DARK_AQUA)
        )

        // 4. Informations avancées (si F3+H activé)
        if (tooltipFlag.isAdvanced) {
            tooltip.add(
                Component.literal("ID: ${ingredient.species.resourceIdentifier}")
                    .withStyle(ChatFormatting.DARK_GRAY)
            )
            if (ingredient.form.aspects.isNotEmpty()) {
                val aspectsStr = ingredient.form.aspects.joinToString(", ")
                tooltip.add(
                    Component.literal("Aspects: $aspectsStr")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
            }
        }

        return tooltip
    }

    override fun getWidth(): Int = 16
    override fun getHeight(): Int = 16

    /**
     * Dessine une icône stylisée 16x16 aux couleurs du type élémentaire si le modèle échoue.
     */
    private fun renderFallbackIcon(guiGraphics: GuiGraphics, ingredient: PokemonSpeciesIngredient) {
        val primaryColor = (0xFF000000.toLong() or (ingredient.primaryType.hue.toLong() and 0xFFFFFFL)).toInt()
        val secondaryColor = ingredient.secondaryType?.let {
            (0xFF000000.toLong() or (it.hue.toLong() and 0xFFFFFFL)).toInt()
        } ?: primaryColor

        // Bordure
        guiGraphics.fill(0, 0, 16, 16, 0xFF1E1E24.toInt())
        guiGraphics.fill(1, 1, 15, 15, 0xFF2F3542.toInt())

        // Demi-cercles / motif supérieur et inférieur
        guiGraphics.fill(2, 2, 14, 8, primaryColor)
        guiGraphics.fill(2, 8, 14, 14, secondaryColor)

        // Centre Poké Ball
        guiGraphics.fill(6, 6, 10, 10, 0xFFFFFFFF.toInt())
        guiGraphics.fill(7, 7, 9, 9, 0xFF2F3542.toInt())
    }

    private fun getTypeColor(typeName: String): ChatFormatting {
        return when (typeName.lowercase()) {
            "fire" -> ChatFormatting.RED
            "water" -> ChatFormatting.BLUE
            "grass" -> ChatFormatting.GREEN
            "electric" -> ChatFormatting.YELLOW
            "ice" -> ChatFormatting.AQUA
            "fighting" -> ChatFormatting.DARK_RED
            "poison" -> ChatFormatting.DARK_PURPLE
            "ground" -> ChatFormatting.GOLD
            "flying" -> ChatFormatting.LIGHT_PURPLE
            "psychic" -> ChatFormatting.LIGHT_PURPLE
            "bug" -> ChatFormatting.DARK_GREEN
            "rock" -> ChatFormatting.GRAY
            "ghost" -> ChatFormatting.DARK_BLUE
            "dragon" -> ChatFormatting.DARK_AQUA
            "dark" -> ChatFormatting.BLACK
            "steel" -> ChatFormatting.GRAY
            "fairy" -> ChatFormatting.LIGHT_PURPLE
            else -> ChatFormatting.WHITE
        }
    }
}
