package com.tmapmobility.reversegeocoding2.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.hibernate.annotations.Type
import org.locationtech.jts.geom.MultiPolygon
import java.math.BigDecimal

@Entity
@Table(name = "dw_jijukmap_bg_jijuk_11_a")
data class JijukEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @JsonIgnore
    @Column(name = "geom", columnDefinition = "geometry(MultiPolygon,4326)")
    var geom: MultiPolygon? = null,

    @Column(name = "dwid")
    var dwid: Int? = null,

    @Column(name = "jibun", length = 22)
    var jibun: String? = null,

    @Column(name = "bon")
    var bon: Int? = null,

    @Column(name = "bu")
    var bu: Int? = null,

    @Column(name = "pnu", length = 19)
    var pnu: String? = null,

    @Column(name = "jimok", length = 3)
    var jimok: String? = null,

    @Column(name = "jb_class", length = 1)
    var jbClass: String? = null,

    @Column(name = "admcode_l", length = 10)
    var admcodeL: String? = null,

    @Column(name = "admcode_a", length = 10)
    var admcodeA: String? = null,

    @Column(name = "dp_area", precision = 10, scale = 2)
    var dpArea: BigDecimal? = null
)
