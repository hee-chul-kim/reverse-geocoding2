package com.tmapmobility.reversegeocoding2

import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

@SpringBootApplication(scanBasePackages = ["com.tmapmobility.reversegeocoding2"])
class ReverseGeocoding2Application

fun main(args: Array<String>) {
    runApplication<ReverseGeocoding2Application>(*args)
}
