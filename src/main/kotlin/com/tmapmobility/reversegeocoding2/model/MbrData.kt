package com.tmapmobility.reversegeocoding2.model

import org.locationtech.jts.geom.Envelope

data class MbrData(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
)

fun Envelope.toMbrData(): MbrData {
    return MbrData(
        minX = this.minX,
        minY = this.minY,
        maxX = this.maxX,
        maxY = this.maxY
    )
}