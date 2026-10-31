package com.gpstracker.infrastructure.imports

import com.gpstracker.application.imports.StationImportRepository
import com.gpstracker.domain.imports.ImportStatus
import com.gpstracker.domain.imports.ImportSummary
import com.gpstracker.domain.imports.StationImport
import com.gpstracker.infrastructure.shared.JdbcRepository
import com.gpstracker.infrastructure.shared.getInstant
import com.gpstracker.infrastructure.shared.toUtc
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.support.SqlArrayValue
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types

@Repository
class JdbcStationImportRepository(jdbc: JdbcClient) :
    JdbcRepository<StationImport, Long>(jdbc, "station_imports"),
    StationImportRepository {

    override fun start(fileName: String, fileHash: String): StationImport =
        jdbc.sql(
            """
            INSERT INTO station_imports (file_name, file_hash, status)
            VALUES (:fileName, :fileHash, 'PROCESSING')
            RETURNING *
            """,
        ).param("fileName", fileName).param("fileHash", fileHash).mapSingle()

    override fun findCompletedByHash(fileHash: String): StationImport? =
        jdbc.sql("SELECT * FROM station_imports WHERE file_hash = :fileHash AND status = 'COMPLETED'")
            .param("fileHash", fileHash)
            .mapOne()

    override fun findPage(stationId: Long?, status: ImportStatus?, limit: Int, offset: Long): List<StationImport> {
        val (where, params) = historyFilter(stationId, status)
        return jdbc.sql("SELECT * FROM station_imports WHERE $where ORDER BY id DESC LIMIT :limit OFFSET :offset")
            .params(params + mapOf("limit" to limit, "offset" to offset))
            .mapList()
    }

    override fun count(stationId: Long?, status: ImportStatus?): Long {
        val (where, params) = historyFilter(stationId, status)
        return jdbc.sql("SELECT COUNT(*) FROM station_imports WHERE $where").params(params).query(Long::class.java).single()
    }

    private fun historyFilter(stationId: Long?, status: ImportStatus?): Pair<String, Map<String, Any>> {
        val conditions = mutableListOf("TRUE")
        val params = mutableMapOf<String, Any>()
        stationId?.let { conditions += "station_id = :stationId"; params["stationId"] = it }
        status?.let { conditions += "status = :status"; params["status"] = it.name }
        return conditions.joinToString(" AND ") to params
    }

    override fun save(entity: StationImport): StationImport {
        val summary = entity.summary
        jdbc.sql(
            """
            UPDATE station_imports
            SET status = :status, station_id = :stationId, period_start = :periodStart, period_end = :periodEnd,
                rows_read = :rowsRead, rows_written = :rowsWritten, rows_skipped = :rowsSkipped,
                rows_rejected = :rowsRejected, missing_precipitation_rows = :missingPrecipitationRows,
                rejections = :rejections, failure_reason = :failureReason, finished_at = :finishedAt
            WHERE id = :id
            """,
        )
            .param("id", entity.id)
            .param("status", entity.status.name)
            .param("stationId", summary?.stationId, Types.BIGINT)
            .param("periodStart", summary?.periodStart?.toUtc(), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("periodEnd", summary?.periodEnd?.toUtc(), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("rowsRead", summary?.rowsRead, Types.INTEGER)
            .param("rowsWritten", summary?.rowsWritten, Types.INTEGER)
            .param("rowsSkipped", summary?.rowsSkipped, Types.INTEGER)
            .param("rowsRejected", summary?.rowsRejected, Types.INTEGER)
            .param("missingPrecipitationRows", summary?.missingPrecipitationRows, Types.INTEGER)
            .param("rejections", SqlArrayValue("text", *summary?.rejections.orEmpty().toTypedArray()))
            .param("failureReason", entity.failureReason, Types.VARCHAR)
            .param("finishedAt", entity.finishedAt?.toUtc(), Types.TIMESTAMP_WITH_TIMEZONE)
            .update()
        return entity
    }

    override fun mapRow(rs: ResultSet): StationImport {
        val status = ImportStatus.valueOf(rs.getString("status"))
        return StationImport(
            id = rs.getLong("id"),
            fileName = rs.getString("file_name"),
            fileHash = rs.getString("file_hash"),
            status = status,
            startedAt = checkNotNull(rs.getInstant("started_at")),
            summary = if (status == ImportStatus.COMPLETED) mapSummary(rs) else null,
            failureReason = rs.getString("failure_reason"),
            finishedAt = rs.getInstant("finished_at"),
        )
    }

    private fun mapSummary(rs: ResultSet) = ImportSummary(
        stationId = rs.getLong("station_id"),
        periodStart = checkNotNull(rs.getInstant("period_start")),
        periodEnd = checkNotNull(rs.getInstant("period_end")),
        rowsRead = rs.getInt("rows_read"),
        rowsWritten = rs.getInt("rows_written"),
        rowsSkipped = rs.getInt("rows_skipped"),
        rowsRejected = rs.getInt("rows_rejected"),
        missingPrecipitationRows = rs.getInt("missing_precipitation_rows"),
        rejections = (rs.getArray("rejections").array as Array<*>).map { it as String },
    )
}
