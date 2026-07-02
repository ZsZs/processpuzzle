import { describe, expect, it, beforeEach } from 'vitest';
import { BaseRule, Severity } from './base-rule';
import { BaseRuleEvaluatorService } from './base-rule-evaluator.service';

describe('BaseRuleEvaluatorService', () => {
  let evaluator: BaseRuleEvaluatorService;

  beforeEach(() => {
    evaluator = new BaseRuleEvaluatorService();
  });

  function rule(overrides: Partial<BaseRule> = {}): BaseRule {
    return Object.assign(
      new BaseRule('r1', 'positive-quantities', 'Quantities must be positive', 'Order', 'entity.quantity > 0', Severity.ERROR, 'Quantity must be positive'),
      overrides,
    );
  }

  it('returns passed=true when the expression evaluates truthy', () => {
    const result = evaluator.evaluate({ quantity: 5 }, rule());

    expect(result.passed).toBe(true);
    expect(result.ruleId).toBe('r1');
    expect(result.severity).toBe(Severity.ERROR);
  });

  it('returns passed=false with the rule message when the expression is falsy', () => {
    const result = evaluator.evaluate({ quantity: 0 }, rule());

    expect(result.passed).toBe(false);
    expect(result.message).toBe('Quantity must be positive');
    expect(result.severity).toBe(Severity.ERROR);
  });

  it('falls back to description, then name, when no message is set', () => {
    const noMessage = rule({ message: undefined });
    expect(evaluator.evaluate({ quantity: 0 }, noMessage).message).toBe('Quantities must be positive');

    const noMessageNoDescription = rule({ message: undefined, description: undefined });
    expect(evaluator.evaluate({ quantity: 0 }, noMessageNoDescription).message).toBe('positive-quantities');
  });

  it('carries translocoId through to the failure result', () => {
    const withTransloco = rule({ translocoId: 'order.quantity.positive' });

    const result = evaluator.evaluate({ quantity: 0 }, withTransloco);

    expect(result.translocoId).toBe('order.quantity.positive');
  });

  it('reports a compile error for a syntactically invalid expression', () => {
    const broken = rule({ expression: 'entity.quantity >' });

    const result = evaluator.evaluate({ quantity: 5 }, broken);

    expect(result.passed).toBe(false);
    expect(result.error).toMatch(/Failed to compile/);
  });

  it('reports a runtime error when the expression throws', () => {
    const throws = rule({ expression: '(() => { throw new Error("boom"); })()' });

    const result = evaluator.evaluate({}, throws);

    expect(result.passed).toBe(false);
    expect(result.error).toMatch(/Rule evaluation threw.*boom/);
  });

  it('reports a compile error for an empty expression', () => {
    const empty = rule({ expression: '   ' });

    const result = evaluator.evaluate({}, empty);

    expect(result.passed).toBe(false);
    expect(result.error).toMatch(/empty/);
  });

  it('short-circuits to passed=true when the rule is disabled', () => {
    const disabled = rule({ enabled: false, expression: 'this-would-throw-if-evaluated' });

    const result = evaluator.evaluate({}, disabled);

    expect(result.passed).toBe(true);
    expect(result.error).toBeUndefined();
  });

  it('reuses the compiled function for identical expressions', () => {
    const first = rule();
    const second = rule({ id: 'r2' });
    const OriginalFunction = globalThis.Function;
    let constructions = 0;
    const globalScope = globalThis as unknown as { Function: FunctionConstructor };
    globalScope.Function = new Proxy(OriginalFunction, {
      construct(target, args) {
        constructions++;
        return Reflect.construct(target, args);
      },
    });
    try {
      evaluator.evaluate({ quantity: 3 }, first);
      const before = constructions;
      evaluator.evaluate({ quantity: 7 }, second);
      expect(constructions).toBe(before);
    } finally {
      globalScope.Function = OriginalFunction;
    }
  });

  it('clearCache() forces re-compilation on the next evaluate', () => {
    const r = rule();
    evaluator.evaluate({ quantity: 1 }, r);

    evaluator.clearCache();

    const OriginalFunction = globalThis.Function;
    let constructions = 0;
    const globalScope = globalThis as unknown as { Function: FunctionConstructor };
    globalScope.Function = new Proxy(OriginalFunction, {
      construct(target, args) {
        constructions++;
        return Reflect.construct(target, args);
      },
    });
    try {
      evaluator.evaluate({ quantity: 1 }, r);
      expect(constructions).toBe(1);
    } finally {
      globalScope.Function = OriginalFunction;
    }
  });
});
