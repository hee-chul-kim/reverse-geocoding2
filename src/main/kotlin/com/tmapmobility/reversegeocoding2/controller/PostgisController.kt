package com.tmapmobility.reversegeocoding2.controller

import com.tmapmobility.reversegeocoding2.model.SearchResponse
import com.tmapmobility.reversegeocoding2.service.LocalSearchService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/postgis")
class PostgisController(@Qualifier("PostgisSearchService") private val localSearchService: LocalSearchService) {

    @GetMapping("/search")
    fun searchByPoint(@RequestParam lat: Double, @RequestParam lon: Double): SearchResponse {
        return localSearchService.searchByPoint(lat, lon)
    }
}
