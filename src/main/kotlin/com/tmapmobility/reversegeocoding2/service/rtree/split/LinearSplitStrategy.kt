package com.tmapmobility.reversegeocoding2.service.rtree.split

import com.tmapmobility.reversegeocoding2.service.rtree.RTree
import com.tmapmobility.reversegeocoding2.service.rtree.RTreeInternalNode
import com.tmapmobility.reversegeocoding2.service.rtree.RTreeLeafNode
import com.tmapmobility.reversegeocoding2.service.rtree.RTreeNode
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry

/**
 * Linear Split 전략 구현
 *
 * 이 전략은 Quadratic Split보다 단순하면서도 효율적인 분할을 제공:
 * 1. 각 차원(x, y)에서 가장 멀리 떨어진 두 개의 MBR을 찾음
 * 2. 분산이 가장 큰 차원을 선택
 * 3. 선택된 차원에서 가장 멀리 떨어진 두 MBR을 시드로 선택
 * 4. 나머지 요소들을 시드와의 거리에 따라 두 그룹으로 분할
 *
 * 시간 복잡도: O(n)
 */
class LinearSplitStrategy : NodeSplitStrategy {
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

        // 각 차원에서 가장 멀리 떨어진 MBR 쌍 찾기
        val xExtremes = findExtremes(boundingBoxes, true)
        val yExtremes = findExtremes(boundingBoxes, false)

        // 각 차원의 분산 계산
        val xVariance = calculateVariance(boundingBoxes, true)
        val yVariance = calculateVariance(boundingBoxes, false)

        // 분산이 가장 큰 차원 선택
        val (seed1, seed2) = if (xVariance > yVariance) xExtremes else yExtremes

        // 두 그룹으로 분할
        val group1 = mutableListOf<Any>()
        val group2 = mutableListOf<Any>()

        // 시드에 해당하는 요소들을 각 그룹에 추가
        val seed1Index = children.indexOfFirst {
            when (it) {
                is Geometry -> it.envelopeInternal == seed1
                is RTreeNode -> it.envelope == seed1
                else -> false
            }
        }
        val seed2Index = children.indexOfFirst {
            when (it) {
                is Geometry -> it.envelopeInternal == seed2
                is RTreeNode -> it.envelope == seed2
                else -> false
            }
        }

        group1.add(children[seed1Index])
        group2.add(children[seed2Index])

        // 나머지 요소들을 시드와의 거리에 따라 분할
        for (i in children.indices) {
            if (i == seed1Index || i == seed2Index) continue

            val child = children[i]
            val box = when (child) {
                is Geometry -> child.envelopeInternal
                is RTreeNode -> child.envelope
                else -> throw IllegalArgumentException("Invalid child type")
            }

            val distance1 = getDistanceFromSeed(box, seed1)
            val distance2 = getDistanceFromSeed(box, seed2)

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
     * 주어진 차원에서 가장 멀리 떨어진 두 개의 MBR을 찾음
     *
     * @param boundingBoxes MBR 리스트
     * @param isXDimension x차원 여부 (true: x차원, false: y차원)
     * @return 가장 멀리 떨어진 두 개의 MBR 쌍
     */
    private fun findExtremes(boundingBoxes: List<Envelope>, isXDimension: Boolean): Pair<Envelope, Envelope> {
        var min = Double.MAX_VALUE
        var max = Double.MIN_VALUE
        var minBox: Envelope? = null
        var maxBox: Envelope? = null

        boundingBoxes.forEach { box ->
            val value = if (isXDimension) (box.minX + box.maxX) / 2 else (box.minY + box.maxY) / 2
            if (value < min) {
                min = value
                minBox = box
            }
            if (value > max) {
                max = value
                maxBox = box
            }
        }

        return Pair(minBox!!, maxBox!!)
    }

    /**
     * 주어진 차원의 분산을 계산
     *
     * @param boundingBoxes MBR 리스트
     * @param isXDimension x차원 여부 (true: x차원, false: y차원)
     * @return 분산 값
     */
    private fun calculateVariance(boundingBoxes: List<Envelope>, isXDimension: Boolean): Double {
        val values = boundingBoxes.map {
            if (isXDimension) (it.minX + it.maxX) / 2 else (it.minY + it.maxY) / 2
        }
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }

    /**
     * MBR과 시드 MBR 사이의 거리를 계산
     *
     * @param box MBR
     * @param seed 시드 MBR
     * @return 두 MBR의 중심점 사이 거리
     */
    private fun getDistanceFromSeed(box: Envelope, seed: Envelope): Double {
        val boxCenterX = (box.minX + box.maxX) / 2
        val boxCenterY = (box.minY + box.maxY) / 2
        val seedCenterX = (seed.minX + seed.maxX) / 2
        val seedCenterY = (seed.minY + seed.maxY) / 2

        return Math.sqrt(
            (boxCenterX - seedCenterX) * (boxCenterX - seedCenterX) +
                    (boxCenterY - seedCenterY) * (boxCenterY - seedCenterY)
        )
    }
} 