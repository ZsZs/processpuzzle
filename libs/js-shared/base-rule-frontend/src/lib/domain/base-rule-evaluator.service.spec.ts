import { ExchangeRates, STANDARD_UNIT_CATALOG } from '@processpuzzle/util';
import { describe, expect, it, beforeEach } from 'vitest';
import { BaseRule, Severity } from './base-rule';
import { BaseRuleEvaluatorService } from './base-rule-evaluator.service';
import measureCases from './rule-measure-cases.json';

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
    const throws = rule({ expression: 'entity.missing.field > 0' });

    const result = evaluator.evaluate({}, throws);

    expect(result.passed).toBe(false);
    expect(result.error).toMatch(/Rule evaluation threw.*Cannot read properties of undefined \(reading 'missing'\)|Rule evaluation threw.*field/);
  });

  it('reports a compile error, naming the construct, for syntax a rule cannot use', () => {
    const assignment = rule({ expression: 'entity.quantity = 1' });
    const block = rule({ expression: '(() => { return true; })()' });

    expect(evaluator.evaluate({}, assignment).error).toMatch(/Failed to compile rule: an assignment/);
    expect(evaluator.evaluate({}, block).error).toMatch(/Failed to compile rule: an arrow function with a block body/);
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

  it('keeps one compiled function for identical expressions', () => {
    evaluator.evaluate({ quantity: 3 }, rule());
    evaluator.evaluate({ quantity: 7 }, rule({ id: 'r2' }));
    expect(evaluator.cacheSize()).toBe(1);

    evaluator.evaluate({ quantity: 7 }, rule({ id: 'r3', expression: 'entity.quantity > 1' }));
    expect(evaluator.cacheSize()).toBe(2);
  });

  it('keeps nothing for an expression that does not compile', () => {
    evaluator.evaluate({}, rule({ expression: 'entity.quantity >' }));

    expect(evaluator.cacheSize()).toBe(0);
  });

  it('clearCache() forces re-compilation on the next evaluate', () => {
    const r = rule();
    evaluator.evaluate({ quantity: 1 }, r);
    expect(evaluator.cacheSize()).toBe(1);

    evaluator.clearCache();
    expect(evaluator.cacheSize()).toBe(0);

    evaluator.evaluate({ quantity: 1 }, r);
    expect(evaluator.cacheSize()).toBe(1);
  });
});

describe('BaseRuleEvaluatorService measure helpers', () => {
  const evaluator = new BaseRuleEvaluatorService();
  const ruleOf = (expression: string): BaseRule => new BaseRule('r1', 'measure-rule', 'A rule', 'Item', expression, Severity.ERROR, 'Rule failed');

  /** The cases the backend's `MeasureBindingsTest` runs too: a rule must say here what it will say there. */
  it.each(measureCases.cases)('$expression', (testCase) => {
    const result = evaluator.evaluate(measureCases.entity, ruleOf(testCase.expression), STANDARD_UNIT_CATALOG, new ExchangeRates(measureCases.rates));

    if ('error' in testCase) {
      expect(result.passed).toBe(false);
      expect(result.error).toContain(testCase.error);
    } else {
      expect(result.error).toBeUndefined();
      expect(result.passed).toBe(testCase.expected);
    }
  });

  it('refuses to change a measure, as it refuses every other write a rule might try', () => {
    const result = evaluator.evaluate({}, ruleOf("(qty('5 kg').amount = '9') === '9'"));

    expect(result.passed).toBe(false);
    expect(result.error).toMatch(/Failed to compile rule: an assignment/);
  });

  it('is available to a rule evaluated again from the cache', () => {
    const rule = ruleOf("!entity.weight || qty(entity.weight).le(qty('10 kg'))");

    expect(evaluator.evaluate({ weight: { amount: '11000', unit: 'g' } }, rule).passed).toBe(false);
    expect(evaluator.evaluate({ weight: { amount: '9000', unit: 'g' } }, rule).passed).toBe(true);
    expect(evaluator.evaluate({}, rule).passed).toBe(true);
  });

  it('cannot build a measure from anything else', () => {
    const errorOf = (expression: string) => evaluator.evaluate({}, ruleOf(expression)).error;

    expect(errorOf('qty(5) === null')).toContain('not a quantity');
    expect(errorOf('money(5) === null')).toContain('not a money');
    expect(errorOf("qty('1 kg').to(5)")).toContain('unit code');
    expect(errorOf("qty('1 kg').gt(5)")).toContain('expected a quantity');
    expect(errorOf("money('1 EUR').gt(5)")).toContain('expected money');
    expect(errorOf("money('1 EUR').times(NaN)")).toContain('expected a number');
    expect(errorOf("qty({ amount: 'x', unit: 'kg' })")).toContain('is not a decimal number');
    expect(errorOf("qty({ amount: true, unit: 'kg' })")).toContain('amount must be a decimal number');
    expect(errorOf('dims(5) === null')).toContain('not a dimensions');
  });

  it('reads an object literal of dimensions, with or without a depth', () => {
    expect(evaluator.evaluate({}, ruleOf("dims({ width: 1, height: 2, depth: 3, unit: 'm' }).volume().eq(qty('6 m3'))")).passed).toBe(true);
    expect(evaluator.evaluate({}, ruleOf("`${dims({ width: 1, height: 2, unit: 'm' })}` === '1 x 2 m'")).passed).toBe(true);
    expect(evaluator.evaluate({}, ruleOf("`${dims({ width: 1, height: 2, depth: 3, unit: 'm' })}` === '1 x 2 x 3 m'")).passed).toBe(true);
  });
});

describe('BaseRuleEvaluatorService with an organization’s units', () => {
  const evaluator = new BaseRuleEvaluatorService();
  const expression = "qty(entity.load).gt(qty('900 kg'))";
  const entity = { load: { amount: '2', unit: 'pallet' } };
  const ruleOf = (): BaseRule => new BaseRule('r1', 'heavy', 'A rule', 'Item', expression, Severity.ERROR, 'Too light');
  const acme = STANDARD_UNIT_CATALOG.withUnits([{ kind: 'MASS', code: 'pallet', symbol: 'plt', name: 'Pallet', factor: '500' }]);

  it('reads units in the catalog it is given, and a rule of another catalog does not know them', () => {
    expect(evaluator.evaluate(entity, ruleOf(), acme).passed).toBe(true);
    expect(evaluator.evaluate(entity, ruleOf()).error).toContain('pallet');
    expect(evaluator.evaluate(entity, ruleOf(), acme).passed).toBe(true);
  });

  it('puts the previous catalog back when the evaluation throws', () => {
    expect(evaluator.evaluate({ load: { amount: '2', unit: 'nonsense' } }, ruleOf(), acme).error).toContain('nonsense');
    expect(evaluator.evaluate(entity, ruleOf()).error).toContain('pallet');
  });
});
