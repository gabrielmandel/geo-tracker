import { DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RasterClass } from '../../r-factor/color-ramp';

@Component({
  selector: 'app-r-factor-legend',
  imports: [DecimalPipe],
  host: { class: 'r-legend' },
  template: `
    <span class="r-legend__heading">R FACTOR · {{ period() }}</span>
    <span class="r-legend__unit">MJ mm ha⁻¹ h⁻¹ yr⁻¹</span>
    @for (range of classes(); track $index) {
      <span class="r-legend__class">
        <i [style.background]="range.color"></i>
        {{ range.from | number: '1.0-0' }} – {{ range.to | number: '1.0-0' }}
      </span>
    }
    <span class="r-legend__readout">
      @if (hoverValue() !== null) { Cursor: <b>{{ hoverValue() | number: '1.0-0' }}</b> } @else { Hover the map to read R }
    </span>
  `,
})
export class RFactorLegendComponent {
  readonly classes = input.required<readonly RasterClass[]>();
  readonly period = input('');
  readonly hoverValue = input<number | null>(null);
}
