import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';

const TOKEN_STORAGE_KEY = 'admin-dashboard.jwt';

export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * Shape of the identity-service POST /login response.
 * See services/identity-service LoginResponse.java.
 */
export interface LoginResponse {
  id: string;
  email: string;
  token: string;
}

/**
 * Minimal auth state: holds the JWT issued by identity-service and exposes
 * it to the rest of the app (interceptor, guard).
 *
 * Storage is plain localStorage — no dedicated refresh-token flow, no
 * silent renewal: the backend JWT is short-lived (15 min, see
 * LoginResponse.java) and on expiry the user is simply redirected to
 * /login (see the auth interceptor's 401 handling).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly tokenSignal = signal<string | null>(
    localStorage.getItem(TOKEN_STORAGE_KEY),
  );

  readonly isAuthenticated = computed(() => this.tokenSignal() !== null);

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.identityApiUrl}/login`, request)
      .pipe(tap((response) => this.setToken(response.token)));
  }

  getToken(): string | null {
    return this.tokenSignal();
  }

  logout(): void {
    this.setToken(null);
  }

  private setToken(token: string | null): void {
    this.tokenSignal.set(token);
    if (token) {
      localStorage.setItem(TOKEN_STORAGE_KEY, token);
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
    }
  }
}