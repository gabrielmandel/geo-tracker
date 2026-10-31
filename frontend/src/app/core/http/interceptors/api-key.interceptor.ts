import { HttpInterceptorFn } from '@angular/common/http';
import { API_DEFINITION } from '../http-context.tokens';

export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const apiKey = req.context.get(API_DEFINITION)?.apiKey;
  return next(apiKey ? req.clone({ setHeaders: { [apiKey.header]: apiKey.value } }) : req);
};
