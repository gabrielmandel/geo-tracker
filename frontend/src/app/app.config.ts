import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import {
  lucideActivity,
  lucideArrowRight,
  lucideCalculator,
  lucideChevronDown,
  lucideChevronUp,
  lucideDownload,
  lucideGrid3x3,
  lucideHistory,
  lucideLayers,
  lucideMap,
  lucideMapPin,
  lucidePlus,
  lucideRefreshCw,
  lucideShieldCheck,
  lucideX,
} from '@ng-icons/lucide';
import { provideBrazilianDates } from './core/dates/brazilian-date-adapter';
import { apiKeyInterceptor } from './core/http/interceptors/api-key.interceptor';
import { authInterceptor } from './core/http/interceptors/auth.interceptor';
import { errorInterceptor } from './core/http/interceptors/error.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(), withInterceptors([apiKeyInterceptor, authInterceptor, errorInterceptor])),
    provideBrazilianDates(),
    provideIcons({
      lucideActivity,
      lucideArrowRight,
      lucideCalculator,
      lucideChevronDown,
      lucideChevronUp,
      lucideDownload,
      lucideGrid3x3,
      lucideHistory,
      lucideLayers,
      lucideMap,
      lucideMapPin,
      lucidePlus,
      lucideRefreshCw,
      lucideShieldCheck,
      lucideX,
    }),
  ],
};
