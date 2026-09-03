import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { Invoice, Plan, Subscription } from '../domain/billing/billing';
import { InvoiceMapper, PlanMapper, SubscriptionMapper } from '../domain/billing/billing.mapper';
import { InvoiceService, PlanService, SubscriptionService } from '../domain/billing/billing.service';
import { InvoiceStore, PlanStore, SubscriptionStore } from '../domain/billing/billing.stores';
import { createInvoiceDescriptor, createPlanDescriptor, createSubscriptionDescriptor } from '../domain/billing/billing.descriptors';

@Injectable()
export class PlanFacade extends BaseEntityFacade<Plan> {
  readonly entityType = Plan;

  private readonly mapperRef = inject(PlanMapper);
  private readonly serviceRef = inject(PlanService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return PlanStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createPlanDescriptor();
  }
}

@Injectable()
export class SubscriptionFacade extends BaseEntityFacade<Subscription> {
  readonly entityType = Subscription;

  private readonly mapperRef = inject(SubscriptionMapper);
  private readonly serviceRef = inject(SubscriptionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return SubscriptionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createSubscriptionDescriptor();
  }
}

@Injectable()
export class InvoiceFacade extends BaseEntityFacade<Invoice> {
  readonly entityType = Invoice;

  private readonly mapperRef = inject(InvoiceMapper);
  private readonly serviceRef = inject(InvoiceService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return InvoiceStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createInvoiceDescriptor();
  }
}
