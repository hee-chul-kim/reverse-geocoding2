package com.tmapmobility.reversegeocoding2.service.strtree

import org.locationtech.jts.geom.Envelope
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import java.util.PriorityQueue

/**
 * STR-Tree (Sort-Tile-Recursive R-tree) 구현
 * 벌크 로딩에 최적화된 R-tree 변형
 * @param nodeCapacity 노드당 최대 항목 수
 */
class STRTree<T : Any>(
    private val nodeCapacity: Int = DEFAULT_NODE_CAPACITY
) {
    private var root: STRNode<T>? = null
    private val items = mutableListOf<STRItem<T>>()
    private var isBuilt = false

    companion object {
        const val DEFAULT_NODE_CAPACITY = 10
    }

    /**
     * 아이템을 트리에 삽입
     * 실제 트리 구성은 build() 호출 시점까지 지연됨
     */
    fun insert(envelope: Envelope, item: T) {
        require(!isBuilt) { "트리가 이미 구축되어 있어 새로운 아이템을 삽입할 수 없습니다." }
        items.add(STRItem(envelope, item))
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
    private fun createLevel(items: List<STRItem<T>>, level: Int): STRNode<T> {
        // 아이템이 노드 용량 이하면 바로 리프 노드 생성
        if (items.size <= nodeCapacity) {
            val envelope = computeEnvelope(items.map { it.envelope })
            return STRNode.LeafNode(envelope, level, items)
        }

        // 1. X축으로 정렬
        val sortedByX = items.sortedBy { it.envelope.minX }

        // 2. X축 슬라이스 수 계산 (M = ceil(N/n), N: 전체 아이템 수, n: 노드 용량)
        val sliceCount = ceil(Math.sqrt(items.size.toDouble() / nodeCapacity)).toInt()
        val itemsPerSlice = ceil(items.size.toDouble() / sliceCount).toInt()

        // 3. X축 슬라이스로 분할하고 각 슬라이스 내에서 Y축으로 정렬
        val slices = sortedByX.chunked(itemsPerSlice)
            .map { slice -> slice.sortedBy { it.envelope.minY } }

        // 4. 각 슬라이스에서 노드 크기만큼 그룹화
        val nodes = mutableListOf<STRNode<T>>()
        
        for (slice in slices) {
            val groups = slice.chunked(nodeCapacity)
            for (group in groups) {
                val envelope = computeEnvelope(group.map { it.envelope })
                nodes.add(STRNode.LeafNode(envelope, level, group))
            }
        }

        // 노드가 하나면 바로 반환
        if (nodes.size == 1) {
            return nodes.first()
        }

        // 노드가 여러 개면 재귀적으로 상위 레벨 생성
        return createInternalLevel(nodes, level + 1)
    }

    /**
     * 내부 노드 레벨 생성
     */
    private fun createInternalLevel(nodes: List<STRNode<T>>, level: Int): STRNode<T> {
        if (nodes.size <= nodeCapacity) {
            val envelope = computeEnvelope(nodes.map { it.envelope })
            return STRNode.InternalNode(envelope, level, nodes)
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
        val parentNodes = mutableListOf<STRNode<T>>()
        
        for (slice in slices) {
            val groups = slice.chunked(nodeCapacity)
            for (group in groups) {
                val envelope = computeEnvelope(group.map { it.envelope })
                parentNodes.add(STRNode.InternalNode(envelope, level, group))
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
    fun query(searchEnv: Envelope): List<T> {
        if (!isBuilt) build()
        
        val result = mutableListOf<T>()
        root?.let { queryInternal(it, searchEnv, result) }
        return result
    }

    private fun queryInternal(node: STRNode<T>, searchEnv: Envelope, result: MutableList<T>) {
        // 현재 노드의 MBR이 검색 영역과 겹치지 않으면 종료
        if (!node.envelope.intersects(searchEnv)) return

        when (node) {
            is STRNode.LeafNode -> {
                // 리프 노드의 경우 각 아이템을 검사
                for (item in node.items) {
                    if (item.envelope.intersects(searchEnv)) {
                        result.add(item.item)
                    }
                }
            }
            is STRNode.InternalNode -> {
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
    fun nearestNeighbor(x: Double, y: Double): T? {
        if (!isBuilt) build()
        if (root == null) return null

        val point = Envelope(x, x, y, y)
        var minDistance = Double.POSITIVE_INFINITY
        var nearest: T? = null

        // 우선순위 큐를 사용하여 가장 가까운 노드부터 탐색
        val queue = PriorityQueue<Pair<Double, STRNode<T>>>(compareBy { it.first })
        queue.offer(Pair(point.distance(root!!.envelope), root!!))

        while (queue.isNotEmpty()) {
            val (distance, node) = queue.poll()
            
            // 현재까지 찾은 최단 거리보다 노드까지의 거리가 더 멀면 탐색 중단
            if (distance > minDistance) break

            when (node) {
                is STRNode.LeafNode -> {
                    // 리프 노드의 경우 각 아이템을 검사
                    for (item in node.items) {
                        val itemDistance = point.distance(item.envelope)
                        if (itemDistance < minDistance) {
                            minDistance = itemDistance
                            nearest = item.item
                        }
                    }
                }
                is STRNode.InternalNode -> {
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
    fun nearestNeighbors(x: Double, y: Double, k: Int): List<T> {
        if (k <= 0) return emptyList()
        if (!isBuilt) build()
        if (root == null) return emptyList()

        val point = Envelope(x, x, y, y)
        val result = PriorityQueue<Pair<Double, T>>(compareByDescending { it.first })

        // 우선순위 큐를 사용하여 가장 가까운 노드부터 탐색
        val queue = PriorityQueue<Pair<Double, STRNode<T>>>(compareBy { it.first })
        queue.offer(Pair(point.distance(root!!.envelope), root!!))

        while (queue.isNotEmpty()) {
            val (distance, node) = queue.poll()
            
            // 현재까지 찾은 k번째 최단 거리보다 노드까지의 거리가 더 멀면 탐색 중단
            if (result.size >= k && distance > result.peek().first) break

            when (node) {
                is STRNode.LeafNode -> {
                    // 리프 노드의 경우 각 아이템을 검사
                    for (item in node.items) {
                        val itemDistance = point.distance(item.envelope)
                        
                        if (result.size < k) {
                            result.offer(Pair(itemDistance, item.item))
                        } else if (itemDistance < result.peek().first) {
                            result.poll()
                            result.offer(Pair(itemDistance, item.item))
                        }
                    }
                }
                is STRNode.InternalNode -> {
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

    /**
     * 트리의 루트 노드를 반환
     */
    fun getRoot(): STRNode<T>? {
        if (!isBuilt) build()
        return root
    }

    /**
     * 노드를 JSON 형식으로 변환
     */
    fun toJson(): Map<String, Any?> {
        if (!isBuilt) build()
        return root?.toJson() ?: mapOf()
    }
}

/**
 * STR-Tree의 노드를 나타내는 sealed 클래스
 */
sealed class STRNode<T : Any> {
    abstract val envelope: Envelope
    abstract val level: Int

    /**
     * 리프 노드: 실제 데이터 아이템을 저장
     */
    class LeafNode<T : Any>(
        override val envelope: Envelope,
        override val level: Int,
        val items: List<STRItem<T>>
    ) : STRNode<T>()

    /**
     * 내부 노드: 자식 노드들을 저장
     */
    class InternalNode<T : Any>(
        override val envelope: Envelope,
        override val level: Int,
        val children: List<STRNode<T>>
    ) : STRNode<T>()

    /**
     * 노드를 JSON 형식으로 변환
     */
    fun toJson(): Map<String, Any?> {
        val json = mutableMapOf<String, Any?>(
            "mbr" to mapOf(
                "minX" to envelope.minX,
                "maxX" to envelope.maxX,
                "minY" to envelope.minY,
                "maxY" to envelope.maxY
            ),
            "level" to level
        )

        when (this) {
            is LeafNode -> {
                json["type"] = "leaf"
                json["items"] = items.map { item ->
                    mapOf(
                        "mbr" to mapOf(
                            "minX" to item.envelope.minX,
                            "maxX" to item.envelope.maxX,
                            "minY" to item.envelope.minY,
                            "maxY" to item.envelope.maxY
                        ),
                        "data" to item.item.toString()
                    )
                }
            }
            is InternalNode -> {
                json["type"] = "internal"
                json["children"] = children.map { it.toJson() }
            }
        }

        return json
    }
}

/**
 * 공간 데이터 아이템을 나타내는 클래스
 */
data class STRItem<T : Any>(
    val envelope: Envelope,
    val item: T
)

// Envelope 확장 함수: 두 Envelope 간의 거리 계산
fun Envelope.distance(other: Envelope): Double {
    val dx = when {
        other.maxX < this.minX -> this.minX - other.maxX
        other.minX > this.maxX -> other.minX - this.maxX
        else -> 0.0
    }
    val dy = when {
        other.maxY < this.minY -> this.minY - other.maxY
        other.minY > this.maxY -> other.minY - this.maxY
        else -> 0.0
    }
    return Math.sqrt(dx * dx + dy * dy)
} 