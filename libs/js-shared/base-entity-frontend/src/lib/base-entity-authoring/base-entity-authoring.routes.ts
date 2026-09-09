import type { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { baseEntityRoutes, type EmbeddedChildRoute } from '../base-entity.routes';
import { ACTIVE_ENTITY_FACADE } from '../base-entity-facade/active-entity-facade.token';
import { ENTITY_NAME_ROUTE_DATA_KEY } from '../base-form-navigator/entity-route.registry';
import { BASE_ENTITY_TRANSLOCO_SCOPE } from '../i18n/base-entity.i18n';
import { EntityDefinitionContainerComponent } from './entity-definition-container.component';
import { ENTITY_ATTRIBUTE_ENTITY_NAME, ENTITY_DEFINITION_ENTITY_NAME } from './entity-authoring-names';
import { EntityAttributeFacade, EntityDefinitionFacade } from './entity-definition.facade';

/**
 * The authoring branch of the knowledge layer: the list and form of an `Entity Definition`, and the
 * `Entity Attribute` level below it. Mounted by the designer under `/design/entities` — see
 * `DESIGN_ROUTES`.
 *
 * These are the screens with which a tenant *declares* its entity types. Not to be confused with the
 * screens those declarations produce: those are mounted by `entityScreenRoute` / `EntityScreenResolver`,
 * which read the very rows this branch writes.
 *
 * The path segment has to be `snakeCaseName('Entity Definition')`, because
 * `BaseFormNavigatorSingletonStore` builds the details URL from the entity name — the same constraint
 * `BASE_APP_ROUTES`, `BASE_RULE_ROUTES` and `BASE_STATE_ROUTES` carry.
 *
 * `entityName` in `data` is not decoration: `readEmbeddedBreadcrumb` pushes a level when it meets the route
 * that *declares* the name, and takes that level's base URL from the URL accumulated so far. It has to sit
 * on the route contributing the definition's own segment — here — or every URL built on that level doubles
 * the segment.
 *
 * As for base-app, base-document, base-widget, base-state and base-workflow, the hosting application has to
 * spread `BASE_ENTITY_AUTHORING_FACADE_PROVIDERS` and `BASE_ENTITY_AUTHORING_ENTITY_FACADES` into its own
 * providers: both levels resolve their store and descriptor through `BASE_ENTITY_FACADE_REGISTRY`, and a
 * library cannot contribute to that token without replacing it.
 */
export const BASE_ENTITY_AUTHORING_ROUTES: Routes = [
  {
    path: 'entity-definition',
    title: 'ProcessPuzzle Design - Entity Definitions',
    data: { icon: 'checkbook', menuTitle: 'design.entities', [ENTITY_NAME_ROUTE_DATA_KEY]: ENTITY_DEFINITION_ENTITY_NAME },
    component: EntityDefinitionContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: EntityDefinitionFacade }, authoringScope()],
    children: baseEntityRoutes(embeddedDefinitionRoutes()),
  },
];

/**
 * The transloco scope this branch needs — one, unlike every other feature's authoring branch, because the
 * entity labels and the generic screen labels are keys of the same scope: this *is* base-entity, so
 * `base_entity.entity_definition.*` sits beside `base_entity.tabs.*` in one file.
 *
 * Registered on the top-level route rather than deeper, so the list, the form and the tabs of both levels
 * resolve entity and attribute labels from it. The embedded branch below needs none of its own:
 * `base_entity.entity_attribute.*` is a key of the scope already registered here.
 *
 * The alias is spelled out, as everywhere in this workspace: transloco camel-cases the default alias, so
 * `base_entity` would silently become `baseEntity` and miss every key below it.
 */
function authoringScope() {
  return provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE });
}

/**
 * The definition as a route branch: its attributes hang below its details route.
 *
 * The nesting mirrors the containment of `base-entity-api.yaml` exactly, and it has to: an embedded row has
 * no id of its own to be looked up by, so the URL —
 * `entity-definition/order/details/entity-attribute/orderNumber/details` — is what addresses it, and each
 * segment resolves against the rows of the level above.
 *
 * An attribute is a leaf. It can *name* another definition through `linkedEntityType`, but that is a
 * reference by code to a sibling aggregate with a list of its own, not containment — so it is authored from
 * the top of this branch rather than by descending into it.
 */
function embeddedDefinitionRoutes(): EmbeddedChildRoute[] {
  return [{ entityName: ENTITY_ATTRIBUTE_ENTITY_NAME, facade: EntityAttributeFacade }];
}
