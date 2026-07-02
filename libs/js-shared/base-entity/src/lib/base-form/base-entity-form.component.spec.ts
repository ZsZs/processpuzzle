import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TestEntity } from '../test-entity';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { RULE_ENGINE, RuleEngine, RuleEvaluationResult } from '../rule-engine/rule-engine';
import { RuleViolationsSingletonStore } from '../rule-engine/rule-violations.store';
import { setupFormComponentTest } from '../../test-setup';
import { describe, expect, it, vi } from 'vitest';

describe('GenericEntityFormComponent', () => {
  const labelConfig = new BaseEntityAttrDescriptor('description', FormControlType.LABEL);
  const checkboxConfig: BaseEntityAttrDescriptor = new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX, 'Is project approved from the business?', undefined, false);
  const testEntity: TestEntity = new TestEntity('1', 'test-entity', 'description of the entity', true);

  describe('sanity tests', () => {
    it('should create', async () => {
      const { component } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity);
      expect(component).toBeTruthy();
    });
  });

  describe('template structure contains:', () => {
    it('mat-card, mat-card-content, mat-card-actions', async () => {
      const { fixture } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity);
      const matCard = fixture.debugElement.query(By.css('mat-card')).nativeElement;
      expect(matCard).toBeTruthy();

      const matCardContent = fixture.debugElement.query(By.css('mat-card-content')).nativeElement;
      expect(matCardContent).toBeTruthy();

      const matCardActions = fixture.debugElement.query(By.css('mat-card-actions')).nativeElement;
      expect(matCardActions).toBeTruthy();
    });

    it('form and the form controls', async () => {
      const { fixture } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity);
      const form = fixture.debugElement.query(By.css('form')).nativeElement;
      expect(form).toBeTruthy();

      const checkbox = fixture.debugElement.query(By.css('form > base-checkbox')).nativeElement;
      expect(checkbox).toBeTruthy();

      const label = fixture.debugElement.query(By.css('form > base-label')).nativeElement;
      expect(label).toBeTruthy();
    });
  });

  describe('form actions:', () => {
    it('onCancel()', async () => {
      // SETUP:
      const { fixture, component } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity);
      vi.spyOn(component, 'onCancel');
      const cancelButton = fixture.debugElement.query(By.css('mat-card-actions > button#cancel')).nativeElement;

      // EXERCISE:
      cancelButton.click();

      // VERIFY:
      expect(cancelButton).toBeTruthy();
      expect(component.onCancel).toHaveBeenCalled();
    });

    it('onSubmit(), when its an existing object updates it in store.', async () => {
      // SETUP:
      const { fixture, component, store, formNavigator } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity);
      fixture.detectChanges();
      await fixture.whenStable();
      const checkbox = fixture.debugElement.query(By.css('form input[type=checkbox]')).nativeElement;
      checkbox.click(); // trigger form changes (dirty)
      checkbox.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      const submitButton = fixture.debugElement.query(By.css('mat-card-actions > button#submit')).nativeElement;
      vi.spyOn(component, 'onSubmit');
      vi.spyOn(store, 'update');
      vi.spyOn(store, 'setCurrentEntity');
      vi.spyOn(formNavigator, 'navigateBack');

      // EXERCISE:
      submitButton.click();
      await new Promise((resolve) => setTimeout(resolve, 0));
      await fixture.whenStable();

      // VERIFY:
      expect(component.onSubmit).toHaveBeenCalled();
      expect(store.update).toHaveBeenCalledWith({ ...testEntity, ...component.baseEntityForm.value });
      expect(store.setCurrentEntity).toHaveBeenCalledWith(undefined);
      expect(formNavigator.navigateBack).toHaveBeenCalled();
    });

    it('onSubmit(), when its new object saves it in store.', async () => {
      // SETUP:
      const { fixture, component, store, formNavigator } = await setupFormComponentTest([labelConfig, checkboxConfig], undefined, true);
      TestBed.flushEffects();
      fixture.detectChanges();
      await fixture.whenStable();
      const checkbox = fixture.debugElement.query(By.css('form input[type=checkbox]')).nativeElement;
      checkbox.click(); // trigger form changes (dirty)
      component.baseEntityForm.get('description')?.setValue('hello world');
      checkbox.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      const submitButton = fixture.debugElement.query(By.css('mat-card-actions > button#submit')).nativeElement;
      vi.spyOn(component, 'onSubmit');
      vi.spyOn(store, 'add');
      vi.spyOn(store, 'setCurrentEntity');
      vi.spyOn(formNavigator, 'navigateBack');

      // EXERCISE:
      submitButton.click();
      await new Promise((resolve) => setTimeout(resolve, 0));
      await fixture.whenStable();

      // VERIFY:
      expect(component.onSubmit).toHaveBeenCalled();
      expect(store.add).toHaveBeenCalledWith({ ...component.entity(), ...component.baseEntityForm.value });
      expect(store.setCurrentEntity).toHaveBeenCalledWith(undefined);
      expect(formNavigator.navigateBack).toHaveBeenCalled();
    });

    it('onDelete() deletes the current entity and navigates back', async () => {
      const { component, store, formNavigator } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity);
      const deleteSpy = vi.spyOn(store, 'delete').mockResolvedValue(undefined);
      const navSpy = vi.spyOn(formNavigator, 'navigateBack').mockResolvedValue(undefined);

      await component.onDelete();

      expect(deleteSpy).toHaveBeenCalledWith(testEntity.id);
      expect(navSpy).toHaveBeenCalled();
    });
  });

  describe('rule engine integration', () => {
    function makeRuleEngine(rules: Parameters<RuleEngine['evaluate']>[1][], evaluator?: (entity: unknown, rule: Parameters<RuleEngine['evaluate']>[1]) => RuleEvaluationResult): RuleEngine {
      return {
        loadRulesFor: vi.fn().mockReturnValue(of(rules)),
        evaluate: vi.fn().mockImplementation((entity, rule) => evaluator?.(entity, rule) ?? { ruleId: rule.id, passed: true, severity: rule.severity }),
      };
    }

    it('loadRules() subscribes on init and records the fetched rules', async () => {
      const ruleEngine = makeRuleEngine([{ id: 'r1', expression: 'true', severity: 'ERROR', fields: ['description'] }]);
      const { component } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity, false, [{ provide: RULE_ENGINE, useValue: ruleEngine }]);

      expect(ruleEngine.loadRulesFor).toHaveBeenCalledWith('TestEntity');
      expect(component['rules']).toHaveLength(1);
    });

    it('loadRules() logs a warning when the rule engine fails', async () => {
      const ruleEngine: RuleEngine = {
        loadRulesFor: vi.fn().mockReturnValue(throwError(() => new Error('boom'))),
        evaluate: vi.fn(),
      };
      const { component } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity, false, [{ provide: RULE_ENGINE, useValue: ruleEngine }]);
      const warnSpy = vi.spyOn(component['logger'], 'warn').mockImplementation(() => undefined);

      // trigger re-load by calling private loadRules manually
      component['loadRules']();

      expect(warnSpy).toHaveBeenCalled();
    });

    it('onFieldBlur() evaluates rules and stores failing violations on matching form controls', async () => {
      const violation: RuleEvaluationResult = { ruleId: 'r1', passed: false, severity: 'ERROR', message: 'Description required', fields: ['description'] };
      const ruleEngine = makeRuleEngine([{ id: 'r1', expression: 'x', severity: 'ERROR', fields: ['description'] }], () => violation);
      const { component, fixture } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity, false, [{ provide: RULE_ENGINE, useValue: ruleEngine }]);
      fixture.detectChanges();

      component.onFieldBlur();

      expect(component.violations()).toEqual([violation]);
      expect(component.hasErrorViolations()).toBe(true);
      const control = component.baseEntityForm.get('description');
      expect(control?.errors?.['ruleViolation']).toEqual({ message: 'Description required', severity: 'ERROR' });
      expect(control?.touched).toBe(true);
    });

    it('re-evaluating rules clears previously applied violations from unrelated controls', async () => {
      const failing: RuleEvaluationResult = { ruleId: 'r1', passed: false, severity: 'ERROR', message: 'msg', fields: ['description'] };
      const evaluate = vi.fn<RuleEngine['evaluate']>().mockReturnValueOnce(failing).mockReturnValueOnce({ ruleId: 'r1', passed: true, severity: 'ERROR' });
      const ruleEngine: RuleEngine = { loadRulesFor: vi.fn().mockReturnValue(of([{ id: 'r1', expression: 'x', severity: 'ERROR', fields: ['description'] }])), evaluate };
      const { component } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity, false, [{ provide: RULE_ENGINE, useValue: ruleEngine }]);

      component.onFieldBlur();
      expect(component.baseEntityForm.get('description')?.errors?.['ruleViolation']).toBeDefined();

      component.onFieldBlur();
      expect(component.baseEntityForm.get('description')?.errors).toBeNull();
      expect(component.violations()).toEqual([]);
    });

    it('onSubmit() blocks persistence when a rule violation is at ERROR severity', async () => {
      const violation: RuleEvaluationResult = { ruleId: 'r1', passed: false, severity: 'ERROR' };
      const ruleEngine = makeRuleEngine([{ id: 'r1', expression: 'x', severity: 'ERROR' }], () => violation);
      const { component, store, formNavigator } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity, false, [{ provide: RULE_ENGINE, useValue: ruleEngine }]);
      const updateSpy = vi.spyOn(store, 'update');
      const navSpy = vi.spyOn(formNavigator, 'navigateBack');

      await component.onSubmit();

      expect(updateSpy).not.toHaveBeenCalled();
      expect(navSpy).not.toHaveBeenCalled();
    });

    it('ngOnDestroy() clears violations and unsubscribes rule loading', async () => {
      const ruleEngine = makeRuleEngine([{ id: 'r1', expression: 'x', severity: 'ERROR', fields: ['description'] }]);
      const { component } = await setupFormComponentTest([labelConfig, checkboxConfig], testEntity, false, [{ provide: RULE_ENGINE, useValue: ruleEngine }]);
      const violationsStore = TestBed.inject(RuleViolationsSingletonStore);
      const clearSpy = vi.spyOn(violationsStore, 'clearViolations');
      const unsubscribeSpy = vi.spyOn(component['ruleSubscription']!, 'unsubscribe');

      component.ngOnDestroy();

      expect(clearSpy).toHaveBeenCalled();
      expect(unsubscribeSpy).toHaveBeenCalled();
    });
  });
});
