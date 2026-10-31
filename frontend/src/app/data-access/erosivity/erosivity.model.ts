import type { ApiModel, Schemas } from '../api/api-types';

// Generated from the backend OpenAPI document (`npm run api:types`); overrides cover what the generator can't express.
export type CalculationParameters = ApiModel<'CalculationParameters'>;
export type ParameterOverrides = Schemas['ErosivityParametersRequest'];
export type ParameterPreset = ApiModel<'ParameterPresetResponse'>;
export type AnnualErosivity = ApiModel<'AnnualErosivityResponse'>;
export type ErosivityCalculation = ApiModel<'ErosivityCalculationResponse'>;
export type ErosivityCalculationSummary = ApiModel<'ErosivityCalculationSummaryResponse'>;
export type CalculationRequest = Schemas['ErosivityCalculationRequest'];
export type RasterGrid = ApiModel<'GridResponse'>;
export type MapStation = ApiModel<'MapStationResponse'>;

export type ErosivityMap = Omit<ApiModel<'ErosivityMapResponse'>, 'values' | 'stats'> & {
  /** Row-major from the north-west cell; null = no data (Kotlin List<Double?> loses element nullability in OpenAPI). */
  values: (number | null)[];
  stats: ApiModel<'MapStatsResponse'>;
};

export type CacheRefresh = Omit<ApiModel<'CacheRefreshResponse'>, 'status'> & {
  status: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
};

export type CacheStatus = Omit<ApiModel<'CacheStatusResponse'>, 'latestRefresh'> & {
  latestRefresh: CacheRefresh | null;
};

/** Query string of the map endpoints (not an OpenAPI schema); null parameters are left out. */
export interface ErosivityMapQuery {
  fromYear: number;
  toYear: number;
  minCoverage: number;
  minYears: number | null;
  cellSize: number;
  power: number;
  maxDistanceKm: number;
  maxNeighbors: number;
}
