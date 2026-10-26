package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbilityType
import com.google.gson.JsonObject

/**
 * Filtre de taille pour le radar de Pokémon.
 */
enum class SizeFilter(val translationKey: String) {
    ANY("gui.cobblemon_jei.tracker.size.any"),
    RARE_ONLY("gui.cobblemon_jei.tracker.size.rare"),
    ONLY_MICROSCOPIC("gui.cobblemon_jei.tracker.size.micro"),
    ONLY_GIGANTIC("gui.cobblemon_jei.tracker.size.gigantic");

    fun matches(scaleModifier: Float): Boolean {
        return when (this) {
            ANY -> true
            RARE_ONLY -> scaleModifier <= 0.75f || scaleModifier >= 1.25f
            ONLY_MICROSCOPIC -> scaleModifier <= 0.75f
            ONLY_GIGANTIC -> scaleModifier >= 1.25f
        }
    }

    fun next(): SizeFilter {
        val values = values()
        return values[(ordinal + 1) % values.size]
    }
}

/**
 * Critères de filtrage avancés pour le radar et tracker Cobblemon.
 * Permet de cibler spécifiquement les talents cachés, les IVs d'exception, les tailles rares et les natures.
 */
data class RadarAdvancedFilter(
    var onlyHiddenAbility: Boolean = false,
    var minPerfectIvs: Int = 0, // 0 = Tout, 1 = 1+, 2 = 2+, 3 = 3+, 4 = 4+, 5 = 5+, 6 = 6 Parfaits
    var sizeFilter: SizeFilter = SizeFilter.ANY,
    var targetNature: String? = null,
    var inWorldGlowing: Boolean = true,
    var inWorldParticles: Boolean = true
) {

    /**
     * Vérifie si le Pokémon donné satisfait tous les critères avancés.
     */
    fun matches(pokemon: Pokemon): Boolean {
        // 1. Talent Caché (Hidden Ability)
        if (onlyHiddenAbility && !hasHiddenAbility(pokemon)) {
            return false
        }

        // 2. Seuil d'IVs parfaits (31/31)
        if (minPerfectIvs > 0) {
            val perfectCount = countPerfectIvs(pokemon)
            if (perfectCount < minPerfectIvs) {
                return false
            }
        }

        // 3. Filtre de taille
        if (!sizeFilter.matches(pokemon.scaleModifier)) {
            return false
        }

        // 4. Nature spécifique
        if (!targetNature.isNullOrBlank()) {
            val pokeNature = try {
                pokemon.effectiveNature.name.path.lowercase()
            } catch (_: Throwable) {
                pokemon.nature.name.path.lowercase()
            }
            if (!pokeNature.equals(targetNature!!.lowercase().trim(), ignoreCase = true)) {
                return false
            }
        }

        return true
    }

    /**
     * Retourne la liste des badges visuels décrivant les qualités remarquables du Pokémon.
     */
    fun getBadges(pokemon: Pokemon): List<String> {
        val badges = mutableListOf<String>()

        if (hasHiddenAbility(pokemon)) {
            badges.add("§d[Talent Caché]")
        }

        val perfectCount = countPerfectIvs(pokemon)
        if (perfectCount == 6) {
            badges.add("§6[6x31 IV (Parfait)]")
        } else if (perfectCount >= 3) {
            badges.add("§e[${perfectCount}x31 IV]")
        } else if (perfectCount > 0) {
            badges.add("§a[${perfectCount}x31 IV]")
        }

        if (pokemon.scaleModifier <= 0.75f) {
            badges.add("§b[Micro]")
        } else if (pokemon.scaleModifier >= 1.25f) {
            badges.add("§6[Colossal]")
        }

        return badges
    }

    fun toJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("onlyHiddenAbility", onlyHiddenAbility)
        json.addProperty("minPerfectIvs", minPerfectIvs)
        json.addProperty("sizeFilter", sizeFilter.name)
        if (targetNature != null) {
            json.addProperty("targetNature", targetNature)
        }
        json.addProperty("inWorldGlowing", inWorldGlowing)
        json.addProperty("inWorldParticles", inWorldParticles)
        return json
    }

    fun fromJson(json: JsonObject) {
        if (json.has("onlyHiddenAbility")) {
            onlyHiddenAbility = json.get("onlyHiddenAbility").asBoolean
        }
        if (json.has("minPerfectIvs")) {
            minPerfectIvs = json.get("minPerfectIvs").asInt.coerceIn(0, 6)
        }
        if (json.has("sizeFilter")) {
            sizeFilter = try {
                SizeFilter.valueOf(json.get("sizeFilter").asString)
            } catch (_: Throwable) {
                SizeFilter.ANY
            }
        }
        if (json.has("targetNature")) {
            val n = json.get("targetNature").asString
            targetNature = n.ifBlank { null }
        }
        if (json.has("inWorldGlowing")) {
            inWorldGlowing = json.get("inWorldGlowing").asBoolean
        }
        if (json.has("inWorldParticles")) {
            inWorldParticles = json.get("inWorldParticles").asBoolean
        }
    }

    fun hasAnyFilter(): Boolean {
        return onlyHiddenAbility || minPerfectIvs > 0 || sizeFilter != SizeFilter.ANY || !targetNature.isNullOrBlank()
    }

    fun resetFilters() {
        onlyHiddenAbility = false
        minPerfectIvs = 0
        sizeFilter = SizeFilter.ANY
        targetNature = null
    }

    companion object {
        /**
         * Détermine si un Pokémon possède sa capacité cachée (Hidden Ability).
         */
        fun hasHiddenAbility(pokemon: Pokemon): Boolean {
            return try {
                val abilityTemplate = pokemon.ability.template
                pokemon.form.abilities.mapping
                    .flatMap { it.value }
                    .any { it.template == abilityTemplate && it.type == HiddenAbilityType }
            } catch (_: Throwable) {
                false
            }
        }

        /**
         * Compte le nombre de statistiques permanentes ayant 31 IVs.
         */
        fun countPerfectIvs(pokemon: Pokemon): Int {
            return try {
                Stats.PERMANENT.count { stat ->
                    pokemon.ivs[stat] == 31
                }
            } catch (_: Throwable) {
                0
            }
        }
    }
}
