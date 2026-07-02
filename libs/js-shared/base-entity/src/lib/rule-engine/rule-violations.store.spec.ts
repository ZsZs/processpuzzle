import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { RuleEvaluationResult } from './rule-engine';
import { RuleViolationsSingletonStore } from './rule-violations.store';

function make(severity: RuleEvaluationResult['severity'], ruleId = severity.toLowerCase()): RuleEvaluationResult {
  return { ruleId, passed: false, severity };
}

describe('RuleViolationsSingletonStore', () => {
  it('setViolations records the entity name and violations', () => {
    const store = TestBed.inject(RuleViolationsSingletonStore);
    const violations = [make('ERROR'), make('WARNING')];

    store.setViolations('Order', violations);

    expect(store.entityName()).toBe('Order');
    expect(store.violations()).toEqual(violations);
    expect(store.hasViolations()).toBe(true);
  });

  it('exposes counts per severity as computed signals', () => {
    const store = TestBed.inject(RuleViolationsSingletonStore);
    store.setViolations('Order', [make('ERROR'), make('ERROR'), make('WARNING'), make('INFO'), make('INFO'), make('INFO')]);

    expect(store.errorCount()).toBe(2);
    expect(store.warningCount()).toBe(1);
    expect(store.infoCount()).toBe(3);
  });

  it('clearViolations resets the store to its initial state', () => {
    const store = TestBed.inject(RuleViolationsSingletonStore);
    store.setViolations('Order', [make('ERROR')]);

    store.clearViolations();

    expect(store.entityName()).toBeUndefined();
    expect(store.violations()).toEqual([]);
    expect(store.hasViolations()).toBe(false);
    expect(store.errorCount()).toBe(0);
  });
});
