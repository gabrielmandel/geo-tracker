import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { NgIcon } from '@ng-icons/core';
import { Subscription } from 'rxjs';
import { AppError } from '@core/http/app-error';
import { WeatherStationApiService } from '@data-access/weather-stations/weather-station-api.service';
import { AnnualStationData, HourlyObservation, RainfallEvent, WeatherStation } from '@data-access/weather-stations/weather-station.model';
import { DateRangeFieldComponent, DayRange } from '@shared/date-range-field/date-range-field.component';
import { StationRFactorComponent } from '../station-r-factor/station-r-factor.component';

// The API serves at most 32 days of hourly rows; whole local days keep a month within that.
const MAX_HOURLY_DAYS = 31;

const HOURLY_COLUMNS: { key: keyof HourlyObservation; label: string }[] = [
  { key: 'precipitationMm', label: 'Rain mm' },
  { key: 'airTemperatureC', label: 'Air °C' },
  { key: 'dewPointC', label: 'Dew point °C' },
  { key: 'relativeHumidityPct', label: 'Humidity %' },
  { key: 'stationPressureMb', label: 'Pressure mb' },
  { key: 'windSpeedMs', label: 'Wind m/s' },
  { key: 'windGustMs', label: 'Gust m/s' },
  { key: 'globalRadiationKjM2', label: 'Radiation kJ/m²' },
];

function previousMonth(): DayRange {
  const now = new Date();
  return {
    start: new Date(now.getFullYear(), now.getMonth() - 1, 1),
    end: new Date(now.getFullYear(), now.getMonth(), 0),
  };
}

@Component({
  selector: 'app-station-details-dialog',
  imports: [
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatTableModule,
    MatTabsModule,
    NgIcon,
    DateRangeFieldComponent,
    StationRFactorComponent,
  ],
  templateUrl: './station-details-dialog.component.html',
})
export class StationDetailsDialogComponent {
  private readonly api = inject(WeatherStationApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialogRef = inject(MatDialogRef<StationDetailsDialogComponent>);
  readonly station = signal(inject<WeatherStation>(MAT_DIALOG_DATA));
  readonly hourlyRows = signal<HourlyObservation[]>([]);
  readonly annualRows = signal<AnnualStationData[] | null>(null);
  readonly events = signal<RainfallEvent[]>([]);
  readonly activeTab = signal(0);
  readonly selectedYear = signal<number | null>(null);
  readonly hourlyLoading = signal(false);
  readonly annualLoading = signal(false);
  readonly eventsLoading = signal(false);
  readonly hourlyError = signal('');
  readonly annualError = signal('');
  readonly eventsError = signal('');
  readonly hourlyRange = signal<DayRange>(previousMonth());
  protected readonly today = new Date();
  protected readonly hourlyColumns = HOURLY_COLUMNS;
  protected readonly hourlyColumnIds = ['observedAt', ...HOURLY_COLUMNS.map((column) => column.key)];
  protected readonly annualColumnIds = ['year', 'precipitationMm', 'observationHours', 'missingPrecipitationHours'];
  protected readonly eventColumnIds = ['startedAt', 'endedAt', 'precipitationMm', 'maxHourlyMm', 'ei30', 'erosive'];
  private hourlyRequest?: Subscription;

  constructor() {
    this.loadStation();
    this.loadHourly();
  }

  close(): void {
    this.dialogRef.close();
  }

  setHourlyRange(range: DayRange): void {
    this.hourlyRange.set(range);
    if (range.start && range.end) this.loadHourly();
  }

  private loadHourly(): void {
    const { start, end } = this.hourlyRange();
    if (!start || !end) return;
    const from = new Date(start.getFullYear(), start.getMonth(), start.getDate());
    const to = new Date(end.getFullYear(), end.getMonth(), end.getDate() + 1);
    this.hourlyRequest?.unsubscribe();
    if (Math.round((to.getTime() - from.getTime()) / 86_400_000) > MAX_HOURLY_DAYS) {
      this.hourlyRows.set([]);
      this.hourlyLoading.set(false);
      this.hourlyError.set(`Choose up to ${MAX_HOURLY_DAYS} days of hourly data.`);
      return;
    }
    this.hourlyLoading.set(true);
    this.hourlyError.set('');
    this.hourlyRequest = this.api.findHourly(this.station().id, from, to)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.hourlyRows.set(rows);
          this.hourlyLoading.set(false);
        },
        error: (error: AppError) => {
          this.hourlyError.set(error.message);
          this.hourlyLoading.set(false);
        },
      });
  }

  onTabChange(index: number): void {
    this.activeTab.set(index);
    if (index === 1) this.loadAnnual();
    if (index === 2) {
      this.loadAnnual(() => {
        const year = this.selectedYear() ?? this.annualRows()?.at(-1)?.year;
        if (year !== undefined) this.loadEvents(year);
      });
    }
  }

  onYearChange(year: number): void {
    this.loadEvents(year);
  }

  private loadStation(): void {
    this.api.findById(this.station().id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (station) => this.station.set(station) });
  }

  private loadAnnual(afterLoad?: () => void): void {
    if (this.annualRows() !== null) {
      afterLoad?.();
      return;
    }
    if (this.annualLoading()) return;
    this.annualLoading.set(true);
    this.annualError.set('');
    this.api.findAnnualData(this.station().id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.annualRows.set(rows);
          this.selectedYear.set(rows.at(-1)?.year ?? null);
          this.annualLoading.set(false);
          afterLoad?.();
        },
        error: (error: AppError) => {
          this.annualError.set(error.message);
          this.annualLoading.set(false);
        },
      });
  }

  private loadEvents(year: number): void {
    this.selectedYear.set(year);
    this.eventsLoading.set(true);
    this.eventsError.set('');
    this.api.findRainfallEvents(this.station().id, year)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (events) => {
          this.events.set(events);
          this.eventsLoading.set(false);
        },
        error: (error: AppError) => {
          this.eventsError.set(error.message);
          this.eventsLoading.set(false);
        },
      });
  }
}