package fr.k0bus.cobblemon_jei.journeymap

import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat
import fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader
import fr.k0bus.cobblemon_jei.pokedex.PokemonTrackingManager
import fr.k0bus.cobblemon_jei.pokedex.TrackingMode
import net.minecraft.core.BlockPos
import java.util.UUID

/**
 * Moteur de clustering spatial des Pokémon détectés pour JourneyMap.
 *
 * Règles absolues :
 * 1. Les Pokémon **Shiny** ne sont JAMAIS regroupés (conservent leur marqueur individuel or étincelant).
 * 2. Les Pokémon **Légendaires** ne sont JAMAIS regroupés.
 * 3. Les Pokémon de rareté **Ultra-Rare** ne sont JAMAIS regroupés.
 * 4. La **cible active traquée** en mode unique (TrackingMode.SINGLE) n'est JAMAIS regroupée.
 * 5. Seuls les Pokémon ordinaires (communs/non prioritaires) très proches (distance <= CLUSTER_DISTANCE_BLOCKS)
 *    sont fusionnés dans un marqueur de groupe (ex: "Roucool x3").
 */
object RadarClusteringEngine {

    const val CLUSTER_DISTANCE_BLOCKS = 10.0
    private const val CLUSTER_DIST_SQ = CLUSTER_DISTANCE_BLOCKS * CLUSTER_DISTANCE_BLOCKS

    data class ClusterGroup(
        val representative: PokemonRadarService.DetectedPokemon,
        val members: MutableList<PokemonRadarService.DetectedPokemon> = mutableListOf()
    ) {
        val count: Int
            get() = members.size

        val centerPos: BlockPos
            get() {
                if (members.isEmpty()) return representative.blockPos
                var sumX = 0L
                var sumY = 0L
                var sumZ = 0L
                for (m in members) {
                    sumX += m.blockPos.x
                    sumY += m.blockPos.y
                    sumZ += m.blockPos.z
                }
                val n = members.size.toLong()
                return BlockPos((sumX / n).toInt(), (sumY / n).toInt(), (sumZ / n).toInt())
            }
    }

    /**
     * Vérifie si une entité est protégée et doit impérativement rester isolée sans jamais être clusterisée.
     */
    fun isExemptFromClustering(detected: PokemonRadarService.DetectedPokemon): Boolean {
        // 1. Shiny
        if (detected.isShiny) return true

        val speciesName = detected.species.name.lowercase()

        // 2. Légendaire (selon compatibilité Legendary Monuments ou tags)
        if (LegendaryMonumentsCompat.isCoveredSpecies(speciesName)) return true

        // 3. Cible unique traquée par l'utilisateur
        if (PokemonTrackingManager.currentMode == TrackingMode.SINGLE &&
            PokemonTrackingManager.targetSpecies?.let { PokemonTrackingManager.isSameSpecies(detected.species, it) } == true
        ) {
            return true
        }

        // 4. Rareté Ultra-Rare
        val recipes = PokemonSpawnDataLoader.getSpawnRecipesForSpecies(detected.species, detected.form)
        if (recipes.any { it.bucket.equals("ultra-rare", ignoreCase = true) || it.bucket.equals("ultra_rare", ignoreCase = true) }) {
            return true
        }

        return false
    }

    /**
     * Regroupe les entités en clusters ou éléments individuels selon les règles de proximité et d'exclusion.
     */
    fun clusterEntities(entities: Collection<PokemonRadarService.DetectedPokemon>): List<ClusterGroup> {
        val result = mutableListOf<ClusterGroup>()
        val clusterable = mutableListOf<PokemonRadarService.DetectedPokemon>()

        for (e in entities) {
            if (isExemptFromClustering(e)) {
                // Entité haute priorité / exemptée : créée immédiatement comme un cluster unitaire
                val singleGroup = ClusterGroup(e)
                singleGroup.members.add(e)
                result.add(singleGroup)
            } else {
                clusterable.add(e)
            }
        }

        // Pour les entités ordinaires, on regroupe par espèce identique et proximité
        val visited = mutableSetOf<UUID>()

        for (i in 0 until clusterable.size) {
            val a = clusterable[i]
            if (visited.contains(a.uuid)) continue
            visited.add(a.uuid)

            val group = ClusterGroup(a)
            group.members.add(a)

            for (j in i + 1 until clusterable.size) {
                val b = clusterable[j]
                if (visited.contains(b.uuid)) continue

                // Même espèce requise pour regrouper proprement (ou même famille proche)
                if (PokemonTrackingManager.isSameSpecies(a.species, b.species)) {
                    val distSq = a.blockPos.distSqr(b.blockPos)
                    if (distSq <= CLUSTER_DIST_SQ) {
                        visited.add(b.uuid)
                        group.members.add(b)
                    }
                }
            }
            result.add(group)
        }

        return result
    }
}
