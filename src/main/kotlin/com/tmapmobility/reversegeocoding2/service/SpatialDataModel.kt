package com.tmapmobility.reversegeocoding2.service

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry

interface SpatialDataModel {
    fun insert(geometry: Geometry)
    fun query(searchEnv: Envelope): List<Geometry>
}