package com.gpstracker.api

import com.gpstracker.application.erosivity.ErosivityCacheService
import com.gpstracker.application.erosivity.ErosivityYearCache
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThan
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.testcontainers.junit.jupiter.Testcontainers

// Annual erosivity cache behind the R map: staleness, stations without rain, and the raster endpoint.
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class ErosivityCacheIntegrationTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var cacheService: ErosivityCacheService

    @Autowired
    lateinit var cache: ErosivityYearCache

    @Test
    fun `stations are stale until computed, even without rain readings, and again after a newer export`() {
        val withRain = importStation("Z901", rain = true)
        val withoutRain = importStation("Z902", rain = false)
        assertThat(staleIds()).contains(withRain, withoutRain)

        cacheService.refreshStation(withRain)
        cacheService.refreshStation(withoutRain)

        assertThat(staleIds()).doesNotContain(withRain, withoutRain)
        assertThat(cachedYears(withRain)).isEqualTo(1)
        assertThat(cachedYears(withoutRain)).isZero()

        val newer = stationCsv("Z901", rain = true).trimEnd() + "\n2026/01/02;0700 UTC;0;1011;1011;1010,9;0;22;20;22;21;20;19;90;88;89;100;2;1;\n"
        upload("z901-newer.csv", newer)
        assertThat(staleIds()).contains(withRain).doesNotContain(withoutRain)
    }

    @Test
    fun `builds an R raster clipped to Brazil from the cached years`() {
        val station = importStation("Z903", rain = true)
        cacheService.refreshStation(station)

        mvc.get("/erosivity-maps") {
            param("fromYear", "2026")
            param("toYear", "2026")
            param("minCoverage", "0")
            param("minYears", "1")
            param("cellSize", "0.5")
        }.andExpect {
            status { isOk() }
            jsonPath("$.stats.stations") { value(greaterThan(0)) }
            jsonPath("$.stats.cellsWithData") { value(greaterThan(0)) }
            jsonPath("$.stations[?(@.code == 'Z903')].rFactor") { exists() }
        }
    }

    private fun importStation(code: String, rain: Boolean): Long {
        upload("${code.lowercase()}.csv", stationCsv(code, rain))
        return jdbc.sql("SELECT id FROM stations WHERE code = :code").param("code", code).query(Long::class.java).single()
    }

    // The A801 fixture under another code and name (the database is shared with other test classes);
    // without rain, every hour keeps its other readings.
    private fun stationCsv(code: String, rain: Boolean): String {
        val lines = checkNotNull(javaClass.classLoader.getResourceAsStream("inmet/layout-2026-a801.csv"))
            .use { it.readBytes() }
            .toString(Charsets.ISO_8859_1)
            .lines()
            .toMutableList()
        lines[2] = "ESTACAO:;CACHE TEST $code"
        lines[3] = "CODIGO (WMO):;$code"
        if (!rain) {
            for (i in 9 until lines.size) lines[i] = lines[i].replaceFirst(Regex("^([^;]*;[^;]*;)[^;]*;"), "$1;")
        }
        return lines.joinToString("\n")
    }

    private fun upload(name: String, content: String) {
        mvc.multipart("/imports") { file(MockMultipartFile("file", name, "text/csv", content.toByteArray(Charsets.ISO_8859_1))) }
            .andExpect { status { isCreated() } }
    }

    private fun staleIds() = cache.findStaleStationIds(cacheService.parametersKey)

    private fun cachedYears(stationId: Long): Int =
        jdbc.sql("SELECT COUNT(*) FROM erosivity_station_years WHERE station_id = :id")
            .param("id", stationId)
            .query(Int::class.java)
            .single()
}
