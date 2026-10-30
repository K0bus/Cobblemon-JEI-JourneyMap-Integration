package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.journeymap.CobblemonOverlayManager
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack
import kotlin.math.max

data class FormStatusInfo(val text: String, val color: Int)

/**
 * Utilitaires partagés pour le rendu graphique (DA sombre, scrollbars, jauges, statuts).
 */
object PokedexRenderHelper {

    fun getElementColor(typeName: String): Int =
        CobblemonOverlayManager.getElementColor(typeName)

    fun getFormDisplayName(form: FormData?, sp: Species): String =
        PokemonTrackerService.getFormDisplayName(form, sp)

    fun getSpeciesForms(sp: Species): List<FormData> {
        val allForms = mutableListOf<FormData>()
        allForms.add(sp.standardForm)
        for (f in sp.forms) {
            if (f != sp.standardForm && allForms.none { it.name.equals(f.name, ignoreCase = true) }) {
                allForms.add(f)
            }
        }
        return allForms
    }

    fun getFormStatusInfo(sp: Species, form: FormData, isShiny: Boolean, currentTracked: TrackedPokemon?): FormStatusInfo {
        val formRecord = try {
            val specRecord = CobblemonClient.clientPokedexData.speciesRecords[sp.resourceIdentifier]
            specRecord?.getFormRecord(form.name.lowercase().ifBlank { "normal" })
                ?: specRecord?.getFormRecord(form.name.lowercase())
                ?: specRecord?.getFormRecord("")
        } catch (_: Throwable) { null }

        return if (isShiny) {
            val ownedShinyCount = currentTracked?.ownedInstances?.count {
                it.pokemon.shiny && PokemonTrackerService.isFormMatch(it.pokemon, form, sp)
            } ?: 0
            val isDexShinyCaught = formRecord?.hasSeenShinyState(true) == true && (formRecord.knowledge == PokedexEntryProgress.CAUGHT)
            val isDexShinySeen = formRecord?.hasSeenShinyState(true) == true

            when {
                ownedShinyCount > 0 -> FormStatusInfo("§6★ Possédé ($ownedShinyCount)§r", 0xFFF1C40F.toInt())
                isDexShinyCaught -> FormStatusInfo("§6★ Capturé Shiny§r", 0xFFF1C40F.toInt())
                isDexShinySeen -> FormStatusInfo("§e👁 Vu Shiny (Pokédex)§r", 0xFFE67E22.toInt())
                else -> FormStatusInfo("§c✕ Non découvert Shiny§r", 0xFFE74C3C.toInt())
            }
        } else {
            val ownedCount = currentTracked?.ownedInstances?.count { PokemonTrackerService.isFormMatch(it.pokemon, form, sp) } ?: 0
            val isDexCaught = formRecord?.knowledge == PokedexEntryProgress.CAUGHT
            val isDexSeen = formRecord?.knowledge == PokedexEntryProgress.ENCOUNTERED

            when {
                ownedCount > 0 -> FormStatusInfo("§a✔ Possédé ($ownedCount)§r", 0xFF2ECC71.toInt())
                isDexCaught -> FormStatusInfo("§a✔ Capturé§r", 0xFF2ECC71.toInt())
                isDexSeen -> FormStatusInfo("§6👁 Vu (Pokédex)§r", 0xFFE67E22.toInt())
                else -> FormStatusInfo("§c✕ Non découvert§r", 0xFFE74C3C.toInt())
            }
        }
    }

    fun drawScrollbar(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, scrollOffset: Double, maxScroll: Double, isDragging: Boolean) {
        if (maxScroll <= 0) return
        guiGraphics.fill(x, y, x + w, y + h, 0x44000000)
        val thumbH = max(16, (h * (h / (h + maxScroll))).toInt())
        val thumbY = y + ((h - thumbH) * (scrollOffset / maxScroll)).toInt()
        val thumbColor = if (isDragging) 0xFF54A0FF.toInt() else 0xFF353C4D.toInt()
        guiGraphics.fill(x, thumbY, x + w, thumbY + thumbH, thumbColor)
    }

    fun truncateString(font: Font, str: String, maxW: Int): String {
        if (font.width(str) <= maxW) return str
        var sub = str
        while (sub.isNotEmpty() && font.width("$sub…") > maxW) {
            sub = sub.dropLast(1)
        }
        return if (sub.isEmpty()) "…" else "$sub…"
    }

    fun renderBottomProgressBar(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        width: Int,
        height: Int,
        stats: PokedexTrackerStats,
        font: Font
    ) {
        val total = max(1, stats.totalSpecies)
        val caught = stats.caughtCount
        val seenOnly = stats.encounteredCount
        val totalSeen = caught + seenOnly

        val caughtPct = (caught.toDouble() / total) * 100.0
        val seenOnlyPct = (seenOnly.toDouble() / total) * 100.0
        val totalSeenPct = (totalSeen.toDouble() / total) * 100.0
        val missingPct = (stats.missingCount.toDouble() / total) * 100.0

        val footerY = height - 23
        val barX = 14
        val barW = max(50, width - 28)
        val barH = 5
        val barY = footerY + 15
        val textY = footerY + 4

        // 1. Fond et bordure du bandeau inférieur
        guiGraphics.fill(0, footerY, width, height, 0xFF14171E.toInt())
        guiGraphics.fill(0, footerY, width, footerY + 1, 0xFF282D3C.toInt())

        // 2. Chiffres & icônes sans texte superflu
        var curX = 14

        // Icône Pokéball + Valeur Capturé (Vert)
        val pokeStack = try {
            ItemStack(com.cobblemon.mod.common.CobblemonItems.POKE_BALL)
        } catch (_: Throwable) {
            ItemStack.EMPTY
        }

        if (!pokeStack.isEmpty) {
            val pose = guiGraphics.pose()
            pose.pushPose()
            pose.translate(curX.toFloat(), (textY - 2).toFloat(), 0f)
            pose.scale(0.68f, 0.68f, 1.0f)
            guiGraphics.renderItem(pokeStack, 0, 0)
            pose.popPose()
            curX += 13
        } else {
            guiGraphics.drawString(font, "●", curX, textY, 0xFF2ECC71.toInt(), false)
            curX += 9
        }

        val caughtStr = "§a§l$caught§r §7(${String.format("%.1f", caughtPct)}%)§r"
        guiGraphics.drawString(font, caughtStr, curX, textY, 0xFFFFFFFF.toInt(), false)
        curX += font.width(caughtStr) + 16

        // Icône Œil + Vus uniquement + Somme (Total vus)
        val eyeIcon = "§6👁§r"
        guiGraphics.drawString(font, eyeIcon, curX, textY, 0xFFFFFFFF.toInt(), false)
        curX += font.width(eyeIcon) + 4

        val encStr = "§6§l$seenOnly§r §8(§7Total: §e§l$totalSeen§r §7- ${String.format("%.1f", totalSeenPct)}%§8)§r"
        guiGraphics.drawString(font, encStr, curX, textY, 0xFFFFFFFF.toInt(), false)

        // Valeurs à droite : Total et Non découverts
        val totalStr = "§8/§r §f§l${stats.totalSpecies}§r §7(100%)§r"
        val totalX = width - 14 - font.width(totalStr)
        guiGraphics.drawString(font, totalStr, totalX, textY, 0xFFFFFFFF.toInt(), false)

        val missingStr = "§c✕ §l${stats.missingCount}§r §7(${String.format("%.1f", missingPct)}%)§r"
        val missingX = totalX - font.width(missingStr) - 14
        if (missingX > curX + font.width(encStr) + 16) {
            guiGraphics.drawString(font, missingStr, missingX, textY, 0xFFFFFFFF.toInt(), false)
        }

        // 3. Barre de progression à 2 segments (Capturé: Vert / Vu: Orange)
        guiGraphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF282D3C.toInt())
        guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF141720.toInt())

        var caughtW = ((caught.toDouble() / total) * barW).toInt()
        var encW = ((seenOnly.toDouble() / total) * barW).toInt()

        if (caught > 0 && caughtW == 0) caughtW = 1
        if (seenOnly > 0 && encW == 0) encW = 1
        if (caughtW + encW > barW) {
            encW = max(0, barW - caughtW)
        }

        // Segment vert (Capturé)
        if (caughtW > 0) {
            guiGraphics.fill(barX, barY, barX + caughtW, barY + barH, 0xFF2ECC71.toInt())
            guiGraphics.fill(barX, barY, barX + caughtW, barY + 1, 0xFF55FF55.toInt())
        }

        // Segment orange (Vu uniquement)
        if (encW > 0) {
            val encStartX = barX + caughtW
            guiGraphics.fill(encStartX, barY, encStartX + encW, barY + barH, 0xFFE67E22.toInt())
            guiGraphics.fill(encStartX, barY, encStartX + encW, barY + 1, 0xFFFFAA00.toInt())
        }

        // 4. Infobulle au survol
        if (mouseX >= barX && mouseX <= barX + barW && mouseY >= footerY && mouseY <= height) {
            val tooltip = listOf(
                net.minecraft.network.chat.Component.literal("§e§lProgression globale du Pokédex§r"),
                net.minecraft.network.chat.Component.literal("§a✔ Capturés : §f$caught §7(${String.format("%.1f", caughtPct)}%)§r"),
                net.minecraft.network.chat.Component.literal("§6👁 Vus uniquement : §f$seenOnly §7(${String.format("%.1f", seenOnlyPct)}%)§r"),
                net.minecraft.network.chat.Component.literal("§e👁+✔ Total vus (Vus + Capturés) : §f$totalSeen §7(${String.format("%.1f", totalSeenPct)}%)§r"),
                net.minecraft.network.chat.Component.literal("§c✕ Non découverts : §f${stats.missingCount} §7(${String.format("%.1f", missingPct)}%)§r"),
                net.minecraft.network.chat.Component.literal("§8Total d'espèces possibles : §f${stats.totalSpecies}§r")
            )
            guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY)
        }
    }
}
