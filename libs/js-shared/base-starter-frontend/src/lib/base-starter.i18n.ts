/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/base-starter-frontend/src/assets/i18n/base_starter/*.json` and are published with the
 * package; a consuming application copies them to `assets/i18n/base_starter`. Registered on
 * {@link BASE_STARTER_ROUTES}.
 *
 * The alias is spelled out wherever the scope is registered: transloco camel-cases the default alias, which
 * would turn `base_starter` into `baseStarter` and silently miss every key below.
 */
export const BASE_STARTER_TRANSLOCO_SCOPE = 'base_starter';

/** Key root of the import screen. */
export const STARTER_IMPORT_I18N_SCOPE = `${BASE_STARTER_TRANSLOCO_SCOPE}.import`;

/** Key root of the installed-starters table. */
export const INSTALLED_STARTERS_I18N_SCOPE = `${BASE_STARTER_TRANSLOCO_SCOPE}.installed`;
