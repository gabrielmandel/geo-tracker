package com.gpstracker.api.station

import com.gpstracker.domain.erosivity.RainfallEvent
import com.gpstracker.domain.observation.AnnualObservationSummary
import com.gpstracker.domain.observation.HourlyObservation
import com.gpstracker.domain.observation.ObservationAggregate
import com.gpstracker.domain.observation.StationCoverage
import java.time.Instant
import java.time.LocalDate

data class HourlyObservationResponse(
    val observedAt: Instant,
    val precipitationMm: Double?,
    val stationPressureMb: Double?,
    val maxPressureMb: Double?,
    val minPressureMb: Double?,
    val globalRadiationKjM2: Double?,
    val airTemperatureC: Double?,
    val dewPointC: Double?,
    val maxAirTemperatureC: Double?,
    val minAirTemperatureC: Double?,
    val maxDewPointC: Double?,
    val minDewPointC: Double?,
    val maxRelativeHumidityPct: Int?,
    val minRelativeHumidityPct: Int?,
    val relativeHumidityPct: Int?,
    val windDirectionDeg: Int?,
    val windGustMs: Double?,
    val windSpeedMs: Double?,
)

data class AnnualStationDataResponse(
    val year: Int,
    val observationHours: Int,
    val missingPrecipitationHours: Int,
    val precipitationMm: Double,
)

data class StationCoverageResponse(
    val firstObservation: Instant,
    val lastObservation: Instant,
    val observationHours: Long,
    val precipitationHours: Long,
    val precipitationCompleteness: Double,
    val yearsWithData: Int,
)

data class ObservationAggregateResponse(
    val periodStart: LocalDate,
    val observationHours: Int,
    val missingPrecipitationHours: Int,
    val precipitationMm: Double?,
    val maxHourlyPrecipitationMm: Double?,
    val meanAirTemperatureC: Double?,
    val minAirTemperatureC: Double?,
    val maxAirTemperatureC: Double?,
    val meanRelativeHumidityPct: Double?,
    val meanWindSpeedMs: Double?,
    val maxWindGustMs: Double?,
    val globalRadiationKjM2: Double?,
)

fun StationCoverage.toResponse() = StationCoverageResponse(
    firstObservation, lastObservation, observationHours, precipitationHours,
    Math.round(precipitationCompleteness * 1000) / 1000.0, yearsWithData,
)

fun ObservationAggregate.toResponse() = ObservationAggregateResponse(
    periodStart, observationHours, missingPrecipitationHours, precipitationMm, maxHourlyPrecipitationMm,
    meanAirTemperatureC, minAirTemperatureC, maxAirTemperatureC, meanRelativeHumidityPct, meanWindSpeedMs,
    maxWindGustMs, globalRadiationKjM2,
)

fun HourlyObservation.toResponse() = HourlyObservationResponse(
    observedAt, precipitationMm, stationPressureMb, maxPressureMb, minPressureMb, globalRadiationKjM2,
    airTemperatureC, dewPointC, maxAirTemperatureC, minAirTemperatureC, maxDewPointC, minDewPointC,
    maxRelativeHumidityPct, minRelativeHumidityPct, relativeHumidityPct, windDirectionDeg, windGustMs, windSpeedMs,
)

fun AnnualObservationSummary.toResponse() =
    AnnualStationDataResponse(year, observationHours, missingPrecipitationHours, precipitationMm)

fun RainfallEvent.toResponse() = com.gpstracker.api.erosivity.RainfallEventResponse(
    startedAt, endedAt, precipitationMm, maxHourlyMm, missingHours, kineticEnergyMjHa, i30MmH, ei30, erosive,
)