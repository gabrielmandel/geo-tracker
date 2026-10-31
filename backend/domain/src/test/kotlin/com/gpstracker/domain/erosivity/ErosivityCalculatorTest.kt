package com.gpstracker.domain.erosivity

import com.gpstracker.domain.observation.PrecipitationHour
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.log10

class ErosivityCalculatorTest {

    private val year = ErosivityPeriod(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31))

    @Test
    fun `an event of 12_7 mm or more is erosive and EI30 is energy times I60 x factor`() {
        val hours = rain(at("2020-03-01T10:00:00Z"), 10.0, 5.0)

        val result = ErosivityCalculator().calculate(year, hours)

        val event = result.events.single()
        val energy = (0.119 + 0.0873 * log10(10.0)) * 10 + (0.119 + 0.0873 * log10(5.0)) * 5
        assertThat(event.erosive).isTrue()
        assertThat(event.precipitationMm).isEqualTo(15.0)
        assertThat(event.maxHourlyMm).isEqualTo(10.0)
        assertThat(event.kineticEnergyMjHa).isCloseTo(energy, within(0.001))
        assertThat(event.i30MmH).isEqualTo(17.0)
        assertThat(event.ei30).isCloseTo(energy * 17.0, within(0.1))
        assertThat(result.rFactor).isCloseTo(energy * 17.0, within(0.1))
    }

    @Test
    fun `a storm below the threshold is kept as an event but adds no erosivity`() {
        val result = ErosivityCalculator().calculate(year, rain(at("2020-05-01T00:00:00Z"), 6.0, 6.0))

        assertThat(result.events.single().erosive).isFalse()
        assertThat(result.years.single().ei30Sum).isZero()
        assertThat(result.rFactor).isZero()
    }

    @Test
    fun `less than 1_27 mm in the next 6 hours separates storms`() {
        val start = at("2020-06-01T00:00:00Z")
        val hours = rain(start, 20.0) + rain(start.plusSeconds(3 * 3600), 0.5) + rain(start.plusSeconds(4 * 3600), 0.5)

        val events = ErosivityCalculator().calculate(year, hours).events

        // Each 0.5 mm hour is followed by less than 1.27 mm in 6 hours, so each one closes its own storm.
        assertThat(events.map { it.precipitationMm }).containsExactly(20.0, 0.5, 0.5)
        assertThat(events.drop(1).none { it.erosive }).isTrue()
    }

    @Test
    fun `1_27 mm or more inside the 6 hour window keeps the storm going`() {
        val start = at("2020-06-01T00:00:00Z")
        val hours = rain(start, 10.0) + rain(start.plusSeconds(5 * 3600), 1.3)

        val events = ErosivityCalculator().calculate(year, hours).events

        assertThat(events).hasSize(1)
        assertThat(events.single().precipitationMm).isEqualTo(11.3)
    }

    @Test
    fun `unit energy is capped at 0_283 above 76 mm per hour`() {
        val event = ErosivityCalculator().calculate(year, rain(at("2020-02-01T00:00:00Z"), 100.0)).events.single()

        assertThat(event.kineticEnergyMjHa).isCloseTo(28.3, within(0.001))
    }

    @Test
    fun `an event belongs to the UTC year of its first reading`() {
        val period = ErosivityPeriod(LocalDate.of(2019, 1, 1), LocalDate.of(2020, 12, 31))
        val hours = rain(at("2019-12-31T23:00:00Z"), 10.0, 10.0)

        val result = ErosivityCalculator().calculate(period, hours)

        assertThat(result.years.first { it.year == 2019 }.erosiveEvents).isEqualTo(1)
        assertThat(result.years.first { it.year == 2020 }.erosiveEvents).isZero()
    }

    @Test
    fun `a year without readings is excluded and the other years are averaged`() {
        val period = ErosivityPeriod(LocalDate.of(2019, 1, 1), LocalDate.of(2020, 12, 31))
        val hours = rain(at("2020-07-01T00:00:00Z"), 15.0)

        val result = ErosivityCalculator().calculate(period, hours)

        assertThat(result.years.first { it.year == 2019 }.included).isFalse()
        assertThat(result.yearsUsed).isEqualTo(1)
        assertThat(result.rFactor).isEqualTo(result.years.first { it.year == 2020 }.ei30Sum)
    }

    @Test
    fun `the I30 factor is a run parameter`() {
        val hours = rain(at("2020-03-01T10:00:00Z"), 13.0)

        val default = ErosivityCalculator().calculate(year, hours).events.single()
        val i60 = ErosivityCalculator(RusleParameters(i30Factor = 1.0)).calculate(year, hours).events.single()

        assertThat(default.i30MmH / i60.i30MmH).isCloseTo(1.7, within(0.001))
        assertThat(default.ei30 / i60.ei30).isCloseTo(1.7, within(0.01))
    }

    @Test
    fun `presets and parameter bounds`() {
        assertThat(RusleParameters.PRESETS.keys).contains("RUSLE_HOURLY", "RUSLE_HOURLY_I60", "RS_REGIONAL")
        assertThat(runCatching { RusleParameters(i30Factor = 9.0) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { RusleParameters(separationHours = 0) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `period R keeps only years with enough coverage and needs a minimum number of them`() {
        val years = listOf(
            yearOf(2001, coverage = 0.95, ei30 = 4000.0),
            yearOf(2002, coverage = 0.50, ei30 = 100.0),
            yearOf(2003, coverage = 0.85, ei30 = 6000.0),
        )

        assertThat(PeriodErosivity.rFactor(years, 2001, 2003, 0.8, 2)).isEqualTo(PeriodRFactor(5000.0, 2))
        assertThat(PeriodErosivity.rFactor(years, 2001, 2003, 0.8, 3)).isNull()
        assertThat(PeriodErosivity.rFactor(years, 2002, 2002, 0.8, 1)).isNull()
    }

    private fun yearOf(year: Int, coverage: Double, ei30: Double) =
        StationErosivityYear(1, year, 8760, (8760 * coverage).toInt(), 1000.0, 10, ei30)

    private fun at(text: String): Instant = Instant.parse(text).atOffset(ZoneOffset.UTC).toInstant()

    private fun rain(first: Instant, vararg depths: Double) =
        depths.mapIndexed { index, depth -> PrecipitationHour(first.plusSeconds(index * 3600L), depth) }
}
