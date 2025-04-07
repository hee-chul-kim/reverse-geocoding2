package com.tmapmobility.reversegeocoding2.service.rtree.split

import com.tmapmobility.reversegeocoding2.service.rtree.RTree
import com.tmapmobility.reversegeocoding2.service.rtree.RTreeInternalNode
import com.tmapmobility.reversegeocoding2.service.rtree.RTreeLeafNode
import com.tmapmobility.reversegeocoding2.service.rtree.RTreeNode
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry

/**
 * 기본 노드 분할 전략 구현
 *
 * 현재 구현은 다음과 같은 방식으로 노드를 분할:
 * 1. 노드의 모든 자식 요소들의 MBR(Minimum Bounding Rectangle)을 계산
 * 2. 가장 멀리 떨어진 두 개의 MBR을 선택
 * 3. 각 자식 요소를 선택된 두 MBR 중 더 가까운 쪽에 할당
 *
 * 이 전략은 단순하지만 최적의 분할을 보장하지는 않음
 * 향후 Quadratic Split이나 Linear Split과 같은 더 효율적인 전략으로 대체 가능
 */
class DefaultSplitStrategy : NodeSplitStrategy {
    override fun split(node: RTreeNode, tree: RTree): Pair<RTreeNode, RTreeNode> {
        // 노드 타입에 따라 자식 요소 추출
        val children = when (node) {
            is RTreeLeafNode -> node.geometries.toMutableList()
            is RTreeInternalNode -> node.children.toMutableList()
            else -> throw IllegalArgumentException("Unknown node type")
        }

        // 각 자식 요소의 MBR 계산
        val boundingBoxes = children.map {
            when (it) {
                is Geometry -> it.envelopeInternal
                is RTreeNode -> it.envelope
                else -> throw IllegalArgumentException("Invalid child type")
            }
        }

        // 가장 멀리 떨어진 두 개의 MBR 선택
        val (box1, box2) = findFarthestPair(boundingBoxes)

        // 두 그룹으로 분할
        val group1 = mutableListOf<Any>()
        val group2 = mutableListOf<Any>()

        // 각 자식 요소를 선택된 MBR 중 더 가까운 쪽에 할당
        children.forEach { child ->
            val box = when (child) {
                is Geometry -> child.envelopeInternal
                is RTreeNode -> child.envelope
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

        // 새로운 노드 생성 (원본 노드의 depth와 parent 정보 유지)
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

        return Pair(left, right)
    }

    /**
     * 주어진 MBR 리스트에서 가장 멀리 떨어진 두 개의 MBR을 찾음
     *
     * @param boundingBoxes MBR 리스트
     * @return 가장 멀리 떨어진 두 개의 MBR 쌍
     */
    private fun findFarthestPair(boundingBoxes: List<Envelope>): Pair<Envelope, Envelope> {
        var maxDistance = -1.0
        var pair: Pair<Envelope, Envelope>? = null

        // 모든 가능한 MBR 쌍에 대해 거리 계산
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

    /**
     * 두 MBR 사이의 거리의 제곱을 계산
     *
     * @param box1 첫 번째 MBR
     * @param box2 두 번째 MBR
     * @return 두 MBR의 중심점 사이 거리의 제곱
     */
    private fun powerOfDistanceBetweenBoundingBox(box1: Envelope, box2: Envelope): Double {
        val center1X = (box1.minX + box1.maxX) / 2
        val center1Y = (box1.minY + box1.maxY) / 2
        val center2X = (box2.minX + box2.maxX) / 2
        val center2Y = (box2.minY + box2.maxY) / 2

        return (center2X - center1X) * (center2X - center1X) +
                (center2Y - center1Y) * (center2Y - center1Y)
    }
} 