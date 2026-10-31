package com.gpstracker.domain.erosivity

// One UTC year of a station computed with the map's parameter set; coverage = hours with a precipitation reading.
data class StationErosivityYear(
    val stationId: Long,
    val year: Int,
    val hoursInYear: Int,
    val precipitationHours: Int,
    val precipitationMm: Double,
    val erosiveEvents: Int,
    val ei30Sum: Double,
) {
    val coverage: Double get() = if (hoursInYear == 0) 0.0 else precipitationHours.toDouble() / hoursInYear
}

data class PeriodRFactor(val rFactor: Double, val yearsUsed: Int)

// Quality rule for maps: only years with enough readings count, and a station needs enough such years.
object PeriodErosivity {
    fun rFactor(years: List<StationErosivityYear>, fromYear: Int, toYear: Int, minCoverage: Double, minYears: Int): PeriodRFactor? {
        val usable = years.filter { it.year in fromYear..toYear && it.coverage >= minCoverage }
        if (usable.isEmpty() || usable.size < minYears) return null
        return PeriodRFactor(usable.sumOf { it.ei30Sum } / usable.size, usable.size)
    }
}

// Identifies the parameter set behind cached years; a different key means the cache must be rebuilt.
fun RusleParameters.cacheKey(): String =
    "${ErosivityCalculator.METHOD}:v${ErosivityCalculator.VERSION}:$separationHours:$separationMaxMm:$erosiveMinMm:$i30Factor"
