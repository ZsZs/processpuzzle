import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ACTIVE_ENTITY_FACADE, baseEntityRoutes, BaseEntityContainerComponent, type EmbeddedChildRoute } from '@processpuzzle/base-entity';
import { BASE_ENTITY_TRANSLOCO_SCOPE, BASE_STATE_TRANSLOCO_SCOPE } from './base-state.i18n';
import { STATE_MACHINE_DEFINITION_ENTITY_NAME } from './domain/state-machine-definition.descriptors';
import { STATE_MACHINE_STATE_ENTITY_NAME, STATE_MACHINE_TRANSITION_ENTITY_NAME, STATE_TRANSITION_ACTION_ENTITY_NAME, STATE_TRANSITION_GUARD_ENTITY_NAME } from './domain/state-entity-names';
import { StateMachineDefinitionFacade } from './feature/state-machine-definition.facade';
import { StateMachineStateFacade, StateMachineTransitionFacade, StateTransitionActionFacade, StateTransitionGuardFacade } from './feature/state-machine-embedded.facades';

/**
 * The authoring branch of the knowledge layer: the list and form of a `State Machine Definition`, and the
 * embedded levels below it.
 *
 * The path segment has to be `snakeCaseName('State Machine Definition')`, because
 * `BaseFormNavigatorSingletonStore` builds the details URL from the entity name — the same constraint
 * `BASE_APP_ROUTES` and `BASE_RULE_ROUTES` carry.
 *
 * `entityName` in `data` is not decoration: `readEmbeddedBreadcrumb` pushes a level when it meets the
 * route that *declares* the name, and takes that level's base URL from the URL accumulated so far. It has
 * to sit on the route contributing the definition's own segment — here — or every URL built on that level
 * doubles the segment.
 *
 * The generic container is mounted directly rather than through a component of this library's own: unlike
 * `AppDefinitionContainerComponent`, which exists to contribute a Publish action and a Preview tab, a
 * state machine has no screen and no action beyond List and Details. It resolves its descriptor and its
 * store from `ACTIVE_ENTITY_FACADE` — which is also what the embedded branches below use, so every level
 * of the machine is reached the same way. The diagram this feature will eventually want is an `extraTabs`
 * entry, and that is what this route grows when the time comes.
 */
export const BASE_STATE_ROUTES: Routes = [
  {
    path: 'state-machine-definition',
    title: 'ProcessPuzzle - State Machines',
    data: { icon: 'flag_circle', menuTitle: 'state.machines', entityName: STATE_MACHINE_DEFINITION_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: StateMachineDefinitionFacade }, authoringScopes()],
    children: baseEntityRoutes(embeddedDefinitionRoutes()),
  },
];

/**
 * The transloco scopes this branch needs. Both are required: a route that declares TRANSLOCO_SCOPE
 * replaces the collection it inherits rather than adding to it, and the generic tabs translate the
 * framework's own `base_entity.*` keys.
 *
 * Registered on the top-level route rather than deeper, so the list, the form and the tabs of every level
 * resolve entity and attribute labels from the same scopes. The embedded branches below need none of their
 * own: `base_state.state_machine_state.*` and its siblings are keys of the scope already registered here.
 *
 * Both aliases are spelled out, as everywhere in this workspace: transloco camel-cases the default alias,
 * so `base_state` would silently become `baseState` and miss every key below it.
 */
function authoringScopes() {
  return provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: BASE_STATE_TRANSLOCO_SCOPE, alias: BASE_STATE_TRANSLOCO_SCOPE });
}

/**
 * The machine as route branches: a state and a transition hang below the definition's details route, and
 * a guard and an action below the transition's.
 *
 * The nesting mirrors the containment of `base-state-api.yaml` exactly, and it has to: an embedded row has
 * no id of its own to be looked up by, so the URL — `state-machine-definition/order/details/
 * state-machine-transition/ship/details/state-transition-guard/sufficientBalanceGuard/details` — is what
 * addresses it, and each segment resolves against the rows of the level above it.
 *
 * A state is a leaf: states do not nest in this version of the contract (no parallel and no nested
 * states), so there is no deeper level to expand. A guard and an action are leaves for the same reason —
 * a bean reference contains nothing but its params.
 */
function embeddedDefinitionRoutes(): EmbeddedChildRoute[] {
  return [
    { entityName: STATE_MACHINE_STATE_ENTITY_NAME, facade: StateMachineStateFacade },
    { entityName: STATE_MACHINE_TRANSITION_ENTITY_NAME, facade: StateMachineTransitionFacade, children: () => [guardRoute(), actionRoute()] },
  ];
}

function guardRoute(): EmbeddedChildRoute {
  return { entityName: STATE_TRANSITION_GUARD_ENTITY_NAME, facade: StateTransitionGuardFacade };
}

function actionRoute(): EmbeddedChildRoute {
  return { entityName: STATE_TRANSITION_ACTION_ENTITY_NAME, facade: StateTransitionActionFacade };
}
