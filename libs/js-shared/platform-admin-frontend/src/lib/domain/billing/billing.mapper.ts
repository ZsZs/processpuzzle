import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import {
  BillingInterval,
  Invoice,
  InvoiceProperties,
  InvoiceStatus,
  OrganizationBilling,
  Plan,
  Subscription,
  SubscriptionProperties,
  SubscriptionStatus,
} from './billing';

type PlanDto = Partial<Plan>;
type SubscriptionDto = Partial<Subscription>;
type InvoiceDto = Partial<Invoice>;

/**
 * Reads the billing shapes. There is no `toDto` worth writing beyond the interface's requirement:
 * every billing operation in platform-admin-api.yaml is a read, because the platform has no payment
 * provider. A write appearing here would be the signal that that has changed.
 */
@Injectable({ providedIn: 'root' })
export class PlanMapper implements BaseEntityMapper<Plan> {
  fromDto(dto: unknown): Plan {
    const source = dto as PlanDto;
    return new Plan(source.code, source.name, source.description, source.interval as BillingInterval | undefined, source.currency, source.amountMinor, source.limits);
  }

  toDto(entity: Plan): unknown {
    return { ...entity };
  }
}

@Injectable({ providedIn: 'root' })
export class SubscriptionMapper implements BaseEntityMapper<Subscription> {
  fromDto(dto: unknown): Subscription {
    const source = dto as SubscriptionDto;
    const properties: SubscriptionProperties = {
      id: source.id,
      orgKey: source.orgKey,
      planCode: source.planCode,
      status: source.status as SubscriptionStatus | undefined,
      currentPeriodStart: source.currentPeriodStart,
      currentPeriodEnd: source.currentPeriodEnd,
      canceledAt: source.canceledAt,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    };
    return new Subscription(properties);
  }

  toDto(entity: Subscription): unknown {
    return { ...entity };
  }
}

@Injectable({ providedIn: 'root' })
export class InvoiceMapper implements BaseEntityMapper<Invoice> {
  fromDto(dto: unknown): Invoice {
    const source = dto as InvoiceDto;
    const properties: InvoiceProperties = {
      id: source.id,
      orgKey: source.orgKey,
      number: source.number,
      status: source.status as InvoiceStatus | undefined,
      currency: source.currency,
      totalMinor: source.totalMinor,
      periodStart: source.periodStart,
      periodEnd: source.periodEnd,
      issuedAt: source.issuedAt,
      paidAt: source.paidAt,
      lines: source.lines,
    };
    return new Invoice(properties);
  }

  toDto(entity: Invoice): unknown {
    return { ...entity };
  }
}

/**
 * Reads a whole billing position. Not a `BaseEntityMapper`: `OrganizationBilling` is a projection of
 * four things rather than an entity, so it has no id and belongs to no store.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationBillingMapper {
  constructor(
    private readonly planMapper: PlanMapper,
    private readonly subscriptionMapper: SubscriptionMapper,
    private readonly invoiceMapper: InvoiceMapper,
  ) {}

  fromDto(dto: unknown): OrganizationBilling {
    const source = dto as Partial<OrganizationBilling>;
    return {
      orgKey: source.orgKey ?? '',
      // Each half is mapped only when present. A tenant with no subscription is normal, and a
      // subscription can name a plan that has left the catalog, so neither implies the other.
      subscription: source.subscription ? this.subscriptionMapper.fromDto(source.subscription) : undefined,
      plan: source.plan ? this.planMapper.fromDto(source.plan) : undefined,
      usage: source.usage ?? [],
      invoices: (source.invoices ?? []).map((invoice) => this.invoiceMapper.fromDto(invoice)),
    };
  }
}
