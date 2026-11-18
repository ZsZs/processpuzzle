import { inject, Injectable, Provider } from '@angular/core';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ENTITY_TAB_CONTRIBUTORS, type BaseEntityDescriptor, EntityDefinitionRegistry, type EntityTabContributor, type EntityTabDescriptor } from '@processpuzzle/base-entity';
import { BASE_CHAT_TRANSLOCO_SCOPE, ENTITY_CHAT_I18N_KEY } from '../../base-chat.i18n';
import { ChatDefinitionLookup } from '../../domain/chat-definition.lookup';
import { EntityChatTabComponent, entityCodeOf } from './entity-chat-tab.component';

/** URL segment of an entity's Chat tab, appended to `<entity>/<id>/`. */
export const ENTITY_CHAT_TAB_SEGMENT = 'chat';

export const ENTITY_CHAT_TAB: EntityTabDescriptor = {
  segment: ENTITY_CHAT_TAB_SEGMENT,
  i18nKey: ENTITY_CHAT_I18N_KEY,
  component: EntityChatTabComponent,
};

/** Offers the Chat tab to every entity some chat definition is about, and to no other. */
@Injectable({ providedIn: 'root' })
export class EntityChatTabContributor implements EntityTabContributor {
  private readonly definitions = inject(ChatDefinitionLookup);
  private readonly entities = inject(EntityDefinitionRegistry);

  async tabsFor(descriptor: BaseEntityDescriptor): Promise<EntityTabDescriptor[]> {
    const chats = await this.definitions.forEntity(await entityCodeOf(this.entities, descriptor.entityName));
    return chats.length > 0 ? [ENTITY_CHAT_TAB] : [];
  }
}

/**
 * Registers the Chat tab for the whole application; spread into the root `providers`. The transloco scope is
 * registered at the root too, but a route that provides its own transloco scope hides that one: each route that
 * renders an entity's screens must also provide `base_chat`, as base-state's `provideEntityStateMachineTab()` describes.
 */
export function provideEntityChatTab(): Provider[] {
  return [
    { provide: ENTITY_TAB_CONTRIBUTORS, useExisting: EntityChatTabContributor, multi: true },
    provideTranslocoScope({ scope: BASE_CHAT_TRANSLOCO_SCOPE, alias: BASE_CHAT_TRANSLOCO_SCOPE }),
  ];
}
