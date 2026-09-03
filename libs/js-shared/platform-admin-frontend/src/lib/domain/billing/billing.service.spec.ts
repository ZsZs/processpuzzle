import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { InvoiceMapper, OrganizationBillingMapper, PlanMapper, SubscriptionMapper } from './billing.mapper';
import { OrganizationBillingService } from './billing.service';

describe('OrganizationBillingService', () => {
  const root = 'http://localhost:8080/api';
  let service: OrganizationBillingService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { PLATFORM_ADMIN_SERVICE_ROOT: root } } },
        PlanMapper,
        SubscriptionMapper,
        InvoiceMapper,
        OrganizationBillingMapper,
        OrganizationBillingService,
      ],
    });
    service = TestBed.inject(OrganizationBillingService);
    controller = TestBed.inject(HttpTestingController);
  });

  it('gets and maps the tenant billing projection', async () => {
    const pending = firstValueFrom(service.findByOrgKey('acme'));
    const request = controller.expectOne(`${root}/platform/organizations/acme/billing`);

    expect(request.request.method).toBe('GET');
    request.flush({ orgKey: 'acme', plan: { code: 'standard' }, usage: [], invoices: [] });

    expect(await pending).toMatchObject({ orgKey: 'acme', plan: { code: 'standard' } });
  });
});
