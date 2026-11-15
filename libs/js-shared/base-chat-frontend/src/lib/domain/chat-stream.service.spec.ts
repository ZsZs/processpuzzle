import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CHAT_FETCH, CHAT_IDENTITY } from './chat-identity';
import { backoff, ChatStreamClient, ChatStreamEvent, ChatStreamService } from './chat-stream.service';

const ROOT = 'https://backend/api/v1/organizations/my-org';
const encoder = new TextEncoder();

/** A response whose body the test feeds by hand, like a stream that stays open. */
class FakeStream {
  private static readonly byResponse = new WeakMap<Response, FakeStream>();
  private controller!: ReadableStreamDefaultController<Uint8Array>;
  readonly response: Response;

  /** What a real fetch does: aborting the request errors the body. */
  static linkAbort(response: Response, signal: AbortSignal | null | undefined): void {
    const stream = FakeStream.byResponse.get(response);
    signal?.addEventListener('abort', () => {
      try {
        stream?.controller.error(new DOMException('aborted', 'AbortError'));
      } catch {
        // already closed
      }
    });
  }

  constructor(status = 200) {
    const body = new ReadableStream<Uint8Array>({
      start: (controller) => {
        this.controller = controller;
      },
    });
    this.response = new Response(status === 200 ? body : null, { status });
    FakeStream.byResponse.set(this.response, this);
  }

  send(text: string): void {
    this.controller.enqueue(encoder.encode(text));
  }

  end(): void {
    this.controller.close();
  }
}

interface Setup {
  service: ChatStreamService;
  fetcher: ReturnType<typeof vi.fn>;
  events: ChatStreamEvent[];
  opened: ReturnType<typeof vi.fn>;
  client: ChatStreamClient;
  resume: Map<string, number>;
}

function setup(responses: Array<() => Promise<Response>>, token: () => Promise<string | undefined> = async () => 'tok-1'): Setup {
  const queue = [...responses];
  const fetcher = vi.fn(async (_url: string, init?: RequestInit) => {
    const next = queue.shift();
    if (!next) return new Promise<Response>(() => undefined); // nothing more to connect to: stay pending
    const response = await next();
    FakeStream.linkAbort(response, init?.signal);
    return response;
  });
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT } } },
      { provide: CHAT_FETCH, useValue: fetcher },
      { provide: CHAT_IDENTITY, useValue: { accessToken: token, userId: () => 'u-1' } },
    ],
  });
  const events: ChatStreamEvent[] = [];
  const opened = vi.fn();
  const resume = new Map<string, number>();
  const client: ChatStreamClient = { onEvent: (event) => events.push(event), resumeFrom: () => resume, onOpen: opened };
  return { service: TestBed.inject(ChatStreamService), fetcher, events, opened, client, resume };
}

/** Lets pending promises and microtasks run. */
const settle = async () => {
  for (let i = 0; i < 10; i++) await Promise.resolve();
};

describe('ChatStreamService', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('connects with the bearer token and both accepted media types', async () => {
    const stream = new FakeStream();
    const { service, fetcher, client } = setup([async () => stream.response]);

    service.start(client);
    await settle();

    expect(service.state()).toBe('connecting');
    const [url, init] = fetcher.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${ROOT}/chat/stream`);
    expect(init.headers).toEqual({ Accept: 'text/event-stream, application/json', Authorization: 'Bearer tok-1' });
    expect(init.cache).toBe('no-store');
    service.stop();
  });

  it('sends no Authorization header when the host has no token', async () => {
    const { service, fetcher, client } = setup([async () => new FakeStream().response], async () => undefined);

    service.start(client);
    await settle();

    expect((fetcher.mock.calls[0]?.[1] as RequestInit).headers).toEqual({ Accept: 'text/event-stream, application/json' });
    service.stop();
  });

  it('is open once the server says it is ready, and hands every event to the client with its data read as JSON', async () => {
    const stream = new FakeStream();
    const { service, events, opened, client } = setup([async () => stream.response]);
    service.start(client);
    await settle();

    stream.send('event:stream.ready\ndata:{"expiresAt":"2026-11-16T10:15:00Z","heartbeatSeconds":25}\n\n');
    stream.send('id:c-1:4\nevent:message.posted\ndata:{"conversationId":"c-1","message":{"seq":4}}\n\n');
    await settle();

    expect(service.state()).toBe('open');
    expect(opened).toHaveBeenCalledTimes(1);
    expect(events.map((event) => event.name)).toEqual(['stream.ready', 'message.posted']);
    expect(events[1]).toEqual({ name: 'message.posted', id: 'c-1:4', data: { conversationId: 'c-1', message: { seq: 4 } } });
    service.stop();
  });

  it('reads an event that arrives split over several chunks, and ignores the heartbeat', async () => {
    const stream = new FakeStream();
    const { service, events, client } = setup([async () => stream.response]);
    service.start(client);
    await settle();

    stream.send(': keepalive\n\nevent:read.up');
    stream.send('dated\ndata:{"conversationId":"c-1",');
    stream.send('"lastReadSeq":3,"following":true}\n\n');
    await settle();

    expect(events).toEqual([{ name: 'read.updated', id: undefined, data: { conversationId: 'c-1', lastReadSeq: 3, following: true } }]);
    service.stop();
  });

  it('skips an event whose data is not JSON and keeps reading', async () => {
    const stream = new FakeStream();
    const { service, events, client } = setup([async () => stream.response]);
    service.start(client);
    await settle();

    stream.send('event:odd\ndata:not json\n\nevent:resync\ndata:{"conversationId":"c-1"}\n\n');
    await settle();

    expect(events.map((event) => event.name)).toEqual(['resync']);
    service.stop();
  });

  it('is not taken down by a client that throws', async () => {
    const stream = new FakeStream();
    const { service, client } = setup([async () => stream.response]);
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    let calls = 0;
    service.start({
      ...client,
      onEvent: () => {
        calls++;
        throw new Error('listener bug');
      },
    });
    await settle();

    stream.send('event:a\ndata:1\n\nevent:b\ndata:2\n\n');
    await settle();

    expect(calls).toBe(2);
    expect(error).toHaveBeenCalled();
    service.stop();
    error.mockRestore();
  });

  it('says, per conversation, what the client holds — asked again at every connect', async () => {
    const first = new FakeStream();
    const second = new FakeStream();
    const { service, fetcher, client, resume } = setup([async () => first.response, async () => second.response]);
    resume.set('c-1', 4);
    service.start(client);
    await settle();
    expect(fetcher.mock.calls[0]?.[0]).toBe(`${ROOT}/chat/stream?after=${encodeURIComponent('c-1:4')}`);

    resume.set('c-1', 9);
    resume.set('c-2', 2);
    first.send('event:stream.ready\ndata:{}\n\n');
    await settle();
    await vi.advanceTimersByTimeAsync(2_500);
    first.end(); // ends after a healthy stretch, as at a token's expiry
    await settle();

    expect(fetcher.mock.calls[1]?.[0]).toBe(`${ROOT}/chat/stream?after=${encodeURIComponent('c-1:9,c-2:2')}`);
    service.stop();
  });

  it('reconnects at once, with a fresh token, when the server ends a healthy stream', async () => {
    const first = new FakeStream();
    const second = new FakeStream();
    let issued = 0;
    const { service, fetcher, client } = setup([async () => first.response, async () => second.response], async () => `tok-${++issued}`);
    service.start(client);
    await settle();
    first.send('event:stream.ready\ndata:{}\n\n');
    await settle();
    await vi.advanceTimersByTimeAsync(5_000);

    first.end();
    await settle();

    expect(service.state()).toBe('reconnecting');
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect((fetcher.mock.calls[1]?.[1] as RequestInit).headers).toMatchObject({ Authorization: 'Bearer tok-2' });
    service.stop();
  });

  it('backs off after a refusal and tries again', async () => {
    const stream = new FakeStream();
    const { service, fetcher, client } = setup([async () => new FakeStream(403).response, async () => stream.response]);
    service.start(client);
    await settle();
    expect(fetcher).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(500);
    expect(fetcher).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1_000);

    expect(fetcher).toHaveBeenCalledTimes(2);
    service.stop();
  });

  it('backs off after the network fails', async () => {
    const { service, fetcher, client } = setup([
      async () => {
        throw new TypeError('network down');
      },
      async () => new FakeStream().response,
    ]);
    service.start(client);
    await settle();

    expect(service.state()).toBe('reconnecting');
    await vi.advanceTimersByTimeAsync(1_500);

    expect(fetcher).toHaveBeenCalledTimes(2);
    service.stop();
  });

  it('treats a stream that ends at once as a refusal, so it does not hammer the server', async () => {
    const instantlyDone = new FakeStream();
    const { service, fetcher, client } = setup([async () => instantlyDone.response, async () => new FakeStream().response]);
    service.start(client);
    await settle();

    instantlyDone.end();
    await settle();
    expect(fetcher).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1_500);
    expect(fetcher).toHaveBeenCalledTimes(2);
    service.stop();
  });

  it('drops a connection that has gone silent for longer than the heartbeat allows', async () => {
    const silent = new FakeStream();
    const { service, fetcher, client } = setup([async () => silent.response, async () => new FakeStream().response]);
    service.start(client);
    await settle();
    silent.send('event:stream.ready\ndata:{}\n\n');
    await settle();

    await vi.advanceTimersByTimeAsync(71_000);
    await vi.advanceTimersByTimeAsync(1_500);

    expect(fetcher).toHaveBeenCalledTimes(2);
    service.stop();
  });

  it('stops: aborts the connection, goes idle and does not reconnect', async () => {
    const stream = new FakeStream();
    const { service, fetcher, client } = setup([async () => stream.response]);
    service.start(client);
    await settle();

    service.stop();
    await vi.advanceTimersByTimeAsync(60_000);

    expect(service.state()).toBe('idle');
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect((fetcher.mock.calls[0]?.[1] as RequestInit).signal?.aborted).toBe(true);
  });

  it('ignores a second start while it is running', async () => {
    const { service, fetcher, client } = setup([async () => new FakeStream().response]);

    service.start(client);
    service.start(client);
    await settle();

    expect(fetcher).toHaveBeenCalledTimes(1);
    service.stop();
  });
});

describe('backoff', () => {
  it('doubles from one second, within ±20 %, up to thirty seconds', () => {
    for (const [attempt, base] of [[1, 1_000], [2, 2_000], [3, 4_000], [5, 16_000], [6, 30_000], [20, 30_000]]) {
      for (let i = 0; i < 20; i++) {
        const delay = backoff(attempt as number);
        expect(delay).toBeGreaterThanOrEqual(Math.floor((base as number) * 0.8));
        expect(delay).toBeLessThanOrEqual(Math.ceil((base as number) * 1.2));
      }
    }
  });
});
