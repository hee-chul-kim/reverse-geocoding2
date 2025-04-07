package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.geometryFactory
import com.tmapmobility.reversegeocoding2.model.SearchResponse
import com.tmapmobility.reversegeocoding2.service.strtree.KhcSTRtree
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.nio.charset.Charset
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

@Service
@Primary
class SearchServiceImpl(
    @Value("\${shapefile.path}") private val shapefilePath: String
) : SearchService {

    private lateinit var spatialIndex: KhcSTRtree
    private lateinit var store: FileDataStore
    override fun searchByPoint(lat: Double, lon: Double): SearchResponse {
        val point = geometryFactory.createPoint(Coordinate(lat, lon))
        val candidates = spatialIndex.query(point.envelopeInternal)
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
        spatialIndex = KhcSTRtree()
        thread {
            initFileDataStore()
            createSpatialIndex()
        }
    }

    private fun createSpatialIndex() {
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

            val loadingTime = measureTimeMillis {
                source.features.features().use { features ->
                    while (features.hasNext()) {
                        val feature = features.next()
                        val geometry = feature.defaultGeometry as? MultiPolygon

                        val dbfRecord = Array<Any?>(colSize) { null }
                        dbfReader.readEntry(dbfRecord)

                        if (geometry != null) {
                            geometry.userData = dbfRecord
                            spatialIndex.insert(geometry)
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

            // STRtree 빌드
            logger.info { "STRtree 빌드 시작" }
            val buildTime = measureTimeMillis {
                spatialIndex.build()
            }
            logger.info { "STRtree 빌드 완료 - 소요 시간: ${buildTime}ms" }

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

    override fun getTreeVisualizationData(): NodeData? {
        return spatialIndex.getRoot()?.let { convertToNodeData(it) }
    }

    private fun convertToNodeData(node: com.tmapmobility.reversegeocoding2.service.strtree.STRNode, depth: Int = 0): NodeData {
        val id = System.identityHashCode(node).toString()
        return when (node) {
            is com.tmapmobility.reversegeocoding2.service.strtree.STRNode.InternalNode -> NodeData(
                id = id,
                isLeaf = false,
                mbr = convertToMBRData(node.envelope),
                children = node.children.map { convertToNodeData(it, depth + 1) },
                depth = depth,
                size = node.children.size
            )
            is com.tmapmobility.reversegeocoding2.service.strtree.STRNode.LeafNode -> NodeData(
                id = id,
                isLeaf = true,
                mbr = convertToMBRData(node.envelope),
                children = emptyList(),
                depth = depth,
                size = node.items.size
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