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
    var root: RTreeNode? = null

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
            root = RTreeLeafNode(mutableListOf(polygon)).apply {
                depth = 0  // root의 depth는 0
            }
        } else {
            val leaf = findLeafNode(root!!, polygon)
            leaf.polygons.add(polygon)
            // 리프 노드의 boundingBox 재계산
            leaf.boundingBox = RTreeLeafNode.computeBoundingBox(leaf.polygons)
            // 부모 노드들의 boundingBox도 재계산
            updateParentBoundingBoxes(leaf)

            // 분할이 필요한지 체크
            if (leaf.polygons.size > nodeCapacity && splitStrategy.needsSplit(leaf)) {
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
        val (left, right) = splitStrategy.split(node, this)

        // 새로 생성된 노드들의 바운딩 박스 업데이트
        when (left) {
            is RTreeLeafNode -> left.boundingBox = RTreeLeafNode.computeBoundingBox(left.polygons)
            is RTreeInternalNode -> left.boundingBox = RTreeInternalNode.computeBoundingBox(left.children)
        }
        when (right) {
            is RTreeLeafNode -> right.boundingBox = RTreeLeafNode.computeBoundingBox(right.polygons)
            is RTreeInternalNode -> right.boundingBox = RTreeInternalNode.computeBoundingBox(right.children)
        }

        if (root == node) {
            root = RTreeInternalNode(mutableListOf(left, right)).apply {
                depth = 0  // root의 depth는 0
                left.parent = this
                right.parent = this
                left.depth = 1  // root의 자식들의 depth는 1
                right.depth = 1
                // root의 바운딩 박스 업데이트
                boundingBox = RTreeInternalNode.computeBoundingBox(children)
            }
        } else {
            val parent = node.parent as RTreeInternalNode
            parent.children.remove(node)
            parent.children.addAll(listOf(left, right))
            left.parent = parent
            right.parent = parent
            left.depth = parent.depth + 1  // 부모의 depth + 1
            right.depth = parent.depth + 1
            // 부모의 바운딩 박스와 그 상위 노드들의 바운딩 박스 업데이트
            updateParentBoundingBoxes(left)

            // 부모 노드의 분할 조건 체크
            if (parent.children.size > nodeCapacity && splitStrategy.needsSplit(parent)) {
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
