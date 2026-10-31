package com.gpstracker.application.erosivity

import com.gpstracker.application.station.WeatherStationRepository
import com.gpstracker.domain.erosivity.PeriodErosivity
import com.gpstracker.domain.station.WeatherStation
import com.gpstracker.domain.raster.GridSpec
import com.gpstracker.domain.raster.IdwInterpolator
import com.gpstracker.domain.raster.Sample
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.ceil

data class ErosivityMapRequest(
    val fromYear: Int,
    val toYear: Int,
    // Share of the year's hours that must have a precipitation reading for the year to count.
    val minCoverage: Double = 0.8,
    val minYears: Int? = null,
    val cellSize: Double = 0.1,
    val power: Double = 2.0,
    val maxDistanceKm: Double = 400.0,
    val maxNeighbors: Int = 12,
) {
    init {
        require(fromYear <= toYear) { "fromYear must not be after toYear" }
        require(fromYear >= 1990 && toYear <= LocalDate.now(ZoneOffset.UTC).year) { "Years must be between 1990 and the current year" }
        require(minCoverage in 0.0..1.0) { "minCoverage must be between 0 and 1" }
        require(minYears == null || minYears in 1..(toYear - fromYear + 1)) { "minYears must be between 1 and the number of years in the period" }
        require(cellSize in 0.05..1.0) { "cellSize must be between 0.05 and 1 degree" }
    }

    val periodYears: Int get() = toYear - fromYear + 1

    // Default: half of the period, capped at 5 years, so long periods still accept younger stations.
    val effectiveMinYears: Int get() = minYears ?: minOf(5, ceil(periodYears / 2.0).toInt())
}

data class StationRFactor(val station: WeatherStation, val rFactor: Double, val yearsUsed: Int)

class ErosivityMap(
    val request: ErosivityMapRequest,
    val parametersKey: String,
    val grid: GridSpec,
    // Row-major from the north-west cell; NaN = no data.
    val values: FloatArray,
    val stations: List<StationRFactor>,
) {
    private val valid get() = values.filter { !it.isNaN() }
    val minValue: Double? get() = valid.minOrNull()?.toDouble()
    val maxValue: Double? get() = valid.maxOrNull()?.toDouble()
    val meanValue: Double? get() = valid.takeIf { it.isNotEmpty() }?.average()
}

@Service
class ErosivityMapService(
    private val stations: WeatherStationRepository,
    private val cache: ErosivityYearCache,
    private val cacheService: ErosivityCacheService,
    private val boundaries: BoundaryProvider,
) {
    // Recent maps of the current cache version: most visitors ask for the same default period.
    private val recent = object : LinkedHashMap<ErosivityMapRequest, ErosivityMap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ErosivityMapRequest, ErosivityMap>) = size > MAX_RECENT_MAPS
    }
    private var recentVersion = -1L

    fun stationRFactors(request: ErosivityMapRequest): List<StationRFactor> {
        val byStation = cache.findYears(cacheService.parametersKey, request.fromYear, request.toYear).groupBy { it.stationId }
        if (byStation.isEmpty()) return emptyList()
        val stationsById = stations.findAll().associateBy { it.id }
        return byStation.mapNotNull { (stationId, years) ->
            val station = stationsById[stationId] ?: return@mapNotNull null
            PeriodErosivity.rFactor(years, request.fromYear, request.toYear, request.minCoverage, request.effectiveMinYears)
                ?.let { StationRFactor(station, it.rFactor, it.yearsUsed) }
        }.sortedBy { it.station.details.code }
    }

    fun build(request: ErosivityMapRequest): ErosivityMap {
        val version = cacheService.version
        synchronized(recent) {
            if (version != recentVersion) {
                recent.clear()
                recentVersion = version
            }
            recent[request]?.let { return it }
        }
        val map = interpolate(request)
        synchronized(recent) { if (version == recentVersion) recent[request] = map }
        return map
    }

    private fun interpolate(request: ErosivityMapRequest): ErosivityMap {
        val samples = stationRFactors(request)
        val boundary = boundaries.brazil()
        val grid = GridSpec.covering(boundary.mainBounds(), request.cellSize)
        val interpolator = IdwInterpolator(request.power, request.maxDistanceKm, request.maxNeighbors)
        val values = interpolator.interpolate(
            samples.map { Sample(it.station.details.coordinate.longitude, it.station.details.coordinate.latitude, it.rFactor) },
            grid,
            boundary.mask(grid),
        )
        return ErosivityMap(request, cacheService.parametersKey, grid, values, samples)
    }

    private companion object {
        const val MAX_RECENT_MAPS = 8
    }
}
