import { Injector, Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { WorkingContextService } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import { CHAT_ACTIVE_SUBJECT } from '../../domain/chat-identity';
import { ChatSubjectRef } from '../../domain/chat.types';
import { provideChatWorkingContextSubject } from './chat-working-context';

interface ActiveContext {
  name: string;
  id: string;
}

function activeSubject(active: Signal<ActiveContext[]>): Signal<ChatSubjectRef | undefined> {
  TestBed.configureTestingModule({
    providers: [{ provide: WorkingContextService, useValue: { active } }, provideChatWorkingContextSubject()],
  });
  return TestBed.inject(Injector).get(CHAT_ACTIVE_SUBJECT);
}

describe('provideChatWorkingContextSubject', () => {
  it('is no subject while no working context is active', () => {
    expect(activeSubject(signal([]))()).toBeUndefined();
  });

  it('is the entity of the active working context', () => {
    expect(activeSubject(signal([{ name: 'order', id: 'o-1' }]))()).toEqual({ subjectType: 'ENTITY', subjectId: 'o-1' });
  });

  it('is the innermost context when they are nested — the record the user is working on', () => {
    const subject = activeSubject(signal([{ name: 'regatta', id: 'g-1' }, { name: 'race', id: 'r-3' }]));

    expect(subject()).toEqual({ subjectType: 'ENTITY', subjectId: 'r-3' });
  });

  it('follows the working context as it changes', () => {
    const active = signal<ActiveContext[]>([{ name: 'order', id: 'o-1' }]);
    const subject = activeSubject(active);

    active.set([{ name: 'order', id: 'o-2' }]);
    expect(subject()?.subjectId).toBe('o-2');

    active.set([]);
    expect(subject()).toBeUndefined();
  });
});
