package com.tmapmobility.reversegeocoding2.service.rtree.khc

import io.github.oshai.kotlinlogging.KotlinLogging
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.index.SpatialIndex
import com.tmapmobility.reversegeocoding2.util.*
import org.locationtech.jts.geom.Geometry

val logger = KotlinLogging.logger {}

class RTree(private val nodeCapacity: Int = 10) : SpatialIndex {
    private var root: RTreeNode? = null

    override fun query(range: Envelope): MutableList<Any?> {
        val result = mutableListOf<Any?>()
        root?.let { search(it, range, result) }
        return result
    }

    private fun search(node: RTreeNode, range: Envelope, result: MutableList<Any?>) {
        if (!node.boundingBox.intersects(range)) return

        when (node) {
            is RTreeLeafNode -> {
                result.addAll(node.polygons.filter { it.envelopeInternal.intersects(range) })
            }
            is RTreeInternalNode -> {
                node.children.forEach { search(it, range, result) }
            }
        }
    }

    override fun insert(itemEnv: Envelope?, item: Any?) {
        val polygon: MultiPolygon = item as MultiPolygon

        if (root == null) {
            root = RTreeLeafNode(mutableListOf(polygon))
        } else {
            val leaf = findLeafNode(root!!, polygon)
            leaf.polygons.add(polygon)
            // 리프 노드의 boundingBox 재계산
            leaf.boundingBox = RTreeLeafNode.computeBoundingBox(leaf.polygons)
            // 부모 노드들의 boundingBox도 재계산
            updateParentBoundingBoxes(leaf)

            if (leaf.polygons.size > nodeCapacity) {
                splitNode(leaf)
            }
        }
    }

    private fun findLeafNode(node: RTreeNode, polygon: MultiPolygon): RTreeLeafNode {
        return when (node) {
            is RTreeLeafNode -> node
            is RTreeInternalNode -> {
                val bestFit = bestFit(node.children, polygon)
                findLeafNode(bestFit, polygon)
            }
            else -> throw IllegalStateException("Unknown node type")
        }
    }

    private fun bestFit(children: MutableList<RTreeNode>, polygon: MultiPolygon): RTreeNode {
        // 폴리곤 추가했을 때 가장 작은 박스가 되는 노드
        var minBoxNode: RTreeNode = children.first()
        var minBox: Envelope = minBoxNode.boundingBox
        for (child in children) {
            if ((child.boundingBox + polygon.envelopeInternal).area < minBox.area) {
                minBoxNode = child
                minBox = child.boundingBox
            }
        }
        return minBoxNode
    }

    private fun splitNode(node: RTreeNode) {
        val children = when (node) {
            is RTreeLeafNode -> node.polygons.toMutableList()
            is RTreeInternalNode -> node.children.toMutableList()
            else -> throw IllegalArgumentException("Unknown node type")
        }

        val boundingBoxes = children.map {
            when (it) {
                is Geometry -> it.envelopeInternal
                is RTreeNode -> it.boundingBox
                else -> throw IllegalArgumentException("Invalid child type")
            }
        }

        val (box1, box2) = findFarthestPair(boundingBoxes)

        val group1 = mutableListOf<Any>()
        val group2 = mutableListOf<Any>()

        children.forEach { child ->
            val box = when (child) {
                is Geometry -> child.envelopeInternal
                is RTreeNode -> child.boundingBox
                else -> throw IllegalArgumentException("Invalid child type")
            }

            val distance1 = powerOfDistanceBetweenBoundingBox(box, box1)
            val distance2 = powerOfDistanceBetweenBoundingBox(box, box2)

            if (distance1 < distance2) {
                group1.add(child)
            } else {
                group2.add(child)
            }
        }

        val left = if (node is RTreeLeafNode) {
            RTreeLeafNode(group1.map { it as Geometry }.toMutableList()).apply {
                depth = node.depth
                parent = node.parent
            }
        } else {
            RTreeInternalNode(group1.map { it as RTreeNode }.toMutableList()).apply {
                depth = node.depth
                parent = node.parent
            }
        }

        val right = if (node is RTreeLeafNode) {
            RTreeLeafNode(group2.map { it as Geometry }.toMutableList()).apply {
                depth = node.depth
                parent = node.parent
            }
        } else {
            RTreeInternalNode(group2.map { it as RTreeNode }.toMutableList()).apply {
                depth = node.depth
                parent = node.parent
            }
        }

        if (root == node) {
            root = RTreeInternalNode(mutableListOf(left, right))
        } else {
            val parent = findParent(root!!, node) as RTreeInternalNode
            parent.children.remove(node)
            parent.children.addAll(listOf(left, right))

            if (parent.children.size > nodeCapacity) {
                splitNode(parent)
            }
        }
    }

    private fun findParent(node: RTreeNode, child: RTreeNode): RTreeNode? {
        if (node is RTreeInternalNode) {
            if (node.children.contains(child)) return node
            return node.children.mapNotNull { findParent(it, child) }.firstOrNull()
        }
        return null
    }

    override fun query(searchEnv: Envelope?, visitor: ItemVisitor?) {
        TODO("Not yet implemented")
    }

    override fun remove(itemEnv: Envelope?, item: Any?): Boolean {
        TODO("Not yet implemented")
    }

    fun findFarthestPair(boundingBoxes: List<Envelope>): Pair<Envelope, Envelope> {
        var maxDistance = -1.0
        var pair: Pair<Envelope, Envelope>? = null

        for (i in boundingBoxes.indices) {
            for (j in i + 1 until boundingBoxes.size) {
                val box1 = boundingBoxes[i]
                val box2 = boundingBoxes[j]

                val distance = powerOfDistanceBetweenBoundingBox(box1, box2)

                if (distance > maxDistance) {
                    maxDistance = distance
                    pair = Pair(box1, box2)
                }
            }
        }

        return pair ?: throw IllegalStateException("Bounding box list must have at least two elements")
    }

    fun powerOfDistanceBetweenBoundingBox(box1: Envelope, box2: Envelope): Double {
        val center1X = (box1.minX + box1.maxX) / 2
        val center1Y = (box1.minY + box1.maxY) / 2
        val center2X = (box2.minX + box2.maxX) / 2
        val center2Y = (box2.minY + box2.maxY) / 2

        // 비교에는 제곱근 필요 없어서 제곱으로 계산
        val distance = (
                (center2X - center1X) * (center2X - center1X) +
                        (center2Y - center1Y) * (center2Y - center1Y)
                )

        return distance
    }

    fun logTreeStats() {
        val (maxDepth, nodeCount) = calculateStats(root, 0)
        logger.info { "RTree Stats - Max Depth: $maxDepth, Node Count: $nodeCount" }
    }

    private fun calculateStats(node: RTreeNode?, depth: Int): Pair<Int, Int> {
        if (node == null) return Pair(depth, 0)

        return when (node) {
            is RTreeLeafNode -> Pair(depth, 1)
            is RTreeInternalNode -> {
                val childStats = node.children.map { calculateStats(it, depth + 1) }
                val maxChildDepth = childStats.maxOf { it.first }
                val totalNodes = childStats.sumOf { it.second } + 1
                Pair(maxChildDepth, totalNodes)
            }

            else -> {
                throw IllegalStateException("Unknown node type")
            }
        }
    }

    private fun updateParentBoundingBoxes(node: RTreeNode) {
        var current = node.parent
        while (current != null) {
            current.boundingBox = RTreeInternalNode.computeBoundingBox(current.children)
            current = current.parent
        }
    }
}
