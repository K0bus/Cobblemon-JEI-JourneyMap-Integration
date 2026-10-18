package fr.k0bus.cobblemon_jei.compat.rct

/**
 * Gestionnaire de compatibilité avec le mod Radical Cobblemon Trainers (rctmod).
 */
object RctCompat {

    /**
     * Indique si le mod Radical Cobblemon Trainers est présent dans le chargeur de mods.
     */
    val isLoaded: Boolean by lazy {
        try {
            val modListClass = Class.forName("net.neoforged.fml.ModList")
            val getMethod = modListClass.getMethod("get")
            val modList = getMethod.invoke(null)
            if (modList != null) {
                val isLoadedMethod = modList.javaClass.getMethod("isLoaded", String::class.java)
                val loaded = isLoadedMethod.invoke(modList, "rctmod") as? Boolean
                    ?: isLoadedMethod.invoke(modList, "radical_cobblemon_trainers") as? Boolean
                if (loaded == true) return@lazy true
            }
        } catch (_: Throwable) {}

        try {
            val fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader")
            val getInstanceMethod = fabricLoaderClass.getMethod("getInstance")
            val loader = getInstanceMethod.invoke(null)
            if (loader != null) {
                val isModLoadedMethod = loader.javaClass.getMethod("isModLoaded", String::class.java)
                val loaded = isModLoadedMethod.invoke(loader, "rctmod") as? Boolean
                    ?: isModLoadedMethod.invoke(loader, "radical_cobblemon_trainers") as? Boolean
                if (loaded == true) return@lazy true
            }
        } catch (_: Throwable) {}

        try {
            Class.forName("com.gitlab.srcmc.rctmod.RCTMod")
            return@lazy true
        } catch (_: Throwable) {}

        try {
            Class.forName("com.gitlab.srcmc.rctmod.api.RCTApi")
            return@lazy true
        } catch (_: Throwable) {}

        false
    }

    /**
     * Indique si l'interface RCT est accessible (soit mod chargé, soit données/séries disponibles).
     */
    fun isAvailable(): Boolean {
        return isLoaded || RctDataLoader.hasAnySeries()
    }
}
