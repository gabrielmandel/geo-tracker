package com.gpstracker.application.erosivity

import com.gpstracker.application.imports.ArchiveImportedEvent
import com.gpstracker.application.observation.StationObservationRepository
import com.gpstracker.application.station.WeatherStationRepository
import com.gpstracker.domain.erosivity.ErosivityCalculator
import com.gpstracker.domain.erosivity.ErosivityPeriod
import com.gpstracker.domain.erosivity.RusleParameters
import com.gpstracker.domain.erosivity.StationErosivityYear
import com.gpstracker.domain.erosivity.cacheKey
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

// Keeps one row per station and year with the default RUSLE parameters, the input of every R map.
@Service
class ErosivityCacheService(
    private val stations: WeatherStationRepository,
    private val observations: StationObservationRepository,
    private val cache: ErosivityYearCache,
    private val refreshes: CacheRefreshRepository,
    private val transaction: TransactionOperations,
) : DisposableBean {

    val parameters = RusleParameters()
    val parametersKey = parameters.cacheKey()
    private val calculator = ErosivityCalculator(parameters)
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "erosivity-cache") }
    private val changes = AtomicLong()

    // Bumped whenever cached years change, so results derived from them (R maps) know they are stale.
    val version: Long get() = changes.get()

    fun status(): CacheStatus = cache.status(parametersKey)

    fun latestRefresh(): CacheRefresh? = refreshes.findLatest()

    fun findRefresh(id: Long): CacheRefresh = refreshes.findById(id) ?: throw CacheRefreshNotFoundException(id)

    // Queued jobs run one at a time; onlyStale skips stations whose cached years are newer than their imports.
    fun queueRefresh(onlyStale: Boolean): CacheRefresh {
        val refresh = refreshes.create(parametersKey, onlyStale)
        worker.execute { process(refresh) }
        return refresh
    }

    @EventListener
    fun onArchiveImported(event: ArchiveImportedEvent) {
        log.info("Archive import ${event.archiveImportId} added ${event.filesImported} file(s); refreshing stale erosivity cache")
        queueRefresh(onlyStale = true)
    }

    @EventListener(ContextRefreshedEvent::class)
    fun failInterrupted() {
        val failed = refreshes.failUnfinished("Interrupted by an application restart")
        if (failed > 0) log.warn("Marked $failed unfinished erosivity cache refresh(es) as FAILED")
    }

    override fun destroy() {
        worker.shutdownNow()
    }

    private fun process(refresh: CacheRefresh) {
        try {
            val stationIds = if (refresh.onlyStale) cache.findStaleStationIds(parametersKey) else stations.findAll().map { it.id }
            refreshes.start(refresh.id, stationIds.size)
            log.info("Erosivity cache refresh ${refresh.id}: ${stationIds.size} station(s)")
            var done = 0
            var failed = 0
            for (stationId in stationIds) {
                runCatching { refreshStation(stationId) }
                    .onFailure {
                        failed++
                        log.warn("Erosivity cache: station $stationId failed: ${it.message}")
                    }
                done++
                refreshes.progress(refresh.id, done, failed)
            }
            refreshes.finish(refresh.id, RefreshStatus.COMPLETED, null)
            log.info("Erosivity cache refresh ${refresh.id} completed: $done station(s), $failed failed")
        } catch (e: Exception) {
            log.error("Erosivity cache refresh ${refresh.id} failed", e)
            runCatching { refreshes.finish(refresh.id, RefreshStatus.FAILED, e.message ?: e.javaClass.simpleName) }
        }
    }

    // The whole record at once, so events crossing 31 Dec -> 1 Jan are split the same way as in a long run.
    fun refreshStation(stationId: Long) {
        // Taken before reading, so an import finishing meanwhile still makes the station stale.
        val computedAt = Instant.now()
        val hours = observations.findPrecipitation(stationId, FIRST_READING, computedAt.plusSeconds(86_400))
        val years = if (hours.none { it.precipitationMm != null }) {
            emptyList()
        } else {
            val firstYear = hours.first().observedAt.atOffset(ZoneOffset.UTC).year
            val lastYear = hours.last().observedAt.atOffset(ZoneOffset.UTC).year
            val period = ErosivityPeriod(LocalDate.of(firstYear, 1, 1), LocalDate.of(lastYear, 12, 31))
            calculator.calculate(period, hours).years.map {
                val hoursInYear = Year.of(it.year).length() * 24
                StationErosivityYear(
                    stationId = stationId,
                    year = it.year,
                    hoursInYear = hoursInYear,
                    precipitationHours = hoursInYear - it.missingHours,
                    precipitationMm = it.precipitationMm,
                    erosiveEvents = it.erosiveEvents,
                    ei30Sum = it.ei30Sum,
                )
            }.filter { it.precipitationHours > 0 }
        }
        transaction.executeWithoutResult { cache.replace(parametersKey, stationId, years, computedAt) }
        changes.incrementAndGet()
    }

    private companion object {
        val log = LogFactory.getLog(ErosivityCacheService::class.java)
        val FIRST_READING: Instant = LocalDate.of(1990, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
    }
}
