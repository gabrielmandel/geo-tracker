import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseApiService } from '@core/http/base-api.service';
import { environment } from '@env/environment';
import {
  CacheRefresh,
  CacheStatus,
  CalculationRequest,
  ErosivityCalculation,
  ErosivityCalculationSummary,
  ErosivityMap,
  ErosivityMapQuery,
  ParameterOverrides,
  ParameterPreset,
} from './erosivity.model';

@Injectable({ providedIn: 'root' })
export class ErosivityCalculationApiService extends BaseApiService {
  protected override readonly api = environment.apis.geoTracker;
  protected override readonly resource = 'erosivity-calculations';

  presets(): Observable<ParameterPreset[]> {
    return this.get('presets');
  }

  findByStation(stationId: number): Observable<ErosivityCalculationSummary[]> {
    return this.get('', { params: { stationId } });
  }

  find(id: number): Observable<ErosivityCalculation> {
    return this.get(String(id));
  }

  calculate(request: CalculationRequest): Observable<ErosivityCalculation> {
    return this.post('', request);
  }

  rerun(id: number, preset?: string, parameters?: ParameterOverrides): Observable<ErosivityCalculation> {
    return this.post(`${id}/rerun`, { preset, parameters });
  }

  /** Same-origin download links (the browser saves them with the server's file name). */
  exportUrl(id: number, file: 'annual.csv' | 'events.csv' | 'station.geojson'): string {
    return `${this.api.baseUrl}/${this.resource}/${id}/${file}`;
  }
}

@Injectable({ providedIn: 'root' })
export class ErosivityMapApiService extends BaseApiService {
  protected override readonly api = environment.apis.geoTracker;
  protected override readonly resource = 'erosivity-maps';

  build(query: ErosivityMapQuery): Observable<ErosivityMap> {
    return this.get('', { params: toParams(query) });
  }

  cacheStatus(): Observable<CacheStatus> {
    return this.get('cache');
  }

  refreshCache(onlyStale = true): Observable<CacheRefresh> {
    return this.post('cache/refresh', null, { params: { onlyStale } });
  }

  exportUrl(file: 'grid.zip' | 'stations.geojson' | 'stations.csv', query: ErosivityMapQuery): string {
    return `${this.api.baseUrl}/${this.resource}/${file}?${toParams(query).toString()}`;
  }
}

function toParams(query: ErosivityMapQuery): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== null && value !== undefined) params = params.set(key, String(value));
  }
  return params;
}
