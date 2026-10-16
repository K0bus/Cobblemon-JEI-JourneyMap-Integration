package fr.k0bus.cobblemon_jei.journeymap

import journeymap.api.v2.client.model.MapPolygon
import journeymap.api.v2.client.model.MapPolygonWithHoles
import journeymap.api.v2.client.util.PolygonHelper
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import org.slf4j.LoggerFactory

/**
 * Algorithme de fusion géométrique et d'union 2D de chunks contigus.
 * Détecte les arêtes frontalières extérieures, les assemble en contours fermés continus
 * (avec anneaux extérieurs et trous intérieurs), simplifie les segments colinéaires
 * et génère des [MapPolygonWithHoles] sans aucune arête de quadrillage interne.
 */
object ChunkPolygonMerger {

    private val logger = LoggerFactory.getLogger("CobblemonJM/Merger")

    private data class GridPoint(val x: Int, val z: Int)

    private data class DirectedEdge(
        val start: GridPoint,
        val end: GridPoint,
        val dx: Int,
        val dz: Int
    )

    /**
     * Décompose un ensemble de chunks en composantes connexes orthogonales (4-connectivité : N, S, E, O).
     * Garantit que chaque îlot de chunks est traité de manière autonome avec une boîte englobante minimale.
     */
    fun getConnectedComponents(chunks: Set<ChunkPos>): List<Set<ChunkPos>> {
        val unvisited = HashSet(chunks)
        val components = mutableListOf<Set<ChunkPos>>()

        while (unvisited.isNotEmpty()) {
            val start = unvisited.iterator().next()
            unvisited.remove(start)
            val component = HashSet<ChunkPos>()
            val queue = ArrayDeque<ChunkPos>()
            queue.add(start)
            component.add(start)

            while (queue.isNotEmpty()) {
                val curr = queue.removeFirst()
                val neighbors = arrayOf(
                    ChunkPos(curr.x + 1, curr.z),
                    ChunkPos(curr.x - 1, curr.z),
                    ChunkPos(curr.x, curr.z + 1),
                    ChunkPos(curr.x, curr.z - 1)
                )
                for (neighbor in neighbors) {
                    if (unvisited.remove(neighbor)) {
                        component.add(neighbor)
                        queue.add(neighbor)
                    }
                }
            }
            components.add(component)
        }
        return components
    }

    /**
     * Fusionne un ensemble de [ChunkPos] en une liste optimisée de [MapPolygonWithHoles].
     * Traite chaque composante connexe indépendamment pour éviter les boîtes englobantes géantes.
     *
     * @param chunks Ensemble des chunks éligibles partageant la même rareté.
     * @param y Altitude Y pour la projection JourneyMap.
     * @return Liste de polygones englobants avec découpe des trous.
     */
    fun mergeChunks(chunks: Set<ChunkPos>, y: Int): List<MapPolygonWithHoles> {
        if (chunks.isEmpty()) return emptyList()

        val components = getConnectedComponents(chunks)
        val allPolygons = mutableListOf<MapPolygonWithHoles>()

        for (component in components) {
            allPolygons.addAll(mergeSingleComponent(component, y))
        }

        return allPolygons
    }

    private fun mergeSingleComponent(component: Set<ChunkPos>, y: Int): List<MapPolygonWithHoles> {
        if (component.isEmpty()) return emptyList()

        try {
            val result = mergeChunksInternal(component, y)
            if (result.isNotEmpty()) return result
        } catch (e: Exception) {
            logger.warn("Échec de la fusion géométrique sur une composante de ${component.size} chunks, repli sur PolygonHelper : {}", e.message)
        }

        try {
            val fallback = PolygonHelper.createChunksPolygon(component, y)
            if (fallback.isNotEmpty()) return fallback
        } catch (fallbackEx: Exception) {
            logger.error("Échec du fallback PolygonHelper.createChunksPolygon : {}", fallbackEx.message)
        }

        // Repli ultime individuel (1 polygone par chunk éligible) garantissant l'affichage absolu
        return component.map { pos ->
            val singleHull = PolygonHelper.createChunkPolygon(pos.x, y, pos.z)
            MapPolygonWithHoles(singleHull, null)
        }
    }

    private fun mergeChunksInternal(chunks: Set<ChunkPos>, y: Int): List<MapPolygonWithHoles> {
        // 1. Détection des arêtes frontières (boundary edges) non partagées
        val edges = mutableListOf<DirectedEdge>()
        for (pos in chunks) {
            val cx = pos.x
            val cz = pos.z

            val nw = GridPoint(cx, cz)
            val ne = GridPoint(cx + 1, cz)
            val se = GridPoint(cx + 1, cz + 1)
            val sw = GridPoint(cx, cz + 1)

            // Bordure Nord : présente si aucun voisin au Nord (cz - 1)
            if (!chunks.contains(ChunkPos(cx, cz - 1))) {
                edges.add(DirectedEdge(ne, nw, -1, 0))
            }
            // Bordure Est : présente si aucun voisin à l'Est (cx + 1)
            if (!chunks.contains(ChunkPos(cx + 1, cz))) {
                edges.add(DirectedEdge(se, ne, 0, -1))
            }
            // Bordure Sud : présente si aucun voisin au Sud (cz + 1)
            if (!chunks.contains(ChunkPos(cx, cz + 1))) {
                edges.add(DirectedEdge(sw, se, 1, 0))
            }
            // Bordure Ouest : présente si aucun voisin à l'Ouest (cx - 1)
            if (!chunks.contains(ChunkPos(cx - 1, cz))) {
                edges.add(DirectedEdge(nw, sw, 0, 1))
            }
        }

        if (edges.isEmpty()) return emptyList()

        // Indexation des arêtes sortantes par sommet de départ
        val outgoing = mutableMapOf<GridPoint, MutableList<DirectedEdge>>()
        for (edge in edges) {
            outgoing.computeIfAbsent(edge.start) { mutableListOf() }.add(edge)
        }

        // 2. Assemblage des arêtes en contours fermés continus
        val rawLoops = mutableListOf<List<GridPoint>>()
        while (outgoing.values.any { it.isNotEmpty() }) {
            val startEntry = outgoing.entries.firstOrNull { it.value.isNotEmpty() } ?: break
            val startVertex = startEntry.key
            val firstEdge = startEntry.value.removeAt(0)

            val loop = mutableListOf<GridPoint>()
            loop.add(startVertex)

            var currentVertex = firstEdge.end
            var lastDirX = firstEdge.dx
            var lastDirZ = firstEdge.dz

            var safetyCounter = 0
            val maxSteps = edges.size * 2 + 10

            while (currentVertex != startVertex && safetyCounter++ < maxSteps) {
                loop.add(currentVertex)
                val candidates = outgoing[currentVertex]
                if (candidates.isNullOrEmpty()) break

                // Priorité au virage le plus à gauche pour suivre la matière du chunk
                candidates.sortBy { turnPriority(lastDirX, lastDirZ, it.dx, it.dz) }
                val nextEdge = candidates.removeAt(0)
                currentVertex = nextEdge.end
                lastDirX = nextEdge.dx
                lastDirZ = nextEdge.dz
            }

            if (loop.size >= 3) {
                rawLoops.add(loop)
            }
        }

        // 3. Simplification des points colinéaires pour chaque contour
        val simplifiedLoops = rawLoops.map { simplifyCollinear(it) }.filter { it.size >= 3 }

        // 4. Séparation entre anneaux extérieurs (hulls) et trous (holes) via le calcul de l'aire signée
        val hulls = mutableListOf<List<GridPoint>>()
        val holes = mutableListOf<List<GridPoint>>()

        for (loop in simplifiedLoops) {
            val signedArea = calculateSignedArea(loop)
            if (signedArea < 0) {
                // Aire négative dans notre repère (X croissant vers l'Est, Z croissant vers le Sud) = contour extérieur
                hulls.add(loop)
            } else if (signedArea > 0) {
                // Aire positive = trou intérieur
                holes.add(loop)
            }
        }

        // 5. Association des trous intérieurs à leur contour extérieur englobant
        val hullWithHolesMap = hulls.associateWith { mutableListOf<List<GridPoint>>() }.toMutableMap()
        for (hole in holes) {
            val holeSample = hole.first()
            val enclosingHull = hulls.firstOrNull { hull -> isPointInsidePolygon(holeSample, hull) }
            if (enclosingHull != null) {
                hullWithHolesMap[enclosingHull]?.add(hole)
            }
        }

        // 6. Conversion en objets MapPolygonWithHoles de l'API JourneyMap (coordonnées blocs x16)
        val resultPolygons = mutableListOf<MapPolygonWithHoles>()
        for ((hullPoints, holeLoops) in hullWithHolesMap) {
            val hullBlockPos = toBlockPosList(hullPoints, y)
            if (hullBlockPos.size < 3) continue

            val outerMapPolygon = MapPolygon(hullBlockPos)
            val holeMapPolygons = holeLoops.mapNotNull { holePts ->
                val blockPts = toBlockPosList(holePts, y)
                if (blockPts.size >= 3) MapPolygon(blockPts) else null
            }

            resultPolygons.add(
                MapPolygonWithHoles(
                    outerMapPolygon,
                    holeMapPolygons.ifEmpty { null }
                )
            )
        }

        return resultPolygons
    }

    /**
     * Calcule la priorité de virage en coordonnées entières :
     * 1 = Virage à gauche (90°)
     * 2 = Tout droit (0°)
     * 3 = Virage à droite (90°)
     * 4 = Demi-tour (180°)
     */
    private fun turnPriority(inDx: Int, inDz: Int, outDx: Int, outDz: Int): Int {
        // Dans notre repère : Gauche relative = (inDz, -inDx)
        if (outDx == inDz && outDz == -inDx) return 1
        // Tout droit = (inDx, inDz)
        if (outDx == inDx && outDz == inDz) return 2
        // Droite relative = (-inDz, inDx)
        if (outDx == -inDz && outDz == inDx) return 3
        return 4
    }

    /**
     * Supprime les points intermédiaires alignés sur un même axe X ou Z.
     */
    private fun simplifyCollinear(points: List<GridPoint>): List<GridPoint> {
        val n = points.size
        if (n <= 3) return points

        val simplified = mutableListOf<GridPoint>()
        for (i in 0 until n) {
            val prev = points[(i - 1 + n) % n]
            val curr = points[i]
            val next = points[(i + 1) % n]

            val isCollinearX = prev.x == curr.x && curr.x == next.x
            val isCollinearZ = prev.z == curr.z && curr.z == next.z

            if (!isCollinearX && !isCollinearZ) {
                simplified.add(curr)
            }
        }
        return simplified
    }

    /**
     * Formule de Shoelace pour calculer l'aire signée du polygone 2D.
     */
    private fun calculateSignedArea(points: List<GridPoint>): Long {
        var sum = 0L
        val n = points.size
        for (i in 0 until n) {
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            sum += (p1.x.toLong() * p2.z.toLong() - p2.x.toLong() * p1.z.toLong())
        }
        return sum
    }

    /**
     * Teste si un point est à l'intérieur d'un polygone via ray-casting horizontal.
     */
    private fun isPointInsidePolygon(point: GridPoint, polygon: List<GridPoint>): Boolean {
        var inside = false
        val px = point.x.toDouble() + 0.5 // Décalage au centre pour éviter les ambiguïtés d'arête
        val pz = point.z.toDouble() + 0.5
        val n = polygon.size

        var j = n - 1
        for (i in 0 until n) {
            val xi = polygon[i].x.toDouble()
            val zi = polygon[i].z.toDouble()
            val xj = polygon[j].x.toDouble()
            val zj = polygon[j].z.toDouble()

            if (((zi > pz) != (zj > pz)) && (px < (xj - xi) * (pz - zi) / (zj - zi) + xi)) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Convertit les coordonnées de grille de chunk en coordonnées blocs Minecraft (x16).
     */
    private fun toBlockPosList(points: List<GridPoint>, y: Int): List<BlockPos> {
        return points.map { BlockPos(it.x * 16, y, it.z * 16) }
    }
}
