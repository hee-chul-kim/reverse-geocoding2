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
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.nio.charset.Charset
import kotlin.concurrent.thread

val logger = KotlinLogging.logger {}

@Service
class RTreeService(@Value("\${shapefile.path}") private val shapefilePath: String,
    private val rtreeFactory: RTreeFactory,
    ): SearchService {

    private lateinit var rtree: SpatialIndex
    private lateinit var store: FileDataStore

    override fun searchByPoint(lat: Double, lon: Double): SearchResponse {
        val point = geometryFactory.createPoint(Coordinate(lat, lon))
        val candidates: List<*> = rtree.query(point.envelopeInternal)
        val polygon: MultiPolygon? = candidates
            .filterIsInstance<MultiPolygon>()
            .firstOrNull { it.contains(point) }

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
        logger.info { "Loading polygons from shapefile: $shapefilePath" }

        val dbfReader = DbaseFileReader(ShpFiles(ClassPathResource(shapefilePath).file.absolutePath),
            true, Charset.forName("UTF-8"))
        val dbfHeader: DbaseFileHeader = dbfReader.header
        val colSize: Int = dbfHeader.numFields
//        for (i in 0 until colSize!!) {
//            val fieldName = dbfHeader.getFieldName(i)
//            val fieldType = dbfHeader.getFieldClass(i).toGenericString()
//            logger.info { "Field $i: $fieldName / $fieldType" }
//        }

        try {
            var cnt = 0L
            source.features.features().use { features ->
                while (features.hasNext()) {
                    val feature = features.next()
                    val geometry = feature.defaultGeometry as? MultiPolygon

                    val dbfRecord = Array<Any?>(colSize) { null }
                    dbfReader.readEntry(dbfRecord)

                    if (geometry != null) {
                        geometry.userData = dbfRecord
                        rtree.insert(geometry.envelopeInternal, geometry)
                        cnt++
                    }
                }
            }

            logger.info { "Loaded $cnt polygons from shapefile." }

            (rtree as? RTree)?.logTreeStats()
        } catch (e: Exception) {
            logger.error(e) { "Failed to load polygons from shapefile." }
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