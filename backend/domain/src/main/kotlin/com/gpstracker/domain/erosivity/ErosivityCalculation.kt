package com.gpstracker.domain.erosivity

import com.gpstracker.domain.shared.BaseEntity
import java.time.Instant

enum class CalculationStatus { PENDING, RUNNING, COMPLETED, FAILED }

class ErosivityCalculation(
    override val id: Long,
    val stationId: Long,
    val period: ErosivityPeriod,
    val method: String,
    val methodVersion: Int,
    val parameters: RusleParameters,
    val status: CalculationStatus,
    val rFactor: Double?,
    val yearsUsed: Int,
    val createdAt: Instant,
    val finishedAt: Instant?,
    // Data imported after the run changed hours inside its period, so a new run may give another R.
    val outdated: Boolean = false,
) : BaseEntity<Long>()
