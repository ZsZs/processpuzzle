import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, map, Observable, of, throwError } from 'rxjs';
import { AvailableTransition, EntityObjectState } from './entity-object-state';

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
 * surface drives an object through its machine". That surface is the State Machine tab.
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
    // The configured root carries no trailing slash — see the `APP_SERVICE_ROOT` values in the testbed's
    // `run-time-conf` — so the separator is added here, as `buildUrl` does for the CRUD services.
    const url = `${this.baseUrl}/entities/${encodeURIComponent(entityName)}/${encodeURIComponent(objectId)}/state`;
    return this.httpClient.get<unknown>(url).pipe(
      map((response) => fromDto(response)),
      catchError((error: unknown) => (error instanceof HttpErrorResponse && error.status === 404 ? of(undefined) : throwError(() => error))),
    );
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
