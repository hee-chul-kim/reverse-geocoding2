//package com.tmapmobility.reversegeocoding2.service
//
//import com.google.common.geometry.*
//import io.github.oshai.kotlinlogging.KotlinLogging
//import org.geotools.api.data.FileDataStore
//import org.geotools.api.data.FileDataStoreFinder
//import org.geotools.api.data.SimpleFeatureSource
//import org.locationtech.jts.geom.Coordinate
//import org.locationtech.jts.geom.MultiPolygon
//import org.locationtech.jts.geom.Polygon
//import org.locationtech.jts.index.strtree.STRtree
//import org.springframework.beans.factory.annotation.Value
//import org.springframework.core.io.ClassPathResource
//import org.springframework.stereotype.Service
//import java.util.*
//import java.util.stream.Collectors.toList
//import kotlin.collections.HashMap
//
//
//val logger = KotlinLogging.logger {}
//
//// 🔹 ShapefileReaderService: SHP 파일을 읽어 MultiPolygon 리스트 반환
//@Service
//class ShapefileReaderService(
//
//) {
//    fun readShpFile(shapefilePath: String) {
//        val store = fileDataStore(shapefilePath)
//        try {
//            val source: SimpleFeatureSource = store.featureSource
//            logger.info { "Reading polygons from shapefile: $shapefilePath" }
//
//            source.features.features().use { features ->
//                while (features.hasNext()) {
//                    val feature = features.next()
//                    val geometry = feature.defaultGeometry as? MultiPolygon
//                    if (geometry != null) {
//                        geometry.srid = 4326
//                        logger.info { "Loaded polygon with ID: ${feature.id}" }
//                    }
//                }
//            }
//        } catch (e: Exception) {
//            logger.error(e) { "Error reading shapefile: ${e.message}" }
//        } finally {
//            store.dispose()
//        }
//    }
//
//    fun loadSTRtree(shapefilePath: String): STRtree {
//        val store = fileDataStore(shapefilePath)
//
//        val strTree = STRtree()
//        try {
//            val source: SimpleFeatureSource = store.featureSource
//            logger.info { "Loading polygons from shapefile: $shapefilePath" }
//
//            source.features.features().use { features ->
//                while (features.hasNext()) {
//                    val feature = features.next()
//                    val geometry = feature.defaultGeometry as? MultiPolygon
//                    if (geometry != null) {
//                        geometry.srid = 4326
//                        strTree.insert(geometry.envelopeInternal, geometry)
//                    }
//                }
//            }
//        } catch (e: Exception) {
//            logger.error(e) { "Error loading polygons from shapefile: ${e.message}" }
//        } finally {
//            store.dispose()
//        }
//
//        logger.info { "Loaded ${strTree.size()} polygons from shapefile." }
//
//        return strTree
//    }
//
//    private fun fileDataStore(shapefilePath: String): FileDataStore {
//        val resource = ClassPathResource(shapefilePath)
//        val file = resource.file
//        file.setReadOnly()
//        val store = FileDataStoreFinder.getDataStore(file) ?: throw IllegalArgumentException("Could not find data store for file: $shapefilePath")
//        return store
//    }
//
//    fun loadPolygonsByS2(): Unit {
//
//        // 1. 다각형 리스트 생성
//        val polygons: List<S2Polygon> = listOf()
//
//        // 2. 공간 인덱스 (S2CellId -> S2Polygon 매핑) 생성
//        val cellIndex: MutableMap<S2CellId, S2Polygon> = HashMap()
//        val coverer = S2RegionCoverer.builder()
//            .setMaxCells(8) // 셀 개수 제한
//            .setMinLevel(14)
//            .setMaxLevel(19)
//            .build()
//
//
//        for (polygon in polygons) {
//            val cellUnion = coverer.getCovering(polygon)
//            for (cellId in cellUnion.cellIds()) {
//                cellIndex[cellId] = polygon
//            }
//        }
//
//        // 3. 검사할 포인트
//        val pointLatLng = S2LatLng.fromDegrees(37.5, -121.9)
//        val point = pointLatLng.toPoint()
//        val pointCell = S2CellId.fromPoint(point)
//
//
//        // 4. 빠른 검사 (셀 비교 후 다각형 검사)
//        if (cellIndex.containsKey(pointCell)) {
//            val candidatePolygon = cellIndex[pointCell]
//            if (candidatePolygon!!.contains(point)) {
//                println("포인트가 다각형 내부에 있습니다.")
//            } else {
//                println("포인트가 다각형 내부에 없습니다.")
//            }
//        } else {
//            println("포인트가 어떤 다각형에도 포함되지 않습니다.")
//        }
//    }
//
//
//}
