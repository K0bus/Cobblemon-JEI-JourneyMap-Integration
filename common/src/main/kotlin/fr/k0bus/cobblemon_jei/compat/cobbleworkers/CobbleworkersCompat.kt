package fr.k0bus.cobblemon_jei.compat.cobbleworkers

object CobbleworkersCompat {

    /**
     * Indique si le mod Cobbleworkers est présent et chargé.
     */
    val isLoaded: Boolean by lazy {
        try {
            val modListClass = Class.forName("net.neoforged.fml.ModList")
            val getMethod = modListClass.getMethod("get")
            val modList = getMethod.invoke(null)
            if (modList != null) {
                val isLoadedMethod = modList.javaClass.getMethod("isLoaded", String::class.java)
                val loaded = isLoadedMethod.invoke(modList, "cobbleworkers") as? Boolean
                if (loaded == true) return@lazy true
            }
        } catch (_: Throwable) {}

        try {
            val fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader")
            val getInstanceMethod = fabricLoaderClass.getMethod("getInstance")
            val loader = getInstanceMethod.invoke(null)
            if (loader != null) {
                val isModLoadedMethod = loader.javaClass.getMethod("isModLoaded", String::class.java)
                val loaded = isModLoadedMethod.invoke(loader, "cobbleworkers") as? Boolean
                if (loaded == true) return@lazy true
            }
        } catch (_: Throwable) {}

        try {
            Class.forName("accieo.cobbleworkers.Cobbleworkers")
            true
        } catch (_: Throwable) {
            false
        }
    }
}
