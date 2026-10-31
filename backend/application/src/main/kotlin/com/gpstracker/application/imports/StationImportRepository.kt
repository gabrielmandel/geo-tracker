package com.gpstracker.application.imports

import com.gpstracker.application.shared.BaseRepository
import com.gpstracker.domain.imports.ImportStatus
import com.gpstracker.domain.imports.StationImport

interface StationImportRepository : BaseRepository<StationImport, Long> {
    fun start(fileName: String, fileHash: String): StationImport

    fun findCompletedByHash(fileHash: String): StationImport?

    // Newest first.
    fun findPage(stationId: Long?, status: ImportStatus?, limit: Int, offset: Long): List<StationImport>

    fun count(stationId: Long?, status: ImportStatus?): Long
}
