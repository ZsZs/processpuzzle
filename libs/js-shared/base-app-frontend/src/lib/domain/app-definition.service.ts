import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, map, Observable, of, throwError } from 'rxjs';
import { BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { EXPECTED_ERROR_STATUSES } from '@processpuzzle/util';
import { AppDefinition } from './app-definition';
import { AppDefinitionMapper } from './app-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/app-definitions`. As in base-rule, the organization is
 * part of the configured service root — `APP_SERVICE_ROOT`, the optional per-feature escape hatch,
 * falling back to `BACKEND_SERVICE_ROOT`, which is what every deployment actually sets — so the
 * tenant is a deployment concern rather than something every call has to carry.
 */
@Injectable({ providedIn: 'root' })
export class AppDefinitionService extends BaseEntityRestService<AppDefinition> {
  constructor(protected override entityMapper: AppDefinitionMapper) {
    super(entityMapper, 'APP_SERVICE_ROOT', 'app-definitions');
  }

  /**
   * `POST /app-definitions/{appId}/publish` — promotes the stored `version` to `publishedVersion` and
   * sets the status to `PUBLISHED`. The backend validates the definition first and answers 400 when it
   * is not publishable, so the caller only has to deal with the happy path and the error.
   */
  publish(id: string): Observable<PersistedEntity<AppDefinition>> {
    const pathParams = new Map<string, string>([['id', String(id)]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}/publish', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');
    return this.httpClient.post(fullUrl, {}, { headers: this.headers }).pipe(map((response: unknown) => this.entityMapper.fromDto(response) as PersistedEntity<AppDefinition>));
  }

  /**
   * `GET /app-definitions/{appId}/layout` — the **published** revision, role-filtered, with its routes:
   * what the runtime host renders. `findById` is the designer's read and always returns the draft.
   *
   * Resolves to `undefined` while the application has never been published, which the backend answers
   * with 404. That is the state of every new organization, so it is not reported as an error.
   */
  findPublished(id: string): Observable<AppDefinition | undefined> {
    const pathParams = new Map<string, string>([['id', String(id)]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}/layout', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');
    const context = new HttpContext().set(EXPECTED_ERROR_STATUSES, [404]);
    return this.httpClient.get(fullUrl, { headers: this.headers, context }).pipe(
      map((response: unknown) => this.entityMapper.fromDto(response) as AppDefinition),
      catchError((error: unknown) => (error instanceof HttpErrorResponse && error.status === 404 ? of(undefined) : throwError(() => error))),
    );
  }
}
