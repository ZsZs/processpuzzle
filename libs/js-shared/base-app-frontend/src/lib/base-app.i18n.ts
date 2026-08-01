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

/** Keys of the `Publish` form action contributed by `AppDefinitionContainerComponent`. */
export const PUBLISH_BUTTON_I18N_KEY = `${BASE_APP_TRANSLOCO_SCOPE}.publish.button`;
export const PUBLISH_TOOLTIP_I18N_KEY = `${BASE_APP_TRANSLOCO_SCOPE}.publish.tooltip`;
