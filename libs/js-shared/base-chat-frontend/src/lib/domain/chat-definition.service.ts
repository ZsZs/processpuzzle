import { HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { definitionRead } from '@processpuzzle/util';
import { Observable } from 'rxjs';
import { ChatDefinition } from './chat-definition';
import { ChatDefinitionMapper } from './chat-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/chat/definitions`. The organization is part of the configured
 * service root (`CHAT_SERVICE_ROOT`, falling back to `BACKEND_SERVICE_ROOT`), as for every other feature.
 */
@Injectable({ providedIn: 'root' })
export class ChatDefinitionService extends BaseEntityRestService<ChatDefinition> {
  /** Reads of definitions, addressed by the host to the draft or the released revision; see `definitionRevisionInterceptor`. */
  protected override readContext(): HttpContext {
    return definitionRead();
  }

  constructor() {
    super(inject(ChatDefinitionMapper), 'CHAT_SERVICE_ROOT', 'chat/definitions');
  }

  /**
   * A definition is created by `PUT .../definitions/{key}` — the contract has no collection `POST`, because the
   * author chooses the key — so a new one is saved the way an edited one is. The key is the entity's `id`.
   */
  override add(entity: ChatDefinition): Observable<PersistedEntity<ChatDefinition>> {
    return this.update({ ...entity, id: entity.id } as PersistedEntity<ChatDefinition>);
  }
}
