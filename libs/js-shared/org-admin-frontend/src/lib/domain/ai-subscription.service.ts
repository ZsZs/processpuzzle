import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, map, Observable, of } from 'rxjs';
import { ORG_ADMIN_ORG_KEY } from './organization-user.service';

export type AiProvider = 'MISTRAL' | 'OPENAI_COMPATIBLE';

/** What the backend returns: everything but the key, which it never gives back. */
export interface AiSubscription {
  provider: AiProvider;
  model: string;
  baseUrl?: string | null;
  keySet: boolean;
  updatedAt: string;
  updatedBy: string;
}

/** What an administrator submits. A blank `apiKey` keeps the stored one. */
export interface AiSubscriptionChange {
  provider: AiProvider;
  model: string;
  baseUrl?: string;
  apiKey?: string;
}

/**
 * `/organizations/{orgKey}/ai-subscription`: the model service the organization has chosen for its design assistant, with
 * its own API key. Reading answers 404 for an organization that has none, which is a state and not an error.
 */
@Injectable({ providedIn: 'root' })
export class AiSubscriptionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${serviceRootOf(inject(RUNTIME_CONFIGURATION), 'ORG_ADMIN_SERVICE_ROOT')}/organizations/${inject(ORG_ADMIN_ORG_KEY)}/ai-subscription`;

  /** The subscription, or `null` when the organization has none. */
  find(): Observable<AiSubscription | null> {
    return this.http.get<AiSubscription>(this.url).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 404) return of(null);
        throw error;
      }),
    );
  }

  save(change: AiSubscriptionChange): Observable<AiSubscription> {
    return this.http.put<AiSubscription>(this.url, change);
  }

  /**
   * The models the provider offers this key, asked of the provider itself. A blank `apiKey` means the stored one, so the
   * list can be loaded again without retyping it. Nothing is saved.
   */
  listModels(request: Omit<AiSubscriptionChange, 'model'>): Observable<string[]> {
    return this.http.post<{ models: string[] }>(`${this.url}/models`, request).pipe(map((response) => response.models));
  }

  remove(): Observable<void> {
    return this.http.delete<void>(this.url);
  }
}
