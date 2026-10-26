package fr.k0bus.cobblemon_jei.data

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.journeymap.ChunkBiomeTagCache
import fr.k0bus.cobblemon_jei.journeymap.CobblemonSpawnMatcher
import fr.k0bus.cobblemon_jei.recipe.PokemonSpawnRecipe
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

/**
 * Statut d'éligibilité de spawn en temps réel par rapport à l'environnement actuel du joueur.
 */
enum class SpawnEligibilityStatus(val badgeColor: Int, val icon: String, val translationKey: String) {
    /** 🟢 Conditions entièrement réunies à cet endroit précis et à ce moment précis */
    AVAILABLE_NOW(0xFF2ED573.toInt(), "✔", "gui.cobblemon_jei.spawn_badge.available_now"),

    /** 🟠 Bon biome et bonne dimension, mais critère temporel ou météorologique non satisfait */
    ENVIRONMENT_MATCH(0xFFFFA502.toInt(), "⏳", "gui.cobblemon_jei.spawn_badge.environment_match"),

    /** 🔴 Impossible ici (mauvais biome ou mauvaise dimension) */
    UNAVAILABLE(0xFFFF4757.toInt(), "✕", "gui.cobblemon_jei.spawn_badge.unavailable")
}

/**
 * Résultat détaillé de l'évaluation environnementale d'apparition.
 */
data class SpawnEligibilityResult(
    val status: SpawnEligibilityStatus,
    val summary: Component,
    val blockingReasons: List<String>,
    val matchingRecipe: PokemonSpawnRecipe?
)

/**
 * Évaluateur environnemental temps réel des conditions de spawn de Cobblemon.
 * Compare la position du joueur (biome, dimension, altitude, ciel), l'heure et la météo
 * avec les règles déclarées du Pokémon.
 */
object SpawnConditionEvaluator {

    /**
     * Calcule la tranche horaire courante de Minecraft.
     */
    fun getTimeRange(dayTime: Long): String {
        val timeOfDay = dayTime % 24000
        return when (timeOfDay) {
            in 23000..24000, in 0..1000 -> "dawn"
            in 1001..11999 -> "day"
            in 12000..13000 -> "dusk"
            else -> "night"
        }
    }

    /**
     * Vérifie si l'heure actuelle correspond au critère de la recette.
     */
    fun matchesTime(timeRange: String, currentTime: String): Boolean {
        if (timeRange.equals("any", ignoreCase = true) || timeRange.isBlank()) return true
        if (timeRange.equals(currentTime, ignoreCase = true)) return true
        // day inclut dawn/dusk dans certaines règles larges
        if (timeRange.equals("day", ignoreCase = true) && (currentTime == "day" || currentTime == "dawn")) return true
        if (timeRange.equals("night", ignoreCase = true) && (currentTime == "night" || currentTime == "dusk")) return true
        return false
    }

    /**
     * Calcule le temps restant estimé (en minutes et secondes) avant la prochaine occurrence d'une tranche horaire.
     */
    fun getRemainingTimeDescription(currentDayTime: Long, requiredTime: String): String? {
        if (requiredTime.isBlank() || requiredTime.equals("any", ignoreCase = true)) return null
        val req = requiredTime.lowercase()
        val timeOfDay = currentDayTime % 24000
        val targetTick = when (req) {
            "day" -> 1000L
            "dusk" -> 12000L
            "night" -> 13000L
            "dawn" -> 23000L
            else -> return null
        }
        val diffTicks = if (targetTick >= timeOfDay) targetTick - timeOfDay else (24000L - timeOfDay + targetTick)
        val seconds = diffTicks / 20
        val minutes = seconds / 60
        val secRemainder = seconds % 60
        return if (minutes > 0) "${minutes}m ${secRemainder}s" else "${seconds}s"
    }

    /**
     * Vérifie si la météo actuelle correspond au critère de la recette.
     */
    fun matchesWeather(recipeWeather: String, isRaining: Boolean, isThundering: Boolean): Boolean {
        if (recipeWeather.equals("any", ignoreCase = true) || recipeWeather.isBlank()) return true
        val w = recipeWeather.lowercase()
        if (w == "rain" || w == "raining") return isRaining || isThundering
        if (w == "thunder" || w == "thundering") return isThundering
        if (w == "clear") return !isRaining && !isThundering
        return true
    }

    /**
     * Évalue si une espèce/forme peut apparaître auprès du joueur maintenant.
     */
    fun evaluate(player: Player?, level: Level?, species: Species, form: FormData? = null): SpawnEligibilityResult {
        if (player == null || level == null) {
            return SpawnEligibilityResult(
                SpawnEligibilityStatus.UNAVAILABLE,
                Component.literal("§7Information non disponible"),
                emptyList(),
                null
            )
        }

        val dim = level.dimension()
        val pos = player.blockPosition()
        val targetForm = form ?: species.standardForm
        val recipes = CobblemonSpawnMatcher.getValidRecipes(species, targetForm, dim)

        if (recipes.isEmpty()) {
            return SpawnEligibilityResult(
                SpawnEligibilityStatus.UNAVAILABLE,
                Component.translatable(SpawnEligibilityStatus.UNAVAILABLE.translationKey),
                listOf("Dimension incompatible (${dim.location().path})"),
                null
            )
        }

        val biomeHolder = level.getBiome(pos)
        val profile = ChunkBiomeTagCache.getOrCreateProfile(biomeHolder)

        // Filtre les recettes valides pour le biome actuel
        val biomeMatchingRecipes = recipes.filter { CobblemonSpawnMatcher.matchesProfile(profile, it) }

        if (biomeMatchingRecipes.isEmpty()) {
            return SpawnEligibilityResult(
                SpawnEligibilityStatus.UNAVAILABLE,
                Component.translatable(SpawnEligibilityStatus.UNAVAILABLE.translationKey),
                listOf("Biome incompatible (${profile.biomePath})"),
                null
            )
        }

        // Le biome et la dimension correspondent ! Testons maintenant l'heure, la météo, le ciel et l'altitude.
        val currentTime = getTimeRange(level.dayTime)
        val isRaining = level.isRaining
        val isThundering = level.isThundering
        val canSeeSky = level.canSeeSky(pos)
        val playerY = pos.y

        val blockingReasons = mutableListOf<String>()

        for (recipe in biomeMatchingRecipes) {
            val reasons = mutableListOf<String>()

            // Heure
            if (!matchesTime(recipe.timeRange, currentTime)) {
                reasons.add("Heure requise : ${recipe.timeRange} (actuel : $currentTime)")
            }

            // Météo
            if (!matchesWeather(recipe.weather, isRaining, isThundering)) {
                reasons.add("Météo requise : ${recipe.weather}")
            }

            // Ciel ouvert
            if (recipe.canSeeSky != null && recipe.canSeeSky != canSeeSky) {
                if (recipe.canSeeSky) reasons.add("Exige le ciel découvert")
                else reasons.add("Exige d'être abrité / sous terre")
            }

            // Altitude Y
            if (recipe.minY != null && playerY < recipe.minY) {
                reasons.add("Altitude trop basse (min Y=${recipe.minY}, actuel Y=$playerY)")
            }
            if (recipe.maxY != null && playerY > recipe.maxY) {
                reasons.add("Altitude trop haute (max Y=${recipe.maxY}, actuel Y=$playerY)")
            }

            // Si toutes les conditions sont validées pour cette recette
            if (reasons.isEmpty()) {
                return SpawnEligibilityResult(
                    SpawnEligibilityStatus.AVAILABLE_NOW,
                    Component.translatable(SpawnEligibilityStatus.AVAILABLE_NOW.translationKey),
                    emptyList(),
                    recipe
                )
            } else {
                blockingReasons.addAll(reasons)
            }
        }

        return SpawnEligibilityResult(
            SpawnEligibilityStatus.ENVIRONMENT_MATCH,
            Component.translatable(SpawnEligibilityStatus.ENVIRONMENT_MATCH.translationKey),
            blockingReasons.distinct(),
            biomeMatchingRecipes.firstOrNull()
        )
    }
}
