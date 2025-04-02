package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.model.SearchResponse

interface SearchService {

    fun searchByPoint(lat: Double, lon: Double): SearchResponse
}