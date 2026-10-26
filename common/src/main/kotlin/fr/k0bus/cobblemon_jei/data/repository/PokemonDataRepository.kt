package fr.k0bus.cobblemon_jei.data.repository

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.data.CobblemonDataScanner
import fr.k0bus.cobblemon_jei.data.PokemonEvolutionDataLoader
import fr.k0bus.cobblemon_jei.data.PokemonSpawnDataLoader
import fr.k0bus.cobblemon_jei.domain.model.*
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * Source de vérité unique pour les données de Pokémon consolidées.
 *
 * Centralise l'accès aux espèces, formes, évolutions et apparitions sauvages
 * sous forme de snapshot immuable [PokemonDataSnapshot].
 */
object PokemonDataRepository {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/DataRepository")

    private val snapshotRef = AtomicReference<PokemonDataSnapshot>(PokemonDataSnapshot.EMPTY)
    private var isInitialized = false

    /**
     * Retourne le snapshot actif en mémoire. S'il n'est pas encore initialisé,
     * déclenche le chargement immédiat.
     */
    fun getSnapshot(): PokemonDataSnapshot {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    reloadSnapshot()
                }
            }
        }
        return snapshotRef.get()
    }

    /**
     * Recharge l'intégralité des données et produit un nouveau snapshot immuable.
     * Thread-safe et idempotent.
     */
    @Synchronized
    fun reloadSnapshot(): PokemonDataSnapshot {
        logger.info("Cobblemon JEI: Début de la construction du snapshot normalisé...")
        val startTime = System.currentTimeMillis()

        // 1. S'assurer que les espèces Cobblemon et évolutions sont scannées
        PokemonEvolutionDataLoader.ensureSpeciesLoaded()

        // 2. Récupérer toutes les recettes brutes d'apparition et d'évolution existantes
        val rawSpawns = try {
            PokemonSpawnDataLoader.loadSpawnRecipes()
        } catch (t: Throwable) {
            logger.warn("Impossible de charger les apparitions pour le snapshot", t)
            emptyList()
        }

        val rawEvolutions = try {
            PokemonEvolutionDataLoader.loadEvolutionRecipes()
        } catch (t: Throwable) {
            logger.warn("Impossible de charger les évolutions pour le snapshot", t)
            emptyList()
        }

        // 3. Normaliser les entrées de spawn en DTOs de domaine
        val domainSpawns = rawSpawns.map { recipe ->
            val speciesResId = try {
                recipe.species.resourceIdentifier
            } catch (_: Throwable) {
                ResourceLocation.fromNamespaceAndPath("cobblemon", recipe.species.name.lowercase())
            }

            val condition = SpawnConditionInfo(
                timeRange = recipe.timeRange,
                weather = recipe.weather,
                dimensions = recipe.dimensions.mapNotNull {
                    try { ResourceLocation.parse(it) } catch (_: Throwable) { null }
                }.toSet(),
                biomes = recipe.biomes,
                antiBiomes = recipe.antiBiomes,
                canSeeSky = recipe.canSeeSky,
                minSkyLight = recipe.minSkyLight,
                maxSkyLight = recipe.maxSkyLight,
                minLight = recipe.minLight,
                maxLight = recipe.maxLight,
                minY = recipe.minY,
                maxY = recipe.maxY,
                neededBaseBlocks = recipe.neededBaseBlocks,
                neededNearbyBlocks = recipe.neededNearbyBlocks,
                moonPhase = recipe.moonPhase,
                isSlimeChunk = recipe.isSlimeChunk,
                minLureLevel = recipe.minLureLevel,
                maxLureLevel = recipe.maxLureLevel,
                rodType = recipe.rodType,
                bait = recipe.bait,
                structures = recipe.structures,
                antiStructures = recipe.antiStructures,
                context = recipe.context
            )

            SpawnEntryInfo(
                speciesId = speciesResId,
                speciesName = recipe.species.name,
                formName = recipe.form.name,
                isNaturalSpawn = recipe.isNaturalSpawn,
                bucket = recipe.bucket,
                weight = recipe.weight,
                levelMin = recipe.levelMin,
                levelMax = recipe.levelMax,
                condition = condition,
                confidence = if (recipe.isNaturalSpawn) SpawnDataConfidence.EXACT_DATA else SpawnDataConfidence.FALLBACK_SCANNED,
                notes = recipe.notes,
                isMonumentSpawn = recipe.isMonumentSpawn,
                monumentStructureId = recipe.monumentStructureId,
                monumentPedestalBlock = recipe.monumentPedestalBlock
            )
        }

        // 4. Normaliser les évolutions en DTOs de domaine
        val domainEvolutions = rawEvolutions.map { evo ->
            val srcId = try {
                evo.sourceSpecies.resourceIdentifier
            } catch (_: Throwable) {
                ResourceLocation.fromNamespaceAndPath("cobblemon", evo.sourceSpecies.name.lowercase())
            }

            val tgtId = try {
                evo.targetSpecies.resourceIdentifier
            } catch (_: Throwable) {
                ResourceLocation.fromNamespaceAndPath("cobblemon", evo.targetSpecies.name.lowercase())
            }

            EvolutionInfo(
                sourceSpeciesId = srcId,
                sourceSpeciesName = evo.sourceSpecies.name,
                sourceFormName = evo.sourceForm.name,
                targetSpeciesId = tgtId,
                targetSpeciesName = evo.targetSpecies.name,
                targetFormName = evo.targetForm.name,
                requiredItems = evo.requiredItems,
                evolutionVariant = evo.evolutionVariant,
                requirementsSummary = evo.requirementsSummary,
                additionalConditions = evo.additionalConditions,
                requiredGender = evo.requiredGender?.name,
                requiredMove = evo.requiredMove,
                requiredMoveType = evo.requiredMoveType,
                useMoveCount = evo.useMoveCount,
                stepsAmount = evo.stepsAmount,
                criticalHits = evo.criticalHits,
                damageTaken = evo.damageTaken,
                recoilDamage = evo.recoilDamage,
                defeatTarget = evo.defeatTarget,
                defeatAmount = evo.defeatAmount,
                partyMember = evo.partyMember,
                statCompare = evo.statCompare,
                moonPhase = evo.moonPhase,
                minLevel = evo.minLevel,
                minFriendship = evo.minFriendship
            )
        }

        // 5. Indexer les espèces connues
        val speciesById = mutableMapOf<ResourceLocation, PokemonSpeciesInfo>()
        val speciesByName = mutableMapOf<String, PokemonSpeciesInfo>()

        // Indexation depuis les espèces déclarées dans Cobblemon
        val allKnownCobblemonSpecies = mutableListOf<Species>()
        try {
            allKnownCobblemonSpecies.addAll(PokemonSpecies.species)
        } catch (_: Throwable) {}

        // Si la liste est vide, récupérer celles préchargées dans PokemonEvolutionDataLoader
        if (allKnownCobblemonSpecies.isEmpty()) {
            val allNames = domainSpawns.map { it.speciesName }.toSet() + domainEvolutions.map { it.sourceSpeciesName }.toSet()
            for (name in allNames) {
                PokemonEvolutionDataLoader.findSpeciesByName(name)?.let { allKnownCobblemonSpecies.add(it) }
            }
        }

        for (sp in allKnownCobblemonSpecies) {
            val resId = try {
                sp.resourceIdentifier
            } catch (_: Throwable) {
                ResourceLocation.fromNamespaceAndPath("cobblemon", sp.name.lowercase())
            }

            val forms = try {
                sp.forms.map { form ->
                    PokemonFormInfo(
                        formName = form.name,
                        aspects = form.aspects,
                        primaryType = form.primaryType,
                        secondaryType = form.secondaryType,
                        isStandard = form.name.equals("normal", ignoreCase = true)
                    )
                }
            } catch (_: Throwable) {
                listOf(PokemonFormInfo(formName = "normal", isStandard = true))
            }

            val speciesSpawns = domainSpawns.filter { it.speciesName.equals(sp.name, ignoreCase = true) }
            val speciesEvolutions = domainEvolutions.filter { it.sourceSpeciesName.equals(sp.name, ignoreCase = true) }

            val info = PokemonSpeciesInfo(
                id = resId,
                name = sp.name,
                displayName = sp.translatedName,
                nationalDexNumber = sp.nationalPokedexNumber,
                generation = when (sp.nationalPokedexNumber) {
                    in 1..151 -> 1
                    in 152..251 -> 2
                    in 252..386 -> 3
                    in 387..493 -> 4
                    in 494..649 -> 5
                    in 650..721 -> 6
                    in 722..809 -> 7
                    in 810..905 -> 8
                    in 906..1025 -> 9
                    else -> 1
                },
                forms = forms,
                evolutions = speciesEvolutions,
                spawnEntries = speciesSpawns
            )

            speciesById[resId] = info
            speciesByName[sp.name.lowercase()] = info
        }

        val snapshot = PokemonDataSnapshot(
            speciesById = speciesById,
            speciesByName = speciesByName,
            allSpawns = domainSpawns,
            allEvolutions = domainEvolutions,
            createdAtMillis = System.currentTimeMillis()
        )

        snapshotRef.set(snapshot)
        isInitialized = true

        val elapsed = System.currentTimeMillis() - startTime
        logger.info("Cobblemon JEI: Snapshot construit avec succès en ${elapsed}ms : ${speciesById.size} espèces, ${domainSpawns.size} règles de spawn, ${domainEvolutions.size} évolutions.")
        return snapshot
    }

    /**
     * Recherche une fiche d'espèce par son ResourceLocation.
     */
    fun getSpecies(id: ResourceLocation): PokemonSpeciesInfo? = getSnapshot().getSpecies(id)

    /**
     * Recherche une fiche d'espèce par son nom interne.
     */
    fun getSpeciesByName(name: String): PokemonSpeciesInfo? = getSnapshot().getSpeciesByName(name)

    /**
     * Récupère les règles d'apparition d'un Pokémon.
     */
    fun getSpawnsForSpecies(speciesName: String, formName: String? = null): List<SpawnEntryInfo> =
        getSnapshot().getSpawnsForSpecies(speciesName, formName)

    /**
     * Récupère les évolutions d'un Pokémon.
     */
    fun getEvolutionsForSpecies(speciesName: String, formName: String? = null): List<EvolutionInfo> =
        getSnapshot().getEvolutionsForSpecies(speciesName, formName)
}
