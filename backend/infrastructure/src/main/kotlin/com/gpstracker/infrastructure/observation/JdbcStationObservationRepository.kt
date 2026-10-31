package com.gpstracker.infrastructure.observation

import com.gpstracker.application.observation.StationObservationRepository
import com.gpstracker.domain.observation.AggregationPeriod
import com.gpstracker.domain.observation.AnnualObservationSummary
import com.gpstracker.domain.observation.HourlyObservation
import com.gpstracker.domain.observation.ObservationAggregate
import com.gpstracker.domain.observation.StationCoverage
import com.gpstracker.domain.observation.PrecipitationHour
import com.gpstracker.infrastructure.shared.getInstant
import com.gpstracker.infrastructure.shared.getNullableDouble
import com.gpstracker.infrastructure.shared.toUtc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
class JdbcStationObservationRepository(private val jdbc: JdbcTemplate) : StationObservationRepository {

    override fun upsert(stationId: Long, importId: Long, filePeriodEnd: Instant, observations: List<HourlyObservation>): Int {
        val periodEnd = filePeriodEnd.toUtc()
        // One statement cannot touch the same row twice, so a repeated hour keeps its last line (as a row-by-row upsert would).
        val unique = observations.associateBy { it.observedAt }.values.toList()
        // Set-based: each chunk is sent as one array per column and unnested by PostgreSQL (much faster than row batches).
        // The update count covers inserted and updated rows; skipped updates are not counted.
        return unique.chunked(CHUNK_SIZE).sumOf { chunk ->
            jdbc.update(UPSERT) { ps ->
                val connection = ps.connection
                ps.setLong(1, stationId)
                ps.setLong(2, importId)
                ps.setArray(3, connection.createArrayOf("text", chunk.map { it.observedAt.toString() }.toTypedArray()))
                MEASUREMENTS.forEachIndexed { index, (_, value) ->
                    val texts = chunk.map { observation -> value(observation)?.toString() }.toTypedArray()
                    ps.setArray(4 + index, connection.createArrayOf("text", texts))
                }
                ps.setObject(4 + MEASUREMENTS.size, periodEnd)
            }
        }
    }

    override fun findPrecipitation(stationId: Long, from: Instant, to: Instant): List<PrecipitationHour> =
        jdbc.query(
            """
            SELECT observed_at, precipitation_mm FROM station_observations
            WHERE station_id = ? AND observed_at >= ? AND observed_at < ?
            ORDER BY observed_at
            """,
            { rs, _ -> PrecipitationHour(checkNotNull(rs.getInstant("observed_at")), rs.getNullableDouble("precipitation_mm")) },
            stationId,
            from.toUtc(),
            to.toUtc(),
        )

    override fun findHourly(stationId: Long, from: Instant, to: Instant): List<HourlyObservation> =
        jdbc.query(
            """
            SELECT observed_at, ${COLUMNS.joinToString()} FROM station_observations
            WHERE station_id = ? AND observed_at >= ? AND observed_at < ?
            ORDER BY observed_at
            """,
            { rs, _ ->
                fun number(column: String) = rs.getNullableDouble(column)
                fun integer(column: String) = (rs.getObject(column) as? Number)?.toInt()
                HourlyObservation(
                    observedAt = checkNotNull(rs.getInstant("observed_at")),
                    precipitationMm = number("precipitation_mm"),
                    stationPressureMb = number("station_pressure_mb"),
                    maxPressureMb = number("max_pressure_mb"),
                    minPressureMb = number("min_pressure_mb"),
                    globalRadiationKjM2 = number("global_radiation_kj_m2"),
                    airTemperatureC = number("air_temperature_c"),
                    dewPointC = number("dew_point_c"),
                    maxAirTemperatureC = number("max_air_temperature_c"),
                    minAirTemperatureC = number("min_air_temperature_c"),
                    maxDewPointC = number("max_dew_point_c"),
                    minDewPointC = number("min_dew_point_c"),
                    maxRelativeHumidityPct = integer("max_relative_humidity_pct"),
                    minRelativeHumidityPct = integer("min_relative_humidity_pct"),
                    relativeHumidityPct = integer("relative_humidity_pct"),
                    windDirectionDeg = integer("wind_direction_deg"),
                    windGustMs = number("wind_gust_ms"),
                    windSpeedMs = number("wind_speed_ms"),
                )
            },
            stationId,
            from.toUtc(),
            to.toUtc(),
        )

    override fun findAnnualSummaries(stationId: Long): List<AnnualObservationSummary> =
        jdbc.query(
            """
            SELECT EXTRACT(YEAR FROM observed_at AT TIME ZONE 'UTC')::int AS year,
                   COUNT(*)::int AS observation_hours,
                   COUNT(*) FILTER (WHERE precipitation_mm IS NULL)::int AS missing_precipitation_hours,
                   COALESCE(SUM(precipitation_mm), 0)::float8 AS precipitation_mm
            FROM station_observations
            WHERE station_id = ?
            GROUP BY year
            ORDER BY year
            """,
            { rs, _ ->
                AnnualObservationSummary(
                    rs.getInt("year"),
                    rs.getInt("observation_hours"),
                    rs.getInt("missing_precipitation_hours"),
                    rs.getDouble("precipitation_mm"),
                )
            },
            stationId,
        )

    override fun aggregate(stationId: Long, period: AggregationPeriod, from: Instant, to: Instant): List<ObservationAggregate> =
        jdbc.query(
            """
            SELECT date_trunc(CAST(? AS text), observed_at AT TIME ZONE 'UTC')::date AS period_start,
                   COUNT(*)::int AS observation_hours,
                   COUNT(*) FILTER (WHERE precipitation_mm IS NULL)::int AS missing_precipitation_hours,
                   ROUND(SUM(precipitation_mm), 1)::float8 AS precipitation_mm,
                   MAX(precipitation_mm)::float8 AS max_hourly_precipitation_mm,
                   ROUND(AVG(air_temperature_c), 1)::float8 AS mean_air_temperature_c,
                   MIN(min_air_temperature_c)::float8 AS min_air_temperature_c,
                   MAX(max_air_temperature_c)::float8 AS max_air_temperature_c,
                   ROUND(AVG(relative_humidity_pct), 1)::float8 AS mean_relative_humidity_pct,
                   ROUND(AVG(wind_speed_ms), 1)::float8 AS mean_wind_speed_ms,
                   MAX(wind_gust_ms)::float8 AS max_wind_gust_ms,
                   ROUND(SUM(global_radiation_kj_m2), 1)::float8 AS global_radiation_kj_m2
            FROM station_observations
            WHERE station_id = ? AND observed_at >= ? AND observed_at < ?
            GROUP BY 1
            ORDER BY 1
            """,
            { rs, _ ->
                fun number(column: String) = (rs.getObject(column) as Number?)?.toDouble()
                ObservationAggregate(
                    periodStart = rs.getObject("period_start", LocalDate::class.java),
                    observationHours = rs.getInt("observation_hours"),
                    missingPrecipitationHours = rs.getInt("missing_precipitation_hours"),
                    precipitationMm = number("precipitation_mm"),
                    maxHourlyPrecipitationMm = number("max_hourly_precipitation_mm"),
                    meanAirTemperatureC = number("mean_air_temperature_c"),
                    minAirTemperatureC = number("min_air_temperature_c"),
                    maxAirTemperatureC = number("max_air_temperature_c"),
                    meanRelativeHumidityPct = number("mean_relative_humidity_pct"),
                    meanWindSpeedMs = number("mean_wind_speed_ms"),
                    maxWindGustMs = number("max_wind_gust_ms"),
                    globalRadiationKjM2 = number("global_radiation_kj_m2"),
                )
            },
            period.name.lowercase(),
            stationId,
            from.toUtc(),
            to.toUtc(),
        )

    override fun findCoverage(stationId: Long): StationCoverage? =
        jdbc.query(
            """
            SELECT MIN(observed_at) AS first_observation, MAX(observed_at) AS last_observation,
                   COUNT(*) AS observation_hours, COUNT(precipitation_mm) AS precipitation_hours,
                   COUNT(DISTINCT EXTRACT(YEAR FROM observed_at AT TIME ZONE 'UTC'))::int AS years_with_data
            FROM station_observations
            WHERE station_id = ?
            """,
            { rs, _ ->
                rs.getInstant("first_observation")?.let { first ->
                    StationCoverage(
                        firstObservation = first,
                        lastObservation = checkNotNull(rs.getInstant("last_observation")),
                        observationHours = rs.getLong("observation_hours"),
                        precipitationHours = rs.getLong("precipitation_hours"),
                        yearsWithData = rs.getInt("years_with_data"),
                    )
                }
            },
            stationId,
        ).firstOrNull()

    private companion object {
        const val CHUNK_SIZE = 10_000

        val MEASUREMENTS: List<Pair<String, (HourlyObservation) -> Number?>> = listOf(
            "precipitation_mm" to HourlyObservation::precipitationMm,
            "station_pressure_mb" to HourlyObservation::stationPressureMb,
            "max_pressure_mb" to HourlyObservation::maxPressureMb,
            "min_pressure_mb" to HourlyObservation::minPressureMb,
            "global_radiation_kj_m2" to HourlyObservation::globalRadiationKjM2,
            "air_temperature_c" to HourlyObservation::airTemperatureC,
            "dew_point_c" to HourlyObservation::dewPointC,
            "max_air_temperature_c" to HourlyObservation::maxAirTemperatureC,
            "min_air_temperature_c" to HourlyObservation::minAirTemperatureC,
            "max_dew_point_c" to HourlyObservation::maxDewPointC,
            "min_dew_point_c" to HourlyObservation::minDewPointC,
            "max_relative_humidity_pct" to HourlyObservation::maxRelativeHumidityPct,
            "min_relative_humidity_pct" to HourlyObservation::minRelativeHumidityPct,
            "relative_humidity_pct" to HourlyObservation::relativeHumidityPct,
            "wind_direction_deg" to HourlyObservation::windDirectionDeg,
            "wind_gust_ms" to HourlyObservation::windGustMs,
            "wind_speed_ms" to HourlyObservation::windSpeedMs,
        )

        val COLUMNS = MEASUREMENTS.map { it.first }

        // Changed hours are updated unless the stored value came from a file whose data ends later
        // (INMET revises recent hours, so the export covering the longer period is the newer one).
        val UPSERT = """
            INSERT INTO station_observations (station_id, observed_at, import_id, ${COLUMNS.joinToString()})
            SELECT ?, incoming.observed_at, ?, ${COLUMNS.joinToString { "incoming.$it" }}
            FROM unnest(CAST(? AS timestamptz[]), ${COLUMNS.joinToString { "CAST(? AS numeric[])" }})
                AS incoming (observed_at, ${COLUMNS.joinToString()})
            ON CONFLICT (station_id, observed_at) DO UPDATE
            SET import_id = EXCLUDED.import_id, ${COLUMNS.joinToString { "$it = EXCLUDED.$it" }}
            WHERE (${COLUMNS.joinToString { "station_observations.$it" }})
                  IS DISTINCT FROM (${COLUMNS.joinToString { "EXCLUDED.$it" }})
              AND NOT EXISTS (
                  SELECT 1 FROM station_imports src
                  WHERE src.id = station_observations.import_id AND src.period_end > ?)
        """
    }
}
