package com.gpstracker.infrastructure.erosivity

import com.gpstracker.application.erosivity.CacheRefresh
import com.gpstracker.application.erosivity.CacheRefreshRepository
import com.gpstracker.application.erosivity.CacheStatus
import com.gpstracker.application.erosivity.ErosivityYearCache
import com.gpstracker.application.erosivity.RefreshStatus
import com.gpstracker.domain.erosivity.StationErosivityYear
import com.gpstracker.infrastructure.shared.getInstant
import com.gpstracker.infrastructure.shared.toUtc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant

@Repository
class JdbcErosivityYearCache(private val jdbc: JdbcClient, private val batch: JdbcTemplate) : ErosivityYearCache {

    override fun replace(parametersKey: String, stationId: Long, years: List<StationErosivityYear>, computedAt: Instant) {
        jdbc.sql("DELETE FROM erosivity_station_years WHERE parameters_key = :key AND station_id = :stationId")
            .param("key", parametersKey)
            .param("stationId", stationId)
            .update()
        jdbc.sql(
            """
            INSERT INTO erosivity_station_refreshes (parameters_key, station_id, computed_at)
            VALUES (:key, :stationId, :computedAt)
            ON CONFLICT (parameters_key, station_id) DO UPDATE SET computed_at = EXCLUDED.computed_at
            """,
        )
            .param("key", parametersKey)
            .param("stationId", stationId)
            .param("computedAt", computedAt.toUtc())
            .update()
        batch.batchUpdate(
            """
            INSERT INTO erosivity_station_years (parameters_key, station_id, year, hours_in_year, precipitation_hours,
                                                 precipitation_mm, erosive_events, ei30_sum)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            years,
            years.size.coerceAtLeast(1),
        ) { ps, year ->
            ps.setString(1, parametersKey)
            ps.setLong(2, stationId)
            ps.setInt(3, year.year)
            ps.setInt(4, year.hoursInYear)
            ps.setInt(5, year.precipitationHours)
            ps.setBigDecimal(6, BigDecimal.valueOf(year.precipitationMm))
            ps.setInt(7, year.erosiveEvents)
            ps.setBigDecimal(8, BigDecimal.valueOf(year.ei30Sum))
        }
    }

    override fun findYears(parametersKey: String, fromYear: Int, toYear: Int): List<StationErosivityYear> =
        jdbc.sql(
            """
            SELECT * FROM erosivity_station_years
            WHERE parameters_key = :key AND year BETWEEN :fromYear AND :toYear
            ORDER BY station_id, year
            """,
        )
            .param("key", parametersKey)
            .param("fromYear", fromYear)
            .param("toYear", toYear)
            .query { rs, _ -> mapYear(rs) }
            .list()

    override fun findStaleStationIds(parametersKey: String): List<Long> =
        jdbc.sql(
            """
            SELECT s.id FROM stations s
            LEFT JOIN erosivity_station_refreshes computed
                   ON computed.station_id = s.id AND computed.parameters_key = :key
            WHERE computed.station_id IS NULL
               OR EXISTS (SELECT 1 FROM station_imports si
                          WHERE si.station_id = s.id AND si.status = 'COMPLETED' AND si.finished_at > computed.computed_at)
            ORDER BY s.id
            """,
        )
            .param("key", parametersKey)
            .query { rs, _ -> rs.getLong("id") }
            .list()

    override fun status(parametersKey: String): CacheStatus {
        val stale = findStaleStationIds(parametersKey).size
        return jdbc.sql(
            """
            SELECT COUNT(DISTINCT station_id) AS stations, MIN(year) AS first_year, MAX(year) AS last_year,
                   MAX(computed_at) AS computed_at
            FROM erosivity_station_years WHERE parameters_key = :key
            """,
        )
            .param("key", parametersKey)
            .query { rs, _ ->
                CacheStatus(
                    cachedStations = rs.getInt("stations"),
                    staleStations = stale,
                    firstYear = rs.getInt("first_year").takeUnless { rs.wasNull() },
                    lastYear = rs.getInt("last_year").takeUnless { rs.wasNull() },
                    computedAt = rs.getInstant("computed_at"),
                )
            }
            .single()
    }

    private fun mapYear(rs: ResultSet) = StationErosivityYear(
        stationId = rs.getLong("station_id"),
        year = rs.getInt("year"),
        hoursInYear = rs.getInt("hours_in_year"),
        precipitationHours = rs.getInt("precipitation_hours"),
        precipitationMm = rs.getBigDecimal("precipitation_mm").toDouble(),
        erosiveEvents = rs.getInt("erosive_events"),
        ei30Sum = rs.getBigDecimal("ei30_sum").toDouble(),
    )
}

@Repository
class JdbcCacheRefreshRepository(private val jdbc: JdbcClient) : CacheRefreshRepository {

    override fun create(parametersKey: String, onlyStale: Boolean): CacheRefresh =
        jdbc.sql(
            """
            INSERT INTO erosivity_cache_refreshes (parameters_key, only_stale, status)
            VALUES (:key, :onlyStale, 'QUEUED') RETURNING *
            """,
        )
            .param("key", parametersKey)
            .param("onlyStale", onlyStale)
            .query { rs, _ -> map(rs) }
            .single()

    override fun findById(id: Long): CacheRefresh? =
        jdbc.sql("SELECT * FROM erosivity_cache_refreshes WHERE id = :id").param("id", id).query { rs, _ -> map(rs) }.optional().orElse(null)

    override fun findLatest(): CacheRefresh? =
        jdbc.sql("SELECT * FROM erosivity_cache_refreshes ORDER BY id DESC LIMIT 1").query { rs, _ -> map(rs) }.optional().orElse(null)

    override fun start(id: Long, stationsTotal: Int) {
        jdbc.sql("UPDATE erosivity_cache_refreshes SET status = 'PROCESSING', stations_total = :total, started_at = now() WHERE id = :id")
            .param("id", id)
            .param("total", stationsTotal)
            .update()
    }

    override fun progress(id: Long, done: Int, failed: Int) {
        jdbc.sql("UPDATE erosivity_cache_refreshes SET stations_done = :done, stations_failed = :failed WHERE id = :id")
            .param("id", id)
            .param("done", done)
            .param("failed", failed)
            .update()
    }

    override fun finish(id: Long, status: RefreshStatus, failureReason: String?) {
        jdbc.sql(
            """
            UPDATE erosivity_cache_refreshes SET status = :status, failure_reason = :reason, finished_at = now()
            WHERE id = :id
            """,
        )
            .param("id", id)
            .param("status", status.name)
            .param("reason", failureReason, java.sql.Types.VARCHAR)
            .update()
    }

    override fun failUnfinished(reason: String): Int =
        jdbc.sql(
            """
            UPDATE erosivity_cache_refreshes SET status = 'FAILED', failure_reason = :reason, finished_at = now()
            WHERE status IN ('QUEUED', 'PROCESSING')
            """,
        ).param("reason", reason).update()

    private fun map(rs: ResultSet) = CacheRefresh(
        id = rs.getLong("id"),
        parametersKey = rs.getString("parameters_key"),
        onlyStale = rs.getBoolean("only_stale"),
        status = RefreshStatus.valueOf(rs.getString("status")),
        stationsTotal = rs.getInt("stations_total").takeUnless { rs.wasNull() },
        stationsDone = rs.getInt("stations_done"),
        stationsFailed = rs.getInt("stations_failed"),
        failureReason = rs.getString("failure_reason"),
        createdAt = checkNotNull(rs.getInstant("created_at")),
        startedAt = rs.getInstant("started_at"),
        finishedAt = rs.getInstant("finished_at"),
    )
}
