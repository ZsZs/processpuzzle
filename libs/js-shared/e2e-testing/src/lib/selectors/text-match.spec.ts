import { describe, expect, it } from 'vitest';
import { escapeRegExp, exactText } from './text-match';

describe('escapeRegExp', () => {
  it('leaves a value with no metacharacters alone', () => {
    expect(escapeRegExp('Test Entity 1')).toBe('Test Entity 1');
  });

  it('escapes every metacharacter that would otherwise change the pattern', () => {
    expect(escapeRegExp('a.b*c+d?e^f$g{h}i(j)k|l[m]n\\o')).toBe(String.raw`a\.b\*c\+d\?e\^f\$g\{h\}i\(j\)k\|l\[m\]n\\o`);
  });
});

describe('exactText', () => {
  it('matches the value it was built from', () => {
    expect(exactText('Order 1').test('Order 1')).toBe(true);
  });

  it('rejects a longer value that merely contains it — the substring match this exists to prevent', () => {
    expect(exactText('Order 1').test('Order 10')).toBe(false);
  });

  it('treats metacharacters as literal text', () => {
    expect(exactText('Price (USD)').test('Price (USD)')).toBe(true);
    expect(exactText('a.c').test('abc')).toBe(false);
  });
});
