package com.gpstracker.application.station

import com.gpstracker.application.observation.StationObservationRepository
import com.gpstracker.domain.erosivity.ErosivityCalculator
import com.gpstracker.domain.erosivity.ErosivityPeriod
import com.gpstracker.domain.erosivity.RainfallEvent
import com.gpstracker.domain.observation.AggregationPeriod
import com.gpstracker.domain.observation.AnnualObservationSummary
import com.gpstracker.domain.observation.HourlyObservation
import com.gpstracker.domain.observation.ObservationAggregate
import com.gpstracker.domain.observation.StationCoverage
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class WeatherStationDataService(
    private val stations: WeatherStationRepository,
    private val observations: StationObservationRepository,
) {
    fun findHourly(id: Long, from: Instant, to: Instant): List<HourlyObservation> {
        stations.findById(id) ?: throw StationNotFoundException(id.toString())
        require(to > from) { "to must be later than from" }
        require(Duration.between(from, to) <= MAX_HOURLY_RANGE) { "Hourly queries are limited to 32 days" }
        return observations.findHourly(id, from, to)
    }

    fun findAnnualSummaries(id: Long): List<AnnualObservationSummary> {
        stations.findById(id) ?: throw StationNotFoundException(id.toString())
        val summaries = observations.findAnnualSummaries(id).associateBy { it.year }
        if (summaries.isEmpty()) return emptyList()
        return (summaries.keys.min()..summaries.keys.max()).map { year ->
            summaries[year] ?: AnnualObservationSummary(year, 0, 0, 0.0)
        }
    }

    fun findRainfallEvents(id: Long, year: Int): List<RainfallEvent> {
        stations.findById(id) ?: throw StationNotFoundException(id.toString())
        require(year in MIN_YEAR..LocalDate.now(ZoneOffset.UTC).year) { "year is outside the available range" }
        val start = LocalDate.of(year, 1, 1).minusDays(1)
        val end = LocalDate.of(year + 1, 1, 1).plusDays(1).minusDays(1)
        val period = ErosivityPeriod(start, end)
        val hours = observations.findPrecipitation(id, period.start, period.endExclusive)
        return ErosivityCalculator().calculate(period, hours).events
            .filter { it.startedAt.plusSeconds(3600).atOffset(ZoneOffset.UTC).year == year }
    }

    // Whole UTC days [from, to]; the range per granularity is capped to bound the response size.
    fun aggregate(id: Long, period: AggregationPeriod, from: LocalDate, to: LocalDate): List<ObservationAggregate> {
        stations.findById(id) ?: throw StationNotFoundException(id.toString())
        require(!to.isBefore(from)) { "to must not be before from" }
        val maxDays = MAX_AGGREGATE_DAYS.getValue(period)
        require(ChronoUnit.DAYS.between(from, to) < maxDays) { "${period.name.lowercase()} aggregates are limited to $maxDays days" }
        val start = from.atStartOfDay(ZoneOffset.UTC).toInstant()
        val end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        return observations.aggregate(id, period, start, end)
    }

    fun findCoverage(id: Long): StationCoverage? {
        stations.findById(id) ?: throw StationNotFoundException(id.toString())
        return observations.findCoverage(id)
    }

    private companion object {
        val MAX_HOURLY_RANGE: Duration = Duration.ofDays(32)
        const val MIN_YEAR = 1900
        val MAX_AGGREGATE_DAYS = mapOf(
            AggregationPeriod.DAY to 3L * 366,
            AggregationPeriod.MONTH to 50L * 366,
            AggregationPeriod.YEAR to 200L * 366,
        )
    }
}