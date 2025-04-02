package com.tmapmobility.reversegeocoding2.model

import com.tmapmobility.reversegeocoding2.entity.JijukEntity
import java.math.BigDecimal

data class SearchResponse(
    val id: Long? = null,
    var dwid: Int? = null,
    var jibun: String? = null,
    var bon: Int? = null,
    var bu: Int? = null,
    var pnu: String? = null,
    var jimok: String? = null,
    var jbClass: String? = null,
    var admcodeL: String? = null,
    var admcodeA: String? = null,
    var dpArea: Double? = null
) {
    companion object {
        fun of(entity: JijukEntity): SearchResponse {
            return SearchResponse(
                id = entity.id,
                dwid = entity.dwid,
                jibun = entity.jibun,
                bon = entity.bon,
                bu = entity.bu,
                pnu = entity.pnu,
                jimok = entity.jimok,
                jbClass = entity.jbClass,
                admcodeL = entity.admcodeL,
                admcodeA = entity.admcodeA,
                dpArea = entity.dpArea?.toDouble()
            )
        }
    }
}