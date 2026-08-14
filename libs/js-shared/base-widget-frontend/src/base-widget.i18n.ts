/**
 * Transloco scope of this library's **authoring** screens. The translations live in
 * `libs/js-shared/base-widget-frontend/src/assets/i18n/base_widget/*.json` and are published with the
 * package; a consuming application copies them to `assets/i18n/base_widget` (see the testbed's
 * `project.json`). The scope is registered on `BASE_WIDGET_ROUTES`, so it loads with the route rather than
 * with the application shell.
 *
 * Distinct from the library's older **`widgets`** scope (`src/assets/i18n/widgets`), which holds the
 * run-time labels of the widget components themselves — the language selector's list, the share button's
 * tooltip. Two scopes in one library, because they are read in different places and by different people:
 * `widgets` keys reach an application's end user, `base_widget` keys reach whoever designs the widget
 * catalogue. Neither is a good home for the other's keys.
 *
 * The alias is spelled out wherever this is registered: transloco camel-cases the default alias, which
 * would turn `base_widget` into `baseWidget` and silently miss every key below.
 */
export const BASE_WIDGET_TRANSLOCO_SCOPE = 'base_widget';

/**
 * Scope of the generic framework labels (`base_entity.tabs.*`, `base_entity.toolbar.*`), whose files
 * base-entity owns. It has to be registered next to {@link BASE_WIDGET_TRANSLOCO_SCOPE} wherever the
 * generic screens are hosted — a route that declares `TRANSLOCO_SCOPE` replaces the collection it would
 * otherwise inherit rather than adding to it.
 */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

/** Key root of the `Widget Definition` entity name (`._self`) and of its attribute labels. */
export const WIDGET_DEFINITION_I18N_SCOPE = `${BASE_WIDGET_TRANSLOCO_SCOPE}.widget_definition`;

/**
 * Key roots of the port rows the definition form contains through `EMBEDDED_COMPONENTS` controls. Children
 * of this library's scope rather than scopes of their own: the whole graph is edited under one route, and
 * one registration has to cover all of it — the embedded branches add none.
 */
export const WIDGET_INPUT_PORT_I18N_SCOPE = `${BASE_WIDGET_TRANSLOCO_SCOPE}.widget_input_port`;
export const WIDGET_OUTPUT_PORT_I18N_SCOPE = `${BASE_WIDGET_TRANSLOCO_SCOPE}.widget_output_port`;

/** Keys of the `Publish` form action contributed by `WidgetDefinitionContainerComponent`. */
export const PUBLISH_BUTTON_I18N_KEY = `${BASE_WIDGET_TRANSLOCO_SCOPE}.publish.button`;
export const PUBLISH_TOOLTIP_I18N_KEY = `${BASE_WIDGET_TRANSLOCO_SCOPE}.publish.tooltip`;
