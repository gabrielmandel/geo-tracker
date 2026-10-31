import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NgIcon } from '@ng-icons/core';
import { CacheStatus, ErosivityMap, ErosivityMapQuery } from '@data-access/erosivity/erosivity.model';
import { ClassMode, RampName, RAMPS, rampGradient } from '../../r-factor/color-ramp';

export interface RasterStyle {
  ramp: RampName;
  classes: number;
  mode: ClassMode;
  opacity: number;
  showStations: boolean;
  showStates: boolean;
}

export interface ExportLinks {
  grid: string;
  stationsGeoJson: string;
  stationsCsv: string;
}

const CURRENT_YEAR = new Date().getFullYear();

@Component({
  selector: 'app-r-factor-panel',
  imports: [
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSliderModule,
    MatTooltipModule,
    NgIcon,
  ],
  templateUrl: './r-factor-panel.component.html',
  host: { class: 'r-panel' },
})
export class RFactorPanelComponent {
  readonly cache = input<CacheStatus | null>(null);
  readonly map = input<ErosivityMap | null>(null);
  readonly loading = input(false);
  readonly error = input('');
  readonly style = input.required<RasterStyle>();
  readonly exports = input<ExportLinks | null>(null);
  readonly generate = output<ErosivityMapQuery>();
  readonly styleChange = output<RasterStyle>();
  readonly refreshCache = output<void>();
  readonly closed = output<void>();

  protected readonly ramps = Object.keys(RAMPS) as RampName[];
  protected readonly gradient = rampGradient;
  protected readonly coverages = [0.6, 0.7, 0.8, 0.9];
  protected readonly cellSizes = [0.25, 0.1, 0.05];
  protected readonly powers = [1, 2, 3];
  protected readonly distances = [200, 300, 400, 600, 900];

  // Last complete year by default; the period covers 25 years like the station R runs.
  protected readonly toYear = signal(CURRENT_YEAR - 1);
  protected readonly fromYear = signal(CURRENT_YEAR - 25);
  protected readonly minCoverage = signal(0.8);
  protected readonly minYears = signal<number | null>(null);
  protected readonly cellSize = signal(0.1);
  protected readonly power = signal(2);
  protected readonly maxDistanceKm = signal(400);

  protected readonly years = computed(() => {
    const first = this.cache()?.firstYear ?? 2000;
    const last = Math.min(this.cache()?.lastYear ?? CURRENT_YEAR, CURRENT_YEAR);
    return Array.from({ length: last - first + 1 }, (_, i) => last - i);
  });
  protected readonly refreshing = computed(() => {
    const status = this.cache()?.latestRefresh?.status;
    return status === 'QUEUED' || status === 'PROCESSING';
  });
  protected readonly refreshProgress = computed(() => {
    const refresh = this.cache()?.latestRefresh;
    return refresh?.stationsTotal ? (100 * refresh.stationsDone) / refresh.stationsTotal : 0;
  });
  protected readonly periodInvalid = computed(() => this.fromYear() > this.toYear());
  // Collapsing keeps the raster visible on small screens, where the panel covers the map.
  protected readonly collapsed = signal(false);

  constructor() {
    // Keep the default period inside the years the cache actually has.
    effect(() => {
      const first = this.cache()?.firstYear;
      if (first && this.fromYear() < first) this.fromYear.set(first);
    });
  }

  protected submit(): void {
    if (this.periodInvalid()) return;
    this.generate.emit({
      fromYear: this.fromYear(),
      toYear: this.toYear(),
      minCoverage: this.minCoverage(),
      minYears: this.minYears(),
      cellSize: this.cellSize(),
      power: this.power(),
      maxDistanceKm: this.maxDistanceKm(),
      maxNeighbors: 12,
    });
  }

  protected setMinYears(value: string): void {
    const years = Number(value);
    this.minYears.set(value.trim() === '' || !Number.isInteger(years) || years < 1 ? null : years);
  }

  protected updateStyle(change: Partial<RasterStyle>): void {
    this.styleChange.emit({ ...this.style(), ...change });
  }
}
