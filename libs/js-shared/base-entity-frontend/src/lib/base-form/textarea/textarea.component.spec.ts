import { signal } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NEVER } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { setupFormControlTest } from '../../../test-setup';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../../test-entity';
import { AI_ASSIST_CLIENT, AiAssistClient } from '../ai-assist/ai-assist';
import { TextareaComponent } from './textarea.component';

describe('TextareaComponent', () => {
  const textareaConfig = new BaseEntityAttrDescriptor('projectFeedback', FormControlType.TEXTAREA, 'Project feedback', undefined, false, { inputType: 'text' });
  textareaConfig.disabled = false;
  textareaConfig.placeholder = 'Enter project feedback';
  textareaConfig.styleClass = 'col-md-6 mt-3';
  textareaConfig.lines = 5;
  textareaConfig.visible = true;
  const testEntity: TestEntity = new TestEntity('1', 'testEntity', 'This project is developed in Angular v14');

  it('should create', async () => {
    const { containerComponent, component } = await setupFormControlTest(TextareaComponent, textareaConfig, testEntity);
    expect(containerComponent).toBeTruthy();
    expect(component).toBeTruthy();
  });

  it('shows no AI assist button when the attribute does not ask for one', async () => {
    const { fixture } = await setupFormControlTest(TextareaComponent, textareaConfig, testEntity);

    expect(fixture.nativeElement.querySelector('base-ai-assist-button')).toBeNull();
  });

  describe('with an AI assist', () => {
    const expression = new BaseEntityAttrDescriptor('expression', FormControlType.TEXTAREA, 'Expression');
    expression.aiAssist = { task: 'rule.expression', from: 'description', parameters: ['context', 'severity'] };
    const run = vi.fn();
    const client = { isAvailable: () => signal(true), run } as unknown as AiAssistClient;
    // Returns what the real snackbar returns, so the button can subscribe to the undo action.
    const snackBar = { open: vi.fn(() => ({ onAction: () => NEVER })) };

    async function setUp(values: Record<string, string>) {
      const setup = await setupFormControlTest(TextareaComponent, expression, new TestEntity(), [
        { provide: AI_ASSIST_CLIENT, useValue: client },
        { provide: MatSnackBar, useValue: snackBar },
      ]);
      const form = (setup.component as TextareaComponent<TestEntity>).formGroup;
      Object.entries(values).forEach(([name, value]) => form.addControl(name, new FormControl(value)));
      setup.fixture.detectChanges();
      return { ...setup, form };
    }

    const button = (fixture: { nativeElement: HTMLElement }) => fixture.nativeElement.querySelector<HTMLButtonElement>('[data-testid="ai-assist-button"]');

    it('shows the button, disabled until the description attribute has text', async () => {
      const { fixture, form } = await setUp({ description: '', context: 'Order' });
      expect(button(fixture)?.disabled).toBe(true);

      form.get('description')?.setValue('The total must be positive');
      fixture.detectChanges();
      expect(button(fixture)?.disabled).toBe(false);
    });

    it("asks with the description, the named parameters that have a value, and the field's current text, and fills the field", async () => {
      run.mockResolvedValue({ candidate: 'entity.total > 0', explanation: '', assumptions: [], issues: [], rounds: 1, model: 'pp' });
      const { fixture, form } = await setUp({ description: 'The total must be positive', context: 'Order', severity: ' ' });
      form.get('expression')?.setValue('old');
      fixture.detectChanges();

      button(fixture)?.click();
      await fixture.whenStable();

      expect(run).toHaveBeenCalledWith('rule.expression', { text: 'The total must be positive', parameters: { context: 'Order' }, current: 'old' });
      expect(form.get('expression')?.value).toBe('entity.total > 0');
      expect(form.get('expression')?.dirty).toBe(true);
    });
  });
});
