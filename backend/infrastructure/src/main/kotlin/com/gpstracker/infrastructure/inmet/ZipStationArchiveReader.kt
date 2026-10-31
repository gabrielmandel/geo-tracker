package com.gpstracker.infrastructure.inmet

import com.gpstracker.application.imports.ImportFile
import com.gpstracker.application.imports.InvalidStationFileException
import com.gpstracker.application.imports.StationArchive
import com.gpstracker.application.imports.StationArchiveReader
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

// INMET yearly ZIPs: one CSV per station, at the root (2020+) or inside a "<year>/" folder (older years).
@Component
class ZipStationArchiveReader : StationArchiveReader {

    override fun open(archive: Path): StationArchive {
        // Names without the UTF-8 flag are decoded as Latin-1, which never fails; they are only labels here.
        val zip = try {
            ZipFile(archive.toFile(), StandardCharsets.ISO_8859_1)
        } catch (e: IOException) {
            throw InvalidStationFileException("Not a valid ZIP archive: ${e.message}")
        }
        if (zip.size() > MAX_ENTRIES) {
            zip.close()
            throw InvalidStationFileException("The archive has more than $MAX_ENTRIES entries")
        }
        val files = zip.entries().asSequence()
            .filterNot(ZipEntry::isDirectory)
            .map { entry -> ImportFile(entry.name, entry.size) { zip.content(entry) } }
            .toList()
        return ZipArchive(zip, files)
    }

    // The declared size is what the import validates, so the real content must match it: a forged size
    // cannot make a small entry inflate into gigabytes (zip bomb).
    private fun ZipFile.content(entry: ZipEntry): InputStream {
        if (entry.size !in 0..<Int.MAX_VALUE) throw InvalidStationFileException("Entry ${entry.name} has an invalid size")
        val bytes = getInputStream(entry).use { it.readNBytes(entry.size.toInt() + 1) }
        if (bytes.size.toLong() != entry.size) throw InvalidStationFileException("Entry ${entry.name} does not match its declared size")
        return ByteArrayInputStream(bytes)
    }

    private class ZipArchive(private val zip: ZipFile, override val files: List<ImportFile>) : StationArchive {
        override fun close() = zip.close()
    }

    private companion object {
        const val MAX_ENTRIES = 20_000
    }
}
