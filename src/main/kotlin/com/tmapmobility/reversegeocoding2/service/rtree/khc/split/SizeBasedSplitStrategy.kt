package com.tmapmobility.reversegeocoding2.service.rtree.khc.split

import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTree
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeNode
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeLeafNode
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeInternalNode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

val logger = KotlinLogging.logger {}

/**
 * 바운딩 박스의 축 길이를 기반으로 하는 분할 전략
 * 
 * 특징:
 * 1. x축과 y축의 길이를 개별적으로 평가
 * 2. 노드의 depth에 따라 다른 임계값 적용
 * 3. 긴 축을 기준으로 분할하여 효율적인 공간 분할
 * 4. 부모 노드들도 필요한 경우 연쇄적으로 분할
 */
class SizeBasedSplitStrategy : NodeSplitStrategy {
    companion object {
        const val BASE_THRESHOLD = 0.3  // 기본 임계값
        const val DEPTH_FACTOR = 0.75     // depth가 증가할 때마다 임계값이 줄어드는 비율
        const val MIN_THRESHOLD = 0.02     // 최소 임계값
        const val MIN_ENTRIES_PER_NODE = 4 // 노드당 최소 엔트리 수
    }

    override fun split(node: RTreeNode, tree: RTree): Pair<RTreeNode, RTreeNode> {
        // 기본 분할 조건과 전략별 분할 조건을 모두 만족하는 경우에만 분할 수행
        if (!needsSplit(node)) {
            // 기본 분할 수행 (중간점 기준)
            return splitByMiddle(node)
        }
        return splitByAxis(node)
    }

    override fun needsCustomSplit(node: RTreeNode): Boolean {
        val threshold = calculateThresholdForDepth(node.depth)
        val box = node.boundingBox
        val b = box.width > threshold || box.height > threshold
        if (b) logger.info { "Split required for node at depth ${node.depth} with bounding box $box" }
        return b
    }

    private fun splitByMiddle(node: RTreeNode): Pair<RTreeNode, RTreeNode> {
        val entries = getEntries(node)
        val splitIndex = entries.size / 2
        val group1 = entries.take(splitIndex)
        val group2 = entries.drop(splitIndex)
        return createNodes(node, group1, group2)
    }

    private fun splitByAxis(node: RTreeNode): Pair<RTreeNode, RTreeNode> {
        val entries = getEntries(node)
        val mbr = calculateMBR(entries)
        val isWide = mbr.width > mbr.height

        // 더 긴 축을 기준으로 정렬
        val sortedEntries = if (isWide) {
            entries.sortedBy { it.envelope.centre().x }
        } else {
            entries.sortedBy { it.envelope.centre().y }
        }

        // 분할 지점 찾기 (축 길이가 가장 비슷해지는 지점)
        val splitIndex = findBestSplitIndex(sortedEntries, isWide)
        val group1 = sortedEntries.take(splitIndex)
        val group2 = sortedEntries.drop(splitIndex)

        return createNodes(node, group1, group2)
    }

    private fun getEntries(node: RTreeNode): List<Entry> {
        return when (node) {
            is RTreeLeafNode -> node.polygons.map { Entry(it.envelopeInternal, it) }
            is RTreeInternalNode -> node.children.map { Entry(it.boundingBox, it) }
            else -> throw IllegalArgumentException("Unknown node type")
        }
    }

    private fun calculateThresholdForDepth(depth: Int): Double {
        // depth가 증가할수록 임계값이 지수적으로 감소
        val threshold = BASE_THRESHOLD * DEPTH_FACTOR.pow(depth)
        return max(threshold, MIN_THRESHOLD)
    }

    private fun findBestSplitIndex(entries: List<Entry>, isWide: Boolean): Int {
        var bestIndex = entries.size / 2
        var minAxisDiff = Double.MAX_VALUE

        // MIN_ENTRIES_PER_NODE부터 시작하여 가능한 모든 분할점 검사
        for (i in MIN_ENTRIES_PER_NODE until entries.size - MIN_ENTRIES_PER_NODE) {
            val group1 = entries.take(i)
            val group2 = entries.drop(i)
            
            val mbr1 = calculateMBR(group1)
            val mbr2 = calculateMBR(group2)
            
            // 분할 후 두 그룹의 해당 축 길이 차이 계산
            val axisDiff = if (isWide) {
                kotlin.math.abs(mbr1.width - mbr2.width)
            } else {
                kotlin.math.abs(mbr1.height - mbr2.height)
            }

            if (axisDiff < minAxisDiff) {
                minAxisDiff = axisDiff
                bestIndex = i
            }
        }

        return bestIndex
    }

    private fun createNodes(node: RTreeNode, group1: List<Entry>, group2: List<Entry>): Pair<RTreeNode, RTreeNode> {
        return when (node) {
            is RTreeLeafNode -> {
                val left = RTreeLeafNode(group1.map { it.item as Geometry }.toMutableList()).apply {
                    depth = node.depth
                    parent = node.parent
                }
                val right = RTreeLeafNode(group2.map { it.item as Geometry }.toMutableList()).apply {
                    depth = node.depth
                    parent = node.parent
                }
                Pair(left, right)
            }
            is RTreeInternalNode -> {
                val left = RTreeInternalNode(group1.map { it.item as RTreeNode }.toMutableList()).apply {
                    depth = node.depth
                    parent = node.parent
                }
                val right = RTreeInternalNode(group2.map { it.item as RTreeNode }.toMutableList()).apply {
                    depth = node.depth
                    parent = node.parent
                }
                Pair(left, right)
            }
            else -> throw IllegalArgumentException("Unknown node type")
        }
    }

    private data class Entry(val envelope: Envelope, val item: Any) {
        val centre get() = Point2D(
            (envelope.minX + envelope.maxX) / 2,
            (envelope.minY + envelope.maxY) / 2
        )
    }

    private data class Point2D(val x: Double, val y: Double)

    private fun calculateMBR(entries: List<Entry>): Envelope {
        if (entries.isEmpty()) return Envelope()
        val mbr = Envelope(entries.first().envelope)
        entries.forEach { entry ->
            mbr.expandToInclude(entry.envelope)
        }
        return mbr
    }
} 