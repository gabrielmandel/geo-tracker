import { TestBed } from '@angular/core/testing';
import { DateAdapter, MAT_DATE_FORMATS, MatDateFormats } from '@angular/material/core';
import { provideBrazilianDates } from './brazilian-date-adapter';

describe('BrazilianDateAdapter', () => {
  let adapter: DateAdapter<Date>;
  let formats: MatDateFormats;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideBrazilianDates()] });
    adapter = TestBed.inject(DateAdapter);
    formats = TestBed.inject(MAT_DATE_FORMATS);
  });

  it('displays dates as dd/mm/yyyy', () => {
    expect(adapter.format(new Date(2026, 8, 5), formats.display.dateInput)).toBe('05/09/2026');
  });

  it('reads typed dd/mm/yyyy dates', () => {
    const date = adapter.parse('05/09/2026', formats.parse.dateInput);
    expect(date && [date.getDate(), date.getMonth(), date.getFullYear()]).toEqual([5, 8, 2026]);
  });

  it('rejects impossible dates and accepts an empty field', () => {
    expect(adapter.isValid(adapter.parse('31/02/2026', formats.parse.dateInput)!)).toBe(false);
    expect(adapter.parse('', formats.parse.dateInput)).toBeNull();
  });
});
