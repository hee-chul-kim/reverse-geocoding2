package com.tmapmobility.reversegeocoding2.service.kdtree

import io.github.oshai.kotlinlogging.KotlinLogging
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTree
import com.tmapmobility.reversegeocoding2.service.rtree.khc.split.RStarSplitStrategy
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeNode
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeLeafNode
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeInternalNode

private val logger = KotlinLogging.logger {}

/**
 * KDTree implementation for storing geometries.
 * Uses the minimum x,y coordinates of geometry's envelope for sorting.
 */
class KDTree(geometries: List<Geometry>) {
    private var root: Node? = null
    
    init {
        buildTree(geometries)
    }

    /**
     * Node class representing a point in 2D space
     * @param geometry The geometry stored at this node
     * @param envelope The envelope of the geometry
     * @param depth The depth of the node in the tree (used to determine split axis)
     */
    private class Node(
        val geometry: Geometry,
        val envelope: Envelope,
        val depth: Int,
        var left: Node? = null,
        var right: Node? = null
    ) {
        val discriminator: Double
            get() = if (depth % 2 == 0) envelope.minX else envelope.minY
    }

    private fun buildTree(geometries: List<Geometry>) {
        if (geometries.isEmpty()) return
        
        // Sort geometries by x coordinate initially
        val sortedGeometries = geometries.sortedBy { it.envelopeInternal.minX }
        root = buildTreeRecursive(sortedGeometries, 0)
    }

    private fun buildTreeRecursive(geometries: List<Geometry>, depth: Int): Node? {
        if (geometries.isEmpty()) return null

        // Find median index
        val medianIdx = geometries.size / 2

        // Sort by x or y depending on depth
        val sortedGeometries = if (depth % 2 == 0) {
            geometries.sortedBy { it.envelopeInternal.minX }
        } else {
            geometries.sortedBy { it.envelopeInternal.minY }
        }

        // Create node with median geometry
        val medianGeometry = sortedGeometries[medianIdx]
        val node = Node(medianGeometry, medianGeometry.envelopeInternal, depth)

        // Recursively build left and right subtrees
        node.left = buildTreeRecursive(sortedGeometries.subList(0, medianIdx), depth + 1)
        node.right = buildTreeRecursive(sortedGeometries.subList(medianIdx + 1, sortedGeometries.size), depth + 1)

        return node
    }

    /**
     * Query geometries that intersect with the given envelope
     */
    fun query(searchEnv: Envelope): List<Geometry> {
        val result = mutableListOf<Geometry>()
        query(root, searchEnv, result)
        return result
    }

    private fun query(node: Node?, searchEnv: Envelope, result: MutableList<Geometry>) {
        if (node == null) return

        // If current node's envelope intersects with search envelope, add it to results
        if (node.envelope.intersects(searchEnv)) {
            result.add(node.geometry)
        }

        // Check if we need to search left subtree
        val depth = node.depth
        val discriminator = searchEnv.getDiscriminatorValue(depth)
        
        if (discriminator <= node.discriminator) {
            query(node.left, searchEnv, result)
        }
        
        // Check if we need to search right subtree
        if (discriminator >= node.discriminator) {
            query(node.right, searchEnv, result)
        }
    }

    private fun Envelope.getDiscriminatorValue(depth: Int): Double {
        return if (depth % 2 == 0) minX else minY
    }

    /**
     * Returns the size of the tree (number of geometries stored)
     */
    fun size(): Int {
        return calculateSize(root)
    }

    private fun calculateSize(node: Node?): Int {
        if (node == null) return 0
        return 1 + calculateSize(node.left) + calculateSize(node.right)
    }

    /**
     * Returns the maximum depth of the tree
     */
    fun maxDepth(): Int {
        return calculateMaxDepth(root)
    }

    private fun calculateMaxDepth(node: Node?): Int {
        if (node == null) return 0
        return 1 + maxOf(calculateMaxDepth(node.left), calculateMaxDepth(node.right))
    }

    /**
     * Prints tree statistics for debugging
     */
    fun logTreeStats() {
        logger.info { "KDTree Stats - Size: ${size()}, Max Depth: ${maxDepth()}" }
    }

    /**
     * Collects all geometries in the KD-tree in a list
     */
    private fun collectGeometries(): List<Geometry> {
        val geometries = mutableListOf<Geometry>()
        collectGeometriesRecursive(root, geometries)
        return geometries
    }

    private fun collectGeometriesRecursive(node: Node?, geometries: MutableList<Geometry>) {
        if (node == null) return
        
        geometries.add(node.geometry)
        collectGeometriesRecursive(node.left, geometries)
        collectGeometriesRecursive(node.right, geometries)
    }

    /**
     * Converts this KD-tree to an R-tree while preserving the node structure
     * @return A new RTree containing all geometries with similar structure
     */
    fun toRTree(): RTree {
        val rTree = RTree(4, RStarSplitStrategy())
        if (root == null) return rTree

        // KDTree의 구조를 그대로 RTree로 변환
        rTree.root = convertNodeToRTree(root!!, null, 0)
        return rTree
    }

    private fun convertNodeToRTree(node: Node, parent: RTreeInternalNode?, depth: Int): RTreeInternalNode {
        // 최대 깊이 제한
        val maxViewDepth = 10
        val currentDepth = if (depth > maxViewDepth) maxViewDepth else depth

        // 현재 노드를 RTree 내부 노드로 변환
        val currentNode = RTreeInternalNode(mutableListOf()).apply {
            this.parent = parent
            this.depth = currentDepth
            this.boundingBox = node.envelope
        }

        // 최대 깊이에 도달한 경우 모든 하위 노드를 리프 노드로 변환
        if (depth >= maxViewDepth) {
            val geometries = mutableListOf<Geometry>()
            collectGeometriesFromSubtree(node, geometries)
            
            // 수집된 모든 geometry를 하나의 리프 노드로 변환
            if (geometries.isNotEmpty()) {
                val leafNode = RTreeLeafNode(geometries).apply {
                    this.parent = currentNode
                    this.depth = currentDepth + 1
                    // 모든 geometry의 envelope를 포함하는 바운딩 박스 계산
                    this.boundingBox = Envelope(geometries[0].envelopeInternal).apply {
                        geometries.forEach { expandToInclude(it.envelopeInternal) }
                    }
                }
                currentNode.children.add(leafNode)
            }
            return currentNode
        }

        // 왼쪽 자식이 있는 경우
        node.left?.let { leftNode ->
            if (leftNode.left == null && leftNode.right == null) {
                // 리프 노드인 경우
                val leafNode = RTreeLeafNode(mutableListOf(leftNode.geometry)).apply {
                    this.parent = currentNode
                    this.depth = currentDepth + 1
                    this.boundingBox = leftNode.envelope
                }
                currentNode.children.add(leafNode)
            } else {
                // 내부 노드인 경우
                val leftInternalNode = convertNodeToRTree(leftNode, currentNode, depth + 1)
                currentNode.children.add(leftInternalNode)
            }
        }

        // 오른쪽 자식이 있는 경우
        node.right?.let { rightNode ->
            if (rightNode.left == null && rightNode.right == null) {
                // 리프 노드인 경우
                val leafNode = RTreeLeafNode(mutableListOf(rightNode.geometry)).apply {
                    this.parent = currentNode
                    this.depth = currentDepth + 1
                    this.boundingBox = rightNode.envelope
                }
                currentNode.children.add(leafNode)
            } else {
                // 내부 노드인 경우
                val rightInternalNode = convertNodeToRTree(rightNode, currentNode, depth + 1)
                currentNode.children.add(rightInternalNode)
            }
        }

        // 현재 노드의 geometry도 자식 리프 노드로 추가
        val currentLeafNode = RTreeLeafNode(mutableListOf(node.geometry)).apply {
            this.parent = currentNode
            this.depth = currentDepth + 1
            this.boundingBox = node.envelope
        }
        currentNode.children.add(currentLeafNode)

        // 바운딩 박스 재계산
        currentNode.boundingBox = RTreeInternalNode.computeBoundingBox(currentNode.children)

        return currentNode
    }

    /**
     * 서브트리의 모든 geometry를 수집
     */
    private fun collectGeometriesFromSubtree(node: Node, geometries: MutableList<Geometry>) {
        geometries.add(node.geometry)
        node.left?.let { collectGeometriesFromSubtree(it, geometries) }
        node.right?.let { collectGeometriesFromSubtree(it, geometries) }
    }

    private fun validateTreeStructure(root: RTreeInternalNode, expectedDepth: Int) {
        // 트리 구조 검증을 위한 로깅
        var nodesByLevel = mutableMapOf<Int, Int>()
        var totalNodes = 0
        
        fun validateNode(node: RTreeNode, level: Int) {
            nodesByLevel[level] = (nodesByLevel[level] ?: 0) + 1
            totalNodes++
            
            if (node is RTreeInternalNode) {
                require(node.children.isNotEmpty()) { "Internal node at level $level has no children" }
                node.children.forEach { child ->
                    require(child.parent == node) { "Invalid parent reference at level $level" }
                    require(child.depth == level + 1) { "Invalid depth at level ${level + 1}" }
                    validateNode(child, level + 1)
                }
            }
        }
        
        validateNode(root, 0)
        
        // 결과 로깅
        logger.info { "Tree validation complete:" }
        logger.info { "Total nodes: $totalNodes" }
        nodesByLevel.toSortedMap().forEach { (level, count) ->
            logger.info { "Level $level: $count nodes" }
        }
        logger.info { "Maximum depth: ${nodesByLevel.keys.maxOrNull()}" }
    }
} 