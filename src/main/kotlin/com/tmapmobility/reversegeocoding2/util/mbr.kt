package com.tmapmobility.reversegeocoding2.util

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry

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