import { Injectable } from '@angular/core';
import { STANDARD_UNIT_CATALOG, type UnitCatalog } from '@processpuzzle/util';
import { BaseRule } from './base-rule';
import { interpret, rootScope } from './expression/rule-expression-interpreter';
import { withCatalog } from './measure-bindings';
import { parseRuleExpression } from './expression/rule-expression-parser';
import { RuleEvaluationResult } from './rule-evaluation-result';

/** A rule's expression as a function of the entity under evaluation. */
type CompiledRule = (entity: unknown) => unknown;

@Injectable({ providedIn: 'root' })
export class BaseRuleEvaluatorService {
  private readonly compiled = new Map<string, CompiledRule>();

  /** @param catalog the units `qty()`, `money()` and `dims()` read: the organization's, or the standard ones when absent */
  evaluate(entity: unknown, rule: BaseRule, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): RuleEvaluationResult {
    const base = { ruleId: rule.id, ruleName: rule.name, severity: rule.severity };
    if (!rule.enabled) return { ...base, passed: true };

    let fn: CompiledRule;
    try {
      fn = this.compile(rule.expression);
    } catch (err) {
      return { ...base, passed: false, error: `Failed to compile rule: ${errorMessage(err)}` };
    }

    let result: unknown;
    try {
      result = withCatalog(catalog, () => fn(entity));
    } catch (err) {
      return { ...base, passed: false, error: `Rule evaluation threw: ${errorMessage(err)}` };
    }

    const passed = Boolean(result);
    if (passed) return { ...base, passed: true, fields: rule.fields };
    return { ...base, passed: false, message: rule.message ?? rule.description ?? rule.name, translocoId: rule.translocoId, fields: rule.fields };
  }

  clearCache(): void {
    this.compiled.clear();
  }

  /** How many distinct expressions are compiled and kept. */
  cacheSize(): number {
    return this.compiled.size;
  }

  private compile(expression: string): CompiledRule {
    const trimmed = (expression ?? '').trim();
    if (!trimmed) throw new Error('Rule expression is empty');

    const cached = this.compiled.get(trimmed);
    if (cached) return cached;

    // The text is parsed and checked once, and the tree is interpreted for each entity: no text ever becomes code, so a
    // rule can reach nothing of the page — only the entity and the helpers rule-expression-builtins lists.
    const ast = parseRuleExpression(trimmed);
    const fn: CompiledRule = (entity) => interpret(ast, rootScope(entity));
    this.compiled.set(trimmed, fn);
    return fn;
  }
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
