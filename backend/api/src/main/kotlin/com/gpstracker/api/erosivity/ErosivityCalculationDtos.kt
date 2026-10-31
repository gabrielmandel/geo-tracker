package com.gpstracker.api.erosivity

import com.fasterxml.jackson.annotation.JsonProperty
import com.gpstracker.application.erosivity.ErosivityReport
import com.gpstracker.domain.erosivity.AnnualErosivity
import com.gpstracker.domain.erosivity.ErosivityCalculation
import com.gpstracker.domain.erosivity.ErosivityCalculator
import com.gpstracker.domain.erosivity.RainfallEvent
import com.gpstracker.domain.erosivity.RusleParameters
import java.time.Instant
import java.time.LocalDate

// Every field is optional: see resolveParameters.
data class ErosivityParametersRequest(
    val separationHours: Int? = null,
    val separationMaxMm: Double? = null,
    val erosiveMinMm: Double? = null,
    val i30Factor: Double? = null,
)

data class ErosivityCalculationRequest(
    val stationCode: String,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val preset: String? = null,
    val parameters: ErosivityParametersRequest? = null,
)

data class ErosivityRerunRequest(val preset: String? = null, val parameters: ErosivityParametersRequest? = null)

data class ParameterPresetResponse(val name: String, val parameters: CalculationParameters)

// Unset fields keep the preset's value; without a preset they keep `base` (the defaults, or the run being repeated).
fun resolveParameters(preset: String?, overrides: ErosivityParametersRequest?, base: RusleParameters = RusleParameters()): RusleParameters {
    val start = preset?.let {
        RusleParameters.PRESETS[it.trim().uppercase()]
            ?: throw IllegalArgumentException("Unknown preset '$it'; available: ${RusleParameters.PRESETS.keys.joinToString()}")
    } ?: base
    if (overrides == null) return start
    return RusleParameters(
        separationHours = overrides.separationHours ?: start.separationHours,
        separationMaxMm = overrides.separationMaxMm ?: start.separationMaxMm,
        erosiveMinMm = overrides.erosiveMinMm ?: start.erosiveMinMm,
        i30Factor = overrides.i30Factor ?: start.i30Factor,
    )
}

data class ErosivityCalculationResponse(
    val id: Long,
    val station: CalculatedStation,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val method: String,
    val methodVersion: Int,
    val parameters: CalculationParameters,
    val status: String,
    // Jackson 2 (used by springdoc) would derive "rfactor" from getRFactor().
    @get:JsonProperty("rFactor")
    val rFactor: Double?,
    val unit: String,
    val yearsUsed: Int,
    val outdated: Boolean,
    val years: List<AnnualErosivityResponse>,
    val createdAt: Instant,
    val finishedAt: Instant?,
)

data class ErosivityCalculationSummaryResponse(
    val id: Long,
    val stationId: Long,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val method: String,
    val methodVersion: Int,
    val parameters: CalculationParameters,
    val status: String,
    @get:JsonProperty("rFactor")
    val rFactor: Double?,
    val unit: String,
    val yearsUsed: Int,
    val outdated: Boolean,
    val createdAt: Instant,
)

data class CalculatedStation(val id: Long, val code: String, val name: String)

data class CalculationParameters(
    val separationHours: Int,
    val separationMaxMm: Double,
    val erosiveMinMm: Double,
    val energyEquation: String,
    val i30Estimate: String,
    val i30Factor: Double,
    val yearBoundary: String,
)

data class AnnualErosivityResponse(
    val year: Int,
    val erosivity: Double,
    val erosiveEvents: Int,
    val precipitationMm: Double,
    val missingHours: Int,
    val included: Boolean,
)

data class RainfallEventResponse(
    val startedAt: Instant,
    val endedAt: Instant,
    val precipitationMm: Double,
    val maxHourlyMm: Double,
    val missingHours: Int,
    val kineticEnergyMjHa: Double,
    val i30MmH: Double,
    val ei30: Double,
    val erosive: Boolean,
)

const val R_UNIT = "MJ mm ha-1 h-1 yr-1"

fun RusleParameters.toResponse() = CalculationParameters(
    separationHours = separationHours,
    separationMaxMm = separationMaxMm,
    erosiveMinMm = erosiveMinMm,
    energyEquation = ErosivityCalculator.ENERGY_EQUATION,
    i30Estimate = ErosivityCalculator.I30_ESTIMATE,
    i30Factor = i30Factor,
    yearBoundary = ErosivityCalculator.YEAR_BOUNDARY,
)

fun ErosivityCalculation.toSummaryResponse() = ErosivityCalculationSummaryResponse(
    id = id,
    stationId = stationId,
    periodStart = period.from,
    periodEnd = period.to,
    method = method,
    methodVersion = methodVersion,
    parameters = parameters.toResponse(),
    status = status.name,
    rFactor = rFactor,
    unit = R_UNIT,
    yearsUsed = yearsUsed,
    outdated = outdated,
    createdAt = createdAt,
)

fun ErosivityReport.toResponse() = ErosivityCalculationResponse(
    id = calculation.id,
    station = CalculatedStation(station.id, station.details.code, station.details.name),
    periodStart = calculation.period.from,
    periodEnd = calculation.period.to,
    method = calculation.method,
    methodVersion = calculation.methodVersion,
    parameters = calculation.parameters.toResponse(),
    status = calculation.status.name,
    rFactor = calculation.rFactor,
    unit = R_UNIT,
    yearsUsed = calculation.yearsUsed,
    outdated = calculation.outdated,
    years = years.map { it.toResponse() },
    createdAt = calculation.createdAt,
    finishedAt = calculation.finishedAt,
)

private fun AnnualErosivity.toResponse() = AnnualErosivityResponse(
    year = year,
    erosivity = ei30Sum,
    erosiveEvents = erosiveEvents,
    precipitationMm = precipitationMm,
    missingHours = missingHours,
    included = included,
)

fun RainfallEvent.toResponse() = RainfallEventResponse(
    startedAt = startedAt,
    endedAt = endedAt,
    precipitationMm = precipitationMm,
    maxHourlyMm = maxHourlyMm,
    missingHours = missingHours,
    kineticEnergyMjHa = kineticEnergyMjHa,
    i30MmH = i30MmH,
    ei30 = ei30,
    erosive = erosive,
)
