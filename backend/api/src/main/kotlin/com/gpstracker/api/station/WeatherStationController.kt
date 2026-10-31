package com.gpstracker.api.station

import com.gpstracker.application.station.StationFilter
import com.gpstracker.application.station.WeatherStationDataService
import com.gpstracker.application.station.WeatherStationService
import com.gpstracker.domain.observation.AggregationPeriod
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RestController
@RequestMapping("/weather-stations")
class WeatherStationController(
    private val service: WeatherStationService,
    private val data: WeatherStationDataService,
) {
    @GetMapping
    fun search(
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) region: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) minAgeYears: Int?,
    ): List<WeatherStationResponse> {
        minAgeYears?.let { require(it in 0..200) { "minAgeYears must be between 0 and 200" } }
        val filter = StationFilter(
            state = state,
            region = region,
            text = q,
            foundedOnOrBefore = minAgeYears?.let { LocalDate.now(ZoneOffset.UTC).minusYears(it.toLong()) },
        )
        return service.search(filter).map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun find(@PathVariable id: Long): WeatherStationResponse = service.findById(id).toResponse()

    @GetMapping("/{id}/observations")
    fun observations(
        @PathVariable id: Long,
        @RequestParam from: OffsetDateTime,
        @RequestParam to: OffsetDateTime,
    ): List<HourlyObservationResponse> = data.findHourly(id, from.toInstant(), to.toInstant()).map { it.toResponse() }

    @GetMapping("/{id}/annual-data")
    fun annualData(@PathVariable id: Long): List<AnnualStationDataResponse> =
        data.findAnnualSummaries(id).map { it.toResponse() }

    @GetMapping("/{id}/rainfall-events")
    fun rainfallEvents(@PathVariable id: Long, @RequestParam year: Int) =
        data.findRainfallEvents(id, year).map { it.toResponse() }

    // 204 when the station has no observations yet.
    @GetMapping("/{id}/coverage")
    fun coverage(@PathVariable id: Long): ResponseEntity<StationCoverageResponse> =
        data.findCoverage(id)?.let { ResponseEntity.ok(it.toResponse()) } ?: ResponseEntity.noContent().build()

    @GetMapping("/{id}/aggregates")
    fun aggregates(
        @PathVariable id: Long,
        @RequestParam period: String,
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
    ): List<ObservationAggregateResponse> {
        val granularity = AggregationPeriod.entries.firstOrNull { it.name.equals(period.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("period must be one of day, month or year")
        return data.aggregate(id, granularity, from, to).map { it.toResponse() }
    }
}
