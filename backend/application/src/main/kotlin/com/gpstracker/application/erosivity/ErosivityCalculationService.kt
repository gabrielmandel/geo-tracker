package com.gpstracker.application.erosivity

import com.gpstracker.application.observation.StationObservationRepository
import com.gpstracker.application.station.StationNotFoundException
import com.gpstracker.application.station.WeatherStationRepository
import com.gpstracker.domain.erosivity.AnnualErosivity
import com.gpstracker.domain.erosivity.ErosivityCalculation
import com.gpstracker.domain.erosivity.ErosivityCalculator
import com.gpstracker.domain.erosivity.ErosivityPeriod
import com.gpstracker.domain.erosivity.RainfallEvent
import com.gpstracker.domain.erosivity.RusleParameters
import com.gpstracker.domain.station.WeatherStation
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations

class ErosivityReport(val calculation: ErosivityCalculation, val station: WeatherStation, val years: List<AnnualErosivity>)

class NoPrecipitationDataException(stationCode: String) :
    RuntimeException("Station $stationCode has no precipitation data in the requested period")

class ErosivityCalculationNotFoundException(id: Long) : RuntimeException("Erosivity calculation $id was not found")

@Service
class ErosivityCalculationService(
    private val stations: WeatherStationRepository,
    private val observations: StationObservationRepository,
    private val calculations: ErosivityCalculationRepository,
    private val transaction: TransactionOperations,
) {
    // Every run is stored as a new calculation; earlier results are never overwritten.
    fun calculate(stationCode: String, period: ErosivityPeriod, parameters: RusleParameters = RusleParameters()): ErosivityReport {
        val station = stations.findByCode(stationCode) ?: throw StationNotFoundException(stationCode)
        return run(station, period, parameters)
    }

    // Same station and period as an earlier run, optionally with other parameters.
    fun rerun(id: Long, parameters: RusleParameters?): ErosivityReport {
        val previous = calculations.findById(id) ?: throw ErosivityCalculationNotFoundException(id)
        val station = checkNotNull(stations.findById(previous.stationId))
        return run(station, previous.period, parameters ?: previous.parameters)
    }

    fun findByStation(stationId: Long): List<ErosivityCalculation> {
        stations.findById(stationId) ?: throw StationNotFoundException(stationId.toString())
        return calculations.findByStation(stationId)
    }

    fun find(id: Long): ErosivityReport {
        val calculation = calculations.findById(id) ?: throw ErosivityCalculationNotFoundException(id)
        val station = checkNotNull(stations.findById(calculation.stationId))
        return ErosivityReport(calculation, station, calculations.findAnnualResults(id))
    }

    fun findEvents(id: Long): List<RainfallEvent> {
        calculations.findById(id) ?: throw ErosivityCalculationNotFoundException(id)
        return calculations.findEvents(id)
    }

    private fun run(station: WeatherStation, period: ErosivityPeriod, parameters: RusleParameters): ErosivityReport {
        val hours = observations.findPrecipitation(station.id, period.start, period.endExclusive)
        if (hours.none { it.precipitationMm != null }) throw NoPrecipitationDataException(station.details.code)

        val result = ErosivityCalculator(parameters).calculate(period, hours)
        val calculation = checkNotNull(transaction.execute { calculations.create(station.id, period, result) })
        return ErosivityReport(calculation, station, result.years)
    }
}
