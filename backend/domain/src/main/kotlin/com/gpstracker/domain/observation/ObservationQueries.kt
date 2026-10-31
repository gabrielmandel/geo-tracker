package com.gpstracker.domain.observation

import java.time.Instant
import java.time.LocalDate

// Catalog of the hourly variables stored per station (one column each in station_observations).
enum class ObservationVariable(val column: String, val unit: String, val description: String, val inmetHeader: String) {
    PRECIPITATION("precipitation_mm", "mm", "Total precipitation in the hour", "PRECIPITAÇÃO TOTAL, HORÁRIO (mm)"),
    STATION_PRESSURE("station_pressure_mb", "mB", "Atmospheric pressure at station level", "PRESSAO ATMOSFERICA AO NIVEL DA ESTACAO, HORARIA (mB)"),
    MAX_PRESSURE("max_pressure_mb", "mB", "Maximum pressure in the previous hour", "PRESSÃO ATMOSFERICA MAX.NA HORA ANT. (AUT) (mB)"),
    MIN_PRESSURE("min_pressure_mb", "mB", "Minimum pressure in the previous hour", "PRESSÃO ATMOSFERICA MIN. NA HORA ANT. (AUT) (mB)"),
    GLOBAL_RADIATION("global_radiation_kj_m2", "kJ/m²", "Global radiation", "RADIACAO GLOBAL (Kj/m²)"),
    AIR_TEMPERATURE("air_temperature_c", "°C", "Dry-bulb air temperature", "TEMPERATURA DO AR - BULBO SECO, HORARIA (°C)"),
    DEW_POINT("dew_point_c", "°C", "Dew point temperature", "TEMPERATURA DO PONTO DE ORVALHO (°C)"),
    MAX_AIR_TEMPERATURE("max_air_temperature_c", "°C", "Maximum temperature in the previous hour", "TEMPERATURA MÁXIMA NA HORA ANT. (AUT) (°C)"),
    MIN_AIR_TEMPERATURE("min_air_temperature_c", "°C", "Minimum temperature in the previous hour", "TEMPERATURA MÍNIMA NA HORA ANT. (AUT) (°C)"),
    MAX_DEW_POINT("max_dew_point_c", "°C", "Maximum dew point in the previous hour", "TEMPERATURA ORVALHO MAX. NA HORA ANT. (AUT) (°C)"),
    MIN_DEW_POINT("min_dew_point_c", "°C", "Minimum dew point in the previous hour", "TEMPERATURA ORVALHO MIN. NA HORA ANT. (AUT) (°C)"),
    MAX_RELATIVE_HUMIDITY("max_relative_humidity_pct", "%", "Maximum relative humidity in the previous hour", "UMIDADE REL. MAX. NA HORA ANT. (AUT) (%)"),
    MIN_RELATIVE_HUMIDITY("min_relative_humidity_pct", "%", "Minimum relative humidity in the previous hour", "UMIDADE REL. MIN. NA HORA ANT. (AUT) (%)"),
    RELATIVE_HUMIDITY("relative_humidity_pct", "%", "Relative humidity", "UMIDADE RELATIVA DO AR, HORARIA (%)"),
    WIND_DIRECTION("wind_direction_deg", "°", "Wind direction", "VENTO, DIREÇÃO HORARIA (gr) (° (gr))"),
    WIND_GUST("wind_gust_ms", "m/s", "Maximum wind gust", "VENTO, RAJADA MAXIMA (m/s)"),
    WIND_SPEED("wind_speed_ms", "m/s", "Hourly wind speed", "VENTO, VELOCIDADE HORARIA (m/s)"),
}

enum class AggregationPeriod { DAY, MONTH, YEAR }

// One UTC day, month or year of hourly readings; values are null when no hour reported the variable.
data class ObservationAggregate(
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

data class StationCoverage(
    val firstObservation: Instant,
    val lastObservation: Instant,
    val observationHours: Long,
    val precipitationHours: Long,
    val yearsWithData: Int,
) {
    // Share of the hours between the first and last reading that have a precipitation value.
    val precipitationCompleteness: Double
        get() {
            val span = java.time.Duration.between(firstObservation, lastObservation).toHours() + 1
            return if (span <= 0) 0.0 else precipitationHours.toDouble() / span
        }
}
