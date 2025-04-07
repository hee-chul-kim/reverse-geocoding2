package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.ReverseGeocoding2Application
import com.tmapmobility.reversegeocoding2.service.jts.JtsSearchService
import com.tmapmobility.reversegeocoding2.service.strtree.STRtreeSearchService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import kotlin.random.Random

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [ReverseGeocoding2Application::class])
class MyStRtreeTest {

    @Autowired
    lateinit var jtsSearchService: JtsSearchService

    @Autowired
    lateinit var stRtreeSearchService: STRtreeSearchService

    @Test
    fun contextLoads() {
        assertNotNull(jtsSearchService)
        assertNotNull(stRtreeSearchService)
    }

    @Test
    fun `JTS 검색 결과와 CUSTOM RTREE 검색 결과 비교`() {
        val minX = 126.9103
        val maxY = 37.605
        val maxX = 127.0896
        val minY = 37.4743

        generateRandomCoordinates(minX, maxX, minY, maxY, 1000).forEach { coordinate ->
            val jtsResult = jtsSearchService.searchByPoint(coordinate.first, coordinate.second)
            val customRtreeResult = stRtreeSearchService.searchByPoint(coordinate.first, coordinate.second)

            assertEquals(jtsResult.dwid, customRtreeResult.dwid)
            assertEquals(jtsResult.jibun, customRtreeResult.jibun)
            assertEquals(jtsResult.pnu, customRtreeResult.pnu)
        }
    }

    fun generateRandomCoordinates(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        count: Int
    ): List<Pair<Double, Double>> {
        val randomCoordinates = mutableListOf<Pair<Double, Double>>()
        repeat(count) {
            val x = Random.nextDouble(minX, maxX)
            val y = Random.nextDouble(minY, maxY)
            randomCoordinates.add(Pair(x, y))
        }
        return randomCoordinates
    }
}