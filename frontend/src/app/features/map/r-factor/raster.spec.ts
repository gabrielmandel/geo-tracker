import * as L from 'leaflet';
import { classColor, classify, packColor } from './color-ramp';
import { RasterGridLayer } from './raster-grid-layer';

describe('R factor raster', () => {
  it('builds equal-interval classes covering min to max', () => {
    const classes = classify([1000, 2000, 3000, 5000], 4, 'equal', 'Spectral');
    expect(classes.map((c) => [c.from, c.to])).toEqual([
      [1000, 2000],
      [2000, 3000],
      [3000, 4000],
      [4000, 5000],
    ]);
  });

  it('builds quantile classes and merges repeated breaks', () => {
    const classes = classify([5, 5, 5, 5, 5, 5, 10], 4, 'quantile', 'Viridis');
    expect(classes.map((c) => [c.from, c.to])).toEqual([[5, 10]]);
    expect(classColor(classes, 7)).toBe(classes[0].color);
  });

  it('packs colors for little-endian ImageData', () => {
    expect(packColor('rgb(1, 2, 3)')).toBe(0xff030201);
    expect(packColor('#102030', 128)).toBe(0x80302010);
  });

  it('reads the cell under a point, row-major from the north-west corner', () => {
    const layer = new RasterGridLayer().setRaster(
      {
        grid: { west: -60, south: -32, east: -58, north: -30, cellSize: 1, columns: 2, rows: 2 },
        values: [1, 2, 3, null],
      },
      classify([1, 2, 3], 3, 'equal', 'Spectral'),
    );
    expect(layer.valueAt(L.latLng(-30.5, -59.5))).toBe(1);
    expect(layer.valueAt(L.latLng(-30.5, -58.5))).toBe(2);
    expect(layer.valueAt(L.latLng(-31.5, -59.5))).toBe(3);
    expect(layer.valueAt(L.latLng(-31.5, -58.5))).toBeNull();
    expect(layer.valueAt(L.latLng(-29.5, -59.5))).toBeNull();
  });
});
