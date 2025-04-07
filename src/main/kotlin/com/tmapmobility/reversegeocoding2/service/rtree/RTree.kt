package com.tmapmobility.reversegeocoding2.service.rtree

import com.tmapmobility.reversegeocoding2.service.SpatialDataModel
import com.tmapmobility.reversegeocoding2.service.rtree.split.DefaultSplitStrategy
import com.tmapmobility.reversegeocoding2.service.rtree.split.NodeSplitStrategy
import com.tmapmobility.reversegeocoding2.util.computeBoundingBox
import com.tmapmobility.reversegeocoding2.util.plus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry

private val logger = KotlinLogging.logger {}

class RTree(
    private val nodeCapacity: Int = 10,
    private val splitStrategy: NodeSplitStrategy = DefaultSplitStrategy()
) : SpatialDataModel {

    var root: RTreeNode? = null
        private set

    override fun query(searchEnv: Envelope): List<Geometry> {
        val result = mutableListOf<Geometry>()
        root?.let { search(it, searchEnv, result) }
        return result
    }

    private fun search(node: RTreeNode, range: Envelope, result: MutableList<Geometry>) {
        if (!node.envelope.intersects(range)) return

        when (node) {
            is RTreeNode.LeafNode -> {
                result.addAll(node.geometries.filter { it.envelopeInternal.intersects(range) })
            }

            is RTreeNode.InternalNode -> {
                node.children.forEach { search(it, range, result) }
            }
        }
    }

    override fun insert(geometry: Geometry) {
        if (root == null) {
            root = RTreeNode.LeafNode(mutableListOf(geometry)).apply {
                depth = 0  // root의 depth는 0
            }
        } else {
            val leaf = findLeafNode(root!!, geometry)
            leaf.geometries.add(geometry)
            // 리프 노드의 boundingBox 재계산
            leaf.envelope = computeBoundingBox(leaf.geometries)
            // 부모 노드들의 boundingBox도 재계산
            updateParentBoundingBoxes(leaf)

            // 분할이 필요한지 체크
            if (leaf.geometries.size > nodeCapacity && splitStrategy.needsSplit(leaf)) {
                splitNode(leaf)
            }
        }
    }

    private fun findLeafNode(node: RTreeNode, geometry: Geometry): RTreeNode.LeafNode {
        return when (node) {
            is RTreeNode.LeafNode -> node
            is RTreeNode.InternalNode -> {
                val bestFit = bestFit(node.children, geometry)
                findLeafNode(bestFit, geometry)
            }
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
            is RTreeNode.LeafNode -> left.envelope = computeBoundingBox(left.geometries)
            is RTreeNode.InternalNode -> left.envelope = RTreeNode.InternalNode.computeBoundingBox(left.children)
        }
        when (right) {
            is RTreeNode.LeafNode -> right.envelope = computeBoundingBox(right.geometries)
            is RTreeNode.InternalNode -> right.envelope = RTreeNode.InternalNode.computeBoundingBox(right.children)
        }

        if (root == node) {
            root = RTreeNode.InternalNode(mutableListOf(left, right)).apply {
                depth = 0  // root의 depth는 0
                left.parent = this
                right.parent = this
                left.depth = 1  // root의 자식들의 depth는 1
                right.depth = 1
                // root의 바운딩 박스 업데이트
                envelope = RTreeNode.InternalNode.computeBoundingBox(children)
            }
        } else {
            val parent = node.parent as RTreeNode.InternalNode
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
            current.envelope = RTreeNode.InternalNode.computeBoundingBox(current.children)
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
            is RTreeNode.LeafNode -> Pair(depth, 1)
            is RTreeNode.InternalNode -> {
                val childStats = node.children.map { calculateStats(it, depth + 1) }
                val maxChildDepth = childStats.maxOf { it.first }
                val totalNodes = childStats.sumOf { it.second } + 1
                Pair(maxChildDepth, totalNodes)
            }
        }
    }
}
