import type { ApiDefinition } from '../app/core/http/api-definition';

export const environment = {
  apis: {
    geoTracker: { baseUrl: '/api' },
  } satisfies Record<string, ApiDefinition>,
  features: { geofences: false },
};