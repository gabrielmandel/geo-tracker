import { HttpErrorResponse } from '@angular/common/http';

export interface AppError {
  readonly status: number;
  readonly message: string;
  readonly url: string | null;
}

export function toAppError(error: unknown): AppError {
  if (error instanceof HttpErrorResponse) {
    const message = error.status === 0 ? 'Network error: the service could not be reached.' : error.message;
    return { status: error.status, message, url: error.url };
  }
  return { status: -1, message: error instanceof Error ? error.message : 'Unexpected error', url: null };
}
