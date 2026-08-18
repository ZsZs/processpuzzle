/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/base-app-frontend/src/assets/i18n/base_app/*.json` and are published with the
 * package; a consuming application copies them to `assets/i18n/base_app` (see the testbed's
 * `project.json`). The scope is registered on {@link BASE_APP_ROUTES}, so it loads lazily with the
 * route rather than with the application shell.
 *
 * The alias is spelled out on purpose: transloco camel-cases the default alias, which would turn
 * `base_app` into `baseApp` and silently miss every key below.
 */
export const BASE_APP_TRANSLOCO_SCOPE = 'base_app';

/**
 * Scope of the generic framework labels (`base_entity.tabs.*`, `base_entity.toolbar.*`), whose files
 * base-entity owns. It has to be registered next to {@link BASE_APP_TRANSLOCO_SCOPE} wherever the
 * generic screens are hosted: `BaseEntityTabsComponent` translates from it through `TranslocoPipe`,
 * which caches the value it resolved for a key on first render. If only `base_app` were loaded, the
 * tabs would keep the raw key even after the toolbar — which registers this scope for itself —
 * triggers the load.
 */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

/** Key root of the `AppDefinition` entity name (`._self`) and of its attribute labels. */
export const APP_DEFINITION_I18N_SCOPE = `${BASE_APP_TRANSLOCO_SCOPE}.app_definition`;

/**
 * Label of the Preview tab. Under this library's own scope rather than `base_entity.tabs.*`: the generic
 * tabs are List and Details, and an application preview is this feature's contribution, not the framework's.
 * Resolved with `{ entity }` like the generic tab labels, so a translation may name the entity if it reads
 * better that way.
 */
export const APP_PREVIEW_I18N_KEY = `${APP_DEFINITION_I18N_SCOPE}.tabs.preview`;

/**
 * Key root of the `ModuleDefinition` entity name and attribute labels. A child of this library's scope
 * like the one above, and not to be confused with `ModuleDefinition.translocoScope`, which is a scope an
 * *authored* module's own translations load under — see {@link moduleTranslocoScope}. These keys are the
 * framework's labels for the authoring form; that one is the tenant's content.
 */
export const MODULE_DEFINITION_I18N_SCOPE = `${BASE_APP_TRANSLOCO_SCOPE}.module_definition`;

/**
 * Key roots of the nested definitions the `AppDefinition` form contains through `EMBEDDED_COMPONENTS`
 * controls. They are children of {@link BASE_APP_TRANSLOCO_SCOPE} rather than scopes of their own,
 * because the whole graph is edited under `BASE_APP_ROUTES` and one scope registration has to cover
 * all of it — the embedded route branches add none.
 */
export const APP_REGION_I18N_SCOPE = `${BASE_APP_TRANSLOCO_SCOPE}.app_region`;
export const APP_ROUTE_I18N_SCOPE = `${BASE_APP_TRANSLOCO_SCOPE}.app_route`;
export const APP_MODULE_MOUNT_I18N_SCOPE = `${BASE_APP_TRANSLOCO_SCOPE}.app_module_mount`;
export const APP_NAV_ITEM_I18N_SCOPE = `${BASE_APP_TRANSLOCO_SCOPE}.app_nav_item`;
export const APP_WIDGET_I18N_SCOPE = `${BASE_APP_TRANSLOCO_SCOPE}.app_widget`;

/** Keys of the `Publish` form action contributed by `AppDefinitionContainerComponent`. */
export const PUBLISH_BUTTON_I18N_KEY = `${BASE_APP_TRANSLOCO_SCOPE}.publish.button`;
export const PUBLISH_TOOLTIP_I18N_KEY = `${BASE_APP_TRANSLOCO_SCOPE}.publish.tooltip`;
