/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/base-calendar-frontend/src/assets/i18n/base_calendar/*.json` and are published with the
 * package; a consuming application copies them to `assets/i18n/base_calendar` (see the testbed's
 * `project.json`).
 *
 * The alias is spelled out wherever the scope is registered: transloco camel-cases the default alias, which
 * would turn `base_calendar` into `baseCalendar` and silently miss every key.
 */
export const BASE_CALENDAR_TRANSLOCO_SCOPE = 'base_calendar';

/** The alias to register {@link BASE_CALENDAR_TRANSLOCO_SCOPE} with, and the prefix of every key in it. */
export const BASE_CALENDAR_TRANSLOCO_ALIAS = 'base_calendar';
