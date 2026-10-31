package com.gpstracker.api

import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Testcontainers

// The frontend generates its API types from this document, so Kotlin nullability must reach it.
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class OpenApiContractIntegrationTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `non-null Kotlin properties are required and nullable ones are not`() {
        mvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.components.schemas.ErosivityMapResponse.required", hasItems("fromYear", "grid", "values", "stations"))
            jsonPath("$.components.schemas.WeatherStationResponse.required", hasItems("id", "code", "latitude"))
            jsonPath("$.components.schemas.WeatherStationResponse.required", not(hasItem("altitude")))
            jsonPath("$.components.schemas.MapStatsResponse.required", not(hasItem("min")))
        }
    }

    @Test
    fun `property names match the JSON the API sends`() {
        mvc.get("/v3/api-docs").andExpect {
            jsonPath("$.components.schemas.MapStationResponse.required", hasItem("rFactor"))
            jsonPath("$.components.schemas.MapStationResponse.properties.rfactor") { doesNotExist() }
            jsonPath("$.components.schemas.ErosivityCalculationResponse.properties.rfactor") { doesNotExist() }
            jsonPath("$.components.schemas.ErosivityCalculationSummaryResponse.properties.rfactor") { doesNotExist() }
        }
    }
}
