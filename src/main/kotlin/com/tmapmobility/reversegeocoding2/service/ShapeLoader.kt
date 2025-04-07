package com.tmapmobility.reversegeocoding2.service

import org.geotools.api.data.FileDataStore
import org.geotools.api.data.FileDataStoreFinder
import org.geotools.api.data.SimpleFeatureSource
import org.geotools.data.shapefile.dbf.DbaseFileHeader
import org.geotools.data.shapefile.dbf.DbaseFileReader
import org.geotools.data.shapefile.files.ShpFiles
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.MultiPolygon
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import kotlin.system.measureTimeMillis

@Component
class ShapeLoader(
    @Value("\${shapefile.path}") private val shapefilePath: String
) {
    private lateinit var store: FileDataStore

    // 로드 결과
    val geometries = mutableListOf<Geometry>()

    fun load() {
        initFileDataStore()
        createSpatialIndex()
    }

    private fun initFileDataStore() {
        val resource = ClassPathResource(shapefilePath)
        val file = resource.file
        file.setReadOnly()
        store = FileDataStoreFinder.getDataStore(file)
            ?: throw IllegalArgumentException("Could not find data store for file: $shapefilePath")
    }

    private fun createSpatialIndex() {
        val source: SimpleFeatureSource = store.featureSource
        logger.info { "Shapefile에서 다각형 로딩 시작: $shapefilePath" }

        val dbfReader = DbaseFileReader(
            ShpFiles(ClassPathResource(shapefilePath).file.absolutePath),
            true, Charset.forName("UTF-8")
        )
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
                            geometries.add(geometry)
                            cnt++
                            batchCnt++

                            // 100000개마다 배치 처리 시간 로깅
                            if (batchCnt == 100000L) {
                                val batchEndTime = System.currentTimeMillis()
                                val batchDuration = batchEndTime - batchStartTime
                                logger.info {
                                    """
                                    데이터 로딩 배치 처리 완료
                                    - 현재까지 처리된 다각형 수: $cnt
                                    - 배치 처리 시간: ${batchDuration}ms
                                    - 평균 처리 속도: ${100000.0 / batchDuration * 1000} polygons/sec
                                """.trimIndent()
                                }

                                batchCnt = 0L
                                batchStartTime = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }

            logger.info {
                """
                데이터 로딩 완료
                - 총 로드된 다각형 수: $cnt
                - 전체 소요 시간: ${loadingTime}ms
                - 전체 평균 처리 속도: ${cnt.toDouble() / loadingTime * 1000} polygons/sec
            """.trimIndent()
            }

        } catch (e: Exception) {
            logger.error(e) { "Shapefile에서 다각형 로딩 실패" }
        }

        dbfReader.close()
    }
}