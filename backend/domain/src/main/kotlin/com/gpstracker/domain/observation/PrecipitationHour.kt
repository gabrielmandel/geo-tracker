package com.gpstracker.domain.observation

import java.time.Instant

// observedAt is the end of the hour; null means the gauge did not report.
data class PrecipitationHour(val observedAt: Instant, val precipitationMm: Double?)
