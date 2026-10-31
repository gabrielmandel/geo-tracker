import { Component, input, output, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { DateRangeFieldComponent, DayRange } from '@shared/date-range-field/date-range-field.component';

@Component({
  selector: 'app-station-layers',
  imports: [DateRangeFieldComponent, MatFormFieldModule, MatInputModule],
  templateUrl: './station-layers.component.html',
  host: { class: 'station-panel' },
})
export class StationLayersComponent {
  readonly stationCount = input.required<number>();
  readonly visible = input.required<boolean>();
  readonly minimumAgeYears = input<number | null>(null);
  readonly operatingRange = input.required<DayRange>();
  readonly loading = input(false);
  readonly visibilityChange = output<boolean>();
  readonly minimumAgeYearsChange = output<number | null>();
  readonly operatingRangeChange = output<DayRange>();
  readonly invalidAge = signal(false);

  protected readonly today = new Date();

  protected setMinimumAge(value: string): void {
    if (value.trim() === '') {
      this.invalidAge.set(false);
      this.minimumAgeYearsChange.emit(null);
      return;
    }
    const years = Number(value);
    this.invalidAge.set(!Number.isInteger(years) || years < 1 || years > 200);
    if (!this.invalidAge()) this.minimumAgeYearsChange.emit(years);
  }

  protected clearRange(): void {
    this.operatingRangeChange.emit({ start: null, end: null });
  }
}
