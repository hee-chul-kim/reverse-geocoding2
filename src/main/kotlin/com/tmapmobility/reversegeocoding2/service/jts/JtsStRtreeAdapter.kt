package com.tmapmobility.reversegeocoding2.service.jts

import com.tmapmobility.reversegeocoding2.service.SpatialDataModel
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.index.strtree.AbstractNode

typealias JtsStRtree = org.locationtech.jts.index.strtree.STRtree

class JtsStRtreeAdapter : SpatialDataModel {

    private val strTree = JtsStRtree()

    override fun insert(geometry: Geometry) {
        strTree.insert(geometry.envelopeInternal, geometry)
    }

    override fun query(searchEnv: Envelope): List<Geometry> {
        return strTree.query(searchEnv) as List<Geometry>
    }

    val root: AbstractNode
        get() = strTree.root
}