import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { describe, expect, it, beforeEach } from 'vitest';
import { mock } from 'vitest-mock-extended';
import type { EvaluatableRule } from '@processpuzzle/base-entity';
import { RULE_ENGINE } from '@processpuzzle/base-entity';
import { BaseRule, Severity } from '../domain/base-rule';
import { BaseRuleService } from '../domain/base-rule.service';
import { BaseRuleEvaluatorService } from '../domain/base-rule-evaluator.service';
import { RuleEvaluationResult as InternalResult } from '../domain/rule-evaluation-result';
import { BaseRuleEngineAdapter, provideBaseRuleEngine } from './base-rule-engine.adapter';

describe('BaseRuleEngineAdapter', () => {
  const service = mock<BaseRuleService>();
  const evaluator = mock<BaseRuleEvaluatorService>();
  let adapter: BaseRuleEngineAdapter;

  beforeEach(() => {
    service.findByQuery.mockReset();
    evaluator.evaluate.mockReset();

    TestBed.configureTestingModule({
      providers: [
        BaseRuleEngineAdapter,
        { provide: BaseRuleService, useValue: service },
        { provide: BaseRuleEvaluatorService, useValue: evaluator },
      ],
    });
    adapter = TestBed.inject(BaseRuleEngineAdapter);
  });

  function baseRule(overrides: Partial<BaseRule> = {}): BaseRule {
    return Object.assign(
      new BaseRule(
        'r1',
        'positive-quantities',
        'Quantities must be positive',
        'Order',
        'entity.quantity > 0',
        Severity.WARNING,
        'Quantity must be positive',
        'order.quantity.positive',
        undefined,
        undefined,
        true,
        undefined,
        undefined,
        undefined,
        ['quantity'],
      ),
      overrides,
    );
  }

  describe('loadRulesFor()', () => {
    it('queries the service with a context filter', async () => {
      service.findByQuery.mockReturnValue(of([]));

      await firstValueFrom(adapter.loadRulesFor('Order'));

      expect(service.findByQuery).toHaveBeenCalledWith({
        filters: [{ property: 'context', operator: '==', value: 'Order' }],
      });
    });

    it('unwraps a plain array response, filters by context and enabled, and maps to EvaluatableRule', async () => {
      const matching = baseRule({ id: 'r1', context: 'Order', enabled: true });
      const wrongContext = baseRule({ id: 'r2', context: 'Customer' });
      const disabled = baseRule({ id: 'r3', context: 'Order', enabled: false });
      const enabledUndefined = baseRule({ id: 'r4', context: 'Order' });
      enabledUndefined.enabled = undefined as unknown as boolean;
      service.findByQuery.mockReturnValue(of([matching, wrongContext, disabled, enabledUndefined]));

      const rules = await firstValueFrom(adapter.loadRulesFor('Order'));

      expect(rules.map((r) => r.id)).toEqual(['r1', 'r4']);
      expect(rules[0]).toEqual({
        id: 'r1',
        expression: 'entity.quantity > 0',
        severity: 'WARNING',
        message: 'Quantity must be positive',
        translocoId: 'order.quantity.positive',
        enabled: true,
        fields: ['quantity'],
      });
    });

    it('unwraps a paginated { content } response', async () => {
      const paged = { content: [baseRule({ id: 'r1', context: 'Order' })], page: 0, pageSize: 10, totalPageCount: 1 };
      service.findByQuery.mockReturnValue(of(paged));

      const rules = await firstValueFrom(adapter.loadRulesFor('Order'));

      expect(rules).toHaveLength(1);
      expect(rules[0].id).toBe('r1');
    });

    it('treats a single-object response as a one-element list', async () => {
      const single = baseRule({ id: 'r-single', context: 'Order' });
      service.findByQuery.mockReturnValue(of(single));

      const rules = await firstValueFrom(adapter.loadRulesFor('Order'));

      expect(rules).toHaveLength(1);
      expect(rules[0].id).toBe('r-single');
    });
  });

  describe('evaluate()', () => {
    it('delegates to the evaluator using a BaseRule reconstructed from the EvaluatableRule', () => {
      const evaluatable: EvaluatableRule = {
        id: 'r1',
        expression: 'entity.quantity > 0',
        severity: 'WARNING',
        message: 'Nope',
        translocoId: 'nope-id',
        enabled: true,
        fields: ['quantity'],
      };
      const internal: InternalResult = {
        ruleId: 'r1',
        ruleName: 'BaseRule',
        passed: false,
        severity: Severity.WARNING,
        message: 'Nope',
        translocoId: 'nope-id',
        fields: ['quantity'],
      };
      evaluator.evaluate.mockReturnValue(internal);

      const result = adapter.evaluate({ quantity: 0 }, evaluatable);

      expect(evaluator.evaluate).toHaveBeenCalledTimes(1);
      const [entityArg, ruleArg] = evaluator.evaluate.mock.calls[0];
      expect(entityArg).toEqual({ quantity: 0 });
      expect(ruleArg).toBeInstanceOf(BaseRule);
      expect(ruleArg).toMatchObject({
        id: 'r1',
        expression: 'entity.quantity > 0',
        severity: Severity.WARNING,
        message: 'Nope',
        translocoId: 'nope-id',
        enabled: true,
        fields: ['quantity'],
      });
      expect(result).toEqual({
        ruleId: 'r1',
        passed: false,
        severity: 'WARNING',
        message: 'Nope',
        translocoId: 'nope-id',
        error: undefined,
        fields: ['quantity'],
      });
    });

    it('falls back to the rule severity and rule fields when the evaluator result omits them', () => {
      const evaluatable: EvaluatableRule = {
        id: 'r-fb',
        expression: 'true',
        severity: 'INFO',
        fields: ['a', 'b'],
      };
      evaluator.evaluate.mockReturnValue({
        ruleId: 'r-fb',
        passed: true,
      });

      const result = adapter.evaluate({}, evaluatable);

      expect(result.severity).toBe('INFO');
      expect(result.fields).toEqual(['a', 'b']);
    });

    it('defaults enabled=true when the EvaluatableRule omits enabled', () => {
      const evaluatable: EvaluatableRule = {
        id: 'r-en',
        expression: 'true',
        severity: 'ERROR',
      };
      evaluator.evaluate.mockReturnValue({ ruleId: 'r-en', passed: true });

      adapter.evaluate({}, evaluatable);

      const ruleArg = evaluator.evaluate.mock.calls[0][1];
      expect(ruleArg.enabled).toBe(true);
    });

    it('propagates a compile/runtime error from the evaluator', () => {
      const evaluatable: EvaluatableRule = { id: 'r-err', expression: '???', severity: 'ERROR' };
      evaluator.evaluate.mockReturnValue({
        ruleId: 'r-err',
        passed: false,
        severity: Severity.ERROR,
        error: 'Failed to compile',
      });

      const result = adapter.evaluate({}, evaluatable);

      expect(result.passed).toBe(false);
      expect(result.error).toBe('Failed to compile');
    });
  });
});

describe('provideBaseRuleEngine()', () => {
  it('wires the adapter as the RULE_ENGINE via useExisting', () => {
    const service = mock<BaseRuleService>();
    const evaluator = mock<BaseRuleEvaluatorService>();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        BaseRuleEngineAdapter,
        provideBaseRuleEngine(),
        { provide: BaseRuleService, useValue: service },
        { provide: BaseRuleEvaluatorService, useValue: evaluator },
      ],
    });

    const engine = TestBed.inject(RULE_ENGINE);
    const adapter = TestBed.inject(BaseRuleEngineAdapter);
    expect(engine).toBe(adapter);
  });
});
