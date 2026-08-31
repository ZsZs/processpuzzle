import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { catchError, map, Observable, of, throwError } from 'rxjs';
import { WorkflowDiagram } from '../models/workflow-diagram';
import { WorkflowDiagramMapper } from './workflow-diagram.mapper';

/**
 * REST access to `/organizations/{orgKey}/workflow-diagrams`. The organization is part of the configured
 * service root, exactly as in `WorkflowService`, so the tenant stays a deployment concern.
 *
 * Two things separate this from the stock CRUD service, both mirroring base-state's
 * `DiagramDefinitionService`.
 *
 * **There is no POST.** The contract writes a layout with `PUT /workflow-diagrams/{workflowId}`, an upsert —
 * `201` the first time a workflow is arranged, `200` every time after — because saving a layout twice is the
 * normal case rather than the 409 a second workflow of the same id would be. So {@link save} is the single
 * writer, and both `add` and `update` delegate to it: a caller never has to know whether this workflow has
 * been arranged before, and the two inherited paths cannot drift apart.
 *
 * **A missing layout is not an error.** `GET` answers 404 when a workflow has never been arranged, which is
 * how the modeler learns to keep `SwimlaneLayoutService`'s automatic arrangement.
 * {@link findByWorkflowId} therefore reports it as `undefined` rather than letting it surface as a failed
 * request; every other status still propagates.
 */
@Injectable({ providedIn: 'root' })
export class WorkflowDiagramService extends BaseEntityRestService<WorkflowDiagram> {
  constructor(protected override entityMapper: WorkflowDiagramMapper) {
    super(entityMapper, 'WORKFLOW_SERVICE_ROOT', 'workflow-diagrams');
  }

  /**
   * `PUT /workflow-diagrams/{workflowId}` — creates or replaces the layout in one call. Addressed through
   * the `id` mirror of `workflowId`, which is what the inherited URL builder expects.
   */
  save(layout: WorkflowDiagram): Observable<PersistedEntity<WorkflowDiagram>> {
    const workflowId = layout.workflowId || (layout.id ?? '');
    if (!workflowId) throw new Error('A workflow diagram layout is addressed by the id of its workflow, which is missing.');
    const pathParams = new Map<string, string>([['id', workflowId]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');
    return this.httpClient
      .put(fullUrl, this.entityMapper.toDto(layout), { headers: this.headers })
      .pipe(map((response: unknown) => this.entityMapper.fromDto(response) as PersistedEntity<WorkflowDiagram>));
  }

  /** A create is the same upsert as a replace — see the class comment. */
  override add(entity: WorkflowDiagram): Observable<PersistedEntity<WorkflowDiagram>> {
    return this.save(entity);
  }

  override update(entity: PersistedEntity<WorkflowDiagram>): Observable<PersistedEntity<WorkflowDiagram>> {
    return this.save(entity);
  }

  /**
   * Reads the layout of one workflow, answering `undefined` when it has never been arranged. Anything other
   * than a 404 is still an error and propagates.
   */
  findByWorkflowId(workflowId: string): Observable<PersistedEntity<WorkflowDiagram> | undefined> {
    return this.findById(workflowId).pipe(
      map((response) => (response ?? undefined) as PersistedEntity<WorkflowDiagram> | undefined),
      catchError((error: unknown) => (error instanceof HttpErrorResponse && error.status === 404 ? of(undefined) : throwError(() => error))),
    );
  }
}
