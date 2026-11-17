import { inject, Injectable, Injector } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ChatDefinition, ChatMultiplicity, ChatSubjectKind } from './chat-definition';
import { ChatDefinitionService } from './chat-definition.service';

/**
 * Answers questions about the chat definitions that screens need: does a definition hold several titled topics
 * per record, and which chats does an entity type have?
 *
 * The definitions are read once and remembered. A read that fails answers "none" without remembering it, so a
 * screen falls back to what it showed before there was a lookup and a later look may still find out.
 */
@Injectable({ providedIn: 'root' })
export class ChatDefinitionLookup {
  private readonly injector = inject(Injector);
  private known?: ChatDefinition[];

  async hasTopics(definitionKey: string): Promise<boolean> {
    const definition = (await this.all()).find((candidate) => candidate.id === definitionKey);
    return definition?.multiplicity === ChatMultiplicity.MANY;
  }

  /** The chats whose records are of the given entity definition code, by name. */
  async forEntity(entityDefinitionCode: string | undefined): Promise<ChatDefinition[]> {
    if (!entityDefinitionCode) return [];
    return (await this.all())
      .filter((definition) => definition.subjectType === ChatSubjectKind.ENTITY && definition.subjectDefinition === entityDefinitionCode)
      .sort((left, right) => left.name.localeCompare(right.name));
  }

  private async all(): Promise<ChatDefinition[]> {
    if (this.known) return this.known;
    try {
      const found = await firstValueFrom(this.injector.get(ChatDefinitionService).findAll());
      this.known = listOf(found);
      return this.known;
    } catch {
      return [];
    }
  }
}

/** What a read answers — a page, a list or one definition — as a list. */
function listOf(found: unknown): ChatDefinition[] {
  if (Array.isArray(found)) return found as ChatDefinition[];
  const page = found as { content?: unknown };
  return Array.isArray(page.content) ? (page.content as ChatDefinition[]) : [found as ChatDefinition];
}
