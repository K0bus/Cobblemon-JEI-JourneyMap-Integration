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
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.pokemon.Gender
import net.minecraft.core.registries.BuiltInRegistries
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
 * Statut de la capacité requise pour l'évolution.
 */
enum class MoveRequirementStatus {
    NOT_REQUIRED,       // Aucune capacité requise
    KNOWN,              // Connaît déjà la capacité activement
    RELEARNABLE,        // Peut être réapprise immédiatement dans le menu
    LEVEL_UP,           // S'apprend à un niveau supérieur
    TM_IN_INVENTORY,    // CT possédée dans l'inventaire
    TM_MISSING,         // CT requise mais absente de l'inventaire
    CANNOT_LEARN        // Ne peut pas apprendre cette capacité
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
    val gender: Gender get() = pokemon.gender
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
    val targetStatus: PokedexStatus,
    val requiredGender: Gender? = null,
    val hasGenderMatch: Boolean = true,
    val requiredForm: FormData? = null,
    val hasFormMatch: Boolean = true,
    val requiredMove: String? = null,
    val requiredMoveType: String? = null,
    val moveStatus: MoveRequirementStatus = MoveRequirementStatus.NOT_REQUIRED,
    val moveLearnLevel: Int? = null,
    val stepsRequired: Int? = null,
    val useMoveCount: Int? = null,
    val specialActionSummary: String? = null
) {
    val hasSourceInPC: Boolean get() = hasFormMatch && ownedSourcePokemons.isNotEmpty()
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
     * Calcule la liste de tous les objectifs d'évolutions menant à des Pokémon manquants,
     * en consolidant les chemins pour éliminer les doublons et en vérifiant précisément
     * le sexe et les capacités (moves / CTs).
     */
    private fun computeEvolutionGoals(
        trackedMap: Map<String, TrackedPokemon>,
        inventoryItems: List<ItemStack>
    ): List<EvolutionGoal> {
        val allRecipes: List<PokemonEvolutionRecipe> = PokemonEvolutionDataLoader.loadEvolutionRecipes()

        // Regroupement par cible pour éliminer les doublons (même Pokémon cible et même forme)
        val groupedByTarget = mutableMapOf<String, MutableList<PokemonEvolutionRecipe>>()

        for (recipe in allRecipes) {
            val targetKey = getSpeciesKey(recipe.targetSpecies)
            val targetTracked = trackedMap[targetKey]
            val targetStatus = targetTracked?.status ?: PokedexStatus.MISSING

            // On s'intéresse en priorité aux Pokémon cibles qui n'ont PAS encore été capturés
            if (targetStatus == PokedexStatus.CAUGHT) {
                continue
            }

            // Clé de groupe : espèce cible + nom de forme cible
            val groupKey = "$targetKey:${recipe.targetForm.name.lowercase()}"
            groupedByTarget.getOrPut(groupKey) { mutableListOf() }.add(recipe)
        }

        val goals = mutableListOf<EvolutionGoal>()

        for ((_, candidateRecipes) in groupedByTarget) {
            val candidateGoals = candidateRecipes.map { recipe ->
                evaluateEvolutionRecipe(recipe, trackedMap, inventoryItems)
            }

            // Élection du meilleur chemin d'évolution pour cette cible
            val bestGoal = candidateGoals.minWithOrNull(
                compareBy<EvolutionGoal> { it.feasibility.ordinal }
                    .thenBy { it.missingItems.size }
                    .thenBy { if (it.hasGenderMatch) 0 else 1 }
                    .thenBy { if (it.isLevelMet) 0 else 1 }
                    .thenByDescending { it.ownedSourcePokemons.maxOfOrNull { p -> p.level } ?: 0 }
                    .thenBy { it.recipe.requirementsSummary.length }
            )

            if (bestGoal != null) {
                goals.add(bestGoal)
            }
        }

        // Tri : Faisable immédiatement d'abord, puis Pokémon en stock, puis Pokémon manquant, puis numéro Dex cible
        return goals.sortedWith(
            compareBy<EvolutionGoal> { it.feasibility.ordinal }
                .thenBy { it.targetSpecies.nationalPokedexNumber }
                .thenBy { it.sourceSpecies.nationalPokedexNumber }
        )
    }

    /**
     * Vérifie si un Pokémon possédé correspond à la forme requise par une recette d'évolution ou un filtre.
     */
    fun isFormMatch(pokemon: Pokemon, requiredForm: FormData, species: Species): Boolean {
        if (species.forms.size <= 1) return true

        val isRequiredStandard = requiredForm == species.standardForm ||
                requiredForm.name.isBlank() ||
                requiredForm.name.equals("normal", ignoreCase = true) ||
                requiredForm.name.equals("standard", ignoreCase = true)

        val isPokemonStandard = pokemon.form == species.standardForm ||
                pokemon.form.name.isBlank() ||
                pokemon.form.name.equals("normal", ignoreCase = true) ||
                pokemon.form.name.equals("standard", ignoreCase = true)

        if (isRequiredStandard) {
            if (isPokemonStandard) {
                val hasRegionalAspect = pokemon.aspects.any { asp ->
                    val a = asp.lowercase()
                    a == "hisui" || a == "galar" || a == "alola" || a == "paldea"
                }
                return !hasRegionalAspect
            }
            return false
        } else {
            if (pokemon.form == requiredForm) return true
            val reqName = requiredForm.name.lowercase()
            val pokeFormName = pokemon.form.name.lowercase()
            if (pokeFormName.isNotBlank() && (pokeFormName == reqName || pokeFormName.contains(reqName) || reqName.contains(pokeFormName))) {
                return true
            }
            if (requiredForm.aspects.isNotEmpty() && requiredForm.aspects.any { pokemon.aspects.contains(it) }) {
                return true
            }
            if (pokemon.aspects.any { it.equals(reqName, ignoreCase = true) }) {
                return true
            }
            return false
        }
    }

    private fun evaluateEvolutionRecipe(
        recipe: PokemonEvolutionRecipe,
        trackedMap: Map<String, TrackedPokemon>,
        inventoryItems: List<ItemStack>
    ): EvolutionGoal {
        val targetKey = getSpeciesKey(recipe.targetSpecies)
        val targetTracked = trackedMap[targetKey]
        val targetStatus = targetTracked?.status ?: PokedexStatus.MISSING

        val sourceKey = getSpeciesKey(recipe.sourceSpecies)
        val sourceTracked = trackedMap[sourceKey]
        val allOwnedSources = sourceTracked?.ownedInstances ?: emptyList()

        // 1. Filtrage par forme (ex: Qwilfish Hisui vs Johto standard)
        val validFormSources = allOwnedSources.filter { isFormMatch(it.pokemon, recipe.sourceForm, recipe.sourceSpecies) }
        val requiresSpecificForm = recipe.sourceSpecies.forms.size > 1
        val hasFormMatch = if (requiresSpecificForm) {
            if (allOwnedSources.isEmpty()) true else validFormSources.isNotEmpty()
        } else {
            true
        }

        val baseSources = if (requiresSpecificForm && allOwnedSources.isNotEmpty()) validFormSources else allOwnedSources

        // 2. Filtrage par sexe
        val requiredGender = recipe.requiredGender
        val validGenderSources = if (requiredGender != null) {
            baseSources.filter { it.pokemon.gender == requiredGender }
        } else {
            baseSources
        }
        val hasGenderMatch = if (requiredGender != null) {
            if (baseSources.isEmpty()) true else validGenderSources.isNotEmpty()
        } else {
            true
        }

        // 3. Analyse des objets requis
        val missingItems = mutableListOf<ItemStack>()
        for (reqStack in recipe.requiredItems) {
            val hasInInventory = inventoryItems.any { invStack ->
                invStack.item == reqStack.item && invStack.count >= reqStack.count
            }
            val hasHeld = validGenderSources.any { owned ->
                owned.heldItem.item == reqStack.item
            }
            if (!hasInInventory && !hasHeld) {
                missingItems.add(reqStack)
            }
        }
        val hasAllItems = missingItems.isEmpty()

        // 4. Analyse du niveau et du bonheur
        val (detectedLevel, detectedFriendship) = inspectEvolutionConditions(recipe)
        val minLevel = recipe.minLevel ?: detectedLevel
        val minFriendship = recipe.minFriendship ?: detectedFriendship

        val isLevelMet = if (minLevel != null) {
            validGenderSources.any { it.level >= minLevel }
        } else {
            true
        }

        val isFriendshipMet = if (minFriendship != null) {
            validGenderSources.any { it.friendship >= minFriendship }
        } else {
            true
        }

        // 5. Analyse des Capacités / Moves & CTs
        val requiredMove = recipe.requiredMove
        val requiredMoveType = recipe.requiredMoveType
        var moveStatus = MoveRequirementStatus.NOT_REQUIRED
        var moveLearnLevel: Int? = null

        if (requiredMove != null) {
            val cleanMove = requiredMove.lowercase().replace("_", "").replace(" ", "").replace("-", "")

            // Connaît déjà ?
            val hasKnown = validGenderSources.any { owned ->
                owned.pokemon.moveSet.any { it.template.name.lowercase().replace("_", "") == cleanMove }
            }

            if (hasKnown) {
                moveStatus = MoveRequirementStatus.KNOWN
            } else {
                // Réapprenable immédiatement (benched ou relearnable) ?
                val hasRelearnable = validGenderSources.any { owned ->
                    owned.pokemon.relearnableMoves.any { it.name.lowercase().replace("_", "") == cleanMove } ||
                    owned.pokemon.benchedMoves.any { it.moveTemplate.name.lowercase().replace("_", "") == cleanMove }
                }

                if (hasRelearnable) {
                    moveStatus = MoveRequirementStatus.RELEARNABLE
                } else {
                    // A-t-on la CT dans l'inventaire ?
                    val hasTmInBag = inventoryItems.any { stack ->
                        val path = BuiltInRegistries.ITEM.getKey(stack.item).path.lowercase().replace("_", "").replace("-", "")
                        (path.contains("tm") || path.contains("tr") || path.contains("machine")) && path.contains(cleanMove)
                    }

                    if (hasTmInBag) {
                        moveStatus = MoveRequirementStatus.TM_IN_INVENTORY
                    } else {
                        // S'apprend par niveau ?
                        val lvl = recipe.sourceForm.moves.levelUpMoves.entries.firstOrNull { entry ->
                            entry.value.any { it.name.lowercase().replace("_", "") == cleanMove }
                        }?.key

                        if (lvl != null) {
                            moveLearnLevel = lvl
                            moveStatus = MoveRequirementStatus.LEVEL_UP
                        } else {
                            val canLearnByTm = recipe.sourceForm.moves.tmMoves.any {
                                it.name.lowercase().replace("_", "") == cleanMove
                            }
                            if (canLearnByTm) {
                                moveStatus = MoveRequirementStatus.TM_MISSING
                            } else {
                                moveStatus = MoveRequirementStatus.CANNOT_LEARN
                            }
                        }
                    }
                }
            }
        } else if (requiredMoveType != null) {
            val cleanType = requiredMoveType.lowercase()
            val hasKnownType = validGenderSources.any { owned ->
                owned.pokemon.moveSet.any { it.template.elementalType.name.lowercase() == cleanType }
            }
            if (hasKnownType) {
                moveStatus = MoveRequirementStatus.KNOWN
            } else {
                moveStatus = MoveRequirementStatus.CANNOT_LEARN
            }
        }

        val isMoveMet = when (moveStatus) {
            MoveRequirementStatus.NOT_REQUIRED,
            MoveRequirementStatus.KNOWN,
            MoveRequirementStatus.RELEARNABLE,
            MoveRequirementStatus.TM_IN_INVENTORY -> true
            else -> false
        }

        // 6. Actions / Compteurs spéciaux
        val specialActionSummary = buildSpecialActionSummary(recipe)

        // 7. Faisabilité globale
        val feasibility = when {
            allOwnedSources.isEmpty() -> EvolutionFeasibility.MISSING_BASE_POKEMON
            !hasFormMatch -> EvolutionFeasibility.MISSING_BASE_POKEMON // Spécimen possédé mais pas de la forme requise
            !hasGenderMatch -> EvolutionFeasibility.CAN_EVOLVE_WITH_PC // Spécimens présents mais pas du bon sexe
            hasAllItems && isLevelMet && isFriendshipMet && isMoveMet -> EvolutionFeasibility.READY_TO_EVOLVE
            else -> EvolutionFeasibility.CAN_EVOLVE_WITH_PC
        }

        return EvolutionGoal(
            sourceSpecies = recipe.sourceSpecies,
            sourceForm = recipe.sourceForm,
            targetSpecies = recipe.targetSpecies,
            targetForm = recipe.targetForm,
            recipe = recipe,
            feasibility = feasibility,
            ownedSourcePokemons = if (hasFormMatch && hasGenderMatch) validGenderSources else if (hasFormMatch) validFormSources else allOwnedSources,
            requiredItems = recipe.requiredItems,
            missingItems = missingItems,
            minLevel = minLevel,
            isLevelMet = isLevelMet,
            minFriendship = minFriendship,
            isFriendshipMet = isFriendshipMet,
            requirementsSummary = recipe.requirementsSummary,
            additionalConditions = recipe.additionalConditions,
            targetStatus = targetStatus,
            requiredGender = requiredGender,
            hasGenderMatch = hasGenderMatch,
            requiredForm = recipe.sourceForm,
            hasFormMatch = hasFormMatch,
            requiredMove = requiredMove,
            requiredMoveType = requiredMoveType,
            moveStatus = moveStatus,
            moveLearnLevel = moveLearnLevel,
            stepsRequired = recipe.stepsAmount,
            useMoveCount = recipe.useMoveCount,
            specialActionSummary = specialActionSummary
        )
    }

    private fun buildSpecialActionSummary(recipe: PokemonEvolutionRecipe): String? {
        val parts = mutableListOf<String>()
        if (recipe.stepsAmount != null) {
            parts.add("Marcher ${recipe.stepsAmount} pas avec le Pokémon sorti")
        }
        if (recipe.useMoveCount != null && recipe.requiredMove != null) {
            val mvName = Moves.getByName(recipe.requiredMove)?.displayName?.string ?: recipe.requiredMove
            parts.add("Utiliser $mvName ${recipe.useMoveCount} fois en combat")
        }
        if (recipe.criticalHits != null) {
            parts.add("Infliger ${recipe.criticalHits} coups critiques en 1 combat")
        }
        if (recipe.damageTaken != null) {
            parts.add("Subir ≥ ${recipe.damageTaken} dégâts sans tomber K.O.")
        }
        if (recipe.recoilDamage != null) {
            parts.add("Subir ≥ ${recipe.recoilDamage} dégâts de contrecoup")
        }
        if (recipe.defeatAmount != null) {
            parts.add("Vaincre ${recipe.defeatAmount} ${recipe.defeatTarget ?: "adversaires"}")
        }
        if (recipe.partyMember != null) {
            parts.add("Avoir ${recipe.partyMember} dans l'équipe")
        }
        if (recipe.moonPhase != null) {
            parts.add("Phase lunaire : ${recipe.moonPhase}")
        }
        return if (parts.isNotEmpty()) parts.joinToString(" • ") else null
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
