package com.tmapmobility.reversegeocoding2.repository

import com.tmapmobility.reversegeocoding2.entity.JijukEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Point

@Repository
interface JijukRepository : JpaRepository<JijukEntity, Long> {

    @Query(value = """
        SELECT j.* 
        FROM dw_jijukmap_bg_jijuk_11_a j 
        WHERE ST_Contains(j.geom, :point) 
        ORDER BY ST_Area(j.geom) ASC 
        LIMIT 1
    """, nativeQuery = true)
    fun findSmallestPolygonContainingPoint(point: Point): JijukEntity?
}
