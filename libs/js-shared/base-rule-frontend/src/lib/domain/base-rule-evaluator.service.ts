import { Injectable } from '@angular/core';
import { BaseRule } from './base-rule';
import { dims, money, qty } from './measure-bindings';
import { RuleEvaluationResult } from './rule-evaluation-result';

/** A rule's expression as a function of the entity and the measure helpers (`qty`, `money`, `dims`) it may call. */
type CompiledRule = (entity: unknown, qty: unknown, money: unknown, dims: unknown) => unknown;

@Injectable({ providedIn: 'root' })
export class BaseRuleEvaluatorService {
  private readonly compiled = new Map<string, CompiledRule>();

  evaluate(entity: unknown, rule: BaseRule): RuleEvaluationResult {
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
      result = fn(entity, qty, money, dims);
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

  private compile(expression: string): CompiledRule {
    const trimmed = (expression ?? '').trim();
    if (!trimmed) throw new Error('Rule expression is empty');

    const cached = this.compiled.get(trimmed);
    if (cached) return cached;

    // The one place a rule's text becomes code. The expression is authored in the rule designer by users allowed to define
    // rules — it is not input from a page — and runs here only to give the same answer the backend's sandbox will give.
    // (`sonarjs/code-eval` is switched off for this file in eslint.sonar.config.mjs, not with a comment here.)
    const fn = new Function('entity', 'qty', 'money', 'dims', `"use strict"; return (${trimmed});`) as CompiledRule;
    this.compiled.set(trimmed, fn);
    return fn;
  }
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
