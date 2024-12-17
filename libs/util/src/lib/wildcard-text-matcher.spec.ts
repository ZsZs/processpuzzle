import { wildcardTextMatcher } from './wildcard-text-matcher';

describe('wildcardTextMatcher', () => {
  it('should return true if query string matches with subject', () => {
    expect(wildcardTextMatcher('Hello World', 'Hello*')).toBe(true);
    expect(wildcardTextMatcher('Hello World', '*World')).toBe(true);
    expect(wildcardTextMatcher('Hello World', 'He..o World')).toBe(true);
    expect(wildcardTextMatcher('Hello World', 'Hello .ld')).toBe(false);
  });
});
