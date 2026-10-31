import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiDefinition } from './api-definition';
import { API_DEFINITION } from './http-context.tokens';

export interface RequestOptions {
  params?: HttpParams | Record<string, string | number | boolean | readonly (string | number | boolean)[]>;
  context?: HttpContext;
}

export abstract class BaseApiService {
  protected readonly http = inject(HttpClient);
  protected abstract readonly api: ApiDefinition;
  protected abstract readonly resource: string;

  protected get<T>(path = '', options: RequestOptions = {}): Observable<T> {
    return this.http.get<T>(this.url(path), this.withApi(options));
  }

  protected post<T>(path: string, body: unknown, options: RequestOptions = {}): Observable<T> {
    return this.http.post<T>(this.url(path), body, this.withApi(options));
  }

  protected put<T>(path: string, body: unknown, options: RequestOptions = {}): Observable<T> {
    return this.http.put<T>(this.url(path), body, this.withApi(options));
  }

  protected delete<T>(path: string, options: RequestOptions = {}): Observable<T> {
    return this.http.delete<T>(this.url(path), this.withApi(options));
  }

  private url(path: string): string {
    return [this.api.baseUrl, this.resource, path].filter(Boolean).join('/');
  }

  private withApi({ params, context = new HttpContext() }: RequestOptions) {
    return { params, context: context.set(API_DEFINITION, this.api) };
  }
}
