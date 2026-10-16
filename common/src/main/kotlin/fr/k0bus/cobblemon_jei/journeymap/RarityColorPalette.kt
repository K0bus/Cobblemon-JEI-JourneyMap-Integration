package fr.k0bus.cobblemon_jei.journeymap

import fr.k0bus.cobblemon_jei.recipe.PokemonSpawnRecipe
import com.cobblemon.mod.common.pokemon.Species
import journeymap.api.v2.client.model.ShapeProperties

/**
 * Niveaux de rareté d'apparition pour Cobblemon avec métadonnées d'affichage.
 */
enum class SpawnRarity(
    val id: String,
    val displayName: String,
    val formatCode: String,
    val rank: Int
) {
    COMMON("common", "Commun", "§a", 1),
    UNCOMMON("uncommon", "Peu commun", "§b", 2),
    RARE("rare", "Rare", "§d", 3),
    ULTRA_RARE("ultra-rare", "Ultra-Rare", "§6", 4);

    companion object {
        fun fromBucketOrWeight(bucket: String?, weight: Float = 0f): SpawnRarity {
            val normalized = bucket?.lowercase()?.trim() ?: ""
            return when {
                normalized.contains("ultra") || normalized.contains("legend") || normalized.contains("myth") -> ULTRA_RARE
                normalized == "rare" -> RARE
                normalized == "uncommon" -> UNCOMMON
                normalized == "common" -> COMMON
                else -> {
                    when {
                        weight > 0f && weight <= 0.8f -> ULTRA_RARE
                        weight in 0.8f..3.0f -> RARE
                        weight in 3.0f..8.0f -> UNCOMMON
                        else -> COMMON
                    }
                }
            }
        }
    }
}

/**
 * Charte graphique officielle indexée sur la rareté du spawn Cobblemon.
 * Gère les codes hexadécimaux, transparences (fill/stroke), largeurs de bordure
 * et mise en forme des infobulles JourneyMap.
 */
object RarityColorPalette {

    // 1. Commun : Vert émeraude semi-transparent (#2ECC71), bordure vert vif (#27AE60)
    const val COMMON_FILL_COLOR = 0x2ECC71
    const val COMMON_FILL_OPACITY = 0.25f
    const val COMMON_STROKE_COLOR = 0x27AE60
    const val COMMON_STROKE_OPACITY = 0.80f
    const val COMMON_STROKE_WIDTH = 1.5f

    // 2. Peu commun : Bleu azur semi-transparent (#3498DB), bordure bleu cobalt (#2980B9)
    const val UNCOMMON_FILL_COLOR = 0x3498DB
    const val UNCOMMON_FILL_OPACITY = 0.25f
    const val UNCOMMON_STROKE_COLOR = 0x2980B9
    const val UNCOMMON_STROKE_OPACITY = 0.80f
    const val UNCOMMON_STROKE_WIDTH = 1.5f

    // 3. Rare : Violet / Mauve semi-transparent (#9B59B6), bordure violet profond (#8E44AD)
    const val RARE_FILL_COLOR = 0x9B59B6
    const val RARE_FILL_OPACITY = 0.30f
    const val RARE_STROKE_COLOR = 0x8E44AD
    const val RARE_STROKE_OPACITY = 0.85f
    const val RARE_STROKE_WIDTH = 2.0f

    // 4. Ultra-rare / Légendaire : Or / Ambré semi-transparent (#F1C40F / #E67E22), bordure or étincelant (#F39C12 / #D35400)
    const val ULTRA_RARE_FILL_COLOR = 0xF1C40F
    const val ULTRA_RARE_FILL_OPACITY = 0.35f
    const val ULTRA_RARE_STROKE_COLOR = 0xF39C12
    const val ULTRA_RARE_STROKE_OPACITY = 0.90f
    const val ULTRA_RARE_STROKE_WIDTH = 2.5f

    /**
     * Construit les propriétés graphiques du polygone pour un palier de rareté donné.
     */
    fun createShapeProperties(rarity: SpawnRarity): ShapeProperties {
        val shape = ShapeProperties()
        when (rarity) {
            SpawnRarity.COMMON -> {
                shape.setFillColor(COMMON_FILL_COLOR)
                    .setFillOpacity(COMMON_FILL_OPACITY)
                    .setStrokeColor(COMMON_STROKE_COLOR)
                    .setStrokeOpacity(COMMON_STROKE_OPACITY)
                    .setStrokeWidth(COMMON_STROKE_WIDTH)
            }
            SpawnRarity.UNCOMMON -> {
                shape.setFillColor(UNCOMMON_FILL_COLOR)
                    .setFillOpacity(UNCOMMON_FILL_OPACITY)
                    .setStrokeColor(UNCOMMON_STROKE_COLOR)
                    .setStrokeOpacity(UNCOMMON_STROKE_OPACITY)
                    .setStrokeWidth(UNCOMMON_STROKE_WIDTH)
            }
            SpawnRarity.RARE -> {
                shape.setFillColor(RARE_FILL_COLOR)
                    .setFillOpacity(RARE_FILL_OPACITY)
                    .setStrokeColor(RARE_STROKE_COLOR)
                    .setStrokeOpacity(RARE_STROKE_OPACITY)
                    .setStrokeWidth(RARE_STROKE_WIDTH)
            }
            SpawnRarity.ULTRA_RARE -> {
                shape.setFillColor(ULTRA_RARE_FILL_COLOR)
                    .setFillOpacity(ULTRA_RARE_FILL_OPACITY)
                    .setStrokeColor(ULTRA_RARE_STROKE_COLOR)
                    .setStrokeOpacity(ULTRA_RARE_STROKE_OPACITY)
                    .setStrokeWidth(ULTRA_RARE_STROKE_WIDTH)
            }
        }
        shape.setStrokePosition(ShapeProperties.StrokePosition.CENTER)
        return shape
    }

    /**
     * Génère une infobulle (tooltip) richement formatée pour le survol JourneyMap.
     */
    fun buildTooltip(
        species: Species,
        rarity: SpawnRarity,
        matchingRecipes: List<PokemonSpawnRecipe>,
        chunkCount: Int = 0
    ): String {
        val lines = mutableListOf<String>()
        val dexStr = String.format("#%04d", species.nationalPokedexNumber)
        lines.add("§6=== §e${species.translatedName.string} §7($dexStr) §6===")

        val rarityBadge = "${rarity.formatCode}● ${rarity.displayName}"
        val maxWeight = matchingRecipes.maxOfOrNull { it.weight } ?: 0f
        lines.add("§f• Rareté : $rarityBadge §7(Poids max: ${String.format("%.1f", maxWeight)})")

        val bestRecipe = matchingRecipes.maxByOrNull { it.weight } ?: matchingRecipes.firstOrNull()
        if (bestRecipe != null) {
            lines.add("§f• Niveaux : §aLv. ${bestRecipe.levelMin} §7- §aLv. ${bestRecipe.levelMax}")
            val timeStr = formatTime(bestRecipe.timeRange)
            val weatherStr = formatWeather(bestRecipe.weather)
            val ctxStr = formatContext(bestRecipe.context)
            lines.add("§f• Conditions : §e$timeStr §7| §b$weatherStr §7| §d$ctxStr")
        }

        if (chunkCount > 0) {
            lines.add("§f• Chunks éligibles : §e$chunkCount chunks fusionnés")
        }

        val biomes = matchingRecipes.flatMap { it.biomes }.toSet()
        if (biomes.isNotEmpty()) {
            val sampleBiomes = biomes.take(3).joinToString(", ") { b ->
                b.substringAfter(":").replace("_", " ").replaceFirstChar { it.uppercase() }
            }
            val extra = if (biomes.size > 3) " +${biomes.size - 3}" else ""
            lines.add("§f• Biomes : §3$sampleBiomes$extra §7(${biomes.size} configurés)")
        } else {
            lines.add("§f• Biomes : §7Tous biomes de la dimension")
        }

        return lines.joinToString("\n")
    }

    private fun formatContext(ctx: String): String {
        return when (ctx.lowercase()) {
            "grounded" -> "Sol"
            "submerged" -> "Sous l'eau"
            "surface" -> "Surface eau"
            "air" -> "Air / Vol"
            "seafloor" -> "Fond marin"
            else -> ctx.replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatTime(time: String): String {
        return when (time.lowercase()) {
            "day" -> "Jour"
            "night" -> "Nuit"
            "dawn" -> "Aube"
            "dusk" -> "Crépuscule"
            else -> "Tous"
        }
    }

    private fun formatWeather(weather: String): String {
        return when (weather.lowercase()) {
            "clear" -> "Dégagé"
            "rain" -> "Pluie"
            "thundering" -> "Orage"
            else -> "Tous"
        }
    }
}
