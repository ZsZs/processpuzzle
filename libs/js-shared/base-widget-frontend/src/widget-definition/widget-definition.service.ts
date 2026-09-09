import { inject, Injectable } from '@angular/core';
import { BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { map, Observable } from 'rxjs';
import { WidgetDefinition } from './widget-definition';
import { WidgetDefinitionMapper } from './widget-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/widget-definitions`. As for the app and module definitions, the
 * organization is part of the configured service root (`APP_SERVICE_ROOT`), so the tenant is a deployment
 * concern rather than something every call has to carry — one root serves every metadata resource.
 *
 * The URL variable is the widget key, which {@link WidgetDefinitionMapper} maps onto `id`, so the inherited
 * `findById` / `update` / `delete` address `.../widget-definitions/{key}`.
 */
@Injectable({ providedIn: 'root' })
export class WidgetDefinitionService extends BaseEntityRestService<WidgetDefinition> {
  constructor() {
    super(inject(WidgetDefinitionMapper), 'APP_SERVICE_ROOT', 'widget-definitions');
  }

  /**
   * `POST /widget-definitions/{key}/publish` — promotes the stored `version` to `publishedVersion` and sets
   * the status to `PUBLISHED`. A widget type has a lifecycle of its own, unlike a module: what a published
   * definition promises is a stable props contract to every instance that already names it.
   *
   * Same shape as `AppDefinitionService.publish`, including that the backend validates first and answers
   * 400 when the definition is not publishable.
   */
  publish(id: string): Observable<PersistedEntity<WidgetDefinition>> {
    const pathParams = new Map<string, string>([['id', String(id)]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}/publish', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');
    return this.httpClient.post(fullUrl, {}, { headers: this.headers }).pipe(map((response: unknown) => this.entityMapper.fromDto(response) as PersistedEntity<WidgetDefinition>));
  }
}
