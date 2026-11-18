import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CHAT_ACTIVE_SUBJECT, CHAT_FETCH, CHAT_IDENTITY } from './chat-identity';

describe('the defaults of what a host supplies', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('knows nobody: no token, and no user id', async () => {
    const identity = TestBed.inject(CHAT_IDENTITY);

    expect(await identity.accessToken()).toBeUndefined();
    expect(identity.userId()).toBeUndefined();
  });

  it('fetches with the platform fetch, whichever one is current when it is asked', async () => {
    const platformFetch = vi.fn(async () => new Response('ok'));
    vi.stubGlobal('fetch', platformFetch);

    const response = await TestBed.inject(CHAT_FETCH)('https://backend/chat/stream', { cache: 'no-store' });

    expect(await response.text()).toBe('ok');
    expect(platformFetch).toHaveBeenCalledWith('https://backend/chat/stream', { cache: 'no-store' });
  });

  it('has no active subject unless the host provides one', () => {
    expect(TestBed.inject(CHAT_ACTIVE_SUBJECT, null, { optional: true })).toBeNull();
  });
});
