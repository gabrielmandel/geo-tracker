package com.gpstracker.domain.imports

import com.gpstracker.domain.shared.BaseEntity
import java.time.Instant

enum class ArchiveImportStatus { QUEUED, PROCESSING, COMPLETED, FAILED }

// Outcome counters of the files inside an archive; every file ends in exactly one of the four buckets.
data class ArchiveProgress(
    val filesTotal: Int? = null,
    val filesImported: Int = 0,
    val filesDuplicated: Int = 0,
    val filesFailed: Int = 0,
    val filesIgnored: Int = 0,
    val rowsWritten: Long = 0,
    val failures: List<String> = emptyList(),
) {
    val filesProcessed: Int get() = filesImported + filesDuplicated + filesFailed + filesIgnored

    fun imported(rows: Int) = copy(filesImported = filesImported + 1, rowsWritten = rowsWritten + rows)

    fun duplicated() = copy(filesDuplicated = filesDuplicated + 1)

    fun ignored() = copy(filesIgnored = filesIgnored + 1)

    fun failed(fileName: String, reason: String) = copy(
        filesFailed = filesFailed + 1,
        failures = if (failures.size < MAX_FAILURES) failures + "$fileName: $reason" else failures,
    )

    companion object {
        const val MAX_FAILURES = 100
    }
}

class ArchiveImport(
    override val id: Long,
    val fileName: String,
    val status: ArchiveImportStatus,
    val progress: ArchiveProgress,
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val failureReason: String? = null,
) : BaseEntity<Long>() {

    fun start(filesTotal: Int, at: Instant): ArchiveImport {
        check(status == ArchiveImportStatus.QUEUED) { "Archive import $id is already $status" }
        return copy(ArchiveImportStatus.PROCESSING, progress.copy(filesTotal = filesTotal), startedAt = at)
    }

    fun record(newProgress: ArchiveProgress): ArchiveImport {
        checkProcessing()
        return copy(status, newProgress)
    }

    fun complete(at: Instant): ArchiveImport {
        checkProcessing()
        return copy(ArchiveImportStatus.COMPLETED, progress, finishedAt = at)
    }

    // A queued archive can also fail, for example when the application restarts before it is processed.
    fun fail(reason: String, at: Instant): ArchiveImport {
        check(status == ArchiveImportStatus.QUEUED || status == ArchiveImportStatus.PROCESSING) {
            "Archive import $id is already $status"
        }
        return copy(ArchiveImportStatus.FAILED, progress, finishedAt = at, failureReason = reason)
    }

    private fun checkProcessing() = check(status == ArchiveImportStatus.PROCESSING) { "Archive import $id is $status" }

    private fun copy(
        status: ArchiveImportStatus,
        progress: ArchiveProgress,
        startedAt: Instant? = this.startedAt,
        finishedAt: Instant? = this.finishedAt,
        failureReason: String? = this.failureReason,
    ) = ArchiveImport(id, fileName, status, progress, createdAt, startedAt, finishedAt, failureReason)
}
