package com.gpstracker.domain.imports

import com.gpstracker.domain.shared.BaseEntity
import java.time.Instant

class StationImport(
    override val id: Long,
    val fileName: String,
    val fileHash: String,
    val status: ImportStatus,
    val startedAt: Instant,
    val summary: ImportSummary? = null,
    val failureReason: String? = null,
    val finishedAt: Instant? = null,
) : BaseEntity<Long>() {

    fun complete(summary: ImportSummary, at: Instant): StationImport {
        checkProcessing()
        return StationImport(id, fileName, fileHash, ImportStatus.COMPLETED, startedAt, summary, null, at)
    }

    fun fail(reason: String, at: Instant): StationImport {
        checkProcessing()
        return StationImport(id, fileName, fileHash, ImportStatus.FAILED, startedAt, null, reason, at)
    }

    private fun checkProcessing() = check(status == ImportStatus.PROCESSING) { "Import $id is already $status" }
}
