package com.tmapmobility.reversegeocoding2.service.rtree.khc

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.MultiPolygon

open class RTreeNode(var boundingBox: Envelope)

class RTreeLeafNode(var polygons: MutableList<MultiPolygon>) : RTreeNode(computeBoundingBox(polygons)) {
    companion object {
        fun computeBoundingBox(polygons: List<MultiPolygon>): Envelope {
            val minX = polygons.minOf { it.envelopeInternal.minX }
            val minY = polygons.minOf { it.envelopeInternal.minY }
            val maxX = polygons.maxOf { it.envelopeInternal.maxX }
            val maxY = polygons.maxOf { it.envelopeInternal.maxY }
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
}
