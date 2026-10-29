package fr.k0bus.cobblemon_jei.web

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import fr.k0bus.cobblemon_jei.data.PokemonFossilDataLoader
import fr.k0bus.cobblemon_jei.data.PokemonPokeSnackResolver
import fr.k0bus.cobblemon_jei.data.repository.PokemonDataRepository
import fr.k0bus.cobblemon_jei.pokedex.*
import fr.k0bus.cobblemon_jei.web.dto.*
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

/**
 * Service de construction et normalisation des DTOs pour l'API REST et le flux temps réel.
 * Conçu avec tolérance aux pannes pour fonctionner même sans monde chargé ou en environnement de test.
 */
object WebDataService {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/WebDataService")

    private val PERMANENT_STATS = listOf(
        Stats.HP to "hp",
        Stats.ATTACK to "attack",
        Stats.DEFENCE to "defence",
        Stats.SPECIAL_ATTACK to "special_attack",
        Stats.SPECIAL_DEFENCE to "special_defence",
        Stats.SPEED to "speed"
    )

    fun getGeneration(dexNumber: Int): Int = when (dexNumber) {
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
    }

    /**
     * Retourne le statut général du joueur et de sa session.
     */
    fun getPlayerStatus(): PlayerStatusDto {
        val mc = try { Minecraft.getInstance() } catch (_: Throwable) { null }
        val player = mc?.player
        val playerName = player?.gameProfile?.name ?: "Dresseur"
        val dimension = try { player?.level()?.dimension()?.location()?.toString() } catch (_: Throwable) { null } ?: "minecraft:overworld"
        val biome = try {
            player?.let {
                val blockPos = it.blockPosition()
                val biomeHolder = it.level().getBiome(blockPos)
                biomeHolder.unwrapKey().map { k -> k.location().toString() }.orElse("unknown")
            }
        } catch (_: Throwable) { null } ?: "minecraft:plains"

        val posX = player?.blockX ?: 0
        val posY = player?.blockY ?: 0
        val posZ = player?.blockZ ?: 0

        val stats = try { PokemonTrackerService.getStats() } catch (_: Throwable) { null }
        val partySize = try {
            CobblemonClient.storage.party.count { it != null }
        } catch (_: Throwable) { 0 }

        return PlayerStatusDto(
            playerName = playerName,
            dimension = dimension,
            biome = biome,
            posX = posX,
            posY = posY,
            posZ = posZ,
            pokedexCaught = stats?.caughtCount ?: 0,
            pokedexEncountered = stats?.encounteredCount ?: 0,
            pokedexTotal = stats?.totalSpecies ?: 0,
            pokedexPercentage = stats?.completionPercent ?: 0f,
            partyCount = partySize
        )
    }

    /**
     * Retourne la liste résumée de toutes les espèces pour le Pokédex.
     */
    fun getPokedexList(): List<PokemonSummaryDto> {
        try {
            PokemonTrackerService.refresh()
            val allTracked = PokemonTrackerService.getAllTracked()
            val evolutionGoals = try { PokemonTrackerService.getEvolutionGoals() } catch (_: Throwable) { emptyList() }
            val readyGoalTargetSpecies = evolutionGoals
                .filter { it.feasibility == EvolutionFeasibility.READY_TO_EVOLVE }
                .map { it.targetSpecies.resourceIdentifier.toString() }
                .toSet()

            return allTracked.map { tracked ->
                val sp = tracked.species
                val resId = sp.resourceIdentifier.toString()
                val primType = sp.primaryType.name.lowercase()
                val secType = sp.secondaryType?.name?.lowercase()
                val isLeg = sp.labels.any { it.equals("legendary", true) || it.equals("mythical", true) || it.equals("ultra_beast", true) } ||
                        fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat.isCoveredSpecies(sp.name)

                PokemonSummaryDto(
                    nationalDexNumber = sp.nationalPokedexNumber,
                    speciesId = resId,
                    name = sp.name,
                    displayName = sp.translatedName.string,
                    primaryType = primType,
                    secondaryType = secType,
                    generation = getGeneration(sp.nationalPokedexNumber),
                    status = tracked.status.name,
                    formsCount = sp.forms.size,
                    ownedCount = tracked.ownedCount,
                    hasPendingEvolution = readyGoalTargetSpecies.contains(resId),
                    isLegendary = isLeg
                )
            }
        } catch (t: Throwable) {
            logger.debug("Erreur getPokedexList: {}", t.message)
            return emptyList()
        }
    }

    /**
     * Retourne la fiche détaillée d'un Pokémon par son ID ou son nom.
     */
    fun getSpeciesDetail(identifier: String): SpeciesDetailDto? {
        try {
            val cleanId = identifier.trim().lowercase().removePrefix("cobblemon:")
            val allTracked = PokemonTrackerService.getAllTracked()
            val tracked = allTracked.find {
                it.species.name.lowercase() == cleanId ||
                        it.species.resourceIdentifier.path.lowercase() == cleanId ||
                        it.species.nationalPokedexNumber.toString() == cleanId
            } ?: return null

            val sp = tracked.species
            val snapshot = PokemonDataRepository.getSnapshot()
            val domainSpecies = snapshot.getSpecies(sp.resourceIdentifier)

            // 1. Stats de base & EVs
            val baseStats = PERMANENT_STATS.map { (stat, key) ->
                val valBase = try { sp.baseStats[stat] ?: 0 } catch (_: Throwable) { 0 }
                val evVal = try { sp.evYield[stat] ?: 0 } catch (_: Throwable) { 0 }
                StatInfoDto(name = key, value = valBase, evYield = evVal)
            }
            val totalStats = baseStats.sumOf { it.value }

            // 2. Talents
            val abilities = mutableListOf<AbilityInfoDto>()
            try {
                for (pot in sp.standardForm.abilities) {
                    val template = pot.template
                    val isHidden = pot is com.cobblemon.mod.common.pokemon.abilities.HiddenAbility || pot.priority == com.cobblemon.mod.common.api.Priority.LOWEST
                    val rawName = template.displayName.ifBlank { template.name }
                    val nameComp = Component.translatable(rawName)
                    val dispName = if (nameComp.string.isNotBlank() && nameComp.string != rawName) nameComp.string else rawName.replaceFirstChar(Char::titlecase)
                    abilities.add(
                        AbilityInfoDto(
                            name = template.name,
                            displayName = dispName,
                            isHidden = isHidden,
                            description = template.description
                        )
                    )
                }
            } catch (_: Throwable) {}

            // 3. Formes
            val forms = sp.forms.map { form ->
                FormInfoDto(
                    name = form.name,
                    displayName = form.name.replaceFirstChar { it.uppercase() },
                    primaryType = form.primaryType?.name?.lowercase() ?: sp.primaryType.name.lowercase(),
                    secondaryType = form.secondaryType?.name?.lowercase() ?: sp.secondaryType?.name?.lowercase(),
                    aspects = form.aspects.toList()
                )
            }

            // 4. Évolutions
            val evolutions = mutableListOf<EvolutionNodeDto>()
            try {
                val evoRecipes = fr.k0bus.cobblemon_jei.data.PokemonEvolutionDataLoader.loadEvolutionRecipes()
                for (rec in evoRecipes) {
                    if (PokemonTrackingManager.isSameSpecies(rec.sourceSpecies, sp) ||
                        PokemonTrackingManager.isSameSpecies(rec.targetSpecies, sp)) {
                        val reqItem = rec.requiredItems.firstOrNull()?.hoverName?.string
                        evolutions.add(
                            EvolutionNodeDto(
                                sourceSpeciesId = rec.sourceSpecies.resourceIdentifier.toString(),
                                sourceName = rec.sourceSpecies.translatedName.string,
                                targetSpeciesId = rec.targetSpecies.resourceIdentifier.toString(),
                                targetName = rec.targetSpecies.translatedName.string,
                                requirementsSummary = rec.requirementsSummary,
                                requiredLevel = rec.minLevel,
                                requiredItem = reqItem,
                                isItemTriggered = rec.requiredItems.isNotEmpty()
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}

            // 5. Drops
            val drops = mutableListOf<DropInfoDto>()
            try {
                val dropRecipes = fr.k0bus.cobblemon_jei.data.PokemonDropDataLoader.loadDropRecipes()
                val matchRecipe = dropRecipes.find { PokemonTrackingManager.isSameSpecies(it.species, sp) }
                if (matchRecipe != null) {
                    for (dr in matchRecipe.drops) {
                        val item = dr.itemStack
                        drops.add(
                            DropInfoDto(
                                itemId = BuiltInRegistries.ITEM.getKey(item.item).toString(),
                                displayName = item.hoverName.string,
                                minCount = dr.minCount,
                                maxCount = dr.maxCount,
                                chancePercentage = dr.percentage
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}

            // 6. Spawns
            val spawns = mutableListOf<SpawnZoneDto>()
            try {
                val rawSpawns = domainSpecies?.spawnEntries ?: emptyList()
                for (entry in rawSpawns) {
                    spawns.add(
                        SpawnZoneDto(
                            biomes = entry.condition.biomes.toList(),
                            dimensions = entry.condition.dimensions.map { it.toString() },
                            timeRange = entry.condition.timeRange,
                            weather = entry.condition.weather,
                            minLevel = entry.levelMin,
                            maxLevel = entry.levelMax,
                            weight = entry.weight,
                            rarityBucket = entry.bucket
                        )
                    )
                }
            } catch (_: Throwable) {}

            // 7. Poké Snack
            val snackDto = try {
                val advice = PokemonPokeSnackResolver.resolvePokeSnack(sp, sp.standardForm)
                if (advice.seasonings.isNotEmpty()) {
                    val seasoningNames = advice.seasonings.map { it.title }
                    PokeSnackDto(
                        optimalTaste = advice.baseRarity ?: "Équilibré",
                        recipeItems = seasoningNames,
                        description = advice.explanationSummary
                    )
                } else null
            } catch (_: Throwable) { null }

            // 8. Pâturage Cobbleworkers
            val pastureDto = try {
                if (fr.k0bus.cobblemon_jei.compat.cobbleworkers.CobbleworkersCompat.isLoaded) {
                    val pastureRecipes = fr.k0bus.cobblemon_jei.data.CobbleworkersJobDataLoader.loadPastureRecipes()
                    val matchRecipe = pastureRecipes.find { rec ->
                        rec.eligiblePokemon.any { PokemonTrackingManager.isSameSpecies(it.species, sp) }
                    }
                    if (matchRecipe != null) {
                        PastureJobDto(
                            jobName = matchRecipe.jobTitle.string,
                            description = matchRecipe.requirementsDescription
                        )
                    } else null
                } else null
            } catch (_: Throwable) { null }

            // 9. Pokémon possédés par le joueur
            val ownedDtos = tracked.ownedInstances.map { mapOwnedPokemonToDto(it.pokemon, it.location, it.isParty, it.boxIndex) }

            return SpeciesDetailDto(
                nationalDexNumber = sp.nationalPokedexNumber,
                speciesId = sp.resourceIdentifier.toString(),
                name = sp.name,
                displayName = sp.translatedName.string,
                primaryType = sp.primaryType.name.lowercase(),
                secondaryType = sp.secondaryType?.name?.lowercase(),
                generation = getGeneration(sp.nationalPokedexNumber),
                status = tracked.status.name,
                height = sp.height,
                weight = sp.weight,
                catchRate = sp.catchRate,
                baseFriendship = sp.baseFriendship,
                baseStats = baseStats,
                totalStats = totalStats,
                abilities = abilities,
                forms = forms,
                evolutions = evolutions,
                drops = drops,
                spawns = spawns,
                pokeSnack = snackDto,
                pastureJob = pastureDto,
                ownedInstances = ownedDtos
            )
        } catch (t: Throwable) {
            logger.debug("Erreur getSpeciesDetail: {}", t.message)
            return null
        }
    }

    /**
     * Retourne les évolutions en attente (Prêtes / Faisables avec le PC).
     */
    fun getPendingEvolutionGoals(): List<EvolutionGoalDto> {
        return try {
            PokemonTrackerService.refresh()
            val goals = PokemonTrackerService.getEvolutionGoals()
            goals.map { mapEvolutionGoalToDto(it) }
        } catch (t: Throwable) {
            logger.debug("Erreur getPendingEvolutionGoals: {}", t.message)
            emptyList()
        }
    }

    /**
     * Retourne les évolutions déclenchées par une pierre ou un objet spécial.
     */
    fun getItemEvolutionGoals(): List<EvolutionGoalDto> {
        return try {
            PokemonTrackerService.refresh()
            val goals = PokemonTrackerService.getItemEvolutionGoals()
            goals.map { mapEvolutionGoalToDto(it) }
        } catch (t: Throwable) {
            logger.debug("Erreur getItemEvolutionGoals: {}", t.message)
            emptyList()
        }
    }

    /**
     * Retourne les formes de base / bébés manquants réalisables par reproduction.
     */
    fun getMissingOriginGoals(): List<BreedingOriginDto> {
        return try {
            PokemonTrackerService.refresh()
            val origins = PokemonTrackerService.getMissingOriginGoals()
            origins.map { o ->
                BreedingOriginDto(
                    baseSpeciesId = o.baseSpecies.resourceIdentifier.toString(),
                    baseSpeciesName = o.baseSpecies.name,
                    baseName = o.baseSpecies.translatedName.string,
                    baseDexNumber = o.nationalDexNumber,
                    isBaby = o.isBaby,
                    targetStatus = o.targetStatus.name,
                    candidateParents = o.candidateParents.map { it.translatedName.string },
                    ownedParentsSummary = o.parentSummary,
                    isReadyToBreed = o.isReadyToBreed,
                    requiredIncense = o.requiredIncense
                )
            }
        } catch (t: Throwable) {
            logger.debug("Erreur getMissingOriginGoals: {}", t.message)
            emptyList()
        }
    }

    /**
     * Retourne le suivi des fossiles et leur état de restauration.
     */
    fun getFossilGoals(): List<FossilGoalDto> {
        return try {
            PokemonTrackerService.refresh()
            PokemonFossilDataLoader.ensureLoaded()
            val allTracked = PokemonTrackerService.getAllTracked()
            val player = try { Minecraft.getInstance().player } catch (_: Throwable) { null }

            PokemonFossilDataLoader.getAllFossilRecipes().mapNotNull { rec ->
                val sp = rec.resultSpecies ?: return@mapNotNull null
                val tr = allTracked.find { PokemonTrackingManager.isSameSpecies(it.species, sp) }
                val ready = if (player != null) PokemonFossilDataLoader.isPlayerHoldingFossils(player, rec) else false
                val heldCount = if (player != null) rec.requiredFossilItemIds.sumOf { PokemonFossilDataLoader.getPlayerFossilCount(player, it) } else 0

                FossilGoalDto(
                    speciesId = sp.resourceIdentifier.toString(),
                    speciesName = sp.translatedName.string,
                    nationalDexNumber = sp.nationalPokedexNumber,
                    fossilName = rec.fossilDisplayName,
                    heldFossilCount = heldCount,
                    isReadyToRevive = ready,
                    isCaught = tr?.isCaught == true,
                    isEncountered = tr?.isEncountered == true,
                    associatedStructures = rec.associatedStructureDisplayNames
                )
            }.sortedWith(
                compareByDescending<FossilGoalDto> { it.isReadyToRevive }
                    .thenByDescending { it.heldFossilCount > 0 }
                    .thenBy { it.nationalDexNumber }
            )
        } catch (t: Throwable) {
            logger.debug("Erreur getFossilGoals: {}", t.message)
            emptyList()
        }
    }

    /**
     * Retourne la liste des Pokémon légendaires avec compatibilité sanctuaires.
     */
    fun getLegendaryGoals(): List<LegendaryGoalDto> {
        return try {
            PokemonTrackerService.refresh()
            val allTracked = PokemonTrackerService.getAllTracked()
            val legendaries = allTracked.filter {
                fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat.isCoveredSpecies(it.species.name) ||
                        it.species.labels.any { l -> l.equals("legendary", true) || l.equals("mythical", true) || l.equals("ultra_beast", true) }
            }

            legendaries.map { tr ->
                val sp = tr.species
                val isMon = fr.k0bus.cobblemon_jei.compat.legendarymonuments.LegendaryMonumentsCompat.isCoveredSpecies(sp.name)
                LegendaryGoalDto(
                    speciesId = sp.resourceIdentifier.toString(),
                    speciesName = sp.translatedName.string,
                    nationalDexNumber = sp.nationalPokedexNumber,
                    status = tr.status.name,
                    isCoveredByMonuments = isMon,
                    monumentStructure = if (isMon) "Sanctuaire Ancestral" else null
                )
            }
        } catch (t: Throwable) {
            logger.debug("Erreur getLegendaryGoals: {}", t.message)
            emptyList()
        }
    }

    /**
     * Retourne les 6 membres de l'équipe active du joueur.
     */
    fun getParty(): List<OwnedPokemonDto?> {
        val partyList = mutableListOf<OwnedPokemonDto?>()
        try {
            val storageParty = CobblemonClient.storage.party
            for (i in 0 until 6) {
                val p = storageParty.get(i)
                if (p != null) {
                    partyList.add(mapOwnedPokemonToDto(p, "Équipe (Slot ${i + 1})", true, -1, i))
                } else {
                    partyList.add(null)
                }
            }
        } catch (e: Throwable) {
            logger.debug("Erreur lecture équipe pour web: {}", e.message)
        }
        return partyList
    }

    /**
     * Retourne l'ensemble des boîtes du PC.
     */
    fun getPcBoxes(): List<PcBoxDto> {
        val boxes = mutableListOf<PcBoxDto>()
        try {
            for ((boxIdx, pc) in CobblemonClient.storage.pcStores.values.withIndex()) {
                for ((innerIdx, box) in pc.boxes.withIndex()) {
                    val actualIdx = boxIdx * 30 + innerIdx
                    val name = box.name?.string ?: "Boîte ${actualIdx + 1}"
                    val pokes = mutableListOf<OwnedPokemonDto?>()
                    var occupied = 0
                    for ((slotIdx, p) in box.slots.withIndex()) {
                        if (p != null) {
                            occupied++
                            pokes.add(mapOwnedPokemonToDto(p, "PC : $name", false, actualIdx, slotIdx))
                        } else {
                            pokes.add(null)
                        }
                    }
                    boxes.add(
                        PcBoxDto(
                            boxIndex = actualIdx,
                            boxName = name,
                            totalSlots = box.slots.size,
                            occupiedSlots = occupied,
                            pokemons = pokes
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            logger.debug("Erreur lecture boîtes PC pour web: {}", e.message)
        }
        return boxes
    }

    /**
     * Retourne les entités détectées à portée du radar.
     */
    fun getRadarEntities(): List<RadarEntityDto> {
        val list = mutableListOf<RadarEntityDto>()
        try {
            val tracked = PokemonTrackerService.getAllTracked()
            val trackedMap = fr.k0bus.cobblemon_jei.journeymap.PokemonRadarService.getTrackedEntities()
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return emptyList()

            for (detected in trackedMap.values) {
                val sp = detected.species
                val dist = Math.sqrt(player.distanceToSqr(detected.blockPos.x.toDouble(), detected.blockPos.y.toDouble(), detected.blockPos.z.toDouble()))
                val status = tracked.find { PokemonTrackingManager.isSameSpecies(it.species, sp) }?.status?.name ?: "MISSING"

                list.add(
                    RadarEntityDto(
                        speciesId = sp.resourceIdentifier.toString(),
                        displayName = sp.translatedName.string,
                        nationalDexNumber = sp.nationalPokedexNumber,
                        level = detected.level,
                        distance = Math.round(dist * 10.0) / 10.0,
                        relativeYaw = 0f,
                        altitudeDiff = (detected.blockPos.y - player.blockY),
                        isShiny = detected.isShiny,
                        perfectIvsCount = detected.perfectIvsCount,
                        hasHiddenAbility = detected.hasHiddenAbility,
                        status = status
                    )
                )
            }
        } catch (_: Throwable) {}
        return list.sortedBy { it.distance }
    }

    private fun mapEvolutionGoalToDto(g: EvolutionGoal): EvolutionGoalDto {
        return EvolutionGoalDto(
            sourceSpeciesId = g.sourceSpecies.resourceIdentifier.toString(),
            sourceSpeciesName = g.sourceSpecies.name,
            sourceName = g.sourceSpecies.translatedName.string,
            sourceDexNumber = g.sourceSpecies.nationalPokedexNumber,
            targetSpeciesId = g.targetSpecies.resourceIdentifier.toString(),
            targetSpeciesName = g.targetSpecies.name,
            targetName = g.targetSpecies.translatedName.string,
            targetDexNumber = g.targetSpecies.nationalPokedexNumber,
            feasibility = g.feasibility.name,
            requirementsSummary = g.requirementsSummary,
            requiredItems = g.requiredItems.map { it.hoverName.string },
            missingItems = g.missingItems.map { it.hoverName.string },
            isItemOwned = g.missingItems.isEmpty() && g.requiredItems.isNotEmpty(),
            minLevel = g.minLevel,
            isLevelMet = g.isLevelMet,
            minFriendship = g.minFriendship,
            isFriendshipMet = g.isFriendshipMet,
            ownedLocationSummary = g.sourceLocationSummary,
            ownedInstancesCount = g.ownedSourcePokemons.size,
            targetStatus = g.targetStatus.name
        )
    }

    private fun mapOwnedPokemonToDto(
        pokemon: Pokemon,
        location: String,
        isParty: Boolean,
        boxIndex: Int = -1,
        slotIndex: Int = 0
    ): OwnedPokemonDto {
        val ivsMap = mutableMapOf<String, Int>()
        val evsMap = mutableMapOf<String, Int>()
        for ((stat, key) in PERMANENT_STATS) {
            ivsMap[key] = try { pokemon.ivs[stat] ?: 0 } catch (_: Throwable) { 0 }
            evsMap[key] = try { pokemon.evs[stat] ?: 0 } catch (_: Throwable) { 0 }
        }

        val movesList = mutableListOf<MoveInfoDto>()
        try {
            for (m in pokemon.moveSet) {
                val temp = m.template
                val powerVal = try { temp.power.toInt() } catch (_: Throwable) { null }
                val accuracyVal = try { temp.accuracy.toInt() } catch (_: Throwable) { null }
                movesList.add(
                    MoveInfoDto(
                        id = temp.name,
                        displayName = temp.displayName.string,
                        type = temp.elementalType.name.lowercase(),
                        power = powerVal,
                        accuracy = accuracyVal,
                        pp = m.maxPp,
                        maxPp = m.maxPp,
                        currentPp = m.currentPp
                    )
                )
            }
        } catch (_: Throwable) {}

        val heldItemStr = try {
            val stack = pokemon.heldItem()
            if (!stack.isEmpty) stack.hoverName.string else null
        } catch (_: Throwable) { null }

        val translatedName = pokemon.species.translatedName.string
        val nick = try { pokemon.nickname?.string } catch (_: Throwable) { null }
        val pokeDisplayName = if (!nick.isNullOrBlank()) "$nick ($translatedName)" else translatedName

        val abilityName = try { pokemon.ability.template.displayName.ifBlank { pokemon.ability.template.name } } catch (_: Throwable) { pokemon.ability.name }
        val natureName = try { pokemon.nature.displayName } catch (_: Throwable) { pokemon.nature.name.path }

        val primType = try { pokemon.species.primaryType.name.lowercase() } catch (_: Throwable) { "normal" }
        val secType = try { pokemon.species.secondaryType?.name?.lowercase() } catch (_: Throwable) { null }

        return OwnedPokemonDto(
            uuid = pokemon.uuid.toString(),
            speciesId = pokemon.species.resourceIdentifier.toString(),
            speciesName = pokemon.species.name,
            nationalDexNumber = pokemon.species.nationalPokedexNumber,
            displayName = pokeDisplayName,
            primaryType = primType,
            secondaryType = secType,
            level = pokemon.level,
            location = location,
            isParty = isParty,
            boxIndex = boxIndex,
            slotIndex = slotIndex,
            isShiny = pokemon.shiny,
            gender = pokemon.gender.name,
            ability = abilityName,
            nature = natureName,
            heldItem = heldItemStr,
            currentHp = pokemon.currentHealth,
            maxHp = pokemon.maxHealth,
            ivs = ivsMap,
            evs = evsMap,
            moves = movesList
        )
    }
}
