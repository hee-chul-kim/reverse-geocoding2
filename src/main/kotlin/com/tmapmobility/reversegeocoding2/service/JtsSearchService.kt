package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.service.shapeloader.ShapeLoader
import jakarta.annotation.PostConstruct
import org.locationtech.jts.index.strtree.AbstractNode
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Service
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

typealias JtsStRtree = org.locationtech.jts.index.strtree.STRtree

@DependsOn("shapeLoader")
@Service
class JtsSearchService(
    val shapeLoader: ShapeLoader
) : SearchService {

    var spatialDataModel = JtsStRtree()

    @PostConstruct
    fun createSpatialIndex() {
        thread {
            logger.info { "JTS STRtree 빌드 시작" }
            val buildTime = measureTimeMillis {
                shapeLoader.geometries.forEach {
                    spatialDataModel.insert(it.envelopeInternal, it)
                }
            }
            logger.info { "JTS STRtree 빌드 완료 - 소요 시간: ${buildTime}ms" }
        }
    }

    fun getVisualizationData(): NodeData? {
        return spatialDataModel.root?.convertToNodeData()
    }

    fun AbstractNode.convertToNodeData(): NodeData {

    }
}