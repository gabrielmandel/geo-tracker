package com.gpstracker.domain.station

import com.gpstracker.domain.shared.Coordinate
import java.time.LocalDate

data class StationDetails(
    val code: String,
    val name: String,
    val region: String,
    val state: String,
    val coordinate: Coordinate,
    val altitudeMeters: Double?,
    val foundedOn: LocalDate?,
) {
    init {
        require(code.isNotBlank()) { "Station code must not be blank" }
        require(name.isNotBlank()) { "Station name must not be blank" }
    }
}
