import type { ApiDefinition } from '../app/core/http/api-definition';

export const environment = {
  apis: {
    geoTracker: { baseUrl: '/api' },
  } satisfies Record<string, ApiDefinition>,
  // Geofence drawing is parked until it has a backend; flip to true to show it again.
  features: { geofences: false },
};
