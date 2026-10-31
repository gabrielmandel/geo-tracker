package com.gpstracker.application.station

import com.gpstracker.application.shared.BaseRepository
import com.gpstracker.domain.station.StationDetails
import com.gpstracker.domain.station.WeatherStation
import java.time.Instant
import java.time.LocalDate

// Every criterion is optional; text matches the code or name, case-insensitively.
data class StationFilter(
    val state: String? = null,
    val region: String? = null,
    val text: String? = null,
    val foundedOnOrBefore: LocalDate? = null,
)

interface WeatherStationRepository : BaseRepository<WeatherStation, Long> {
    fun findByCode(code: String): WeatherStation?

    fun search(filter: StationFilter): List<WeatherStation>

    fun create(details: StationDetails, detailsAsOf: Instant): WeatherStation
}
