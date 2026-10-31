package com.gpstracker.domain.imports

import java.time.Instant

data class ImportSummary(
    val stationId: Long,
    val periodStart: Instant,
    val periodEnd: Instant,
    val rowsRead: Int,
    val rowsWritten: Int,
    val rowsSkipped: Int,
    val rowsRejected: Int,
    val missingPrecipitationRows: Int,
    val rejections: List<String>,
)
