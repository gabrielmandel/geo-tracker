// QGIS-style color ramps (ColorBrewer and Viridis) and classification for single-band rasters.

export type RampName = 'Spectral' | 'YlOrRd' | 'Viridis' | 'RdYlBu';
export type ClassMode = 'equal' | 'quantile';

export const RAMPS: Record<RampName, string[]> = {
  // Reversed so low erosivity is blue and high is red.
  Spectral: ['#3288bd', '#66c2a5', '#abdda4', '#e6f598', '#fee08b', '#fdae61', '#f46d43', '#d53e4f'],
  YlOrRd: ['#ffffcc', '#ffeda0', '#fed976', '#feb24c', '#fd8d3c', '#fc4e2a', '#e31a1c', '#b10026'],
  Viridis: ['#440154', '#46327e', '#365c8d', '#277f8e', '#1fa187', '#4ac16d', '#a0da39', '#fde725'],
  RdYlBu: ['#4575b4', '#74add1', '#abd9e9', '#e0f3f8', '#fee090', '#fdae61', '#f46d43', '#d73027'],
};

export interface RasterClass {
  from: number;
  to: number;
  color: string;
}

/** Splits [min, max] into `count` classes; quantile classes need the valid values. */
export function classify(values: readonly number[], count: number, mode: ClassMode, ramp: RampName): RasterClass[] {
  if (!values.length) return [];
  const sorted = [...values].sort((a, b) => a - b);
  const min = sorted[0];
  const max = sorted[sorted.length - 1];
  const breaks: number[] = [min];
  for (let i = 1; i < count; i++) {
    breaks.push(mode === 'equal' ? min + ((max - min) * i) / count : sorted[Math.floor((sorted.length - 1) * (i / count))]);
  }
  breaks.push(max);
  const unique = breaks.filter((value, index) => index === 0 || value > breaks[index - 1]);
  const classes = unique.length - 1;
  if (classes < 1) return [{ from: min, to: max, color: rampColor(ramp, 0.5) }];
  return Array.from({ length: classes }, (_, i) => ({
    from: unique[i],
    to: unique[i + 1],
    color: rampColor(ramp, classes === 1 ? 0.5 : i / (classes - 1)),
  }));
}

export function classColor(classes: readonly RasterClass[], value: number): string | null {
  for (const range of classes) if (value <= range.to) return range.color;
  return classes.at(-1)?.color ?? null;
}

/** Color at position t (0..1) along the ramp, linearly interpolated between its stops. */
export function rampColor(ramp: RampName, t: number): string {
  const stops = RAMPS[ramp];
  const position = Math.min(Math.max(t, 0), 1) * (stops.length - 1);
  const index = Math.min(Math.floor(position), stops.length - 2);
  const [r1, g1, b1] = hexToRgb(stops[index]);
  const [r2, g2, b2] = hexToRgb(stops[index + 1]);
  const f = position - index;
  const mix = (a: number, b: number) => Math.round(a + (b - a) * f);
  return `rgb(${mix(r1, r2)}, ${mix(g1, g2)}, ${mix(b1, b2)})`;
}

export function rampGradient(ramp: RampName): string {
  return `linear-gradient(90deg, ${RAMPS[ramp].join(', ')})`;
}

/** Packs a CSS rgb()/hex color into the 0xAABBGGRR layout of a little-endian ImageData Uint32 view. */
export function packColor(color: string, alpha = 255): number {
  const [r, g, b] = color.startsWith('#') ? hexToRgb(color) : (color.match(/\d+/g) ?? ['0', '0', '0']).map(Number);
  return ((alpha << 24) | (b << 16) | (g << 8) | r) >>> 0;
}

function hexToRgb(hex: string): [number, number, number] {
  const value = parseInt(hex.slice(1), 16);
  return [(value >> 16) & 255, (value >> 8) & 255, value & 255];
}
