package com.gpstracker.infrastructure.inmet

import com.gpstracker.application.imports.InvalidStationFileException
import com.gpstracker.application.imports.StationFile
import com.gpstracker.application.imports.StationFileReader
import com.gpstracker.domain.observation.HourlyObservation
import com.gpstracker.domain.shared.Coordinate
import com.gpstracker.domain.station.StationDetails
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import kotlin.math.roundToInt

// Reads one automatic station from the INMET historical CSV, in the layouts used before and after 2019.
@Component
class InmetCsvReader : StationFileReader {

    override fun read(input: InputStream): StationFile {
        val lines = decode(input.readBytes()).lines()
        val headerIndex = lines.indexOfFirst { normalize(it).contains(Column.PRECIPITATION.header) }
        if (headerIndex < 0) throw InvalidStationFileException("INMET header row with the precipitation column was not found")

        val details = readDetails(lines.subList(0, headerIndex))
        val columns = mapColumns(lines[headerIndex])
        val minCells = columns.values.max() + 1

        val observations = mutableListOf<HourlyObservation>()
        val rejections = mutableListOf<String>()
        var rowsRead = 0
        var rowsRejected = 0
        for (index in headerIndex + 1 until lines.size) {
            if (lines[index].isBlank()) continue
            rowsRead++
            try {
                val cells = lines[index].split(';')
                require(cells.size >= minCells) { "Expected $minCells columns, found ${cells.size}" }
                observations += readRow(cells, columns)
            } catch (e: IllegalArgumentException) {
                rowsRejected++
                if (rejections.size < MAX_REJECTIONS) rejections += "Line ${index + 1}: ${e.message}"
            }
        }
        return StationFile(details, observations, rowsRead, rowsRejected, rejections)
    }

    // INMET publishes Latin-1; a file re-saved as UTF-8 must still be read correctly.
    private fun decode(bytes: ByteArray): String {
        val text = try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, StandardCharsets.ISO_8859_1)
        }
        return text.removePrefix("\uFEFF")
    }

    private fun readDetails(lines: List<String>): StationDetails {
        val values = lines.map { it.split(';') }
            .filter { it.size >= 2 }
            .associate { metadataKey(it[0]) to it[1].trim() }

        fun value(key: String) = (values[key] ?: values.entries.firstOrNull { it.key.matchesWithUnknownLetters(key) }?.value)
            ?.takeIf { it.isNotEmpty() }
        fun required(key: String) = value(key) ?: throw InvalidStationFileException("Station metadata '$key' is missing")

        return try {
            StationDetails(
                code = required("CODIGO"),
                name = required("ESTACAO"),
                region = required("REGIAO"),
                state = required("UF"),
                coordinate = Coordinate(decimal(required("LATITUDE")), decimal(required("LONGITUDE"))),
                // Some stations publish "F" or "NULL" instead of a number.
                altitudeMeters = value("ALTITUDE")?.replace(',', '.')?.toDoubleOrNull(),
                foundedOn = value("DATA DE FUNDACAO")?.let(::foundationDate),
            )
        } catch (e: IllegalArgumentException) {
            throw InvalidStationFileException("Invalid station metadata: ${e.message}")
        }
    }

    // The 2019 export replaced "Ã" with a literal '?' ("ESTAC?O:", "REGI?O:").
    private fun String.matchesWithUnknownLetters(key: String) =
        length == key.length && indices.all { this[it] == key[it] || this[it] == '?' }

    private fun mapColumns(header: String): Map<Column, Int> {
        val cells = header.split(';').map(::normalize)
        if (!cells[0].startsWith("DATA") || cells.getOrNull(1)?.startsWith("HORA") != true) {
            throw InvalidStationFileException("Expected 'Data' and 'Hora' as the first two columns")
        }
        return Column.entries
            .associateWith { column -> cells.indexOfFirst { it.startsWith(column.header) } }
            .filterValues { it >= 0 }
    }

    private fun readRow(cells: List<String>, columns: Map<Column, Int>): HourlyObservation {
        fun number(column: Column) = columns[column]?.let { measurement(cells[it]) }
        fun integer(column: Column) = number(column)?.roundToInt()

        return HourlyObservation(
            observedAt = instant(cells[0], cells[1]),
            precipitationMm = number(Column.PRECIPITATION),
            stationPressureMb = number(Column.STATION_PRESSURE),
            maxPressureMb = number(Column.MAX_PRESSURE),
            minPressureMb = number(Column.MIN_PRESSURE),
            globalRadiationKjM2 = number(Column.GLOBAL_RADIATION),
            airTemperatureC = number(Column.AIR_TEMPERATURE),
            dewPointC = number(Column.DEW_POINT),
            maxAirTemperatureC = number(Column.MAX_AIR_TEMPERATURE),
            minAirTemperatureC = number(Column.MIN_AIR_TEMPERATURE),
            maxDewPointC = number(Column.MAX_DEW_POINT),
            minDewPointC = number(Column.MIN_DEW_POINT),
            maxRelativeHumidityPct = integer(Column.MAX_RELATIVE_HUMIDITY),
            minRelativeHumidityPct = integer(Column.MIN_RELATIVE_HUMIDITY),
            relativeHumidityPct = integer(Column.RELATIVE_HUMIDITY),
            windDirectionDeg = integer(Column.WIND_DIRECTION),
            windGustMs = number(Column.WIND_GUST),
            windSpeedMs = number(Column.WIND_SPEED),
        )
    }

    // Older files use "2000-09-22;00:00", newer ones "2026/01/01;0000 UTC". Both are UTC.
    private fun instant(date: String, hour: String): Instant = try {
        val digits = hour.filter(Char::isDigit)
        require(digits.length == 4) { "Invalid hour '$hour'" }
        LocalDate.parse(date.trim().replace('/', '-'))
            .atTime(LocalTime.of(digits.take(2).toInt(), digits.drop(2).toInt()))
            .toInstant(ZoneOffset.UTC)
    } catch (e: DateTimeException) {
        throw IllegalArgumentException("Invalid date or hour '$date $hour'")
    }

    private fun measurement(raw: String): Double? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val value = text.replace(',', '.').toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number '$text'")
        return value.takeIf { it != MISSING }
    }

    private fun decimal(raw: String) = raw.trim().replace(',', '.').toDouble()

    private fun foundationDate(raw: String): LocalDate? = try {
        if ('/' in raw) LocalDate.parse(raw, SHORT_DATE) else LocalDate.parse(raw)
    } catch (e: DateTimeException) {
        null
    }

    private fun metadataKey(cell: String) = normalize(cell).substringBefore('(').substringBefore(':').trim()

    private fun normalize(text: String) =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "").uppercase().trim()

    // Portuguese header prefix (accents removed) for each translated column.
    private enum class Column(val header: String) {
        PRECIPITATION("PRECIPITACAO TOTAL"),
        STATION_PRESSURE("PRESSAO ATMOSFERICA AO NIVEL DA ESTACAO"),
        MAX_PRESSURE("PRESSAO ATMOSFERICA MAX"),
        MIN_PRESSURE("PRESSAO ATMOSFERICA MIN"),
        GLOBAL_RADIATION("RADIACAO GLOBAL"),
        AIR_TEMPERATURE("TEMPERATURA DO AR"),
        DEW_POINT("TEMPERATURA DO PONTO DE ORVALHO"),
        MAX_AIR_TEMPERATURE("TEMPERATURA MAXIMA"),
        MIN_AIR_TEMPERATURE("TEMPERATURA MINIMA"),
        MAX_DEW_POINT("TEMPERATURA ORVALHO MAX"),
        MIN_DEW_POINT("TEMPERATURA ORVALHO MIN"),
        MAX_RELATIVE_HUMIDITY("UMIDADE REL. MAX"),
        MIN_RELATIVE_HUMIDITY("UMIDADE REL. MIN"),
        RELATIVE_HUMIDITY("UMIDADE RELATIVA DO AR"),
        WIND_DIRECTION("VENTO, DIRECAO"),
        WIND_GUST("VENTO, RAJADA"),
        WIND_SPEED("VENTO, VELOCIDADE"),
    }

    private companion object {
        const val MISSING = -9999.0
        const val MAX_REJECTIONS = 100
        val DIACRITICS = Regex("\\p{M}+")

        // "22/09/00" means 2000-09-22; two-digit years 50-99 belong to the 1900s.
        val SHORT_DATE: DateTimeFormatter = DateTimeFormatterBuilder()
            .appendPattern("dd/MM/")
            .appendValueReduced(ChronoField.YEAR, 2, 4, 1950)
            .toFormatter()
    }
}
