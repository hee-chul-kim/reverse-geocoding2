package com.tmapmobility.reversegeocoding2.service.strtree

import com.tmapmobility.reversegeocoding2.service.LocalSearchService
import com.tmapmobility.reversegeocoding2.service.NodeData
import com.tmapmobility.reversegeocoding2.service.shapeloader.ShapeLoader
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

val logger = KotlinLogging.logger {}

@DependsOn("shapeLoader")
@Service
@Primary
class STRtreeSearchService(
    val shapeLoader: ShapeLoader
) : LocalSearchService {

    override var spatialDataModel = KhcSTRtree(10)

    @PostConstruct
    fun createSpatialIndex() {
        thread {
            spatialDataModel.insert(shapeLoader.geometries)

            logger.info { "STRtree 빌드 시작" }
            val buildTime = measureTimeMillis {
                spatialDataModel.build()
            }
            logger.info { "STRtree 빌드 완료 - 소요 시간: ${buildTime}ms" }
        }
    }

    override fun getVisualizationData(): NodeData? {
        return spatialDataModel.root?.convertToNodeData()
    }

} 