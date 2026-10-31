package com.gpstracker.api

import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers

// Full stack against a real PostgreSQL: Flyway schema, CSV import rules, queries and R runs.
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ImportAndQueryIntegrationTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Test
    @Order(1)
    fun `imports a station file once and rejects the same file again`() {
        val file = csv("a801-2026.csv", fixture("layout-2026-a801.csv"))

        mvc.multipart("/imports") { file(file) }.andExpect {
            status { isCreated() }
            jsonPath("$.station.code") { value("A801") }
            jsonPath("$.rowsRejected") { value(0) }
        }
        mvc.multipart("/imports") { file(file) }.andExpect {
            status { isConflict() }
            jsonPath("$.importId") { exists() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
        }
    }

    @Test
    @Order(2)
    fun `a file whose data ends earlier never overwrites hours from a later export`() {
        val lines = fixture("layout-2026-a801.csv").toString(Charsets.ISO_8859_1).lines()
        // Header (9 lines) + first 5 hours, with the 03:00 rain changed: an older export of the same hours.
        val older = lines.take(14).toMutableList()
        older[12] = older[12].replaceFirst(Regex("^(2026/01/01;0300 UTC;)[^;]*;"), "$19,9;")

        mvc.multipart("/imports") { file(csv("a801-older.csv", older.joinToString("\n").toByteArray(Charsets.ISO_8859_1))) }
            .andExpect {
                status { isCreated() }
                jsonPath("$.rowsWritten") { value(0) }
            }
        val stored = jdbc.sql(
            "SELECT precipitation_mm FROM station_observations o JOIN stations s ON s.id = o.station_id " +
                "WHERE s.code = 'A801' AND o.observed_at = '2026-01-01T03:00:00Z'",
        ).query(java.math.BigDecimal::class.java).single()
        org.assertj.core.api.Assertions.assertThat(stored.toDouble()).isNotEqualTo(9.9)
    }

    @Test
    @Order(3)
    fun `station search, coverage and aggregates`() {
        mvc.get("/weather-stations") { param("q", "jardim") }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(1))
            jsonPath("$[0].code") { value("A801") }
        }
        val id = stationId("A801")
        mvc.get("/weather-stations/$id/coverage").andExpect {
            status { isOk() }
            jsonPath("$.firstObservation") { value("2026-01-01T00:00:00Z") }
        }
        mvc.get("/weather-stations/$id/aggregates") {
            param("period", "day")
            param("from", "2026-01-01")
            param("to", "2026-01-02")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].periodStart") { value("2026-01-01") }
            jsonPath("$[0].observationHours") { value(24) }
        }
        mvc.get("/imports") { param("stationId", "$id") }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(2) }
        }
    }

    @Test
    @Order(4)
    fun `calculates R, lists runs and marks them outdated after a newer import`() {
        val request = """{"stationCode":"A801","periodStart":"2026-01-01","periodEnd":"2026-01-31","preset":"RUSLE_HOURLY_I60"}"""
        mvc.post("/erosivity-calculations") {
            contentType = MediaType.APPLICATION_JSON
            content = request
        }.andExpect {
            status { isCreated() }
            jsonPath("$.parameters.i30Factor") { value(closeTo(1.0, 1e-9)) }
            jsonPath("$.outdated") { value(false) }
        }
        val id = stationId("A801")
        mvc.get("/erosivity-calculations") { param("stationId", "$id") }.andExpect {
            jsonPath("$", hasSize<Any>(1))
            jsonPath("$[0].outdated") { value(false) }
        }

        // A later export (one more hour) for the same period makes the run outdated.
        val newer = fixture("layout-2026-a801.csv").toString(Charsets.ISO_8859_1).trimEnd() +
            "\n2026/01/02;0700 UTC;0;1011;1011;1010,9;0;22;20;22;21;20;19;90;88;89;100;2;1;\n"
        mvc.multipart("/imports") { file(csv("a801-newer.csv", newer.toByteArray(Charsets.ISO_8859_1))) }
            .andExpect { status { isCreated() } }
        mvc.get("/erosivity-calculations") { param("stationId", "$id") }.andExpect {
            jsonPath("$[0].outdated") { value(true) }
        }
    }

    @Test
    @Order(5)
    fun `errors use the ProblemDetail format`() {
        mvc.get("/weather-stations/987654").andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value(containsString("987654")) }
        }
        mvc.get("/weather-stations") { param("minAgeYears", "999") }.andExpect { status { isBadRequest() } }
        mvc.post("/erosivity-calculations") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"stationCode":"A801","periodStart":"2026-01-01","periodEnd":"2026-01-31","parameters":{"i30Factor":50}}"""
        }.andExpect { status { isBadRequest() } }
    }

    private fun stationId(code: String): Long =
        jdbc.sql("SELECT id FROM stations WHERE code = :code").param("code", code).query(Long::class.java).single()

    private fun csv(name: String, bytes: ByteArray) = MockMultipartFile("file", name, "text/csv", bytes)

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream("inmet/$name")).use { it.readBytes() }
}
