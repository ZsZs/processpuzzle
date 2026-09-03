/*
 * Public API Surface of @processpuzzle/platform-admin
 */

export { Organization, OrganizationStatus } from './lib/domain/organization';
export { OrganizationMapper } from './lib/domain/organization.mapper';
export { type AdminUser, OrganizationService } from './lib/domain/organization.service';
export { OrganizationStore } from './lib/domain/organization.store';
export { ORGANIZATION_ENTITY_NAME, createOrganizationDescriptor } from './lib/domain/organization.descriptors';

export {
  BillingInterval,
  Invoice,
  type InvoiceLine,
  InvoiceStatus,
  type OrganizationBilling,
  Plan,
  type PlanLimit,
  Subscription,
  SubscriptionStatus,
  UsageMetric,
  type UsageRecord,
  formatMinor,
} from './lib/domain/billing/billing';
export { InvoiceMapper, OrganizationBillingMapper, PlanMapper, SubscriptionMapper } from './lib/domain/billing/billing.mapper';
export { InvoiceService, OrganizationBillingService, PlanService, SubscriptionService } from './lib/domain/billing/billing.service';
export { InvoiceStore, PlanStore, SubscriptionStore } from './lib/domain/billing/billing.stores';
export {
  INVOICE_ENTITY_NAME,
  PLAN_ENTITY_NAME,
  SUBSCRIPTION_ENTITY_NAME,
  createInvoiceDescriptor,
  createPlanDescriptor,
  createSubscriptionDescriptor,
} from './lib/domain/billing/billing.descriptors';

export { OrganizationFacade } from './lib/feature/organization.facade';
export { InvoiceFacade, PlanFacade, SubscriptionFacade } from './lib/feature/billing.facades';
export { OrganizationContainerComponent } from './lib/feature/organization-container.component';
export { AssignAdminDialog, type AssignAdminDialogData, type AssignAdminDialogResult } from './lib/feature/assign-admin.dialog';

export {
  ASSIGN_ADMIN_I18N_SCOPE,
  BASE_ENTITY_TRANSLOCO_SCOPE,
  INVOICE_I18N_SCOPE,
  ORGANIZATION_BILLING_I18N_SCOPE,
  ORGANIZATION_I18N_SCOPE,
  PLAN_I18N_SCOPE,
  PLATFORM_ADMIN_TRANSLATION_SOURCE,
  PLATFORM_ADMIN_TRANSLOCO_SCOPE,
  SUBSCRIPTION_I18N_SCOPE,
} from './lib/platform-admin.i18n';
export { PLATFORM_ADMIN_ENTITY_FACADES, PLATFORM_ADMIN_FACADE_PROVIDERS, PLATFORM_ADMIN_ROUTES } from './lib/platform-admin.routes';
