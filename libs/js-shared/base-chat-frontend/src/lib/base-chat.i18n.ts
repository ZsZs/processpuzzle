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
