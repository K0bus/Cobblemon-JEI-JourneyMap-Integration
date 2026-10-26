package fr.k0bus.cobblemon_jei.pokedex

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.k0bus.cobblemon_jei.data.PokemonEvolutionDataLoader
import fr.k0bus.cobblemon_jei.journeymap.PokemonRadarService
import fr.k0bus.cobblemon_jei.journeymap.SpawnOverlayManager
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Modes de ciblage pour le tracker et radar de Pokémon.
 */
enum class TrackingMode(val translationKey: String) {
    OFF("gui.cobblemon_jei.tracker.mode.off"),
    ALL_UNCAUGHT("gui.cobblemon_jei.tracker.mode.all_uncaught"),
    SINGLE("gui.cobblemon_jei.tracker.mode.single"),
    EVOLUTION_LINE("gui.cobblemon_jei.tracker.mode.evolution_line"),
    CUSTOM_LIST("gui.cobblemon_jei.tracker.mode.custom_list")
}

/**
 * Gestionnaire centralisé du système de suivi (Tracker) de Pokémon.
 *
 * Permet de basculer facilement entre les 4 modes de suivi :
 * 1. Suivre tous les Pokémon non capturés au Pokédex
 * 2. Suivre un Pokémon spécifique (et optionnellement sa forme)
 * 3. Suivre une famille évolutive complète (parents/pré-évolutions et enfants/évolutions)
 * 4. Suivre une liste personnalisée de Pokémon (watchlist)
 */
object PokemonTrackingManager {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/TrackingManager")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    var currentMode: TrackingMode = TrackingMode.OFF
        private set

    // Cible unique
    var targetSpecies: Species? = null
        private set
    var targetForm: FormData? = null
        private set

    // Cible lignée évolutive
    var evolutionBaseSpecies: Species? = null
        private set
    var evolutionFamily: Set<Species> = emptySet()
        private set

    // Liste personnalisée (Watchlist)
    val customWatchlist: LinkedHashSet<Species> = LinkedHashSet()

    // Filtres avancés (Talent caché, IVs, Taille, Nature, Rendu in-world)
    val advancedFilter: RadarAdvancedFilter = RadarAdvancedFilter()

    private var isConfigLoaded = false

    val isTrackingActive: Boolean
        get() = currentMode != TrackingMode.OFF

    init {
        loadConfig()
    }

    /**
     * Active le suivi de tous les Pokémon non capturés au Pokédex.
     */
    fun trackAllUncaught(notifyPlayer: Boolean = true) {
        currentMode = TrackingMode.ALL_UNCAUGHT
        targetSpecies = null
        targetForm = null
        evolutionBaseSpecies = null
        evolutionFamily = emptySet()

        try { SpawnOverlayManager.clearOverlay() } catch (_: Throwable) {}
        try { PokemonRadarService.clearTrackedOnly() } catch (_: Throwable) {}
        saveConfig()

        if (notifyPlayer) {
            displayMessage(Component.translatable("chat.cobblemon_jm.tracker.all_uncaught"))
        }
    }

    /**
     * Active le suivi d'un Pokémon en particulier.
     */
    fun trackSingle(species: Species, form: FormData? = null, notifyPlayer: Boolean = true) {
        currentMode = TrackingMode.SINGLE
        targetSpecies = species
        targetForm = form ?: try { species.standardForm } catch (_: Throwable) { null }
        evolutionBaseSpecies = null
        evolutionFamily = emptySet()

        try { SpawnOverlayManager.setOverlay(species, form) } catch (_: Throwable) {}
        try { PokemonRadarService.clearTrackedOnly() } catch (_: Throwable) {}
        saveConfig()

        if (notifyPlayer) {
            val displayName = try { species.translatedName } catch (_: Throwable) { Component.literal(species.name) }
            displayMessage(Component.translatable("chat.cobblemon_jm.tracker.single", displayName))
        }
    }

    /**
     * Active le suivi d'un Pokémon ainsi que de tous ses ancêtres et descendants évolutifs.
     */
    fun trackEvolutionLine(species: Species, notifyPlayer: Boolean = true) {
        currentMode = TrackingMode.EVOLUTION_LINE
        evolutionBaseSpecies = species
        evolutionFamily = getEvolutionFamily(species)
        targetSpecies = species
        targetForm = try { species.standardForm } catch (_: Throwable) { null }

        try { SpawnOverlayManager.setOverlay(species, targetForm) } catch (_: Throwable) {}
        try { PokemonRadarService.clearTrackedOnly() } catch (_: Throwable) {}
        saveConfig()

        if (notifyPlayer) {
            val displayName = try { species.translatedName } catch (_: Throwable) { Component.literal(species.name) }
            displayMessage(Component.translatable("chat.cobblemon_jm.tracker.evolution_line", displayName, evolutionFamily.size))
        }
    }

    /**
     * Active le suivi de la liste personnalisée (Watchlist).
     */
    fun trackCustomList(notifyPlayer: Boolean = true) {
        if (customWatchlist.isEmpty()) {
            displayMessage(Component.translatable("chat.cobblemon_jm.tracker.custom_list_empty"))
            return
        }
        currentMode = TrackingMode.CUSTOM_LIST
        targetSpecies = null
        targetForm = null
        evolutionBaseSpecies = null
        evolutionFamily = emptySet()

        try { SpawnOverlayManager.clearOverlay() } catch (_: Throwable) {}
        try { PokemonRadarService.clearTrackedOnly() } catch (_: Throwable) {}
        saveConfig()

        if (notifyPlayer) {
            displayMessage(Component.translatable("chat.cobblemon_jm.tracker.custom_list", customWatchlist.size))
        }
    }

    /**
     * Désactive le tracking (la Sentinelle Shiny reste active).
     */
    fun stopTracking(notifyPlayer: Boolean = true) {
        currentMode = TrackingMode.OFF
        targetSpecies = null
        targetForm = null
        evolutionBaseSpecies = null
        evolutionFamily = emptySet()

        try { SpawnOverlayManager.clearOverlay() } catch (_: Throwable) {}
        try { PokemonRadarService.clearTrackedOnly() } catch (_: Throwable) {}
        saveConfig()

        if (notifyPlayer) {
            displayMessage(Component.translatable("chat.cobblemon_jm.tracker.stop"))
        }
    }

    /**
     * Ajoute une espèce à la liste de tracking personnalisée.
     */
    fun addToWatchlist(species: Species, notifyPlayer: Boolean = true): Boolean {
        ensureConfigLoaded()
        val alreadyPresent = customWatchlist.any { isSameSpecies(it, species) }
        if (!alreadyPresent) {
            customWatchlist.add(species)
            saveConfig()
            if (notifyPlayer) {
                displayMessage(Component.translatable("chat.cobblemon_jm.tracker.list_added", species.translatedName))
            }
            if (currentMode == TrackingMode.CUSTOM_LIST) {
                PokemonRadarService.clearTrackedOnly()
            }
            return true
        }
        return false
    }

    /**
     * Retire une espèce de la liste de tracking personnalisée.
     */
    fun removeFromWatchlist(species: Species, notifyPlayer: Boolean = true): Boolean {
        ensureConfigLoaded()
        val removed = customWatchlist.removeIf { isSameSpecies(it, species) }
        if (removed) {
            saveConfig()
            if (notifyPlayer) {
                displayMessage(Component.translatable("chat.cobblemon_jm.tracker.list_removed", species.translatedName))
            }
            if (currentMode == TrackingMode.CUSTOM_LIST) {
                PokemonRadarService.clearTrackedOnly()
                if (customWatchlist.isEmpty()) {
                    currentMode = TrackingMode.OFF
                }
            }
        }
        return removed
    }

    /**
     * Bascule la présence d'une espèce dans la liste personnalisée.
     */
    fun toggleWatchlist(species: Species, notifyPlayer: Boolean = true): Boolean {
        return if (isInWatchlist(species)) {
            removeFromWatchlist(species, notifyPlayer)
            false
        } else {
            addToWatchlist(species, notifyPlayer)
            true
        }
    }

    /**
     * Vérifie si une espèce est dans la liste de tracking personnalisée.
     */
    fun isInWatchlist(species: Species): Boolean {
        ensureConfigLoaded()
        return customWatchlist.any { isSameSpecies(it, species) }
    }

    /**
     * Vide complètement la liste de tracking personnalisée.
     */
    fun clearWatchlist() {
        customWatchlist.clear()
        saveConfig()
        if (currentMode == TrackingMode.CUSTOM_LIST) {
            stopTracking()
        }
    }

    /**
     * Calcule récursivement l'ensemble de la lignée évolutive d'une espèce donnée
     * (toutes les pré-évolutions / ancêtres et toutes les évolutions / descendants).
     */
    fun getEvolutionFamily(species: Species): Set<Species> {
        val allRecipes = PokemonEvolutionDataLoader.loadEvolutionRecipes()
        val family = linkedSetOf<Species>()
        family.add(species)

        var added = true
        var iterations = 0
        while (added && iterations < 15) {
            val currentSize = family.size
            val currentSpeciesList = family.toList()

            for (current in currentSpeciesList) {
                for (recipe in allRecipes) {
                    // Descendants (évolutions cibles directes)
                    if (isSameSpecies(current, recipe.sourceSpecies)) {
                        if (family.none { isSameSpecies(it, recipe.targetSpecies) }) {
                            family.add(recipe.targetSpecies)
                        }
                    }
                    // Ancêtres (pré-évolutions sources directes)
                    if (isSameSpecies(current, recipe.targetSpecies)) {
                        if (family.none { isSameSpecies(it, recipe.sourceSpecies) }) {
                            family.add(recipe.sourceSpecies)
                        }
                    }
                }
            }
            added = family.size > currentSize
            iterations++
        }

        return family.sortedBy { it.nationalPokedexNumber }.toSet()
    }

    /**
     * Vérifie si une entité [PokemonEntity] vivante correspond à la cible actuelle du tracker
     * et respecte tous les filtres avancés (HA, IVs, Taille, Nature).
     */
     fun isTarget(entity: PokemonEntity): Boolean {
         val pokemon = entity.pokemon
         if (!isTarget(pokemon.species, pokemon.form)) return false
         return advancedFilter.matches(pokemon)
     }

    fun isTarget(species: Species): Boolean = isTarget(species, null)

    /**
     * Vérifie si une espèce et forme correspondent à la cible actuelle du tracker.
     */
    fun isTarget(species: Species, form: FormData?): Boolean {
        return when (currentMode) {
            TrackingMode.OFF -> false
            TrackingMode.ALL_UNCAUGHT -> isSpeciesOrFormUncaught(species, form)
            TrackingMode.SINGLE -> {
                targetSpecies?.let { target ->
                    isSameSpecies(species, target) &&
                            (targetForm == null || form == null || form.name.equals(targetForm?.name, ignoreCase = true))
                } ?: false
            }
            TrackingMode.EVOLUTION_LINE -> {
                evolutionFamily.any { isSameSpecies(species, it) }
            }
            TrackingMode.CUSTOM_LIST -> {
                customWatchlist.any { isSameSpecies(species, it) }
            }
        }
    }

    /**
     * Vérifie si une espèce ou sa forme est actuellement non capturée par le joueur.
     */
    fun isSpeciesOrFormUncaught(species: Species, form: FormData?): Boolean {
        // 1. Vérifier si un exemplaire est possédé dans le PC ou l'équipe
        val allTracked = PokemonTrackerService.getAllTracked()
        val tracked = allTracked.find { isSameSpecies(it.species, species) }

        if (form != null && species.forms.size > 1) {
            val hasFormInStock = tracked?.ownedInstances?.any {
                PokemonTrackerService.isFormMatch(it.pokemon, form, species)
            } == true
            if (hasFormInStock) return false
        } else {
            if ((tracked?.ownedCount ?: 0) > 0) return false
        }

        // 2. Vérifier les données enregistrées dans le Pokédex client
        return try {
            val dexData = CobblemonClient.clientPokedexData
            val specRecord = dexData.speciesRecords[species.resourceIdentifier]
            if (specRecord == null || specRecord.getKnowledge() != PokedexEntryProgress.CAUGHT) {
                return true
            }

            if (form != null && species.forms.size > 1) {
                val formName = form.name.lowercase().ifBlank { "normal" }
                val formRecord = specRecord.getFormRecord(formName)
                    ?: specRecord.getFormRecord(form.name.lowercase())
                    ?: specRecord.getFormRecord("")
                if (formRecord == null || formRecord.knowledge != PokedexEntryProgress.CAUGHT) {
                    return true
                }
            }
            false
        } catch (_: Throwable) {
            tracked?.isCaught != true
        }
    }

    /**
     * Retourne le tag visuel associé au mode de tracking pour les marqueurs JourneyMap.
     */
    fun getTargetTag(species: Species, form: FormData?): String {
        return when (currentMode) {
            TrackingMode.ALL_UNCAUGHT -> " §c[Non capturé]"
            TrackingMode.EVOLUTION_LINE -> " §b[Lignée]"
            TrackingMode.CUSTOM_LIST -> " §a[Liste]"
            TrackingMode.SINGLE -> " §3[Cible]"
            TrackingMode.OFF -> ""
        }
    }

    /**
     * Résumé textuel court du mode actuel (pour HUD et tooltips).
     */
    fun getModeSummary(): String {
        return when (currentMode) {
            TrackingMode.OFF -> "Inactif"
            TrackingMode.ALL_UNCAUGHT -> "Non capturés"
            TrackingMode.SINGLE -> targetSpecies?.translatedName?.string ?: "Cible unique"
            TrackingMode.EVOLUTION_LINE -> "Lignée de ${evolutionBaseSpecies?.translatedName?.string ?: "Inconnu"}"
            TrackingMode.CUSTOM_LIST -> "Liste (${customWatchlist.size})"
        }
    }

    fun isSameSpecies(sp1: Species, sp2: Species): Boolean {
        return try {
            sp1.resourceIdentifier == sp2.resourceIdentifier
        } catch (_: Throwable) {
            sp1.name.equals(sp2.name, ignoreCase = true)
        }
    }

    private fun displayMessage(component: Component) {
        try {
            val mc = Minecraft.getInstance() ?: return
            mc.execute {
                mc.player?.displayClientMessage(component, true)
            }
        } catch (_: Throwable) {}
    }

    private fun getConfigFile(): File? {
        return try {
            val gameDir = Minecraft.getInstance().gameDirectory ?: return null
            val configDir = File(gameDir, "config")
            if (!configDir.exists()) configDir.mkdirs()
            File(configDir, "cobblemon_tracker.json")
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureConfigLoaded() {
        if (!isConfigLoaded) {
            loadConfig()
        }
    }

    fun saveConfig() {
        try {
            val file = getConfigFile() ?: return
            val root = JsonObject()
            root.addProperty("mode", currentMode.name)

            targetSpecies?.let {
                root.addProperty("targetSpecies", it.resourceIdentifier.toString())
            }
            targetForm?.let {
                root.addProperty("targetForm", it.name)
            }
            evolutionBaseSpecies?.let {
                root.addProperty("evolutionBaseSpecies", it.resourceIdentifier.toString())
            }

            val listArray = JsonArray()
            for (sp in customWatchlist) {
                try {
                    listArray.add(sp.resourceIdentifier.toString())
                } catch (_: Throwable) {
                    listArray.add(sp.name)
                }
            }
            root.add("customWatchlist", listArray)
            root.add("advancedFilter", advancedFilter.toJson())

            file.writeText(gson.toJson(root), Charsets.UTF_8)
        } catch (e: Throwable) {
            logger.debug("Erreur sauvegarde config tracker: {}", e.message)
        }
    }

    fun loadConfig() {
        isConfigLoaded = true
        try {
            val file = getConfigFile() ?: return
            if (!file.exists()) return

            val json = file.readText(Charsets.UTF_8)
            val root = JsonParser.parseString(json).asJsonObject

            if (root.has("mode")) {
                val modeStr = root.get("mode").asString
                currentMode = try { TrackingMode.valueOf(modeStr) } catch (_: Throwable) { TrackingMode.OFF }
            }

            PokemonEvolutionDataLoader.ensureSpeciesLoaded()

            if (root.has("targetSpecies")) {
                val idStr = root.get("targetSpecies").asString
                targetSpecies = PokemonEvolutionDataLoader.findSpeciesByName(idStr)
            }

            if (root.has("evolutionBaseSpecies")) {
                val idStr = root.get("evolutionBaseSpecies").asString
                evolutionBaseSpecies = PokemonEvolutionDataLoader.findSpeciesByName(idStr)
                if (evolutionBaseSpecies != null) {
                    evolutionFamily = getEvolutionFamily(evolutionBaseSpecies!!)
                }
            }

            if (root.has("customWatchlist") && root.get("customWatchlist").isJsonArray) {
                customWatchlist.clear()
                val array = root.getAsJsonArray("customWatchlist")
                for (elem in array) {
                    val spName = elem.asString
                    val sp = PokemonEvolutionDataLoader.findSpeciesByName(spName)
                    if (sp != null) {
                        customWatchlist.add(sp)
                    }
                }
            }

            if (root.has("advancedFilter") && root.get("advancedFilter").isJsonObject) {
                advancedFilter.fromJson(root.getAsJsonObject("advancedFilter"))
            }
        } catch (e: Throwable) {
            logger.debug("Erreur chargement config tracker: {}", e.message)
        }
    }
}
