package com.gpstracker.infrastructure.erosivity

import com.gpstracker.application.erosivity.ErosivityCalculationRepository
import com.gpstracker.domain.erosivity.AnnualErosivity
import com.gpstracker.domain.erosivity.CalculationStatus
import com.gpstracker.domain.erosivity.ErosivityCalculation
import com.gpstracker.domain.erosivity.ErosivityCalculator
import com.gpstracker.domain.erosivity.ErosivityPeriod
import com.gpstracker.domain.erosivity.ErosivityResult
import com.gpstracker.domain.erosivity.RainfallEvent
import com.gpstracker.domain.erosivity.RusleParameters
import com.gpstracker.infrastructure.shared.JdbcRepository
import com.gpstracker.infrastructure.shared.getInstant
import com.gpstracker.infrastructure.shared.getNullableDouble
import com.gpstracker.infrastructure.shared.toUtc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDate

@Repository
class JdbcErosivityCalculationRepository(jdbc: JdbcClient, private val batch: JdbcTemplate) :
    JdbcRepository<ErosivityCalculation, Long>(jdbc, "erosivity_calculations"),
    ErosivityCalculationRepository {

    override val columns = """
        *,
        (parameters ->> 'separationHours')::int AS separation_hours,
        (parameters ->> 'separationMaxMm')::numeric AS separation_max_mm,
        (parameters ->> 'erosiveMinMm')::numeric AS erosive_min_mm,
        (parameters ->> 'i30Factor')::numeric AS i30_factor,
        EXISTS (
            SELECT 1 FROM station_imports si
            WHERE si.station_id = erosivity_calculations.station_id
              AND si.status = 'COMPLETED'
              AND si.finished_at > erosivity_calculations.created_at
              AND si.period_start < ((erosivity_calculations.period_end + 1)::timestamp AT TIME ZONE 'UTC')
              AND si.period_end >= (erosivity_calculations.period_start::timestamp AT TIME ZONE 'UTC')
        ) AS outdated
    """

    override fun findByStation(stationId: Long): List<ErosivityCalculation> =
        jdbc.sql("SELECT $columns FROM erosivity_calculations WHERE station_id = :stationId ORDER BY created_at DESC, id DESC")
            .param("stationId", stationId)
            .mapList()

    override fun create(stationId: Long, period: ErosivityPeriod, result: ErosivityResult): ErosivityCalculation {
        val parameters = result.parameters
        val calculation = jdbc.sql(
            """
            INSERT INTO erosivity_calculations
                (station_id, period_start, period_end, method, method_version, parameters, status, r_factor, years_used, finished_at)
            VALUES (:stationId, :periodStart, :periodEnd, :method, :methodVersion,
                    jsonb_build_object(
                        'separationHours', CAST(:separationHours AS int),
                        'separationMaxMm', CAST(:separationMaxMm AS numeric),
                        'erosiveMinMm', CAST(:erosiveMinMm AS numeric),
                        'i30Factor', CAST(:i30Factor AS numeric),
                        'i30Estimate', CAST(:i30Estimate AS text),
                        'energyEquation', CAST(:energyEquation AS text),
                        'yearBoundary', CAST(:yearBoundary AS text)),
                    'COMPLETED', :rFactor, :yearsUsed, now())
            RETURNING $columns
            """,
        )
            .param("stationId", stationId)
            .param("periodStart", period.from)
            .param("periodEnd", period.to)
            .param("method", result.method)
            .param("methodVersion", result.methodVersion)
            .param("separationHours", parameters.separationHours)
            .param("separationMaxMm", parameters.separationMaxMm)
            .param("erosiveMinMm", parameters.erosiveMinMm)
            .param("i30Factor", parameters.i30Factor)
            .param("i30Estimate", ErosivityCalculator.I30_ESTIMATE)
            .param("energyEquation", ErosivityCalculator.ENERGY_EQUATION)
            .param("yearBoundary", ErosivityCalculator.YEAR_BOUNDARY)
            .param("rFactor", result.rFactor, Types.NUMERIC)
            .param("yearsUsed", result.yearsUsed)
            .mapSingle()

        batch.batchUpdate(INSERT_EVENT, result.events, BATCH_SIZE) { ps, event ->
            ps.setLong(1, calculation.id)
            ps.setObject(2, event.startedAt.toUtc())
            ps.setObject(3, event.endedAt.toUtc())
            ps.setBigDecimal(4, BigDecimal.valueOf(event.precipitationMm))
            ps.setBigDecimal(5, BigDecimal.valueOf(event.maxHourlyMm))
            ps.setInt(6, event.missingHours)
            ps.setBigDecimal(7, BigDecimal.valueOf(event.kineticEnergyMjHa))
            ps.setBigDecimal(8, BigDecimal.valueOf(event.i30MmH))
            ps.setBigDecimal(9, BigDecimal.valueOf(event.ei30))
            ps.setBoolean(10, event.erosive)
        }
        batch.batchUpdate(INSERT_YEAR, result.years, BATCH_SIZE) { ps, year ->
            ps.setLong(1, calculation.id)
            ps.setInt(2, year.year)
            ps.setBigDecimal(3, BigDecimal.valueOf(year.precipitationMm))
            ps.setInt(4, year.missingHours)
            ps.setInt(5, year.erosiveEvents)
            ps.setBigDecimal(6, BigDecimal.valueOf(year.ei30Sum))
            ps.setBoolean(7, year.included)
        }
        return calculation
    }

    override fun save(entity: ErosivityCalculation): ErosivityCalculation {
        jdbc.sql(
            """
            UPDATE erosivity_calculations
            SET status = :status, r_factor = :rFactor, years_used = :yearsUsed, finished_at = :finishedAt
            WHERE id = :id
            """,
        )
            .param("id", entity.id)
            .param("status", entity.status.name)
            .param("rFactor", entity.rFactor, Types.NUMERIC)
            .param("yearsUsed", entity.yearsUsed)
            .param("finishedAt", entity.finishedAt?.toUtc(), Types.TIMESTAMP_WITH_TIMEZONE)
            .update()
        return entity
    }

    override fun findAnnualResults(calculationId: Long): List<AnnualErosivity> =
        jdbc.sql("SELECT * FROM erosivity_annual_results WHERE calculation_id = :id ORDER BY year")
            .param("id", calculationId)
            .query { rs, _ ->
                AnnualErosivity(
                    year = rs.getInt("year"),
                    precipitationMm = rs.getBigDecimal("precipitation_mm").toDouble(),
                    missingHours = rs.getInt("missing_hours"),
                    erosiveEvents = rs.getInt("erosive_events"),
                    ei30Sum = rs.getBigDecimal("ei30_sum").toDouble(),
                    included = rs.getBoolean("included"),
                )
            }
            .list()

    override fun findEvents(calculationId: Long): List<RainfallEvent> =
        jdbc.sql("SELECT * FROM erosivity_events WHERE calculation_id = :id ORDER BY started_at")
            .param("id", calculationId)
            .query { rs, _ ->
                RainfallEvent(
                    startedAt = checkNotNull(rs.getInstant("started_at")),
                    endedAt = checkNotNull(rs.getInstant("ended_at")),
                    precipitationMm = rs.getBigDecimal("precipitation_mm").toDouble(),
                    maxHourlyMm = rs.getBigDecimal("max_hourly_mm").toDouble(),
                    missingHours = rs.getInt("missing_hours"),
                    kineticEnergyMjHa = rs.getBigDecimal("kinetic_energy_mj_ha").toDouble(),
                    i30MmH = rs.getBigDecimal("i30_mm_h").toDouble(),
                    ei30 = rs.getBigDecimal("ei30").toDouble(),
                    erosive = rs.getBoolean("erosive"),
                )
            }
            .list()

    override fun mapRow(rs: ResultSet) = ErosivityCalculation(
        id = rs.getLong("id"),
        stationId = rs.getLong("station_id"),
        period = ErosivityPeriod(
            rs.getObject("period_start", LocalDate::class.java),
            rs.getObject("period_end", LocalDate::class.java),
        ),
        method = rs.getString("method"),
        methodVersion = rs.getInt("method_version"),
        parameters = RusleParameters(
            separationHours = rs.getInt("separation_hours"),
            separationMaxMm = rs.getBigDecimal("separation_max_mm").toDouble(),
            erosiveMinMm = rs.getBigDecimal("erosive_min_mm").toDouble(),
            i30Factor = rs.getBigDecimal("i30_factor").toDouble(),
        ),
        status = CalculationStatus.valueOf(rs.getString("status")),
        rFactor = rs.getNullableDouble("r_factor"),
        yearsUsed = rs.getInt("years_used"),
        createdAt = checkNotNull(rs.getInstant("created_at")),
        finishedAt = rs.getInstant("finished_at"),
        outdated = rs.getBoolean("outdated"),
    )

    private companion object {
        const val BATCH_SIZE = 1000

        const val INSERT_EVENT = """
            INSERT INTO erosivity_events (calculation_id, started_at, ended_at, precipitation_mm, max_hourly_mm,
                                          missing_hours, kinetic_energy_mj_ha, i30_mm_h, ei30, erosive)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        const val INSERT_YEAR = """
            INSERT INTO erosivity_annual_results (calculation_id, year, precipitation_mm, missing_hours,
                                                  erosive_events, ei30_sum, included)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """
    }
}
