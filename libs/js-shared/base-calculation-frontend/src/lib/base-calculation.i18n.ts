import type { TranslationSource } from '@processpuzzle/util';

/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/base-calculation-frontend/src/assets/i18n/base_calculation/*.json` and are published with the
 * package; a consuming application copies them to `assets/i18n/base_calculation` (see the testbed's
 * `project.json`). The scope is registered on {@link BASE_CALCULATION_ROUTES} and {@link CALCULATION_RUNS_ROUTES}.
 *
 * The alias is spelled out wherever the scope is registered: transloco camel-cases the default alias, which
 * would turn `base_calculation` into `baseCalculation` and silently miss every key.
 */
export const BASE_CALCULATION_TRANSLOCO_SCOPE = 'base_calculation';

/** Scope of the generic framework labels, registered next to this library's on the route — see base-event. */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

/** Key root of the `Calculation Definition` entity name (`._self`) and of its attribute labels. */
export const CALCULATION_DEFINITION_I18N_SCOPE = `${BASE_CALCULATION_TRANSLOCO_SCOPE}.calculation_definition`;

/** Key roots of the three extra tabs and of the runs screen. */
export const CALCULATION_SOURCE_I18N_SCOPE = `${BASE_CALCULATION_TRANSLOCO_SCOPE}.source`;
export const CALCULATION_TABLES_I18N_SCOPE = `${BASE_CALCULATION_TRANSLOCO_SCOPE}.tables`;
export const CALCULATION_EVALUATE_I18N_SCOPE = `${BASE_CALCULATION_TRANSLOCO_SCOPE}.evaluate`;
export const CALCULATION_RUNS_I18N_SCOPE = `${BASE_CALCULATION_TRANSLOCO_SCOPE}.runs`;

/**
 * Where this library's transloco bundles come from when the application ships without its assets. base-calculation
 * does not serve a translations resource yet, so a host that skips the asset copy step gets no translations.
 */
export const BASE_CALCULATION_TRANSLATION_SOURCE: TranslationSource = {
  scopes: [BASE_CALCULATION_TRANSLOCO_SCOPE],
  serviceRootKey: 'CALCULATION_SERVICE_ROOT',
  segment: 'calculation',
};
