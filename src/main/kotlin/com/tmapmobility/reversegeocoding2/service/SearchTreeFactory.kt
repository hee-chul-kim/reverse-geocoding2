package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTree
import com.tmapmobility.reversegeocoding2.service.rtree.khc.split.DefaultNodeSplitStrategy
import org.locationtech.jts.index.SpatialIndex
import org.springframework.stereotype.Component

@Component
class SearchTreeFactory {

    fun create(): SpatialIndex {
        return RTree(4, DefaultNodeSplitStrategy())
    }
} 