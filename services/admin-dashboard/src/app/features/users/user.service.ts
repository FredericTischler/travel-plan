import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

/**
 * Shape of the identity-service GET /users response items.
 * See services/identity-service UserResponse.java.
 */
export interface User {
  id: string;
  email: string;
  createdAt: string;
}

/**
 * Read-only access to GET /users. No create/update/delete: this increment
 * is display-only.
 */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  list(): Observable<User[]> {
    return this.http.get<User[]>(`${environment.identityApiUrl}/users`);
  }
}