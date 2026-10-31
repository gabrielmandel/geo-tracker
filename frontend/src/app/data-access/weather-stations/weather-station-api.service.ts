import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseApiService } from '@core/http/base-api.service';
import { environment } from '@env/environment';
import { AnnualStationData, HourlyObservation, RainfallEvent, WeatherStation } from './weather-station.model';

@Injectable({ providedIn: 'root' })
export class WeatherStationApiService extends BaseApiService {
  protected override readonly api = environment.apis.geoTracker;
  protected override readonly resource = 'weather-stations';

  findAll(minAgeYears: number | null = null): Observable<WeatherStation[]> {
    return this.get('', { params: minAgeYears === null ? undefined : { minAgeYears } });
  }

  findById(id: number): Observable<WeatherStation> {
    return this.get(String(id));
  }

  findHourly(id: number, from: Date, to: Date): Observable<HourlyObservation[]> {
    return this.get(`${id}/observations`, { params: { from: from.toISOString(), to: to.toISOString() } });
  }

  findAnnualData(id: number): Observable<AnnualStationData[]> {
    return this.get(`${id}/annual-data`);
  }

  findRainfallEvents(id: number, year: number): Observable<RainfallEvent[]> {
    return this.get(`${id}/rainfall-events`, { params: { year } });
  }
}