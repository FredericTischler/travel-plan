import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

/**
 * Shape of the travel-service GET/POST /destinations response items.
 * See services/travel-service DestinationResponse.java.
 */
export interface Destination {
  id: string;
  name: string;
  country: string;
  createdAt: string;
}

/**
 * Access to the travel-service /destinations endpoints. The auth
 * interceptor attaches the Bearer token automatically for every request
 * whose URL starts with environment.travelApiUrl.
 *
 * No update() method: travel-service exposes no PATCH/PUT on Destination
 * (soft-delete only), so editing is out of scope here.
 */
@Injectable({ providedIn: 'root' })
export class DestinationService {
  private readonly http = inject(HttpClient);

  list(): Observable<Destination[]> {
    return this.http.get<Destination[]>(`${environment.travelApiUrl}/destinations`);
  }

  create(name: string, country: string): Observable<Destination> {
    return this.http.post<Destination>(`${environment.travelApiUrl}/destinations`, { name, country });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${environment.travelApiUrl}/destinations/${id}`);
  }
}