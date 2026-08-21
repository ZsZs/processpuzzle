import type { TranslationSource } from '@processpuzzle/util';

/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/base-state-frontend/src/assets/i18n/base_state/*.json` and are published with the
 * package; a consuming application copies them to `assets/i18n/base_state` (see the testbed's
 * `project.json`). The scope is registered on {@link BASE_STATE_ROUTES}, so it loads lazily with the
 * route rather than with the application shell.
 *
 * The alias is spelled out on purpose: transloco camel-cases the default alias, which would turn
 * `base_state` into `baseState` and silently miss every key below.
 */
export const BASE_STATE_TRANSLOCO_SCOPE = 'base_state';

/**
 * Scope of the generic framework labels (`base_entity.tabs.*`, `base_entity.toolbar.*`), whose files
 * base-entity owns. It has to be registered next to {@link BASE_STATE_TRANSLOCO_SCOPE} wherever the
 * generic screens are hosted: `BaseEntityTabsComponent` translates from it through `TranslocoPipe`,
 * which caches the value it resolved for a key on first render. If only `base_state` were loaded, the
 * tabs would keep the raw key even after the toolbar — which registers this scope for itself —
 * triggers the load.
 */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

/** Key root of the `State Machine Definition` entity name (`._self`) and of its attribute labels. */
export const STATE_MACHINE_DEFINITION_I18N_SCOPE = `${BASE_STATE_TRANSLOCO_SCOPE}.state_machine_definition`;

/**
 * Key roots of the nested definitions the `State Machine Definition` form contains through
 * `EMBEDDED_COMPONENTS` controls. They are children of {@link BASE_STATE_TRANSLOCO_SCOPE} rather than
 * scopes of their own, because the whole graph is edited under {@link BASE_STATE_ROUTES} and one scope
 * registration has to cover all of it — the embedded route branches add none.
 */
export const STATE_MACHINE_STATE_I18N_SCOPE = `${BASE_STATE_TRANSLOCO_SCOPE}.state_machine_state`;
export const STATE_MACHINE_TRANSITION_I18N_SCOPE = `${BASE_STATE_TRANSLOCO_SCOPE}.state_machine_transition`;
export const STATE_TRANSITION_GUARD_I18N_SCOPE = `${BASE_STATE_TRANSLOCO_SCOPE}.state_transition_guard`;
export const STATE_TRANSITION_ACTION_I18N_SCOPE = `${BASE_STATE_TRANSLOCO_SCOPE}.state_transition_action`;

/**
 * Where this library's transloco bundles come from when the application ships without its assets.
 *
 * Spread into the application's `TRANSLATION_SOURCE_REGISTRY`, the way its facades are spread into
 * `BASE_ENTITY_FACADE_REGISTRY`. The scope normally arrives as static files — the build copies them
 * into `assets/i18n/base_state` and the loader tries the asset first — so this is reached only by a host
 * that skips that copy step. `segment` is base-state-backend's own translations resource
 * (`/organizations/{orgKey}/state/translations/...`, see `StateTranslationEndpoint`).
 */
export const BASE_STATE_TRANSLATION_SOURCE: TranslationSource = {
  scopes: [BASE_STATE_TRANSLOCO_SCOPE],
  serviceRootKey: 'STATE_SERVICE_ROOT',
  segment: 'state',
};
