import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, map, Observable, of } from 'rxjs';
import { EntityDefinition, EntityDefinitionPage } from './entity-definition';

/** `BaseConfiguration` key naming the org-scoped root of the base-entity endpoints. */
export const ENTITY_SERVICE_ROOT_KEY = 'ENTITY_SERVICE_ROOT';

/**
 * How many definitions one page asks for: the contract's own maximum for `size`
 * (`base-entity-api.yaml`, `SizeParam`), which the generated endpoint validates — a larger value is a 400,
 * not a bigger page.
 *
 * One page is taken to be the whole set. A tenant's entity *types* are metadata authored by hand — tens,
 * not thousands — so paging through them would add round trips to a request whose result is cached for the
 * session. A tenant that ever exceeds this needs a second call, which is worth writing then rather than
 * guessing at now.
 */
const PAGE_SIZE = 200;

/**
 * Reads the tenant's entity definitions — `GET /organizations/{orgKey}/entity-definitions`.
 *
 * **All of them in one call**, rather than one call per entity the shell happens to need. Two things need
 * the whole set: an `AppDefinition` route names an entity by its `name` while a definition is keyed by
 * `code`, and an `EMBEDDED_COMPONENTS` attribute names its child by `code` too — so both lookups are
 * over the collection. Fetching by code would mean a request per level of every aggregate.
 *
 * Not a `BaseEntityRestService` subclass, unlike every other service in this library: those exist to give
 * an entity a *store* whose rows the generated screens edit, and a definition is not edited here — it is
 * read once and turned into a descriptor. The designer's own CRUD over definitions is a separate surface
 * with its own facade.
 *
 * A failure resolves to `[]` rather than propagating. The root falls back to `APP_SERVICE_ROOT` (see
 * {@link serviceRootOf}) and a deployment may point that at a host which does not serve this resource at
 * all — the json-server mock does not — so "no definitions" has to be an ordinary answer. What the caller
 * renders then is the same "no entity type registered" state as for an unknown entity, which is the truth.
 */
@Injectable({ providedIn: 'root' })
export class EntityDefinitionService {
  private readonly httpClient = inject(HttpClient);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION, { optional: true });

  findAll(): Observable<EntityDefinition[]> {
    const root = serviceRootOf(this.runtimeConfiguration, ENTITY_SERVICE_ROOT_KEY);
    // No configured root means no host to ask. Issuing the request anyway would resolve it against the
    // document base and answer with the application's own index.html on every rewrite-to-index deployment.
    if (!root) return of([]);

    return this.httpClient.get<EntityDefinitionPage | EntityDefinition[]>(`${root}/entity-definitions`, { params: { page: 0, size: PAGE_SIZE } }).pipe(
      map((response) => contentOf(response)),
      catchError(() => of([])),
    );
  }
}

/**
 * The definitions of a response, whether it is the contract's page or a bare array.
 *
 * Both shapes are accepted for the same reason `BaseEntityRestService` accepts both: the Java backend
 * answers with a `Page`, and a mock or a stub answers with the array. Anything else — an error body that
 * arrived with a 200, an index.html — yields no definitions rather than a cast that fails later.
 */
function contentOf(response: EntityDefinitionPage | EntityDefinition[] | null): EntityDefinition[] {
  if (Array.isArray(response)) return response;
  const content = response?.content;
  return Array.isArray(content) ? content : [];
}
