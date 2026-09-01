import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { StepDefinition, TaskStepType } from '../../domain/definition/task-definition';
import { StepResult } from '../../domain/execution/workflow-instance';
import { StepChecklistComponent } from './step-checklist.component';
import { required } from './test-dashboard';

describe('StepChecklistComponent', () => {
  const userStep = new StepDefinition({ id: 'read-order', name: 'Read the order', description: 'Check the customer’s note.', stepType: TaskStepType.USER_STEP });
  const serviceStep = new StepDefinition({ id: 'check-items', name: 'Check Line Items', stepType: TaskStepType.SERVICE_STEP, toolDefinitionId: 'automated-check-tool' });

  let fixture: ComponentFixture<StepChecklistComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepChecklistComponent],
      providers: [
        provideTranslocoTesting({
          translations: {
            en: {
              'base_workflow.task_instance.dashboard.step_user': 'user step',
              'base_workflow.task_instance.dashboard.step_service': 'service step',
              'base_workflow.task_instance.dashboard.steps_none': 'This task declares no steps.',
            },
          },
        }),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(StepChecklistComponent);
  });

  const render = (steps: StepDefinition[], stepResults: StepResult[] = [], readOnly = false): HTMLElement => {
    fixture.componentRef.setInput('steps', steps);
    fixture.componentRef.setInput('stepResults', stepResults);
    fixture.componentRef.setInput('readOnly', readOnly);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  // The distinction the whole component exists for: a user step is something to do, a service step is
  // something the engine did.
  it('gives a user step a checkbox and a service step a status glyph', () => {
    const host = render([userStep, serviceStep]);

    expect(host.querySelector('[data-testid="step-check-read-order"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="step-check-check-items"]')).toBeNull();
    expect(host.querySelector('[data-testid="step-check-items"] .step__icon')).not.toBeNull();
  });

  it('shows the guidance a user step carries', () => {
    const host = render([userStep]);

    expect(host.querySelector('.step__description')?.textContent?.trim()).toBe('Check the customer’s note.');
  });

  describe('a service step’s outcome', () => {
    it('is waiting until the engine has called the tool', () => {
      const host = render([serviceStep]);

      expect(host.querySelector('[data-testid="step-check-items"] .step__icon')?.getAttribute('data-state')).toBe('waiting');
    });

    it('is done once a result arrived without an error', () => {
      const host = render([serviceStep], [new StepResult({ stepId: 'check-items', completedAt: '2026-08-20T09:01:30Z', toolResponse: { available: 'true' } })]);

      expect(host.querySelector('[data-testid="step-check-items"] .step__icon')?.getAttribute('data-state')).toBe('done');
      expect(host.querySelector('[data-testid="step-response-check-items"]')?.textContent?.trim()).toBe('available: true');
    });

    /** The one thing on this screen a user cannot work around, so it is spelled out rather than summarized. */
    it('spells out a failed tool call', () => {
      const host = render([serviceStep], [new StepResult({ stepId: 'check-items', error: 'tool returned 503 after 3 retries' })]);

      expect(host.querySelector('[data-testid="step-check-items"] .step__icon')?.getAttribute('data-state')).toBe('failed');
      expect(host.querySelector('[data-testid="step-error-check-items"]')?.textContent?.trim()).toBe('tool returned 503 after 3 retries');
    });

    // An error and a response are alternatives: a failed call has nothing useful to summarize.
    it('shows the error instead of the response when both arrived', () => {
      const host = render([serviceStep], [new StepResult({ stepId: 'check-items', error: 'boom', toolResponse: { available: 'true' } })]);

      expect(host.querySelector('[data-testid="step-error-check-items"]')).not.toBeNull();
      expect(host.querySelector('[data-testid="step-response-check-items"]')).toBeNull();
    });
  });

  describe('the local checkbox state', () => {
    const check = (host: HTMLElement, stepId: string): void => {
      required<HTMLInputElement>(host, `[data-testid="step-check-${stepId}"]`).dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };

    it('ticks and unticks', () => {
      const host = render([userStep]);

      check(host, 'read-order');
      expect(required<HTMLInputElement>(host, '[data-testid="step-check-read-order"]').checked).toBe(true);

      check(host, 'read-order');
      expect(required<HTMLInputElement>(host, '[data-testid="step-check-read-order"]').checked).toBe(false);
    });

    /**
     * The reason it is a `linkedSignal`. Nothing persists a per-step acknowledgment (open-questions #5), so
     * the ticks are local — and local state that survived a task switch would show the previous task's
     * progress on this one's steps.
     */
    it('resets when a different task’s steps arrive', () => {
      const host = render([userStep]);
      check(host, 'read-order');

      render([new StepDefinition({ id: 'read-order', name: 'Read the order', stepType: TaskStepType.USER_STEP })]);

      expect(required<HTMLInputElement>(host, '[data-testid="step-check-read-order"]').checked).toBe(false);
    });

    // A task somebody else holds is shown, not offered: the checkboxes are a preview of their work.
    it('refuses a tick while the task is not the session’s own', () => {
      const host = render([userStep], [], true);

      expect(required<HTMLInputElement>(host, '[data-testid="step-check-read-order"]').disabled).toBe(true);
      check(host, 'read-order');
      expect(required<HTMLInputElement>(host, '[data-testid="step-check-read-order"]').checked).toBe(false);
    });
  });

  // A task with no steps is a legitimate shape — `OTHER_TASK_DEFINITION_DTO` is exactly that.
  it('says so when the task declares no steps', () => {
    const host = render([]);

    expect(host.querySelector('[data-testid="steps-empty"]')?.textContent?.trim()).toBe('This task declares no steps.');
  });
});
