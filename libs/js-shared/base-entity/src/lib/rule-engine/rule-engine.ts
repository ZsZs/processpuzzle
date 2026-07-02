import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

export type RuleSeverity = 'ERROR' | 'WARNING' | 'INFO';

export interface EvaluatableRule {
  id: string;
  expression: string;
  severity: RuleSeverity;
  message?: string;
  translocoId?: string;
  enabled?: boolean;
  fields?: string[];
}

export interface RuleEvaluationResult {
  ruleId: string;
  passed: boolean;
  severity: RuleSeverity;
  message?: string;
  translocoId?: string;
  error?: string;
  fields?: string[];
}

export interface RuleEngine {
  loadRulesFor(context: string): Observable<EvaluatableRule[]>;
  evaluate(entity: unknown, rule: EvaluatableRule): RuleEvaluationResult;
}

export const RULE_ENGINE = new InjectionToken<RuleEngine>('RULE_ENGINE');
