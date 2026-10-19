import { inject, Injectable } from '@angular/core';
import { BaseEntityLoadResponse, BaseEntityQueryCondition, BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { map, Observable } from 'rxjs';
import { WidgetDefinition } from './widget-definition';
import { WidgetDefinitionMapper } from './widget-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/widget-definitions`. As for the app and module definitions, the
 * organization is part of the configured service root (`APP_SERVICE_ROOT`, falling back to
 * `BACKEND_SERVICE_ROOT`), so the tenant is a deployment concern rather than something every call has to
 * carry — one root serves every metadata resource.
 *
 * The URL variable is the widget key, which {@link WidgetDefinitionMapper} maps onto `id`, so the inherited
 * `findById` / `update` / `delete` address `.../widget-definitions/{key}`.
 */
/**
 * How many widget types one call asks for when the caller names no size. The backend answers 20 without one,
 * and the catalogue is that long already — so the 21st type, whichever it is, would fall onto a second page the
 * Details screen never loads, and open as an empty form: it finds its record among the entities the list call
 * brought, not by key.
 */
export const WIDGET_DEFINITION_PAGE_SIZE = 200;

@Injectable({ providedIn: 'root' })
export class WidgetDefinitionService extends BaseEntityRestService<WidgetDefinition> {
  constructor() {
    super(inject(WidgetDefinitionMapper), 'APP_SERVICE_ROOT', 'widget-definitions');
  }

  override findByQuery(queryCondition: BaseEntityQueryCondition): Observable<BaseEntityLoadResponse<PersistedEntity<WidgetDefinition>> | PersistedEntity<WidgetDefinition>[] | PersistedEntity<WidgetDefinition>> {
    return super.findByQuery({ ...queryCondition, pageSize: queryCondition.pageSize ?? WIDGET_DEFINITION_PAGE_SIZE });
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
