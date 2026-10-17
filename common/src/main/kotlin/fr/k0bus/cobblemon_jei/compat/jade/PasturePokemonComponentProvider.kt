package fr.k0bus.cobblemon_jei.compat.jade

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import snownee.jade.api.EntityAccessor
import snownee.jade.api.IEntityComponentProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.config.IPluginConfig
import snownee.jade.api.ui.BoxStyle
import snownee.jade.api.ui.IElementHelper

class PastureFullnessProvider private constructor() : IEntityComponentProvider {

    override fun getUid(): ResourceLocation = UID

    override fun appendTooltip(tooltip: ITooltip, accessor: EntityAccessor, config: IPluginConfig) {
        val data = PastureData.from(accessor) ?: return
        val helper = IElementHelper.get()
        val ratio = (data.fullness.toFloat() / data.maxFullness.toFloat()).coerceIn(0f, 1f)

        val (text, startColor, endColor) = if (data.fullness <= 0) {
            Triple(
                Component.translatable("jade.cobblemon_jei.pasture_fullness_hungry", data.fullness, data.maxFullness),
                0xFFFF3B30.toInt(),
                0xFF991B1B.toInt()
            )
        } else if (data.fullness < 30) {
            Triple(
                Component.translatable("jade.cobblemon_jei.pasture_fullness", data.fullness, data.maxFullness),
                0xFFFF9500.toInt(),
                0xFFB45309.toInt()
            )
        } else {
            Triple(
                Component.translatable("jade.cobblemon_jei.pasture_fullness", data.fullness, data.maxFullness),
                0xFF34C759.toInt(),
                0xFF15803D.toInt()
            )
        }

        val style = helper.progressStyle().color(startColor, endColor)
        val progressBar = helper.progress(ratio, text, style, BoxStyle.getNestedBox(), false)
        tooltip.add(progressBar)
    }

    companion object {
        val UID: ResourceLocation = CobblemonJadePlugin.id("pasture_fullness")
        val INSTANCE = PastureFullnessProvider()
    }
}

class PastureDropTimerProvider private constructor() : IEntityComponentProvider {

    override fun getUid(): ResourceLocation = UID

    override fun appendTooltip(tooltip: ITooltip, accessor: EntityAccessor, config: IPluginConfig) {
        val data = PastureData.from(accessor) ?: return
        val helper = IElementHelper.get()

        if (data.fullness <= 0) {
            val pausedText = Component.translatable("jade.cobblemon_jei.pasture_drop_paused")
                .withStyle(ChatFormatting.RED)
            tooltip.add(pausedText)
        } else {
            val remainingTicks = (data.rate - data.cycle).coerceAtLeast(0)
            val remainingSeconds = remainingTicks / 20
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            val formattedTime = if (minutes > 0) {
                "${minutes}m ${seconds.toString().padStart(2, '0')}s"
            } else {
                "${seconds}s"
            }

            val ratio = (data.cycle.toFloat() / data.rate.toFloat()).coerceIn(0f, 1f)
            val dropText = Component.translatable("jade.cobblemon_jei.pasture_drop_timer", formattedTime)
            val style = helper.progressStyle().color(0xFF00C7FF.toInt(), 0xFF0284C7.toInt())
            val progressBar = helper.progress(ratio, dropText, style, BoxStyle.getNestedBox(), false)
            tooltip.add(progressBar)
        }
    }

    companion object {
        val UID: ResourceLocation = CobblemonJadePlugin.id("pasture_drop_timer")
        val INSTANCE = PastureDropTimerProvider()
    }
}
