package com.gpstracker.domain.station

import com.gpstracker.domain.shared.BaseEntity
import java.time.Instant

class WeatherStation(
    override val id: Long,
    val details: StationDetails,
    val detailsAsOf: Instant,
) : BaseEntity<Long>() {

    // INMET renames and relocates stations; details from a file with older data never win.
    fun withDetails(newDetails: StationDetails, asOf: Instant): WeatherStation =
        if (asOf < detailsAsOf) this else WeatherStation(id, newDetails, asOf)
}
