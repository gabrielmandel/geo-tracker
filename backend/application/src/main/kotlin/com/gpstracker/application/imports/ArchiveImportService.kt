package com.gpstracker.application.imports

import com.gpstracker.domain.imports.ArchiveImport
import com.gpstracker.domain.imports.ArchiveProgress
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.Executors

// Published after an archive imported at least one new file, so derived data (the R map cache) can refresh.
data class ArchiveImportedEvent(val archiveImportId: Long, val filesImported: Int)

// Bulk import of an INMET yearly ZIP. Every CSV inside goes through the single-file workflow, so hashing,
// duplicate detection, station registration and the "newer export wins" rule stay in one place.
@Service
class ArchiveImportService(
    private val archives: ArchiveImportRepository,
    private val reader: StationArchiveReader,
    private val fileImports: StationImportService,
    private val events: ApplicationEventPublisher,
) : DisposableBean {

    // One archive at a time, in submission order: parallel archives would race to register the same stations.
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "archive-import") }

    fun submit(file: ImportFile): ArchiveImport {
        if (!file.name.endsWith(".zip", ignoreCase = true)) throw InvalidStationFileException("Only .zip archives are accepted")
        if (file.size == 0L) throw InvalidStationFileException("The archive is empty")

        // The upload only lives for the request, so the archive waits for the worker in a temporary file.
        val staged = Files.createTempFile("inmet-archive-", ".zip")
        try {
            file.open().use { Files.copy(it, staged, StandardCopyOption.REPLACE_EXISTING) }
            return submitStaged(file.name, staged)
        } catch (e: Exception) {
            Files.deleteIfExists(staged)
            throw e
        }
    }

    // Takes ownership of the file: it is deleted once processed.
    fun submitStaged(fileName: String, staged: Path): ArchiveImport {
        val queued = archives.create(fileName)
        worker.execute { process(queued, staged) }
        return queued
    }

    fun find(id: Long): ArchiveImport = archives.findById(id) ?: throw ArchiveImportNotFoundException(id)

    fun findRecent(limit: Int): List<ArchiveImport> = archives.findRecent(limit.coerceIn(1, 200))

    @EventListener(ContextRefreshedEvent::class)
    fun failInterrupted() {
        val failed = archives.failUnfinished("Interrupted by an application restart; upload the archive again", Instant.now())
        if (failed > 0) log.warn("Marked $failed unfinished archive import(s) as FAILED")
    }

    override fun destroy() {
        worker.shutdownNow()
    }

    private fun process(queued: ArchiveImport, staged: Path) {
        var current = queued
        try {
            reader.open(staged).use { archive ->
                current = archives.save(current.start(archive.files.size, Instant.now()))
                log.info("Archive import ${current.id} (${current.fileName}): ${archive.files.size} files")
                for (file in archive.files) {
                    current = archives.save(current.record(importFile(file, current.progress)))
                }
            }
            current = archives.save(current.complete(Instant.now()))
            with(current.progress) {
                log.info(
                    "Archive import ${current.id} (${current.fileName}) completed: imported=$filesImported " +
                        "duplicated=$filesDuplicated failed=$filesFailed ignored=$filesIgnored rows=$rowsWritten",
                )
                if (filesImported > 0) events.publishEvent(ArchiveImportedEvent(current.id, filesImported))
            }
        } catch (e: Exception) {
            log.error("Archive import ${current.id} (${current.fileName}) failed", e)
            runCatching { archives.save(current.fail(e.message ?: e.javaClass.simpleName, Instant.now())) }
                .onFailure { log.error("Could not record the failure of archive import ${current.id}", it) }
        } finally {
            Files.deleteIfExists(staged)
        }
    }

    // A bad file is recorded and skipped; it never stops the rest of the archive.
    private fun importFile(file: ImportFile, progress: ArchiveProgress): ArchiveProgress {
        if (!file.name.endsWith(".csv", ignoreCase = true)) return progress.ignored()
        return try {
            val summary = checkNotNull(fileImports.import(file).stationImport.summary)
            progress.imported(summary.rowsWritten)
        } catch (e: DuplicateImportException) {
            progress.duplicated()
        } catch (e: Exception) {
            progress.failed(file.name, e.message ?: e.javaClass.simpleName)
        }
    }

    private companion object {
        val log = LogFactory.getLog(ArchiveImportService::class.java)
    }
}
