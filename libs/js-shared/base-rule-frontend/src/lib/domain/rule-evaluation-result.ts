import { Severity } from './base-rule';

export interface RuleEvaluationResult {
  ruleId: string;
  ruleName?: string;
  passed: boolean;
  severity?: Severity;
  message?: string;
  translocoId?: string;
  error?: string;
  fields?: string[];
}
