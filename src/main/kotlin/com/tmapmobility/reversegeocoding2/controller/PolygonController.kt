package com.tmapmobility.reversegeocoding2.controller

import com.tmapmobility.reversegeocoding2.model.SearchResponse
import com.tmapmobility.reversegeocoding2.service.rtree.RTreeService
import com.tmapmobility.reversegeocoding2.service.PostgisService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/polygon")
class PolygonController(private val postgisService: PostgisService, private val RTreeService: RTreeService) {

    @GetMapping("/postgis")
    fun findSmallestPolygon(
        @RequestParam("lat") lat: Double,
        @RequestParam("lon") lon: Double
    ): SearchResponse? {
        return postgisService.searchByPoint(lat, lon)
    }

    @GetMapping("/jts")
    fun checkPointContains(
        @RequestParam lon: Double,
        @RequestParam lat: Double
    ): SearchResponse? {
        return RTreeService.searchByPoint(lat, lon)
    }
}
