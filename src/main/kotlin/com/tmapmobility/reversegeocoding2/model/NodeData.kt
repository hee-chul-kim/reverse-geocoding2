package com.tmapmobility.reversegeocoding2.model

data class NodeData(
    val id: String,
    val isLeaf: Boolean,
    val mbr: MbrData,
    val children: List<NodeData>,
    val depth: Int,
    val size: Int
)