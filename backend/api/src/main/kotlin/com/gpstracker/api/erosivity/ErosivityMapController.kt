package com.gpstracker.api.erosivity

import com.fasterxml.jackson.annotation.JsonProperty
import com.gpstracker.api.shared.Csv
import com.gpstracker.application.erosivity.CacheRefresh
import com.gpstracker.application.erosivity.CacheStatus
import com.gpstracker.application.erosivity.ErosivityCacheService
import com.gpstracker.application.erosivity.ErosivityMap
import com.gpstracker.application.erosivity.ErosivityMapRequest
import com.gpstracker.application.erosivity.ErosivityMapService
import com.gpstracker.application.erosivity.StationRFactor
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

// Query string of every map endpoint; defaults match ErosivityMapRequest.
data class ErosivityMapQuery(
    val fromYear: Int,
    val toYear: Int,
    val minCoverage: Double = 0.8,
    val minYears: Int? = null,
    val cellSize: Double = 0.1,
    val power: Double = 2.0,
    val maxDistanceKm: Double = 400.0,
    val maxNeighbors: Int = 12,
) {
    fun toRequest() = ErosivityMapRequest(fromYear, toYear, minCoverage, minYears, cellSize, power, maxDistanceKm, maxNeighbors)
}

data class GridResponse(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val cellSize: Double,
    val columns: Int,
    val rows: Int,
)

data class MapStationResponse(
    val id: Long,
    val code: String,
    val name: String,
    val state: String,
    val latitude: Double,
    val longitude: Double,
    @get:JsonProperty("rFactor")
    val rFactor: Double,
    val yearsUsed: Int,
)

data class MapStatsResponse(val min: Double?, val max: Double?, val mean: Double?, val stations: Int, val cellsWithData: Int)

data class CacheStatusResponse(
    val cachedStations: Int,
    val staleStations: Int,
    val firstYear: Int?,
    val lastYear: Int?,
    val computedAt: Instant?,
    val latestRefresh: CacheRefreshResponse?,
)

data class CacheRefreshResponse(
    val id: Long,
    val onlyStale: Boolean,
    val status: String,
    val stationsTotal: Int?,
    val stationsDone: Int,
    val stationsFailed: Int,
    val failureReason: String?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
)

data class ErosivityMapResponse(
    val fromYear: Int,
    val toYear: Int,
    val minCoverage: Double,
    val minYears: Int,
    val interpolation: String,
    val power: Double,
    val maxDistanceKm: Double,
    val maxNeighbors: Int,
    val parameters: CalculationParameters,
    val unit: String,
    val grid: GridResponse,
    // Row-major from the north-west cell, R rounded to whole units; null = no data.
    val values: List<Int?>,
    val stats: MapStatsResponse,
    val stations: List<MapStationResponse>,
)

@RestController
@RequestMapping("/erosivity-maps")
class ErosivityMapController(private val maps: ErosivityMapService, private val cache: ErosivityCacheService) {

    @GetMapping
    fun map(@ParameterObject query: ErosivityMapQuery): ErosivityMapResponse = maps.build(query.toRequest()).toResponse()

    // ESRI ASCII grid + WGS 84 .prj, the raster pair QGIS, TerrSet and ArcGIS open directly.
    @GetMapping("/grid.zip")
    fun gridZip(@ParameterObject query: ErosivityMapQuery): ResponseEntity<ByteArray> {
        val map = maps.build(query.toRequest())
        val stem = "r_factor_${map.request.fromYear}_${map.request.toYear}"
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("$stem.asc"))
                zip.write(asciiGrid(map).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("$stem.prj"))
                zip.write(WGS84_PRJ.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        return Csv.download("$stem.zip", MediaType("application", "zip"), bytes)
    }

    @GetMapping("/stations.geojson")
    fun stationsGeoJson(@ParameterObject query: ErosivityMapQuery): ResponseEntity<Map<String, Any?>> {
        val request = query.toRequest()
        val features = maps.stationRFactors(request).map {
            val details = it.station.details
            mapOf(
                "type" to "Feature",
                "geometry" to mapOf("type" to "Point", "coordinates" to listOf(details.coordinate.longitude, details.coordinate.latitude)),
                "properties" to linkedMapOf(
                    "code" to details.code,
                    "name" to details.name,
                    "state" to details.state,
                    "r_factor" to round1(it.rFactor),
                    "years_used" to it.yearsUsed,
                    "from_year" to request.fromYear,
                    "to_year" to request.toYear,
                    "unit" to R_UNIT,
                ),
            )
        }
        val body = mapOf("type" to "FeatureCollection", "features" to features)
        return Csv.download("r_factor_stations_${request.fromYear}_${request.toYear}.geojson", MediaType("application", "geo+json"), body)
    }

    @GetMapping("/stations.csv")
    fun stationsCsv(@ParameterObject query: ErosivityMapQuery): ResponseEntity<String> {
        val request = query.toRequest()
        val rows = maps.stationRFactors(request).map {
            val details = it.station.details
            listOf(details.code, details.name, details.state, details.coordinate.latitude, details.coordinate.longitude, round1(it.rFactor), it.yearsUsed)
        }
        val header = listOf("code", "name", "state", "latitude", "longitude", "r_factor", "years_used")
        return Csv.attachment("r_factor_stations_${request.fromYear}_${request.toYear}.csv", Csv.write(header, rows))
    }

    @GetMapping("/cache")
    fun cacheStatus(): CacheStatusResponse = cache.status().toResponse(cache.latestRefresh())

    @PostMapping("/cache/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun refresh(@RequestParam(defaultValue = "true") onlyStale: Boolean): CacheRefreshResponse =
        cache.queueRefresh(onlyStale).toResponse()

    @GetMapping("/cache/refreshes/{id}")
    fun refreshStatus(@PathVariable id: Long): CacheRefreshResponse = cache.findRefresh(id).toResponse()

    private fun ErosivityMap.toResponse(): ErosivityMapResponse {
        val validCells = values.count { !it.isNaN() }
        return ErosivityMapResponse(
            fromYear = request.fromYear,
            toYear = request.toYear,
            minCoverage = request.minCoverage,
            minYears = request.effectiveMinYears,
            interpolation = "IDW",
            power = request.power,
            maxDistanceKm = request.maxDistanceKm,
            maxNeighbors = request.maxNeighbors,
            parameters = cache.parameters.toResponse(),
            unit = R_UNIT,
            grid = GridResponse(grid.west, grid.south, grid.east, grid.north, grid.cellSize, grid.columns, grid.rows),
            values = values.map { if (it.isNaN()) null else it.roundToInt() },
            stats = MapStatsResponse(minValue?.let(::round1), maxValue?.let(::round1), meanValue?.let(::round1), stations.size, validCells),
            stations = stations.map { it.toResponse() },
        )
    }

    private fun StationRFactor.toResponse() = MapStationResponse(
        id = station.id,
        code = station.details.code,
        name = station.details.name,
        state = station.details.state,
        latitude = station.details.coordinate.latitude,
        longitude = station.details.coordinate.longitude,
        rFactor = round1(rFactor),
        yearsUsed = yearsUsed,
    )

    private fun CacheStatus.toResponse(latest: CacheRefresh?) =
        CacheStatusResponse(cachedStations, staleStations, firstYear, lastYear, computedAt, latest?.toResponse())

    private fun CacheRefresh.toResponse() = CacheRefreshResponse(
        id, onlyStale, status.name, stationsTotal, stationsDone, stationsFailed, failureReason, createdAt, startedAt, finishedAt,
    )

    private fun asciiGrid(map: ErosivityMap): String = buildString {
        val grid = map.grid
        appendLine("ncols ${grid.columns}")
        appendLine("nrows ${grid.rows}")
        appendLine("xllcorner ${coordinate(grid.west)}")
        appendLine("yllcorner ${coordinate(grid.south)}")
        appendLine("cellsize ${coordinate(grid.cellSize)}")
        appendLine("NODATA_value $NO_DATA")
        for (row in 0 until grid.rows) {
            val offset = row * grid.columns
            for (column in 0 until grid.columns) {
                if (column > 0) append(' ')
                val value = map.values[offset + column]
                append(if (value.isNaN()) NO_DATA else value.roundToInt())
            }
            append('\n')
        }
    }

    private fun coordinate(value: Double) = String.format(Locale.ROOT, "%.6f", value)

    private fun round1(value: Double) = Math.round(value * 10) / 10.0

    private companion object {
        const val NO_DATA = -9999
        const val WGS84_PRJ = "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]]," +
            "PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]"
    }
}
