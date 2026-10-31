package com.gpstracker.api.imports

import com.gpstracker.application.imports.ArchiveImportService
import com.gpstracker.application.imports.ImportFile
import com.gpstracker.domain.imports.ArchiveImport
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.time.Instant

// Bulk import of an INMET yearly ZIP. Large archives take minutes, so the upload is queued (202) and polled.
@RestController
@RequestMapping("/imports/archives")
class ArchiveImportController(private val service: ArchiveImportService) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun submit(@RequestPart("file") file: MultipartFile): ResponseEntity<ArchiveImportResponse> {
        val queued = service.submit(ImportFile(file.originalFilename ?: file.name, file.size, file::getInputStream))
        val location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(queued.id).toUri()
        return ResponseEntity.accepted().location(location).body(queued.toResponse())
    }

    @GetMapping
    fun recent(@RequestParam(defaultValue = "20") limit: Int): List<ArchiveImportResponse> =
        service.findRecent(limit).map { it.toResponse() }

    @GetMapping("/{id}")
    fun find(@PathVariable id: Long): ArchiveImportResponse = service.find(id).toResponse()
}

data class ArchiveImportResponse(
    val id: Long,
    val fileName: String,
    val status: String,
    val filesTotal: Int?,
    val filesProcessed: Int,
    val filesImported: Int,
    val filesDuplicated: Int,
    val filesFailed: Int,
    val filesIgnored: Int,
    val rowsWritten: Long,
    val failures: List<String>,
    val failureReason: String?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
)

private fun ArchiveImport.toResponse() = ArchiveImportResponse(
    id = id,
    fileName = fileName,
    status = status.name,
    filesTotal = progress.filesTotal,
    filesProcessed = progress.filesProcessed,
    filesImported = progress.filesImported,
    filesDuplicated = progress.filesDuplicated,
    filesFailed = progress.filesFailed,
    filesIgnored = progress.filesIgnored,
    rowsWritten = progress.rowsWritten,
    failures = progress.failures,
    failureReason = failureReason,
    createdAt = createdAt,
    startedAt = startedAt,
    finishedAt = finishedAt,
)
