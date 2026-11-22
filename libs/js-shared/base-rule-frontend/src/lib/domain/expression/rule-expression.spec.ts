import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { parse as parseYaml } from 'yaml';
import { BaseRule, Severity } from '../base-rule';
import { BaseRuleEvaluatorService } from '../base-rule-evaluator.service';
import expressionCases from '../rule-expression-cases.json';
import { interpret, rootScope } from './rule-expression-interpreter';
import { parseRuleExpression, RuleExpressionError } from './rule-expression-parser';

const evaluator = new BaseRuleEvaluatorService();
const ruleOf = (expression: string): BaseRule => new BaseRule('r1', 'a-rule', 'A rule', 'Item', expression, Severity.ERROR, 'Rule failed');
const run = (expression: string, entity: unknown = {}) => evaluator.evaluate(entity, ruleOf(expression));

/**
 * A rule's text is parsed and interpreted; no text becomes code. These specs hold the interpreter to what the rules written
 * so far need, to what the backend's JavaScript engine answers for the same text, and to what a rule must never reach.
 */
describe('the rule expression interpreter', () => {
  /** The cases the backend's `RuleExpressionCasesTest` runs on GraalJS too — the oracle for what each means. */
  describe('shared cases', () => {
    it.each(expressionCases.cases)('$expression', (testCase) => {
      const result = run(testCase.expression, expressionCases.entity);

      if ('error' in testCase) {
        expect(result.passed).toBe(false);
        expect(result.error).toContain(testCase.error);
      } else {
        expect(result.error).toBeUndefined();
        expect(result.passed).toBe(testCase.expected);
      }
    });
  });

  describe('the sample rules the platform ships', () => {
    // The tests run from a bundle, where the spec's own directory is not this one: start from the workspace root.
    const directory = resolve(process.cwd(), 'libs/java-shared/base-rule-backend/src/main/resources/sample-rules') + '/';
    const samples = readdirSync(directory)
      .filter((file) => file.endsWith('.yaml'))
      .flatMap((file) => {
        const document = parseYaml(readFileSync(directory + file, 'utf-8')) as { rules?: { name: string; expression: string }[]; ruleDefinitions?: { name: string; expression: string }[] };
        return document.rules ?? document.ruleDefinitions ?? [];
      });

    it('finds them', () => {
      expect(samples.length).toBeGreaterThanOrEqual(17);
    });

    it.each(samples.map((sample) => [sample.name, sample.expression]))('%s compiles and runs against an empty entity', (_name, expression) => {
      expect(() => parseRuleExpression(expression)).not.toThrow();
      expect(run(expression).error).toBeUndefined();
    });

    it('says what the order rules mean', () => {
      const expressionOf = (name: string) => samples.find((sample) => sample.name === name)?.expression ?? '';
      const total = expressionOf('Total matches sum of line items');
      const line = (pieces: string, unit: string, price: string) => ({ quantity: { amount: pieces, unit }, unitPrice: { amount: price, currency: 'EUR' } });
      const lines = [line('2', 'pc', '19.99'), line('1', 'pc', '49.9')];
      const money = (amount: string) => ({ amount, currency: 'EUR' });

      expect(run(total, { lineItems: lines, total: money('89.88') }).passed).toBe(true);
      expect(run(total, { lineItems: lines, total: money('90') }).passed).toBe(false);
      expect(run(total, {}).passed).toBe(true);
      // A dozen is twelve pieces: 2 dozen at 10.00 and 3 pieces at 1.50.
      expect(run(total, { lineItems: [line('2', 'dz', '10.00'), line('3', 'pc', '1.50')], total: money('244.50') }).passed).toBe(true);

      const positive = expressionOf('All line item quantities are positive');
      expect(run(positive, { lineItems: lines }).passed).toBe(true);
      expect(run(positive, { lineItems: [line('0', 'pc', '1')] }).passed).toBe(false);
      expect(run(expressionOf('Special orders may include zero-quantity placeholder items'), { lineItems: [line('0', 'pc', '0')] }).passed).toBe(true);
    });
  });

  describe('what a rule cannot do', () => {
    const entity = () => ({ n: 5, name: 'Alpha', tags: ['a', 'b'], nested: { value: 1 } });

    it.each([
      ['an assignment', 'entity.n = 6'],
      ['an assignment', "(entity.name += 'x') === 'x'"],
      ['++ or --', 'entity.n++ > 0'],
      ['a function expression', '(function () { return true; })()'],
      ['`this`', 'this === undefined'],
      ['a comma expression', '(1, 2) === 2'],
      ['a tagged template', 'String.raw`a` === "a"'],
      ['`new` of anything but Date', "new Function('return 1')() === 1"],
      ['`new` of anything but Date', 'new Map().size === 0'],
      ['an arrow function with a block body', '(() => { return true; })()'],
      ['an async or generator function', '(async () => true)() !== undefined'],
      ['destructuring, default or rest parameters', '(({ a }) => a)({ a: 1 }) === 1'],
      ['destructuring, default or rest parameters', '((a = 1) => a)() === 1'],
      ['`delete`', 'delete entity.n'],
      ['`void`', 'void 0 === undefined'],
      ['`instanceof`', 'entity.tags instanceof Array'],
      ['a class', 'class A {}'],
      ['a getter, setter or method in an object literal', '({ get a() { return 1; } }).a === 1'],
      ['a computed key in an object literal', "({ ['a']: 1 }).a === 1"],
      ['object spread', '({ ...entity.nested }).value === 1'],
      ['a single expression', 'true); (false'],
    ])('does not compile %s', (what, expression) => {
      const result = run(expression, entity());

      expect(result.passed).toBe(false);
      expect(result.error).toContain('Failed to compile rule');
      expect(result.error).toContain(what === 'a single expression' ? 'a rule is a single expression' : what);
    });

    it.each([
      'window.location.href',
      "document.cookie === ''",
      "fetch('/api/x')",
      "localStorage.getItem('token')",
      'globalThis',
      "eval('1')",
      "Function('return 1')()",
      "setTimeout(() => 1, 0)",
      'process',
      'require',
    ])('has no way to the page: %s', (expression) => {
      const result = run(expression, entity());

      expect(result.passed).toBe(false);
      expect(result.error).toMatch(/is not defined/);
    });

    it.each([
      'entity.constructor === Object',
      'entity.name.constructor',
      "[].map.constructor('return 1')",
      'entity.__proto__ === undefined',
      'Object.prototype === undefined',
      '(() => 1).constructor',
    ])('never reads a constructor or prototype: %s', (expression) => {
      const result = run(expression, entity());

      expect(result.passed).toBe(false);
      expect(result.error).toMatch(/Failed to compile rule: '(constructor|__proto__|prototype)' is not available/);
    });

    it('refuses a computed route to them just the same', () => {
      const result = run("entity['constr' + 'uctor'] === undefined", entity());

      expect(result.passed).toBe(false);
      expect(result.error).toContain("'constructor' is not available");
    });

    it.each([
      ["entity.tags.push('z')", "'push' cannot be called on this value"],
      ['entity.tags.sort()', "'sort' cannot be called on this value"],
      ['entity.tags.reverse()', "'reverse' cannot be called on this value"],
      ['entity.tags.splice(0, 1)', "'splice' cannot be called on this value"],
      ['Object.assign(entity, { n: 1 })', "'assign' cannot be called on this value"],
      ["Object.defineProperty(entity, 'n', { value: 1 })", "'defineProperty' cannot be called on this value"],
      ['Object.freeze(entity)', "'freeze' cannot be called on this value"],
      ["entity.name.anchor('x')", "'anchor' cannot be called on this value"],
      ['(x => x).bind(null)', "'bind' cannot be called on this value"],
      ['(x => x).apply(null, [1])', "'apply' cannot be called on this value"],
      ['[1].map.call([1], x => x)', "'map' is a function"],
    ])('calls only what is listed: %s', (expression, message) => {
      const subject = entity();
      const result = run(expression, subject);

      expect(result.passed).toBe(false);
      expect(result.error).toContain(message);
      expect(subject).toEqual(entity());
    });

    it.each([
      ['entity.tags.map(entity.tags.push, entity.tags)', 'push'],
      ['[1].reduce(Object.getOwnPropertyDescriptor, {})', 'getOwnPropertyDescriptor'],
      ['[qty].map(Object.getPrototypeOf)', 'getPrototypeOf'],
      ['[1].map(Math.max).length > 0', 'max'],
    ])('does not pass a native method around as a value: %s', (expression, name) => {
      const subject = entity();
      const result = run(expression, subject);

      expect(result.passed).toBe(false);
      expect(result.error).toMatch(new RegExp(name));
      expect(subject).toEqual(entity());
    });

    it('does not call a function the entity carries, or anything that is not a rule’s own or a listed function', () => {
      expect(run('entity.fn()', { fn: () => 1 }).error).toContain("'fn' cannot be called on this value");
      expect(run('((f) => f(1))(entity.n)', { n: 5 }).error).toContain('only the functions of a rule expression can be called here');
      expect(run('((f) => f(1))(entity.fn)', { fn: () => 1 }).error).toContain("'fn' is a function");
    });

    it('leaves the entity as it was after any rule that reads it', () => {
      const subject = entity();

      run("entity.tags.map(t => t + '!').filter(Boolean).length === 2 && Object.keys(entity.nested).length === 1", subject);

      expect(subject).toEqual(entity());
    });

    it('stops a rule that recurses without end, as a failed rule', () => {
      const result = run('((f) => f(f))((f) => f(f))');

      expect(result.passed).toBe(false);
      expect(result.error).toMatch(/Rule evaluation threw/);
    });
  });

  describe('the language', () => {
    it('reports where the text stops making sense, in the author’s own text', () => {
      expect(() => parseRuleExpression('entity.quantity >')).toThrow('Unexpected token at character 17');
      expect(() => parseRuleExpression('entity.quantity >')).toThrow(RuleExpressionError);
    });

    it('lets a trailing line comment end before the rule does', () => {
      expect(run('entity.n > 3 // more than three', { n: 5 }).passed).toBe(true);
    });

    it('short-circuits the way JavaScript does', () => {
      expect(run("entity.missing && entity.missing.deep.deeper", {}).passed).toBe(false);
      expect(run('entity.a || entity.b.c', { a: 1 }).passed).toBe(true);
      expect(run('entity.a ?? entity.b.c', { a: 0 }).passed).toBe(false);
      expect(run('entity.n > 0 ? entity.n < 10 : entity.boom.x', { n: 5 }).passed).toBe(true);
    });

    it('closes over the names of the function it was made in', () => {
      expect(run('((base) => [1, 2, 3].map(x => x + base).every(x => x > 10))(10)').passed).toBe(true);
      expect(run('[1, 2].every(x => [3, 4].every(y => y > x))').passed).toBe(true);
    });

    it('reads a name it does not know as an error, and `typeof` of it as a string', () => {
      expect(run('unknown + 1').error).toContain('unknown is not defined');
      expect(run("typeof unknown === 'undefined'").passed).toBe(true);
    });

    it('interprets a parsed expression directly', () => {
      const ast = parseRuleExpression('entity.a + 1');

      expect(interpret(ast, rootScope({ a: 1 }))).toBe(2);
      expect(interpret(ast, rootScope({ a: 41 }))).toBe(42);
    });
  });
});
