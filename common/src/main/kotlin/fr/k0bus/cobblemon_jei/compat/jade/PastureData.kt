package fr.k0bus.cobblemon_jei.compat.jade

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import snownee.jade.api.EntityAccessor

data class PastureData(
    val fullness: Int,
    val maxFullness: Int,
    val cycle: Int,
    val rate: Int
) {
    companion object {
        fun from(accessor: EntityAccessor): PastureData? {
            val tag = accessor.serverData
            if (tag.contains("CobblemonPastured")) {
                if (!tag.getBoolean("CobblemonPastured")) return null
                return PastureData(
                    fullness = tag.getInt("CobblemonFullness"),
                    maxFullness = if (tag.contains("CobblemonMaxFullness")) tag.getInt("CobblemonMaxFullness").coerceAtLeast(1) else 100,
                    cycle = tag.getInt("CobblemonMetabolismCycle"),
                    rate = if (tag.contains("CobblemonMetabolismRate")) tag.getInt("CobblemonMetabolismRate").coerceAtLeast(1) else 6000
                )
            }

            // Fallback local si serveur sans Jade ou en solo
            val entity = accessor.entity as? PokemonEntity ?: return null
            val pokemon = entity.pokemon
            val isPastured = entity.tethering != null || pokemon.tetheringId != null
            if (!isPastured) return null

            return PastureData(
                fullness = pokemon.currentFullness,
                maxFullness = pokemon.getMaxFullness().coerceAtLeast(1),
                cycle = pokemon.metabolismCycle,
                rate = pokemon.getMetabolismRate().coerceAtLeast(1)
            )
        }
    }
}
