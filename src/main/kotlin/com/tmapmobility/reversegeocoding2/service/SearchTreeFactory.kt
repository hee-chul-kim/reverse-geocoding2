package com.tmapmobility.reversegeocoding2.service

import com.tmapmobility.reversegeocoding2.service.rtree.RTree
import com.tmapmobility.reversegeocoding2.service.rtree.split.DefaultSplitStrategy
import org.locationtech.jts.index.SpatialIndex
import org.springframework.stereotype.Component

@Component
class SearchTreeFactory {

    fun create(): SpatialIndex {
        return RTree(4, DefaultSplitStrategy())
    }
} 