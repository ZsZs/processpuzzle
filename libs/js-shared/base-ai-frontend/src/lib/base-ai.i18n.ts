import type { TranslationSource } from '@processpuzzle/util';

/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/base-ai-frontend/src/assets/i18n/base_ai/*.json` and are published with the package; a
 * consuming application copies them to `assets/i18n/base_ai`. Registered on {@link BASE_AI_ROUTES} and by
 * `provideEntityEnrollmentTab()`.
 *
 * The alias is spelled out wherever the scope is registered: transloco camel-cases the default alias, which
 * would turn `base_ai` into `baseAi` and silently miss every key below.
 */
export const BASE_AI_TRANSLOCO_SCOPE = 'base_ai';

/**
 * Scope of the generic framework labels (`base_entity.tabs.*`, `base_entity.toolbar.*`), whose files
 * base-entity owns. Registered next to {@link BASE_AI_TRANSLOCO_SCOPE} wherever the generic screens are
 * hosted, for the reason base-state gives: the tabs translate from it on first render.
 */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

/** Key root of the `Recognition Profile` entity name (`._self`) and of its attribute labels. */
export const RECOGNITION_PROFILE_I18N_SCOPE = `${BASE_AI_TRANSLOCO_SCOPE}.recognition_profile`;

/**
 * Key root of the Enrollment tab — the gallery of one subject, contributed onto *another* feature's entity
 * screens. A root of its own rather than a child of {@link RECOGNITION_PROFILE_I18N_SCOPE}: the tab is shown
 * on a `Boat`, and its labels talk about that boat's photos.
 */
export const ENTITY_ENROLLMENT_I18N_SCOPE = `${BASE_AI_TRANSLOCO_SCOPE}.entity_enrollment`;

/** Label of the Enrollment tab, resolved with `{ entity }` like every other tab label. */
export const ENTITY_ENROLLMENT_I18N_KEY = `${ENTITY_ENROLLMENT_I18N_SCOPE}.tab`;

/**
 * Where this library's bundles come from when the application ships without its assets: base-ai-backend's
 * own translations resource, `/organizations/{orgKey}/ai/translations/...`.
 */
export const BASE_AI_TRANSLATION_SOURCE: TranslationSource = {
  scopes: [BASE_AI_TRANSLOCO_SCOPE],
  serviceRootKey: 'AI_SERVICE_ROOT',
  segment: 'ai',
};
