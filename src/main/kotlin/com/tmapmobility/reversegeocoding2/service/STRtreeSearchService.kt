package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.service.strtree.KhcSTRtree
import com.tmapmobility.reversegeocoding2.service.strtree.STRNode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.locationtech.jts.geom.Envelope
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

val logger = KotlinLogging.logger {}

@Service
@Primary
class STRtreeSearchService(
    val shapeLoader: ShapeLoader
) : SearchService {

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
        return spatialDataModel.root?.let { convertToNodeData(it) }
    }

    private fun convertToNodeData(node: STRNode, depth: Int = 0): NodeData {
        val id = System.identityHashCode(node).toString()
        return when (node) {
            is STRNode.InternalNode -> NodeData(
                id = id,
                isLeaf = false,
                mbr = convertToMBRData(node.envelope),
                children = node.children.map { convertToNodeData(it, depth + 1) },
                depth = depth,
                size = node.children.size
            )

            is STRNode.LeafNode -> NodeData(
                id = id,
                isLeaf = true,
                mbr = convertToMBRData(node.envelope),
                children = emptyList(),
                depth = depth,
                size = node.geometries.size
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