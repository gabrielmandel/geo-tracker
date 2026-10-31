import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { toAppError } from '../app-error';
import { API_DEFINITION } from '../http-context.tokens';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && req.context.get(API_DEFINITION)?.auth) {
        // Redirect to /login belongs here once authGuard is enabled on the routes.
        auth.clearSession();
      }
      return throwError(() => toAppError(error));
    }),
  );
};
