package com.tmapmobility.reversegeocoding2.service.rtree

import com.tmapmobility.reversegeocoding2.geometryFactory
import com.tmapmobility.reversegeocoding2.model.SearchResponse
import com.tmapmobility.reversegeocoding2.service.SearchService
import com.tmapmobility.reversegeocoding2.service.kdtree.KDTree
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTree
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeInternalNode
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeLeafNode
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTreeNode
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.geotools.api.data.FileDataStore
import org.geotools.api.data.FileDataStoreFinder
import org.geotools.api.data.SimpleFeatureSource
import org.geotools.data.shapefile.dbf.DbaseFileHeader
import org.geotools.data.shapefile.dbf.DbaseFileReader
import org.geotools.data.shapefile.files.ShpFiles
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.strtree.AbstractNode
import org.locationtech.jts.index.strtree.STRtree
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.nio.charset.Charset
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

val logger = KotlinLogging.logger {}

data class NodeData(
    val id: String,
    val isLeaf: Boolean,
    val mbr: MBRData,
    val children: List<NodeData>,
    val depth: Int,
    val size: Int
)

data class MBRData(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
)

@Service
class RTreeService(@Value("\${shapefile.path}") private val shapefilePath: String,
    private val rtreeFactory: RTreeFactory,
    ): SearchService {

    private lateinit var rtree: SpatialIndex
    private lateinit var store: FileDataStore

    override fun searchByPoint(lat: Double, lon: Double): SearchResponse {
        val point = geometryFactory.createPoint(Coordinate(lat, lon))
        val candidates = rtree.query(point.envelopeInternal)
        val polygons = candidates.filterIsInstance<MultiPolygon>()
        val polygon = polygons.firstOrNull { it.contains(point) }
        val userData = polygon?.userData as? Array<*>

        return SearchResponse(
            dwid = userData?.get(0) as? Int?,
            jibun = userData?.get(1) as? String?,
            bon = userData?.get(2) as? Int?,
            bu = userData?.get(3) as? Int?,
            pnu = userData?.get(4) as? String?,
            jimok = userData?.get(5) as? String?,
            jbClass = userData?.get(6) as? String?,
            admcodeL = userData?.get(7) as? String?,
            admcodeA = userData?.get(8) as? String?,
            dpArea = userData?.get(9) as? Double?,
        )
    }

    @PostConstruct
    fun init() {
        rtreeFactory.create().also { rtree = it }
        thread {
            initFileDataStore()
            createRtree()
        }
    }

    private fun createRtree() {
        val source: SimpleFeatureSource = store.featureSource
        logger.info { "Shapefile에서 다각형 로딩 시작: $shapefilePath" }

        val dbfReader = DbaseFileReader(ShpFiles(ClassPathResource(shapefilePath).file.absolutePath),
            true, Charset.forName("UTF-8"))
        val dbfHeader: DbaseFileHeader = dbfReader.header
        val colSize: Int = dbfHeader.numFields

        try {
            var cnt = 0L
            var batchStartTime = System.currentTimeMillis()
            var batchCnt = 0L

            // 먼저 모든 geometry를 리스트에 수집
            val geometries = mutableListOf<Geometry>()
            val loadingTime = measureTimeMillis {
                source.features.features().use { features ->
                    while (features.hasNext()) {
                        val feature = features.next()
                        val geometry = feature.defaultGeometry as? MultiPolygon

                        val dbfRecord = Array<Any?>(colSize) { null }
                        dbfReader.readEntry(dbfRecord)

                        if (geometry != null) {
                            geometry.userData = dbfRecord
                            geometries.add(geometry)
                            cnt++
                            batchCnt++

                            // 100000개마다 배치 처리 시간 로깅
                            if (batchCnt == 100000L) {
                                val batchEndTime = System.currentTimeMillis()
                                val batchDuration = batchEndTime - batchStartTime
                                logger.info { """
                                    데이터 로딩 배치 처리 완료
                                    - 현재까지 처리된 다각형 수: $cnt
                                    - 배치 처리 시간: ${batchDuration}ms
                                    - 평균 처리 속도: ${100000.0 / batchDuration * 1000} polygons/sec
                                """.trimIndent() }
                                
                                batchCnt = 0L
                                batchStartTime = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }

            logger.info { """
                데이터 로딩 완료
                - 총 로드된 다각형 수: $cnt
                - 전체 소요 시간: ${loadingTime}ms
                - 전체 평균 처리 속도: ${cnt.toDouble() / loadingTime * 1000} polygons/sec
            """.trimIndent() }

            // KDTree 생성 및 RTree 변환
            logger.info { "KDTree 생성 시작" }
            val treeCreationTime = measureTimeMillis {
                val kdTree = KDTree(geometries)
                kdTree.logTreeStats()
                
                // KDTree를 RTree로 변환
                logger.info { "KDTree를 RTree로 변환 시작" }
                rtree = kdTree.toRTree()
            }

            logger.info { """
                KDTree 생성 및 RTree 변환 완료
                - 소요 시간: ${treeCreationTime}ms
                - 평균 처리 속도: ${cnt.toDouble() / treeCreationTime * 1000} polygons/sec
            """.trimIndent() }

            (rtree as? RTree)?.logTreeStats()
        } catch (e: Exception) {
            logger.error(e) { "Shapefile에서 다각형 로딩 실패" }
        }

        dbfReader.close()
    }

    private fun initFileDataStore() {
        val resource = ClassPathResource(shapefilePath)
        val file = resource.file
        file.setReadOnly()
        store = FileDataStoreFinder.getDataStore(file) ?:
                throw IllegalArgumentException("Could not find data store for file: $shapefilePath")
    }

    fun getTreeVisualizationData(): NodeData? {
        return when (rtree) {
            is RTree -> (rtree as RTree).root?.let { convertToNodeData(it) }
            is STRtree -> convertToNodeData((rtree as STRtree).root)
            else -> null
        }
    }

    private fun convertToNodeData(node: RTreeNode, depth: Int = 0): NodeData {
        val id = System.identityHashCode(node).toString()
        return when {
            depth >= 10 -> NodeData(
                id = id,
                isLeaf = true,
                mbr = convertToMBRData(node.boundingBox),
                children = emptyList(),
                depth = depth,
                size = when (node) {
                    is RTreeLeafNode -> node.polygons.size
                    is RTreeInternalNode -> node.children.size
                    else -> 0
                }
            )
            node is RTreeLeafNode -> NodeData(
                id = id,
                isLeaf = true,
                mbr = convertToMBRData(node.boundingBox),
                children = emptyList(),
                depth = depth,
                size = node.polygons.size
            )
            node is RTreeInternalNode -> NodeData(
                id = id,
                isLeaf = false,
                mbr = convertToMBRData(node.boundingBox),
                children = node.children.map { convertToNodeData(it, depth + 1) },
                depth = depth,
                size = node.children.size
            )
            else -> throw IllegalStateException("Unknown node type")
        }
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
                children = node.childBoundables.map { convertToNodeData(it as AbstractNode, depth + 1) },
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