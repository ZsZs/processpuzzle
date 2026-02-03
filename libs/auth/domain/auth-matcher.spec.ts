import { UrlSegment } from '@angular/router';
import { authMatcher } from './auth-matcher';

describe('authMatcher', () => {
  it('should return consumed segments up to and including "auth" when "auth" is present', () => {
    const segments = [new UrlSegment('some', {}), new UrlSegment('path', {}), new UrlSegment('auth', {}), new UrlSegment('extra', {})];

    const result = authMatcher(segments);

    expect(result).not.toBeNull();
    expect(result?.consumed).toHaveLength(3);
    expect(result?.consumed[0].path).toBe('some');
    expect(result?.consumed[1].path).toBe('path');
    expect(result?.consumed[2].path).toBe('auth');
    expect(result?.posParams).toEqual({});
  });

  it('should return only "auth" segment if it is the first one', () => {
    const segments = [new UrlSegment('auth', {}), new UrlSegment('login', {})];

    const result = authMatcher(segments);

    expect(result).not.toBeNull();
    expect(result?.consumed).toHaveLength(1);
    expect(result?.consumed[0].path).toBe('auth');
    expect(result?.posParams).toEqual({});
  });

  it('should return segments up to "auth" if it is the last one', () => {
    const segments = [new UrlSegment('admin', {}), new UrlSegment('auth', {})];

    const result = authMatcher(segments);

    expect(result).not.toBeNull();
    expect(result?.consumed).toHaveLength(2);
    expect(result?.consumed[0].path).toBe('admin');
    expect(result?.consumed[1].path).toBe('auth');
  });

  it('should return null if "auth" is not present in segments', () => {
    const segments = [new UrlSegment('home', {}), new UrlSegment('about', {})];

    const result = authMatcher(segments);

    expect(result).toBeNull();
  });

  it('should return null for empty segments array', () => {
    const segments: UrlSegment[] = [];

    const result = authMatcher(segments);

    expect(result).toBeNull();
  });

  it('should only match the first occurrence of "auth"', () => {
    const segments = [new UrlSegment('prefix', {}), new UrlSegment('auth', {}), new UrlSegment('middle', {}), new UrlSegment('auth', {}), new UrlSegment('suffix', {})];

    const result = authMatcher(segments);

    expect(result).not.toBeNull();
    expect(result?.consumed).toHaveLength(2);
    expect(result?.consumed[0].path).toBe('prefix');
    expect(result?.consumed[1].path).toBe('auth');
  });
});
