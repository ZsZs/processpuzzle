import { describe, expect, it } from 'vitest';
import { BaseRule } from './base-rule';

describe('BaseRule', () => {
  it('exposes the name and priority passed to the constructor', () => {
    const rule = new BaseRule('credit-check', 5);

    expect(rule.name).toBe('credit-check');
    expect(rule.priority).toBe(5);
  });

  it('defaults priority to zero when not provided', () => {
    const rule = new BaseRule('credit-check');

    expect(rule.priority).toBe(0);
  });

  it('formats a description string combining name and priority', () => {
    const rule = new BaseRule('credit-check', 5);

    expect(rule.describe()).toBe('credit-check (priority 5)');
  });
});
