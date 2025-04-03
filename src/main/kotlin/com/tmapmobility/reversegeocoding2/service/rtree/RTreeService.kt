package com.tmapmobility.reversegeocoding2.service.rtree

import com.tmapmobility.reversegeocoding2.geometryFactory
import com.tmapmobility.reversegeocoding2.model.SearchResponse
import com.tmapmobility.reversegeocoding2.service.SearchService
import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTree
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.geotools.api.data.FileDataStore
import org.geotools.api.data.FileDataStoreFinder
import org.geotools.api.data.SimpleFeatureSource
import org.geotools.data.shapefile.dbf.DbaseFileHeader
import org.geotools.data.shapefile.dbf.DbaseFileReader
import org.geotools.data.shapefile.files.ShpFiles
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.strtree.STRtree
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.nio.charset.Charset
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

val logger = KotlinLogging.logger {}

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
            var totalTime = 0L

            val creationTime = measureTimeMillis {
                source.features.features().use { features ->
                    while (features.hasNext()) {
                        val feature = features.next()
                        val geometry = feature.defaultGeometry as? MultiPolygon

                        val dbfRecord = Array<Any?>(colSize) { null }
                        dbfReader.readEntry(dbfRecord)

                        if (geometry != null) {
                            val insertTime = measureTimeMillis {
                                geometry.userData = dbfRecord
                                rtree.insert(geometry.envelopeInternal, geometry)
                            }
                            totalTime += insertTime
                            cnt++
                            batchCnt++

                            // 100000개마다 배치 처리 시간 로깅
                            if (batchCnt == 100000L) {
                                val batchEndTime = System.currentTimeMillis()
                                val batchDuration = batchEndTime - batchStartTime
                                logger.info { """
                                    RTree 배치 처리 완료
                                    - 현재까지 처리된 다각형 수: $cnt
                                    - 배치 처리 시간: ${batchDuration}ms
                                    - 평균 처리 속도: ${100000.0 / batchDuration * 1000} polygons/sec
                                    - 마지막 100000개 평균 insert 시간: ${totalTime / 100000}ms/polygon
                                """.trimIndent() }
                                
                                batchCnt = 0L
                                batchStartTime = System.currentTimeMillis()
                                totalTime = 0L
                            }
                        }
                    }
                }
            }

            // 마지막 배치 처리 로깅
            if (batchCnt > 0) {
                val batchEndTime = System.currentTimeMillis()
                val batchDuration = batchEndTime - batchStartTime
                logger.info { """
                    RTree 마지막 배치 처리 완료
                    - 처리된 다각형 수: $batchCnt
                    - 배치 처리 시간: ${batchDuration}ms
                    - 평균 처리 속도: ${batchCnt.toDouble() / batchDuration * 1000} polygons/sec
                    - 평균 insert 시간: ${totalTime / batchCnt}ms/polygon
                """.trimIndent() }
            }

            logger.info { """
                RTree 생성 완료
                - 총 로드된 다각형 수: $cnt
                - 전체 소요 시간: ${creationTime}ms
                - 전체 평균 처리 속도: ${cnt.toDouble() / creationTime * 1000} polygons/sec
            """.trimIndent() }

            (rtree as? RTree)?.logTreeStats()
        } catch (e: Exception) {
            logger.error(e) { "Shapefile에서 다각형 로딩 실패" }
        }
    }

    private fun initFileDataStore() {
        val resource = ClassPathResource(shapefilePath)
        val file = resource.file
        file.setReadOnly()
        store = FileDataStoreFinder.getDataStore(file) ?:
                throw IllegalArgumentException("Could not find data store for file: $shapefilePath")
    }
}