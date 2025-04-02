package com.tmapmobility.reversegeocoding2.util

import org.locationtech.jts.geom.Envelope

operator fun Envelope.plus(b: Envelope): Envelope {
    val copy = this.copy()
    copy.expandToInclude(b)
    return copy
}