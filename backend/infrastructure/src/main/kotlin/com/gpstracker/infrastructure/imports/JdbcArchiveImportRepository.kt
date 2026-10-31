package com.gpstracker.infrastructure.imports

import com.gpstracker.application.imports.ArchiveImportRepository
import com.gpstracker.domain.imports.ArchiveImport
import com.gpstracker.domain.imports.ArchiveImportStatus
import com.gpstracker.domain.imports.ArchiveProgress
import com.gpstracker.infrastructure.shared.JdbcRepository
import com.gpstracker.infrastructure.shared.getInstant
import com.gpstracker.infrastructure.shared.toUtc
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.support.SqlArrayValue
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant

@Repository
class JdbcArchiveImportRepository(jdbc: JdbcClient) :
    JdbcRepository<ArchiveImport, Long>(jdbc, "archive_imports"),
    ArchiveImportRepository {

    override fun create(fileName: String): ArchiveImport =
        jdbc.sql("INSERT INTO archive_imports (file_name, status) VALUES (:fileName, 'QUEUED') RETURNING *")
            .param("fileName", fileName)
            .mapSingle()

    override fun findRecent(limit: Int): List<ArchiveImport> =
        jdbc.sql("SELECT * FROM archive_imports ORDER BY id DESC LIMIT :limit").param("limit", limit).mapList()

    override fun failUnfinished(reason: String, at: Instant): Int =
        jdbc.sql(
            """
            UPDATE archive_imports SET status = 'FAILED', failure_reason = :reason, finished_at = :at
            WHERE status IN ('QUEUED', 'PROCESSING')
            """,
        ).param("reason", reason).param("at", at.toUtc()).update()

    override fun save(entity: ArchiveImport): ArchiveImport {
        val progress = entity.progress
        jdbc.sql(
            """
            UPDATE archive_imports
            SET status = :status, files_total = :filesTotal, files_imported = :filesImported,
                files_duplicated = :filesDuplicated, files_failed = :filesFailed, files_ignored = :filesIgnored,
                rows_written = :rowsWritten, failures = :failures, failure_reason = :failureReason,
                started_at = :startedAt, finished_at = :finishedAt
            WHERE id = :id
            """,
        )
            .param("id", entity.id)
            .param("status", entity.status.name)
            .param("filesTotal", progress.filesTotal, Types.INTEGER)
            .param("filesImported", progress.filesImported)
            .param("filesDuplicated", progress.filesDuplicated)
            .param("filesFailed", progress.filesFailed)
            .param("filesIgnored", progress.filesIgnored)
            .param("rowsWritten", progress.rowsWritten)
            .param("failures", SqlArrayValue("text", *progress.failures.toTypedArray()))
            .param("failureReason", entity.failureReason, Types.VARCHAR)
            .param("startedAt", entity.startedAt?.toUtc(), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("finishedAt", entity.finishedAt?.toUtc(), Types.TIMESTAMP_WITH_TIMEZONE)
            .update()
        return entity
    }

    override fun mapRow(rs: ResultSet) = ArchiveImport(
        id = rs.getLong("id"),
        fileName = rs.getString("file_name"),
        status = ArchiveImportStatus.valueOf(rs.getString("status")),
        progress = ArchiveProgress(
            filesTotal = rs.getInt("files_total").takeUnless { rs.wasNull() },
            filesImported = rs.getInt("files_imported"),
            filesDuplicated = rs.getInt("files_duplicated"),
            filesFailed = rs.getInt("files_failed"),
            filesIgnored = rs.getInt("files_ignored"),
            rowsWritten = rs.getLong("rows_written"),
            failures = (rs.getArray("failures").array as Array<*>).map { it as String },
        ),
        createdAt = checkNotNull(rs.getInstant("created_at")),
        startedAt = rs.getInstant("started_at"),
        finishedAt = rs.getInstant("finished_at"),
        failureReason = rs.getString("failure_reason"),
    )
}
