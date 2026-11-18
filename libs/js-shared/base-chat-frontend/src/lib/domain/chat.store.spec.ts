import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CHAT_IDENTITY } from './chat-identity';
import { ChatStreamClient, ChatStreamService } from './chat-stream.service';
import { ChatService } from './chat.service';
import { ChatStore } from './chat.store';
import { ChatMessage, Conversation, unreadOf } from './chat.types';

const SUBJECT = { subjectType: 'ENTITY', subjectId: 'o-1' } as const;

function conversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    id: 'c-1',
    definitionKey: 'order-discussion',
    subjectType: 'ENTITY',
    subjectDefinition: 'order',
    subjectId: 'o-1',
    title: null,
    status: 'OPEN',
    lastSeq: 0,
    permissions: { canWrite: true },
    following: false,
    lastReadSeq: null,
    unread: 0,
    ...overrides,
  };
}

function message(seq: number, overrides: Partial<ChatMessage> = {}): ChatMessage {
  return { seq, clientMessageId: `m-${seq}`, authorId: 'u-other', authorName: 'Other', kind: 'TEXT', body: `message ${seq}`, createdAt: `2026-11-16T10:00:${String(seq % 60).padStart(2, '0')}Z`, ...overrides };
}

/** Newest first, as the API answers. */
const page = (from: number, to: number) => Array.from({ length: from - to + 1 }, (_, i) => message(from - i));

function setup() {
  const chat = {
    findConversations: vi.fn(async () => [] as Conversation[]),
    openConversation: vi.fn(async () => conversation({ following: true, lastReadSeq: 0 })),
    listMessages: vi.fn(async () => [] as ChatMessage[]),
    postMessage: vi.fn(async (_id: string, clientMessageId: string, body: string) => ({ message: message(1, { clientMessageId, body, authorId: 'u-me' }), created: true })),
    markRead: vi.fn(async (id: string, seq: number) => ({ conversationId: id, lastReadSeq: seq, lastSeq: seq, unread: 0 })),
    unfollow: vi.fn(async () => undefined),
    followedConversations: vi.fn(async () => [] as Conversation[]),
  };
  let client: ChatStreamClient | undefined;
  const stream = {
    state: signal('idle'),
    start: vi.fn((started: ChatStreamClient) => {
      client = started;
    }),
    stop: vi.fn(),
  };
  TestBed.configureTestingModule({
    providers: [
      { provide: ChatService, useValue: chat },
      { provide: ChatStreamService, useValue: stream },
      { provide: CHAT_IDENTITY, useValue: { accessToken: async () => 'tok', userId: () => 'u-me' } },
    ],
  });
  const store = TestBed.inject(ChatStore);
  return {
    store,
    chat,
    stream,
    emit: (name: string, data: unknown) => client?.onEvent({ name, data }),
    streamClient: () => client,
  };
}

const settle = async () => {
  for (let i = 0; i < 10; i++) await Promise.resolve();
};

/** Opens conversation c-1 with some history, as a panel does. */
async function shown(s: ReturnType<typeof setup>, overrides: Partial<Conversation> = {}, history: ChatMessage[] = []) {
  s.chat.findConversations.mockResolvedValue([conversation({ following: true, lastReadSeq: 0, ...overrides })]);
  s.chat.listMessages.mockResolvedValue(history);
  await s.store.openForSubject(SUBJECT);
}

describe('ChatStore', () => {
  let s: ReturnType<typeof setup>;

  beforeEach(() => {
    s = setup();
  });

  // region connection
  describe('the shared stream', () => {
    it('opens once for the first consumer and closes with the last', () => {
      s.store.connect();
      s.store.connect();
      expect(s.stream.start).toHaveBeenCalledTimes(1);

      s.store.release();
      expect(s.stream.stop).not.toHaveBeenCalled();
      s.store.release();
      expect(s.stream.stop).toHaveBeenCalledTimes(1);
    });

    it('can be reopened after it was closed', () => {
      s.store.connect();
      s.store.release();
      s.store.connect();

      expect(s.stream.start).toHaveBeenCalledTimes(2);
    });

    it('does not go below zero consumers', () => {
      s.store.release();
      s.store.connect();

      expect(s.stream.start).toHaveBeenCalledTimes(1);
    });

    it('reads what the user follows when it opens, and again at every reconnect', async () => {
      s.store.connect();
      await settle();
      expect(s.chat.followedConversations).toHaveBeenCalledTimes(1);

      s.streamClient()?.onOpen?.();
      await settle();

      expect(s.chat.followedConversations).toHaveBeenCalledTimes(2);
    });

    it('resumes from the newest message held per conversation, and from lastSeq for one whose messages are not loaded', async () => {
      await shown(s, { lastSeq: 5 }, page(5, 3));
      s.chat.followedConversations.mockResolvedValue([conversation({ following: true, lastReadSeq: 1, lastSeq: 8 })]);
      s.store.connect();

      expect(s.streamClient()?.resumeFrom()).toEqual(new Map([['c-1', 5]]));
    });

    it('exposes the connection state of the stream', () => {
      s.stream.state.set('reconnecting');

      expect(s.store.connection()).toBe('reconnecting');
    });
  });
  // endregion

  // region followed and unread
  describe('what the user follows', () => {
    it('totals the unread messages of the conversations they follow', async () => {
      s.chat.followedConversations.mockResolvedValue([
        conversation({ id: 'c-1', following: true, lastSeq: 7, lastReadSeq: 4 }),
        conversation({ id: 'c-2', following: true, lastSeq: 2, lastReadSeq: 2 }),
        conversation({ id: 'c-3', following: true, lastSeq: 9, lastReadSeq: null }),
      ]);

      await s.store.refreshFollowed();

      expect(s.store.followed().map((c) => c.id)).toEqual(['c-1', 'c-2', 'c-3']);
      expect(s.store.totalUnread()).toBe(3 + 0 + 9);
      expect(s.store.unread('c-1')).toBe(3);
      expect(s.store.unread('unknown')).toBe(0);
    });

    it('stops counting a conversation the server no longer lists as followed', async () => {
      s.chat.followedConversations.mockResolvedValueOnce([conversation({ following: true, lastSeq: 5, lastReadSeq: 1 })]);
      await s.store.refreshFollowed();
      expect(s.store.totalUnread()).toBe(4);

      s.chat.followedConversations.mockResolvedValueOnce([]);
      await s.store.refreshFollowed();

      expect(s.store.totalUnread()).toBe(0);
      expect(s.store.followed()).toEqual([]);
    });

    it('never takes a newer marker or message number back for an older answer that was in flight', async () => {
      await shown(s, { lastSeq: 9 }, page(9, 8));
      s.chat.followedConversations.mockResolvedValue([conversation({ following: true, lastSeq: 6, lastReadSeq: 2 })]);

      await s.store.refreshFollowed();

      expect(s.store.viewOf('c-1')?.conversation.lastSeq).toBe(9);
    });

    it('keeps the counts when the server cannot be reached', async () => {
      s.chat.followedConversations.mockResolvedValueOnce([conversation({ following: true, lastSeq: 3, lastReadSeq: 1 })]);
      await s.store.refreshFollowed();
      s.chat.followedConversations.mockRejectedValueOnce(new Error('down'));

      await s.store.refreshFollowed();

      expect(s.store.totalUnread()).toBe(2);
    });

    it('unfollows: the conversation no longer counts', async () => {
      await shown(s, { lastSeq: 3, lastReadSeq: 1 });

      await s.store.unfollow('c-1');

      expect(s.chat.unfollow).toHaveBeenCalledWith('c-1');
      expect(s.store.totalUnread()).toBe(0);
      expect(s.store.viewOf('c-1')?.conversation.following).toBe(false);
    });

    it('derives unread from the two numbers, never below zero and zero when not following', () => {
      expect(unreadOf(conversation({ following: true, lastSeq: 5, lastReadSeq: 2 }))).toBe(3);
      expect(unreadOf(conversation({ following: true, lastSeq: 2, lastReadSeq: 5 }))).toBe(0);
      expect(unreadOf(conversation({ following: false, lastSeq: 9, lastReadSeq: null }))).toBe(0);
    });
  });
  // endregion

  // region opening and history
  describe('opening a conversation', () => {
    it('lists every conversation of a subject, oldest first, without loading their messages', async () => {
      s.chat.findConversations.mockResolvedValue([
        conversation({ id: 'c-1', title: 'Protest A', following: true, lastSeq: 4, lastReadSeq: 1 }),
        conversation({ id: 'c-2', title: 'Protest B' }),
      ]);

      const ids = await s.store.listForSubject(SUBJECT, 'race-protests');

      expect(ids).toEqual(['c-1', 'c-2']);
      expect(s.chat.findConversations).toHaveBeenCalledWith(SUBJECT, 'race-protests');
      expect(s.chat.listMessages).not.toHaveBeenCalled();
      expect(s.store.viewOf('c-1')?.conversation.title).toBe('Protest A');
      expect(s.store.unread('c-1')).toBe(3);
    });

    it('answers nothing for a subject that has none the user may read', async () => {
      expect(await s.store.openForSubject(SUBJECT)).toBeUndefined();
    });

    it('loads the newest page, oldest first, and says whether there are older ones', async () => {
      s.chat.findConversations.mockResolvedValue([conversation({ following: true, lastReadSeq: 0, lastSeq: 3 })]);
      s.chat.listMessages.mockResolvedValue(page(3, 1));

      const id = await s.store.openForSubject(SUBJECT, 'order-discussion');

      expect(id).toBe('c-1');
      expect(s.chat.findConversations).toHaveBeenCalledWith(SUBJECT, 'order-discussion');
      expect(s.chat.listMessages).toHaveBeenCalledWith('c-1', undefined, 50);
      const view = s.store.viewOf('c-1');
      expect(view?.messages.map((m) => m.seq)).toEqual([1, 2, 3]);
      expect(view?.hasMore).toBe(false);
      expect(view?.loading).toBe(false);
    });

    it('knows there are older messages when a whole page came back', async () => {
      await shown(s, { lastSeq: 60 }, page(60, 11));

      expect(s.store.viewOf('c-1')?.hasMore).toBe(true);
    });

    it('prepends the page before the oldest message, by cursor', async () => {
      await shown(s, { lastSeq: 60 }, page(60, 11));
      s.chat.listMessages.mockResolvedValueOnce(page(10, 1));

      await s.store.loadOlder('c-1');

      expect(s.chat.listMessages).toHaveBeenLastCalledWith('c-1', 11, 50);
      const view = s.store.viewOf('c-1');
      expect(view?.messages[0]?.seq).toBe(1);
      expect(view?.messages).toHaveLength(60);
      expect(view?.hasMore).toBe(false);
    });

    it('asks for no older page when there is none', async () => {
      await shown(s, { lastSeq: 3 }, page(3, 1));
      s.chat.listMessages.mockClear();

      await s.store.loadOlder('c-1');

      expect(s.chat.listMessages).not.toHaveBeenCalled();
    });

    it('is not loading any more after a failed load of older messages, and lets the caller know', async () => {
      await shown(s, { lastSeq: 60 }, page(60, 11));
      s.chat.listMessages.mockRejectedValueOnce(new Error('down'));

      await expect(s.store.loadOlder('c-1')).rejects.toThrow('down');

      const view = s.store.viewOf('c-1');
      expect(view?.loading).toBe(false);
      expect(view?.hasMore).toBe(true);
      expect(view?.messages).toHaveLength(50);
    });

    it('does not load two pages of older messages at once', async () => {
      await shown(s, { lastSeq: 60 }, page(60, 11));
      s.chat.listMessages.mockClear();
      let answer!: (messages: ChatMessage[]) => void;
      s.chat.listMessages.mockImplementationOnce(() => new Promise((resolve) => (answer = resolve)));

      const first = s.store.loadOlder('c-1');
      await s.store.loadOlder('c-1');
      answer(page(10, 1));
      await first;

      expect(s.chat.listMessages).toHaveBeenCalledTimes(1);
    });

    it('is not loading any more after a failed load, and lets the caller know', async () => {
      s.chat.findConversations.mockResolvedValue([conversation({ following: true, lastReadSeq: 0 })]);
      s.chat.listMessages.mockRejectedValue(new Error('down'));

      await expect(s.store.openForSubject(SUBJECT)).rejects.toThrow('down');

      expect(s.store.viewOf('c-1')?.loading).toBe(false);
    });

    it('starts a conversation on a subject that has none', async () => {
      const id = await s.store.startConversation('order-discussion', 'o-1', 'Topic');

      expect(s.chat.openConversation).toHaveBeenCalledWith('order-discussion', 'o-1', 'Topic');
      expect(id).toBe('c-1');
      expect(s.store.viewOf('c-1')?.messages).toEqual([]);
      expect(s.store.followed().map((c) => c.id)).toEqual(['c-1']);
    });
  });
  // endregion

  // region sending
  describe('sending', () => {
    beforeEach(async () => {
      await shown(s, { lastSeq: 0, lastReadSeq: 0 });
    });

    it('shows the message at once as pending, then as the confirmed message', async () => {
      const sending = s.store.send('c-1', '  Hello  ');

      const pending = s.store.viewOf('c-1')?.pending[0];
      expect(pending).toMatchObject({ body: 'Hello', status: 'sending' });
      await sending;

      const view = s.store.viewOf('c-1');
      expect(view?.pending).toEqual([]);
      expect(view?.messages.map((m) => [m.seq, m.body])).toEqual([[1, 'Hello']]);
      expect(s.chat.postMessage).toHaveBeenCalledWith('c-1', pending?.clientMessageId, 'Hello');
    });

    it('sends nothing for a blank message', async () => {
      await s.store.send('c-1', '   ');

      expect(s.chat.postMessage).not.toHaveBeenCalled();
      expect(s.store.viewOf('c-1')?.pending).toEqual([]);
    });

    it('moves the sender along when they were up to date, as the server does', async () => {
      await s.store.send('c-1', 'Hello');

      const sent = s.store.viewOf('c-1')?.conversation;
      expect(sent?.lastSeq).toBe(1);
      expect(sent?.lastReadSeq).toBe(1);
      expect(s.store.totalUnread()).toBe(0);
    });

    it('leaves the sender behind when they had not read what came before', async () => {
      await shown(s, { lastSeq: 5, lastReadSeq: 2 });
      s.chat.postMessage.mockImplementation(async (_id, clientMessageId, body) => ({ message: message(6, { clientMessageId, body, authorId: 'u-me' }), created: true }));

      await s.store.send('c-1', 'Hello');

      expect(s.store.viewOf('c-1')?.conversation.lastReadSeq).toBe(2);
      expect(s.store.unread('c-1')).toBe(4);
    });

    it('shows the message once when it arrives on the stream before the answer to the post', async () => {
      let answer!: (posted: { message: ChatMessage; created: boolean }) => void;
      s.chat.postMessage.mockImplementationOnce(() => new Promise((resolve) => (answer = resolve)));
      s.store.connect();

      const sending = s.store.send('c-1', 'Hello');
      const clientMessageId = s.store.viewOf('c-1')?.pending[0]?.clientMessageId as string;
      const confirmed = message(1, { clientMessageId, body: 'Hello', authorId: 'u-me' });
      s.emit('message.posted', { conversationId: 'c-1', message: confirmed });
      expect(s.store.viewOf('c-1')?.pending).toEqual([]);

      answer({ message: confirmed, created: true });
      await sending;

      expect(s.store.viewOf('c-1')?.messages).toHaveLength(1);
    });

    it('keeps a failed message with the reason, and retries it under the same id', async () => {
      s.chat.postMessage.mockRejectedValueOnce(new HttpErrorResponse({ status: 409 }));

      await s.store.send('c-1', 'Hello');

      const failed = s.store.viewOf('c-1')?.pending[0];
      expect(failed).toMatchObject({ status: 'failed', failure: 'conflict' });

      await s.store.retry('c-1', failed?.clientMessageId as string);

      expect(s.chat.postMessage).toHaveBeenCalledTimes(2);
      expect(s.chat.postMessage.mock.calls[1]?.[1]).toBe(failed?.clientMessageId);
      expect(s.store.viewOf('c-1')?.pending).toEqual([]);
      expect(s.store.viewOf('c-1')?.messages).toHaveLength(1);
    });

    it('retries nothing that has not failed', async () => {
      await s.store.retry('c-1', 'unknown');

      expect(s.chat.postMessage).not.toHaveBeenCalled();
    });

    it('discards a failed message', async () => {
      s.chat.postMessage.mockRejectedValueOnce(new Error('offline'));
      await s.store.send('c-1', 'Hello');
      const failed = s.store.viewOf('c-1')?.pending[0];
      expect(failed?.failure).toBe('unavailable');

      s.store.discard('c-1', failed?.clientMessageId as string);

      expect(s.store.viewOf('c-1')?.pending).toEqual([]);
    });
  });
  // endregion

  // region reading
  describe('reading', () => {
    it('marks a conversation read when it is shown, and the badge does not wait for the answer', async () => {
      await shown(s, { lastSeq: 3, lastReadSeq: 0 }, page(3, 1));
      let answer!: (state: { conversationId: string; lastReadSeq: number; lastSeq: number; unread: number }) => void;
      s.chat.markRead.mockImplementationOnce(() => new Promise((resolve) => (answer = resolve)));

      s.store.setViewing('c-1', true);

      expect(s.store.unread('c-1')).toBe(0);
      expect(s.chat.markRead).toHaveBeenCalledWith('c-1', 3);
      answer({ conversationId: 'c-1', lastReadSeq: 3, lastSeq: 3, unread: 0 });
      await settle();
      expect(s.store.viewOf('c-1')?.conversation.lastReadSeq).toBe(3);
    });

    it('asks nothing when there is nothing unread', async () => {
      await shown(s, { lastSeq: 3, lastReadSeq: 3 }, page(3, 1));

      s.store.setViewing('c-1', true);

      expect(s.chat.markRead).not.toHaveBeenCalled();
    });

    it('follows a conversation it marks read', async () => {
      await shown(s, { following: false, lastSeq: 2, lastReadSeq: null });

      await s.store.markRead('c-1');

      expect(s.chat.markRead).toHaveBeenCalledWith('c-1', 2);
      expect(s.store.viewOf('c-1')?.conversation.following).toBe(true);
    });

    it('marks messages that arrive while it is shown, and not while it is not', async () => {
      await shown(s, { lastSeq: 2, lastReadSeq: 2 }, page(2, 1));
      s.store.connect();
      s.store.setViewing('c-1', true);

      s.emit('message.posted', { conversationId: 'c-1', message: message(3) });
      await settle();
      expect(s.chat.markRead).toHaveBeenCalledWith('c-1', 3);

      s.chat.markRead.mockClear();
      s.store.setViewing('c-1', false);
      s.emit('message.posted', { conversationId: 'c-1', message: message(4) });
      await settle();
      expect(s.chat.markRead).not.toHaveBeenCalled();
      expect(s.store.unread('c-1')).toBe(1);
    });

    it('does not mark read for a message the user wrote themselves, which the server has already counted', async () => {
      await shown(s, { lastSeq: 2, lastReadSeq: 2 }, page(2, 1));
      s.store.connect();
      s.store.setViewing('c-1', true);

      s.emit('message.posted', { conversationId: 'c-1', message: message(3, { authorId: 'u-me' }) });
      await settle();

      expect(s.chat.markRead).not.toHaveBeenCalled();
    });

    it('keeps a conversation shown by two panels viewed until both are gone', async () => {
      await shown(s, { lastSeq: 1, lastReadSeq: 1 }, page(1, 1));
      s.store.connect();
      s.store.setViewing('c-1', true);
      s.store.setViewing('c-1', true);
      s.store.setViewing('c-1', false);

      s.emit('message.posted', { conversationId: 'c-1', message: message(2) });
      await settle();

      expect(s.chat.markRead).toHaveBeenCalledWith('c-1', 2);
    });

    it('folds several marks read into one request in flight and one more afterwards', async () => {
      await shown(s, { lastSeq: 1, lastReadSeq: 0 }, page(1, 1));
      const answers: Array<() => void> = [];
      s.chat.markRead.mockImplementation((id: string, seq: number) => new Promise((resolve) => answers.push(() => resolve({ conversationId: id, lastReadSeq: seq, lastSeq: seq, unread: 0 }))));
      s.store.connect();

      s.store.setViewing('c-1', true); // the first request, which stays in flight
      s.emit('message.posted', { conversationId: 'c-1', message: message(2) });
      s.emit('message.posted', { conversationId: 'c-1', message: message(3) });
      expect(s.chat.markRead).toHaveBeenCalledTimes(1);
      answers[0]?.();
      await settle();

      expect(s.chat.markRead).toHaveBeenCalledTimes(2);
      expect(s.chat.markRead).toHaveBeenLastCalledWith('c-1', 3);
      answers[1]?.();
      await settle();
    });

    it('keeps going when marking read fails', async () => {
      await shown(s, { lastSeq: 2, lastReadSeq: 0 }, page(2, 1));
      s.chat.markRead.mockRejectedValueOnce(new Error('down'));

      await expect(s.store.markRead('c-1')).resolves.toBeUndefined();
    });
  });
  // endregion

  // region stream events
  describe('events from the stream', () => {
    beforeEach(() => s.store.connect());

    it('adds a message to a conversation whose messages are loaded, once however often it arrives', async () => {
      await shown(s, { lastSeq: 1, lastReadSeq: 1 }, page(1, 1));

      s.emit('message.posted', { conversationId: 'c-1', message: message(2) });
      s.emit('message.posted', { conversationId: 'c-1', message: message(2) });

      const view = s.store.viewOf('c-1');
      expect(view?.messages.map((m) => m.seq)).toEqual([1, 2]);
      expect(view?.conversation.lastSeq).toBe(2);
    });

    it('counts a message of a conversation whose messages are not loaded without inventing a history', async () => {
      s.chat.followedConversations.mockResolvedValue([conversation({ following: true, lastSeq: 4, lastReadSeq: 4 })]);
      await s.store.refreshFollowed();

      s.emit('message.posted', { conversationId: 'c-1', message: message(5) });

      expect(s.store.unread('c-1')).toBe(1);
      expect(s.store.viewOf('c-1')?.messages).toEqual([]);
    });

    it('reads the followed list again for a message of a conversation it has never seen', async () => {
      s.chat.followedConversations.mockClear();

      s.emit('message.posted', { conversationId: 'c-9', message: message(1) });
      await settle();

      expect(s.chat.followedConversations).toHaveBeenCalledTimes(1);
    });

    it('moves the marker when the user read the conversation in another tab', async () => {
      await shown(s, { lastSeq: 6, lastReadSeq: 1 });

      s.emit('read.updated', { conversationId: 'c-1', lastReadSeq: 6, following: true });

      expect(s.store.unread('c-1')).toBe(0);
    });

    it('stops counting a conversation the user unfollowed in another tab', async () => {
      await shown(s, { lastSeq: 6, lastReadSeq: 1 });

      s.emit('read.updated', { conversationId: 'c-1', lastReadSeq: 0, following: false });

      expect(s.store.totalUnread()).toBe(0);
      expect(s.store.followed()).toEqual([]);
    });

    it('learns of a conversation followed in another tab that it has not seen', async () => {
      s.chat.followedConversations.mockClear();

      s.emit('read.updated', { conversationId: 'c-9', lastReadSeq: 0, following: true });
      await settle();

      expect(s.chat.followedConversations).toHaveBeenCalledTimes(1);
    });

    it('reloads the newest page when the server says too much was missed', async () => {
      await shown(s, { lastSeq: 1, lastReadSeq: 1 }, page(1, 1));
      s.chat.listMessages.mockClear();
      s.chat.listMessages.mockResolvedValue(page(9, 5));

      s.emit('resync', { conversationId: 'c-1' });
      await settle();

      expect(s.chat.listMessages).toHaveBeenCalledWith('c-1', undefined, 50);
      expect(s.store.viewOf('c-1')?.messages.map((m) => m.seq)).toEqual([5, 6, 7, 8, 9]);
    });

    it('keeps no older messages across a reload, so the history has no gap', async () => {
      await shown(s, { lastSeq: 3, lastReadSeq: 3 }, page(3, 1));
      s.chat.listMessages.mockResolvedValue(page(60, 11));

      await s.store.loadHistory('c-1');

      const seqs = s.store.viewOf('c-1')?.messages.map((m) => m.seq) ?? [];
      expect(seqs[0]).toBe(11);
      expect(seqs).toHaveLength(50);
    });

    it('keeps a message that arrived on the stream while the page was loading', async () => {
      await shown(s, { lastSeq: 3, lastReadSeq: 3 }, page(3, 1));
      let answer!: (messages: ChatMessage[]) => void;
      s.chat.listMessages.mockImplementationOnce(() => new Promise((resolve) => (answer = resolve)));

      const loading = s.store.loadHistory('c-1');
      s.emit('message.posted', { conversationId: 'c-1', message: message(5) });
      answer(page(4, 1));
      await loading;

      expect(s.store.viewOf('c-1')?.messages.map((m) => m.seq)).toEqual([1, 2, 3, 4, 5]);
    });

    it('ignores events it does not know', () => {
      expect(() => s.emit('stream.ready', { heartbeatSeconds: 25 })).not.toThrow();
      expect(() => s.emit('something.new', {})).not.toThrow();
    });
  });
  // endregion
});
