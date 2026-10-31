package com.gpstracker.application.imports

import com.gpstracker.application.observation.StationObservationRepository
import com.gpstracker.application.station.WeatherStationRepository
import com.gpstracker.domain.imports.ImportSummary
import com.gpstracker.domain.imports.StationImport
import com.gpstracker.domain.station.StationDetails
import com.gpstracker.domain.station.WeatherStation
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat

class ImportResult(val stationImport: StationImport, val station: WeatherStation)

@Service
class StationImportService(
    private val reader: StationFileReader,
    private val imports: StationImportRepository,
    private val stations: WeatherStationRepository,
    private val observations: StationObservationRepository,
    private val transaction: TransactionOperations,
) {
    fun import(file: ImportFile): ImportResult {
        if (!file.name.endsWith(".csv", ignoreCase = true)) throw InvalidStationFileException("Only INMET .csv files are accepted")
        if (file.size == 0L) throw InvalidStationFileException("The file is empty")
        // The reader loads the whole file; one station-year of INMET data is about 1 MB.
        if (file.size > MAX_FILE_BYTES) throw InvalidStationFileException("The file exceeds ${MAX_FILE_BYTES / 1024 / 1024} MB")

        val hash = sha256(file)
        imports.findCompletedByHash(hash)?.let { throw DuplicateImportException(it.id) }

        // Recorded outside the data transaction so a failed attempt stays in the history.
        val started = imports.start(file.name, hash)
        return try {
            val parsed = file.open().use(reader::read)
            if (parsed.observations.isEmpty()) throw InvalidStationFileException("The file has no valid rows")
            checkNotNull(transaction.execute { store(started, parsed) })
        } catch (e: Exception) {
            runCatching { imports.save(started.fail(e.message ?: e.javaClass.simpleName, Instant.now())) }
                .onFailure(e::addSuppressed)
            throw e
        }
    }

    private fun store(started: StationImport, file: StationFile): ImportResult {
        val periodStart = file.observations.minOf { it.observedAt }
        val periodEnd = file.observations.maxOf { it.observedAt }

        val station = registerStation(file.details, periodEnd)
        val written = observations.upsert(station.id, started.id, periodEnd, file.observations)

        val summary = ImportSummary(
            stationId = station.id,
            periodStart = periodStart,
            periodEnd = periodEnd,
            rowsRead = file.rowsRead,
            rowsWritten = written,
            rowsSkipped = file.observations.size - written,
            rowsRejected = file.rowsRejected,
            missingPrecipitationRows = file.observations.count { it.precipitationMm == null },
            rejections = file.rejections,
        )
        return ImportResult(imports.save(started.complete(summary, Instant.now())), station)
    }

    private fun registerStation(details: StationDetails, dataUntil: Instant): WeatherStation {
        val existing = stations.findByCode(details.code) ?: return stations.create(details, dataUntil)
        val updated = existing.withDetails(details, dataUntil)
        return if (updated === existing) existing else stations.save(updated)
    }

    private fun sha256(file: ImportFile): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.open().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private companion object {
        const val MAX_FILE_BYTES = 20L * 1024 * 1024
    }
}
