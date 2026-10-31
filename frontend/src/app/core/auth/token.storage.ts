import { Injectable } from '@angular/core';

const TOKEN_KEY = 'geo-tracker.token';

// sessionStorage is readable by any script on the page; prefer an httpOnly cookie once the backend exists.
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  read(): string | null {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  write(token: string): void {
    sessionStorage.setItem(TOKEN_KEY, token);
  }

  clear(): void {
    sessionStorage.removeItem(TOKEN_KEY);
  }
}
