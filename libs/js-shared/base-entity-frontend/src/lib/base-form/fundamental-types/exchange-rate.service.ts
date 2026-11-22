import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, map, Observable, of } from 'rxjs';
import { ENTITY_SERVICE_ROOT_KEY } from '../../base-entity-definition/entity-definition.service';

/** An exchange rate of the organization (`ExchangeRate` of the contract): what one `from` is worth in `to`, from `validFrom` on. */
export interface ExchangeRateRecord {
  id?: string;
  from: string;
  to: string;
  /** Decimal text. */
  rate: string;
  /** An ISO date, `2026-07-01`. */
  validFrom: string;
  version?: number;
}

/** The contract's own maximum for `size`; an organization quotes a handful of pairs, so one page is the whole table. */
const PAGE_SIZE = 200;

/**
 * Reads the organization's exchange rates — `GET /organizations/{orgKey}/exchange-rates`.
 *
 * A failure resolves to `[]`, as for {@link OrganizationUnitService}: a deployment may point `BACKEND_SERVICE_ROOT` at a host that
 * does not serve this resource (the json-server mock does not), and "no rates" is then an ordinary answer — money converts to
 * nothing but itself.
 */
@Injectable({ providedIn: 'root' })
export class ExchangeRateService {
  private readonly httpClient = inject(HttpClient);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION, { optional: true });

  findAll(): Observable<ExchangeRateRecord[]> {
    const root = serviceRootOf(this.runtimeConfiguration, ENTITY_SERVICE_ROOT_KEY);
    if (!root) return of([]);
    return this.httpClient.get<{ content?: ExchangeRateRecord[] } | ExchangeRateRecord[]>(`${root}/exchange-rates`, { params: { page: 0, size: PAGE_SIZE } }).pipe(
      map((response) => ratesOf(response)),
      catchError(() => of([])),
    );
  }
}

/** The rates of a response, whether it is the contract's page or a bare array; anything else is none. */
function ratesOf(response: { content?: ExchangeRateRecord[] } | ExchangeRateRecord[] | null): ExchangeRateRecord[] {
  if (Array.isArray(response)) return response;
  const content = response?.content;
  return Array.isArray(content) ? content : [];
}
