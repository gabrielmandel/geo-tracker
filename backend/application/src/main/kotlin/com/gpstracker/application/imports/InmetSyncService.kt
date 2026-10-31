package com.gpstracker.application.imports

import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

sealed interface ArchiveDownload {
    data object NotModified : ArchiveDownload
    data object NotFound : ArchiveDownload
    data class Downloaded(val file: Path, val lastModified: String?, val etag: String?) : ArchiveDownload
}

// Port to INMET's historical data portal (one ZIP per year).
fun interface HistoricalArchiveSource {
    fun download(year: Int, lastModified: String?, etag: String?): ArchiveDownload
}

data class SyncState(val year: Int, val lastModified: String?, val etag: String?, val checkedAt: Instant, val archiveImportId: Long?)

interface SyncStateRepository {
    fun find(year: Int): SyncState?

    fun save(state: SyncState)

    fun findAll(): List<SyncState>
}

data class SyncOutcome(val year: Int, val result: String, val archiveImportId: Long?)

// Keeps the database current without manual uploads; unchanged files inside a new ZIP are skipped by their hash.
@Service
class InmetSyncService(
    private val source: HistoricalArchiveSource,
    private val states: SyncStateRepository,
    private val archives: ArchiveImportService,
    @Value("\${geo.inmet-sync.enabled:false}") private val enabled: Boolean,
) {
    @Scheduled(cron = "\${geo.inmet-sync.cron:0 0 4 * * MON}", zone = "UTC")
    fun scheduledSync() {
        if (!enabled) return
        runCatching { syncRecentYears() }.onFailure { log.error("Scheduled INMET sync failed", it) }
    }

    // INMET keeps revising the previous year early in the new one, so it is checked until March.
    fun syncRecentYears(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): List<SyncOutcome> {
        val years = if (today.monthValue <= 3) listOf(today.year - 1, today.year) else listOf(today.year)
        return years.map(::sync)
    }

    fun sync(year: Int): SyncOutcome {
        require(year in 2000..LocalDate.now(ZoneOffset.UTC).year) { "year must be between 2000 and the current year" }
        val previous = states.find(year)
        val outcome = when (val download = source.download(year, previous?.lastModified, previous?.etag)) {
            ArchiveDownload.NotModified -> SyncOutcome(year, "NOT_MODIFIED", previous?.archiveImportId)
            ArchiveDownload.NotFound -> SyncOutcome(year, "NOT_FOUND", null)
            is ArchiveDownload.Downloaded -> {
                val queued = archives.submitStaged("inmet-$year.zip", download.file)
                states.save(SyncState(year, download.lastModified, download.etag, Instant.now(), queued.id))
                SyncOutcome(year, "QUEUED", queued.id)
            }
        }
        if (outcome.result != "QUEUED") {
            states.save(SyncState(year, previous?.lastModified, previous?.etag, Instant.now(), previous?.archiveImportId))
        }
        log.info("INMET sync $year: ${outcome.result}")
        return outcome
    }

    fun states(): List<SyncState> = states.findAll()

    private companion object {
        val log = LogFactory.getLog(InmetSyncService::class.java)
    }
}
