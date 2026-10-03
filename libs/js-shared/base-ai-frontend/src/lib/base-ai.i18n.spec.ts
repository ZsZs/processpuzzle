import { describe, expect, it } from 'vitest';

import { BASE_AI_TRANSLOCO_SCOPE } from './base-ai.i18n';

describe('base-ai i18n', () => {
  it('declares the library scope', () => {
    expect(BASE_AI_TRANSLOCO_SCOPE).toBe('base_ai');
  });
});
