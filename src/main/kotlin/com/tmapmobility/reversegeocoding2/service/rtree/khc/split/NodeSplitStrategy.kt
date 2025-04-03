package com.tmapmobility.reversegeocoding2.service.rtree.khc.split

import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeNode
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeLeafNode
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeInternalNode
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry

/**
 * RTree 노드 분할 전략을 정의하는 인터페이스
 * 
 * RTree에서 노드가 최대 용량을 초과할 때 노드를 두 개의 새로운 노드로 분할하는 방법을 정의
 * 다양한 분할 전략(예: Quadratic Split, Linear Split 등)을 구현할 수 있도록 함
 */
interface NodeSplitStrategy {
    /**
     * 주어진 노드를 두 개의 새로운 노드로 분할
     * 
     * @param node 분할할 노드 (RTreeLeafNode 또는 RTreeInternalNode)
     * @return 분할된 두 개의 새로운 노드 (left, right)
     */
    fun split(node: RTreeNode): Pair<RTreeNode, RTreeNode>
}