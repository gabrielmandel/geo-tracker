package com.gpstracker.domain.erosivity

import java.time.Instant

// Every rainfall event found, erosive or not; startedAt/endedAt bound the rainy hours (UTC).
data class RainfallEvent(
    val startedAt: Instant,
    val endedAt: Instant,
    val precipitationMm: Double,
    val maxHourlyMm: Double,
    val missingHours: Int,
    val kineticEnergyMjHa: Double,
    val i30MmH: Double,
    val ei30: Double,
    val erosive: Boolean,
)

// One UTC calendar year: ei30Sum is the annual erosivity (MJ mm ha-1 h-1 yr-1) from erosive events only.
data class AnnualErosivity(
    val year: Int,
    val precipitationMm: Double,
    val missingHours: Int,
    val erosiveEvents: Int,
    val ei30Sum: Double,
    val included: Boolean,
)

class ErosivityResult(
    val method: String,
    val methodVersion: Int,
    val parameters: RusleParameters,
    val events: List<RainfallEvent>,
    val years: List<AnnualErosivity>,
    val rFactor: Double?,
) {
    val yearsUsed: Int get() = years.count { it.included }
}
