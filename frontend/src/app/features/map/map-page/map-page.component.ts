import { DatePipe } from '@angular/common';
import { AfterViewInit, Component, computed, DestroyRef, inject, OnDestroy, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NgIcon } from '@ng-icons/core';
import * as L from 'leaflet';
import '@geoman-io/leaflet-geoman-free';
import { AppError } from '@core/http/app-error';
import { BoundaryService } from '@data-access/geo/boundary.service';
import { ErosivityMapApiService } from '@data-access/erosivity/erosivity-api.service';
import { CacheStatus, ErosivityMap, ErosivityMapQuery } from '@data-access/erosivity/erosivity.model';
import { Geofence } from '@data-access/geofences/geofence.model';
import { GeofenceService } from '@data-access/geofences/geofence.service';
import { WeatherStationApiService } from '@data-access/weather-stations/weather-station-api.service';
import { WeatherStation } from '@data-access/weather-stations/weather-station.model';
import { environment } from '@env/environment';
import { Subscription, switchMap, timer } from 'rxjs';
import { GeofenceListComponent } from '../components/geofence-list/geofence-list.component';
import { RFactorLegendComponent } from '../components/r-factor-legend/r-factor-legend.component';
import { ExportLinks, RasterStyle, RFactorPanelComponent } from '../components/r-factor-panel/r-factor-panel.component';
import { StationDetailsDialogComponent } from '../components/station-details-dialog/station-details-dialog.component';
import { DayRange } from '@shared/date-range-field/date-range-field.component';
import { StationLayersComponent } from '../components/station-layers/station-layers.component';
import { classColor, classify } from '../r-factor/color-ramp';
import { RasterGridLayer } from '../r-factor/raster-grid-layer';

const BRAZIL_CENTER: L.LatLngTuple = [-14.2, -51.9];
const GEOFENCE_COLORS = [['#27775f', '#5db08a'], ['#d57c5f', '#e98267'], ['#6688a0', '#9bb9cc']];
const STATION_RECENT_COLOR = '#d8f36a';
const STATION_ARCHIVE_COLOR = '#e98267';

// Picker dates are local midnight; station dates are UTC midnight, so compare calendar days.
const localDay = (date: Date): number => Date.UTC(date.getFullYear(), date.getMonth(), date.getDate());

@Component({
  selector: 'app-map-page',
  imports: [
    DatePipe,
    FormsModule,
    NgIcon,
    MatButtonModule,
    MatDialogModule,
    MatTooltipModule,
    GeofenceListComponent,
    StationLayersComponent,
    RFactorPanelComponent,
    RFactorLegendComponent,
  ],
  templateUrl: './map-page.component.html',
  host: { class: 'map-workspace' },
})
export class MapPageComponent implements AfterViewInit, OnDestroy {
  private readonly geofenceService = inject(GeofenceService);
  private readonly stationApi = inject(WeatherStationApiService);
  private readonly mapApi = inject(ErosivityMapApiService);
  private readonly boundaries = inject(BoundaryService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly geofencesEnabled = environment.features.geofences;

  readonly geofences = signal<Geofence[]>([]);
  readonly selectedGeofenceId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly drawing = signal(false);
  readonly pendingCoordinates = signal<[number, number][] | null>(null);
  readonly notice = signal('Map ready');
  readonly stations = signal<WeatherStation[]>([]);
  readonly stationsLoading = signal(false);
  readonly stationsLoadedAt = signal<Date | null>(null);
  readonly stationVisible = signal(true);
  readonly minimumAgeYears = signal<number | null>(null);
  readonly operatingRange = signal<DayRange>({ start: null, end: null });
  readonly filteredStations = computed(() => {
    const { start, end } = this.operatingRange();
    if (!start && !end) return this.stations();
    const from = start ? localDay(start) : -Infinity;
    const to = end ? localDay(end) : Infinity;
    return this.stations().filter(({ foundedOn }) => {
      if (!foundedOn) return false;
      const day = Date.parse(`${foundedOn}T00:00:00Z`);
      return day >= from && day <= to;
    });
  });
  readonly stationsWithRecentData = computed(() =>
    this.filteredStations().filter((station) => Date.now() - Date.parse(station.detailsAsOf) < 90 * 24 * 60 * 60 * 1000).length,
  );
  newGeofenceName = 'New polygon';

  // R factor raster (QGIS-like singleband pseudocolor layer).
  readonly rPanelOpen = signal(false);
  readonly rMap = signal<ErosivityMap | null>(null);
  readonly rLoading = signal(false);
  readonly rError = signal('');
  readonly rCache = signal<CacheStatus | null>(null);
  readonly rHoverValue = signal<number | null>(null);
  readonly rStyle = signal<RasterStyle>({ ramp: 'Spectral', classes: 7, mode: 'quantile', opacity: 0.75, showStations: true, showStates: true });
  readonly rQuery = signal<ErosivityMapQuery | null>(null);
  readonly rClasses = computed(() => {
    const map = this.rMap();
    if (!map) return [];
    const { ramp, classes, mode } = this.rStyle();
    return classify(map.values.filter((value): value is number => value !== null), classes, mode, ramp);
  });
  readonly rExports = computed<ExportLinks | null>(() => {
    const query = this.rQuery();
    if (!query) return null;
    return {
      grid: this.mapApi.exportUrl('grid.zip', query),
      stationsGeoJson: this.mapApi.exportUrl('stations.geojson', query),
      stationsCsv: this.mapApi.exportUrl('stations.csv', query),
    };
  });
  readonly rPeriod = computed(() => {
    const map = this.rMap();
    return map ? (map.fromYear === map.toYear ? `${map.fromYear}` : `${map.fromYear}–${map.toYear}`) : '';
  });

  private readonly rasterLayer = new RasterGridLayer({ pane: 'rasterPane', opacity: 0.75, keepBuffer: 4 });
  private readonly rStationLayer = L.layerGroup();
  private statesLayer?: L.GeoJSON;
  private cachePolling?: Subscription;

  private map?: L.Map;
  private pendingLayer?: L.Polygon;
  private readonly polygons = new Map<string, L.Polygon>();
  private readonly stationLayer = L.layerGroup();

  ngAfterViewInit(): void {
    const map = L.map('operations-map', { zoomControl: false, preferCanvas: true })
      .setView(BRAZIL_CENTER, 4)
      .locate({ setView: true, maxZoom: 10 });
    this.map = map;
    L.control.zoom({ position: 'bottomright' }).addTo(map);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map);
    this.stationLayer.addTo(map);
    // Raster under vector layers, as in a GIS layer stack.
    map.createPane('rasterPane').style.zIndex = '350';
    map.createPane('bordersPane').style.zIndex = '360';
    map.on('mousemove', (event: L.LeafletMouseEvent) => {
      if (this.rPanelOpen() && this.rMap()) this.rHoverValue.set(this.rasterLayer.valueAt(event.latlng));
    });
    map.getContainer().addEventListener('mouseleave', () => this.rHoverValue.set(null));
    map.pm.setLang('en');
    map.on('pm:create', (event: L.LeafletEvent) => {
      const layer = (event as L.LeafletEvent & { layer: L.Polygon }).layer;
      this.onPolygonCreated(layer);
    });
    setTimeout(() => map.invalidateSize(), 0);
    this.loadStations();
  }

  ngOnDestroy(): void {
    this.cachePolling?.unsubscribe();
    this.map?.remove();
  }

  toggleRFactorMap(): void {
    if (this.rPanelOpen()) {
      this.closeRFactorMap();
      return;
    }
    this.rPanelOpen.set(true);
    this.pollCacheStatus();
    if (this.rMap()) this.showRaster();
    this.notice.set('R factor map · choose a period and generate the raster');
  }

  closeRFactorMap(): void {
    this.rPanelOpen.set(false);
    this.cachePolling?.unsubscribe();
    this.rasterLayer.remove();
    this.rStationLayer.remove();
    this.statesLayer?.remove();
    this.rHoverValue.set(null);
    if (this.stationVisible() && this.map) this.stationLayer.addTo(this.map);
  }

  generateRFactorMap(query: ErosivityMapQuery): void {
    this.rLoading.set(true);
    this.rError.set('');
    this.mapApi.build(query)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (map) => {
          this.rQuery.set(query);
          this.rMap.set(map);
          this.rLoading.set(false);
          if (!map.stats.stations) {
            this.rError.set('No station meets the quality rules for this period. Lower the coverage or the minimum years.');
          }
          if (this.rPanelOpen()) this.showRaster();
          this.notice.set(
            `R factor ${map.fromYear}–${map.toYear} · ${map.stats.stations} stations · IDW p=${map.power}, ${map.maxDistanceKm} km, ${map.grid.cellSize}° pixels`,
          );
        },
        error: (error: AppError) => {
          this.rLoading.set(false);
          this.rError.set(error.message);
        },
      });
  }

  setRasterStyle(style: RasterStyle): void {
    this.rStyle.set(style);
    if (this.rPanelOpen() && this.rMap()) this.showRaster();
  }

  refreshRCache(): void {
    this.mapApi.refreshCache(true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.pollCacheStatus(),
        error: (error: AppError) => this.rError.set(error.message),
      });
  }

  loadGeofences(): void {
    if (!this.map) return;
    this.loading.set(true);
    this.geofenceService.getGeofences().subscribe((geofences) => {
      this.geofences.set(geofences);
      this.selectedGeofenceId.set(null);
      this.renderGeofences(geofences);
      this.loading.set(false);
      this.notice.set(`${geofences.length} geofences loaded`);
    });
  }

  setStationVisibility(visible: boolean): void {
    this.stationVisible.set(visible);
    if (!this.map) return;
    if (visible && !this.rPanelOpen()) this.stationLayer.addTo(this.map);
    else this.stationLayer.remove();
  }

  setMinimumAgeYears(years: number | null): void {
    this.minimumAgeYears.set(years);
    this.loadStations();
  }

  setOperatingRange(range: DayRange): void {
    this.operatingRange.set(range);
    const stations = this.filteredStations();
    this.renderStations(stations);
    this.notice.set(
      range.start || range.end
        ? `${stations.length} of ${this.stations().length} stations match the foundation-date range`
        : `${stations.length} database stations shown`,
    );
  }

  startDrawing(): void {
    if (!this.map) return;
    this.pendingCoordinates.set(null);
    this.pendingLayer = undefined;
    this.map.pm.enableDraw('Polygon', {
      snappable: true,
      templineStyle: { color: '#e98267', weight: 2 },
      hintlineStyle: { color: '#e98267', dashArray: [5, 5] },
      pathOptions: { color: '#e98267', fillColor: '#e98267', fillOpacity: 0.18, weight: 2 },
    });
    this.drawing.set(true);
    this.notice.set('Click the map to place fence points · double-click to finish');
  }

  saveGeofence(): void {
    const coordinates = this.pendingCoordinates();
    const name = this.newGeofenceName.trim();
    if (!coordinates || !name) return;
    this.geofenceService.saveGeofence({ name, coordinates }).subscribe((geofence) => {
      this.geofences.update((current) => [...current, geofence]);
      this.pendingLayer?.bindPopup(this.popupContent(geofence.name, [geofence.updatedAt]));
      if (this.pendingLayer) this.polygons.set(geofence.id, this.pendingLayer);
      this.pendingLayer = undefined;
      this.pendingCoordinates.set(null);
      this.newGeofenceName = 'New polygon';
      this.notice.set(`${geofence.name} saved to mock storage`);
    });
  }

  cancelDrawing(): void {
    if (this.pendingLayer) this.map?.removeLayer(this.pendingLayer);
    this.pendingLayer = undefined;
    this.pendingCoordinates.set(null);
    this.drawing.set(false);
    this.map?.pm.disableDraw();
    this.notice.set('Polygon discarded');
  }

  focusGeofence(id: string): void {
    const polygon = this.polygons.get(id);
    if (!polygon || !this.map) return;
    this.selectedGeofenceId.set(id);
    this.map.fitBounds(polygon.getBounds(), { padding: [48, 48], maxZoom: 16 });
    polygon.openPopup();
  }

  private loadStations(): void {
    this.stationsLoading.set(true);
    this.stationApi.findAll(this.minimumAgeYears())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (stations) => {
          this.stations.set(stations);
          this.renderStations(this.filteredStations());
          this.stationsLoadedAt.set(new Date());
          this.stationsLoading.set(false);
          this.notice.set(`${stations.length} database stations loaded`);
        },
        error: (error: AppError) => {
          this.stationsLoading.set(false);
          this.notice.set(`Weather stations unavailable · ${error.message}`);
        },
      });
  }

  private renderStations(stations: WeatherStation[]): void {
    this.stationLayer.clearLayers();
    for (const station of stations) {
      const recent = Date.now() - Date.parse(station.detailsAsOf) < 90 * 24 * 60 * 60 * 1000;
      const marker = L.circleMarker([station.latitude, station.longitude], {
        radius: 5,
        color: '#0a2823',
        weight: 1,
        fillColor: recent ? STATION_RECENT_COLOR : STATION_ARCHIVE_COLOR,
        fillOpacity: 0.95,
      });
      marker.bindTooltip(`${station.name} · ${station.code}`, { direction: 'top', sticky: true });
      marker.on('click', () => this.openStation(station));
      marker.addTo(this.stationLayer);
    }
  }

  private openStation(station: WeatherStation): void {
    this.dialog.open(StationDetailsDialogComponent, {
      data: station,
      panelClass: 'station-details-dialog',
      width: 'min(1080px, calc(100vw - 32px))',
      maxWidth: '1080px',
      autoFocus: false,
      ariaLabel: `${station.name} station details`,
    });
  }

  // Built with textContent so names from the API or the user can't inject HTML.
  private popupContent(title: string, details: string[]): HTMLElement {
    const content = document.createElement('div');
    content.className = 'map-popup';
    const heading = document.createElement('strong');
    heading.textContent = title;
    content.append(heading);
    for (const detail of details) {
      const line = document.createElement('small');
      line.textContent = detail;
      content.append(line);
    }
    return content;
  }

  private onPolygonCreated(layer: L.Polygon): void {
    const rings = layer.getLatLngs()[0] as L.LatLng[];
    if (rings.length < 3) return;
    this.pendingLayer = layer;
    this.pendingCoordinates.set(rings.map(({ lat, lng }) => [lat, lng]));
    this.drawing.set(false);
    this.notice.set('Name this boundary, then save it to mock storage');
  }

  private renderGeofences(geofences: Geofence[]): void {
    if (!this.map) return;
    for (const polygon of this.polygons.values()) this.map.removeLayer(polygon);
    this.polygons.clear();
    for (const [index, geofence] of geofences.entries()) {
      const [color, fillColor] = GEOFENCE_COLORS[index % GEOFENCE_COLORS.length];
      const polygon = L.polygon(geofence.coordinates, { color, fillColor, fillOpacity: 0.2, weight: 2 }).addTo(this.map);
      polygon.bindPopup(this.popupContent(geofence.name, [geofence.updatedAt]));
      this.polygons.set(geofence.id, polygon);
    }
    if (geofences.length) {
      const bounds = L.featureGroup([...this.polygons.values()]).getBounds();
      this.map.fitBounds(bounds, { padding: [55, 55], maxZoom: 14 });
    }
  }

  // While the cache builds, poll so the panel shows progress and the station count grows.
  private pollCacheStatus(): void {
    this.cachePolling?.unsubscribe();
    this.cachePolling = timer(0, 4000)
      .pipe(switchMap(() => this.mapApi.cacheStatus()), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (status) => {
          this.rCache.set(status);
          const state = status.latestRefresh?.status;
          if (state !== 'QUEUED' && state !== 'PROCESSING') this.cachePolling?.unsubscribe();
        },
        error: () => this.cachePolling?.unsubscribe(),
      });
  }

  private showRaster(): void {
    const map = this.map;
    const data = this.rMap();
    if (!map || !data) return;
    const style = this.rStyle();
    const classes = this.rClasses();
    this.rasterLayer.setRaster(data, classes).setOpacity(style.opacity).addTo(map);

    // Station markers would hide the raster; the R stations replace them while the layer is open.
    this.stationLayer.remove();
    this.rStationLayer.clearLayers();
    if (style.showStations) {
      const stationsById = new Map(this.stations().map((station) => [station.id, station]));
      for (const station of data.stations) {
        const marker = L.circleMarker([station.latitude, station.longitude], {
          radius: 4,
          color: '#0a2823',
          weight: 1,
          fillColor: classColor(classes, station.rFactor) ?? '#ffffff',
          fillOpacity: 1,
        }).bindTooltip(`${station.code} · R ${Math.round(station.rFactor).toLocaleString()} (${station.yearsUsed} yrs)`, { direction: 'top' });
        const details = stationsById.get(station.id);
        if (details) marker.on('click', () => this.openStation(details));
        marker.addTo(this.rStationLayer);
      }
      this.rStationLayer.addTo(map);
    } else {
      this.rStationLayer.remove();
    }

    if (style.showStates) this.showStateBorders(map);
    else this.statesLayer?.remove();
  }

  private showStateBorders(map: L.Map): void {
    if (this.statesLayer) {
      this.statesLayer.addTo(map);
      return;
    }
    this.boundaries.brazilianStates()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((states) => {
        this.statesLayer = L.geoJSON(states, {
          pane: 'bordersPane',
          interactive: false,
          style: { color: '#0a2823', weight: 1, opacity: 0.7, fill: false },
        });
        if (this.rPanelOpen() && this.rStyle().showStates) this.statesLayer.addTo(map);
      });
  }
}