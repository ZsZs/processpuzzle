import { describe, expect, it } from 'vitest';
import { BaseState } from './base-state';

describe('BaseState', () => {
  it('exposes the name passed to the constructor', () => {
    const state = new BaseState('approved');

    expect(state.name).toBe('approved');
  });

  it('defaults isFinal to false when not provided', () => {
    const state = new BaseState('approved');

    expect(state.isFinal).toBe(false);
  });

  it('marks the description as final when the state is final', () => {
    const state = new BaseState('closed', true);

    expect(state.describe()).toBe('closed (final)');
  });

  it('returns only the name when the state is not final', () => {
    const state = new BaseState('open');

    expect(state.describe()).toBe('open');
  });
});
