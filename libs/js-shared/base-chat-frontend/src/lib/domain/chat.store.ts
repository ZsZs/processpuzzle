import { computed, inject, Injectable, signal } from '@angular/core';
import { CHAT_IDENTITY } from './chat-identity';
import { ChatStreamEvent, ChatStreamService } from './chat-stream.service';
import { ChatService, failureKindOf } from './chat.service';
import { ChatFailureKind, ChatMessage, ChatSubjectRef, Conversation, newClientMessageId, unreadOf } from './chat.types';

/** A message the user sent that the server has not confirmed. */
export interface PendingMessage {
  clientMessageId: string;
  body: string;
  /** `failed` stays in the list until it is retried or discarded; retrying reuses the id, so it cannot post twice. */
  status: 'sending' | 'failed';
  failure?: ChatFailureKind;
  createdAt: string;
}

/** What a panel shows of one conversation. */
export interface ConversationView {
  conversation: Conversation;
  /** Confirmed messages, oldest first. */
  messages: readonly ChatMessage[];
  pending: readonly PendingMessage[];
  /** Whether older messages than the oldest loaded exist. */
  hasMore: boolean;
  loading: boolean;
}

interface History {
  messages: readonly ChatMessage[];
  pending: readonly PendingMessage[];
  hasMore: boolean;
  loading: boolean;
}

const PAGE_SIZE = 50;

const EMPTY_HISTORY: History = { messages: [], pending: [], hasMore: false, loading: false };

/**
 * Everything the client knows about chat: the conversations it has seen, the messages it has loaded of some of
 * them, and the unread count of what the user follows — kept current by the live stream.
 *
 * **Sending is optimistic.** A message shows at once as pending with a client-generated id; the server's answer, or
 * the same message arriving on the stream, replaces it (by that id, so it never shows twice). A failed message
 * stays and can be retried: the retry carries the same id, so a post that did arrive but whose answer was lost is
 * not stored a second time.
 *
 * **Reading.** A conversation a panel shows is marked read as soon as it is on screen and again for every message
 * that arrives while it is; marking read also follows it. The unread count is derived — `lastSeq − lastReadSeq` —
 * never counted, which is exact because message numbers have no gaps.
 *
 * **The stream** is shared and reference-counted: every panel or badge calls {@link connect} when it appears and
 * {@link release} when it goes, and the connection exists while at least one is there.
 */
@Injectable({ providedIn: 'root' })
export class ChatStore {
  private readonly chat = inject(ChatService);
  private readonly stream = inject(ChatStreamService);
  private readonly identity = inject(CHAT_IDENTITY);

  private readonly conversations = signal<ReadonlyMap<string, Conversation>>(new Map());
  private readonly histories = signal<ReadonlyMap<string, History>>(new Map());
  private readonly viewers = new Map<string, number>();
  private readonly marking = new Set<string>();
  private readonly markAgain = new Set<string>();
  private consumers = 0;

  /** Where the live connection stands. */
  readonly connection = this.stream.state;

  /** The conversations the user follows. */
  readonly followed = computed(() => [...this.conversations().values()].filter((conversation) => conversation.following));

  /** Unread messages across everything the user follows. */
  readonly totalUnread = computed(() => this.followed().reduce((sum, conversation) => sum + unreadOf(conversation), 0));

  /** One conversation and its loaded messages; reads signals, so call it inside a `computed`. */
  viewOf(conversationId: string): ConversationView | undefined {
    const conversation = this.conversations().get(conversationId);
    if (!conversation) return undefined;
    return { conversation, ...(this.histories().get(conversationId) ?? EMPTY_HISTORY) };
  }

  /** The unread count of one conversation; reads signals. */
  unread(conversationId: string): number {
    const conversation = this.conversations().get(conversationId);
    return conversation ? unreadOf(conversation) : 0;
  }

  // region connection
  /** A panel or badge appeared: opens the stream if it is the first. */
  connect(): void {
    this.consumers++;
    if (this.consumers > 1) return;
    void this.refreshFollowed();
    this.stream.start({
      onEvent: (event) => this.apply(event),
      resumeFrom: () => this.positions(),
      // A reconnect may have missed messages of conversations whose history is not loaded: ask for the counts again.
      onOpen: () => void this.refreshFollowed(),
    });
  }

  /** A panel or badge went: closes the stream when it was the last. */
  release(): void {
    this.consumers = Math.max(0, this.consumers - 1);
    if (this.consumers === 0) this.stream.stop();
  }

  /** Marks a conversation as on screen: it is read now, and stays read while messages arrive. */
  setViewing(conversationId: string, viewing: boolean): void {
    const count = (this.viewers.get(conversationId) ?? 0) + (viewing ? 1 : -1);
    if (count > 0) this.viewers.set(conversationId, count);
    else this.viewers.delete(conversationId);
    if (viewing) void this.markRead(conversationId);
  }
  // endregion

  // region conversations
  /** Re-reads what the user follows, with their markers. */
  async refreshFollowed(): Promise<void> {
    try {
      const followed = await this.chat.followedConversations();
      this.conversations.update((known) => {
        const next = new Map(known);
        const stillFollowed = new Set(followed.map((conversation) => conversation.id));
        for (const [id, conversation] of next) {
          if (conversation.following && !stillFollowed.has(id)) next.set(id, { ...conversation, following: false, lastReadSeq: null });
        }
        for (const conversation of followed) next.set(conversation.id, mergeConversation(next.get(conversation.id), conversation));
        return next;
      });
    } catch {
      // The counts stay as they were; the stream's reconnect asks again.
    }
  }

  /**
   * Finds the conversation of a subject and loads its newest messages.
   *
   * @returns the conversation's id, or undefined when the subject has none the user may read
   */
  async openForSubject(subject: ChatSubjectRef, definitionKey?: string): Promise<string | undefined> {
    const found = await this.chat.findConversations(subject, definitionKey);
    const conversation = found[0];
    if (!conversation) return undefined;
    this.upsert(conversation);
    await this.loadHistory(conversation.id);
    return conversation.id;
  }

  /** Opens a conversation on a subject that has none yet. */
  async startConversation(definitionKey: string, subjectId: string, title?: string): Promise<string> {
    const conversation = await this.chat.openConversation(definitionKey, subjectId, title);
    this.upsert(conversation);
    this.setHistory(conversation.id, () => EMPTY_HISTORY);
    return conversation.id;
  }

  async unfollow(conversationId: string): Promise<void> {
    await this.chat.unfollow(conversationId);
    this.patchConversation(conversationId, { following: false, lastReadSeq: null });
  }
  // endregion

  // region history
  /**
   * Loads the newest page. It replaces what was held, because older messages held from an earlier visit would
   * leave a gap between them and the page — except what arrived on the stream while the page was loading, which
   * is newer than the page and is kept.
   */
  async loadHistory(conversationId: string): Promise<void> {
    this.setHistory(conversationId, (history) => ({ ...history, loading: true }));
    try {
      const page = await this.chat.listMessages(conversationId, undefined, PAGE_SIZE);
      this.setHistory(conversationId, (history) => ({
        ...history,
        messages: replaceWithNewestPage(history.messages, page),
        hasMore: page.length >= PAGE_SIZE,
        loading: false,
      }));
    } catch (error) {
      this.setHistory(conversationId, (history) => ({ ...history, loading: false }));
      throw error;
    }
  }

  /** Loads the page before the oldest message shown. */
  async loadOlder(conversationId: string): Promise<void> {
    const history = this.histories().get(conversationId);
    const oldest = history?.messages[0];
    if (!history?.hasMore || !oldest || history.loading) return;
    this.setHistory(conversationId, (current) => ({ ...current, loading: true }));
    try {
      const page = await this.chat.listMessages(conversationId, oldest.seq, PAGE_SIZE);
      this.setHistory(conversationId, (current) => ({
        ...current,
        messages: mergeMessages(current.messages, page),
        hasMore: page.length >= PAGE_SIZE,
        loading: false,
      }));
    } catch (error) {
      this.setHistory(conversationId, (current) => ({ ...current, loading: false }));
      throw error;
    }
  }
  // endregion

  // region sending
  /** Sends a message; it shows at once as pending. Resolves when the server has answered — it never rejects. */
  async send(conversationId: string, body: string): Promise<void> {
    const text = body.trim();
    if (!text) return;
    const pending: PendingMessage = { clientMessageId: newClientMessageId(), body: text, status: 'sending', createdAt: new Date().toISOString() };
    this.setHistory(conversationId, (history) => ({ ...history, pending: [...history.pending, pending] }));
    await this.post(conversationId, pending);
  }

  /** Sends a failed message again, under the id it already has. */
  async retry(conversationId: string, clientMessageId: string): Promise<void> {
    const failed = this.histories().get(conversationId)?.pending.find((candidate) => candidate.clientMessageId === clientMessageId && candidate.status === 'failed');
    if (!failed) return;
    const sending: PendingMessage = { ...failed, status: 'sending', failure: undefined };
    this.setPending(conversationId, sending);
    await this.post(conversationId, sending);
  }

  discard(conversationId: string, clientMessageId: string): void {
    this.setHistory(conversationId, (history) => ({ ...history, pending: history.pending.filter((candidate) => candidate.clientMessageId !== clientMessageId) }));
  }

  private async post(conversationId: string, pending: PendingMessage): Promise<void> {
    try {
      const { message, created } = await this.chat.postMessage(conversationId, pending.clientMessageId, pending.body);
      const before = this.conversations().get(conversationId);
      this.receive(conversationId, message);
      if (created && before) {
        // The server moves the poster's marker when they were up to date; so does the client, without asking.
        const caughtUp = !before.following || (before.lastReadSeq ?? 0) >= message.seq - 1;
        if (caughtUp) this.patchConversation(conversationId, { following: true, lastReadSeq: message.seq });
      }
    } catch (error) {
      this.setPending(conversationId, { ...pending, status: 'failed', failure: failureKindOf(error) });
    }
  }
  // endregion

  // region reading
  /** Moves the user's read marker to the newest message they hold, and so follows the conversation. */
  async markRead(conversationId: string): Promise<void> {
    const conversation = this.conversations().get(conversationId);
    if (!conversation) return;
    const newest = Math.max(conversation.lastSeq, this.histories().get(conversationId)?.messages.at(-1)?.seq ?? 0);
    if (conversation.following && (conversation.lastReadSeq ?? 0) >= newest) return;
    if (this.marking.has(conversationId)) {
      this.markAgain.add(conversationId);
      return;
    }
    this.marking.add(conversationId);
    // At once, so that the badge does not wait for the round trip.
    this.patchConversation(conversationId, { following: true, lastReadSeq: Math.max(conversation.lastReadSeq ?? 0, newest) });
    try {
      const state = await this.chat.markRead(conversationId, newest);
      this.patchConversation(conversationId, { following: true, lastReadSeq: state.lastReadSeq });
    } catch {
      // The next message or the next reconnect tries again.
    } finally {
      this.marking.delete(conversationId);
      if (this.markAgain.delete(conversationId)) void this.markRead(conversationId);
    }
  }
  // endregion

  // region events
  private apply(event: ChatStreamEvent): void {
    switch (event.name) {
      case 'message.posted': {
        const { conversationId, message } = event.data as { conversationId: string; message: ChatMessage };
        this.receive(conversationId, message);
        break;
      }
      case 'read.updated': {
        const { conversationId, lastReadSeq, following } = event.data as { conversationId: string; lastReadSeq: number; following: boolean };
        if (!this.conversations().has(conversationId)) void this.refreshFollowed();
        else this.patchConversation(conversationId, following ? { following: true, lastReadSeq } : { following: false, lastReadSeq: null });
        break;
      }
      case 'resync': {
        const { conversationId } = event.data as { conversationId: string };
        void this.loadHistory(conversationId).catch(() => undefined);
        void this.refreshFollowed();
        break;
      }
      default:
        break; // stream.ready and whatever a newer server adds
    }
  }

  /** A confirmed message, from the stream or from the answer to a post. Idempotent: a `seq` already held is ignored. */
  private receive(conversationId: string, message: ChatMessage): void {
    const conversation = this.conversations().get(conversationId);
    if (!conversation) {
      void this.refreshFollowed(); // a conversation this client has not seen
      return;
    }
    this.patchConversation(conversationId, { lastSeq: Math.max(conversation.lastSeq, message.seq) });
    if (this.histories().has(conversationId)) {
      this.setHistory(conversationId, (history) => ({
        ...history,
        messages: mergeMessages(history.messages, [message]),
        pending: history.pending.filter((candidate) => candidate.clientMessageId !== message.clientMessageId),
      }));
    }
    if (this.viewers.has(conversationId) && message.authorId !== this.identity.userId()) void this.markRead(conversationId);
  }

  /** Per conversation whose messages are loaded, the newest `seq` held — what a reconnect resumes from. */
  private positions(): ReadonlyMap<string, number> {
    const positions = new Map<string, number>();
    for (const [id, history] of this.histories()) {
      const newest = history.messages.at(-1)?.seq ?? this.conversations().get(id)?.lastSeq;
      if (newest !== undefined) positions.set(id, newest);
    }
    return positions;
  }
  // endregion

  // region state
  private upsert(conversation: Conversation): void {
    this.conversations.update((known) => new Map(known).set(conversation.id, mergeConversation(known.get(conversation.id), conversation)));
  }

  private patchConversation(conversationId: string, patch: Partial<Conversation>): void {
    this.conversations.update((known) => {
      const current = known.get(conversationId);
      return current ? new Map(known).set(conversationId, { ...current, ...patch }) : known;
    });
  }

  private setHistory(conversationId: string, change: (history: History) => History): void {
    this.histories.update((known) => new Map(known).set(conversationId, change(known.get(conversationId) ?? EMPTY_HISTORY)));
  }

  private setPending(conversationId: string, pending: PendingMessage): void {
    this.setHistory(conversationId, (history) => ({
      ...history,
      pending: history.pending.map((candidate) => (candidate.clientMessageId === pending.clientMessageId ? pending : candidate)),
    }));
  }
  // endregion
}

/**
 * What the server says now, but never a `lastSeq` or marker older than what a live event already told the client:
 * an answer in flight can be older than the stream.
 */
function mergeConversation(known: Conversation | undefined, fresh: Conversation): Conversation {
  if (!known) return fresh;
  const lastSeq = Math.max(known.lastSeq, fresh.lastSeq);
  const lastReadSeq = known.following && fresh.following ? Math.max(known.lastReadSeq ?? 0, fresh.lastReadSeq ?? 0) : fresh.lastReadSeq;
  return { ...fresh, lastSeq, lastReadSeq };
}

/** The newest page, oldest first, plus whatever is held that is newer than it. */
function replaceWithNewestPage(held: readonly ChatMessage[], page: readonly ChatMessage[]): readonly ChatMessage[] {
  const newest = page.reduce((max, message) => Math.max(max, message.seq), 0);
  return mergeMessages(
    held.filter((message) => message.seq > newest),
    page,
  );
}

/** The union of two lists of messages by `seq`, oldest first. */
function mergeMessages(held: readonly ChatMessage[], incoming: readonly ChatMessage[]): readonly ChatMessage[] {
  const bySeq = new Map<number, ChatMessage>();
  for (const message of held) bySeq.set(message.seq, message);
  for (const message of incoming) bySeq.set(message.seq, message);
  return [...bySeq.values()].sort((a, b) => a.seq - b.seq);
}
