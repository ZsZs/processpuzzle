import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, map, Observable, of, throwError } from 'rxjs';
import { AvailableTransition, EntityObjectState, TransitionResult } from './entity-object-state';

/**
 * The operation layer's read side: where one governed object currently sits in its machine.
 *
 * A plain HTTP service rather than a `BaseEntityRestService` subclass, because what it reads is not an
 * entity collection. `GET /organizations/{orgKey}/entities/{entityName}/{objectId}/state` is a projection
 * over an object that belongs to base-entity — there is no `EntityObjectState` resource to list, create or
 * delete — so the CRUD surface would be five methods that must never be called. The organization stays part
 * of the configured service root, exactly as in `StateMachineDefinitionService`.
 *
 * This is the resource `StateMachineDefinitionService`'s class comment set aside as belonging "to whatever
 * surface drives an object through its machine". The State Machine tab reads it; the STATE form control reads
 * it and fires transitions through {@link fireTransition}.
 */
@Injectable({ providedIn: 'root' })
export class EntityObjectStateService {
  private readonly httpClient = inject(HttpClient);
  private readonly baseUrl = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'STATE_SERVICE_ROOT');

  /**
   * The current state of one object, or `undefined` when there is nothing to report.
   *
   * **404 is an ordinary answer**, not an error, and covers two cases the caller treats alike: no machine
   * governs this entity type, and no object of that type has this id. Either way the tab has no state to
   * highlight, and neither is worth a red snackbar over a screen the user merely opened. Every other status
   * still propagates.
   *
   * @param entityName the machine's key — the entity *definition code*, e.g. `order`
   */
  findState(entityName: string, objectId: string): Observable<EntityObjectState | undefined> {
    return this.httpClient.get<unknown>(`${this.objectUrl(entityName, objectId)}/state`).pipe(
      map((response) => fromDto(response)),
      catchError((error: unknown) => (error instanceof HttpErrorResponse && error.status === 404 ? of(undefined) : throwError(() => error))),
    );
  }

  /**
   * Fires `triggerKey` on one object. The server re-evaluates the guards — whatever {@link findState} said is
   * only a dry run — and, on success, writes the new state onto the object and bumps its version.
   *
   * @param version the object's current version; a stale one is a 409, which propagates.
   */
  fireTransition(entityName: string, objectId: string, triggerKey: string, version: number): Observable<TransitionResult> {
    return this.httpClient.post<TransitionResult>(`${this.objectUrl(entityName, objectId)}/state-transitions`, { triggerKey, version }).pipe(
      map((result) => ({ ...result, executedActions: result?.executedActions ?? [] })),
    );
  }

  // The configured root carries no trailing slash — see the `BACKEND_SERVICE_ROOT` values in the testbed's
  // `run-time-conf` — so the separator is added here, as `buildUrl` does for the CRUD services.
  private objectUrl(entityName: string, objectId: string): string {
    return `${this.baseUrl}/entities/${encodeURIComponent(entityName)}/${encodeURIComponent(objectId)}`;
  }
}

/**
 * The response as this library's shape. Defensive about the two required arrays and about absent fields,
 * because the same tab runs against the Spring backend, json-server and the Firebase functions, and only
 * the first of those is generated from the contract.
 */
function fromDto(response: unknown): EntityObjectState | undefined {
  if (!response || typeof response !== 'object') return undefined;
  const dto = response as Partial<EntityObjectState>;
  if (!dto.currentStateKey) return undefined;

  return {
    objectId: dto.objectId ?? '',
    entityName: dto.entityName ?? '',
    currentStateKey: dto.currentStateKey,
    isFinal: dto.isFinal ?? false,
    enteredStateAt: dto.enteredStateAt,
    availableTransitions: Array.isArray(dto.availableTransitions) ? (dto.availableTransitions as AvailableTransition[]) : [],
  };
}
