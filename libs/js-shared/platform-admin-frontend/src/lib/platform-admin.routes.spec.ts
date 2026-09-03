import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { InvoiceMapper, PlanMapper, SubscriptionMapper } from './domain/billing/billing.mapper';
import { InvoiceService, PlanService, SubscriptionService } from './domain/billing/billing.service';
import { OrganizationMapper } from './domain/organization.mapper';
import { OrganizationService } from './domain/organization.service';
import { InvoiceFacade, PlanFacade, SubscriptionFacade } from './feature/billing.facades';
import { OrganizationFacade } from './feature/organization.facade';
import { PLATFORM_ADMIN_ENTITY_FACADES, PLATFORM_ADMIN_FACADE_PROVIDERS, PLATFORM_ADMIN_ROUTES } from './platform-admin.routes';

describe('platform admin routes and facades', () => {
  it('registers every staff entity at its expected route', () => {
    expect(PLATFORM_ADMIN_ROUTES.map((route) => route.path)).toEqual(['organization', 'plan', 'subscription', 'invoice']);
    expect(Object.keys(PLATFORM_ADMIN_ENTITY_FACADES)).toEqual(['Organization', 'Plan', 'Subscription', 'Invoice']);
    expect(PLATFORM_ADMIN_FACADE_PROVIDERS).toEqual([OrganizationFacade, PlanFacade, SubscriptionFacade, InvoiceFacade]);
  });

  it('binds each facade to its model, mapper, service, store and descriptor', () => {
    const services = {
      organization: {},
      plan: {},
      subscription: {},
      invoice: {},
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: OrganizationMapper, useValue: {} },
        { provide: PlanMapper, useValue: {} },
        { provide: SubscriptionMapper, useValue: {} },
        { provide: InvoiceMapper, useValue: {} },
        { provide: OrganizationService, useValue: services.organization },
        { provide: PlanService, useValue: services.plan },
        { provide: SubscriptionService, useValue: services.subscription },
        { provide: InvoiceService, useValue: services.invoice },
        OrganizationFacade,
        PlanFacade,
        SubscriptionFacade,
        InvoiceFacade,
      ],
    });

    const facades = [TestBed.inject(OrganizationFacade), TestBed.inject(PlanFacade), TestBed.inject(SubscriptionFacade), TestBed.inject(InvoiceFacade)];
    expect(facades.map((facade) => facade.entityName)).toEqual(['Organization', 'Plan', 'Subscription', 'Invoice']);
    expect(facades.map((facade) => facade.mapper)).toEqual([TestBed.inject(OrganizationMapper), TestBed.inject(PlanMapper), TestBed.inject(SubscriptionMapper), TestBed.inject(InvoiceMapper)]);
    expect(facades.map((facade) => facade.service)).toEqual([services.organization, services.plan, services.subscription, services.invoice]);
  });
});
