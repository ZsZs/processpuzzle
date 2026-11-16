import { inject, Injectable, Injector, runInInjectionContext } from '@angular/core';
import { ChatDefinitionService } from '@processpuzzle/base-chat';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { type BaseEntityLoadResponse, DynamicEntityService, EntityDefinitionService } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';

/** A chat definition as the sample offers it. */
export interface ChatDefinitionOption {
  key: string;
  name: string;
  subjectType: string;
  /** The entity definition code (ENTITY) or the workflow id (WORKFLOW_INSTANCE) whose records get the chat. */
  subjectDefinition: string;
}

/** One record a chat can be about: its id, and what titles it — the value of its `isLinkToDetails` attribute. */
export interface RecordOption {
  id: string;
  label?: string;
}

/** How many records the picker offers; a longer list is a reason to type, not to scroll. */
const RECORD_LIMIT = 200;

/**
 * What the chat sample offers instead of asking for ids by heart: the chat definitions there are, and for a definition
 * about entity records, those records — with the attribute that titles them next to the id.
 *
 * Every read that fails offers nothing, and the sample's fields stay free text.
 */
@Injectable({ providedIn: 'root' })
export class ChatSampleOptions {
  private readonly injector = inject(Injector);
  private readonly chatDefinitions = inject(ChatDefinitionService);
  private readonly entityDefinitions = inject(EntityDefinitionService);

  async definitions(): Promise<ChatDefinitionOption[]> {
    try {
      const found = await firstValueFrom(this.chatDefinitions.findAll());
      const list = listOf(found);
      return list
        .map((definition) => ({ key: definition.id, name: definition.name, subjectType: definition.subjectType, subjectDefinition: definition.subjectDefinition }))
        .sort((left, right) => left.name.localeCompare(right.name));
    } catch {
      return [];
    }
  }

  /** The records of the entity type, newest page first as the API gives them, each with its title attribute. */
  async records(entityDefinitionCode: string): Promise<RecordOption[]> {
    try {
      const definitions = await firstValueFrom(this.entityDefinitions.findAll());
      const titleAttribute = definitions.find((definition) => definition.code === entityDefinitionCode)?.attributes?.find((attribute) => attribute.isLinkToDetails === true)?.code;
      const service = runInInjectionContext(this.injector, () => new DynamicEntityService(entityDefinitionCode));
      const found = await firstValueFrom(service.findByQuery({ pageSize: RECORD_LIMIT }));
      const list = listOf(found);
      return list
        .filter((record) => typeof record.id === 'string')
        .map((record) => {
          const title = titleAttribute ? record[titleAttribute] : undefined;
          return { id: record.id as string, label: typeof title === 'string' || typeof title === 'number' ? titleText(title) : undefined };
        });
    } catch {
      return [];
    }
  }
}

/** A titled record's title as text; an empty one titles nothing. */
function titleText(title: string | number): string | undefined {
  const text = String(title);
  return text === '' ? undefined : text;
}

/** What a read answers — a page, a list or one record — as a list. */
function listOf<T extends object>(found: BaseEntityLoadResponse<T> | T[] | T): T[] {
  if (Array.isArray(found)) return found;
  return 'content' in found && Array.isArray(found.content) ? found.content : [found as T];
}
