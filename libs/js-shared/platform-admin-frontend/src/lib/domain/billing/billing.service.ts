import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { Observable, map } from 'rxjs';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { Invoice, OrganizationBilling, Plan, Subscription } from './billing';
import { InvoiceMapper, OrganizationBillingMapper, PlanMapper, SubscriptionMapper } from './billing.mapper';

/** `/platform/plans` — the catalog. Unpaged by contract: a plan list short enough to render at once. */
@Injectable({ providedIn: 'root' })
export class PlanService extends BaseEntityRestService<Plan> {
  constructor(protected override entityMapper: PlanMapper) {
    super(entityMapper, 'PLATFORM_ADMIN_SERVICE_ROOT', 'platform/plans');
  }
}

/** `/platform/subscriptions` across all tenants. Filter to one with `orgKey=="..."` in the RSQL. */
@Injectable({ providedIn: 'root' })
export class SubscriptionService extends BaseEntityRestService<Subscription> {
  constructor(protected override entityMapper: SubscriptionMapper) {
    super(entityMapper, 'PLATFORM_ADMIN_SERVICE_ROOT', 'platform/subscriptions');
  }
}

/** `/platform/invoices` across all tenants. */
@Injectable({ providedIn: 'root' })
export class InvoiceService extends BaseEntityRestService<Invoice> {
  constructor(protected override entityMapper: InvoiceMapper) {
    super(entityMapper, 'PLATFORM_ADMIN_SERVICE_ROOT', 'platform/invoices');
  }
}

/**
 * One tenant's billing position: `GET /platform/organizations/{orgKey}/billing`.
 *
 * A plain service rather than a `BaseEntityRestService`, because the response is a projection of four
 * things and not an entity — it has no id, so there is nothing for a store to key it by.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationBillingService {
  private readonly httpClient = inject(HttpClient);
  private readonly mapper = inject(OrganizationBillingMapper);
  private readonly baseUrl = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'PLATFORM_ADMIN_SERVICE_ROOT');

  findByOrgKey(orgKey: string): Observable<OrganizationBilling> {
    return this.httpClient.get<unknown>(`${this.baseUrl}/platform/organizations/${orgKey}/billing`).pipe(map((dto) => this.mapper.fromDto(dto)));
  }
}
