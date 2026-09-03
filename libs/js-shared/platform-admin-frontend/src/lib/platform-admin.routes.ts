import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ACTIVE_ENTITY_FACADE, BASE_ENTITY_ROUTES, BaseEntityContainerComponent } from '@processpuzzle/base-entity';
import { BASE_ENTITY_TRANSLOCO_SCOPE, PLATFORM_ADMIN_TRANSLOCO_SCOPE } from './platform-admin.i18n';
import { INVOICE_ENTITY_NAME, PLAN_ENTITY_NAME, SUBSCRIPTION_ENTITY_NAME } from './domain/billing/billing.descriptors';
import { ORGANIZATION_ENTITY_NAME } from './domain/organization.descriptors';
import { InvoiceFacade, PlanFacade, SubscriptionFacade } from './feature/billing.facades';
import { OrganizationContainerComponent } from './feature/organization-container.component';
import { OrganizationFacade } from './feature/organization.facade';

/**
 * The staff surface: organizations, and the three read-only billing screens.
 *
 * Every path segment is `snakeCaseName(entityName)` — `organization`, `plan`, `subscription`,
 * `invoice` — because `BaseFormNavigatorSingletonStore` builds the details URL from the entity name.
 * A mismatch fails quietly: the Name column stops linking and Edit navigates nowhere.
 *
 * `entityName` in `data` is not decoration either: `readEmbeddedBreadcrumb` pushes a level when it
 * meets the route that *declares* the name, and takes that level's base URL from the URL accumulated
 * so far — so it has to sit on the route contributing the entity's own segment, not one deeper.
 *
 * Organizations mount {@link OrganizationContainerComponent} rather than the generic container,
 * because they contribute the suspend / activate / assign-administrator actions. The billing screens
 * mount the generic one: they have no action beyond List and Details, since there is nothing to write.
 */
export const PLATFORM_ADMIN_ROUTES: Routes = [
  {
    path: 'organization',
    title: 'ProcessPuzzle Platform - Organizations',
    data: { icon: 'domain', menuTitle: 'platform.organizations', entityName: ORGANIZATION_ENTITY_NAME },
    component: OrganizationContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: OrganizationFacade }, platformScopes()],
    children: BASE_ENTITY_ROUTES,
  },
  {
    path: 'plan',
    title: 'ProcessPuzzle Platform - Plans',
    data: { icon: 'sell', menuTitle: 'platform.plans', entityName: PLAN_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: PlanFacade }, platformScopes()],
    children: BASE_ENTITY_ROUTES,
  },
  {
    path: 'subscription',
    title: 'ProcessPuzzle Platform - Subscriptions',
    data: { icon: 'receipt_long', menuTitle: 'platform.subscriptions', entityName: SUBSCRIPTION_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: SubscriptionFacade }, platformScopes()],
    children: BASE_ENTITY_ROUTES,
  },
  {
    path: 'invoice',
    title: 'ProcessPuzzle Platform - Invoices',
    data: { icon: 'request_quote', menuTitle: 'platform.invoices', entityName: INVOICE_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: InvoiceFacade }, platformScopes()],
    children: BASE_ENTITY_ROUTES,
  },
];

/**
 * The transloco scopes this branch needs. Both are required and both aliases are spelled out.
 *
 * Both, because a route that declares `TRANSLOCO_SCOPE` *replaces* the collection it inherits rather
 * than adding to it, and the generic tabs and toolbar translate the framework's own `base_entity.*`
 * keys. Aliases spelled out, because transloco camel-cases the default one — `platform_admin` would
 * silently become `platformAdmin` and miss every key below it.
 */
function platformScopes() {
  return provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: PLATFORM_ADMIN_TRANSLOCO_SCOPE, alias: PLATFORM_ADMIN_TRANSLOCO_SCOPE });
}

/**
 * The facades this library's routes resolve `ACTIVE_ENTITY_FACADE` from, and the registry entries a
 * host application spreads into `BASE_ENTITY_FACADE_REGISTRY`.
 *
 * Provided at application level rather than on the routes, because `useExisting` above needs the
 * facade to already exist — a route-level `providers` entry would create a second instance per
 * activation and lose whatever the first had loaded.
 */
export const PLATFORM_ADMIN_FACADE_PROVIDERS = [OrganizationFacade, PlanFacade, SubscriptionFacade, InvoiceFacade];

/** Entity name → facade, for a host's `BASE_ENTITY_FACADE_REGISTRY`. */
export const PLATFORM_ADMIN_ENTITY_FACADES = {
  [ORGANIZATION_ENTITY_NAME]: OrganizationFacade,
  [PLAN_ENTITY_NAME]: PlanFacade,
  [SUBSCRIPTION_ENTITY_NAME]: SubscriptionFacade,
  [INVOICE_ENTITY_NAME]: InvoiceFacade,
};
