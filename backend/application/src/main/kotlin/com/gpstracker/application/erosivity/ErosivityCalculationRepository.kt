package com.gpstracker.application.erosivity

import com.gpstracker.application.shared.BaseRepository
import com.gpstracker.domain.erosivity.AnnualErosivity
import com.gpstracker.domain.erosivity.ErosivityCalculation
import com.gpstracker.domain.erosivity.ErosivityPeriod
import com.gpstracker.domain.erosivity.ErosivityResult
import com.gpstracker.domain.erosivity.RainfallEvent

interface ErosivityCalculationRepository : BaseRepository<ErosivityCalculation, Long> {
    // Stores the completed run together with its rainfall events and annual results.
    fun create(stationId: Long, period: ErosivityPeriod, result: ErosivityResult): ErosivityCalculation

    // Newest first.
    fun findByStation(stationId: Long): List<ErosivityCalculation>

    fun findAnnualResults(calculationId: Long): List<AnnualErosivity>

    fun findEvents(calculationId: Long): List<RainfallEvent>
}
