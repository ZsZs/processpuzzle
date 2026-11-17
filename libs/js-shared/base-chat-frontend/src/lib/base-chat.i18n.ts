import type { TranslationSource } from '@processpuzzle/util';

/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/base-chat-frontend/src/assets/i18n/base_chat/*.json` and are published with the
 * package; a consuming application copies them to `assets/i18n/base_chat` (see the testbed's
 * `project.json`).
 *
 * The alias is spelled out wherever the scope is registered: transloco camel-cases the default alias, which
 * would turn `base_chat` into `baseChat` and silently miss every key.
 */
export const BASE_CHAT_TRANSLOCO_SCOPE = 'base_chat';

/**
 * Scope of the generic framework labels (`base_entity.tabs.*`, `base_entity.toolbar.*`), whose files base-entity
 * owns. Registered next to {@link BASE_CHAT_TRANSLOCO_SCOPE} on the authoring route, because a route that declares
 * a transloco scope replaces the collection it inherits rather than adding to it.
 */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

/** Key root of the `Chat Definition` entity name (`._self`) and of its attribute labels. */
export const CHAT_DEFINITION_I18N_SCOPE = `${BASE_CHAT_TRANSLOCO_SCOPE}.chat_definition`;

/**
 * Where this library's transloco bundles come from when the application ships without its assets, spread into the
 * application's `TRANSLATION_SOURCE_REGISTRY`. The bundles normally arrive as static files and the loader tries the
 * asset first; base-chat-backend does not serve a translations resource yet.
 */
export const BASE_CHAT_TRANSLATION_SOURCE: TranslationSource = {
  scopes: [BASE_CHAT_TRANSLOCO_SCOPE],
  serviceRootKey: 'CHAT_SERVICE_ROOT',
  segment: 'chat',
};

/**
 * Label of the Chat tab an entity's screens get from base-chat. It renders on the entity's own route, so that
 * route has to provide this library's scope too: a route's own transloco scope hides the root registration that
 * `provideEntityChatTab()` makes.
 */
export const ENTITY_CHAT_I18N_KEY = `${BASE_CHAT_TRANSLOCO_SCOPE}.tabs.chat`;
