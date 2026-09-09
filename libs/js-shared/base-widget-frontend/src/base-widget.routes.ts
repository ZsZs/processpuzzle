import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { baseEntityRoutes, type EmbeddedChildRoute } from '@processpuzzle/base-entity';
import { BASE_ENTITY_TRANSLOCO_SCOPE, BASE_WIDGET_TRANSLOCO_SCOPE } from './base-widget.i18n';
import { WidgetDefinitionContainerComponent } from './widget-definition/widget-definition-container.component';
import { WIDGET_DEFINITION_ENTITY_NAME, WIDGET_INPUT_PORT_ENTITY_NAME, WIDGET_OUTPUT_PORT_ENTITY_NAME } from './widget-definition/widget-entity-names';
import { WidgetInputPortFacade } from './widget-definition/widget-input-port.facade';
import { WidgetOutputPortFacade } from './widget-definition/widget-output-port.facade';

/**
 * The authoring branch of the widget catalogue: the list and form of a widget *type*, plus the two port
 * levels below its details route.
 *
 * The path segment has to be `snakeCaseName('Widget Definition')`, because `BaseFormNavigatorSingletonStore`
 * builds the details URL from the entity name — the same constraint as on `BASE_APP_ROUTES`.
 *
 * A branch to be mounted, not a section: the designer shows it as the third tab of its Application section
 * (see `@processpuzzle/design`), because a widget type is what an application's routes and regions place. The
 * hosting application still has to spread `BASE_WIDGET_FACADE_PROVIDERS` and `BASE_WIDGET_ENTITY_FACADES`
 * into its own providers — a base-entity screen resolves its entity and embedded levels through
 * `BASE_ENTITY_FACADE_REGISTRY`, and a library cannot contribute to that token without replacing it.
 */
export const BASE_WIDGET_ROUTES: Routes = [
  {
    path: 'widget-definition',
    title: 'ProcessPuzzle Design - Widgets',
    data: { icon: 'widgets', menuTitle: 'design.widgets', entityName: WIDGET_DEFINITION_ENTITY_NAME },
    component: WidgetDefinitionContainerComponent,
    // Both scopes, both aliases spelled out: a route declaring TRANSLOCO_SCOPE replaces the collection it
    // inherits rather than adding to it, so `base_entity` — which the generic tabs and toolbar translate
    // from — has to be re-listed here; and transloco camel-cases a default alias, turning `base_widget`
    // into `baseWidget`.
    providers: [provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: BASE_WIDGET_TRANSLOCO_SCOPE, alias: BASE_WIDGET_TRANSLOCO_SCOPE })],
    children: baseEntityRoutes(embeddedPortRoutes()),
  },
];

/**
 * The ports as route branches below the definition's details route. Leaves: a port contains nothing, and
 * `propsSchema` — the definition's other nested field — has no branch at all, because it is not on the form.
 */
function embeddedPortRoutes(): EmbeddedChildRoute[] {
  return [
    { entityName: WIDGET_INPUT_PORT_ENTITY_NAME, facade: WidgetInputPortFacade },
    { entityName: WIDGET_OUTPUT_PORT_ENTITY_NAME, facade: WidgetOutputPortFacade },
  ];
}
