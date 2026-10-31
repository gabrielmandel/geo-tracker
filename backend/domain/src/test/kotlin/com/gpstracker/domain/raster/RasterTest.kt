package com.gpstracker.domain.raster

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class RasterTest {

    @Test
    fun `grid snaps outwards to whole cells`() {
        val grid = GridSpec.covering(Bounds(west = -50.05, south = -30.02, east = -49.71, north = -29.66), 0.1)

        assertThat(grid.west).isCloseTo(-50.1, within(1e-9))
        assertThat(grid.north).isCloseTo(-29.6, within(1e-9))
        assertThat(grid.east).isGreaterThanOrEqualTo(-49.71)
        assertThat(grid.south).isLessThanOrEqualTo(-30.02)
    }

    @Test
    fun `scanline mask keeps cell centres inside the polygon and honours holes`() {
        val grid = GridSpec(west = 0.0, north = 10.0, cellSize = 1.0, columns = 10, rows = 10)
        val square = doubleArrayOf(2.0, 2.0, 8.0, 2.0, 8.0, 8.0, 2.0, 8.0)
        val hole = doubleArrayOf(4.0, 4.0, 6.0, 4.0, 6.0, 6.0, 4.0, 6.0)

        val mask = Boundary(listOf(square, hole)).mask(grid)

        fun inside(lon: Double, lat: Double) = mask[(10 - lat.toInt() - 1) * 10 + lon.toInt()]
        assertThat(mask.count { it }).isEqualTo(36 - 4)
        assertThat(inside(2.5, 2.5)).isTrue()
        assertThat(inside(4.5, 4.5)).isFalse()
        assertThat(inside(1.5, 1.5)).isFalse()
    }

    @Test
    fun `main bounds come from the largest ring`() {
        val mainland = doubleArrayOf(-60.0, -30.0, -40.0, -30.0, -40.0, 0.0, -60.0, 0.0)
        val island = doubleArrayOf(-30.0, -4.0, -29.9, -4.0, -29.9, -3.9)

        assertThat(Boundary(listOf(island, mainland)).mainBounds()).isEqualTo(Bounds(-60.0, -30.0, -40.0, 0.0))
    }

    @Test
    fun `IDW returns the sample value on top of it and a weighted value between samples`() {
        val grid = GridSpec(west = 0.0, north = 1.0, cellSize = 1.0, columns = 3, rows = 1)
        val samples = listOf(Sample(0.5, 0.5, 100.0), Sample(2.5, 0.5, 300.0))

        val values = IdwInterpolator(power = 2.0, maxDistanceKm = 1000.0).interpolate(samples, grid)

        assertThat(values[0]).isEqualTo(100f)
        assertThat(values[1]).isCloseTo(200f, within(0.01f))
        assertThat(values[2]).isEqualTo(300f)
    }

    @Test
    fun `cells beyond the search radius or outside the mask have no data`() {
        val grid = GridSpec(west = 0.0, north = 1.0, cellSize = 1.0, columns = 10, rows = 1)
        val samples = listOf(Sample(0.5, 0.5, 50.0))
        val mask = BooleanArray(10) { it != 1 }

        val values = IdwInterpolator(maxDistanceKm = 300.0).interpolate(samples, grid, mask)

        assertThat(values[0]).isEqualTo(50f)
        assertThat(values[1]).isNaN()
        assertThat(values[2]).isEqualTo(50f)
        assertThat(values[5]).isNaN()
    }

    @Test
    fun `only the nearest samples are used`() {
        val grid = GridSpec(west = 0.0, north = 1.0, cellSize = 1.0, columns = 1, rows = 1)
        val samples = listOf(Sample(0.6, 0.5, 10.0), Sample(1.5, 0.5, 1000.0), Sample(0.4, 0.5, 10.0))

        val values = IdwInterpolator(maxNeighbors = 2, maxDistanceKm = 1000.0).interpolate(samples, grid)

        assertThat(values[0]).isEqualTo(10f)
    }
}
