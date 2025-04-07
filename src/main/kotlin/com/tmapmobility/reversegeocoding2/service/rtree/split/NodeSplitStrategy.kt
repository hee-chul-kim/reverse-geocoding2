package com.tmapmobility.reversegeocoding2.service.rtree.split

import com.tmapmobility.reversegeocoding2.service.rtree.RTree
import com.tmapmobility.reversegeocoding2.service.rtree.RTreeNode

/**
 * RTree 노드 분할 전략을 정의하는 인터페이스
 *
 * RTree에서 노드가 최대 용량을 초과할 때 노드를 두 개의 새로운 노드로 분할하는 방법을 정의
 * 다양한 분할 전략(예: Quadratic Split, Linear Split 등)을 구현할 수 있도록 함
 */
interface NodeSplitStrategy {
    companion object {
        const val MIN_ENTRIES_PER_NODE = 4 // 노드당 최소 엔트리 수
    }

    /**
     * 주어진 노드를 두 개의 새로운 노드로 분할
     *
     * @param node 분할할 노드 (RTreeLeafNode 또는 RTreeInternalNode)
     * @param tree 분할할 RTree
     * @return 분할된 두 개의 새로운 노드 (left, right)
     */
    fun split(node: RTreeNode, tree: RTree): Pair<RTreeNode, RTreeNode>

    /**
     * 노드의 분할 조건을 검사
     *
     * @param node 검사할 노드
     * @return 분할이 필요한지 여부
     */
    fun needsSplit(node: RTreeNode): Boolean {
        val size = when (node) {
            is RTreeNode.LeafNode -> node.geometries.size
            is RTreeNode.InternalNode -> node.children.size
        }
        return size >= MIN_ENTRIES_PER_NODE * 2 || needsCustomSplit(node)
    }

    /**
     * 노드의 추가적인 분할 조건을 검사
     * 각 전략에서 구현해야 함
     *
     * @param node 검사할 노드
     * @return 분할이 필요한지 여부
     */
    fun needsCustomSplit(node: RTreeNode): Boolean = false
}