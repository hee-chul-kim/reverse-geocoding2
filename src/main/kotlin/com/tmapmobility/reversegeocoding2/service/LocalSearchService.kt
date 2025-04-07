package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.geometryFactory
import com.tmapmobility.reversegeocoding2.model.SearchResponse
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.MultiPolygon

interface LocalSearchService : SearchService {
    val spatialDataModel: SpatialDataModel

    fun getVisualizationData(): NodeData?

    //fun createSpatialIndex()

    override fun searchByPoint(lat: Double, lon: Double): SearchResponse {
        val point = geometryFactory.createPoint(Coordinate(lat, lon))
        val candidates = spatialDataModel.query(point.envelopeInternal)
        val polygons = candidates.filterIsInstance<MultiPolygon>()
        val polygon = polygons.firstOrNull { it.contains(point) }
        val userData = polygon?.userData as? Array<*>

        return SearchResponse(
            dwid = userData?.get(0) as? Int?,
            jibun = userData?.get(1) as? String?,
            bon = userData?.get(2) as? Int?,
            bu = userData?.get(3) as? Int?,
            pnu = userData?.get(4) as? String?,
            jimok = userData?.get(5) as? String?,
            jbClass = userData?.get(6) as? String?,
            admcodeL = userData?.get(7) as? String?,
            admcodeA = userData?.get(8) as? String?,
            dpArea = userData?.get(9) as? Double?,
        )
    }
}

data class NodeData(
    val id: String,
    val isLeaf: Boolean,
    val mbr: MBRData,
    val children: List<NodeData>,
    val depth: Int,
    val size: Int
)

data class MBRData(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
)