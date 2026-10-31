import { HttpContextToken } from '@angular/common/http';
import { ApiDefinition } from './api-definition';

/** Set by BaseApiService; interceptors ignore requests without it (tiles, CDNs, third parties). */
export const API_DEFINITION = new HttpContextToken<ApiDefinition | null>(() => null);

export const SKIP_AUTH = new HttpContextToken<boolean>(() => false);
