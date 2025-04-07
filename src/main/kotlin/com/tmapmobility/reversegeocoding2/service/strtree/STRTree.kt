package com.tmapmobility.reversegeocoding2.service.strtree

import com.tmapmobility.reversegeocoding2.service.SpatialDataModel
import com.tmapmobility.reversegeocoding2.service.rtree.RTreeNode
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import java.util.*
import kotlin.math.ceil

/**
 * STR-Tree (Sort-Tile-Recursive R-tree) 구현
 * 벌크 로딩에 최적화된 R-tree 변형
 * @param nodeCapacity 노드당 최대 항목 수
 */
class KhcSTRtree(
    private val nodeCapacity: Int = DEFAULT_NODE_CAPACITY
) : SpatialDataModel {
    var root: RTreeNode? = null
        private set
    private val items = mutableListOf<Geometry>()
    private var isBuilt = false

    companion object {
        const val DEFAULT_NODE_CAPACITY = 10
    }

    /**
     * 아이템을 트리에 삽입
     * 실제 트리 구성은 build() 호출 시점까지 지연됨
     */
    override fun insert(geometry: Geometry) {
        require(!isBuilt) { "트리가 이미 구축되어 있어 새로운 아이템을 삽입할 수 없습니다." }
        items.add(geometry)
    }

    /**
     * 여러 아이템을 트리에 삽입
     * 실제 트리 구성은 build() 호출 시점까지 지연됨
     */
    fun insert(items: List<Geometry>) {
        require(!isBuilt) { "트리가 이미 구축되어 있어 새로운 아이템을 삽입할 수 없습니다." }
        this.items.addAll(items)
    }

    /**
     * 임시 저장된 아이템들을 사용하여 실제 트리를 구축
     */
    fun build() {
        if (isBuilt) return
        if (items.isEmpty()) return

        root = createLevel(items, 0)
        isBuilt = true
    }

    /**
     * STR 알고리즘을 사용하여 레벨별로 노드를 생성
     */
    private fun createLevel(items: MutableList<Geometry>, depth: Int): RTreeNode {
        // 아이템이 노드 용량 이하면 바로 리프 노드 생성
        if (items.size <= nodeCapacity) {
            val envelope = computeEnvelope(items.map { it.envelopeInternal })
            return RTreeNode.LeafNode(envelope, depth, items)
        }

        // 1. X축으로 정렬
        val sortedByX = items.sortedBy { it.envelopeInternal.minX }

        // 2. X축 슬라이스 수 계산 (M = ceil(N/n), N: 전체 아이템 수, n: 노드 용량)
        val sliceCount = ceil(Math.sqrt(items.size.toDouble() / nodeCapacity)).toInt()
        val itemsPerSlice = ceil(items.size.toDouble() / sliceCount).toInt()

        // 3. X축 슬라이스로 분할하고 각 슬라이스 내에서 Y축으로 정렬
        val slices = sortedByX.chunked(itemsPerSlice)
            .map { slice -> slice.sortedBy { it.envelopeInternal.minY } }

        // 4. 각 슬라이스에서 노드 크기만큼 그룹화
        val nodes = mutableListOf<RTreeNode.LeafNode>()

        for (slice in slices) {
            val groups = slice.chunked(nodeCapacity)
            for (group in groups) {
                val envelope = computeEnvelope(group.map { it.envelopeInternal })
                nodes.add(RTreeNode.LeafNode(envelope, depth, group.toMutableList()))
            }
        }

        // 노드가 하나면 바로 반환
        if (nodes.size == 1) {
            return nodes.first()
        }

        // 노드가 여러 개면 재귀적으로 상위 레벨 생성
        return createInternalLevel(nodes, depth + 1)
    }

    /**
     * 내부 노드 레벨 생성
     */
    private fun createInternalLevel(nodes: List<RTreeNode>, level: Int): RTreeNode {
        if (nodes.size <= nodeCapacity) {
            val envelope = computeEnvelope(nodes.map { it.envelope })
            return RTreeNode.InternalNode(envelope, level, nodes.toMutableList())
        }

        // 노드들을 X축으로 정렬
        val sortedByX = nodes.sortedBy { it.envelope.minX }

        // X축 슬라이스 수 계산
        val sliceCount = ceil(Math.sqrt(nodes.size.toDouble() / nodeCapacity)).toInt()
        val nodesPerSlice = ceil(nodes.size.toDouble() / sliceCount).toInt()

        // X축 슬라이스로 분할하고 각 슬라이스 내에서 Y축으로 정렬
        val slices = sortedByX.chunked(nodesPerSlice)
            .map { slice -> slice.sortedBy { it.envelope.minY } }

        // 각 슬라이스에서 노드 크기만큼 그룹화하여 상위 노드 생성
        val parentNodes = mutableListOf<RTreeNode>()

        for (slice in slices) {
            val groups = slice.chunked(nodeCapacity)
            for (group in groups) {
                val envelope = computeEnvelope(group.map { it.envelope })
                parentNodes.add(RTreeNode.InternalNode(envelope, level, group.toMutableList()))
            }
        }

        // 노드가 하나면 바로 반환
        if (parentNodes.size == 1) {
            return parentNodes.first()
        }

        // 노드가 여러 개면 재귀적으로 상위 레벨 생성
        return createInternalLevel(parentNodes, level + 1)
    }

    /**
     * 여러 Envelope의 합집합 계산
     */
    private fun computeEnvelope(envelopes: List<Envelope>): Envelope {
        if (envelopes.isEmpty()) throw IllegalArgumentException("Envelopes list cannot be empty")

        val result = Envelope(envelopes[0])
        for (i in 1 until envelopes.size) {
            result.expandToInclude(envelopes[i])
        }
        return result
    }

    /**
     * 주어진 영역과 겹치는 모든 아이템을 검색
     */
    override fun query(searchEnv: Envelope): List<Geometry> {
        if (!isBuilt) build()

        val result = mutableListOf<Geometry>()
        root?.let { queryInternal(it, searchEnv, result) }
        return result
    }

    private fun queryInternal(node: RTreeNode, searchEnv: Envelope, result: MutableList<Geometry>) {
        // 현재 노드의 MBR이 검색 영역과 겹치지 않으면 종료
        if (!node.envelope.intersects(searchEnv)) return

        when (node) {
            is RTreeNode.LeafNode -> {
                // 리프 노드의 경우 각 아이템을 검사
                for (item in node.geometries) {
                    if (item.envelopeInternal.intersects(searchEnv)) {
                        result.add(item)
                    }
                }
            }

            is RTreeNode.InternalNode -> {
                // 내부 노드의 경우 자식 노드들을 재귀적으로 검사
                for (child in node.children) {
                    queryInternal(child, searchEnv, result)
                }
            }
        }
    }

    /**
     * 주어진 좌표에서 가장 가까운 아이템을 검색
     */
    fun nearestNeighbor(x: Double, y: Double): Geometry? {
        if (!isBuilt) build()
        if (root == null) return null

        val point = Envelope(x, x, y, y)
        var minDistance = Double.POSITIVE_INFINITY
        var nearest: Geometry? = null

        // 우선순위 큐를 사용하여 가장 가까운 노드부터 탐색
        val queue = PriorityQueue<Pair<Double, RTreeNode>>(compareBy { it.first })
        queue.offer(Pair(point.distance(root!!.envelope), root!!))

        while (queue.isNotEmpty()) {
            val (distance, node) = queue.poll()

            // 현재까지 찾은 최단 거리보다 노드까지의 거리가 더 멀면 탐색 중단
            if (distance > minDistance) break

            when (node) {
                is RTreeNode.LeafNode -> {
                    // 리프 노드의 경우 각 아이템을 검사
                    for (item in node.geometries) {
                        val itemDistance = point.distance(item.envelopeInternal)
                        if (itemDistance < minDistance) {
                            minDistance = itemDistance
                            nearest = item
                        }
                    }
                }

                is RTreeNode.InternalNode -> {
                    // 내부 노드의 경우 자식 노드들을 우선순위 큐에 추가
                    for (child in node.children) {
                        val childDistance = point.distance(child.envelope)
                        if (childDistance < minDistance) {
                            queue.offer(Pair(childDistance, child))
                        }
                    }
                }
            }
        }

        return nearest
    }

    /**
     * k개의 최근접 이웃을 검색
     */
    fun nearestNeighbors(x: Double, y: Double, k: Int): List<Geometry> {
        if (k <= 0) return emptyList()
        if (!isBuilt) build()
        if (root == null) return emptyList()

        val point = Envelope(x, x, y, y)
        val result = PriorityQueue<Pair<Double, Geometry>>(compareByDescending { it.first })

        // 우선순위 큐를 사용하여 가장 가까운 노드부터 탐색
        val queue = PriorityQueue<Pair<Double, RTreeNode>>(compareBy { it.first })
        queue.offer(Pair(point.distance(root!!.envelope), root!!))

        while (queue.isNotEmpty()) {
            val (distance, node) = queue.poll()

            // 현재까지 찾은 k번째 최단 거리보다 노드까지의 거리가 더 멀면 탐색 중단
            if (result.size >= k && distance > result.peek().first) break

            when (node) {
                is RTreeNode.LeafNode -> {
                    // 리프 노드의 경우 각 아이템을 검사
                    for (item in node.geometries) {
                        val itemDistance = point.distance(item.envelopeInternal)

                        if (result.size < k) {
                            result.offer(Pair(itemDistance, item))
                        } else if (itemDistance < result.peek().first) {
                            result.poll()
                            result.offer(Pair(itemDistance, item))
                        }
                    }
                }

                is RTreeNode.InternalNode -> {
                    // 내부 노드의 경우 자식 노드들을 우선순위 큐에 추가
                    for (child in node.children) {
                        val childDistance = point.distance(child.envelope)
                        if (result.size < k || childDistance < result.peek().first) {
                            queue.offer(Pair(childDistance, child))
                        }
                    }
                }
            }
        }

        // 거리순으로 정렬된 결과 반환
        return result.map { it.second }.reversed()
    }
}
//
///**
// * STR-Tree의 노드를 나타내는 sealed 클래스
// */
//sealed class STRNode {
//    abstract var envelope: Envelope
//    abstract var depth: Int
//    abstract var parent: InternalNode?
//
//    /**
//     * 리프 노드: 실제 데이터 아이템을 저장
//     */
//    class LeafNode(
//        override var envelope: Envelope,
//        override var depth: Int,
//        val geometries: List<Geometry>,
//        override var parent: InternalNode? = null,
//    ) : STRNode()
//
//    /**
//     * 내부 노드: 자식 노드들을 저장
//     */
//    class InternalNode(
//        override var envelope: Envelope,
//        override var depth: Int,
//        val children: List<STRNode>,
//        override var parent: InternalNode? = null,
//    ) : STRNode()
//}
