package com.tmapmobility.reversegeocoding2.service.rtree

import com.tmapmobility.reversegeocoding2.service.SpatialDataModel
import com.tmapmobility.reversegeocoding2.service.rtree.split.DefaultSplitStrategy
import com.tmapmobility.reversegeocoding2.service.rtree.split.NodeSplitStrategy
import com.tmapmobility.reversegeocoding2.util.plus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry

val logger = KotlinLogging.logger {}

class RTree(
    private val nodeCapacity: Int = 10,
    private val splitStrategy: NodeSplitStrategy = DefaultSplitStrategy()
) : SpatialDataModel {

    var root: RTreeNode? = null
        private set

    override fun query(range: Envelope): List<Geometry> {
        val result = mutableListOf<Geometry>()
        root?.let { search(it, range, result) }
        return result
    }

    private fun search(node: RTreeNode, range: Envelope, result: MutableList<Geometry>) {
        if (!node.envelope.intersects(range)) return

        when (node) {
            is RTreeLeafNode -> {
                result.addAll(node.geometries.filter { it.envelopeInternal.intersects(range) })
            }

            is RTreeInternalNode -> {
                node.children.forEach { search(it, range, result) }
            }
        }
    }

    override fun insert(geometry: Geometry) {
        if (root == null) {
            root = RTreeLeafNode(mutableListOf(geometry)).apply {
                depth = 0  // root의 depth는 0
            }
        } else {
            val leaf = findLeafNode(root!!, geometry)
            leaf.geometries.add(geometry)
            // 리프 노드의 boundingBox 재계산
            leaf.envelope = RTreeLeafNode.computeBoundingBox(leaf.geometries)
            // 부모 노드들의 boundingBox도 재계산
            updateParentBoundingBoxes(leaf)

            // 분할이 필요한지 체크
            if (leaf.geometries.size > nodeCapacity && splitStrategy.needsSplit(leaf)) {
                splitNode(leaf)
            }
        }
    }

    private fun findLeafNode(node: RTreeNode, geometry: Geometry): RTreeLeafNode {
        return when (node) {
            is RTreeLeafNode -> node
            is RTreeInternalNode -> {
                val bestFit = bestFit(node.children, geometry)
                findLeafNode(bestFit, geometry)
            }

            else -> throw IllegalStateException("Unknown node type")
        }
    }

    private fun bestFit(children: MutableList<RTreeNode>, geometry: Geometry): RTreeNode {
        var minBoxNode: RTreeNode = children.first()
        var minBox: Envelope = minBoxNode.envelope
        for (child in children) {
            if ((child.envelope + geometry.envelopeInternal).area < minBox.area) {
                minBoxNode = child
                minBox = child.envelope
            }
        }
        return minBoxNode
    }

    private fun splitNode(node: RTreeNode) {
        val (left, right) = splitStrategy.split(node, this)

        // 새로 생성된 노드들의 바운딩 박스 업데이트
        when (left) {
            is RTreeLeafNode -> left.envelope = RTreeLeafNode.computeBoundingBox(left.geometries)
            is RTreeInternalNode -> left.envelope = RTreeInternalNode.computeBoundingBox(left.children)
        }
        when (right) {
            is RTreeLeafNode -> right.envelope = RTreeLeafNode.computeBoundingBox(right.geometries)
            is RTreeInternalNode -> right.envelope = RTreeInternalNode.computeBoundingBox(right.children)
        }

        if (root == node) {
            root = RTreeInternalNode(mutableListOf(left, right)).apply {
                depth = 0  // root의 depth는 0
                left.parent = this
                right.parent = this
                left.depth = 1  // root의 자식들의 depth는 1
                right.depth = 1
                // root의 바운딩 박스 업데이트
                envelope = RTreeInternalNode.computeBoundingBox(children)
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
            current.envelope = RTreeInternalNode.computeBoundingBox(current.children)
            current = current.parent
        }
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
