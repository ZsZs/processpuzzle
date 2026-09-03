import { BaseEntity } from '@processpuzzle/base-entity';

export enum BillingInterval {
  MONTHLY = 'MONTHLY',
  YEARLY = 'YEARLY',
}

export enum SubscriptionStatus {
  TRIALING = 'TRIALING',
  ACTIVE = 'ACTIVE',
  PAST_DUE = 'PAST_DUE',
  CANCELED = 'CANCELED',
}

export enum InvoiceStatus {
  DRAFT = 'DRAFT',
  ISSUED = 'ISSUED',
  PAID = 'PAID',
  VOID = 'VOID',
}

export enum UsageMetric {
  USERS = 'USERS',
  ENTITY_OBJECTS = 'ENTITY_OBJECTS',
  DOCUMENTS = 'DOCUMENTS',
  WORKFLOW_INSTANCES = 'WORKFLOW_INSTANCES',
  STORAGE_BYTES = 'STORAGE_BYTES',
  API_CALLS = 'API_CALLS',
}

/**
 * Money as an integer of the currency's minor unit — cents — and never as a `number` of major units.
 *
 * JavaScript has one numeric type and it is a binary float, so `49.00` is not exactly representable
 * and a column of them does not sum to what a customer was charged. Keeping the integer the API sends
 * and formatting it for display is the only arrangement in which the figure on screen is the figure
 * on the invoice. {@link formatMinor} is the one place that division happens.
 */
export function formatMinor(amountMinor: number | undefined, currency: string | undefined): string {
  if (amountMinor === undefined || amountMinor === null) return '';
  const major = amountMinor / 100;
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currency || 'EUR' }).format(major);
}

/** A per-metric ceiling on a plan. An absent metric means unmetered; `0` means unavailable. */
export interface PlanLimit {
  metric: UsageMetric;
  maxQuantity: number;
}

/** A purchasable entitlement level. Keyed by `code`, which a subscription names. */
export class Plan implements BaseEntity {
  readonly id: string;
  readonly code: string;
  name: string;
  description: string | undefined;
  interval: BillingInterval;
  currency: string;
  amountMinor: number;
  limits: PlanLimit[];

  constructor(code?: string, name?: string, description?: string, interval?: BillingInterval, currency?: string, amountMinor?: number, limits?: PlanLimit[]) {
    this.code = code ?? '';
    this.id = this.code;
    this.name = name ?? '';
    this.description = description;
    this.interval = interval ?? BillingInterval.MONTHLY;
    this.currency = currency ?? 'EUR';
    this.amountMinor = amountMinor ?? 0;
    this.limits = limits ?? [];
  }

  /** The price, formatted for display. See {@link formatMinor} on why the raw value stays an integer. */
  get price(): string {
    return formatMinor(this.amountMinor, this.currency);
  }
}

/** What a tenant is currently entitled to, and for which period. */
export class Subscription implements BaseEntity {
  readonly id: string;
  orgKey: string;
  planCode: string;
  status: SubscriptionStatus;
  currentPeriodStart: string | undefined;
  currentPeriodEnd: string | undefined;
  canceledAt: string | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;

  constructor(
    id?: string,
    orgKey?: string,
    planCode?: string,
    status?: SubscriptionStatus,
    currentPeriodStart?: string,
    currentPeriodEnd?: string,
    canceledAt?: string,
    createdAt?: string,
    updatedAt?: string,
  ) {
    this.id = id ?? '';
    this.orgKey = orgKey ?? '';
    this.planCode = planCode ?? '';
    this.status = status ?? SubscriptionStatus.TRIALING;
    this.currentPeriodStart = currentPeriodStart;
    this.currentPeriodEnd = currentPeriodEnd;
    this.canceledAt = canceledAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }
}

/** One metric's measured quantity for one tenant over one period. */
export interface UsageRecord {
  id: string;
  orgKey: string;
  metric: UsageMetric;
  quantity: number;
  periodStart?: string;
  periodEnd?: string;
  recordedAt?: string;
}

/** One charge on an invoice. `metric` is absent for a flat plan fee. */
export interface InvoiceLine {
  description: string;
  metric?: UsageMetric;
  quantity: number;
  unitAmountMinor: number;
  amountMinor: number;
}

/** What a tenant was charged for one period. A `DRAFT` has no number: numbers must be gapless. */
export class Invoice implements BaseEntity {
  readonly id: string;
  orgKey: string;
  number: string | undefined;
  status: InvoiceStatus;
  currency: string;
  totalMinor: number;
  periodStart: string | undefined;
  periodEnd: string | undefined;
  issuedAt: string | undefined;
  paidAt: string | undefined;
  lines: InvoiceLine[];

  constructor(
    id?: string,
    orgKey?: string,
    invoiceNumber?: string,
    status?: InvoiceStatus,
    currency?: string,
    totalMinor?: number,
    periodStart?: string,
    periodEnd?: string,
    issuedAt?: string,
    paidAt?: string,
    lines?: InvoiceLine[],
  ) {
    this.id = id ?? '';
    this.orgKey = orgKey ?? '';
    this.number = invoiceNumber;
    this.status = status ?? InvoiceStatus.DRAFT;
    this.currency = currency ?? 'EUR';
    this.totalMinor = totalMinor ?? 0;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.issuedAt = issuedAt;
    this.paidAt = paidAt;
    this.lines = lines ?? [];
  }

  get total(): string {
    return formatMinor(this.totalMinor, this.currency);
  }
}

/**
 * Everything the billing screen shows for one tenant, in one response.
 *
 * `subscription` and `plan` are independently optional, and both being absent is the common case
 * today rather than an error: nothing in the platform creates subscriptions yet. A subscription can
 * also name a plan withdrawn from the catalog, which is why the plan is optional even when the
 * subscription is present.
 */
export interface OrganizationBilling {
  orgKey: string;
  subscription?: Subscription;
  plan?: Plan;
  usage: UsageRecord[];
  invoices: Invoice[];
}
