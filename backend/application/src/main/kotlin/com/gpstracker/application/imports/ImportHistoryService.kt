package com.gpstracker.application.imports

import com.gpstracker.application.shared.PageResult
import com.gpstracker.domain.imports.ImportStatus
import com.gpstracker.domain.imports.StationImport
import org.springframework.stereotype.Service

@Service
class ImportHistoryService(private val imports: StationImportRepository) {

    fun find(id: Long): StationImport = imports.findById(id) ?: throw StationImportNotFoundException(id)

    fun findPage(stationId: Long?, status: ImportStatus?, page: Int, size: Int): PageResult<StationImport> {
        require(page >= 0) { "page must not be negative" }
        require(size in 1..MAX_PAGE_SIZE) { "size must be between 1 and $MAX_PAGE_SIZE" }
        val items = imports.findPage(stationId, status, size, page.toLong() * size)
        return PageResult(items, page, size, imports.count(stationId, status))
    }

    private companion object {
        const val MAX_PAGE_SIZE = 200
    }
}
