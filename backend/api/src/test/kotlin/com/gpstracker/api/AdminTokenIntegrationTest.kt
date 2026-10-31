package com.gpstracker.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(properties = ["geo.admin.open=false", "geo.admin.token=test-secret"])
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class AdminTokenIntegrationTest {

    @Autowired
    lateinit var mvc: MockMvc

    private val notInmet = MockMultipartFile("file", "x.csv", "text/csv", "a;b\n1;2".toByteArray())

    @Test
    fun `bulk writes need the admin token while reads stay open`() {
        mvc.multipart("/imports") { file(notInmet) }.andExpect { status { isForbidden() } }
        mvc.multipart("/imports") {
            file(notInmet)
            header("X-Admin-Token", "wrong")
        }.andExpect { status { isForbidden() } }
        mvc.multipart("/imports") {
            file(notInmet)
            header("X-Admin-Token", "test-secret")
        }.andExpect { status { isUnprocessableContent() } }
        mvc.get("/weather-stations").andExpect { status { isOk() } }
        mvc.get("/actuator/health").andExpect { status { isOk() } }
    }
}
