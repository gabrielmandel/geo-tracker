import { computed, inject, Injectable, signal } from '@angular/core';
import { TokenStorage } from './token.storage';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly storage = inject(TokenStorage);
  private readonly tokenState = signal<string | null>(this.storage.read());

  readonly token = this.tokenState.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenState() !== null);

  setSession(token: string): void {
    this.storage.write(token);
    this.tokenState.set(token);
  }

  clearSession(): void {
    this.storage.clear();
    this.tokenState.set(null);
  }
}
