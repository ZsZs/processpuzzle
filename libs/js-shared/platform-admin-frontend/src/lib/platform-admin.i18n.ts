import type { TranslationSource } from '@processpuzzle/util';

/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/platform-admin-frontend/src/assets/i18n/platform_admin/*.json` and are published
 * with the package; a consuming application copies them to `assets/i18n/platform_admin` (see
 * `apps/platform-admin/project.json`).
 *
 * The alias is spelled out wherever this is registered, and that is not a style choice: transloco
 * camel-cases the default alias, which would turn `platform_admin` into `platformAdmin` and silently
 * miss every key below it. Both the underscore and a hyphen suffer the same fate, so there is no
 * spelling of the scope that makes the default safe.
 */
export const PLATFORM_ADMIN_TRANSLOCO_SCOPE = 'platform_admin';

/**
 * Scope of the generic framework labels (`base_entity.tabs.*`, `base_entity.toolbar.*`), whose files
 * base-entity owns. It has to be registered next to {@link PLATFORM_ADMIN_TRANSLOCO_SCOPE} wherever
 * the generic screens are hosted: a route that declares `TRANSLOCO_SCOPE` *replaces* the collection it
 * inherits rather than adding to it, and `BaseEntityTabsComponent` caches the value it resolved for a
 * key on first render — so with only `platform_admin` loaded the tabs keep the raw key even after the
 * toolbar triggers the other scope's load.
 */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

/** Key root of the `Organization` entity name (`._self`) and of its attribute labels. */
export const ORGANIZATION_I18N_SCOPE = `${PLATFORM_ADMIN_TRANSLOCO_SCOPE}.organization`;

/** Key roots of the read-only billing entities. */
export const PLAN_I18N_SCOPE = `${PLATFORM_ADMIN_TRANSLOCO_SCOPE}.plan`;
export const SUBSCRIPTION_I18N_SCOPE = `${PLATFORM_ADMIN_TRANSLOCO_SCOPE}.subscription`;
export const INVOICE_I18N_SCOPE = `${PLATFORM_ADMIN_TRANSLOCO_SCOPE}.invoice`;

/** Key root of the billing tab shown on an organization, and of the assign-administrator dialog. */
export const ORGANIZATION_BILLING_I18N_SCOPE = `${ORGANIZATION_I18N_SCOPE}.billing`;
export const ASSIGN_ADMIN_I18N_SCOPE = `${ORGANIZATION_I18N_SCOPE}.assign_admin`;

/**
 * Where this library's transloco bundles come from when the application ships without its assets.
 *
 * Spread into the application's `TRANSLATION_SOURCE_REGISTRY`, the way its facades are spread into
 * `BASE_ENTITY_FACADE_REGISTRY`. Normally the scope arrives as static files — the build copies them
 * into `assets/i18n/platform_admin` and the loader tries the asset first — so this is reached only by
 * a host that skips that copy step.
 *
 * `segment` is `platform`, which is where every path in platform-admin-api.yaml lives. There is no
 * translations endpoint behind it yet: platform-admin-backend serves no bundles, unlike base-state and
 * base-app, because its only audience is ProcessPuzzle staff and the platform ships their language
 * files. The entry exists so that adding one later needs no change on this side.
 */
export const PLATFORM_ADMIN_TRANSLATION_SOURCE: TranslationSource = {
  scopes: [PLATFORM_ADMIN_TRANSLOCO_SCOPE],
  serviceRootKey: 'PLATFORM_ADMIN_SERVICE_ROOT',
  segment: 'platform',
};
