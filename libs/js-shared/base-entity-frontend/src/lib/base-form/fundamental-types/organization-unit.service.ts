import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, map, Observable, of } from 'rxjs';
import { ENTITY_SERVICE_ROOT_KEY } from '../../base-entity-definition/entity-definition.service';

/** A unit the organization added to a quantity kind of the standard catalog (`OrganizationUnit` of the contract). */
export interface OrganizationUnit {
  code: string;
  symbol: string;
  name: string;
  kind: string;
  /** Decimal text; with `divisor`, the unit's size in the kind's base unit. */
  factor: string;
  divisor?: string;
  status?: 'ACTIVE' | 'RETIRED';
  version?: number;
}

/** The contract's own maximum for `size`; an organization's own units are a handful, so one page is the whole set. */
const PAGE_SIZE = 200;

/**
 * Reads the organization's own units — `GET /organizations/{orgKey}/units`.
 *
 * A failure resolves to `[]`, as for `EntityDefinitionService`: a deployment may point `BACKEND_SERVICE_ROOT` at a host
 * that does not serve this resource (the json-server mock does not), and "no units of its own" is then an ordinary
 * answer — the standard catalog.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationUnitService {
  private readonly httpClient = inject(HttpClient);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION, { optional: true });

  findAll(): Observable<OrganizationUnit[]> {
    const root = serviceRootOf(this.runtimeConfiguration, ENTITY_SERVICE_ROOT_KEY);
    if (!root) return of([]);
    return this.httpClient.get<{ content?: OrganizationUnit[] } | OrganizationUnit[]>(`${root}/units`, { params: { page: 0, size: PAGE_SIZE } }).pipe(
      map((response) => unitsOf(response)),
      catchError(() => of([])),
    );
  }
}

/** The units of a response, whether it is the contract's page or a bare array; anything else is none. */
function unitsOf(response: { content?: OrganizationUnit[] } | OrganizationUnit[] | null): OrganizationUnit[] {
  if (Array.isArray(response)) return response;
  const content = response?.content;
  return Array.isArray(content) ? content : [];
}
