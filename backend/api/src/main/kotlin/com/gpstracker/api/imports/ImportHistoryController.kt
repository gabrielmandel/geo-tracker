package com.gpstracker.api.imports

import com.gpstracker.application.imports.ImportHistoryService
import com.gpstracker.application.shared.PageResult
import com.gpstracker.domain.imports.ImportStatus
import com.gpstracker.domain.imports.StationImport
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class ImportHistoryResponse(
    val id: Long,
    val fileName: String,
    val status: String,
    val stationId: Long?,
    val periodStart: Instant?,
    val periodEnd: Instant?,
    val rowsRead: Int?,
    val rowsWritten: Int?,
    val rowsSkipped: Int?,
    val rowsRejected: Int?,
    val missingPrecipitationRows: Int?,
    val rejections: List<String>,
    val failureReason: String?,
    val startedAt: Instant,
    val finishedAt: Instant?,
)

data class PageResponse<T>(val items: List<T>, val page: Int, val size: Int, val total: Long)

@RestController
@RequestMapping("/imports")
class ImportHistoryController(private val service: ImportHistoryService) {

    @GetMapping
    fun history(
        @RequestParam(required = false) stationId: Long?,
        @RequestParam(required = false) status: ImportStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<ImportHistoryResponse> = service.findPage(stationId, status, page, size).toResponse()

    @GetMapping("/{id}")
    fun find(@PathVariable id: Long): ImportHistoryResponse = service.find(id).toHistoryResponse()

    private fun PageResult<StationImport>.toResponse() = PageResponse(items.map { it.toHistoryResponse() }, page, size, total)

    private fun StationImport.toHistoryResponse() = ImportHistoryResponse(
        id = id,
        fileName = fileName,
        status = status.name,
        stationId = summary?.stationId,
        periodStart = summary?.periodStart,
        periodEnd = summary?.periodEnd,
        rowsRead = summary?.rowsRead,
        rowsWritten = summary?.rowsWritten,
        rowsSkipped = summary?.rowsSkipped,
        rowsRejected = summary?.rowsRejected,
        missingPrecipitationRows = summary?.missingPrecipitationRows,
        rejections = summary?.rejections.orEmpty(),
        failureReason = failureReason,
        startedAt = startedAt,
        finishedAt = finishedAt,
    )
}
