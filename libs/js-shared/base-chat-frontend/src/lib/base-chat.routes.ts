import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ACTIVE_ENTITY_FACADE, BaseEntityContainerComponent, baseEntityRoutes } from '@processpuzzle/base-entity';
import { BASE_CHAT_TRANSLOCO_SCOPE, BASE_ENTITY_TRANSLOCO_SCOPE } from './base-chat.i18n';
import { CHAT_DEFINITION_ENTITY_NAME } from './domain/chat-entity-names';
import { ChatDefinitionFacade } from './feature/definition/chat-definition.facade';

/**
 * The authoring branch of the chat definitions: the list and form of a `Chat Definition`.
 *
 * The path segment has to be `snakeCaseName('Chat Definition')`, because `BaseFormNavigatorSingletonStore` builds
 * the details URL from the entity name; `entityName` sits in `data` of the route contributing that segment. Both
 * transloco scopes are registered here with their aliases spelled out — transloco would otherwise camel-case
 * `base_chat` into `baseChat` and miss every key.
 */
export const BASE_CHAT_ROUTES: Routes = [
  {
    path: 'chat-definition',
    title: 'ProcessPuzzle - Chat Definitions',
    data: { icon: 'forum', menuTitle: 'chat.definitions', entityName: CHAT_DEFINITION_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [
      { provide: ACTIVE_ENTITY_FACADE, useExisting: ChatDefinitionFacade },
      provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: BASE_CHAT_TRANSLOCO_SCOPE, alias: BASE_CHAT_TRANSLOCO_SCOPE }),
    ],
    children: baseEntityRoutes(),
  },
];
