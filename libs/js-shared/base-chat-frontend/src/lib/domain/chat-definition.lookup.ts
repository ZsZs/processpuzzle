import { inject, Injectable, Injector } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ChatDefinition, ChatMultiplicity } from './chat-definition';
import { ChatDefinitionService } from './chat-definition.service';

/**
 * Answers one question about a chat definition for the panel: does it hold several titled topics per record?
 *
 * The definitions are read once and remembered. A read that fails answers "no" without remembering it, so the panel
 * falls back to the single chat it has always shown and a later look may still find out.
 */
@Injectable({ providedIn: 'root' })
export class ChatDefinitionLookup {
  private readonly injector = inject(Injector);
  private readonly known = new Map<string, boolean>();

  async hasTopics(definitionKey: string): Promise<boolean> {
    const remembered = this.known.get(definitionKey);
    if (remembered !== undefined) return remembered;
    try {
      const found = await firstValueFrom(this.injector.get(ChatDefinitionService).findAll());
      for (const definition of listOf(found)) this.known.set(definition.id, definition.multiplicity === ChatMultiplicity.MANY);
    } catch {
      return false;
    }
    return this.known.get(definitionKey) ?? false;
  }
}

/** What a read answers — a page, a list or one definition — as a list. */
function listOf(found: unknown): ChatDefinition[] {
  if (Array.isArray(found)) return found as ChatDefinition[];
  const page = found as { content?: unknown };
  return Array.isArray(page.content) ? (page.content as ChatDefinition[]) : [found as ChatDefinition];
}
