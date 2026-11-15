import { inject, Injectable, signal } from '@angular/core';
import { CHAT_FETCH, CHAT_IDENTITY } from './chat-identity';
import { ChatService } from './chat.service';
import { randomFraction } from './chat.types';
import { SseFrame, SseParser } from './sse-parser';

/** Where the live connection stands. */
export type ChatStreamState = 'idle' | 'connecting' | 'open' | 'reconnecting';

/** One event of the stream, its data already read as JSON. */
export interface ChatStreamEvent {
  name: string;
  id?: string;
  data: unknown;
}

/** Who the stream works for: it hands them events, and asks them how far they have got. */
export interface ChatStreamClient {
  onEvent(event: ChatStreamEvent): void;
  /** For each conversation the client holds messages of, the newest `seq` it has — asked at every (re)connect. */
  resumeFrom(): ReadonlyMap<string, number>;
  /** The server accepted the connection (`stream.ready`) — at the first connect and after each reconnect. */
  onOpen?(): void;
}

const BACKOFF_START_MS = 1_000;
const BACKOFF_MAX_MS = 30_000;
/** The server sends a comment every 25 s; silence for this long means the connection is dead without saying so. */
const WATCHDOG_MS = 70_000;
/** A connection that ended sooner than this was refused in all but name: back off rather than hammer the server. */
const MIN_HEALTHY_MS = 2_000;

/**
 * The live stream of `base-chat-api.yaml`, read with `fetch` because the browser's `EventSource` cannot send the
 * `Authorization` header. One stream for everything the user follows.
 *
 * **Reconnecting.** The server ends a stream at the token's expiry, so a stream that simply ends is reconnected at
 * once — with a fresh token from the {@link CHAT_IDENTITY} port, which is what re-authorizes it. A refusal or a
 * network failure is retried with exponential backoff, jittered, up to 30 s. A watchdog drops a connection that
 * has been silent for longer than the server's heartbeat allows.
 *
 * **Resuming.** Each connect says, per conversation, the newest `seq` the client holds; the server replays what
 * was missed right after `stream.ready`. A message may therefore arrive twice — the client ignores a `seq` it has.
 *
 * Not reference-counted: whoever owns the stream ({@link ChatStore}) starts and stops it.
 */
@Injectable({ providedIn: 'root' })
export class ChatStreamService {
  private readonly chat = inject(ChatService);
  private readonly identity = inject(CHAT_IDENTITY);
  private readonly fetcher = inject(CHAT_FETCH);

  readonly state = signal<ChatStreamState>('idle');

  private client: ChatStreamClient | undefined;
  private running = false;
  private attempt = 0;
  private controller: AbortController | undefined;
  private pauseTimer: ReturnType<typeof setTimeout> | undefined;
  private wakeUp: (() => void) | undefined;

  start(client: ChatStreamClient): void {
    if (this.running) return;
    this.running = true;
    this.client = client;
    this.attempt = 0;
    this.state.set('connecting');
    void this.loop();
  }

  stop(): void {
    this.running = false;
    this.controller?.abort();
    clearTimeout(this.pauseTimer);
    this.wakeUp?.();
    this.client = undefined;
    this.state.set('idle');
  }

  private async loop(): Promise<void> {
    while (this.running) {
      const startedAt = Date.now();
      const ended = await this.connectOnce();
      if (!this.running) return;
      this.state.set('reconnecting');
      const healthy = ended && Date.now() - startedAt >= MIN_HEALTHY_MS;
      if (healthy) {
        this.attempt = 0;
        continue;
      }
      this.attempt++;
      await this.pause(backoff(this.attempt));
    }
  }

  /** One connection, from the request to its end. Returns whether it ended the way a healthy one does. */
  private async connectOnce(): Promise<boolean> {
    const controller = new AbortController();
    this.controller = controller;
    const response = await this.request(controller);
    if (!response?.ok || !response.body) {
      await response?.body?.cancel().catch(() => undefined);
      return false;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    const parser = new SseParser();
    let watchdog: ReturnType<typeof setTimeout> | undefined;
    const arm = () => {
      clearTimeout(watchdog);
      watchdog = setTimeout(() => controller.abort(), WATCHDOG_MS);
    };
    try {
      arm();
      for (;;) {
        const { done, value } = await reader.read();
        if (done) return true;
        arm();
        for (const frame of parser.feed(decoder.decode(value, { stream: true }))) this.dispatch(frame);
      }
    } catch {
      return false;
    } finally {
      clearTimeout(watchdog);
      controller.abort();
    }
  }

  private async request(controller: AbortController): Promise<Response | undefined> {
    try {
      const token = await this.identity.accessToken();
      // Both types, so that a refusal — a JSON error — is not answered 406 for want of anything it could send.
      const headers: Record<string, string> = { Accept: 'text/event-stream, application/json' };
      if (token) headers['Authorization'] = `Bearer ${token}`;
      const after = this.client?.resumeFrom() ?? new Map<string, number>();
      return await this.fetcher(this.chat.streamUrl(after), { headers, signal: controller.signal, cache: 'no-store' });
    } catch {
      return undefined;
    }
  }

  private dispatch(frame: SseFrame): void {
    let data: unknown;
    try {
      data = frame.data ? JSON.parse(frame.data) : undefined;
    } catch {
      return; // not ours to understand
    }
    if (frame.event === 'stream.ready') {
      this.state.set('open');
      this.safely(() => this.client?.onOpen?.());
    }
    this.safely(() => this.client?.onEvent({ name: frame.event, id: frame.id, data }));
  }

  /** A client that throws must not take the connection down with it. */
  private safely(action: () => void): void {
    try {
      action();
    } catch (error) {
      console.error('A chat stream listener failed', error);
    }
  }

  private pause(milliseconds: number): Promise<void> {
    return new Promise((resolve) => {
      this.wakeUp = resolve;
      this.pauseTimer = setTimeout(resolve, milliseconds);
    });
  }
}

/** Exponential from one second, capped, ±20 % so that a server restart does not bring every tab back at once. */
export function backoff(attempt: number): number {
  const base = Math.min(BACKOFF_MAX_MS, BACKOFF_START_MS * 2 ** Math.max(0, attempt - 1));
  return Math.round(base * (0.8 + randomFraction() * 0.4));
}
