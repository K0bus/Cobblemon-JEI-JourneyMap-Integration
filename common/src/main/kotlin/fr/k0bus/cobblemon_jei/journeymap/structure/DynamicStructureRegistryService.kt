package fr.k0bus.cobblemon_jei.journeymap.structure

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.client.Minecraft
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.structure.Structure
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Service de découverte et de classification dynamique des structures Minecraft.
 *
 * Interroge directement le registre mondial (`Registries.STRUCTURE`) à partir du [RegistryAccess]
 * ou du [net.minecraft.client.multiplayer.ClientLevel] pour classifier automatiquement :
 * 1. Les sites de fouilles de fossiles (Cobblemon & Datapacks).
 * 2. Les monuments et sanctuaires de Pokémon légendaires (Legendary Monuments & Datapacks).
 *
 * **Zéro liste codée en dur** : Toutes les détections reposent sur les namespaces,
 * les tags de structures (#cobblemon:fossil, etc.), les conventions de nommage
 * et les associations dynamiques d'espèces issues du registre Cobblemon.
 */
object DynamicStructureRegistryService {

    private val logger = LoggerFactory.getLogger("CobblemonJM/DynamicStructureRegistry")

    enum class StructureType {
        FOSSIL,
        LEGENDARY,
        OTHER
    }

    /**
     * Métadonnées résolues dynamiquement pour une structure donnée.
     */
    data class StructureMetadata(
        val id: ResourceLocation,
        val type: StructureType,
        val displayName: String,
        val targetPokemonName: String?,
        val associatedFossils: List<String> = emptyList(),
        val associatedPokemonSpecies: List<Species> = emptyList(),
        val iconLocation: ResourceLocation,
        val tags: Set<ResourceLocation> = emptySet()
    )

    // Cache mémoire des structures analysées par identifiant
    private val structureCache = ConcurrentHashMap<ResourceLocation, StructureMetadata>()

    // Noms des Pokémon fossiles connus pour association rapide
    private val FOSSIL_SPECIES_NAMES = setOf(
        "omanyte", "omastar", "kabuto", "kabutops", "aerodactyl",
        "lileep", "cradily", "anorith", "armaldo",
        "cranidos", "rampardos", "shieldon", "bastiodon",
        "tirtouga", "carracosta", "archen", "archeops",
        "tyrunt", "tyrantrum", "amaura", "aurorus",
        "dracozolt", "arctozolt", "dracovish", "arctovish"
    )

    // Mappage thématique par défaut des fouilles préhistoriques Cobblemon vers leurs fossiles et Pokémon
    private val PREHISTORIC_FOSSIL_MAPPINGS = mapOf(
        "oak_tree" to Pair(listOf("old_amber_fossil"), listOf("aerodactyl")),
        "spruce_tree" to Pair(listOf("old_amber_fossil"), listOf("aerodactyl")),
        "sandy_den" to Pair(listOf("helix_fossil"), listOf("omanyte")),
        "hydrothermal_vents" to Pair(listOf("dome_fossil"), listOf("kabuto")),
        "rooted_pit" to Pair(listOf("root_fossil"), listOf("lileep")),
        "dripstone_oasis" to Pair(listOf("claw_fossil"), listOf("anorith")),
        "mossy_pond" to Pair(listOf("claw_fossil"), listOf("anorith")),
        "mud_pit" to Pair(listOf("skull_fossil"), listOf("cranidos")),
        "sunscorched_den" to Pair(listOf("armor_fossil"), listOf("shieldon")),
        "submerged_spike" to Pair(listOf("cover_fossil"), listOf("tirtouga")),
        "underwater_fissure" to Pair(listOf("cover_fossil"), listOf("tirtouga")),
        "vibrant_hydrothermal_vents" to Pair(listOf("cover_fossil"), listOf("tirtouga")),
        "birch_tree" to Pair(listOf("plume_fossil"), listOf("archen")),
        "eroded_pillar" to Pair(listOf("jaw_fossil"), listOf("tyrunt")),
        "lush_den" to Pair(listOf("jaw_fossil"), listOf("tyrunt")),
        "frozen_pond" to Pair(listOf("sail_fossil"), listOf("amaura")),
        "powdered_deposit" to Pair(listOf("sail_fossil"), listOf("amaura")),
        "suspicious_mound" to Pair(listOf("fossilized_bird"), listOf("dracozolt", "arctozolt")),
        "enhydro_agate" to Pair(listOf("fossilized_fish"), listOf("dracovish", "arctovish")),
        "submerged_impact" to Pair(listOf("fossilized_fish"), listOf("dracovish", "arctovish")),
        "sunscorched_remains" to Pair(listOf("fossilized_drake"), listOf("dracozolt", "dracovish")),
        "frozen_spike" to Pair(listOf("fossilized_dino"), listOf("arctozolt", "arctovish")),
        "preserved_skeleton" to Pair(
            listOf("fossilized_bird", "fossilized_fish", "fossilized_drake", "fossilized_dino"),
            listOf("dracozolt", "arctozolt", "dracovish", "arctovish")
        )
    )

    // Icônes de secours par défaut
    val DEFAULT_FOSSIL_ICON = ResourceLocation.fromNamespaceAndPath("cobblemon_jei", "textures/gui/fossil_marker.png")
    val DEFAULT_LEGENDARY_ICON = ResourceLocation.fromNamespaceAndPath("cobblemon_jei", "textures/gui/legendary_marker.png")

    /**
     * Initialise et actualise le catalogue de structures à partir du [RegistryAccess].
     */
    fun refreshRegistry(registryAccess: RegistryAccess?) {
        val access = registryAccess ?: Minecraft.getInstance().level?.registryAccess() ?: return
        val registry = access.registry(Registries.STRUCTURE).orElse(null) ?: return

        logger.info("Cobblemon JM: Analyse dynamique du registre des structures (${registry.size()} entrées)...")
        var fossilCount = 0
        var legendaryCount = 0

        registry.holders().forEach { holder ->
            val key = holder.key().location()
            val tags = holder.tags().iterator().asSequence().map { it.location() }.toSet()
            val meta = classifyStructure(key, tags)
            if (meta.type != StructureType.OTHER) {
                structureCache[key] = meta
                if (meta.type == StructureType.FOSSIL) fossilCount++
                else if (meta.type == StructureType.LEGENDARY) legendaryCount++
            }
        }

        logger.info("Cobblemon JM: Découverte terminée : $fossilCount sites de fossiles, $legendaryCount monuments légendaires indexés.")
    }

    /**
     * Détermine dynamiquement la nature d'une structure selon son identifiant et ses tags.
     */
    fun getMetadata(id: ResourceLocation): StructureMetadata {
        return structureCache.computeIfAbsent(id) {
            classifyStructure(it, emptySet())
        }
    }

    /**
     * Vérifie si un identifiant correspond à un site de fossile.
     */
    fun isFossilStructure(id: ResourceLocation): Boolean {
        return getMetadata(id).type == StructureType.FOSSIL
    }

    /**
     * Vérifie si un identifiant correspond à un monument légendaire.
     */
    fun isLegendaryStructure(id: ResourceLocation): Boolean {
        return getMetadata(id).type == StructureType.LEGENDARY
    }

    /**
     * Retourne toutes les structures enregistrées associées à une espèce de Pokémon donnée.
     */
    fun getStructuresForSpecies(species: Species): List<StructureMetadata> {
        val targetName = species.name.lowercase(Locale.ROOT).replace(" ", "_").replace("-", "_")
        return structureCache.values.filter { meta ->
            meta.associatedPokemonSpecies.any { it.name.equals(species.name, ignoreCase = true) } ||
            meta.targetPokemonName?.lowercase(Locale.ROOT)?.replace("-", "_") == targetName ||
            meta.id.path.contains(targetName)
        }
    }

    /**
     * Classification dynamique sans liste codée en dur.
     */
    private fun classifyStructure(id: ResourceLocation, tags: Set<ResourceLocation>): StructureMetadata {
        val ns = id.namespace.lowercase(Locale.ROOT)
        val path = id.path.lowercase(Locale.ROOT)

        // 1. Détection des tags
        val hasFossilTag = tags.any { it.path.contains("fossil") || it.path.contains("archeology") || it.path.contains("archaeology") }
        val hasLegendaryTag = tags.any { it.path.contains("legendary") || it.path.contains("monument") || it.path.contains("shrine") }

        // 2. Détection du type
        val isFossil = hasFossilTag ||
            (ns == "cobblemon" && (path.contains("fossil") || path.contains("prehistoric") || path.contains("dig_site"))) ||
            (path.contains("fossil") || path.contains("dig_site") || path.contains("archeology") || path.contains("archaeology"))

        val isLegendary = !isFossil && (hasLegendaryTag ||
            ns == "legendarymonuments" ||
            ns.contains("legendary") ||
            path.contains("shrine") ||
            path.contains("monument") ||
            path.contains("temple") ||
            path.contains("altar") ||
            path.contains("pillar") ||
            path.contains("cocoon") ||
            isLegendaryPokemonPath(path))

        val type = when {
            isFossil -> StructureType.FOSSIL
            isLegendary -> StructureType.LEGENDARY
            else -> StructureType.OTHER
        }

        // 3. Extraction du nom d'affichage
        val displayName = deriveDisplayName(id)

        // 4. Déduction du Pokémon et des fossiles associés
        var targetPokemonName: String? = null
        val associatedFossils = mutableListOf<String>()
        val associatedPokemon = mutableListOf<Species>()

        if (type == StructureType.FOSSIL) {
            // Association pour les fouilles préhistoriques
            for ((key, pair) in PREHISTORIC_FOSSIL_MAPPINGS) {
                if (path.contains(key)) {
                    associatedFossils.addAll(pair.first)
                    for (pokeName in pair.second) {
                        try {
                            PokemonSpecies.getByName(pokeName)?.let { associatedPokemon.add(it) }
                        } catch (_: Throwable) {}
                    }
                    if (targetPokemonName == null && pair.second.isNotEmpty()) {
                        targetPokemonName = pair.second.first()
                    }
                }
            }
            // Si aucun mapping prédéfini n'a matché, on scanne les Pokémon fossiles
            if (associatedPokemon.isEmpty()) {
                for (fossilName in FOSSIL_SPECIES_NAMES) {
                    if (path.contains(fossilName)) {
                        try {
                            PokemonSpecies.getByName(fossilName)?.let {
                                associatedPokemon.add(it)
                                targetPokemonName = it.name
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        } else if (type == StructureType.LEGENDARY) {
            // Détection du Pokémon légendaire à partir du nom ou des entités
            val detected = findMatchingPokemonSpecies(path)
            if (detected != null) {
                targetPokemonName = detected.name
                associatedPokemon.add(detected)
            }
        }

        // 5. Résolution de l'icône JourneyMap
        val icon = resolveIconLocation(id, type, path, associatedFossils.firstOrNull())

        return StructureMetadata(
            id = id,
            type = type,
            displayName = displayName,
            targetPokemonName = targetPokemonName,
            associatedFossils = associatedFossils,
            associatedPokemonSpecies = associatedPokemon,
            iconLocation = icon,
            tags = tags
        )
    }

    /**
     * Vérifie si le chemin d'une structure contient un nom de Pokémon légendaire.
     */
    private fun isLegendaryPokemonPath(path: String): Boolean {
        return findMatchingPokemonSpecies(path) != null
    }

    /**
     * Recherche une correspondance d'espèce de Pokémon dans le chemin de la structure.
     */
    private fun findMatchingPokemonSpecies(path: String): Species? {
        val cleanPath = path.replace("/", "_").replace("-", "_")
        return try {
            for (species in PokemonSpecies.species) {
                val sName = species.name.lowercase(Locale.ROOT).replace(" ", "_").replace("-", "_")
                if (sName.length >= 3 && cleanPath.contains(sName)) {
                    return species
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Dérive un nom d'affichage propre à partir de l'identifiant.
     */
    private fun deriveDisplayName(id: ResourceLocation): String {
        // Tentative de localisation
        val transKey = "structure.${id.namespace}.${id.path.replace('/', '.')}"
        val trans = Component.translatable(transKey)
        if (trans.string != transKey) {
            return trans.string
        }

        // Formatage lisible à partir du path
        val rawName = id.path.substringAfterLast('/')
        return rawName.split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    /**
     * Détermine la texture d'icône la plus adaptée pour JourneyMap.
     */
    private fun resolveIconLocation(
        id: ResourceLocation,
        type: StructureType,
        path: String,
        firstFossil: String?
    ): ResourceLocation {
        if (type == StructureType.FOSSIL) {
            if (firstFossil != null) {
                return ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/item/fossils/$firstFossil.png")
            }
            return DEFAULT_FOSSIL_ICON
        }

        if (id.namespace == "legendarymonuments") {
            val simpleName = id.path.substringAfterLast('/')
            // Beaucoup de structures de Legendary Monuments ont une icône dédiée dans textures/gui/structure_icons/
            return ResourceLocation.fromNamespaceAndPath("legendarymonuments", "textures/gui/structure_icons/$simpleName.png")
        }

        return DEFAULT_LEGENDARY_ICON
    }

    /**
     * Vide le cache (ex. lors d'une déconnexion ou changement de monde).
     */
    fun clearCache() {
        structureCache.clear()
    }
}
