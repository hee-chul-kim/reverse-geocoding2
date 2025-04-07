package com.tmapmobility.reversegeocoding2.service.jts

import com.tmapmobility.reversegeocoding2.model.NodeData
import com.tmapmobility.reversegeocoding2.model.toMbrData
import com.tmapmobility.reversegeocoding2.service.LocalSearchService
import com.tmapmobility.reversegeocoding2.service.shapeloader.ShapeLoader
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.strtree.AbstractNode
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Service
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

@DependsOn("shapeLoader")
@Service
class JtsSearchService(
    val shapeLoader: ShapeLoader
) : LocalSearchService {

    override var spatialDataModel = JtsStRtreeAdapter()

    @PostConstruct
    fun createSpatialIndex() {
        thread {
            logger.info { "JTS STRtree 빌드 시작" }
            val buildTime = measureTimeMillis {
                shapeLoader.geometries.forEach {
                    spatialDataModel.insert(it)
                }
            }
            logger.info { "JTS STRtree 빌드 완료 - 소요 시간: ${buildTime}ms" }
        }
    }

    override fun getVisualizationData(): NodeData? {
        return convertToNodeData(spatialDataModel.root)
    }

    private fun convertToNodeData(node: AbstractNode, depth: Int = 0): NodeData {
        val id = System.identityHashCode(node).toString()
        return when {
            depth >= 7 -> NodeData(
                id = id,
                isLeaf = true,
                mbr = (node.bounds as Envelope).toMbrData(),
                children = emptyList(),
                depth = depth,
                size = node.size()
            )

            else -> NodeData(
                id = id,
                isLeaf = true,
                mbr = (node.bounds as Envelope).toMbrData(),
                children = node.childBoundables
                    .filterIsInstance<AbstractNode>()
                    .map { convertToNodeData(it, depth + 1) },
                depth = depth,
                size = node.size()
            )
        }
    }
}