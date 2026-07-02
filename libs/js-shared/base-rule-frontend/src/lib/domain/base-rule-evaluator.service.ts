import { Injectable } from '@angular/core';
import { BaseRule } from './base-rule';
import { RuleEvaluationResult } from './rule-evaluation-result';

type CompiledRule = (entity: unknown) => unknown;

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
      result = fn(entity);
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

    const fn = new Function('entity', `"use strict"; return (${trimmed});`) as CompiledRule;
    this.compiled.set(trimmed, fn);
    return fn;
  }
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
