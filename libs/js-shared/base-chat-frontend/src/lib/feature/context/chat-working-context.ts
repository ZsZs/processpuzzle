import { computed, inject, Provider } from '@angular/core';
import { WorkingContextService } from '@processpuzzle/base-entity';
import { CHAT_ACTIVE_SUBJECT } from '../../domain/chat-identity';
import { ChatSubjectRef } from '../../domain/chat.types';

/**
 * Lets a `chat-panel` placed without a `subjectId` follow the entity the user has made the active working
 * context — the innermost one when contexts are nested, because that is the record the user is working on. Spread
 * it into the application's providers; without it a panel needs its own `subjectId`.
 *
 * Lives apart from the panel so that the panel, and everything in the domain layer, stays free of base-entity.
 */
export function provideChatWorkingContextSubject(): Provider {
  return {
    provide: CHAT_ACTIVE_SUBJECT,
    useFactory: () => {
      const contexts = inject(WorkingContextService);
      return computed<ChatSubjectRef | undefined>(() => {
        const innermost = contexts.active().at(-1);
        return innermost ? { subjectType: 'ENTITY', subjectId: innermost.id } : undefined;
      });
    },
  };
}
