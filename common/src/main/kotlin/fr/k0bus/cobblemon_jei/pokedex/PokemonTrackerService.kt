package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.requirements.FriendshipRequirement
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement
import fr.k0bus.cobblemon_jei.data.PokemonEvolutionDataLoader
import fr.k0bus.cobblemon_jei.recipe.PokemonEvolutionRecipe
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

/**
 * Statut d'un Pokémon dans le Pokédex du joueur.
 */
enum class PokedexStatus {
    CAUGHT,      // Déjà capturé / enregistré ou possédé
    ENCOUNTERED, // Vu / combattu dans la nature
    MISSING      // Non découvert / manquant
}

/**
 * Faisabilité d'une évolution pour obtenir un Pokémon manquant.
 */
enum class EvolutionFeasibility {
    READY_TO_EVOLVE,          // Pokémon possédé dans PC/Équipe + objets possédés + niveau atteint !
    CAN_EVOLVE_WITH_PC,       // Pokémon possédé dans PC/Équipe, mais objets ou niveaux manquants
    MISSING_BASE_POKEMON      // Le joueur ne possède pas encore le Pokémon de base
}

/**
 * Informations sur un Pokémon possédé par le joueur (dans l'équipe ou le PC).
 */
data class OwnedPokemonInfo(
    val pokemon: Pokemon,
    val location: String,
    val isParty: Boolean,
    val boxIndex: Int = -1
) {
    val level: Int get() = pokemon.level
    val friendship: Int get() = pokemon.friendship
    val heldItem: ItemStack get() = pokemon.heldItem()
}

/**
 * Données de suivi pour une espèce de Pokémon donnée.
 */
data class TrackedPokemon(
    val species: Species,
    val status: PokedexStatus,
    val ownedInstances: List<OwnedPokemonInfo>
) {
    val nationalDexNumber: Int get() = species.nationalPokedexNumber
    val isCaught: Boolean get() = status == PokedexStatus.CAUGHT
    val isEncountered: Boolean get() = status == PokedexStatus.ENCOUNTERED
    val isMissing: Boolean get() = status == PokedexStatus.MISSING
    val ownedCount: Int get() = ownedInstances.size
}

/**
 * Représente un objectif d'évolution permettant d'obtenir un Pokémon manquant.
 */
data class EvolutionGoal(
    val sourceSpecies: Species,
    val sourceForm: FormData,
    val targetSpecies: Species,
    val targetForm: FormData,
    val recipe: PokemonEvolutionRecipe,
    val feasibility: EvolutionFeasibility,
    val ownedSourcePokemons: List<OwnedPokemonInfo>,
    val requiredItems: List<ItemStack>,
    val missingItems: List<ItemStack>,
    val minLevel: Int?,
    val isLevelMet: Boolean,
    val minFriendship: Int?,
    val isFriendshipMet: Boolean,
    val requirementsSummary: String,
    val additionalConditions: List<String>,
    val targetStatus: PokedexStatus
) {
    val hasSourceInPC: Boolean get() = ownedSourcePokemons.isNotEmpty()
    val hasAllRequiredItems: Boolean get() = missingItems.isEmpty()

    val sourceLocationSummary: String get() = if (ownedSourcePokemons.isEmpty()) {
        "Non possédé"
    } else {
        val count = ownedSourcePokemons.size
        val firstLoc = ownedSourcePokemons.first().location
        if (count == 1) firstLoc else "$firstLoc (+${count - 1})"
    }
}

/**
 * Statistiques globales du Pokédex du joueur.
 */
data class PokedexTrackerStats(
    val totalSpecies: Int,
    val caughtCount: Int,
    val encounteredCount: Int,
    val missingCount: Int,
    val readyEvolutionsCount: Int,
    val pcEvolutionsCount: Int
) {
    val completionPercent: Float get() = if (totalSpecies > 0) (caughtCount.toFloat() / totalSpecies.toFloat()) * 100f else 0f
}

/**
 * Service centralisé pour le suivi du Pokédex, l'inspection des stocks PC/équipe
 * et l'identification des évolutions réalisables pour compléter le Pokédex.
 */
object PokemonTrackerService {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/PokedexTracker")

    private var trackedSpeciesList: List<TrackedPokemon> = emptyList()
    private var trackedSpeciesMap: Map<String, TrackedPokemon> = emptyMap()
    private var evolutionGoalsList: List<EvolutionGoal> = emptyList()
    private var lastScanTime: Long = 0L

    /**
     * Analyse et met à jour l'ensemble des données de Pokédex et d'évolutions.
     */
    @Synchronized
    fun refresh() {
        PokemonEvolutionDataLoader.ensureSpeciesLoaded()

        val allSpecies = PokemonSpecies.species.toList()
            .sortedBy { it.nationalPokedexNumber }

        val ownedMap = scanPlayerOwnedPokemons()
        val inventoryItems = scanPlayerInventory()

        // 1. Détermination du statut de chaque espèce
        val trackedList = mutableListOf<TrackedPokemon>()
        val trackedMap = mutableMapOf<String, TrackedPokemon>()

        for (sp in allSpecies) {
            val key = getSpeciesKey(sp)
            val owned = ownedMap[key] ?: emptyList()

            // Connaissance enregistrée au Pokédex du joueur
            val dexProgress = try {
                CobblemonClient.clientPokedexData.getHighestKnowledgeForSpecies(sp.resourceIdentifier)
            } catch (_: Throwable) {
                PokedexEntryProgress.NONE
            }

            val status = when {
                dexProgress == PokedexEntryProgress.CAUGHT || owned.isNotEmpty() -> PokedexStatus.CAUGHT
                dexProgress == PokedexEntryProgress.ENCOUNTERED -> PokedexStatus.ENCOUNTERED
                else -> PokedexStatus.MISSING
            }

            val tracked = TrackedPokemon(sp, status, owned)
            trackedList.add(tracked)
            trackedMap[key] = tracked
        }

        trackedSpeciesList = trackedList
        trackedSpeciesMap = trackedMap

        // 2. Analyse des évolutions vers les Pokémon manquants
        evolutionGoalsList = computeEvolutionGoals(trackedMap, inventoryItems)
        lastScanTime = System.currentTimeMillis()

        logger.debug(
            "Pokédex Tracker actualisé : {} espèces, {} capturées, {} évolutions cibles.",
            trackedList.size,
            trackedList.count { it.isCaught },
            evolutionGoalsList.size
        )
    }

    /**
     * Retourne la liste ordonnée de toutes les espèces suivies.
     */
    fun getAllTracked(): List<TrackedPokemon> {
        if (trackedSpeciesList.isEmpty() || System.currentTimeMillis() - lastScanTime > 2000L) {
            refresh()
        }
        return trackedSpeciesList
    }

    /**
     * Retourne uniquement les Pokémon non capturés (manquants au Pokédex).
     */
    fun getMissingTracked(): List<TrackedPokemon> {
        return getAllTracked().filter { it.isMissing || it.isEncountered }
    }

    /**
     * Retourne uniquement les Pokémon déjà capturés ou possédés.
     */
    fun getCaughtTracked(): List<TrackedPokemon> {
        return getAllTracked().filter { it.isCaught }
    }

    /**
     * Retourne les objectifs d'évolutions triés par faisabilité :
     * 1. Faisables immédiatement (Pokémon en stock + objets/niveaux validés)
     * 2. Pokémon en stock mais prérequis à terminer
     * 3. Pokémon de base non possédé
     */
    fun getEvolutionGoals(): List<EvolutionGoal> {
        if (trackedSpeciesList.isEmpty() || System.currentTimeMillis() - lastScanTime > 2000L) {
            refresh()
        }
        return evolutionGoalsList
    }

    /**
     * Retourne les statistiques globales actuelles.
     */
    fun getStats(): PokedexTrackerStats {
        val all = getAllTracked()
        val caught = all.count { it.isCaught }
        val encountered = all.count { it.isEncountered }
        val missing = all.count { it.isMissing }
        val goals = getEvolutionGoals()
        val ready = goals.count { it.feasibility == EvolutionFeasibility.READY_TO_EVOLVE }
        val pcReady = goals.count { it.feasibility == EvolutionFeasibility.CAN_EVOLVE_WITH_PC }

        return PokedexTrackerStats(
            totalSpecies = all.size,
            caughtCount = caught,
            encounteredCount = encountered,
            missingCount = missing,
            readyEvolutionsCount = ready,
            pcEvolutionsCount = pcReady
        )
    }

    /**
     * Scanne l'équipe du joueur et l'ensemble des boîtes de PC accessibles.
     */
    private fun scanPlayerOwnedPokemons(): Map<String, List<OwnedPokemonInfo>> {
        val map = mutableMapOf<String, MutableList<OwnedPokemonInfo>>()

        // 1. Équipe actuelle (Party)
        try {
            val party = CobblemonClient.storage.party
            for ((index, pokemon) in party.slots.withIndex()) {
                if (pokemon != null) {
                    val key = getSpeciesKey(pokemon.species)
                    val info = OwnedPokemonInfo(
                        pokemon = pokemon,
                        location = "Équipe (Slot ${index + 1})",
                        isParty = true
                    )
                    map.getOrPut(key) { mutableListOf() }.add(info)
                }
            }
        } catch (e: Throwable) {
            logger.debug("Erreur lecture équipe client : {}", e.message)
        }

        // 2. Boîtes de PC du client
        try {
            for (pc in CobblemonClient.storage.pcStores.values) {
                for ((boxIdx, box) in pc.boxes.withIndex()) {
                    val boxName = box.name?.string ?: "Boîte ${boxIdx + 1}"
                    for (pokemon in box.slots) {
                        if (pokemon != null) {
                            val key = getSpeciesKey(pokemon.species)
                            val info = OwnedPokemonInfo(
                                pokemon = pokemon,
                                location = "PC : $boxName",
                                isParty = false,
                                boxIndex = boxIdx
                            )
                            map.getOrPut(key) { mutableListOf() }.add(info)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logger.debug("Erreur lecture PC client : {}", e.message)
        }

        // 3. Fallback solo : si en solo et PC client encore non synchronisé
        try {
            val client = Minecraft.getInstance()
            val server = client.singleplayerServer
            val playerUUID = client.player?.uuid
            if (server != null && playerUUID != null && CobblemonClient.storage.pcStores.isEmpty()) {
                val serverPlayer = server.playerList.getPlayer(playerUUID)
                if (serverPlayer != null) {
                    val pc = Cobblemon.storage.getPC(serverPlayer)
                    for ((boxIdx, box) in pc.boxes.withIndex()) {
                        val boxName = box.name ?: "Boîte ${boxIdx + 1}"
                        for (pokemon in box) {
                            if (pokemon != null) {
                                val key = getSpeciesKey(pokemon.species)
                                val info = OwnedPokemonInfo(
                                    pokemon = pokemon,
                                    location = "PC : $boxName",
                                    isParty = false,
                                    boxIndex = boxIdx
                                )
                                map.getOrPut(key) { mutableListOf() }.add(info)
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        return map
    }

    /**
     * Scanne les objets présents dans l'inventaire du joueur Minecraft.
     */
    private fun scanPlayerInventory(): List<ItemStack> {
        val player = Minecraft.getInstance().player ?: return emptyList()
        val items = mutableListOf<ItemStack>()

        for (item in player.inventory.items) {
            if (!item.isEmpty) items.add(item)
        }
        for (item in player.inventory.offhand) {
            if (!item.isEmpty) items.add(item)
        }

        return items
    }

    /**
     * Calcule la liste de tous les objectifs d'évolutions menant à des Pokémon manquants.
     */
    private fun computeEvolutionGoals(
        trackedMap: Map<String, TrackedPokemon>,
        inventoryItems: List<ItemStack>
    ): List<EvolutionGoal> {
        val allRecipes: List<PokemonEvolutionRecipe> = PokemonEvolutionDataLoader.loadEvolutionRecipes()
        val goals = mutableListOf<EvolutionGoal>()
        val seen = mutableSetOf<String>()

        for (recipe in allRecipes) {
            val targetKey = getSpeciesKey(recipe.targetSpecies)
            val targetTracked = trackedMap[targetKey]
            val targetStatus = targetTracked?.status ?: PokedexStatus.MISSING

            // On s'intéresse en priorité aux Pokémon cibles qui n'ont PAS encore été capturés
            if (targetStatus == PokedexStatus.CAUGHT) {
                continue
            }

            val sourceKey = getSpeciesKey(recipe.sourceSpecies)
            val sourceTracked = trackedMap[sourceKey]
            val ownedSources = sourceTracked?.ownedInstances ?: emptyList()

            val goalKey = "$sourceKey->${recipe.sourceForm.name}:$targetKey:${recipe.targetForm.name}:${recipe.requirementsSummary}"
            if (!seen.add(goalKey)) continue

            // 1. Analyse des objets requis
            val missingItems = mutableListOf<ItemStack>()
            val reqItems: List<ItemStack> = recipe.requiredItems
            for (reqStack in reqItems) {
                val hasInInventory = inventoryItems.any { invStack ->
                    invStack.item == reqStack.item && invStack.count >= reqStack.count
                }
                val hasHeld = ownedSources.any { owned ->
                    owned.heldItem.item == reqStack.item
                }
                if (!hasInInventory && !hasHeld) {
                    missingItems.add(reqStack)
                }
            }
            val hasAllItems = missingItems.isEmpty()

            // 2. Analyse des niveaux et conditions
            val (minLevel, minFriendship) = inspectEvolutionConditions(recipe)
            val isLevelMet = if (minLevel != null) {
                ownedSources.any { it.level >= minLevel }
            } else {
                true
            }

            val isFriendshipMet = if (minFriendship != null) {
                ownedSources.any { it.friendship >= minFriendship }
            } else {
                true
            }

            // 3. Détermination de la faisabilité
            val feasibility = when {
                ownedSources.isEmpty() -> EvolutionFeasibility.MISSING_BASE_POKEMON
                hasAllItems && isLevelMet && isFriendshipMet -> EvolutionFeasibility.READY_TO_EVOLVE
                else -> EvolutionFeasibility.CAN_EVOLVE_WITH_PC
            }

            goals.add(
                EvolutionGoal(
                    sourceSpecies = recipe.sourceSpecies,
                    sourceForm = recipe.sourceForm,
                    targetSpecies = recipe.targetSpecies,
                    targetForm = recipe.targetForm,
                    recipe = recipe,
                    feasibility = feasibility,
                    ownedSourcePokemons = ownedSources,
                    requiredItems = recipe.requiredItems,
                    missingItems = missingItems,
                    minLevel = minLevel,
                    isLevelMet = isLevelMet,
                    minFriendship = minFriendship,
                    isFriendshipMet = isFriendshipMet,
                    requirementsSummary = recipe.requirementsSummary,
                    additionalConditions = recipe.additionalConditions,
                    targetStatus = targetStatus
                )
            )
        }

        // Tri : Faisable immédiatement d'abord, puis Pokémon en stock, puis Pokémon manquant, puis numéro Dex cible
        return goals.sortedWith(
            compareBy<EvolutionGoal> { it.feasibility.ordinal }
                .thenBy { it.targetSpecies.nationalPokedexNumber }
                .thenBy { it.sourceSpecies.nationalPokedexNumber }
        )
    }

    /**
     * Inspecte les exigences réelles de l'évolution pour extraire le niveau min et le bonheur min.
     */
    private fun inspectEvolutionConditions(recipe: PokemonEvolutionRecipe): Pair<Int?, Int?> {
        var minLevel: Int? = null
        var minFriendship: Int? = null

        // 1. Essai direct via l'instance native de Cobblemon si présente
        try {
            val evos = recipe.sourceForm.evolutions
            for (evo in evos) {
                val resultSpeciesName = evo.result.species ?: continue
                if (resultSpeciesName.equals(recipe.targetSpecies.name, ignoreCase = true) ||
                    resultSpeciesName.equals(recipe.targetSpecies.resourceIdentifier.path, ignoreCase = true)
                ) {
                    for (req in evo.requirements) {
                        if (req is LevelRequirement && req.minLevel > 1) {
                            minLevel = req.minLevel
                        } else if (req is FriendshipRequirement) {
                            minFriendship = req.amount
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 2. Fallback sur le texte si non trouvé
        if (minLevel == null) {
            val text = recipe.requirementsSummary + " " + recipe.additionalConditions.joinToString(" ")
            val levelRegex = Regex("""(?:Niveau|Level)\s*[≥>=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
            val match = levelRegex.find(text)
            if (match != null) {
                minLevel = match.groupValues[1].toIntOrNull()
            }
        }

        if (minFriendship == null) {
            val text = recipe.requirementsSummary + " " + recipe.additionalConditions.joinToString(" ")
            val friendRegex = Regex("""(?:Bonheur|Friendship)\s*[≥>=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
            val match = friendRegex.find(text)
            if (match != null) {
                minFriendship = match.groupValues[1].toIntOrNull()
            }
        }

        return Pair(minLevel, minFriendship)
    }

    private fun getSpeciesKey(species: Species): String {
        return try {
            species.resourceIdentifier.toString()
        } catch (_: Throwable) {
            "cobblemon:${species.name.lowercase().replace(" ", "_")}"
        }
    }
}
