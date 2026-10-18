package fr.k0bus.cobblemon_jei.compat.rct

import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import kotlin.math.max

/**
 * Service gérant le suivi de la progression du joueur dans Radical Cobblemon Trainers.
 * Détecte les dresseurs vaincus, la série en cours, le cap de niveau actuel
 * et calcule la disposition de l'arbre graphique de manière hautement optimisée.
 */
object RctProgressionService {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/RctProgression")

    // Dresseurs marqués vaincus (interrogés directement auprès du mod RCT et advancements)
    private val defeatedTrainers = mutableSetOf<String>()

    private var selectedSeriesId: String = "radicalred"
    private var onlyKeyTrainers: Boolean = true

    init {
        refresh()
    }

    /**
     * Rafraîchit les données de progression en interrogeant directement le mod RCT et les succès.
     */
    fun refresh() {
        RctDataLoader.ensureLoaded()

        // 1. Interrogation directe du mod RCT (PlayerState client et serveur interne)
        detectDefeatedFromRctMod()

        // 2. Détection complémentaire via les Advancements Minecraft
        detectDefeatedFromAdvancements()

        // 3. Choix de la série par défaut
        val allSeries = RctDataLoader.getAllSeries()
        if (allSeries.isNotEmpty() && allSeries.none { it.id == selectedSeriesId }) {
            selectedSeriesId = allSeries.first().id
        }
    }

    fun getSelectedSeriesId(): String = selectedSeriesId

    fun setSelectedSeriesId(id: String) {
        selectedSeriesId = id
    }

    fun isOnlyKeyTrainers(): Boolean = onlyKeyTrainers

    fun setOnlyKeyTrainers(value: Boolean) {
        onlyKeyTrainers = value
    }

    fun isTrainerDefeated(trainerId: String): Boolean {
        return defeatedTrainers.contains(trainerId)
    }

    @org.jetbrains.annotations.VisibleForTesting
    fun setTrainerDefeatedForTesting(trainerId: String, defeated: Boolean) {
        if (defeated) {
            defeatedTrainers.add(trainerId)
        } else {
            defeatedTrainers.remove(trainerId)
        }
    }

    fun resetProgress(seriesId: String) {
        val trainers = RctDataLoader.getTrainersForSeries(seriesId)
        for (t in trainers) {
            defeatedTrainers.remove(t.id)
        }
    }

    /**
     * Interroge directement les structures du mod Radical Cobblemon Trainers (RCT).
     */
    private fun detectDefeatedFromRctMod() {
        try {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return

            // 1. Appel du PlayerState côté client RCT
            try {
                val playerStateClass = Class.forName("com.gitlab.srcmc.rctmod.api.data.sync.PlayerState")
                val getMethod = playerStateClass.getMethod("get", net.minecraft.world.entity.player.Player::class.java)
                val playerState = getMethod.invoke(null, player)
                if (playerState != null) {
                    val defeatedMethod = playerState.javaClass.getMethod("getDefeatedTrainerIds")
                    val set = defeatedMethod.invoke(playerState) as? Set<*>
                    if (set != null) {
                        for (item in set) {
                            if (item is String) defeatedTrainers.add(item)
                        }
                    }

                    // Récupère la série active du joueur dans RCT
                    try {
                        val currentSeriesMethod = playerState.javaClass.getMethod("getCurrentSeries")
                        val activeSeries = currentSeriesMethod.invoke(playerState) as? String
                        if (!activeSeries.isNullOrBlank() && RctDataLoader.getSeries(activeSeries) != null) {
                            selectedSeriesId = activeSeries
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}

            // 2. Si serveur intégré (Singleplayer), interroger directement le serveur interne
            try {
                val singleplayer = mc.singleplayerServer
                if (singleplayer != null) {
                    val serverPlayer = singleplayer.playerList.getPlayer(player.uuid)
                    if (serverPlayer != null) {
                        try {
                            val playerStateClass = Class.forName("com.gitlab.srcmc.rctmod.api.data.sync.PlayerState")
                            val getMethod = playerStateClass.getMethod("get", net.minecraft.world.entity.player.Player::class.java)
                            val serverState = getMethod.invoke(null, serverPlayer)
                            if (serverState != null) {
                                val defeatedMethod = serverState.javaClass.getMethod("getDefeatedTrainerIds")
                                val set = defeatedMethod.invoke(serverState) as? Set<*>
                                if (set != null) {
                                    for (item in set) {
                                        if (item is String) defeatedTrainers.add(item)
                                    }
                                }
                            }
                        } catch (_: Throwable) {}

                        try {
                            val dataClass = Class.forName("com.gitlab.srcmc.rctmod.api.data.save.TrainerPlayerData")
                            val forPlayerMethod = dataClass.getMethod("forPlayer", net.minecraft.world.entity.player.Player::class.java)
                            val playerData = forPlayerMethod.invoke(null, serverPlayer)
                            if (playerData != null) {
                                val dataDefeatedMethod = playerData.javaClass.getMethod("getDefeatedTrainerIds")
                                val set = dataDefeatedMethod.invoke(playerData) as? Set<*>
                                if (set != null) {
                                    for (item in set) {
                                        if (item is String) defeatedTrainers.add(item)
                                    }
                                }
                                val seriesMethod = playerData.javaClass.getMethod("getCurrentSeries")
                                val s = seriesMethod.invoke(playerData) as? String
                                if (!s.isNullOrBlank() && RctDataLoader.getSeries(s) != null) {
                                    selectedSeriesId = s
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            logger.debug("Erreur interrogation directe mod RCT: {}", e.message)
        }
    }

    /**
     * Analyse rapide des advancements reçus par le joueur connecté en O(1) par succès.
     */
    private fun detectDefeatedFromAdvancements() {
        try {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return
            val conn = player.connection
            val advManager = conn.advancements

            val allTrainers = RctDataLoader.getAllTrainers()
            if (allTrainers.isEmpty()) return

            val trainerIdSet = allTrainers.map { it.id }.toHashSet()
            val trainerIdentityMap = allTrainers.filter { it.identity.isNotBlank() }
                .associateBy({ it.identity.lowercase() }, { it.id })

            val progressField = advManager.javaClass.declaredFields.firstOrNull {
                java.util.Map::class.java.isAssignableFrom(it.type)
            }?.apply { isAccessible = true }

            val progressMap = progressField?.get(advManager) as? Map<*, *>
            if (progressMap != null) {
                for ((holder, advProgress) in progressMap) {
                    if (holder != null && advProgress != null) {
                        try {
                            val isDoneMethod = advProgress.javaClass.getMethod("isDone")
                            val isDone = isDoneMethod.invoke(advProgress) as? Boolean ?: false
                            if (isDone) {
                                val idMethod = holder.javaClass.getMethod("id")
                                val loc = idMethod.invoke(holder)
                                val idStr = loc?.toString() ?: ""

                                if (idStr.contains("rctmod", ignoreCase = true)) {
                                    val path = idStr.substringAfterLast('/')
                                    val extractedId = path.removePrefix("defeat_")
                                    if (trainerIdSet.contains(extractedId)) {
                                        defeatedTrainers.add(extractedId)
                                    } else if (trainerIdentityMap.containsKey(extractedId.lowercase())) {
                                        defeatedTrainers.add(trainerIdentityMap[extractedId.lowercase()]!!)
                                    } else {
                                        for (trainer in allTrainers) {
                                            if (path.contains(trainer.id, ignoreCase = true)) {
                                                defeatedTrainers.add(trainer.id)
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (e: Throwable) {
            logger.debug("Erreur lecture advancements RCT: {}", e.message)
        }
    }

    /**
     * Construit le graphe 2D complet pour la série sélectionnée.
     */
    fun buildTreeGraph(seriesId: String, keyTrainersOnly: Boolean = onlyKeyTrainers): RctTreeGraph {
        refresh()

        val series = RctDataLoader.getSeries(seriesId) ?: RctSeries(
            id = seriesId,
            title = seriesId.replaceFirstChar { it.uppercase() }
        )

        val rawTrainers = RctDataLoader.getTrainersForSeries(seriesId)
        val allTrainersMap = rawTrainers.associateBy { it.id }

        // Si le mod contient un volume massif de dresseurs (> 50 comme Radical Red avec 1500+ dresseurs),
        // on filtre intelligemment sur les dresseurs clés de progression par défaut pour une lisibilité et des FPS parfaits.
        val trainers = if (keyTrainersOnly && rawTrainers.size > 50) {
            val keySet = HashSet<String>()
            for (t in rawTrainers) {
                if (!t.optional ||
                    t.requiredDefeats.isNotEmpty() ||
                    t.type in listOf("leader", "boss", "rival", "elite_four", "champion", "gym_leader")) {
                    keySet.add(t.id)
                }
            }
            // Inclut également les prérequis directs
            for (t in rawTrainers) {
                if (keySet.contains(t.id)) {
                    for (group in t.requiredDefeats) {
                        keySet.addAll(group)
                    }
                }
            }
            rawTrainers.filter { keySet.contains(it.id) }
        } else {
            rawTrainers
        }

        // 1. Détermination du statut de chaque dresseur
        val nodeMap = mutableMapOf<String, RctTreeNode>()
        for (trainer in trainers) {
            val isDefeated = isTrainerDefeated(trainer.id)
            val isAvailable = !isDefeated && isTrainerAvailable(trainer, allTrainersMap)

            val status = when {
                isDefeated -> RctProgressionStatus.DEFEATED
                isAvailable -> RctProgressionStatus.AVAILABLE
                else -> RctProgressionStatus.LOCKED
            }

            nodeMap[trainer.id] = RctTreeNode(
                trainer = trainer,
                status = status
            )
        }

        // 2. Établissement des prédécesseurs et successeurs
        for (trainer in trainers) {
            val node = nodeMap[trainer.id] ?: continue
            for (reqGroup in trainer.requiredDefeats) {
                for (reqId in reqGroup) {
                    if (nodeMap.containsKey(reqId)) {
                        node.predecessorIds.add(reqId)
                        nodeMap[reqId]?.successorIds?.add(trainer.id)
                    }
                }
            }
        }

        // 3. Calcul du rang topologique (Layering)
        computeRanks(nodeMap)

        // 4. Disposition dans l'espace 2D (Positionnement X, Y)
        val ranks = nodeMap.values.groupBy { it.rank }.toSortedMap()

        val cardW = 145.0
        val cardH = 56.0
        val colGap = 85.0
        val rowGap = 35.0
        val startX = 60.0

        var maxTotalHeight = 0.0

        val rankHeights = ranks.mapValues { (_, nodes) ->
            nodes.size * cardH + (nodes.size - 1).coerceAtLeast(0) * rowGap
        }
        val globalMaxHeight = rankHeights.values.maxOrNull() ?: 200.0

        for ((rank, nodesInRank) in ranks) {
            val colX = startX + rank * (cardW + colGap)

            val sortedNodes = nodesInRank.sortedWith(
                compareBy<RctTreeNode> { it.trainer.optional }
                    .thenBy { it.trainer.computedLevelCap }
                    .thenBy { it.trainer.name }
            )

            val totalColH = sortedNodes.size * cardH + (sortedNodes.size - 1).coerceAtLeast(0) * rowGap
            val startY = 80.0 + (globalMaxHeight - totalColH) / 2.0

            sortedNodes.forEachIndexed { index, node ->
                node.row = index
                node.x = colX
                node.y = startY + index * (cardH + rowGap)
            }

            maxTotalHeight = max(maxTotalHeight, startY + totalColH + 80.0)
        }

        val maxRank = ranks.keys.maxOrNull() ?: 0
        val totalWidth = startX + (maxRank + 1) * (cardW + colGap) + 120.0
        val totalHeight = max(maxTotalHeight, 500.0)

        // 5. Génération des arêtes (Edges)
        val edges = mutableListOf<RctTreeEdge>()
        for (node in nodeMap.values) {
            for (predId in node.predecessorIds) {
                val predNode = nodeMap[predId] ?: continue
                val isComplete = (predNode.status == RctProgressionStatus.DEFEATED && node.status == RctProgressionStatus.DEFEATED)
                val isReady = (predNode.status == RctProgressionStatus.DEFEATED && node.status == RctProgressionStatus.AVAILABLE)

                edges.add(
                    RctTreeEdge(
                        fromNode = predNode,
                        toNode = node,
                        isComplete = isComplete,
                        isReady = isReady
                    )
                )
            }
        }

        // 6. Calcul des statistiques et du Cap de niveau du joueur
        val defeatedInSeries = rawTrainers.filter { isTrainerDefeated(it.id) }
        val playerCap = maxOf(
            series.initialLevelCap,
            defeatedInSeries.map { it.computedLevelCap }.maxOrNull() ?: series.initialLevelCap
        )

        return RctTreeGraph(
            series = series,
            nodes = nodeMap.values.toList(),
            edges = edges,
            totalWidth = totalWidth,
            totalHeight = totalHeight,
            defeatedCount = defeatedInSeries.size,
            totalCount = rawTrainers.size,
            playerLevelCap = playerCap
        )
    }

    private fun isTrainerAvailable(trainer: RctTrainer, trainerMap: Map<String, RctTrainer>): Boolean {
        if (trainer.requiredDefeats.isEmpty()) return true

        for (innerList in trainer.requiredDefeats) {
            val anyDefeatedInGroup = innerList.any { reqId ->
                !trainerMap.containsKey(reqId) || isTrainerDefeated(reqId)
            }
            if (!anyDefeatedInGroup) return false
        }
        return true
    }

    private fun computeRanks(nodeMap: Map<String, RctTreeNode>) {
        val memo = mutableMapOf<String, Int>()
        val visiting = mutableSetOf<String>()

        fun getRank(id: String): Int {
            if (memo.containsKey(id)) return memo[id]!!
            if (visiting.contains(id)) return 0

            visiting.add(id)
            val node = nodeMap[id]
            val rank = if (node == null || node.predecessorIds.isEmpty()) {
                0
            } else {
                val maxPredRank = node.predecessorIds.map { getRank(it) }.maxOrNull() ?: 0
                maxPredRank + 1
            }
            visiting.remove(id)
            memo[id] = rank
            return rank
        }

        for (node in nodeMap.values) {
            node.rank = getRank(node.trainer.id)
        }
    }
}
