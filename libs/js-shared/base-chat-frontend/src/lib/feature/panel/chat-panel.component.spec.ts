import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatDefinitionLookup } from '../../domain/chat-definition.lookup';
import { CHAT_ACTIVE_SUBJECT, CHAT_IDENTITY } from '../../domain/chat-identity';
import { ChatStore, ConversationView, PendingMessage } from '../../domain/chat.store';
import { ChatMessage, ChatSubjectRef, Conversation } from '../../domain/chat.types';
import { chatTranslations } from '../../test-translations';
import { ChatPanelComponent } from './chat-panel.component';

function conversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    id: 'c-1',
    definitionKey: 'order-discussion',
    subjectType: 'ENTITY',
    subjectDefinition: 'order',
    subjectId: 'o-1',
    title: null,
    status: 'OPEN',
    lastSeq: 2,
    permissions: { canWrite: true },
    following: true,
    lastReadSeq: 2,
    unread: 0,
    ...overrides,
  };
}

function message(seq: number, overrides: Partial<ChatMessage> = {}): ChatMessage {
  return { seq, clientMessageId: `m-${seq}`, authorId: 'u-other', authorName: 'Olga Other', kind: 'TEXT', body: `message ${seq}`, createdAt: '2026-11-16T10:30:00Z', ...overrides };
}

function view(overrides: Partial<ConversationView> = {}): ConversationView {
  return { conversation: conversation(), messages: [message(1), message(2)], pending: [], hasMore: false, loading: false, ...overrides };
}

/** A store the test steers; the panel reads its signals exactly as it reads the real one. */
function fakeStore() {
  const views = signal<ReadonlyMap<string, ConversationView>>(new Map());
  return {
    views,
    connection: signal<'idle' | 'connecting' | 'open' | 'reconnecting'>('open'),
    connect: vi.fn(),
    release: vi.fn(),
    setViewing: vi.fn(),
    viewOf: (id: string) => views().get(id),
    openForSubject: vi.fn(async (_subject: ChatSubjectRef, _definitionKey?: string): Promise<string | undefined> => undefined),
    startConversation: vi.fn(async (_definitionKey: string, _subjectId: string): Promise<string> => 'c-1'),
    send: vi.fn(async () => undefined),
    retry: vi.fn(async () => undefined),
    discard: vi.fn(),
    loadOlder: vi.fn(async () => undefined),
    loadHistory: vi.fn(async (_conversationId: string): Promise<void> => undefined),
    listForSubject: vi.fn(async (_subject: ChatSubjectRef, _definitionKey?: string): Promise<string[]> => []),
  };
}

type FakeStore = ReturnType<typeof fakeStore>;

interface Rendered {
  fixture: ComponentFixture<ChatPanelComponent>;
  root: HTMLElement;
  store: FakeStore;
  active?: ReturnType<typeof signal<ChatSubjectRef | undefined>>;
}

async function render(inputs: Record<string, unknown>, options: { store?: FakeStore; active?: ChatSubjectRef; topicDefinitions?: string[] } = {}): Promise<Rendered> {
  const store = options.store ?? fakeStore();
  const topicDefinitions = options.topicDefinitions ?? [];
  const active = options.active ? signal<ChatSubjectRef | undefined>(options.active) : undefined;
  TestBed.configureTestingModule({
    providers: [
      provideTranslocoTesting({ translations: chatTranslations() }),
      { provide: ChatStore, useValue: store },
      { provide: ChatDefinitionLookup, useValue: { hasTopics: async (key: string) => topicDefinitions.includes(key) } },
      { provide: CHAT_IDENTITY, useValue: { accessToken: async () => undefined, userId: () => 'u-me' } },
      ...(active ? [{ provide: CHAT_ACTIVE_SUBJECT, useValue: active }] : []),
    ],
  });
  const fixture = TestBed.createComponent(ChatPanelComponent);
  for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
  fixture.detectChanges();
  await fixture.whenStable();
  // The definition lookup is one more asynchronous step before the conversation is asked for.
  await new Promise((resolve) => setTimeout(resolve));
  fixture.detectChanges();
  return { fixture, root: fixture.nativeElement as HTMLElement, store, active };
}

const byId = <T extends HTMLElement>(root: HTMLElement, id: string) => root.querySelector<T>(`[data-testid="${id}"]`);

/** The element a test is about to act on; a missing one fails the test with its id rather than with a TypeError. */
function required<T extends HTMLElement>(root: HTMLElement, id: string): T {
  const element = byId<T>(root, id);
  if (!element) throw new Error(`No element with data-testid '${id}'`);
  return element;
}

async function flush(fixture: ComponentFixture<unknown>) {
  fixture.detectChanges();
  await fixture.whenStable();
  await new Promise((resolve) => setTimeout(resolve));
  fixture.detectChanges();
}

/** A store whose lookup finds `c-1` with the given view. */
function storeShowing(shown: ConversationView): FakeStore {
  const store = fakeStore();
  store.views.set(new Map([['c-1', shown]]));
  store.openForSubject.mockResolvedValue('c-1');
  return store;
}

describe('ChatPanelComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  describe('which conversation it shows', () => {
    it('asks to be told nothing yet when it has no subject', async () => {
      const { root, store } = await render({});

      expect(byId(root, 'chat-no-subject')?.textContent).toContain('Select an item');
      expect(store.openForSubject).not.toHaveBeenCalled();
    });

    it('opens the conversation of the record it is given, narrowed to its definition', async () => {
      const store = storeShowing(view());
      await render({ subjectId: 'o-1', definitionKey: 'order-discussion' }, { store });

      expect(store.openForSubject).toHaveBeenCalledWith({ subjectType: 'ENTITY', subjectId: 'o-1' }, 'order-discussion');
      expect(store.setViewing).toHaveBeenCalledWith('c-1', true);
    });

    describe('for a definition with topics', () => {
      const topicView = (id: string, title: string) => view({ conversation: conversation({ id, title, definitionKey: 'order-topics' }) });
      const topicsStore = () => {
        const store = fakeStore();
        store.views.set(
          new Map([
            ['t-1', topicView('t-1', 'Delivery')],
            ['t-2', topicView('t-2', 'Invoice')],
          ]),
        );
        store.listForSubject.mockResolvedValue(['t-1', 't-2']);
        return store;
      };

      it('lists the topics in a drop-down and shows only the newest one', async () => {
        const store = topicsStore();
        const { root } = await render({ subjectId: 'o-1', definitionKey: 'order-topics' }, { store, topicDefinitions: ['order-topics'] });

        const options = [...required<HTMLSelectElement>(root, 'topic-select').options].map((option) => option.textContent?.trim());
        expect(options).toEqual(['Delivery', 'Invoice']);
        expect(store.openForSubject).not.toHaveBeenCalled();
        expect(store.loadHistory).toHaveBeenCalledWith('t-2');
        expect(root.querySelectorAll('[data-testid="chat-log"]')).toHaveLength(1);
        expect(byId(root, 'chat-start')).toBeNull();
      });

      it('switches to the topic picked in the drop-down', async () => {
        const store = topicsStore();
        const { fixture, root } = await render({ subjectId: 'o-1', definitionKey: 'order-topics' }, { store, topicDefinitions: ['order-topics'] });
        const select = required<HTMLSelectElement>(root, 'topic-select');
        select.value = 't-1';
        select.dispatchEvent(new Event('change'));
        await flush(fixture);

        expect(store.loadHistory).toHaveBeenCalledWith('t-1');
        expect(store.setViewing).toHaveBeenCalledWith('t-1', true);
      });

      it('offers only the form to start a topic while the record has none, and starts it under its title', async () => {
        const store = fakeStore();
        store.startConversation.mockImplementation(async () => {
          store.views.set(new Map([['t-3', topicView('t-3', 'Protest')]]));
          return 't-3';
        });
        const { fixture, root } = await render({ subjectId: 'o-1', definitionKey: 'order-topics' }, { store, topicDefinitions: ['order-topics'] });
        expect(byId(root, 'topic-select')).toBeNull();
        expect(byId(root, 'topics-empty')).not.toBeNull();

        const title = required<HTMLInputElement>(root, 'topic-title');
        title.value = 'Protest';
        title.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        required<HTMLButtonElement>(root, 'topic-create').click();
        await flush(fixture);

        expect(store.startConversation).toHaveBeenCalledWith('order-topics', 'o-1', 'Protest');
        expect(byId(root, 'chat-log')).not.toBeNull();
        expect(required<HTMLSelectElement>(root, 'topic-select').options).toHaveLength(1);
      });

      it('says so when the record already has a topic of that title', async () => {
        const store = fakeStore();
        store.startConversation.mockRejectedValue(new HttpErrorResponse({ status: 409 }));
        const { fixture, root } = await render({ subjectId: 'o-1', definitionKey: 'order-topics' }, { store, topicDefinitions: ['order-topics'] });
        const title = required<HTMLInputElement>(root, 'topic-title');
        title.value = 'Delivery';
        title.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        required<HTMLButtonElement>(root, 'topic-create').click();
        await flush(fixture);

        expect(byId(root, 'topic-create-failed')?.textContent).toContain('already has a topic');
      });

      it('says so when the topic could not be started', async () => {
        const store = fakeStore();
        store.startConversation.mockRejectedValue(new Error('down'));
        const { fixture, root } = await render({ subjectId: 'o-1', definitionKey: 'order-topics' }, { store, topicDefinitions: ['order-topics'] });
        const title = required<HTMLInputElement>(root, 'topic-title');
        title.value = 'Protest';
        title.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        required<HTMLButtonElement>(root, 'topic-create').click();
        await flush(fixture);

        expect(byId(root, 'topic-create-failed')).not.toBeNull();
      });
    });

    it('shows a conversation it is given by id, loading its history and not looking for it by record', async () => {
      const store = storeShowing(view());
      const { root } = await render({ conversationId: 'c-1', subjectId: 'o-1' }, { store });

      expect(store.loadHistory).toHaveBeenCalledWith('c-1');
      expect(store.openForSubject).not.toHaveBeenCalled();
      expect(store.setViewing).toHaveBeenCalledWith('c-1', true);
      expect(byId(root, 'chat-log')).not.toBeNull();
    });

    it('says so when the conversation it is given cannot be loaded', async () => {
      const store = storeShowing(view());
      store.loadHistory.mockRejectedValue(new Error('down'));
      const { root } = await render({ conversationId: 'c-1' }, { store });

      expect(byId(root, 'chat-error')).not.toBeNull();
      expect(store.setViewing).not.toHaveBeenCalledWith('c-1', true);
    });

    it('asks for the conversation of a workflow instance when it is told the record is one', async () => {
      const store = storeShowing(view());
      await render({ subjectId: 'w-1', subjectType: 'WORKFLOW_INSTANCE' }, { store });

      expect(store.openForSubject).toHaveBeenCalledWith({ subjectType: 'WORKFLOW_INSTANCE', subjectId: 'w-1' }, undefined);
    });

    it('follows the active working context when it is given no record', async () => {
      const store = storeShowing(view());
      await render({}, { store, active: { subjectType: 'ENTITY', subjectId: 'o-7' } });

      expect(store.openForSubject).toHaveBeenCalledWith({ subjectType: 'ENTITY', subjectId: 'o-7' }, undefined);
    });

    it('prefers its own record to the working context', async () => {
      const store = storeShowing(view());
      await render({ subjectId: 'o-1' }, { store, active: { subjectType: 'ENTITY', subjectId: 'o-7' } });

      expect(store.openForSubject).toHaveBeenCalledWith({ subjectType: 'ENTITY', subjectId: 'o-1' }, undefined);
    });

    it('changes conversation when the working context changes, and stops viewing the one it leaves', async () => {
      const store = storeShowing(view());
      const { fixture, active } = await render({}, { store, active: { subjectType: 'ENTITY', subjectId: 'o-1' } });
      store.openForSubject.mockResolvedValue(undefined);

      active?.set({ subjectType: 'ENTITY', subjectId: 'o-2' });
      await flush(fixture);

      expect(store.setViewing).toHaveBeenCalledWith('c-1', false);
      expect(byId(fixture.nativeElement, 'chat-no-chat')).toBeTruthy();
    });

    it('goes back to the hint when the working context is cleared', async () => {
      const store = storeShowing(view());
      const { fixture, root, active } = await render({}, { store, active: { subjectType: 'ENTITY', subjectId: 'o-1' } });

      active?.set(undefined);
      await flush(fixture);

      expect(byId(root, 'chat-no-subject')).toBeTruthy();
      expect(byId(root, 'chat-log')).toBeNull();
    });

    it('ignores the answer for a record it has already left', async () => {
      const store = fakeStore();
      let answerFirst!: (id: string | undefined) => void;
      store.openForSubject.mockImplementationOnce(() => new Promise((resolve) => (answerFirst = resolve)));
      store.openForSubject.mockResolvedValueOnce(undefined);
      store.views.set(new Map([['c-1', view()]]));
      const { fixture, root } = await render({ subjectId: 'o-1' }, { store });
      expect(byId(root, 'chat-loading')).toBeTruthy();

      fixture.componentRef.setInput('subjectId', 'o-2');
      await flush(fixture);
      answerFirst('c-1');
      await flush(fixture);

      expect(byId(root, 'chat-log')).toBeNull();
      expect(byId(root, 'chat-no-chat')).toBeTruthy();
    });
  });

  describe('showing the conversation', () => {
    it('lists the messages with their author and time, oldest first', async () => {
      const { root } = await render({ subjectId: 'o-1' }, { store: storeShowing(view({ messages: [message(1, { body: 'First' }), message(2, { body: 'Second', authorName: null, authorId: 'u-anon' })] })) });

      const items = [...root.querySelectorAll('[data-testid="chat-message"]')];
      expect(items.map((item) => item.querySelector('.pp-chat__body')?.textContent)).toEqual(['First', 'Second']);
      expect(items[0]?.querySelector('.pp-chat__author')?.textContent).toBe('Olga Other');
      expect(items[1]?.querySelector('.pp-chat__author')?.textContent).toBe('u-anon');
      expect(items[0]?.querySelector('time')?.getAttribute('datetime')).toBe('2026-11-16T10:30:00Z');
      expect(root.querySelector('[data-testid="chat-log"]')?.getAttribute('role')).toBe('log');
    });

    it('marks the user’s own messages', async () => {
      const { root } = await render({ subjectId: 'o-1' }, { store: storeShowing(view({ messages: [message(1, { authorId: 'u-me' }), message(2)] })) });

      const items = [...root.querySelectorAll('[data-testid="chat-message"]')];
      expect(items.map((item) => item.classList.contains('pp-chat__message--own'))).toEqual([true, false]);
    });

    it('shows a message as text, never as markup', async () => {
      const { root } = await render({ subjectId: 'o-1' }, { store: storeShowing(view({ messages: [message(1, { body: '<img src=x onerror=alert(1)><b>bold</b>' })] })) });

      const body = root.querySelector('[data-testid="chat-message"] .pp-chat__body');
      expect(body?.textContent).toBe('<img src=x onerror=alert(1)><b>bold</b>');
      expect(body?.querySelector('img, b')).toBeNull();
    });

    it('shows the title of a topic', async () => {
      const { root } = await render({ subjectId: 'o-1' }, { store: storeShowing(view({ conversation: conversation({ title: 'Protest of race 3' }) })) });

      expect(byId(root, 'chat-title')?.textContent).toBe('Protest of race 3');
    });

    it('says so when there is nothing yet', async () => {
      const { root } = await render({ subjectId: 'o-1' }, { store: storeShowing(view({ messages: [] })) });

      expect(byId(root, 'chat-empty')?.textContent).toContain('No messages yet');
    });

    it('offers earlier messages when there are some, and asks for them', async () => {
      const store = storeShowing(view({ hasMore: true }));
      const { root } = await render({ subjectId: 'o-1' }, { store });

      required<HTMLButtonElement>(root, 'chat-load-older').click();

      expect(store.loadOlder).toHaveBeenCalledWith('c-1');
    });

    it('offers no earlier messages when there are none', async () => {
      const { root } = await render({ subjectId: 'o-1' }, { store: storeShowing(view()) });

      expect(byId(root, 'chat-load-older')).toBeNull();
    });

    it('warns that new messages may be late while the connection is being restored', async () => {
      const store = storeShowing(view());
      const { fixture, root } = await render({ subjectId: 'o-1' }, { store });
      expect(byId(root, 'chat-reconnecting')).toBeNull();

      store.connection.set('reconnecting');
      await flush(fixture);

      expect(byId(root, 'chat-reconnecting')?.textContent).toContain('Reconnecting');
    });

    it('updates as the store does', async () => {
      const store = storeShowing(view());
      const { fixture, root } = await render({ subjectId: 'o-1' }, { store });

      store.views.set(new Map([['c-1', view({ messages: [message(1), message(2), message(3, { body: 'Live' })] })]]));
      await flush(fixture);

      expect(root.querySelectorAll('[data-testid="chat-message"]')).toHaveLength(3);
    });
  });

  describe('when there is no conversation yet', () => {
    it('offers to start one when it knows the definition', async () => {
      const store = fakeStore();
      store.startConversation.mockImplementation(async () => {
        store.views.set(new Map([['c-1', view({ messages: [] })]]));
        return 'c-1';
      });
      const { fixture, root } = await render({ subjectId: 'o-1', definitionKey: 'order-discussion' }, { store });

      required<HTMLButtonElement>(root, 'chat-start').click();
      await flush(fixture);

      expect(store.startConversation).toHaveBeenCalledWith('order-discussion', 'o-1');
      expect(store.setViewing).toHaveBeenCalledWith('c-1', true);
      expect(byId(root, 'chat-log')).toBeTruthy();
      expect(byId(root, 'chat-start')).toBeNull();
    });

    it('says why when the user may not start one', async () => {
      const store = fakeStore();
      store.startConversation.mockRejectedValue(new HttpErrorResponse({ status: 403 }));
      const { fixture, root } = await render({ subjectId: 'o-1', definitionKey: 'order-discussion' }, { store });

      required<HTMLButtonElement>(root, 'chat-start').click();
      await flush(fixture);

      expect(byId(root, 'chat-start-failed')?.textContent).toContain('not allowed');
      expect(byId(root, 'chat-start')).toBeTruthy();
    });

    it('says that it failed when it failed for another reason', async () => {
      const store = fakeStore();
      store.startConversation.mockRejectedValue(new Error('down'));
      const { fixture, root } = await render({ subjectId: 'o-1', definitionKey: 'order-discussion' }, { store });

      required<HTMLButtonElement>(root, 'chat-start').click();
      await flush(fixture);

      expect(byId(root, 'chat-start-failed')?.textContent).toContain('could not be started');
    });

    it('only says there is no chat when it does not know which one to start', async () => {
      const { root } = await render({ subjectId: 'o-1' });

      expect(byId(root, 'chat-no-chat')).toBeTruthy();
      expect(byId(root, 'chat-start')).toBeNull();
    });
  });

  describe('when loading fails', () => {
    it('says so and tries again on request', async () => {
      const store = fakeStore();
      store.openForSubject.mockRejectedValueOnce(new Error('down'));
      store.views.set(new Map([['c-1', view()]]));
      const { fixture, root } = await render({ subjectId: 'o-1' }, { store });
      expect(byId(root, 'chat-error')).toBeTruthy();

      store.openForSubject.mockResolvedValue('c-1');
      required<HTMLButtonElement>(root, 'chat-reload').click();
      await flush(fixture);

      expect(byId(root, 'chat-error')).toBeNull();
      expect(byId(root, 'chat-log')).toBeTruthy();
    });
  });

  describe('writing', () => {
    function typeInto(root: HTMLElement, text: string) {
      const input = required<HTMLTextAreaElement>(root, 'chat-input');
      input.value = text;
      input.dispatchEvent(new Event('input'));
    }

    it('sends the draft when the button is pressed, and empties the box', async () => {
      const store = storeShowing(view());
      const { fixture, root } = await render({ subjectId: 'o-1' }, { store });

      typeInto(root, '  Hello there ');
      await flush(fixture);
      required<HTMLButtonElement>(root, 'chat-send').click();
      await flush(fixture);

      expect(store.send).toHaveBeenCalledWith('c-1', 'Hello there');
      expect(required<HTMLTextAreaElement>(root, 'chat-input').value).toBe('');
    });

    it('cannot send an empty draft', async () => {
      const { fixture, root } = await render({ subjectId: 'o-1' }, { store: storeShowing(view()) });
      expect(required<HTMLButtonElement>(root, 'chat-send').disabled).toBe(true);

      typeInto(root, '   ');
      await flush(fixture);
      expect(required<HTMLButtonElement>(root, 'chat-send').disabled).toBe(true);

      typeInto(root, 'x');
      await flush(fixture);
      expect(required<HTMLButtonElement>(root, 'chat-send').disabled).toBe(false);
    });

    it('sends on Enter and breaks the line on Shift+Enter', async () => {
      const store = storeShowing(view());
      const { fixture, root } = await render({ subjectId: 'o-1' }, { store });
      const input = required<HTMLTextAreaElement>(root, 'chat-input');

      typeInto(root, 'Line one');
      await flush(fixture);
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, cancelable: true }));
      expect(store.send).not.toHaveBeenCalled();

      const enter = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
      input.dispatchEvent(enter);

      expect(enter.defaultPrevented).toBe(true);
      expect(store.send).toHaveBeenCalledWith('c-1', 'Line one');
    });

    it('has no message box for a conversation the user may read but not write', async () => {
      const { root } = await render({ subjectId: 'o-1' }, { store: storeShowing(view({ conversation: conversation({ permissions: { canWrite: false } }) })) });

      expect(byId(root, 'chat-input')).toBeNull();
      expect(byId(root, 'chat-read-only')?.textContent).toContain('read this conversation');
    });

    it('says a message was refused for posting too fast, and keeps it for a retry', async () => {
      const failed: PendingMessage = { clientMessageId: 'p-3', body: 'Too eager', status: 'failed', failure: 'rate-limited', createdAt: '2026-11-16T10:33:00Z' };
      const store = storeShowing(view({ pending: [failed] }));
      const { root } = await render({ subjectId: 'o-1' }, { store });

      expect(byId(root, 'chat-failed')?.textContent).toContain('posting too fast');
      expect(byId(root, 'chat-retry')).not.toBeNull();
    });

    it('shows a message being sent, and a failed one with a retry and a discard', async () => {
      const sending: PendingMessage = { clientMessageId: 'p-1', body: 'On its way', status: 'sending', createdAt: '2026-11-16T10:31:00Z' };
      const failed: PendingMessage = { clientMessageId: 'p-2', body: 'Lost', status: 'failed', failure: 'conflict', createdAt: '2026-11-16T10:32:00Z' };
      const store = storeShowing(view({ pending: [sending, failed] }));
      const { root } = await render({ subjectId: 'o-1' }, { store });

      const items = [...root.querySelectorAll('[data-testid="chat-pending"]')];
      expect(items).toHaveLength(2);
      expect(items[0]?.textContent).toContain('Sending');
      expect(items[1]?.querySelector('[data-testid="chat-failed"]')?.textContent).toContain('conversation is closed');

      required<HTMLButtonElement>(root, 'chat-retry').click();
      expect(store.retry).toHaveBeenCalledWith('c-1', 'p-2');
      required<HTMLButtonElement>(root, 'chat-discard').click();
      expect(store.discard).toHaveBeenCalledWith('c-1', 'p-2');
    });

    it('tells a refusal from a failure in the reason it gives', async () => {
      const refused: PendingMessage = { clientMessageId: 'p-1', body: 'No', status: 'failed', failure: 'forbidden', createdAt: '2026-11-16T10:31:00Z' };
      const broken: PendingMessage = { clientMessageId: 'p-2', body: 'Oops', status: 'failed', failure: 'unavailable', createdAt: '2026-11-16T10:32:00Z' };
      const { root } = await render({ subjectId: 'o-1' }, { store: storeShowing(view({ pending: [refused, broken] })) });

      const reasons = [...root.querySelectorAll('[data-testid="chat-failed"]')].map((reason) => reason.textContent?.trim());
      expect(reasons).toEqual(['Not sent: you may not post here.', 'Not sent.']);
    });
  });

  describe('its life', () => {
    it('keeps the live stream open while it is there and lets go when it is gone', async () => {
      const store = storeShowing(view());
      const { fixture } = await render({ subjectId: 'o-1' }, { store });
      expect(store.connect).toHaveBeenCalledTimes(1);

      fixture.destroy();

      expect(store.setViewing).toHaveBeenLastCalledWith('c-1', false);
      expect(store.release).toHaveBeenCalledTimes(1);
    });

    it('scrolls the log to its end for a new message when the reader was at the end', async () => {
      const store = storeShowing(view());
      const { fixture, root } = await render({ subjectId: 'o-1' }, { store });
      const log = required<HTMLElement>(root, 'chat-log');
      Object.defineProperty(log, 'scrollHeight', { value: 500, configurable: true });
      log.scrollTop = 0;

      store.views.set(new Map([['c-1', view({ messages: [message(1), message(2), message(3)] })]]));
      await flush(fixture);
      await Promise.resolve();

      expect(log.scrollTop).toBe(500);
    });

    it('leaves the log where it is when the reader had scrolled up', async () => {
      const store = storeShowing(view());
      const { fixture, root } = await render({ subjectId: 'o-1' }, { store });
      const log = required<HTMLElement>(root, 'chat-log');
      Object.defineProperty(log, 'scrollHeight', { value: 1000, configurable: true });
      Object.defineProperty(log, 'clientHeight', { value: 100, configurable: true });
      log.scrollTop = 0;
      log.dispatchEvent(new Event('scroll'));

      store.views.set(new Map([['c-1', view({ messages: [message(1), message(2), message(3)] })]]));
      await flush(fixture);
      await Promise.resolve();

      expect(log.scrollTop).toBe(0);
    });
  });
});
