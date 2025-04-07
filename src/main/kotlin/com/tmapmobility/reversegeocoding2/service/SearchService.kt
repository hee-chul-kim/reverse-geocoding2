package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.model.SearchResponse

interface SearchService {
    fun searchByPoint(lat: Double, lon: Double): SearchResponse
    fun getTreeVisualizationData(): NodeData?
}

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