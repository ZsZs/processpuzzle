import { describe, expect, it } from 'vitest';
import { pickVideoRow, storedObjectOf, VideoPlayerRow } from './video-player.model';

const row = (id: string, language: string, extra: Record<string, unknown> = {}): VideoPlayerRow => ({ id, payload: { language, ...extra } });

describe('pickVideoRow', () => {
  const rows = [row('en', 'en'), row('hu', 'hu')];

  it('picks the row in the requested language', () => {
    expect(pickVideoRow(rows, 'hu')?.id).toBe('hu');
  });

  it('falls back to English when the language is not recorded', () => {
    expect(pickVideoRow(rows, 'de')?.id).toBe('en');
  });

  it('falls back to the first row when neither the language nor English is there', () => {
    expect(pickVideoRow([row('fr', 'fr')], 'de')?.id).toBe('fr');
  });

  it('returns nothing for no rows', () => {
    expect(pickVideoRow([], 'en')).toBeUndefined();
  });
});

describe('storedObjectOf', () => {
  it('reads an uploaded object', () => {
    expect(storedObjectOf(row('x', 'en', { video: { bucket: 'b', objectId: 'o', name: 'v.mp4' } }), 'video')).toEqual({ bucket: 'b', objectId: 'o', name: 'v.mp4' });
  });

  it('is empty while nothing is uploaded', () => {
    expect(storedObjectOf(row('x', 'en', { video: null }), 'video')).toBeUndefined();
    expect(storedObjectOf(undefined, 'video')).toBeUndefined();
  });
});
