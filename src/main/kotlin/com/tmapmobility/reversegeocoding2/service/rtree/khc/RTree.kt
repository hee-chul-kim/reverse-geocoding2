package com.tmapmobility.reversegeocoding2.service.rtree.khc

import io.github.oshai.kotlinlogging.KotlinLogging
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.index.SpatialIndex
import com.tmapmobility.reversegeocoding2.util.*

val logger = KotlinLogging.logger {}

class RTree(private val maxChildren: Int = 10) : SpatialIndex {
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

            if (leaf.polygons.size > maxChildren) {
                splitLeaf(leaf)
            }
        }
    }

    private fun findLeafNode(node: RTreeNode, polygon: MultiPolygon): RTreeLeafNode {
        return when (node) {
            is RTreeLeafNode -> node
            is RTreeInternalNode -> {
                //val bestFit = node.children.minByOrNull { it.boundingBox.minX }
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

    private fun splitLeaf(leaf: RTreeLeafNode) {
        val sortedPolygons = leaf.polygons.sortedBy { it.envelopeInternal.minX }
        val mid = sortedPolygons.size / 2
        val left = RTreeLeafNode(sortedPolygons.subList(0, mid).toMutableList())
        val right = RTreeLeafNode(sortedPolygons.subList(mid, sortedPolygons.size).toMutableList())

        if (root == leaf) {
            root = RTreeInternalNode(mutableListOf(left, right))
        } else {
            val parent = findParent(root!!, leaf) as RTreeInternalNode
            parent.children.remove(leaf)
            parent.children.addAll(listOf(left, right))

            if (parent.children.size > maxChildren) {
                splitInternal(parent)
            }
        }
    }

    private fun splitInternal(node: RTreeInternalNode) {
        val sortedChildren = node.children.sortedBy { it.boundingBox.minX }
        val mid = sortedChildren.size / 2
        val left = RTreeInternalNode(sortedChildren.subList(0, mid).toMutableList())
        val right = RTreeInternalNode(sortedChildren.subList(mid, sortedChildren.size).toMutableList())

        if (root == node) {
            root = RTreeInternalNode(mutableListOf(left, right))
        } else {
            val parent = findParent(root!!, node) as RTreeInternalNode
            parent.children.remove(node)
            parent.children.addAll(listOf(left, right))

            if (parent.children.size > maxChildren) {
                splitInternal(parent)
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
