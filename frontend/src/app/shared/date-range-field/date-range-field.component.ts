import { Component, input, output } from '@angular/core';
import { MatDateRangeInput, MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';

export interface DayRange {
  start: Date | null;
  end: Date | null;
}

@Component({
  selector: 'app-date-range-field',
  imports: [MatFormFieldModule, MatDatepickerModule],
  templateUrl: './date-range-field.component.html',
})
export class DateRangeFieldComponent {
  readonly range = input.required<DayRange>();
  readonly max = input<Date | null>(null);
  readonly ariaLabel = input('Date range');
  readonly rangeChange = output<DayRange>();

  // Read both ends from the input: picking a new start also clears the end in the same model update.
  protected emitRange({ value }: MatDateRangeInput<Date>): void {
    this.rangeChange.emit({ start: value?.start ?? null, end: value?.end ?? null });
  }
}
