package com.gpstracker.domain.observation

import java.time.Instant

// Values from INMET automatic stations; null means the sensor did not report, never zero.
data class HourlyObservation(
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
) {
    init {
        require(precipitationMm == null || precipitationMm >= 0) { "Precipitation cannot be negative: $precipitationMm" }
    }
}

data class AnnualObservationSummary(
    val year: Int,
    val observationHours: Int,
    val missingPrecipitationHours: Int,
    val precipitationMm: Double,
)
