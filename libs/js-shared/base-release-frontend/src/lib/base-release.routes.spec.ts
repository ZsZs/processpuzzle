import { describe, expect, it } from 'vitest';
import { BASE_RELEASE_ROUTES } from './base-release.routes';

describe('BASE_RELEASE_ROUTES', () => {
  it('is an empty branch until release screens are added', () => {
    expect(BASE_RELEASE_ROUTES).toEqual([]);
  });
});
