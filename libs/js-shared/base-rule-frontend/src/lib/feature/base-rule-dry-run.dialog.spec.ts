import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { describe, expect, it } from 'vitest';
import { mock } from 'vitest-mock-extended';
import { BaseRule, Severity } from '../domain/base-rule';
import { BaseRuleEvaluatorService } from '../domain/base-rule-evaluator.service';
import { RuleEvaluationResult } from '../domain/rule-evaluation-result';
import { BaseRuleDryRunDialog, BaseRuleDryRunDialogData, BaseRuleDryRunDialogResult } from './base-rule-dry-run.dialog';

describe('BaseRuleDryRunDialog', () => {
  const testRule = new BaseRule('r1', 'positive-quantities', 'desc', 'Order', 'entity.qty > 0', Severity.WARNING);

  function setup(opts: { data?: Partial<BaseRuleDryRunDialogData>; evaluationResult?: RuleEvaluationResult } = {}) {
    const dialogRef = mock<MatDialogRef<BaseRuleDryRunDialog, BaseRuleDryRunDialogResult>>();
    const evaluator = mock<BaseRuleEvaluatorService>();
    const defaultResult: RuleEvaluationResult = {
      ruleId: testRule.id,
      ruleName: testRule.name,
      passed: true,
      severity: testRule.severity,
    };
    evaluator.evaluate.mockReturnValue(opts.evaluationResult ?? defaultResult);

    const data: BaseRuleDryRunDialogData = { rule: testRule, ...opts.data };

    TestBed.configureTestingModule({
      imports: [BaseRuleDryRunDialog, NoopAnimationsModule],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: BaseRuleEvaluatorService, useValue: evaluator },
      ],
    });

    const fixture = TestBed.createComponent(BaseRuleDryRunDialog);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, evaluator, dialogRef };
  }

  describe('constructor entityJson prefill', () => {
    it("defaults entityJson to '{}' when no prefilledEntity is provided", () => {
      const { component } = setup();
      expect((component as unknown as { entityJson: string }).entityJson).toBe('{}');
    });

    it('serializes the prefilled entity as pretty-printed JSON', () => {
      const { component } = setup({ data: { prefilledEntity: { qty: 3, nested: { id: 'x' } } } });
      const json = (component as unknown as { entityJson: string }).entityJson;
      expect(JSON.parse(json)).toEqual({ qty: 3, nested: { id: 'x' } });
      expect(json).toContain('\n');
    });

    it("serializes prefilledEntity === null (not the default '{}')", () => {
      const { component } = setup({ data: { prefilledEntity: null } });
      expect((component as unknown as { entityJson: string }).entityJson).toBe('null');
    });
  });

  describe('onRun()', () => {
    it('parses entityJson and passes the result to the evaluator', () => {
      const { component, evaluator } = setup();
      (component as unknown as { entityJson: string }).entityJson = '{"qty": 5}';

      component.onRun();

      expect(evaluator.evaluate).toHaveBeenCalledWith({ qty: 5 }, testRule);
    });

    it('stores the evaluator result on the result signal when JSON is valid', () => {
      const evaluationResult: RuleEvaluationResult = { ruleId: 'r1', ruleName: testRule.name, passed: false, severity: Severity.WARNING, message: 'Nope' };
      const { component } = setup({ evaluationResult });
      (component as unknown as { entityJson: string }).entityJson = '{}';

      component.onRun();

      const result = (component as unknown as { result: () => RuleEvaluationResult | undefined }).result();
      expect(result).toEqual(evaluationResult);
    });

    it('records a JSON-parse error on the signal and does not call the evaluator', () => {
      const { component, evaluator } = setup();
      (component as unknown as { entityJson: string }).entityJson = '{not-json';

      component.onRun();

      expect(evaluator.evaluate).not.toHaveBeenCalled();
      const result = (component as unknown as { result: () => RuleEvaluationResult | undefined }).result();
      expect(result).toMatchObject({
        ruleId: testRule.id,
        ruleName: testRule.name,
        passed: false,
        severity: testRule.severity,
      });
      expect(result?.error).toMatch(/Invalid entity JSON:/);
    });

  });

  describe('onPick()', () => {
    it("closes the dialog with { action: 'pick' }", () => {
      const { component, dialogRef } = setup();

      component.onPick();

      expect(dialogRef.close).toHaveBeenCalledWith({ action: 'pick' });
    });
  });

  describe('template rendering', () => {
    it('shows a Pick button when the rule has a context', () => {
      const { fixture } = setup();
      const pickButton = fixture.nativeElement.querySelector('[data-testid="dry-run-pick-button"]');
      expect(pickButton).not.toBeNull();
    });

    it('omits the Pick button when the rule has no context', () => {
      const noContextRule = new BaseRule('r2', 'x', 'd', '', 'true', Severity.INFO);
      const { fixture } = setup({ data: { rule: noContextRule } });
      const pickButton = fixture.nativeElement.querySelector('[data-testid="dry-run-pick-button"]');
      expect(pickButton).toBeNull();
    });

    it('renders the passed message when result.passed is true', () => {
      const { fixture, component } = setup({
        evaluationResult: { ruleId: 'r1', ruleName: testRule.name, passed: true, severity: Severity.WARNING },
      });
      component.onRun();
      fixture.detectChanges();

      const passed = fixture.nativeElement.querySelector('.dry-run-passed');
      expect(passed).not.toBeNull();
      expect(passed?.textContent).toContain('Passed');
    });

    it('renders the violation message and severity when result.passed is false', () => {
      const { fixture, component } = setup({
        evaluationResult: { ruleId: 'r1', ruleName: testRule.name, passed: false, severity: Severity.WARNING, message: 'qty must be positive' },
      });
      component.onRun();
      fixture.detectChanges();

      const failed = fixture.nativeElement.querySelector('.dry-run-failed');
      expect(failed).not.toBeNull();
      expect(failed?.textContent).toContain('WARNING');
      expect(failed?.textContent).toContain('qty must be positive');
    });

    it('renders the error message when result carries an error (JSON parse failure)', () => {
      const { fixture, component } = setup();
      (component as unknown as { entityJson: string }).entityJson = '{not-json';
      component.onRun();
      fixture.detectChanges();

      const error = fixture.nativeElement.querySelector('.dry-run-error');
      expect(error).not.toBeNull();
      expect(error?.textContent).toMatch(/Invalid entity JSON:/);
    });
  });
});
