package com.gpstracker.api.erosivity

import com.gpstracker.api.shared.Csv
import com.gpstracker.application.erosivity.ErosivityCalculationService
import com.gpstracker.application.erosivity.ErosivityReport
import com.gpstracker.domain.erosivity.ErosivityPeriod
import com.gpstracker.domain.erosivity.RusleParameters
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/erosivity-calculations")
class ErosivityCalculationController(private val service: ErosivityCalculationService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun calculate(@RequestBody request: ErosivityCalculationRequest): ErosivityCalculationResponse {
        require(request.stationCode.isNotBlank()) { "stationCode is required" }
        val period = ErosivityPeriod(request.periodStart, request.periodEnd)
        val parameters = resolveParameters(request.preset, request.parameters)
        return service.calculate(request.stationCode.trim().uppercase(), period, parameters).toResponse()
    }

    @GetMapping
    fun findByStation(@RequestParam stationId: Long): List<ErosivityCalculationSummaryResponse> =
        service.findByStation(stationId).map { it.toSummaryResponse() }

    @GetMapping("/presets")
    fun presets(): List<ParameterPresetResponse> =
        RusleParameters.PRESETS.map { (name, parameters) -> ParameterPresetResponse(name, parameters.toResponse()) }

    @GetMapping("/{id}")
    fun find(@PathVariable id: Long): ErosivityCalculationResponse = service.find(id).toResponse()

    @GetMapping("/{id}/events")
    fun events(@PathVariable id: Long): List<RainfallEventResponse> = service.findEvents(id).map { it.toResponse() }

    // New run for the same station and period; without a body it repeats the original parameters.
    @PostMapping("/{id}/rerun")
    @ResponseStatus(HttpStatus.CREATED)
    fun rerun(@PathVariable id: Long, @RequestBody(required = false) request: ErosivityRerunRequest?): ErosivityCalculationResponse {
        val previous = service.find(id).calculation.parameters
        val parameters = resolveParameters(request?.preset, request?.parameters, previous)
        return service.rerun(id, parameters).toResponse()
    }

    @GetMapping("/{id}/annual.csv")
    fun annualCsv(@PathVariable id: Long): ResponseEntity<String> {
        val report = service.find(id)
        val code = report.station.details.code
        val rows = report.years.map {
            listOf(code, it.year, it.ei30Sum, it.erosiveEvents, it.precipitationMm, it.missingHours, it.included)
        }
        val header = listOf("station_code", "year", "erosivity", "erosive_events", "precipitation_mm", "missing_hours", "included")
        return Csv.attachment("${fileStem(report)}_annual.csv", Csv.write(header, rows))
    }

    @GetMapping("/{id}/events.csv")
    fun eventsCsv(@PathVariable id: Long): ResponseEntity<String> {
        val report = service.find(id)
        val rows = service.findEvents(id).map {
            listOf(it.startedAt, it.endedAt, it.precipitationMm, it.maxHourlyMm, it.missingHours, it.kineticEnergyMjHa, it.i30MmH, it.ei30, it.erosive)
        }
        val header = listOf("started_at_utc", "ended_at_utc", "precipitation_mm", "max_hourly_mm", "missing_hours", "kinetic_energy_mj_ha", "i30_mm_h", "ei30", "erosive")
        return Csv.attachment("${fileStem(report)}_events.csv", Csv.write(header, rows))
    }

    // Station point with R and one r_<year> property per included year, flat so GIS tools read them as columns.
    @GetMapping("/{id}/station.geojson")
    fun stationGeoJson(@PathVariable id: Long): ResponseEntity<Map<String, Any?>> {
        val report = service.find(id)
        val details = report.station.details
        val properties = linkedMapOf<String, Any?>(
            "code" to details.code,
            "name" to details.name,
            "state" to details.state,
            "r_factor" to report.calculation.rFactor,
            "unit" to R_UNIT,
            "period_start" to report.calculation.period.from.toString(),
            "period_end" to report.calculation.period.to.toString(),
            "years_used" to report.calculation.yearsUsed,
            "i30_factor" to report.calculation.parameters.i30Factor,
            "calculation_id" to report.calculation.id,
        )
        report.years.filter { it.included }.forEach { properties["r_${it.year}"] = it.ei30Sum }
        val feature = mapOf(
            "type" to "Feature",
            "geometry" to mapOf("type" to "Point", "coordinates" to listOf(details.coordinate.longitude, details.coordinate.latitude)),
            "properties" to properties,
        )
        val body = mapOf("type" to "FeatureCollection", "features" to listOf(feature))
        return Csv.download("${fileStem(report)}.geojson", GEO_JSON, body)
    }

    private fun fileStem(report: ErosivityReport) = with(report.calculation) {
        "${report.station.details.code}_R_${period.from.year}-${period.to.year}_run$id"
    }

    private companion object {
        val GEO_JSON = MediaType("application", "geo+json")
    }
}
