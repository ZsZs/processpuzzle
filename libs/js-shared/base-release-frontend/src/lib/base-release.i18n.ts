import type { TranslationSource } from '@processpuzzle/util';

/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/base-release-frontend/src/assets/i18n/base_release/*.json` and are published with the
 * package; a consuming application copies them to `assets/i18n/base_release` (see the testbed's
 * `project.json`).
 *
 * The alias is spelled out wherever the scope is registered: transloco camel-cases the default alias, which
 * would turn `base_release` into `baseRelease` and silently miss every key.
 */
export const BASE_RELEASE_TRANSLOCO_SCOPE = 'base_release';

/**
 * Where this library's transloco bundles come from when the application ships without its assets.
 *
 * Spread into the application's `TRANSLATION_SOURCE_REGISTRY`, like every other feature's; `segment` names the
 * `/organizations/{orgKey}/release/translations/...` resource, which base-release-backend does not serve yet.
 */
export const BASE_RELEASE_TRANSLATION_SOURCE: TranslationSource = {
  scopes: [BASE_RELEASE_TRANSLOCO_SCOPE],
  serviceRootKey: 'RELEASE_SERVICE_ROOT',
  segment: 'release',
};
