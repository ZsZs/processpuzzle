import { describe, expect, it } from 'vitest';
import { BaseRule, Severity } from './base-rule';

describe('BaseRule', () => {
  it('generates an id when none is provided', () => {
    const rule = new BaseRule();

    expect(rule.id).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('keeps the supplied id', () => {
    const rule = new BaseRule('positive-quantities');

    expect(rule.id).toBe('positive-quantities');
  });

  it('applies sensible defaults for unspecified attributes', () => {
    const rule = new BaseRule();

    expect(rule.name).toBe('BaseRule');
    expect(rule.context).toBe('');
    expect(rule.expression).toBe('');
    expect(rule.severity).toBe(Severity.ERROR);
    expect(rule.override).toBe(false);
    expect(rule.enabled).toBe(true);
  });

  it('exposes every property passed to the constructor', () => {
    const rule = new BaseRule(
      'r1',
      'No negative quantities',
      'Quantity must be > 0',
      'Order',
      'entity.quantity > 0',
      Severity.WARNING,
      'Quantity is negative',
      'order.quantity.negative',
      'parent-rule',
      true,
      false,
      3,
      '2026-01-01T00:00:00Z',
      '2026-01-02T00:00:00Z',
    );

    expect(rule).toMatchObject({
      id: 'r1',
      name: 'No negative quantities',
      description: 'Quantity must be > 0',
      context: 'Order',
      expression: 'entity.quantity > 0',
      severity: Severity.WARNING,
      message: 'Quantity is negative',
      translocoId: 'order.quantity.negative',
      extendsRuleId: 'parent-rule',
      override: true,
      enabled: false,
      version: 3,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
    });
  });
});
