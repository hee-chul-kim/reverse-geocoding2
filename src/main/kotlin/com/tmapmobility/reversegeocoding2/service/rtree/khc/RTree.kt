package com.tmapmobility.reversegeocoding2.service.rtree.khc

import io.github.oshai.kotlinlogging.KotlinLogging
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.index.SpatialIndex
import com.tmapmobility.reversegeocoding2.util.*
import org.locationtech.jts.geom.Geometry
import com.tmapmobility.reversegeocoding2.service.rtree.khc.split.DefaultNodeSplitStrategy
import com.tmapmobility.reversegeocoding2.service.rtree.khc.split.NodeSplitStrategy

val logger = KotlinLogging.logger {}

class RTree(
    private val nodeCapacity: Int = 10,
    private val splitStrategy: NodeSplitStrategy = DefaultNodeSplitStrategy()
) : SpatialIndex {
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
        val (left, right) = splitStrategy.split(node)

        if (root == node) {
            root = RTreeInternalNode(mutableListOf(left, right))
            left.parent = root as RTreeInternalNode
            right.parent = root as RTreeInternalNode
        } else {
            val parent = node.parent as RTreeInternalNode
            parent.children.remove(node)
            parent.children.addAll(listOf(left, right))
            left.parent = parent
            right.parent = parent

            if (parent.children.size > nodeCapacity) {
                splitNode(parent)
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

    override fun query(searchEnv: Envelope?, visitor: ItemVisitor?) {
        TODO("Not yet implemented")
    }

    override fun remove(itemEnv: Envelope?, item: Any?): Boolean {
        TODO("Not yet implemented")
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
}
