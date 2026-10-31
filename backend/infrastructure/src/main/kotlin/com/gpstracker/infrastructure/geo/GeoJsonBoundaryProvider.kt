package com.gpstracker.infrastructure.geo

import com.gpstracker.application.erosivity.BoundaryProvider
import com.gpstracker.domain.raster.Boundary
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

// Brazil outline from IBGE (malhas territoriais, minimum quality), bundled to clip rasters offline.
@Component
class GeoJsonBoundaryProvider : BoundaryProvider {

    private val brazil: Boundary by lazy { load("geo/br-country.geojson") }

    override fun brazil(): Boundary = brazil

    private fun load(resource: String): Boundary {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(resource)) { "Missing $resource" }
        val root = stream.use { JsonMapper.builder().build().readTree(it) }
        val rings = mutableListOf<DoubleArray>()
        for (feature in root.path("features")) {
            val geometry = feature.path("geometry")
            val coordinates = geometry.path("coordinates")
            when (geometry.path("type").asString()) {
                "Polygon" -> coordinates.forEach { rings += ring(it) }
                "MultiPolygon" -> coordinates.forEach { polygon -> polygon.forEach { rings += ring(it) } }
            }
        }
        return Boundary(rings)
    }

    private fun ring(node: JsonNode): DoubleArray {
        val values = DoubleArray(node.size() * 2)
        node.forEachIndexed { index, point ->
            values[2 * index] = point.get(0).doubleValue()
            values[2 * index + 1] = point.get(1).doubleValue()
        }
        return values
    }
}
