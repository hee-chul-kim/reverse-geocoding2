package com.tmapmobility.reversegeocoding2.service

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class ShapeLoaderRunner(
    private val shapeLoader: ShapeLoader,
    private val stRtreeSearchService: STRtreeSearchService,
    private val rtreeSearchService: RtreeSearchService
) :
    CommandLineRunner {

    override fun run(vararg args: String?) {
        shapeLoader.load()
        stRtreeSearchService.createSpatialIndex()
        rtreeSearchService.createSpatialIndex()
    }
}