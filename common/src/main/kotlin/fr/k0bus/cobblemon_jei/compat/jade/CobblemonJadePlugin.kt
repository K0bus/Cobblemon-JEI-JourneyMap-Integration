package fr.k0bus.cobblemon_jei.compat.jade

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.resources.ResourceLocation
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaCommonRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.WailaPlugin

@WailaPlugin(value = "cobblemon_jei")
class CobblemonJadePlugin : IWailaPlugin {

    override fun register(registration: IWailaCommonRegistration) {
        registration.registerEntityDataProvider(PasturePokemonDataProvider.INSTANCE, PokemonEntity::class.java)
    }

    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerEntityComponent(PastureFullnessProvider.INSTANCE, PokemonEntity::class.java)
        registration.markAsClientFeature(PastureFullnessProvider.UID)

        registration.registerEntityComponent(PokemonStatusComponentProvider.INSTANCE, PokemonEntity::class.java)
        registration.markAsClientFeature(PokemonStatusComponentProvider.UID)
    }

    companion object {
        const val MOD_ID = "cobblemon_jei"
        fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
    }
}
