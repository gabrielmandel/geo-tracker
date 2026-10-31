import { Injectable } from '@angular/core';
import { delay, Observable, of } from 'rxjs';
import { Geofence } from './geofence.model';

@Injectable({ providedIn: 'root' })
export class GeofenceService {
  private geofences: Geofence[] = [
    {
      id: 'ws-porto-alegre',
      name: 'Porto Alegre region',
      coordinates: [[-29.951389, -51.124167], [-30.115278, -50.929167], [-30.186111, -51.178333], [-30.053611, -51.174722]],
      updatedAt: 'Just now',
    }
  ];

  getGeofences(): Observable<Geofence[]> {
    return of(this.geofences.map((geofence) => ({ ...geofence }))).pipe(delay(350));
  }

  saveGeofence(geofence: Omit<Geofence, 'id' | 'updatedAt'>): Observable<Geofence> {
    const saved: Geofence = {
      ...geofence,
      id: `gf-${crypto.randomUUID()}`,
      updatedAt: 'Just now',
    };
    this.geofences = [...this.geofences, saved];
    return of({ ...saved }).pipe(delay(250));
  }
}