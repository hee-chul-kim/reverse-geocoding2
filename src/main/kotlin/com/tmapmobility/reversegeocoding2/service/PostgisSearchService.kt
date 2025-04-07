package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.geometryFactory
import com.tmapmobility.reversegeocoding2.model.SearchResponse
import com.tmapmobility.reversegeocoding2.repository.JijukRepository
import org.locationtech.jts.geom.Coordinate
import org.springframework.stereotype.Service

@Service
class PostgisSearchService(
    private val jijukRepository: JijukRepository
) : SearchService {

    override fun searchByPoint(lat: Double, lon: Double): SearchResponse {
        val point = geometryFactory.createPoint(Coordinate(lat, lon))
        return jijukRepository.findSmallestPolygonContainingPoint(point)
            ?.let { SearchResponse.of(it) }
            ?: SearchResponse()
    }

    override fun getVisualizationData(): NodeData? {
        // PostGIS는 트리 시각화를 지원하지 않음
        return null
    }
}
