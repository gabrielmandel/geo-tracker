export type LonLat = [lon: number, lat: number];
type Point = [x: number, y: number, z: number];

const RADIANS = Math.PI / 180;

/** Orthographic projection onto the unit disk: x right, y up, z toward the viewer (z < 0 is the far side). */
export function projectPoint([lon, lat]: LonLat, centerLon: number, centerLat: number): Point {
  const phi = lat * RADIANS;
  const lambda = (lon - centerLon) * RADIANS;
  const phi0 = centerLat * RADIANS;
  const cosPhi = Math.cos(phi);
  return [
    cosPhi * Math.sin(lambda),
    Math.cos(phi0) * Math.sin(phi) - Math.sin(phi0) * cosPhi * Math.cos(lambda),
    Math.sin(phi0) * Math.sin(phi) + Math.cos(phi0) * cosPhi * Math.cos(lambda),
  ];
}

/** SVG path data for the parts of the lines on the visible hemisphere, cut at the horizon. */
export function visiblePath(lines: LonLat[][], radius: number, centerLon: number, centerLat: number): string {
  const format = ([x, y]: Point): string => `${(x * radius).toFixed(1)} ${(-y * radius).toFixed(1)}`;
  let d = '';
  for (const line of lines) {
    let previous: Point | undefined;
    for (const coordinates of line) {
      const point = projectPoint(coordinates, centerLon, centerLat);
      if (point[2] >= 0) {
        if (previous && previous[2] >= 0) d += `L${format(point)}`;
        else d += previous ? `M${format(horizon(previous, point))}L${format(point)}` : `M${format(point)}`;
      } else if (previous && previous[2] >= 0) {
        d += `L${format(horizon(previous, point))}`;
      }
      previous = point;
    }
  }
  return d;
}

function horizon(a: Point, b: Point): Point {
  const t = a[2] / (a[2] - b[2]);
  const x = a[0] + (b[0] - a[0]) * t;
  const y = a[1] + (b[1] - a[1]) * t;
  const length = Math.hypot(x, y) || 1;
  return [x / length, y / length, 0];
}

function range(from: number, to: number, step: number): number[] {
  const values: number[] = [];
  for (let value = from; value <= to; value += step) values.push(value);
  return values;
}

export function parallel(lat: number, sample = 5): LonLat[] {
  return range(-180, 180, sample).map((lon): LonLat => [lon, lat]);
}

export function graticule(step: number, sample: number): LonLat[][] {
  const meridians = range(-180, 180 - step, step).map((lon) => range(-80, 80, sample).map((lat): LonLat => [lon, lat]));
  const parallels = range(step - 90, 90 - step, step).map((lat) => parallel(lat, sample));
  return [...meridians, ...parallels];
}

/** Ring of points at an angular radius (degrees) around a center on the sphere. */
export function smallCircle([lon, lat]: LonLat, radius: number): LonLat[] {
  const phi0 = lat * RADIANS;
  const r = radius * RADIANS;
  return range(0, 360, 20).map((bearing): LonLat => {
    const b = bearing * RADIANS;
    const phi = Math.asin(Math.sin(phi0) * Math.cos(r) + Math.cos(phi0) * Math.sin(r) * Math.cos(b));
    const lambda = Math.atan2(Math.sin(b) * Math.sin(r) * Math.cos(phi0), Math.cos(r) - Math.sin(phi0) * Math.sin(phi));
    return [lon + lambda / RADIANS, phi / RADIANS];
  });
}

interface QuantizedTopology {
  arcs: number[][][];
  transform: { scale: number[]; translate: number[] };
}

/** Decodes TopoJSON arcs into coastline polylines. */
export function decodeCoastlines({ arcs, transform }: QuantizedTopology): LonLat[][] {
  const [scaleX, scaleY] = transform.scale;
  const [translateX, translateY] = transform.translate;
  const lines: LonLat[][] = [];
  for (const arc of arcs) {
    let x = 0;
    let y = 0;
    let line: LonLat[] = [];
    for (const [dx, dy] of arc) {
      const point: LonLat = [(x += dx) * scaleX + translateX, (y += dy) * scaleY + translateY];
      const previous = line[line.length - 1];
      // Natural Earth cuts polygons along the antimeridian; those edges are not coastline.
      if (previous && Math.abs(previous[0]) > 179.9 && Math.abs(point[0]) > 179.9) {
        lines.push(line);
        line = [];
      }
      line.push(point);
    }
    lines.push(line);
  }
  return lines;
}
