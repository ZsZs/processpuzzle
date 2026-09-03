import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import {
  BillingInterval,
  Invoice,
  InvoiceStatus,
  Plan,
  Subscription,
  SubscriptionStatus,
  UsageMetric,
  formatMinor,
} from './billing';
import { createInvoiceDescriptor, createPlanDescriptor, createSubscriptionDescriptor } from './billing.descriptors';
import { InvoiceMapper, OrganizationBillingMapper, PlanMapper, SubscriptionMapper } from './billing.mapper';

function leaves(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? leaves(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('billing models', () => {
  it('formats minor units and leaves absent amounts blank', () => {
    expect(formatMinor(undefined, 'EUR')).toBe('');
    expect(formatMinor(4900, 'EUR')).toMatch(/49[,.]00/);
  });

  it('applies defaults and exposes formatted plan and invoice prices', () => {
    const plan = new Plan();
    const subscription = new Subscription();
    const invoice = new Invoice();

    expect(plan).toMatchObject({ id: '', code: '', name: '', interval: BillingInterval.MONTHLY, currency: 'EUR', amountMinor: 0, limits: [] });
    expect(subscription).toMatchObject({ id: '', orgKey: '', planCode: '', status: SubscriptionStatus.TRIALING });
    expect(invoice).toMatchObject({ id: '', orgKey: '', status: InvoiceStatus.DRAFT, currency: 'EUR', totalMinor: 0, lines: [] });
    expect(new Plan('standard', 'Standard', undefined, BillingInterval.YEARLY, 'USD', 1200).price).toMatch(/12/);
    expect(new Invoice({ totalMinor: 2500, currency: 'USD' }).total).toMatch(/25/);
  });
});

describe('billing mappers', () => {
  const planMapper = new PlanMapper();
  const subscriptionMapper = new SubscriptionMapper();
  const invoiceMapper = new InvoiceMapper();

  it('maps every model and preserves read-only DTOs', () => {
    const plan = planMapper.fromDto({ code: 'standard', name: 'Standard', interval: 'YEARLY', currency: 'USD', amountMinor: 1200, limits: [{ metric: UsageMetric.USERS, maxQuantity: 10 }] });
    const subscription = subscriptionMapper.fromDto({ id: 'sub-1', orgKey: 'acme', planCode: 'standard', status: 'ACTIVE' });
    const invoice = invoiceMapper.fromDto({ id: 'inv-1', orgKey: 'acme', number: '2026-001', status: 'ISSUED', currency: 'USD', totalMinor: 1200, lines: [] });

    expect(plan).toBeInstanceOf(Plan);
    expect(subscription).toMatchObject({ id: 'sub-1', status: SubscriptionStatus.ACTIVE });
    expect(invoice).toMatchObject({ id: 'inv-1', status: InvoiceStatus.ISSUED });
    expect(planMapper.toDto(plan)).toEqual({ ...plan });
    expect(subscriptionMapper.toDto(subscription)).toEqual({ ...subscription });
    expect(invoiceMapper.toDto(invoice)).toEqual({ ...invoice });
  });

  it('maps optional billing halves only when present', () => {
    const mapper = new OrganizationBillingMapper(planMapper, subscriptionMapper, invoiceMapper);

    expect(mapper.fromDto({ orgKey: 'acme' })).toEqual({ orgKey: 'acme', subscription: undefined, plan: undefined, usage: [], invoices: [] });

    const billing = mapper.fromDto({
      orgKey: 'acme',
      plan: { code: 'standard' },
      subscription: { id: 'sub-1', status: 'ACTIVE' },
      usage: [{ id: 'usage-1', orgKey: 'acme', metric: UsageMetric.USERS, quantity: 2 }],
      invoices: [{ id: 'inv-1', totalMinor: 100 }],
    });
    expect(billing.plan).toBeInstanceOf(Plan);
    expect(billing.subscription).toBeInstanceOf(Subscription);
    expect(billing.invoices[0]).toBeInstanceOf(Invoice);
  });
});

describe('billing descriptors', () => {
  it.each([
    ['plan', createPlanDescriptor, 'Plan', 'code'],
    ['subscription', createSubscriptionDescriptor, 'Subscription', 'orgKey'],
    ['invoice', createInvoiceDescriptor, 'Invoice', 'number'],
  ] as const)('makes the %s screen fully read-only', (_, createDescriptor, entityName, titleKey) => {
    const descriptor = createDescriptor();
    const attributes = leaves(descriptor.attrDescriptors);

    expect(descriptor.entityName).toBe(entityName);
    expect(descriptor.titleKey).toBe(titleKey);
    expect(descriptor.isAbstract).toBe(true);
    expect(attributes).not.toHaveLength(0);
    expect(attributes.every((attribute) => attribute.disabled)).toBe(true);
  });
});
