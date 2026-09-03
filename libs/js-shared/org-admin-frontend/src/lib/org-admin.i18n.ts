import type { TranslationSource } from '@processpuzzle/util';

/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/org-admin-frontend/src/assets/i18n/org_admin/*.json` and are published with the
 * package; a consuming application copies them to `assets/i18n/org_admin`.
 *
 * The alias is spelled out wherever this is registered: transloco camel-cases the default one, so
 * `org_admin` would silently become `orgAdmin` and miss every key below it. A hyphen fares no better,
 * so there is no spelling of the scope that makes the default safe.
 */
export const ORG_ADMIN_TRANSLOCO_SCOPE = 'org_admin';

/**
 * Scope of the generic framework labels, whose files base-entity owns. Registered next to
 * {@link ORG_ADMIN_TRANSLOCO_SCOPE} wherever the generic screens are hosted, because a route that
 * declares `TRANSLOCO_SCOPE` *replaces* the collection it inherits rather than adding to it — and
 * `BaseEntityTabsComponent` caches the value it resolved for a key on first render, so with only
 * `org_admin` loaded the tabs keep the raw key even after the toolbar triggers the other load.
 */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

/** Key root of the `Organization User` entity name (`._self`) and of its attribute labels. */
export const ORGANIZATION_USER_I18N_SCOPE = `${ORG_ADMIN_TRANSLOCO_SCOPE}.organization_user`;

/** Key root of the `Organization Role` entity. */
export const ORGANIZATION_ROLE_I18N_SCOPE = `${ORG_ADMIN_TRANSLOCO_SCOPE}.organization_role`;

/** Key root of the role-assignment screen, which is about a user rather than about a role. */
export const ROLE_ASSIGNMENT_I18N_SCOPE = `${ORGANIZATION_USER_I18N_SCOPE}.roles`;

/**
 * Where this library's transloco bundles come from when the application ships without its assets.
 *
 * `segment` is `admin`, matching where every path in org-admin-api.yaml lives. There is no
 * translations endpoint behind it: org-admin-backend persists nothing at all, so it serves no
 * bundles. The entry exists so that adding one later needs no change on this side.
 */
export const ORG_ADMIN_TRANSLATION_SOURCE: TranslationSource = {
  scopes: [ORG_ADMIN_TRANSLOCO_SCOPE],
  serviceRootKey: 'ORG_ADMIN_SERVICE_ROOT',
  segment: 'admin',
};
