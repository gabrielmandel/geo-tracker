import * as L from 'leaflet';
import { RasterGrid } from '@data-access/erosivity/erosivity.model';
import { classColor, packColor, RasterClass } from './color-ramp';

export interface RasterData {
  grid: RasterGrid;
  values: readonly (number | null)[];
}

/**
 * Paints a lon/lat grid onto Web Mercator tiles pixel by pixel (nearest cell), like QGIS' on-the-fly reprojection:
 * in Mercator a pixel's longitude depends only on its column and its latitude only on its row.
 */
export class RasterGridLayer extends L.GridLayer {
  private data: RasterData | null = null;
  private cellColors = new Uint32Array(0);

  setRaster(data: RasterData, classes: readonly RasterClass[]): this {
    this.data = data;
    this.cellColors = new Uint32Array(data.values.length);
    const cache = new Map<string, number>();
    data.values.forEach((value, index) => {
      if (value === null) return;
      const color = classColor(classes, value);
      if (!color) return;
      let packed = cache.get(color);
      if (packed === undefined) {
        packed = packColor(color);
        cache.set(color, packed);
      }
      this.cellColors[index] = packed;
    });
    return this.redraw();
  }

  /** R of the cell under a point, or null outside the grid / without data. */
  valueAt(latLng: L.LatLng): number | null {
    const index = this.data ? cellIndex(this.data.grid, latLng.lat, latLng.lng) : -1;
    return index < 0 ? null : (this.data?.values[index] ?? null);
  }

  protected override createTile(coords: L.Coords): HTMLElement {
    const size = this.getTileSize();
    const canvas = L.DomUtil.create('canvas', 'raster-tile') as HTMLCanvasElement;
    canvas.width = size.x;
    canvas.height = size.y;
    const data = this.data;
    const map = this._map;
    if (!data || !map) return canvas;

    const { grid } = data;
    const columns = new Int32Array(size.x);
    const rows = new Int32Array(size.y);
    const originX = coords.x * size.x;
    const originY = coords.y * size.y;
    for (let x = 0; x < size.x; x++) {
      const longitude = map.unproject(L.point(originX + x + 0.5, originY), coords.z).lng;
      const column = Math.floor((longitude - grid.west) / grid.cellSize);
      columns[x] = column >= 0 && column < grid.columns ? column : -1;
    }
    for (let y = 0; y < size.y; y++) {
      const latitude = map.unproject(L.point(originX, originY + y + 0.5), coords.z).lat;
      const row = Math.floor((grid.north - latitude) / grid.cellSize);
      rows[y] = row >= 0 && row < grid.rows ? row : -1;
    }

    const context = canvas.getContext('2d');
    if (!context) return canvas;
    const image = context.createImageData(size.x, size.y);
    const pixels = new Uint32Array(image.data.buffer);
    for (let y = 0; y < size.y; y++) {
      const row = rows[y];
      if (row < 0) continue;
      const rowOffset = row * grid.columns;
      const pixelOffset = y * size.x;
      for (let x = 0; x < size.x; x++) {
        const column = columns[x];
        if (column >= 0) pixels[pixelOffset + x] = this.cellColors[rowOffset + column];
      }
    }
    context.putImageData(image, 0, 0);
    return canvas;
  }
}

function cellIndex(grid: RasterGrid, latitude: number, longitude: number): number {
  const column = Math.floor((longitude - grid.west) / grid.cellSize);
  const row = Math.floor((grid.north - latitude) / grid.cellSize);
  if (column < 0 || column >= grid.columns || row < 0 || row >= grid.rows) return -1;
  return row * grid.columns + column;
}
