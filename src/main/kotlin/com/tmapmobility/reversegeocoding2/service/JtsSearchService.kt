package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.model.SearchResponse
import com.tmapmobility.reversegeocoding2.service.shapeloader.ShapeLoader
import jakarta.annotation.PostConstruct
import org.locationtech.jts.geom.Envelope
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

    override fun searchByPoint(lat: Double, lon: Double): SearchResponse {
        TODO("Not yet implemented")
    }

    fun getVisualizationData(): NodeData? {
        return convertToNodeData(spatialDataModel.root)
    }

    private fun convertToNodeData(node: AbstractNode, depth: Int = 0): NodeData {
        val id = System.identityHashCode(node).toString()
        return when {
            depth >= 7 -> NodeData(
                id = id,
                isLeaf = true,
                mbr = convertToMBRData(node.bounds as Envelope),
                children = emptyList(),
                depth = depth,
                size = node.size()
            )

            else -> NodeData(
                id = id,
                isLeaf = true,
                mbr = convertToMBRData(node.bounds as Envelope),
                children = node.childBoundables
                    .filterIsInstance<AbstractNode>()
                    .map { convertToNodeData(it as AbstractNode, depth + 1) },
                depth = depth,
                size = node.size()
            )
        }
    }

    private fun convertToMBRData(envelope: Envelope): MBRData {
        return MBRData(
            minX = envelope.minX,
            minY = envelope.minY,
            maxX = envelope.maxX,
            maxY = envelope.maxY
        )
    }
}