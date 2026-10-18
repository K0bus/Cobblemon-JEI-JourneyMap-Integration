package fr.k0bus.cobblemon_jei.leaderboard

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Scoreboard
import java.util.UUID

/**
 * Service client de détection et d'extraction des classements depuis le Scoreboard vanilla
 * alimenté par CobblemonSB sur le serveur.
 */
object CobblemonScoreboardService {

    private val skinCache = java.util.concurrent.ConcurrentHashMap<String, PlayerSkin>()
    private val pendingSkinRequests = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val offlineUuidCache = java.util.concurrent.ConcurrentHashMap<String, UUID>()
    private var lastCacheTime = 0L
    private val entriesCache = mutableMapOf<CobblemonLeaderboardCategory, List<LeaderboardPlayerEntry>>()

    /**
     * Vérifie si le système de classement CobblemonSB est disponible sur le serveur actuel.
     * Si aucun des objectifs 'cobble_*' ou leurs alias n'est présent dans le Scoreboard, la feature est désactivée.
     */
    fun isAvailable(): Boolean {
        val scoreboard = getScoreboard() ?: return false
        return CobblemonLeaderboardCategory.entries.any { cat ->
            cat.allObjectiveNames.any { scoreboard.getObjective(it) != null }
        }
    }

    /**
     * Retourne la liste des catégories effectivement configurées et présentes sur le serveur.
     * Si au moins une catégorie est détectée, l'ensemble des catégories standard est mis à disposition.
     */
    fun getAvailableCategories(): List<CobblemonLeaderboardCategory> {
        if (!isAvailable()) return emptyList()
        return CobblemonLeaderboardCategory.entries
    }

    /**
     * Trouve le premier objectif scoreboard correspondant à une catégorie parmi ses noms et alias.
     */
    fun findObjective(scoreboard: Scoreboard, category: CobblemonLeaderboardCategory): Objective? {
        return category.allObjectiveNames.firstNotNullOfOrNull { scoreboard.getObjective(it) }
    }

    /**
     * Filtre les faux joueurs (fakeplayers de mods comme Create, ComputerCraft, SFM...),
     * les placeholders techniques de scoreboards (c_sb_*, #*, $*, %*) et les entrées invalides.
     */
    fun isRealPlayerName(name: String): Boolean {
        if (name.isBlank()) return false
        // Exclure les faux joueurs délimités par des crochets [Create], [SFM], etc.
        if (name.startsWith("[") || name.endsWith("]") || name.startsWith("Player_")) return false
        // Exclure les placeholders scoreboard internes (ex: c_sb_1, #dummy, etc.)
        if (name.startsWith("#") || name.startsWith("$") || name.startsWith("%") || name.startsWith("c_sb_") || name.startsWith("!")) return false
        // Exclure les noms avec espaces ou séparateurs non valides pour un pseudo Minecraft
        if (name.contains(" ") || name.contains(":") || name.contains("/") || name.contains("\\")) return false
        // Un pseudo Minecraft (Java ou Bedrock via Floodgate/Geyser) comporte entre 3 et 16 caractères
        return name.matches(Regex("^[.*+]?[a-zA-Z0-9_]{3,16}$"))
    }

    /**
     * Récupère le classement complet d'une catégorie donnée, trié par score décroissant.
     */
    fun getEntries(category: CobblemonLeaderboardCategory): List<LeaderboardPlayerEntry> {
        val now = System.currentTimeMillis()
        if (now - lastCacheTime < 1000L && entriesCache.containsKey(category)) {
            return entriesCache[category] ?: emptyList()
        }

        val scoreboard = getScoreboard()
        val mc = try { Minecraft.getInstance() } catch (_: Throwable) { null }
        val localName = mc?.player?.scoreboardName ?: ""

        val localStats = try {
            fr.k0bus.cobblemon_jei.pokedex.PokemonTrackerService.getStats()
        } catch (_: Throwable) {
            null
        }

        // Rassemble tous les joueurs répertoriés sur les objectifs scoreboard Cobblemon (en excluant les fakeplayers)
        val allKnownOwners = mutableSetOf<String>()
        if (scoreboard != null) {
            for (cat in CobblemonLeaderboardCategory.entries) {
                for (objName in cat.allObjectiveNames) {
                    val obj = scoreboard.getObjective(objName) ?: continue
                    try {
                        val minScore = if (cat == CobblemonLeaderboardCategory.CAP_LEVEL) 5 else 0
                        for (score in scoreboard.listPlayerScores(obj)) {
                            if (score.value > minScore && !score.isHidden && isRealPlayerName(score.owner)) {
                                allKnownOwners.add(score.owner)
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
        }
        if (localName.isNotBlank() && isRealPlayerName(localName)) {
            allKnownOwners.add(localName)
        }

        val result = mutableListOf<LeaderboardPlayerEntry>()

        for (owner in allKnownOwners) {
            val isLocal = owner.equals(localName, ignoreCase = true)
            val skin = resolvePlayerSkin(owner)

            val pCaptured = if (isLocal && localStats != null) localStats.caughtCount else (if (scoreboard != null) getScore(scoreboard, owner, CobblemonLeaderboardCategory.CAPTURES.allObjectiveNames) else 0)
            val pSeen = if (isLocal && localStats != null) localStats.encounteredCount else {
                if (scoreboard != null) getScore(scoreboard, owner, listOf("cobble_seen", "cobble_scanned", "seen")) else 0
            }
            val pRawDex = if (scoreboard != null) getScore(scoreboard, owner, CobblemonLeaderboardCategory.POKEDEX.allObjectiveNames) else 0
            val pDex = if (isLocal && localStats != null) {
                // Cumul des entrées découvertes : vus + capturés
                localStats.caughtCount + localStats.encounteredCount
            } else {
                if (pRawDex > 0) pRawDex else (pCaptured + pSeen)
            }

            val pShinies = if (scoreboard != null) getScore(scoreboard, owner, CobblemonLeaderboardCategory.SHINIES.allObjectiveNames) else 0
            val pCapLevel = if (scoreboard != null) getScore(scoreboard, owner, CobblemonLeaderboardCategory.CAP_LEVEL.allObjectiveNames) else 0
            val effectiveCapLevel = if (pCapLevel > 5) pCapLevel else 0

            val effectiveCaught = if (isLocal && localStats != null) localStats.caughtCount else maxOf(pCaptured, if (category == CobblemonLeaderboardCategory.CAPTURES) pCaptured else 0)
            val effectiveSeen = if (isLocal && localStats != null) localStats.encounteredCount else {
                if (pSeen > 0) pSeen else maxOf(0, pDex - effectiveCaught)
            }

            val score = when (category) {
                CobblemonLeaderboardCategory.POKEDEX -> pDex
                CobblemonLeaderboardCategory.CAPTURES -> effectiveCaught
                CobblemonLeaderboardCategory.SHINIES -> pShinies
                CobblemonLeaderboardCategory.CAP_LEVEL -> effectiveCapLevel
            }

            if (score > 0) {
                result.add(
                    LeaderboardPlayerEntry(
                        rank = 1,
                        username = owner,
                        score = score,
                        isLocalPlayer = isLocal,
                        skin = skin,
                        caughtCount = effectiveCaught,
                        seenCount = effectiveSeen,
                        shiniesCount = pShinies
                    )
                )
            }
        }

        result.sortByDescending { it.score }
        for ((idx, entry) in result.withIndex()) {
            result[idx] = entry.copy(rank = idx + 1)
        }

        entriesCache[category] = result
        lastCacheTime = now
        return result
    }

    private fun getScore(scoreboard: Scoreboard, owner: String, objectiveNames: List<String>): Int {
        for (name in objectiveNames) {
            val obj = scoreboard.getObjective(name) ?: continue
            try {
                val holder = net.minecraft.world.scores.ScoreHolder.forNameOnly(owner)
                val info = scoreboard.getPlayerScoreInfo(holder, obj)
                val v = info?.value() ?: 0
                if (v > 0) return v
            } catch (_: Throwable) {}
        }
        return 0
    }

    data class PlayerPersonalStats(
        val pokedex: Int,
        val caught: Int,
        val seen: Int,
        val shinies: Int,
        val capLevel: Int
    )

    /**
     * Récupère les statistiques personnelles du joueur local couvrant tous les objectifs.
     */
    fun getLocalPersonalStats(): PlayerPersonalStats {
        val scoreboard = getScoreboard()
        val mc = try { Minecraft.getInstance() } catch (_: Throwable) { null }
        val localName = mc?.player?.scoreboardName ?: ""

        val localStats = try {
            fr.k0bus.cobblemon_jei.pokedex.PokemonTrackerService.getStats()
        } catch (_: Throwable) {
            null
        }

        val caught = localStats?.caughtCount ?: (if (scoreboard != null && localName.isNotBlank()) getScore(scoreboard, localName, CobblemonLeaderboardCategory.CAPTURES.allObjectiveNames) else 0)
        val seen = localStats?.encounteredCount ?: (if (scoreboard != null && localName.isNotBlank()) getScore(scoreboard, localName, listOf("cobble_seen", "cobble_scanned", "seen")) else 0)
        val dex = if (localStats != null) (caught + seen) else (if (scoreboard != null && localName.isNotBlank()) getScore(scoreboard, localName, CobblemonLeaderboardCategory.POKEDEX.allObjectiveNames) else (caught + seen))
        val shinies = if (scoreboard != null && localName.isNotBlank()) getScore(scoreboard, localName, CobblemonLeaderboardCategory.SHINIES.allObjectiveNames) else 0
        val cap = if (scoreboard != null && localName.isNotBlank()) getScore(scoreboard, localName, CobblemonLeaderboardCategory.CAP_LEVEL.allObjectiveNames) else 0

        return PlayerPersonalStats(
            pokedex = dex,
            caught = caught,
            seen = seen,
            shinies = shinies,
            capLevel = if (cap > 5) cap else 0
        )
    }

    /**
     * Récupère les X premiers joueurs pour l'affichage (HUD ou tableau).
     */
    fun getTopEntries(category: CobblemonLeaderboardCategory, limit: Int = 10): List<LeaderboardPlayerEntry> {
        return getEntries(category).take(limit)
    }

    /**
     * Récupère l'entrée du joueur local dans le classement d'une catégorie.
     */
    fun getLocalPlayerEntry(category: CobblemonLeaderboardCategory): LeaderboardPlayerEntry? {
        val entries = getEntries(category)
        return entries.firstOrNull { it.isLocalPlayer }
    }

    /**
     * Résout la texture de skin Minecraft d'un joueur :
     * 1. Joueur en ligne : extrait directement de la tablist client.
     * 2. Joueur hors ligne : résolution asynchrone du profil Mojang / SessionService
     *    pour charger la vraie texture de skin (avec fallback temporaire non bloquant).
     */
    fun resolvePlayerSkin(username: String): PlayerSkin {
        skinCache[username]?.let { return it }

        val mc = try { Minecraft.getInstance() } catch (_: Throwable) { null }
        val playerInfo = mc?.connection?.getPlayerInfo(username)
        if (playerInfo != null) {
            val skin = playerInfo.skin
            skinCache[username] = skin
            return skin
        }

        // Lancer la résolution asynchrone en arrière-plan sans bloquer le rendu
        if (mc != null && isRealPlayerName(username) && pendingSkinRequests.add(username)) {
            java.util.concurrent.CompletableFuture.runAsync {
                try {
                    var uuid = offlineUuidCache[username]

                    // 1. Résolution de l'UUID via l'API Mojang (rapide et direct)
                    if (uuid == null) {
                        try {
                            val url = java.net.URI("https://api.mojang.com/users/profiles/minecraft/$username").toURL()
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 3000
                            conn.readTimeout = 3000
                            conn.setRequestProperty("User-Agent", "Minecraft")
                            if (conn.responseCode == 200) {
                                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                                val idIdx = resp.indexOf("\"id\":\"")
                                if (idIdx != -1) {
                                    val hex = resp.substring(idIdx + 6, idIdx + 6 + 32)
                                    val formatted = "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
                                    uuid = UUID.fromString(formatted)
                                    offlineUuidCache[username] = uuid
                                }
                            }
                        } catch (_: Throwable) {}
                    }

                    // 2. Si échec de l'API web, tentative via le cache local de skulls / GameProfileCache
                    if (uuid == null) {
                        try {
                            val opt = net.minecraft.world.level.block.entity.SkullBlockEntity.fetchGameProfile(username).get(2, java.util.concurrent.TimeUnit.SECONDS)
                            if (opt.isPresent) {
                                uuid = opt.get().id
                                offlineUuidCache[username] = uuid
                            }
                        } catch (_: Throwable) {}
                    }

                    // 3. Récupération des textures via le SessionService officiel de Minecraft
                    if (uuid != null) {
                        val sessionService = mc.minecraftSessionService
                        val profileResult = sessionService.fetchProfile(uuid, false)
                        if (profileResult != null && profileResult.profile != null) {
                            mc.execute {
                                try {
                                    val realSkin = mc.skinManager.getInsecureSkin(profileResult.profile)
                                    skinCache[username] = realSkin
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        val offlineUuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray())
        return DefaultPlayerSkin.get(offlineUuid)
    }

    /**
     * Invalide le cache pour forcer un rafraîchissement immédiat.
     */
    fun clearCache() {
        entriesCache.clear()
        lastCacheTime = 0L
    }

    private fun getScoreboard(): Scoreboard? {
        val mc = try {
            Minecraft.getInstance()
        } catch (_: Throwable) {
            null
        } ?: return null

        return try {
            mc.level?.scoreboard ?: mc.connection?.scoreboard()
        } catch (_: Throwable) {
            null
        }
    }
}
