import type { TranslationSource } from '@processpuzzle/util';

/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/base-event-frontend/src/assets/i18n/base_event/*.json` and are published with the
 * package; a consuming application copies them to `assets/i18n/base_event` (see the testbed's
 * `project.json`). The scope is registered on {@link BASE_EVENT_ROUTES}, so it loads lazily with the route.
 *
 * The alias is spelled out wherever the scope is registered: transloco camel-cases the default alias, which
 * would turn `base_event` into `baseEvent` and silently miss every key below.
 */
export const BASE_EVENT_TRANSLOCO_SCOPE = 'base_event';

/**
 * Scope of the generic framework labels (`base_entity.tabs.*`, `base_entity.toolbar.*`), whose files
 * base-entity owns. Registered next to {@link BASE_EVENT_TRANSLOCO_SCOPE} on the route, because a route that
 * declares `TRANSLOCO_SCOPE` replaces the collection it inherits rather than adding to it.
 */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

/** Key root of the `Event Definition` entity name (`._self`) and of its attribute labels. */
export const EVENT_DEFINITION_I18N_SCOPE = `${BASE_EVENT_TRANSLOCO_SCOPE}.event_definition`;

/**
 * Where this library's transloco bundles come from when the application ships without its assets.
 *
 * Spread into the application's `TRANSLATION_SOURCE_REGISTRY`, like every other feature's. The bundles
 * normally arrive as static files and the loader tries the asset first; `segment` names the
 * `/organizations/{orgKey}/event/translations/...` resource. base-event-backend does not serve that resource
 * yet, so a host that skips the asset copy step gets no translations for this scope.
 */
export const BASE_EVENT_TRANSLATION_SOURCE: TranslationSource = {
  scopes: [BASE_EVENT_TRANSLOCO_SCOPE],
  serviceRootKey: 'EVENT_SERVICE_ROOT',
  segment: 'event',
};
