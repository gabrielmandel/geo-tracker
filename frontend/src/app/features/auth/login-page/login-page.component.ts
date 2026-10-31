import { AfterViewInit, Component, ElementRef, OnDestroy, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { decodeCoastlines, graticule, LonLat, parallel, projectPoint, smallCircle, visiblePath } from './globe';

const EARTH_RADIUS = 92;
const EARTH_VIEW_LATITUDE = 24;
const EARTH_START_LONGITUDE = -100;
const SPIN_DEGREES_PER_SECOND = 10;
const EARTH_GRID = graticule(30, 5);
const EQUATOR = [parallel(0)];
// Seattle (ping), São Paulo, Cairo, Tokyo — same order as the markers in the template.
const MARKERS: LonLat[] = [[-122.3, 47.6], [-46.6, -23.5], [31.2, 30], [139.7, 35.7]];

// Real scale: the Moon's radius is 0.273 of Earth's; its ~60 Earth-radii distance is compressed to fit.
const MOON_RADIUS = 25;
const MOON_ORBIT = { rx: 236, ry: 46, periodSeconds: 48, startAngle: Math.PI / 2 + 0.5 };
const MOON_VIEW_LATITUDE = 11; // camera elevation implied by the orbit ellipse: asin(46 / 236)
const MOON_GRID = graticule(45, 10);
// Selenographic [lon (east +), lat, angular radius]; radii are exaggerated so they read at this size.
const MOON_CRATERS = [
  [-15.6, 32.8, 18], // Mare Imbrium
  [17.5, 28, 11], // Mare Serenitatis
  [31.4, 8.5, 13], // Mare Tranquillitatis
  [59.1, 17, 9], // Mare Crisium
  [-20.1, 9.6, 7], // Copernicus
  [-11.2, -43.3, 7], // Tycho
  [147.9, 27.3, 9], // Mare Moscoviense
  [129.1, -20.4, 8], // Tsiolkovskiy
  [-157.4, -4, 12], // Korolev
  [-151.4, -36.1, 10], // Apollo
  [-128.7, 2.6, 11], // Hertzsprung
].map(([lon, lat, radius]) => smallCircle([lon, lat], radius));

@Component({
  selector: 'app-login-page',
  imports: [FormsModule, RouterLink, NgIcon, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './login-page.component.html',
})
export class LoginPageComponent implements AfterViewInit, OnDestroy {
  email = '';
  password = '';

  private readonly graticule = viewChild.required<ElementRef<SVGPathElement>>('graticule');
  private readonly equator = viewChild.required<ElementRef<SVGPathElement>>('equator');
  private readonly land = viewChild.required<ElementRef<SVGPathElement>>('land');
  private readonly markers = viewChild.required<ElementRef<SVGGElement>>('markers');
  private readonly moon = viewChild.required<ElementRef<SVGGElement>>('moon');
  private readonly moonBack = viewChild.required<ElementRef<SVGGElement>>('moonBack');
  private readonly moonFront = viewChild.required<ElementRef<SVGGElement>>('moonFront');
  private readonly moonGrid = viewChild.required<ElementRef<SVGPathElement>>('moonGrid');
  private readonly moonCraters = viewChild.required<ElementRef<SVGPathElement>>('moonCraters');
  private coastlines: LonLat[][] = [];
  private frame = 0;

  constructor(private readonly router: Router) {}

  ngAfterViewInit(): void {
    const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
    void import('world-atlas/land-110m.json').then(({ default: atlas }) => {
      this.coastlines = decodeCoastlines(atlas);
      if (reducedMotion) this.render(0);
    });

    if (reducedMotion) {
      this.render(0);
      return;
    }

    const svg = this.markers().nativeElement.ownerSVGElement!;
    const start = performance.now();
    const tick = (now: number): void => {
      if (svg.clientWidth > 0) this.render((now - start) / 1000);
      this.frame = requestAnimationFrame(tick);
    };
    this.frame = requestAnimationFrame(tick);
  }

  ngOnDestroy(): void {
    cancelAnimationFrame(this.frame);
  }

  connect(): void {
    void this.router.navigateByUrl('/home');
  }

  private render(seconds: number): void {
    // A decreasing center longitude turns the sphere west-to-east.
    const earthLon = EARTH_START_LONGITUDE - seconds * SPIN_DEGREES_PER_SECOND;
    this.graticule().nativeElement.setAttribute('d', visiblePath(EARTH_GRID, EARTH_RADIUS, earthLon, EARTH_VIEW_LATITUDE));
    this.equator().nativeElement.setAttribute('d', visiblePath(EQUATOR, EARTH_RADIUS, earthLon, EARTH_VIEW_LATITUDE));
    this.land().nativeElement.setAttribute('d', visiblePath(this.coastlines, EARTH_RADIUS, earthLon, EARTH_VIEW_LATITUDE));

    const markers = this.markers().nativeElement.children;
    MARKERS.forEach((coordinates, index) => {
      const marker = markers[index] as SVGGraphicsElement;
      const [x, y, z] = projectPoint(coordinates, earthLon, EARTH_VIEW_LATITUDE);
      marker.setAttribute('transform', `translate(${x * EARTH_RADIUS} ${-y * EARTH_RADIUS})`);
      marker.style.opacity = String(Math.min(Math.max(z / 0.15, 0), 1));
    });

    const angle = MOON_ORBIT.startAngle - (seconds / MOON_ORBIT.periodSeconds) * 2 * Math.PI;
    const depth = Math.sin(angle);
    // Tidally locked: longitude 0 always faces Earth, so the camera sees longitude (orbit angle + 90°).
    const moonLon = (angle * 180) / Math.PI + 90;
    this.moonGrid().nativeElement.setAttribute('d', visiblePath(MOON_GRID, MOON_RADIUS, moonLon, MOON_VIEW_LATITUDE));
    this.moonCraters().nativeElement.setAttribute('d', visiblePath(MOON_CRATERS, MOON_RADIUS, moonLon, MOON_VIEW_LATITUDE));

    const moon = this.moon().nativeElement;
    const layer = (depth > 0 ? this.moonFront() : this.moonBack()).nativeElement;
    moon.setAttribute('transform', `translate(${MOON_ORBIT.rx * Math.cos(angle)} ${MOON_ORBIT.ry * depth}) scale(${1 + 0.08 * depth})`);
    if (moon.parentNode !== layer) layer.appendChild(moon);
  }
}