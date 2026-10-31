import { Component, input, output } from '@angular/core';
import { Geofence } from '@data-access/geofences/geofence.model';

@Component({
  selector: 'app-geofence-list',
  templateUrl: './geofence-list.component.html',
  host: { class: 'geofence-panel' },
})
export class GeofenceListComponent {
  readonly geofences = input.required<Geofence[]>();
  readonly selectedId = input<string | null>(null);
  readonly geofenceSelected = output<string>();
}
