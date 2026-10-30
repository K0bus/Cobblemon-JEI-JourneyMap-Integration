package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

/**
 * Modale d'inspection 3D grand format (Showcase) pour un Pokémon et ses formes/variantes.
 */
class PokemonZoomModalWidget(
    private val getItemStack: (Species, FormData?, Boolean) -> ItemStack,
    private val isFormShiny: (FormData?) -> Boolean,
    private val onFormChanged: (FormData) -> Unit = {},
    private val onCloseCallback: () -> Unit = {}
) {
    var isOpen: Boolean = false
        private set

    private var targetTracked: TrackedPokemon? = null
    var activeForm: FormData? = null
    var isShiny: Boolean = false
        private set

    fun open(tracked: TrackedPokemon?, initialForm: FormData?) {
        val currentTracked = tracked ?: return
        val sp = currentTracked.species
        targetTracked = currentTracked
        activeForm = initialForm ?: sp.standardForm
        isShiny = isFormShiny(activeForm ?: sp.standardForm)
        isOpen = true
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
    }

    fun close() {
        if (isOpen) {
            isOpen = false
            onCloseCallback()
        }
    }

    fun cycleForm(direction: Int) {
        val sp = targetTracked?.species ?: return
        val allForms = PokedexRenderHelper.getSpeciesForms(sp)
        if (allForms.isEmpty()) return
        val curIndex = allForms.indexOfFirst { it.name.equals(activeForm?.name ?: sp.standardForm.name, ignoreCase = true) }.coerceAtLeast(0)
        val newIndex = Math.floorMod(curIndex + direction, allForms.size)
        activeForm = allForms[newIndex]
        onFormChanged(allForms[newIndex])
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f))
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, screenWidth: Int, screenHeight: Int, font: Font) {
        if (!isOpen) return

        guiGraphics.flush()
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(0f, 0f, 400f)

        val currentTracked = targetTracked ?: return
        val sp = currentTracked.species
        val allForms = PokedexRenderHelper.getSpeciesForms(sp)
        val currentActiveForm = activeForm ?: sp.standardForm
        val formIndex = allForms.indexOfFirst { it.name.equals(currentActiveForm.name, ignoreCase = true) }.coerceAtLeast(0)

        // 1. Fond sombre obscurcissant tout l'écran
        guiGraphics.fill(0, 0, screenWidth, screenHeight, 0xCC080A10.toInt())

        // 2. Fenêtre modale centrée
        val modalW = 240
        val modalH = 226
        val modalX = (screenWidth - modalW) / 2
        val modalY = (screenHeight - modalH) / 2

        // Fond et bordures de la modale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF141722.toInt())
        guiGraphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX - 1, modalY + modalH, modalX + modalW + 1, modalY + modalH + 1, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX - 1, modalY, modalX, modalY + modalH, 0xFF353C4D.toInt())
        guiGraphics.fill(modalX + modalW, modalY, modalX + modalW + 1, modalY + modalH, 0xFF353C4D.toInt())

        // Barre de titre de la modale
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xFF1A1F2C.toInt())
        guiGraphics.fill(modalX, modalY + 21, modalX + modalW, modalY + 22, 0xFF2A3144.toInt())

        val title = "§e§l${sp.translatedName.string}§r §7#${String.format("%04d", sp.nationalPokedexNumber)}§r"
        guiGraphics.drawString(font, title, modalX + 8, modalY + 7, 0xFFFFFFFF.toInt(), false)

        // Bouton Fermer [✕] en haut à droite
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        val isCloseHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14
        val closeColor = if (isCloseHovered) 0xFFFF5555.toInt() else 0xFFAAAAAA.toInt()
        guiGraphics.drawString(font, "§l✕§r", closeBtnX + 3, closeBtnY + 3, closeColor, false)

        // Sous-titre : Nom de la variante en grand
        val formDisplayName = PokemonTrackerService.getFormDisplayName(currentActiveForm, sp)
        guiGraphics.drawCenteredString(font, "§f§l$formDisplayName§r", modalX + modalW / 2, modalY + 27, 0xFFFFFFFF.toInt())

        // 3. Grande scène d'exposition 3D (Showcase)
        val stageW = 180
        val stageH = 100
        val stageX = modalX + (modalW - stageW) / 2
        val stageY = modalY + 40

        guiGraphics.fill(stageX, stageY, stageX + stageW, stageY + stageH, 0xFF0D0F17.toInt())
        guiGraphics.fill(stageX, stageY, stageX + stageW, stageY + 1, 0xFF2A3144.toInt())
        guiGraphics.fill(stageX, stageY + stageH - 1, stageX + stageW, stageY + stageH, 0xFF2A3144.toInt())
        guiGraphics.fill(stageX, stageY, stageX + 1, stageY + stageH, 0xFF2A3144.toInt())
        guiGraphics.fill(stageX + stageW - 1, stageY, stageX + stageW, stageY + stageH, 0xFF2A3144.toInt())

        // Modèle 3D rendu en grand format (Scale 5.0f -> 80x80px)
        val zoomStack = getItemStack(sp, currentActiveForm, isShiny)
        if (!zoomStack.isEmpty) {
            pose.pushPose()
            val modelScale = 5.0f
            val modelRenderSize = 16f * modelScale
            val modelX = stageX + (stageW - modelRenderSize) / 2f
            val modelY = stageY + (stageH - modelRenderSize) / 2f
            pose.translate(modelX, modelY, 0f)
            pose.scale(modelScale, modelScale, 1.0f)
            guiGraphics.renderItem(zoomStack, 0, 0)
            pose.popPose()
        }

        // 4. Ligne de contrôle : [ ◀ ]   [ ★ Shiny ]   [ ▶ ]
        val ctrlY = stageY + stageH + 6

        // Flèche Précédent [ ◀ ]
        val prevBtnX = stageX
        val prevBtnW = 24
        val prevBtnH = 15
        val isPrevHovered = mouseX >= prevBtnX && mouseX <= prevBtnX + prevBtnW && mouseY >= ctrlY && mouseY <= ctrlY + prevBtnH
        val prevBg = if (isPrevHovered) 0xFF353C4D.toInt() else 0xFF1D212E.toInt()
        val prevBorder = if (isPrevHovered) 0xFF54A0FF.toInt() else 0xFF2A3144.toInt()
        guiGraphics.fill(prevBtnX, ctrlY, prevBtnX + prevBtnW, ctrlY + prevBtnH, prevBg)
        guiGraphics.fill(prevBtnX, ctrlY, prevBtnX + prevBtnW, ctrlY + 1, prevBorder)
        guiGraphics.fill(prevBtnX, ctrlY + prevBtnH - 1, prevBtnX + prevBtnW, ctrlY + prevBtnH, prevBorder)
        guiGraphics.fill(prevBtnX, ctrlY, prevBtnX + 1, ctrlY + prevBtnH, prevBorder)
        guiGraphics.fill(prevBtnX + prevBtnW - 1, ctrlY, prevBtnX + prevBtnW, ctrlY + prevBtnH, prevBorder)
        guiGraphics.drawCenteredString(font, "◀", prevBtnX + prevBtnW / 2, ctrlY + 3, 0xFFFFFFFF.toInt())

        // Bouton Shiny au centre [ ★ Shiny ]
        val shinyBtnW = 68
        val shinyBtnH = 15
        val shinyBtnX = modalX + (modalW - shinyBtnW) / 2
        val isShinyHovered = mouseX >= shinyBtnX && mouseX <= shinyBtnX + shinyBtnW && mouseY >= ctrlY && mouseY <= ctrlY + shinyBtnH
        val shinyBg = when {
            isShiny -> if (isShinyHovered) 0xFFD4AC0D.toInt() else 0xFFB7950B.toInt()
            isShinyHovered -> 0xFF353C4D.toInt()
            else -> 0xFF1D212E.toInt()
        }
        val shinyBorder = if (isShiny) 0xFFF1C40F.toInt() else 0xFF2A3144.toInt()
        val shinyText = if (isShiny) "§6★ Shiny§r" else "§7☆ Shiny§r"

        guiGraphics.fill(shinyBtnX, ctrlY, shinyBtnX + shinyBtnW, ctrlY + shinyBtnH, shinyBg)
        guiGraphics.fill(shinyBtnX, ctrlY, shinyBtnX + shinyBtnW, ctrlY + 1, shinyBorder)
        guiGraphics.fill(shinyBtnX, ctrlY, shinyBtnX + shinyBtnW, ctrlY + 1, shinyBorder)
        guiGraphics.fill(shinyBtnX, ctrlY + shinyBtnH - 1, shinyBtnX + shinyBtnW, ctrlY + shinyBtnH, shinyBorder)
        guiGraphics.fill(shinyBtnX, ctrlY, shinyBtnX + 1, ctrlY + shinyBtnH, shinyBorder)
        guiGraphics.fill(shinyBtnX + shinyBtnW - 1, ctrlY, shinyBtnX + shinyBtnW, ctrlY + shinyBtnH, shinyBorder)
        guiGraphics.drawCenteredString(font, shinyText, shinyBtnX + shinyBtnW / 2, ctrlY + 4, 0xFFFFFFFF.toInt())

        // Flèche Suivant [ ▶ ]
        val nextBtnX = stageX + stageW - 24
        val nextBtnW = 24
        val nextBtnH = 15
        val isNextHovered = mouseX >= nextBtnX && mouseX <= nextBtnX + nextBtnW && mouseY >= ctrlY && mouseY <= ctrlY + nextBtnH
        val nextBg = if (isNextHovered) 0xFF353C4D.toInt() else 0xFF1D212E.toInt()
        val nextBorder = if (isNextHovered) 0xFF54A0FF.toInt() else 0xFF2A3144.toInt()
        guiGraphics.fill(nextBtnX, ctrlY, nextBtnX + nextBtnW, ctrlY + nextBtnH, nextBg)
        guiGraphics.fill(nextBtnX, ctrlY, nextBtnX + nextBtnW, ctrlY + 1, nextBorder)
        guiGraphics.fill(nextBtnX, ctrlY + nextBtnH - 1, nextBtnX + nextBtnW, ctrlY + nextBtnH, nextBorder)
        guiGraphics.fill(nextBtnX, ctrlY, nextBtnX + 1, ctrlY + nextBtnH, nextBorder)
        guiGraphics.fill(nextBtnX + nextBtnW - 1, ctrlY, nextBtnX + nextBtnW, ctrlY + nextBtnH, nextBorder)
        guiGraphics.drawCenteredString(font, "▶", nextBtnX + nextBtnW / 2, ctrlY + 3, 0xFFFFFFFF.toInt())

        // 5. Compteur de variante : Variante X / Total
        guiGraphics.drawCenteredString(font, "§7Variante ${formIndex + 1} / ${allForms.size}§r", modalX + modalW / 2, ctrlY + 19, 0xFFAAAAAA.toInt())

        // 6. Types élémentaires de la forme
        val primColor = PokedexRenderHelper.getElementColor(currentActiveForm.primaryType.name)
        val primName = currentActiveForm.primaryType.displayName.string.uppercase()
        val primW = font.width(primName) + 6
        val sec = currentActiveForm.secondaryType
        val totalTypeW = if (sec != null) primW + 4 + font.width(sec.displayName.string.uppercase()) + 6 else primW

        var typeStartX = modalX + (modalW - totalTypeW) / 2
        val typeY = ctrlY + 30
        guiGraphics.fill(typeStartX, typeY, typeStartX + primW, typeY + 10, primColor)
        guiGraphics.drawString(font, primName, typeStartX + 3, typeY + 1, 0xFFFFFFFF.toInt(), true)
        typeStartX += primW + 4

        if (sec != null) {
            val secColor = PokedexRenderHelper.getElementColor(sec.name)
            val secName = sec.displayName.string.uppercase()
            val secW = font.width(secName) + 6
            guiGraphics.fill(typeStartX, typeY, typeStartX + secW, typeY + 10, secColor)
            guiGraphics.drawString(font, secName, typeStartX + 3, typeY + 1, 0xFFFFFFFF.toInt(), true)
        }

        // 7. Statut de capture / possession
        val statusInfo = PokedexRenderHelper.getFormStatusInfo(sp, currentActiveForm, isShiny, currentTracked)
        guiGraphics.drawCenteredString(font, statusInfo.text, modalX + modalW / 2, ctrlY + 44, statusInfo.color)

        // 8. Bouton Fermer en bas
        val bottomCloseW = 90
        val bottomCloseH = 14
        val bottomCloseX = modalX + (modalW - bottomCloseW) / 2
        val bottomCloseY = modalY + modalH - 18
        val isBottomCloseHovered = mouseX >= bottomCloseX && mouseX <= bottomCloseX + bottomCloseW &&
                mouseY >= bottomCloseY && mouseY <= bottomCloseY + bottomCloseH
        val bCloseBg = if (isBottomCloseHovered) 0xFF353C4D.toInt() else 0xFF1D212E.toInt()
        val bCloseBorder = if (isBottomCloseHovered) 0xFF54A0FF.toInt() else 0xFF2A3144.toInt()

        guiGraphics.fill(bottomCloseX, bottomCloseY, bottomCloseX + bottomCloseW, bottomCloseY + bottomCloseH, bCloseBg)
        guiGraphics.fill(bottomCloseX, bottomCloseY, bottomCloseX + bottomCloseW, bottomCloseY + 1, bCloseBorder)
        guiGraphics.fill(bottomCloseX, bottomCloseY + bottomCloseH - 1, bottomCloseX + bottomCloseW, bottomCloseY + bottomCloseH, bCloseBorder)
        guiGraphics.fill(bottomCloseX, bottomCloseY, bottomCloseX + 1, bottomCloseY + bottomCloseH, bCloseBorder)
        guiGraphics.fill(bottomCloseX + bottomCloseW - 1, bottomCloseY, bottomCloseX + bottomCloseW, bottomCloseY + bottomCloseH, bCloseBorder)
        guiGraphics.drawCenteredString(font, "§7Fermer (Échap)§r", bottomCloseX + bottomCloseW / 2, bottomCloseY + 3, 0xFFFFFFFF.toInt())

        guiGraphics.flush()
        pose.popPose()
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isOpen) return false

        val modalW = 240
        val modalH = 226
        val modalX = (screenWidth - modalW) / 2
        val modalY = (screenHeight - modalH) / 2

        // Clic sur [ ✕ ] haut droite
        val closeBtnX = modalX + modalW - 18
        val closeBtnY = modalY + 4
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 14 && mouseY >= closeBtnY && mouseY <= closeBtnY + 14) {
            close()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }

        // Clic sur bouton fermer en bas
        val bottomCloseW = 90
        val bottomCloseH = 14
        val bottomCloseX = modalX + (modalW - bottomCloseW) / 2
        val bottomCloseY = modalY + modalH - 18
        if (mouseX >= bottomCloseX && mouseX <= bottomCloseX + bottomCloseW && mouseY >= bottomCloseY && mouseY <= bottomCloseY + bottomCloseH) {
            close()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }

        val stageW = 180
        val stageH = 100
        val stageX = modalX + (modalW - stageW) / 2
        val stageY = modalY + 40
        val ctrlY = stageY + stageH + 6

        // [ ◀ ]
        val prevBtnX = stageX
        val prevBtnW = 24
        val prevBtnH = 15
        if (mouseX >= prevBtnX && mouseX <= prevBtnX + prevBtnW && mouseY >= ctrlY && mouseY <= ctrlY + prevBtnH) {
            cycleForm(-1)
            return true
        }

        // [ ▶ ]
        val nextBtnX = stageX + stageW - 24
        val nextBtnW = 24
        val nextBtnH = 15
        if (mouseX >= nextBtnX && mouseX <= nextBtnX + nextBtnW && mouseY >= ctrlY && mouseY <= ctrlY + nextBtnH) {
            cycleForm(1)
            return true
        }

        // [ ★ Shiny ]
        val shinyBtnW = 68
        val shinyBtnH = 15
        val shinyBtnX = modalX + (modalW - shinyBtnW) / 2
        if (mouseX >= shinyBtnX && mouseX <= shinyBtnX + shinyBtnW && mouseY >= ctrlY && mouseY <= ctrlY + shinyBtnH) {
            isShiny = !isShiny
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f))
            return true
        }

        // Clic en dehors de la boîte modale : ferme la modale
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            close()
            return true
        }

        return true
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, screenWidth: Int, screenHeight: Int): Boolean =
        mouseClicked(mouseX, mouseY, screenWidth, screenHeight)

    fun mouseScrolled(scrollY: Double): Boolean {
        if (!isOpen) return false
        cycleForm(if (scrollY > 0) -1 else 1)
        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean =
        mouseScrolled(scrollY)

    fun keyPressed(keyCode: Int): Boolean {
        if (!isOpen) return false
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close()
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            cycleForm(-1)
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            cycleForm(1)
            return true
        }
        return true
    }

    fun keyPressed(keyCode: Int, scanCode: Int = 0, modifiers: Int = 0): Boolean =
        keyPressed(keyCode)
}
