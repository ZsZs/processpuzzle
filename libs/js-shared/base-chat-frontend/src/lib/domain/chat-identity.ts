import { InjectionToken, Signal } from '@angular/core';
import { ChatSubjectRef } from './chat.types';

/**
 * Who is chatting, as the host application tells it. A feature library names no identity provider — that is the
 * application's business — so the two things chat needs from it are a port, supplied where the application is
 * composed:
 *
 * - `accessToken` resolves the bearer token for the next call, refreshed if it is about to expire. The live stream
 *   is read with `fetch`, which the application's HTTP interceptors do not see, so it needs the token itself.
 * - `userId` is the user's id in the identity provider — the same value messages carry as `authorId` — so the panel
 *   can tell their messages from the others'.
 *
 * The default knows nobody: no token (the stream is refused) and no id (every message looks like somebody else's).
 */
export interface ChatIdentity {
  accessToken(): Promise<string | undefined>;
  userId(): string | undefined;
}

export const CHAT_IDENTITY = new InjectionToken<ChatIdentity>('CHAT_IDENTITY', {
  providedIn: 'root',
  factory: () => ({ accessToken: async () => undefined, userId: () => undefined }),
});

/** `fetch`, as a token so a test can stand in for the network. */
export type ChatFetch = (input: string, init?: RequestInit) => Promise<Response>;

export const CHAT_FETCH = new InjectionToken<ChatFetch>('CHAT_FETCH', {
  providedIn: 'root',
  factory: () => (input, init) => globalThis.fetch(input, init),
});

/**
 * The subject a panel placed without one falls back to — in practice the entity the user has made the active
 * working context. Optional: with no provider, a panel needs its `subjectId` input.
 */
export const CHAT_ACTIVE_SUBJECT = new InjectionToken<Signal<ChatSubjectRef | undefined>>('CHAT_ACTIVE_SUBJECT');
