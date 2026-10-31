package com.gpstracker.infrastructure.inmet

import com.gpstracker.application.imports.InvalidStationFileException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate

// Fixtures are the first lines of real INMET files, byte for byte (Latin-1).
class InmetCsvReaderTest {

    private val reader = InmetCsvReader()

    @Test
    fun `reads the layout used up to 2018`() {
        val file = read("layout-2000-a801.csv")

        with(file.details) {
            assertThat(code).isEqualTo("A801")
            assertThat(name).isEqualTo("PORTO ALEGRE")
            assertThat(region).isEqualTo("S")
            assertThat(state).isEqualTo("RS")
            assertThat(coordinate.latitude).isEqualTo(-30.05)
            assertThat(altitudeMeters).isEqualTo(46.97)
            assertThat(foundedOn).isEqualTo(LocalDate.of(2000, 9, 22))
        }
        assertThat(file.rowsRead).isEqualTo(31)
        assertThat(file.rowsRejected).isZero()
        val first = file.observations.first()
        assertThat(first.observedAt).isEqualTo(Instant.parse("2000-09-22T00:00:00Z"))
        // -9999 is a missing reading, never a value.
        assertThat(first.precipitationMm).isNull()
    }

    @Test
    fun `reads the layout used from 2019 on`() {
        val file = read("layout-2026-a801.csv")

        assertThat(file.details.name).isEqualTo("PORTO ALEGRE - JARDIM BOTANICO")
        assertThat(file.details.foundedOn).isEqualTo(LocalDate.of(2000, 9, 22))
        val first = file.observations.first()
        assertThat(first.observedAt).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
        assertThat(first.precipitationMm).isEqualTo(0.0)
        assertThat(first.stationPressureMb).isEqualTo(1010.8)
        assertThat(first.relativeHumidityPct).isEqualTo(82)
        assertThat(first.windSpeedMs).isEqualTo(2.4)
    }

    @Test
    fun `accepts the 2019 metadata keys with a literal question mark`() {
        val file = read("question-mark-keys-2019-a001.csv")

        assertThat(file.details.name).isEqualTo("BRASILIA")
        assertThat(file.details.region).isEqualTo("CO")
        assertThat(file.details.foundedOn).isEqualTo(LocalDate.of(2000, 5, 7))
        assertThat(file.observations.first().precipitationMm).isEqualTo(1.0)
    }

    @Test
    fun `stores a non numeric altitude as unknown`() {
        val file = read("altitude-f-2017-s108.csv")

        assertThat(file.details.code).isEqualTo("S108")
        assertThat(file.details.altitudeMeters).isNull()
    }

    @Test
    fun `rejects a bad row and keeps the rest of the file`() {
        val lines = fixture("layout-2026-a801.csv").toString(Charsets.ISO_8859_1).lines().toMutableList()
        lines[9] = lines[9].replaceFirst(";0;", ";abc;")

        val file = reader.read(ByteArrayInputStream(lines.joinToString("\n").toByteArray(Charsets.ISO_8859_1)))

        assertThat(file.rowsRejected).isEqualTo(1)
        assertThat(file.rejections.single()).startsWith("Line 10:")
        assertThat(file.observations).hasSize(file.rowsRead - 1)
    }

    @Test
    fun `a file without the INMET header is invalid`() {
        assertThatThrownBy { reader.read(ByteArrayInputStream("not;an;inmet;file\n1;2;3;4".toByteArray())) }
            .isInstanceOf(InvalidStationFileException::class.java)
    }

    private fun read(name: String) = reader.read(ByteArrayInputStream(fixture(name)))

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream("inmet/$name")) { "Missing fixture $name" }.use { it.readBytes() }
}
