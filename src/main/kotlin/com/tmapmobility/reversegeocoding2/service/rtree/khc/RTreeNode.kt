package com.tmapmobility.reversegeocoding2.service.rtree.khc

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry

open class RTreeNode(
    var boundingBox: Envelope,
    var depth: Int = 0,
    var parent: RTreeInternalNode? = null
)

class RTreeLeafNode(var polygons: MutableList<Geometry>) : RTreeNode(computeBoundingBox(polygons)) {
    companion object {
        fun computeBoundingBox(geometries: List<Geometry>): Envelope {
            val minX = geometries.minOf { it.envelopeInternal.minX }
            val minY = geometries.minOf { it.envelopeInternal.minY }
            val maxX = geometries.maxOf { it.envelopeInternal.maxX }
            val maxY = geometries.maxOf { it.envelopeInternal.maxY }
            return Envelope(minX, maxX, minY, maxY)
        }
    }
}

class RTreeInternalNode(var children: MutableList<RTreeNode>) : RTreeNode(computeBoundingBox(children)) {
    companion object {
        fun computeBoundingBox(nodes: List<RTreeNode>): Envelope {
            val minX = nodes.minOf { it.boundingBox.minX }
            val minY = nodes.minOf { it.boundingBox.minY }
            val maxX = nodes.maxOf { it.boundingBox.maxX }
            val maxY = nodes.maxOf { it.boundingBox.maxY }
            return Envelope(minX, maxX, minY, maxY)
        }
    }

    init {
        children.forEach { child ->
            child.parent = this
            child.depth = this.depth + 1
        }
    }
}
