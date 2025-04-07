package com.tmapmobility.reversegeocoding2.service.rtree

import com.tmapmobility.reversegeocoding2.service.LocalSearchService
import com.tmapmobility.reversegeocoding2.service.NodeData
import com.tmapmobility.reversegeocoding2.service.shapeloader.ShapeLoader
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Service
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

@DependsOn("shapeLoader")
@Service
class RtreeSearchService(
    val shapeLoader: ShapeLoader
) : LocalSearchService {

    override var spatialDataModel: RTree = RTree(10)

    @PostConstruct
    fun createSpatialIndex() {
        thread {
            logger.info { "Rtree 빌드 시작" }
            val buildTime = measureTimeMillis {
                shapeLoader.geometries.forEach(spatialDataModel::insert)
            }
            logger.info { "Rtree 빌드 완료 - 소요 시간: ${buildTime}ms" }
        }
    }

    override fun getVisualizationData(): NodeData? {
        return spatialDataModel.root?.convertToNodeData()
    }

}