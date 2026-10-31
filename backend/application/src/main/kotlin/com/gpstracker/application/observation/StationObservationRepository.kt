package com.gpstracker.application.observation

import com.gpstracker.domain.observation.AggregationPeriod
import com.gpstracker.domain.observation.HourlyObservation
import com.gpstracker.domain.observation.AnnualObservationSummary
import com.gpstracker.domain.observation.ObservationAggregate
import com.gpstracker.domain.observation.PrecipitationHour
import com.gpstracker.domain.observation.StationCoverage
import java.time.Instant

interface StationObservationRepository {
    // Inserts new hours and updates changed ones, unless the stored hour came from a file whose data ends later.
    // Returns how many rows were inserted or updated.
    fun upsert(stationId: Long, importId: Long, filePeriodEnd: Instant, observations: List<HourlyObservation>): Int

    // Readings timestamped in [from, to), in time order; each reading covers the hour ending at its timestamp.
    fun findPrecipitation(stationId: Long, from: Instant, to: Instant): List<PrecipitationHour>

    fun findHourly(stationId: Long, from: Instant, to: Instant): List<HourlyObservation>

    fun findAnnualSummaries(stationId: Long): List<AnnualObservationSummary>

    // Readings in [from, to) grouped by UTC day, month or year of their timestamp.
    fun aggregate(stationId: Long, period: AggregationPeriod, from: Instant, to: Instant): List<ObservationAggregate>

    fun findCoverage(stationId: Long): StationCoverage?
}
