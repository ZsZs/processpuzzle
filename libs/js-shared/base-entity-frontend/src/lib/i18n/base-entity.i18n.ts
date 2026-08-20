import type { TranslationSource } from '@processpuzzle/util';

/**
 * Transloco scope of this library — the generic screen labels (`base_entity.tabs.*`,
 * `base_entity.toolbar.*`) every generated list and form renders.
 *
 * Declared here as a constant so the scope name has one home; the routes and components that register
 * it still spell the alias out, because transloco camel-cases a default alias and would turn
 * `base_entity` into `baseEntity`, missing every key below it.
 */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

/**
 * Where this library's transloco bundles come from when the application ships without its assets.
 *
 * Spread into the application's `TRANSLATION_SOURCE_REGISTRY`, the way its facades are spread into
 * `BASE_ENTITY_FACADE_REGISTRY`. The scope normally arrives as a static file — the build copies it into
 * `assets/i18n/base_entity` and the loader tries the asset first — so this is reached only by a host
 * that skips that copy step. It is declared here rather than in the application because which backend
 * owns a scope is base-entity's knowledge, not its caller's.
 */
export const BASE_ENTITY_TRANSLATION_SOURCE: TranslationSource = {
  scopes: [BASE_ENTITY_TRANSLOCO_SCOPE],
  serviceRootKey: 'ENTITY_SERVICE_ROOT',
  segment: 'entity',
};
