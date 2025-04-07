package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.geometryFactory
import com.tmapmobility.reversegeocoding2.model.NodeData
import com.tmapmobility.reversegeocoding2.model.SearchResponse
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.MultiPolygon

interface LocalSearchService : SearchService {
    val spatialDataModel: SpatialDataModel

    fun getVisualizationData(): NodeData?

    override fun searchByPoint(lat: Double, lon: Double): SearchResponse {
        val point = geometryFactory.createPoint(Coordinate(lat, lon))
        // 1. MBR 로 검색
        val candidates = spatialDataModel.query(point.envelopeInternal)
        // 2. Polygon 내 포함 여부 확인
        val polygons = candidates.filterIsInstance<MultiPolygon>()
        val polygon = polygons.firstOrNull { it.contains(point) }
        // 3. UserData 추출
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
