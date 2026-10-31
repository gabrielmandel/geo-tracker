package com.gpstracker.infrastructure.inmet

import com.gpstracker.application.imports.ArchiveDownload
import com.gpstracker.application.imports.HistoricalArchiveSource
import com.gpstracker.application.imports.SyncState
import com.gpstracker.application.imports.SyncStateRepository
import com.gpstracker.infrastructure.shared.getInstant
import com.gpstracker.infrastructure.shared.toUtc
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Types
import java.time.Duration

@Component
class InmetHistoricalArchiveClient(
    @Value("\${geo.inmet-sync.base-url:https://portal.inmet.gov.br/uploads/dadoshistoricos}") private val baseUrl: String,
) : HistoricalArchiveSource {

    private val http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    override fun download(year: Int, lastModified: String?, etag: String?): ArchiveDownload {
        val request = HttpRequest.newBuilder(URI.create("${baseUrl.trimEnd('/')}/$year.zip"))
            .timeout(Duration.ofMinutes(30))
            .header("User-Agent", "geo-tracker (open source INMET importer)")
            .apply {
                lastModified?.let { header("If-Modified-Since", it) }
                etag?.let { header("If-None-Match", it) }
            }
            .GET()
            .build()
        val target = Files.createTempFile("inmet-$year-", ".zip")
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofFile(target))
        } catch (e: Exception) {
            Files.deleteIfExists(target)
            throw e
        }
        return when (response.statusCode()) {
            200 -> ArchiveDownload.Downloaded(
                target,
                response.headers().firstValue("Last-Modified").orElse(null),
                response.headers().firstValue("ETag").orElse(null),
            )
            304 -> ArchiveDownload.NotModified.also { Files.deleteIfExists(target) }
            404 -> ArchiveDownload.NotFound.also { Files.deleteIfExists(target) }
            else -> {
                Files.deleteIfExists(target)
                error("INMET answered HTTP ${response.statusCode()} for $year.zip")
            }
        }
    }
}

@Repository
class JdbcSyncStateRepository(private val jdbc: JdbcClient) : SyncStateRepository {

    override fun find(year: Int): SyncState? =
        jdbc.sql("SELECT * FROM inmet_sync_state WHERE year = :year").param("year", year)
            .query { rs, _ -> map(rs) }.optional().orElse(null)

    override fun save(state: SyncState) {
        jdbc.sql(
            """
            INSERT INTO inmet_sync_state (year, last_modified, etag, checked_at, archive_import_id)
            VALUES (:year, :lastModified, :etag, :checkedAt, :archiveImportId)
            ON CONFLICT (year) DO UPDATE SET last_modified = EXCLUDED.last_modified, etag = EXCLUDED.etag,
                checked_at = EXCLUDED.checked_at, archive_import_id = EXCLUDED.archive_import_id
            """,
        )
            .param("year", state.year)
            .param("lastModified", state.lastModified, Types.VARCHAR)
            .param("etag", state.etag, Types.VARCHAR)
            .param("checkedAt", state.checkedAt.toUtc())
            .param("archiveImportId", state.archiveImportId, Types.BIGINT)
            .update()
    }

    override fun findAll(): List<SyncState> =
        jdbc.sql("SELECT * FROM inmet_sync_state ORDER BY year").query { rs, _ -> map(rs) }.list()

    private fun map(rs: java.sql.ResultSet) = SyncState(
        year = rs.getInt("year"),
        lastModified = rs.getString("last_modified"),
        etag = rs.getString("etag"),
        checkedAt = checkNotNull(rs.getInstant("checked_at")),
        archiveImportId = rs.getLong("archive_import_id").takeUnless { rs.wasNull() },
    )
}
