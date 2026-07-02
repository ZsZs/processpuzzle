/*
 * Public API Surface of @processpuzzle/base-rule-frontend
 */

export { BaseRule, Severity } from './lib/domain/base-rule';
export { BaseRuleEvaluatorService } from './lib/domain/base-rule-evaluator.service';
export { RuleEvaluationResult } from './lib/domain/rule-evaluation-result';
export { createBaseRuleDescriptor } from './lib/domain/base-rule.descriptors';
export { BaseRuleMapper } from './lib/domain/base-rule.mapper';
export { BaseRuleService } from './lib/domain/base-rule.service';
export { BaseRuleStore } from './lib/domain/base-rule.store';
export { BaseRuleFacade } from './lib/feature/base-rule.facade';
export { BaseRuleContainerComponent } from './lib/feature/base-rule-container.component';
export { BASE_RULE_ROUTES } from './lib/base-rule.routes';
export { BaseRuleEngineAdapter, provideBaseRuleEngine } from './lib/adapter/base-rule-engine.adapter';
