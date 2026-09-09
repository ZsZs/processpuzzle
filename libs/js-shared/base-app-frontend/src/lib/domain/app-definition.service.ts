import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { AppDefinition } from './app-definition';
import { AppDefinitionMapper } from './app-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/app-definitions`. As in base-rule, the organization is
 * part of the configured service root (`APP_SERVICE_ROOT`), so the tenant is a deployment concern
 * rather than something every call has to carry.
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
}
