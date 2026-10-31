package com.gpstracker.api.imports

import com.gpstracker.application.imports.ImportResult
import java.time.Instant

data class StationImportResponse(
    val id: Long,
    val fileName: String,
    val status: String,
    val station: ImportedStation,
    val periodStart: Instant,
    val periodEnd: Instant,
    val rowsRead: Int,
    val rowsWritten: Int,
    val rowsSkipped: Int,
    val rowsRejected: Int,
    val missingPrecipitationRows: Int,
    val rejections: List<String>,
    val startedAt: Instant,
    val finishedAt: Instant?,
)

data class ImportedStation(val id: Long, val code: String, val name: String)

fun ImportResult.toResponse(): StationImportResponse {
    val summary = checkNotNull(stationImport.summary) { "Import ${stationImport.id} has no summary" }
    return StationImportResponse(
        id = stationImport.id,
        fileName = stationImport.fileName,
        status = stationImport.status.name,
        station = ImportedStation(station.id, station.details.code, station.details.name),
        periodStart = summary.periodStart,
        periodEnd = summary.periodEnd,
        rowsRead = summary.rowsRead,
        rowsWritten = summary.rowsWritten,
        rowsSkipped = summary.rowsSkipped,
        rowsRejected = summary.rowsRejected,
        missingPrecipitationRows = summary.missingPrecipitationRows,
        rejections = summary.rejections,
        startedAt = stationImport.startedAt,
        finishedAt = stationImport.finishedAt,
    )
}
