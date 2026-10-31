package com.gpstracker.domain.erosivity

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// Whole UTC days; the year boundary is UTC and the front end converts to local time.
data class ErosivityPeriod(val from: LocalDate, val to: LocalDate) {
    init {
        require(!to.isBefore(from)) { "periodEnd must not be before periodStart" }
        require(ChronoUnit.YEARS.between(from, to) < MAX_YEARS) { "The period cannot exceed $MAX_YEARS years" }
    }

    val start: Instant get() = from.atStartOfDay(ZoneOffset.UTC).toInstant()

    val endExclusive: Instant get() = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

    private companion object {
        const val MAX_YEARS = 100
    }
}
