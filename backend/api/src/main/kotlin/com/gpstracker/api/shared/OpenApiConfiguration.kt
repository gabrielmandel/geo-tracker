package com.gpstracker.api.shared

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {
    @Bean
    fun geoTrackerOpenApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("GEO TRACKER API")
            .version("v1")
            .description(
                "INMET automatic weather stations imported from the yearly CSV archives: hourly observations, " +
                    "rainfall erosivity (RUSLE R factor) per station and interpolated R maps.",
            )
            .license(License().name("Open source")),
    )
}
