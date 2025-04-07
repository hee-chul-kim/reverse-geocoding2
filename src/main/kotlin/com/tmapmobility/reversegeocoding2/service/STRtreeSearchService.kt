package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.service.strtree.KhcSTRtree
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

val logger = KotlinLogging.logger {}

@Service
@Primary
class STRtreeSearchService(
    val shapeLoader: ShapeLoader
) : LocalSearchService {

    override lateinit var spatialDataModel: KhcSTRtree

    override fun createSpatialIndex() {
        spatialDataModel.insert(shapeLoader.geometries)

        logger.info { "STRtree 빌드 시작" }
        val buildTime = measureTimeMillis {
            spatialDataModel.build()
        }
        logger.info { "STRtree 빌드 완료 - 소요 시간: ${buildTime}ms" }
    }

    override fun getVisualizationData(): NodeData? {
        return spatialDataModel.root?.convertToNodeData()
    }

} 