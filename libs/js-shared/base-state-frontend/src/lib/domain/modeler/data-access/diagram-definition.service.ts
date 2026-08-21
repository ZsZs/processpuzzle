import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { catchError, map, Observable, of, throwError } from 'rxjs';
import { DiagramDefinition } from '../models/diagram-definition';
import { DiagramDefinitionMapper } from './diagram-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/diagrams`. The organization is part of the configured service
 * root, exactly as in `StateMachineDefinitionService`, so the tenant stays a deployment concern.
 *
 * Two things separate this from the stock CRUD service.
 *
 * **There is no POST.** The contract writes a layout with `PUT /diagrams/{entityName}`, an upsert —
 * `201` the first time an entityName is arranged, `200` every time after — because saving a layout
 * twice is the normal case rather than the 409 a second state machine for the same entity would be. So
 * {@link save} is the single writer, and both `add` and `update` delegate to it: a caller never has to
 * know whether this machine has been arranged before, and the two inherited paths cannot drift apart.
 *
 * **A missing layout is not an error.** `GET` answers 404 when a machine has never been arranged, which
 * is how the modeler learns to fall back to `DagreLayoutService` / `ElkLayoutService`.
 * {@link findByEntityName} therefore reports it as `undefined` rather than letting it surface as a
 * failed request; every other status still propagates.
 */
@Injectable({ providedIn: 'root' })
export class DiagramDefinitionService extends BaseEntityRestService<DiagramDefinition> {
  constructor(protected override entityMapper: DiagramDefinitionMapper) {
    super(entityMapper, 'STATE_SERVICE_ROOT', 'diagrams');
  }

  /**
   * `PUT /diagrams/{entityName}` — creates or replaces the layout in one call. Addressed through the
   * `id` mirror of `entityName`, which is what the inherited URL builder expects.
   */
  save(layout: DiagramDefinition): Observable<PersistedEntity<DiagramDefinition>> {
    const entityName = layout.entityName || (layout.id ?? '');
    if (!entityName) throw new Error('A diagram layout is addressed by the entity name of its state machine, which is missing.');
    const pathParams = new Map<string, string>([['id', entityName]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');
    return this.httpClient
      .put(fullUrl, this.entityMapper.toDto(layout), { headers: this.headers })
      .pipe(map((response: unknown) => this.entityMapper.fromDto(response) as PersistedEntity<DiagramDefinition>));
  }

  /** A create is the same upsert as a replace — see the class comment. */
  override add(entity: DiagramDefinition): Observable<PersistedEntity<DiagramDefinition>> {
    return this.save(entity);
  }

  override update(entity: PersistedEntity<DiagramDefinition>): Observable<PersistedEntity<DiagramDefinition>> {
    return this.save(entity);
  }

  /**
   * Reads the layout of one entity type's state machine, answering `undefined` when it has never been
   * arranged. Anything other than a 404 is still an error and propagates.
   */
  findByEntityName(entityName: string): Observable<PersistedEntity<DiagramDefinition> | undefined> {
    return this.findById(entityName).pipe(
      map((response) => (response ?? undefined) as PersistedEntity<DiagramDefinition> | undefined),
      catchError((error: unknown) => (error instanceof HttpErrorResponse && error.status === 404 ? of(undefined) : throwError(() => error))),
    );
  }
}
