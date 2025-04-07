package com.tmapmobility.reversegeocoding2.service.rtree

import com.tmapmobility.reversegeocoding2.service.MBRData
import com.tmapmobility.reversegeocoding2.service.NodeData
import com.tmapmobility.reversegeocoding2.util.computeBoundingBox
import com.tmapmobility.reversegeocoding2.util.plus
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry

sealed class RTreeNode {
    abstract var envelope: Envelope
    abstract var depth: Int
    abstract var parent: InternalNode?

    /**
     * 엔트리를 노드에 삽입
     * @param entry 삽입할 엔트리 (Geometry 또는 RTreeNode)
     */
    abstract fun insertEntry(entry: Any)

    class LeafNode(
        var geometries: MutableList<Geometry>
    ) : RTreeNode() {
        override var envelope: Envelope = computeBoundingBox(geometries)
        override var depth: Int = 0
        override var parent: InternalNode? = null

        constructor(envelope: Envelope, depth: Int, geometries: MutableList<Geometry>) : this(geometries) {
            this.envelope = envelope
            this.depth = depth
        }

        override fun insertEntry(entry: Any) {
            if (entry !is Geometry) {
                throw IllegalArgumentException("Leaf node can only insert Geometry")
            }
            geometries.add(entry)
            envelope += entry.envelopeInternal
        }
    }

    class InternalNode(
        var children: MutableList<RTreeNode>
    ) : RTreeNode() {
        override var envelope: Envelope = computeBoundingBox(children)
        override var depth: Int = 0
        override var parent: InternalNode? = null

        init {
            children.forEach { child ->
                child.parent = this
                child.depth = this.depth + 1
            }
        }

        constructor(envelope: Envelope, depth: Int, nodes: MutableList<RTreeNode>) : this(nodes) {
            this.envelope = envelope
            this.depth = depth
        }

        override fun insertEntry(entry: Any) {
            if (entry !is RTreeNode) {
                throw IllegalArgumentException("Internal node can only insert RTreeNode")
            }
            children.add(entry)
            entry.parent = this
            entry.depth = this.depth + 1
            // 바운딩 박스 업데이트
            envelope += entry.envelope
        }

        companion object {
            fun computeBoundingBox(nodes: List<RTreeNode>): Envelope {
                if (nodes.isEmpty()) {
                    return Envelope()
                }
                val minX = nodes.minOf { it.envelope.minX }
                val minY = nodes.minOf { it.envelope.minY }
                val maxX = nodes.maxOf { it.envelope.maxX }
                val maxY = nodes.maxOf { it.envelope.maxY }
                return Envelope(minX, maxX, minY, maxY)
            }
        }
    }

    fun convertToNodeData(): NodeData {
        val id = System.identityHashCode(this).toString()
        return when (this) {
            is InternalNode -> NodeData(
                id = id,
                isLeaf = false,
                mbr = convertToMBRData(envelope),
                children = children.map { it.convertToNodeData() },
                depth = depth,
                size = children.size
            )

            is LeafNode -> NodeData(
                id = id,
                isLeaf = true,
                mbr = convertToMBRData(envelope),
                children = emptyList(),
                depth = depth,
                size = geometries.size
            )
        }
    }

    private fun convertToMBRData(envelope: Envelope): MBRData {
        return MBRData(
            minX = envelope.minX,
            minY = envelope.minY,
            maxX = envelope.maxX,
            maxY = envelope.maxY
        )
    }
}


