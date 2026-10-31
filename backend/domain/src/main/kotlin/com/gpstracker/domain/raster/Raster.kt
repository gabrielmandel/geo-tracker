package com.gpstracker.domain.raster

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

// Regular lon/lat grid (EPSG:4326). Row 0 is the northern edge, as in ESRI ASCII grids and image rows.
data class GridSpec(val west: Double, val north: Double, val cellSize: Double, val columns: Int, val rows: Int) {
    init {
        require(cellSize > 0) { "cellSize must be positive" }
        require(columns > 0 && rows > 0) { "The grid must have at least one cell" }
    }

    val east: Double get() = west + columns * cellSize
    val south: Double get() = north - rows * cellSize

    fun centerLongitude(column: Int) = west + (column + 0.5) * cellSize

    fun centerLatitude(row: Int) = north - (row + 0.5) * cellSize

    companion object {
        // Snaps the extent outwards to whole cells so grids with the same cell size line up.
        fun covering(bounds: Bounds, cellSize: Double): GridSpec {
            val west = floor(bounds.west / cellSize) * cellSize
            val north = ceil(bounds.north / cellSize) * cellSize
            val columns = ceil((bounds.east - west) / cellSize - 1e-9).toInt()
            val rows = ceil((north - bounds.south) / cellSize - 1e-9).toInt()
            return GridSpec(west, north, cellSize, columns, rows)
        }
    }
}

data class Bounds(val west: Double, val south: Double, val east: Double, val north: Double)

// Polygon rings as [lon0, lat0, lon1, lat1, ...]; holes and multipolygons follow the even-odd rule.
class Boundary(val rings: List<DoubleArray>) {
    init {
        require(rings.isNotEmpty() && rings.all { it.size >= 6 && it.size % 2 == 0 }) { "A boundary needs rings of 3+ points" }
    }

    // Extent of the largest ring, i.e. the mainland, so distant oceanic islands don't stretch the grid.
    fun mainBounds(): Bounds {
        val ring = rings.maxBy { abs(area(it)) }
        var west = Double.MAX_VALUE
        var east = -Double.MAX_VALUE
        var south = Double.MAX_VALUE
        var north = -Double.MAX_VALUE
        for (i in ring.indices step 2) {
            west = minOf(west, ring[i]); east = maxOf(east, ring[i])
            south = minOf(south, ring[i + 1]); north = maxOf(north, ring[i + 1])
        }
        return Bounds(west, south, east, north)
    }

    // Scanline fill: for each row, sorted edge crossings at the cell-centre latitude delimit the inside spans.
    fun mask(grid: GridSpec): BooleanArray {
        val inside = BooleanArray(grid.columns * grid.rows)
        val crossings = ArrayList<Double>()
        for (row in 0 until grid.rows) {
            val latitude = grid.centerLatitude(row)
            crossings.clear()
            for (ring in rings) {
                val points = ring.size / 2
                var j = points - 1
                for (i in 0 until points) {
                    val yi = ring[2 * i + 1]
                    val yj = ring[2 * j + 1]
                    if ((yi > latitude) != (yj > latitude)) {
                        val xi = ring[2 * i]
                        val xj = ring[2 * j]
                        crossings += xi + (latitude - yi) * (xj - xi) / (yj - yi)
                    }
                    j = i
                }
            }
            crossings.sort()
            for (k in 0 until crossings.size - 1 step 2) {
                val first = maxOf(0, ceil((crossings[k] - grid.west) / grid.cellSize - 0.5).toInt())
                val last = minOf(grid.columns - 1, floor((crossings[k + 1] - grid.west) / grid.cellSize - 0.5).toInt())
                for (column in first..last) inside[row * grid.columns + column] = true
            }
        }
        return inside
    }

    private fun area(ring: DoubleArray): Double {
        var sum = 0.0
        val points = ring.size / 2
        var j = points - 1
        for (i in 0 until points) {
            sum += (ring[2 * j] + ring[2 * i]) * (ring[2 * j + 1] - ring[2 * i + 1])
            j = i
        }
        return sum / 2
    }
}

data class Sample(val longitude: Double, val latitude: Double, val value: Double)

// Inverse distance weighting (Shepard), as QGIS' IDW interpolation, limited to the nearest samples within a radius.
class IdwInterpolator(
    private val power: Double = 2.0,
    private val maxDistanceKm: Double = 400.0,
    private val maxNeighbors: Int = 12,
) {
    init {
        require(power in 0.5..6.0) { "power must be between 0.5 and 6" }
        require(maxDistanceKm in 10.0..3000.0) { "maxDistanceKm must be between 10 and 3000" }
        require(maxNeighbors in 1..64) { "maxNeighbors must be between 1 and 64" }
    }

    // NaN marks no data: outside the mask or no sample within maxDistanceKm.
    fun interpolate(samples: List<Sample>, grid: GridSpec, mask: BooleanArray? = null): FloatArray {
        val values = FloatArray(grid.columns * grid.rows) { Float.NaN }
        if (samples.isEmpty()) return values
        val distances = DoubleArray(maxNeighbors)
        val weights = DoubleArray(maxNeighbors)
        val sampleValues = DoubleArray(maxNeighbors)
        val maxLatitudeDelta = maxDistanceKm / KM_PER_DEGREE_LATITUDE
        for (row in 0 until grid.rows) {
            val latitude = grid.centerLatitude(row)
            val kmPerDegreeLongitude = KM_PER_DEGREE_LATITUDE * cos(Math.toRadians(latitude))
            for (column in 0 until grid.columns) {
                val index = row * grid.columns + column
                if (mask != null && !mask[index]) continue
                val longitude = grid.centerLongitude(column)
                var found = 0
                var exact = Double.NaN
                for (sample in samples) {
                    val dLat = sample.latitude - latitude
                    if (abs(dLat) > maxLatitudeDelta) continue
                    val dx = (sample.longitude - longitude) * kmPerDegreeLongitude
                    val dy = dLat * KM_PER_DEGREE_LATITUDE
                    val distance = sqrt(dx * dx + dy * dy)
                    if (distance > maxDistanceKm) continue
                    if (distance < 1e-6) {
                        exact = sample.value
                        break
                    }
                    found = insertNearest(distances, sampleValues, found, distance, sample.value)
                }
                values[index] = when {
                    !exact.isNaN() -> exact.toFloat()
                    found == 0 -> Float.NaN
                    else -> {
                        var weightSum = 0.0
                        var valueSum = 0.0
                        for (n in 0 until found) {
                            weights[n] = 1.0 / distances[n].pow(power)
                            weightSum += weights[n]
                            valueSum += weights[n] * sampleValues[n]
                        }
                        (valueSum / weightSum).toFloat()
                    }
                }
            }
        }
        return values
    }

    // Keeps the k nearest samples sorted by distance (k is small, so insertion is cheapest).
    private fun insertNearest(distances: DoubleArray, values: DoubleArray, count: Int, distance: Double, value: Double): Int {
        if (count == maxNeighbors && distance >= distances[count - 1]) return count
        var position = if (count < maxNeighbors) count else count - 1
        while (position > 0 && distances[position - 1] > distance) {
            distances[position] = distances[position - 1]
            values[position] = values[position - 1]
            position--
        }
        distances[position] = distance
        values[position] = value
        return minOf(count + 1, maxNeighbors)
    }

    private companion object {
        const val KM_PER_DEGREE_LATITUDE = 111.2
    }
}
