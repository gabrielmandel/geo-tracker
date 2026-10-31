package com.gpstracker.application.erosivity

import com.gpstracker.domain.erosivity.StationErosivityYear
import com.gpstracker.domain.raster.Boundary
import java.time.Instant

interface ErosivityYearCache {
    // Replaces every cached year of the station for the parameter set and records when its data was read.
    fun replace(parametersKey: String, stationId: Long, years: List<StationErosivityYear>, computedAt: Instant)

    fun findYears(parametersKey: String, fromYear: Int, toYear: Int): List<StationErosivityYear>

    // Stations never computed, or with a completed import newer than their last computation.
    fun findStaleStationIds(parametersKey: String): List<Long>

    fun status(parametersKey: String): CacheStatus
}

data class CacheStatus(
    val cachedStations: Int,
    val staleStations: Int,
    val firstYear: Int?,
    val lastYear: Int?,
    val computedAt: Instant?,
)

enum class RefreshStatus { QUEUED, PROCESSING, COMPLETED, FAILED }

data class CacheRefresh(
    val id: Long,
    val parametersKey: String,
    val onlyStale: Boolean,
    val status: RefreshStatus,
    val stationsTotal: Int?,
    val stationsDone: Int,
    val stationsFailed: Int,
    val failureReason: String?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
)

interface CacheRefreshRepository {
    fun create(parametersKey: String, onlyStale: Boolean): CacheRefresh

    fun findById(id: Long): CacheRefresh?

    fun findLatest(): CacheRefresh?

    fun start(id: Long, stationsTotal: Int)

    fun progress(id: Long, done: Int, failed: Int)

    fun finish(id: Long, status: RefreshStatus, failureReason: String?)

    fun failUnfinished(reason: String): Int
}

// Country outline used to clip interpolated rasters.
fun interface BoundaryProvider {
    fun brazil(): Boundary
}

class CacheRefreshNotFoundException(id: Long) : RuntimeException("Erosivity cache refresh $id was not found")
