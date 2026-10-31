package com.gpstracker.application.imports

import com.gpstracker.domain.observation.HourlyObservation
import com.gpstracker.domain.station.StationDetails
import java.io.InputStream

interface StationFileReader {
    // Throws InvalidStationFileException when the file structure (metadata or header) is unusable.
    fun read(input: InputStream): StationFile
}

class StationFile(
    val details: StationDetails,
    val observations: List<HourlyObservation>,
    val rowsRead: Int,
    val rowsRejected: Int,
    val rejections: List<String>,
)
