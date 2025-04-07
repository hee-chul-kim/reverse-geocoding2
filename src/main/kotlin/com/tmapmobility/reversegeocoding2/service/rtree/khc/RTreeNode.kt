package com.tmapmobility.reversegeocoding2.service.rtree.khc

import com.tmapmobility.reversegeocoding2.util.plus
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry

abstract class RTreeNode {
    abstract var boundingBox: Envelope
    abstract var depth: Int
    abstract var parent: RTreeInternalNode?

    /**
     * 엔트리를 노드에 삽입
     * @param entry 삽입할 엔트리 (Geometry 또는 RTreeNode)
     */
    abstract fun insertEntry(entry: Any)
}

class RTreeLeafNode(
    var geometries: MutableList<Geometry>
) : RTreeNode() {
    override var boundingBox: Envelope = computeBoundingBox(geometries)
    override var depth: Int = 0
    override var parent: RTreeInternalNode? = null

    companion object {
        fun computeBoundingBox(geometries: List<Geometry>): Envelope {
            if (geometries.isEmpty()) {
                return Envelope()
            }
            val minX = geometries.minOf { it.envelopeInternal.minX }
            val minY = geometries.minOf { it.envelopeInternal.minY }
            val maxX = geometries.maxOf { it.envelopeInternal.maxX }
            val maxY = geometries.maxOf { it.envelopeInternal.maxY }
            return Envelope(minX, maxX, minY, maxY)
        }
    }

    override fun insertEntry(entry: Any) {
        if (entry !is Geometry) {
            throw IllegalArgumentException("Leaf node can only insert Geometry")
        }
        addPolygon(entry)
    }

    private fun addPolygon(polygon: Geometry) {
        geometries.add(polygon)
        boundingBox += polygon.envelopeInternal
    }
}

class RTreeInternalNode(
    var children: MutableList<RTreeNode>
) : RTreeNode() {
    override var boundingBox: Envelope = computeBoundingBox(children)
    override var depth: Int = 0
    override var parent: RTreeInternalNode? = null

    init {
        children.forEach { child ->
            child.parent = this
            child.depth = this.depth + 1
        }
    }

    override fun insertEntry(entry: Any) {
        if (entry !is RTreeNode) {
            throw IllegalArgumentException("Internal node can only insert RTreeNode")
        }
        children.add(entry)
        entry.parent = this
        entry.depth = this.depth + 1
        // 바운딩 박스 업데이트
        boundingBox = computeBoundingBox(children)
    }

    companion object {
        fun computeBoundingBox(nodes: List<RTreeNode>): Envelope {
            if (nodes.isEmpty()) {
                return Envelope()
            }
            val minX = nodes.minOf { it.boundingBox.minX }
            val minY = nodes.minOf { it.boundingBox.minY }
            val maxX = nodes.maxOf { it.boundingBox.maxX }
            val maxY = nodes.maxOf { it.boundingBox.maxY }
            return Envelope(minX, maxX, minY, maxY)
        }
    }
}
