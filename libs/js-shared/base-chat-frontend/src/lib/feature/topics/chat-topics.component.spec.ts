import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CHAT_ACTIVE_SUBJECT, CHAT_IDENTITY } from '../../domain/chat-identity';
import { ChatStore, ConversationView } from '../../domain/chat.store';
import { ChatSubjectRef, Conversation } from '../../domain/chat.types';
import { chatTranslations } from '../../test-translations';
import { ChatPanelComponent } from '../panel/chat-panel.component';
import { ChatTopicsComponent } from './chat-topics.component';

function conversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    id: 'c-1',
    definitionKey: 'race-protests',
    subjectType: 'ENTITY',
    subjectDefinition: 'race',
    subjectId: 'r-1',
    title: 'Protest A',
    status: 'OPEN',
    lastSeq: 0,
    permissions: { canWrite: true },
    following: false,
    lastReadSeq: null,
    unread: 0,
    ...overrides,
  };
}

function view(overrides: Partial<Conversation> = {}): ConversationView {
  return { conversation: conversation(overrides), messages: [], pending: [], hasMore: false, loading: false };
}

function fakeStore() {
  const views = signal<ReadonlyMap<string, ConversationView>>(new Map());
  return {
    views,
    connection: signal('open'),
    connect: vi.fn(),
    release: vi.fn(),
    setViewing: vi.fn(),
    viewOf: (id: string) => views().get(id),
    listForSubject: vi.fn(async (_subject: ChatSubjectRef, _definitionKey?: string): Promise<string[]> => []),
    startConversation: vi.fn(async (_definitionKey: string, _subjectId: string, _title?: string): Promise<string> => 'c-new'),
    loadHistory: vi.fn(async () => undefined),
    send: vi.fn(),
    retry: vi.fn(),
    discard: vi.fn(),
    loadOlder: vi.fn(),
    openForSubject: vi.fn(),
  };
}

type FakeStore = ReturnType<typeof fakeStore>;

async function render(inputs: Record<string, unknown>, options: { store?: FakeStore; active?: ChatSubjectRef } = {}) {
  const store = options.store ?? fakeStore();
  TestBed.configureTestingModule({
    providers: [
      provideTranslocoTesting({ translations: chatTranslations() }),
      { provide: ChatStore, useValue: store },
      { provide: CHAT_IDENTITY, useValue: { accessToken: async () => undefined, userId: () => 'u-me' } },
      ...(options.active ? [{ provide: CHAT_ACTIVE_SUBJECT, useValue: signal(options.active) }] : []),
    ],
  });
  const fixture = TestBed.createComponent(ChatTopicsComponent);
  for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
  await flush(fixture);
  return { fixture, root: fixture.nativeElement as HTMLElement, store };
}

async function flush(fixture: ComponentFixture<unknown>) {
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

const byId = <T extends HTMLElement>(root: HTMLElement, id: string) => root.querySelector<T>(`[data-testid="${id}"]`);
const allById = (root: HTMLElement, id: string) => Array.from(root.querySelectorAll<HTMLElement>(`[data-testid="${id}"]`));

function required<T extends HTMLElement>(root: HTMLElement, id: string): T {
  const element = byId<T>(root, id);
  if (!element) throw new Error(`No element with data-testid '${id}'`);
  return element;
}

function storeWithTopics(): FakeStore {
  const store = fakeStore();
  store.views.set(
    new Map([
      ['c-1', view({ id: 'c-1', title: 'Protest A', following: true, lastSeq: 5, lastReadSeq: 2 })],
      ['c-2', view({ id: 'c-2', title: 'Protest B', permissions: { canWrite: false } })],
    ]),
  );
  store.listForSubject.mockResolvedValue(['c-1', 'c-2']);
  return store;
}

describe('ChatTopicsComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('asks for a record when it has none', async () => {
    const { root, store } = await render({});

    expect(byId(root, 'topics-no-subject')?.textContent).toContain('Select an item');
    expect(store.listForSubject).not.toHaveBeenCalled();
  });

  it('lists the topics of the record with their unread counts and closed marks', async () => {
    const store = storeWithTopics();
    const { root } = await render({ subjectId: 'r-1', definitionKey: 'race-protests' }, { store });

    expect(store.listForSubject).toHaveBeenCalledWith({ subjectType: 'ENTITY', subjectId: 'r-1' }, 'race-protests');
    expect(allById(root, 'topic').map((topic) => topic.textContent?.replace(/\s+/g, ' ').trim())).toEqual(['Protest A3', 'Protest Bclosed']);
    expect(byId(root, 'topic-unread')?.textContent?.trim()).toBe('3');
    expect(byId(root, 'topic-closed')).not.toBeNull();
  });

  it('lists the topics of a workflow instance when it is told the record is one', async () => {
    const store = storeWithTopics();
    await render({ subjectId: 'w-1', subjectType: 'WORKFLOW_INSTANCE' }, { store });

    expect(store.listForSubject).toHaveBeenCalledWith({ subjectType: 'WORKFLOW_INSTANCE', subjectId: 'w-1' }, undefined);
  });

  it('follows the active working context when it is given no record', async () => {
    const store = storeWithTopics();
    await render({}, { store, active: { subjectType: 'ENTITY', subjectId: 'r-9' } });

    expect(store.listForSubject).toHaveBeenCalledWith({ subjectType: 'ENTITY', subjectId: 'r-9' }, undefined);
  });

  it('says there are none when the record has no topics yet', async () => {
    const { root } = await render({ subjectId: 'r-1', definitionKey: 'race-protests' });

    expect(byId(root, 'topics-empty')).not.toBeNull();
    expect(byId(root, 'topic')).toBeNull();
  });

  it('shows the conversation of the topic that is picked, and nothing before', async () => {
    const store = storeWithTopics();
    const { fixture, root } = await render({ subjectId: 'r-1', definitionKey: 'race-protests' }, { store });
    expect(fixture.debugElement.query((debug) => debug.componentInstance instanceof ChatPanelComponent)).toBeNull();

    required(root, 'topic').click();
    await flush(fixture);

    const panel = fixture.debugElement.query((debug) => debug.componentInstance instanceof ChatPanelComponent);
    expect(panel.componentInstance.conversationId()).toBe('c-1');
    expect(required(root, 'topic').getAttribute('aria-current')).toBe('true');
  });

  describe('starting a topic', () => {
    async function typeTitle(root: HTMLElement, fixture: ComponentFixture<unknown>, title: string) {
      const input = required<HTMLInputElement>(root, 'topic-title');
      input.value = title;
      input.dispatchEvent(new Event('input'));
      await flush(fixture);
    }

    it('offers the form only for a named chat definition, and not before there is a title', async () => {
      const without = await render({ subjectId: 'r-1' });
      expect(byId(without.root, 'topic-title')).toBeNull();

      TestBed.resetTestingModule();
      const { fixture, root } = await render({ subjectId: 'r-1', definitionKey: 'race-protests' });
      expect(required<HTMLButtonElement>(root, 'topic-create').disabled).toBe(true);
      await typeTitle(root, fixture, '  ');
      expect(required<HTMLButtonElement>(root, 'topic-create').disabled).toBe(true);
      await typeTitle(root, fixture, 'Protest C');
      expect(required<HTMLButtonElement>(root, 'topic-create').disabled).toBe(false);
    });

    it('starts the topic under its title, lists it, selects it and clears the form', async () => {
      const store = storeWithTopics();
      store.startConversation.mockImplementation(async () => {
        store.views.update((known) => new Map(known).set('c-new', view({ id: 'c-new', title: 'Protest C' })));
        return 'c-new';
      });
      const { fixture, root } = await render({ subjectId: 'r-1', definitionKey: 'race-protests' }, { store });

      await typeTitle(root, fixture, ' Protest C ');
      required<HTMLButtonElement>(root, 'topic-create').click();
      await flush(fixture);

      expect(store.startConversation).toHaveBeenCalledWith('race-protests', 'r-1', 'Protest C');
      expect(allById(root, 'topic').map((topic) => topic.querySelector('.pp-topics__name')?.textContent)).toEqual(['Protest A', 'Protest B', 'Protest C']);
      expect(required<HTMLInputElement>(root, 'topic-title').value).toBe('');
      const panel = fixture.debugElement.query((debug) => debug.componentInstance instanceof ChatPanelComponent);
      expect(panel.componentInstance.conversationId()).toBe('c-new');
    });

    it.each([
      [403, 'You are not allowed to start a topic here.'],
      [500, 'The topic could not be started.'],
    ])('says so in words when the server answers %i', async (status, text) => {
      const store = fakeStore();
      store.startConversation.mockRejectedValue(new HttpErrorResponse({ status }));
      const { fixture, root } = await render({ subjectId: 'r-1', definitionKey: 'race-protests' }, { store });

      await typeTitle(root, fixture, 'Protest C');
      required<HTMLButtonElement>(root, 'topic-create').click();
      await flush(fixture);

      expect(byId(root, 'topic-create-failed')?.textContent?.trim()).toBe(text);
      expect(required<HTMLInputElement>(root, 'topic-title').value).toBe('Protest C');
    });
  });

  it('offers a retry when the topics cannot be loaded', async () => {
    const store = fakeStore();
    store.listForSubject.mockRejectedValueOnce(new Error('down')).mockResolvedValueOnce([]);
    const { fixture, root } = await render({ subjectId: 'r-1', definitionKey: 'race-protests' }, { store });
    expect(byId(root, 'topics-error')).not.toBeNull();

    required(root, 'topics-reload').click();
    await flush(fixture);

    expect(store.listForSubject).toHaveBeenCalledTimes(2);
    expect(byId(root, 'topics-empty')).not.toBeNull();
  });

  it('ignores a slow answer for a record it has moved away from', async () => {
    const store = storeWithTopics();
    const pending: Array<(ids: string[]) => void> = [];
    store.listForSubject.mockImplementationOnce(() => new Promise<string[]>((resolve) => pending.push(resolve)));
    store.listForSubject.mockResolvedValueOnce([]);
    const { fixture, root } = await render({ subjectId: 'r-1', definitionKey: 'race-protests' }, { store });

    fixture.componentRef.setInput('subjectId', 'r-2');
    await flush(fixture);
    pending[0](['c-1', 'c-2']);
    await flush(fixture);

    expect(byId(root, 'topics-empty')).not.toBeNull();
    expect(byId(root, 'topic')).toBeNull();
  });
});
