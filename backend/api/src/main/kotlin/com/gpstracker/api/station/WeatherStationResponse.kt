package com.gpstracker.api.station

import com.gpstracker.domain.station.WeatherStation
import java.time.Instant
import java.time.LocalDate

data class WeatherStationResponse(
    val id: Long,
    val code: String,
    val name: String,
    val region: String,
    val state: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val foundedOn: LocalDate?,
    val detailsAsOf: Instant,
)

fun WeatherStation.toResponse() = WeatherStationResponse(
    id = id,
    code = details.code,
    name = details.name,
    region = details.region,
    state = details.state,
    latitude = details.coordinate.latitude,
    longitude = details.coordinate.longitude,
    altitude = details.altitudeMeters,
    foundedOn = details.foundedOn,
    detailsAsOf = detailsAsOf,
)
