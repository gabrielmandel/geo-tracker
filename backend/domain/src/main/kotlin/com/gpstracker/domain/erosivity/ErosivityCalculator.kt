package com.gpstracker.domain.erosivity

import com.gpstracker.domain.observation.PrecipitationHour
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

// R factor from hourly data: RUSLE storm rules with Wischmeier & Smith (1978) rainfall energy.
class ErosivityCalculator(private val parameters: RusleParameters = RusleParameters()) {

    fun calculate(period: ErosivityPeriod, hours: List<PrecipitationHour>): ErosivityResult {
        val series = HourlySeries(period, hours)
        val events = findEvents(series)
        val years = summarizeYears(period, series, events)
        val included = years.filter { it.included }
        val rFactor = if (included.isEmpty()) null else round(included.sumOf { it.ei30Sum } / included.size, 1)
        return ErosivityResult(METHOD, VERSION, parameters, events.map { it.event }, years, rFactor)
    }

    private fun findEvents(series: HourlySeries): List<YearEvent> {
        val events = mutableListOf<YearEvent>()
        var index = 0
        while (index < series.size) {
            if (series.depth[index] <= 0.0) {
                index++
                continue
            }
            val first = index
            var last = index
            // The storm continues while the next 6 hours bring at least 1.27 mm; otherwise those hours separate storms.
            while (last + 1 < series.size &&
                series.sum(last + 1, last + parameters.separationHours) >= parameters.separationMaxMm
            ) {
                last = series.nextRainyHour(last + 1)
            }
            events += YearEvent(series.readingTime(first).atOffset(ZoneOffset.UTC).year, buildEvent(series, first, last))
            index = last + 1
        }
        return events
    }

    private fun buildEvent(series: HourlySeries, first: Int, last: Int): RainfallEvent {
        var total = 0.0
        var peak = 0.0
        var energy = 0.0
        var missing = 0
        for (i in first..last) {
            if (series.missing[i]) missing++
            val depth = series.depth[i]
            if (depth > 0.0) {
                total += depth
                peak = max(peak, depth)
                // An hourly depth in mm is also that hour's intensity in mm/h.
                energy += unitEnergy(depth) * depth
            }
        }
        val i30 = peak * parameters.i30Factor
        val precipitation = round(total, 1)
        return RainfallEvent(
            startedAt = series.readingTime(first).minus(1, ChronoUnit.HOURS),
            endedAt = series.readingTime(last),
            precipitationMm = precipitation,
            maxHourlyMm = round(peak, 1),
            missingHours = missing,
            kineticEnergyMjHa = round(energy, 3),
            i30MmH = round(i30, 1),
            ei30 = round(energy * i30, 1),
            erosive = precipitation >= parameters.erosiveMinMm,
        )
    }

    private fun summarizeYears(period: ErosivityPeriod, series: HourlySeries, events: List<YearEvent>): List<AnnualErosivity> {
        val erosiveByYear = events.filter { it.event.erosive }.groupBy({ it.year }, { it.event })
        return (period.from.year..period.to.year).map { year ->
            val from = max(0, series.indexOf(yearStart(year)))
            val to = min(series.size, series.indexOf(yearStart(year + 1)))
            var precipitation = 0.0
            var hoursWithData = 0
            for (i in from until to) {
                if (!series.missing[i]) {
                    precipitation += series.depth[i]
                    hoursWithData++
                }
            }
            val erosive = erosiveByYear[year].orEmpty()
            AnnualErosivity(
                year = year,
                precipitationMm = round(precipitation, 1),
                missingHours = (to - from) - hoursWithData,
                erosiveEvents = erosive.size,
                ei30Sum = round(erosive.sumOf { it.ei30 }, 1),
                // A year without any precipitation record would count as a year without rain.
                included = hoursWithData > 0,
            )
        }
    }

    // MJ ha-1 mm-1 in SI units (Foster et al. 1981); constant above 76 mm/h.
    private fun unitEnergy(intensityMmH: Double) =
        if (intensityMmH > 76.0) 0.283 else max(0.0, 0.119 + 0.0873 * log10(intensityMmH))

    private fun yearStart(year: Int): Instant = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()

    private fun round(value: Double, decimals: Int) =
        BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toDouble()

    companion object {
        const val METHOD = "RUSLE_HOURLY"
        const val VERSION = 1
        const val ENERGY_EQUATION = "WISCHMEIER_SMITH_1978"
        const val I30_ESTIMATE = "I60_X_FACTOR"
        const val YEAR_BOUNDARY = "UTC"
    }
}

// Events belong to the year of their first reading, as INMET groups readings into annual files.
private class YearEvent(val year: Int, val event: RainfallEvent)

// Dense hourly series; index 0 is the reading at period.start. Each reading is the rain of the hour ending at its time.
// Hours without a value count as dry.
private class HourlySeries(period: ErosivityPeriod, hours: List<PrecipitationHour>) {
    private val start = period.start
    val size = Duration.between(period.start, period.endExclusive).toHours().toInt()
    val depth = DoubleArray(size)
    val missing = BooleanArray(size) { true }
    private val prefix = DoubleArray(size + 1)

    init {
        for (hour in hours) {
            val value = hour.precipitationMm ?: continue
            val index = indexOf(hour.observedAt)
            if (index in 0 until size) {
                depth[index] = value
                missing[index] = false
            }
        }
        for (i in 0 until size) prefix[i + 1] = prefix[i] + depth[i]
    }

    fun sum(from: Int, to: Int): Double = prefix[min(to, size - 1) + 1] - prefix[from]

    fun nextRainyHour(from: Int): Int {
        var index = from
        while (depth[index] <= 0.0) index++
        return index
    }

    fun readingTime(index: Int): Instant = start.plus(index.toLong(), ChronoUnit.HOURS)

    fun indexOf(instant: Instant): Int = Duration.between(start, instant).toHours().toInt()
}
