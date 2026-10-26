import { HttpClient } from '@angular/common/http';
import { EnvironmentProviders, inject, Injectable, makeEnvironmentProviders } from '@angular/core';
import { REALM_ROLE_SUGGESTIONS, TAG_SUGGESTION_SOURCES } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { catchError, map, Observable, of } from 'rxjs';
import { organizationRoot, type SelectableRole } from './self-service-roles.service';

/**
 * The roles an author may refer to — `GET {root}/role-catalog`, open to every member of the organization.
 * The backend leaves out the administrative roles, so what comes back is safe to offer in a picker.
 *
 * Uses `ROLE_CATALOG_SERVICE_ROOT`, or — when that is not configured — the self-service root, so an
 * application that opts into self-service keeps its catalog without a second key. Without either the
 * catalog is empty and role fields stay plain free text.
 */
@Injectable({ providedIn: 'root' })
export class RoleCatalogService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION, { optional: true });

  findAll(): Observable<readonly SelectableRole[]> {
    const root = organizationRoot(this.runtimeConfiguration, 'ROLE_CATALOG_SERVICE_ROOT') || organizationRoot(this.runtimeConfiguration);
    if (!root) return of([]);
    return this.http.get<SelectableRole[]>(`${root}/role-catalog`).pipe(catchError(() => of([])));
  }
}

/**
 * Offers the role catalog to every `TAGS` attribute that names {@link REALM_ROLE_SUGGESTIONS} — the
 * reader / editor / publisher roles of documents and content books, for one.
 */
export function provideRoleCatalogSuggestions(): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: TAG_SUGGESTION_SOURCES,
      multi: true,
      useFactory: () => {
        const catalog = inject(RoleCatalogService);
        return { key: REALM_ROLE_SUGGESTIONS, suggestions: () => catalog.findAll().pipe(map((roles) => roles.map((role) => role.name))) };
      },
    },
  ]);
}
