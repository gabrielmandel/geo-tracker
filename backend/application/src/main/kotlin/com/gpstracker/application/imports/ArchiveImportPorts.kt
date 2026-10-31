package com.gpstracker.application.imports

import com.gpstracker.application.shared.BaseRepository
import com.gpstracker.domain.imports.ArchiveImport
import java.io.Closeable
import java.nio.file.Path
import java.time.Instant

interface ArchiveImportRepository : BaseRepository<ArchiveImport, Long> {
    fun create(fileName: String): ArchiveImport

    // Newest first.
    fun findRecent(limit: Int): List<ArchiveImport>

    // Archives left QUEUED or PROCESSING by a stopped application; returns how many were failed.
    fun failUnfinished(reason: String, at: Instant): Int
}

// Port for opening an uploaded archive (ZIP) of INMET station files.
interface StationArchiveReader {
    fun open(archive: Path): StationArchive
}

// Files are read lazily; they can only be opened while the archive is open.
interface StationArchive : Closeable {
    val files: List<ImportFile>
}

class ArchiveImportNotFoundException(id: Long) : RuntimeException("Archive import $id was not found")
