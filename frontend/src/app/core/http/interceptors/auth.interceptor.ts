import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../../auth/auth.service';
import { API_DEFINITION, SKIP_AUTH } from '../http-context.tokens';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token();
  const api = req.context.get(API_DEFINITION);
  if (!api?.auth || !token || req.context.get(SKIP_AUTH)) return next(req);
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
