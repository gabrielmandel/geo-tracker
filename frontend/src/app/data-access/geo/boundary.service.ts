import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import type { FeatureCollection } from 'geojson';

/** Static IBGE boundaries served with the app (public/geo); not a REST API, so it bypasses BaseApiService. */
@Injectable({ providedIn: 'root' })
export class BoundaryService {
  private readonly http = inject(HttpClient);
  private readonly states$ = this.http.get<FeatureCollection>('geo/br-states.geojson').pipe(shareReplay(1));

  brazilianStates(): Observable<FeatureCollection> {
    return this.states$;
  }
}
