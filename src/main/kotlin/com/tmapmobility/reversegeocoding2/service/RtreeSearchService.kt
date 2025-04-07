package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.service.rtree.RTree
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class RtreeSearchService(
    val shapeLoader: ShapeLoader
) : SearchService {

    override lateinit var spatialDataModel: RTree

    override fun getVisualizationData(): NodeData? {
        return spatialDataModel.root?.let { convertToNodeData(it) }
    }

    override fun createSpatialIndex() {
        logger.info { "Rtree 빌드 시작" }
        val buildTime = measureTimeMillis {
            shapeLoader.geometries.forEach(spatialDataModel::insert)
        }
        logger.info { "STRtree 빌드 완료 - 소요 시간: ${buildTime}ms" }
    }
}