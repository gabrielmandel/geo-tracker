import { Injectable, Provider } from '@angular/core';
import { DateAdapter, MAT_DATE_FORMATS, MAT_NATIVE_DATE_FORMATS, MatDateFormats, NativeDateAdapter } from '@angular/material/core';

const DAY_MONTH_YEAR = 'dd/MM/yyyy';

const BRAZILIAN_DATE_FORMATS: MatDateFormats = {
  parse: { dateInput: DAY_MONTH_YEAR },
  display: { ...MAT_NATIVE_DATE_FORMATS.display, dateInput: DAY_MONTH_YEAR },
};

// Typed and displayed dates use dd/mm/yyyy; calendar labels keep the app's English locale.
@Injectable()
export class BrazilianDateAdapter extends NativeDateAdapter {
  // `Object` (not `object`) to match NativeDateAdapter.format.
  override format(date: Date, displayFormat: Object): string {
    if (displayFormat !== DAY_MONTH_YEAR) return super.format(date, displayFormat);
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
  }

  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value !== 'string') return super.parse(value, parseFormat);
    const text = value.trim();
    if (!text) return null;
    const match = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(text);
    if (!match) return this.invalid();
    const [day, month, year] = match.slice(1).map(Number);
    const date = new Date(year, month - 1, day);
    // Rejects overflowing dates such as 31/02/2026.
    return date.getMonth() === month - 1 && date.getDate() === day ? date : this.invalid();
  }
}

export function provideBrazilianDates(): Provider[] {
  return [
    { provide: DateAdapter, useClass: BrazilianDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: BRAZILIAN_DATE_FORMATS },
  ];
}
