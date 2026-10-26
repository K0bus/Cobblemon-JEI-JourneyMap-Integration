package fr.k0bus.cobblemon_jei.domain.expedition

import fr.k0bus.cobblemon_jei.domain.goals.PokemonTarget
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import java.util.UUID

/**
 * Type d'opportunité d'expédition suggérée de façon proactive.
 */
enum class SuggestionType {
    POKEDEX_OPPORTUNITY,    // Zone concentrant plusieurs Pokémon non capturés
    WEATHER_TRIGGER,        // Condition météo active en direct (pluie/orage/neige)
    TIME_TRIGGER,           // Tranche horaire active en direct (nuit/aube/crépuscule)
    EVOLUTION_LINE,         // Famille ou pré-évolution manquante
    THEMATIC                // Thématique (ex: Aquatique, Grottes, Montagne)
}

/**
 * Correspondance d'un Pokémon ciblé dans une zone géographique.
 */
data class ZoneTargetMatch(
    val target: PokemonTarget,
    val spawnConfidence: String = "EXACT_DATA",
    val requiredWeather: String? = null,
    val requiredTime: String? = null,
    val isPrimaryBiome: Boolean = true
)

/**
 * Zone géographique regroupant un ou plusieurs chunks explorés compatibles avec des objectifs.
 */
data class ExpeditionZone(
    val id: String,
    val name: String,
    val dimension: ResourceLocation,
    val centerPos: BlockPos,
    val sampleChunks: Set<ChunkPos>,
    val matchingTargets: List<ZoneTargetMatch>,
    val score: Double,
    val distanceToPlayer: Double,
    val weatherRequirements: Set<String> = emptySet(),
    val timeRequirements: Set<String> = emptySet(),
    val isSubterranean: Boolean = false,
    val yLevel: Int? = null
) {
    val targetCount: Int get() = matchingTargets.size
}

/**
 * Plan d'expédition consolidé contenant les cibles et les zones classées par pertinence.
 */
data class ExpeditionPlan(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val targets: List<PokemonTarget>,
    val rankedZones: List<ExpeditionZone>,
    val generatedAt: Long = System.currentTimeMillis()
)

/**
 * Suggestion automatique d'expédition générée sans configuration manuelle par le joueur.
 */
data class ExpeditionSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val description: String,
    val type: SuggestionType,
    val targets: List<PokemonTarget>,
    val primaryZone: ExpeditionZone,
    val isConditionActiveNow: Boolean = false
)
