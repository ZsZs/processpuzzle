import { describe, expect, it } from 'vitest';
import { currentOrgKey, firstPathSegment, looksLikeOrgKey, resolveTenantRealm } from './tenant-realm.resolver';

describe('resolveTenantRealm', () => {
  describe('firstPathSegment', () => {
    it('reads the first non-empty segment', () => {
      expect(firstPathSegment('/my-org/admin/users')).toBe('my-org');
      expect(firstPathSegment('my-org')).toBe('my-org');
      expect(firstPathSegment('//my-org//')).toBe('my-org');
    });

    it('has nothing to read at the root', () => {
      expect(firstPathSegment('/')).toBeUndefined();
      expect(firstPathSegment('')).toBeUndefined();
    });

    it('decodes a percent-encoded segment, so an encoded key is not mistaken for a malformed one', () => {
      expect(firstPathSegment('/my%2Dorg')).toBe('my-org');
    });
  });

  describe('looksLikeOrgKey', () => {
    it('accepts the slug shape the backend enforces', () => {
      expect(looksLikeOrgKey('my-org')).toBe(true);
      expect(looksLikeOrgKey('org1')).toBe(true);
      expect(looksLikeOrgKey('a-b-c-1')).toBe(true);
    });

    it('rejects what the backend would reject', () => {
      expect(looksLikeOrgKey('a')).toBe(false); // too short
      expect(looksLikeOrgKey('a'.repeat(64))).toBe(false); // too long
      expect(looksLikeOrgKey('-org')).toBe(false);
      expect(looksLikeOrgKey('org-')).toBe(false);
      expect(looksLikeOrgKey('my--org')).toBe(false);
      expect(looksLikeOrgKey('My-Org')).toBe(false); // upper case
      expect(looksLikeOrgKey('my org')).toBe(false);
      expect(looksLikeOrgKey('my_org')).toBe(false);
      expect(looksLikeOrgKey(undefined)).toBe(false);
      expect(looksLikeOrgKey('')).toBe(false);
    });

    // The point of scanning rather than using the contract's nested-repetition regex: an
    // adversarial segment must be cheap to reject, not a backtracking hazard.
    it('rejects a long adversarial segment without pathological cost', () => {
      const started = Date.now();
      expect(looksLikeOrgKey(`${'a-'.repeat(5000)}!`)).toBe(false);
      expect(Date.now() - started).toBeLessThan(100);
    });
  });

  it('substitutes the tenant read from the path', () => {
    expect(resolveTenantRealm('{orgKey}', '/my-org/admin/users')).toBe('my-org');
    expect(resolveTenantRealm('tenant-{orgKey}', '/my-org/home')).toBe('tenant-my-org');
  });

  it('leaves a template without the placeholder alone, so a fixed realm needs no special case', () => {
    expect(resolveTenantRealm('processpuzzle-platform', '/my-org/admin')).toBe('processpuzzle-platform');
  });

  it('falls back when the path names no plausible tenant', () => {
    expect(resolveTenantRealm('{orgKey}', '/', 'processpuzzle-public')).toBe('processpuzzle-public');
    expect(resolveTenantRealm('{orgKey}', '/a', 'processpuzzle-public')).toBe('processpuzzle-public');
  });

  // A reserved segment resolves to a realm that does not exist, and Keycloak's own "no such realm"
  // is a clearer answer than a guess made here — ReservedOrganizationKeys lives on the backend and
  // this runs before the first HTTP call.
  it('does not filter reserved segments, but does filter what could never be a key', () => {
    expect(resolveTenantRealm('{orgKey}', '/login', 'fallback')).toBe('login');
    expect(resolveTenantRealm('{orgKey}', '/API', 'fallback')).toBe('fallback');
  });

  it('reports the tenant the URL names', () => {
    expect(currentOrgKey('/my-org/admin/users')).toBe('my-org');
    expect(currentOrgKey('/')).toBeUndefined();
  });
});
