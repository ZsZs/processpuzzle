import { describe, expect, it } from 'vitest';
import { BaseRuleMapper } from './base-rule.mapper';

describe('BaseRuleMapper', () => {
  const mapper = new BaseRuleMapper();

  /** Reported by the backend on a draft; never sent back, since only a release changes it. */
  it('reads the release state, and does not send it back', () => {
    const rule = mapper.fromDto({ id: 'r1', name: 'Positive amount', context: 'Order', expression: 'entity.amount > 0', releaseState: 'NEW' });

    expect(rule.releaseState).toBe('NEW');
    expect(mapper.toDto(rule)).not.toHaveProperty('releaseState');
    expect(mapper.toDto(rule)).toMatchObject({ id: 'r1', name: 'Positive amount', context: 'Order' });
  });
});
