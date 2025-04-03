package com.tmapmobility.reversegeocoding2.service.rtree

import com.tmapmobility.reversegeocoding2.service.rtree.khc.RTree
import com.tmapmobility.reversegeocoding2.service.rtree.khc.split.LinearSplitStrategy
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.strtree.STRtree
import org.springframework.stereotype.Component

@Component
class RTreeFactory {

    fun create(): SpatialIndex {
        //return STRtree(10)
        return RTree(10, LinearSplitStrategy())
    }
}