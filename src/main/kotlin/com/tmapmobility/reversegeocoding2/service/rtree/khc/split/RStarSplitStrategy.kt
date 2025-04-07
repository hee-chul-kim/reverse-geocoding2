package com.tmapmobility.reversegeocoding2.service.rtree.khc.split

import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTree
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeInternalNode
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeLeafNode
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeNode
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import kotlin.math.sqrt

/**
 * R*-tree의 split 전략 구현
 *
 * 특징:
 * 1. 여러 차원에서의 겹침 영역 최소화
 * 2. 둘레 길이 최소화로 더 좋은 MBR 형성
 * 3. 분배 영역의 균형 고려
 * 4. Forced Reinsert로 트리 재구성
 */
class RStarSplitStrategy : NodeSplitStrategy {
    companion object {
        const val SPLIT_FACTOR = 0.4  // 분할 비율 (보통 40:60)
        const val REINSERT_PERCENTAGE = 0.3  // 재삽입할 엔트리의 비율 (30%)
        const val MAX_REINSERT_LEVEL = 3  // 재삽입을 시도할 최대 레벨 (루트에 가까운 레벨만)
    }

    private var reinsertLevels = mutableSetOf<Int>()  // 이미 재삽입을 시도한 레벨들

    override fun split(node: RTreeNode, tree: RTree): Pair<RTreeNode, RTreeNode> {
        // 재삽입 조건 확인: 현재 레벨에서 아직 재삽입을 시도하지 않았고, 레벨이 MAX_REINSERT_LEVEL 이하인 경우
        if (node.depth <= MAX_REINSERT_LEVEL && !reinsertLevels.contains(node.depth)) {
            reinsertLevels.add(node.depth)
            return tryReinsert(node, tree)
        }

        val entries = when (node) {
            is RTreeLeafNode -> node.geometries.map { Entry(it.envelopeInternal, it) }
            is RTreeInternalNode -> node.children.map { Entry(it.boundingBox, it) }
            else -> throw IllegalArgumentException("Unknown node type")
        }

        // 최적의 분할 축과 인덱스 찾기
        val (axis, splitIndex) = findBestSplit(entries)

        // 정렬된 엔트리들을 분할
        val sortedEntries = if (axis == 0) {
            entries.sortedBy { it.envelope.centre().x }
        } else {
            entries.sortedBy { it.envelope.centre().y }
        }

        // 분할된 그룹 생성
        val group1 = sortedEntries.take(splitIndex)
        val group2 = sortedEntries.drop(splitIndex)

        // 새로운 노드 생성
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

    private fun tryReinsert(node: RTreeNode, tree: RTree): Pair<RTreeNode, RTreeNode> {
        val entries = when (node) {
            is RTreeLeafNode -> node.geometries.map { Entry(it.envelopeInternal, it) }
            is RTreeInternalNode -> node.children.map { Entry(it.boundingBox, it) }
            else -> throw IllegalArgumentException("Unknown node type")
        }

        // 노드의 중심점 계산
        val center = calculateNodeCenter(node.boundingBox)

        // 엔트리들을 중심점으로부터의 거리에 따라 정렬
        val sortedEntries = entries.sortedBy { entry ->
            val entryCenter = Point2D(entry.envelope.centre().x, entry.envelope.centre().y)
            distanceBetweenPoints(center, entryCenter)
        }

        // 재삽입할 엔트리 선택 (가장 멀리 있는 30%)
        val reinsertCount = (entries.size * REINSERT_PERCENTAGE).toInt()
        val entriesToReinsert = sortedEntries.takeLast(reinsertCount)
        val entriesToKeep = sortedEntries.dropLast(reinsertCount)

        // 재삽입할 엔트리들 제거 및 새로운 노드 생성
        return when (node) {
            is RTreeLeafNode -> {
                val newNode = RTreeLeafNode(entriesToKeep.map { it.item as Geometry }.toMutableList()).apply {
                    depth = node.depth
                    parent = node.parent
                }

                // 재삽입할 엔트리들을 트리에 다시 삽입
                entriesToReinsert.forEach { entry ->
                    tree.insert(entry.envelope, entry.item as Geometry)
                }

                // split이 필요 없으므로 같은 노드를 반환
                Pair(newNode, newNode)
            }

            is RTreeInternalNode -> {
                val newNode = RTreeInternalNode(entriesToKeep.map { it.item as RTreeNode }.toMutableList()).apply {
                    depth = node.depth
                    parent = node.parent
                }

                // 재삽입할 엔트리들을 트리에 다시 삽입
                entriesToReinsert.forEach { entry ->
                    tree.insert(entry.envelope, entry.item as RTreeNode)
                }

                // split이 필요 없으므로 같은 노드를 반환
                Pair(newNode, newNode)
            }

            else -> throw IllegalArgumentException("Unknown node type")
        }
    }

    private fun findBestChild(children: List<RTreeNode>, envelope: Envelope): RTreeNode {
        return children.minByOrNull { child ->
            val enlargedBox = Envelope(child.boundingBox)
            enlargedBox.expandToInclude(envelope)
            calculateEnlargement(child.boundingBox, enlargedBox)
        } ?: throw IllegalStateException("No children available")
    }

    private fun calculateEnlargement(original: Envelope, enlarged: Envelope): Double {
        val originalArea = (original.maxX - original.minX) * (original.maxY - original.minY)
        val enlargedArea = (enlarged.maxX - enlarged.minX) * (enlarged.maxY - enlarged.minY)
        return enlargedArea - originalArea
    }

    private fun calculateNodeCenter(envelope: Envelope): Point2D {
        return Point2D(
            (envelope.minX + envelope.maxX) / 2,
            (envelope.minY + envelope.maxY) / 2
        )
    }

    private fun distanceBetweenPoints(p1: Point2D, p2: Point2D): Double {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }

    private data class Entry(val envelope: Envelope, val item: Any) {
        val centre
            get() = Point2D(
                (envelope.minX + envelope.maxX) / 2,
                (envelope.minY + envelope.maxY) / 2
            )
    }

    private data class Point2D(val x: Double, val y: Double)

    private fun findBestSplit(entries: List<Entry>): Pair<Int, Int> {
        var bestAxis = 0
        var bestIndex = 0
        var minMetric = Double.MAX_VALUE

        // x축과 y축에 대해 각각 평가
        for (axis in 0..1) {
            val sortedEntries = if (axis == 0) {
                entries.sortedBy { it.envelope.centre().x }
            } else {
                entries.sortedBy { it.envelope.centre().y }
            }

            // 가능한 분할 지점들에 대해 평가
            val minSize = (entries.size * SPLIT_FACTOR).toInt()
            for (k in minSize..entries.size - minSize) {
                val group1 = sortedEntries.take(k)
                val group2 = sortedEntries.drop(k)

                val metric = calculateMetric(group1, group2)
                if (metric < minMetric) {
                    minMetric = metric
                    bestAxis = axis
                    bestIndex = k
                }
            }
        }

        return Pair(bestAxis, bestIndex)
    }

    private fun calculateMetric(group1: List<Entry>, group2: List<Entry>): Double {
        val mbr1 = calculateMBR(group1)
        val mbr2 = calculateMBR(group2)

        // 겹침 영역
        val overlap = calculateOverlap(mbr1, mbr2)

        // 둘레 길이
        val perimeter1 = calculatePerimeter(mbr1)
        val perimeter2 = calculatePerimeter(mbr2)

        // 면적
        val area1 = calculateArea(mbr1)
        val area2 = calculateArea(mbr2)

        // 메트릭 = 겹침 영역 + 둘레 길이 + 면적 차이
        return overlap + (perimeter1 + perimeter2) + Math.abs(area1 - area2)
    }

    private fun calculateMBR(entries: List<Entry>): Envelope {
        val mbr = Envelope()
        entries.forEach { entry ->
            mbr.expandToInclude(entry.envelope)
        }
        return mbr
    }

    /**
     * 두 바운딩 박스 간의 겹침 영역 계산
     *
     * @param box1 첫 번째 바운딩 박스
     * @param box2 두 번째 바운딩 박스
     * @return 겹침 영역의 면적 (겹치지 않는 경우 0.0)
     */
    private fun calculateOverlap(box1: Envelope, box2: Envelope): Double {
        val overlapWidth = minOf(box1.maxX, box2.maxX) - maxOf(box1.minX, box2.minX)
        val overlapHeight = minOf(box1.maxY, box2.maxY) - maxOf(box1.minY, box2.minY)
        return if (overlapWidth > 0 && overlapHeight > 0) {
            overlapWidth * overlapHeight
        } else {
            0.0
        }
    }

    private fun calculatePerimeter(mbr: Envelope): Double {
        return 2 * ((mbr.maxX - mbr.minX) + (mbr.maxY - mbr.minY))
    }

    private fun calculateArea(mbr: Envelope): Double {
        return (mbr.maxX - mbr.minX) * (mbr.maxY - mbr.minY)
    }

    /**
     * 리스트의 모든 가능한 2개 조합을 생성하는 확장 함수
     */
    private fun <T> List<T>.combinations(): List<Pair<T, T>> {
        val result = mutableListOf<Pair<T, T>>()
        for (i in 0 until size - 1) {
            for (j in i + 1 until size) {
                result.add(Pair(this[i], this[j]))
            }
        }
        return result
    }

    /**
     * R* 트리의 분할 조건을 검사
     * 둘레 길이와 오버랩을 기준으로 분할 여부를 결정
     */
    override fun needsCustomSplit(node: RTreeNode): Boolean {
        val box = node.boundingBox
        val perimeter = 2 * (box.width + box.height)
        val maxPerimeter = calculateMaxPerimeter(node.depth)

        // 둘레 길이가 임계값을 초과하는 경우
        if (perimeter > maxPerimeter) {
            return true
        }

        // 오버랩 검사
        when (node) {
            is RTreeLeafNode -> {
                if (node.geometries.size < 2) return false
                val boxes = node.geometries.map { it.envelopeInternal }
                val totalOverlap = boxes.combinations().sumOf { (box1, box2) -> calculateOverlap(box1, box2) }
                val avgOverlap = totalOverlap / (boxes.size * (boxes.size - 1) / 2.0)
                return avgOverlap > calculateMaxOverlap(node.depth)
            }

            is RTreeInternalNode -> {
                if (node.children.size < 2) return false
                val boxes = node.children.map { it.boundingBox }
                val totalOverlap = boxes.combinations().sumOf { (box1, box2) -> calculateOverlap(box1, box2) }
                val avgOverlap = totalOverlap / (boxes.size * (boxes.size - 1) / 2.0)
                return avgOverlap > calculateMaxOverlap(node.depth)
            }

            else -> return false
        }
    }

    private fun calculateMaxPerimeter(depth: Int): Double {
        // depth가 깊어질수록 허용되는 최대 둘레 길이가 감소
        return 1000.0 * Math.pow(0.7, depth.toDouble())
    }

    private fun calculateMaxOverlap(depth: Int): Double {
        // depth가 깊어질수록 허용되는 최대 오버랩이 감소
        return 0.3 * Math.pow(0.8, depth.toDouble())
    }
} 