import { afterEach, describe, expect, it, vi } from 'vitest';
import { newClientMessageId, randomFraction, unreadOf } from './chat.types';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('newClientMessageId', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('is a version 4 UUID, and a new one each time', () => {
    const ids = new Set(Array.from({ length: 50 }, () => newClientMessageId()));

    expect(ids.size).toBe(50);
    expect([...ids].every((id) => UUID_V4.test(id))).toBe(true);
  });

  it('still makes one where randomUUID does not exist — a development server reached over plain HTTP', () => {
    vi.stubGlobal('crypto', { getRandomValues: globalThis.crypto.getRandomValues.bind(globalThis.crypto) });

    const ids = new Set(Array.from({ length: 50 }, () => newClientMessageId()));

    expect(ids.size).toBe(50);
    expect([...ids].every((id) => UUID_V4.test(id))).toBe(true);
  });
});

describe('randomFraction', () => {
  it('is at least 0 and below 1', () => {
    for (let i = 0; i < 100; i++) {
      const fraction = randomFraction();
      expect(fraction).toBeGreaterThanOrEqual(0);
      expect(fraction).toBeLessThan(1);
    }
  });
});

describe('unreadOf', () => {
  const followed = { following: true, lastSeq: 5, lastReadSeq: 2 };

  it('is what came after the marker', () => {
    expect(unreadOf(followed as never)).toBe(3);
  });

  it('counts a followed conversation with no marker from the start', () => {
    expect(unreadOf({ ...followed, lastReadSeq: null } as never)).toBe(5);
  });

  it('is never negative, and nothing for a conversation that is not followed', () => {
    expect(unreadOf({ ...followed, lastReadSeq: 9 } as never)).toBe(0);
    expect(unreadOf({ ...followed, following: false } as never)).toBe(0);
  });
});
