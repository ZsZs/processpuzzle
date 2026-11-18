import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { ChatDefinition } from '../../domain/chat-definition';
import { createChatDefinitionDescriptor } from '../../domain/chat-definition.descriptors';
import { ChatDefinitionMapper } from '../../domain/chat-definition.mapper';
import { ChatDefinitionService } from '../../domain/chat-definition.service';
import { ChatDefinitionStore } from '../../domain/chat-definition.store';
import { ChatDefinitionOptions } from './chat-definition.options';

@Injectable()
export class ChatDefinitionFacade extends BaseEntityFacade<ChatDefinition> {
  readonly entityType = ChatDefinition;

  private readonly mapperRef = inject(ChatDefinitionMapper);
  private readonly serviceRef = inject(ChatDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return ChatDefinitionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createChatDefinitionDescriptor(() => this.injector.get(ChatDefinitionOptions).entityCodes());
  }
}
