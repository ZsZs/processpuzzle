import { inject, Injectable, Provider } from '@angular/core';
import { map, Observable } from 'rxjs';
import type { EvaluatableRule, RuleEngine, RuleEvaluationResult } from '@processpuzzle/base-entity';
import { RULE_ENGINE } from '@processpuzzle/base-entity';
import { BaseRuleService } from '../domain/base-rule.service';
import { BaseRuleEvaluatorService } from '../domain/base-rule-evaluator.service';
import { BaseRule, Severity } from '../domain/base-rule';

@Injectable({ providedIn: 'root' })
export class BaseRuleEngineAdapter implements RuleEngine {
  private readonly service = inject(BaseRuleService);
  private readonly evaluator = inject(BaseRuleEvaluatorService);

  loadRulesFor(context: string): Observable<EvaluatableRule[]> {
    return this.service
      .findByQuery({
        filters: [{ property: 'context', operator: '==', value: context }],
      })
      .pipe(
        map((response) =>
          this.unwrap(response)
            .filter((rule) => rule.context === context && rule.enabled !== false)
            .map(toEvaluatable),
        ),
      );
  }

  evaluate(entity: unknown, rule: EvaluatableRule): RuleEvaluationResult {
    const result = this.evaluator.evaluate(entity, toBaseRule(rule));
    return {
      ruleId: result.ruleId,
      passed: result.passed,
      severity: (result.severity ?? rule.severity) as EvaluatableRule['severity'],
      message: result.message,
      translocoId: result.translocoId,
      error: result.error,
      fields: result.fields ?? rule.fields,
    };
  }

  private unwrap(response: unknown): BaseRule[] {
    if (Array.isArray(response)) return response as BaseRule[];
    if (response && typeof response === 'object' && 'content' in response) {
      return (response as { content: BaseRule[] }).content;
    }
    return [response as BaseRule];
  }
}

export function provideBaseRuleEngine(): Provider {
  return { provide: RULE_ENGINE, useExisting: BaseRuleEngineAdapter };
}

function toEvaluatable(rule: BaseRule): EvaluatableRule {
  return {
    id: rule.id,
    expression: rule.expression,
    severity: rule.severity as EvaluatableRule['severity'],
    message: rule.message,
    translocoId: rule.translocoId,
    enabled: rule.enabled,
    fields: rule.fields,
  };
}

function toBaseRule(rule: EvaluatableRule): BaseRule {
  return new BaseRule(rule.id, undefined, undefined, undefined, rule.expression, rule.severity as Severity, rule.message, rule.translocoId, undefined, undefined, rule.enabled ?? true, undefined, undefined, undefined, rule.fields);
}
