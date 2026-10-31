import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NgIcon } from '@ng-icons/core';
import { forkJoin } from 'rxjs';
import { AppError } from '@core/http/app-error';
import { ErosivityCalculationApiService } from '@data-access/erosivity/erosivity-api.service';
import {
  CalculationParameters,
  ErosivityCalculation,
  ErosivityCalculationSummary,
  ParameterPreset,
} from '@data-access/erosivity/erosivity.model';
import { WeatherStationApiService } from '@data-access/weather-stations/weather-station-api.service';
import { AnnualStationData, WeatherStation } from '@data-access/weather-stations/weather-station.model';

// A year counts as complete when at least 80% of its hours have a precipitation reading.
const COMPLETE_YEAR_COVERAGE = 0.8;

const hoursInYear = (year: number) => ((year % 4 === 0 && year % 100 !== 0) || year % 400 === 0 ? 8784 : 8760);

@Component({
  selector: 'app-station-r-factor',
  imports: [
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
    NgIcon,
  ],
  templateUrl: './station-r-factor.component.html',
  host: { class: 'r-tab' },
})
export class StationRFactorComponent implements OnInit {
  readonly station = input.required<WeatherStation>();

  private readonly stationApi = inject(WeatherStationApiService);
  protected readonly api = inject(ErosivityCalculationApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly years = signal<number[]>([]);
  protected readonly completeYears = signal<Set<number>>(new Set());
  protected readonly fromYear = signal<number | null>(null);
  protected readonly toYear = signal<number | null>(null);
  protected readonly presets = signal<ParameterPreset[]>([]);
  protected readonly preset = signal('RUSLE_HOURLY');
  protected readonly parameters = signal<CalculationParameters | null>(null);
  protected readonly showAdvanced = signal(false);
  protected readonly result = signal<ErosivityCalculation | null>(null);
  protected readonly history = signal<ErosivityCalculationSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly calculating = signal(false);
  protected readonly error = signal('');

  protected readonly yearColumns = ['year', 'erosivity', 'erosiveEvents', 'precipitationMm', 'missingHours', 'included'];
  protected readonly historyColumns = ['createdAt', 'period', 'parameters', 'rFactor', 'yearsUsed', 'actions'];
  protected readonly periodInvalid = computed(() => {
    const from = this.fromYear();
    const to = this.toYear();
    return from === null || to === null || from > to;
  });
  protected readonly periodHasGaps = computed(() => {
    const from = this.fromYear();
    const to = this.toYear();
    if (from === null || to === null) return false;
    for (let year = from; year <= to; year++) if (!this.completeYears().has(year)) return true;
    return false;
  });
  protected readonly maxErosivity = computed(() => Math.max(1, ...(this.result()?.years.map((year) => year.erosivity) ?? [1])));

  ngOnInit(): void {
    const station = this.station();
    forkJoin([this.stationApi.findAnnualData(station.id), this.api.presets(), this.api.findByStation(station.id)])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ([annual, presets, history]) => {
          this.applyCoverage(annual);
          this.presets.set(presets);
          this.selectPreset('RUSLE_HOURLY');
          this.history.set(history);
          this.loading.set(false);
          if (history.length) this.open(history[0].id);
        },
        error: (error: AppError) => {
          this.error.set(error.message);
          this.loading.set(false);
        },
      });
  }

  protected selectPreset(name: string): void {
    this.preset.set(name);
    const preset = this.presets().find((candidate) => candidate.name === name);
    if (preset) this.parameters.set({ ...preset.parameters });
  }

  protected setParameter(key: 'separationHours' | 'separationMaxMm' | 'erosiveMinMm' | 'i30Factor', value: string): void {
    const number = Number(value);
    const current = this.parameters();
    if (!current || value.trim() === '' || !Number.isFinite(number)) return;
    this.parameters.set({ ...current, [key]: number });
  }

  protected calculate(): void {
    const from = this.fromYear();
    const to = this.toYear();
    const parameters = this.parameters();
    if (from === null || to === null || from > to || !parameters) return;
    this.calculating.set(true);
    this.error.set('');
    this.api.calculate({
      stationCode: this.station().code,
      periodStart: `${from}-01-01`,
      periodEnd: `${to}-12-31`,
      preset: this.preset(),
      parameters: {
        separationHours: parameters.separationHours,
        separationMaxMm: parameters.separationMaxMm,
        erosiveMinMm: parameters.erosiveMinMm,
        i30Factor: parameters.i30Factor,
      },
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (calculation) => this.showResult(calculation),
        error: (error: AppError) => this.fail(error),
      });
  }

  protected rerun(id: number): void {
    this.calculating.set(true);
    this.error.set('');
    this.api.rerun(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (calculation) => this.showResult(calculation),
        error: (error: AppError) => this.fail(error),
      });
  }

  protected open(id: number): void {
    this.api.find(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (calculation) => this.result.set(calculation),
        error: (error: AppError) => this.error.set(error.message),
      });
  }

  protected barHeight(erosivity: number): number {
    return Math.max(1, (100 * erosivity) / this.maxErosivity());
  }

  private showResult(calculation: ErosivityCalculation): void {
    this.result.set(calculation);
    this.calculating.set(false);
    this.api.findByStation(this.station().id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((history) => this.history.set(history));
  }

  private fail(error: AppError): void {
    this.error.set(error.message);
    this.calculating.set(false);
  }

  // Default period: from the first to the last complete year, ignoring the current (partial) year.
  private applyCoverage(annual: AnnualStationData[]): void {
    const currentYear = new Date().getFullYear();
    const complete = annual.filter(
      (year) =>
        year.year < currentYear &&
        year.observationHours - year.missingPrecipitationHours >= COMPLETE_YEAR_COVERAGE * hoursInYear(year.year),
    );
    this.completeYears.set(new Set(complete.map((year) => year.year)));
    this.years.set(annual.map((year) => year.year).reverse());
    const range = complete.length ? complete : annual;
    this.fromYear.set(range[0]?.year ?? null);
    this.toYear.set(range.at(-1)?.year ?? null);
  }
}
