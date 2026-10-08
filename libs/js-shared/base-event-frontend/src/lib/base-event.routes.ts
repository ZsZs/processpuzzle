import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ACTIVE_ENTITY_FACADE, BaseEntityContainerComponent, baseEntityRoutes } from '@processpuzzle/base-entity';
import { BASE_ENTITY_TRANSLOCO_SCOPE, BASE_EVENT_TRANSLOCO_SCOPE } from './base-event.i18n';
import { EVENT_DEFINITION_ENTITY_NAME } from './domain/event-definition.descriptors';
import { EventDefinitionFacade } from './feature/event-definition.facade';

/**
 * The authoring branch of the event catalog: the list and form of an `Event Definition`.
 *
 * The path segment has to be `snakeCaseName('Event Definition')`, because `BaseFormNavigatorSingletonStore`
 * builds the details URL from the entity name. `entityName` sits in `data` of the route contributing that
 * segment, as on every other feature's branch.
 *
 * The generic container is mounted directly: an event definition has no screen beyond List and Details.
 * Both transloco scopes are registered here, with their aliases spelled out — transloco would otherwise
 * camel-case `base_event` into `baseEvent` and miss every key.
 */
export const BASE_EVENT_ROUTES: Routes = [
  {
    path: 'event-definition',
    title: 'ProcessPuzzle - Event Catalog',
    data: { icon: 'bolt', menuTitle: 'event.definitions', entityName: EVENT_DEFINITION_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [
      { provide: ACTIVE_ENTITY_FACADE, useExisting: EventDefinitionFacade },
      provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: BASE_EVENT_TRANSLOCO_SCOPE, alias: BASE_EVENT_TRANSLOCO_SCOPE }),
    ],
    children: baseEntityRoutes(),
  },
];
