package fr.k0bus.cobblemon_jei.compat.jade

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import snownee.jade.api.EntityAccessor
import snownee.jade.api.IServerDataProvider

class PasturePokemonDataProvider private constructor() : IServerDataProvider<EntityAccessor> {

    override fun getUid(): ResourceLocation = UID

    override fun appendServerData(tag: CompoundTag, accessor: EntityAccessor) {
        val entity = accessor.entity as? PokemonEntity ?: return
        val pokemon = entity.pokemon
        val isPastured = entity.tethering != null || pokemon.tetheringId != null
        if (!isPastured) return

        tag.putBoolean("CobblemonPastured", true)
        tag.putInt("CobblemonFullness", pokemon.currentFullness)
        tag.putInt("CobblemonMaxFullness", pokemon.getMaxFullness())
        tag.putInt("CobblemonMetabolismCycle", pokemon.metabolismCycle)
        tag.putInt("CobblemonMetabolismRate", pokemon.getMetabolismRate())
    }

    companion object {
        val UID: ResourceLocation = CobblemonJadePlugin.id("pasture_data")
        val INSTANCE = PasturePokemonDataProvider()
    }
}
