package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.geometryFactory
import com.tmapmobility.reversegeocoding2.model.SearchResponse
import com.tmapmobility.reversegeocoding2.repository.JijukRepository
import org.locationtech.jts.geom.Coordinate
import org.springframework.stereotype.Service

@Service
class PostgisLocalSearchService(
    private val jijukRepository: JijukRepository
) : SearchService {

    override fun searchByPoint(lat: Double, lon: Double): SearchResponse {
        val point = geometryFactory.createPoint(Coordinate(lat, lon))
        return jijukRepository.findSmallestPolygonContainingPoint(point)
            ?.let { SearchResponse.of(it) }
            ?: SearchResponse()
    }

}
