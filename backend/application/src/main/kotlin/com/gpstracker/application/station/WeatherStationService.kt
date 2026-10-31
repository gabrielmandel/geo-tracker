package com.gpstracker.application.station

import com.gpstracker.domain.station.WeatherStation
import org.springframework.stereotype.Service

@Service
class WeatherStationService(private val repository: WeatherStationRepository) {
    fun search(filter: StationFilter): List<WeatherStation> = repository.search(filter)

    fun findById(id: Long): WeatherStation = repository.findById(id) ?: throw StationNotFoundException(id.toString())
}
