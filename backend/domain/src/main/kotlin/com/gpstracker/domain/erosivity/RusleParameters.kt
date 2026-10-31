package com.gpstracker.domain.erosivity

// RUSLE storm criteria (Renard et al. 1997); the 15-minute erosive test needs sub-hourly data and is not applied.
data class RusleParameters(
    val separationHours: Int = 6,
    val separationMaxMm: Double = 1.27,
    val erosiveMinMm: Double = 12.7,
    // Hourly data has no 30-minute peak: I30 = I60 x 2 x 0.85, the hourly-data adjustment used for southern Brazil.
    val i30Factor: Double = 1.7,
) {
    init {
        require(separationHours in 1..48) { "separationHours must be between 1 and 48" }
        require(separationMaxMm > 0 && separationMaxMm <= 20) { "separationMaxMm must be greater than 0 and at most 20" }
        require(erosiveMinMm > 0 && erosiveMinMm <= 100) { "erosiveMinMm must be greater than 0 and at most 100" }
        require(i30Factor > 0 && i30Factor <= 5) { "i30Factor must be greater than 0 and at most 5" }
    }

    companion object {
        // Named parameter sets; a run stores the values, never the preset name, so presets can evolve safely.
        val PRESETS: Map<String, RusleParameters> = linkedMapOf(
            "RUSLE_HOURLY" to RusleParameters(),
            "RUSLE_HOURLY_I60" to RusleParameters(i30Factor = 1.0),
            "RS_REGIONAL" to RusleParameters(separationMaxMm = 1.0, erosiveMinMm = 10.0),
        )
    }
}
