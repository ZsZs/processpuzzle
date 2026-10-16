import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
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
        provideRouter([]),
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

  describe('the checkboxes', () => {
    const check = (host: HTMLElement, stepId: string): void => {
      required<HTMLInputElement>(host, `[data-testid="step-check-${stepId}"]`).dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };

    const toggled = (): string[] => {
      const emitted: string[] = [];
      fixture.componentInstance.stepToggled.subscribe((stepId) => emitted.push(stepId));
      return emitted;
    };

    // The ticks are the host's, because the host gates Complete on them.
    it('shows the ticks it is given', () => {
      fixture.componentRef.setInput('checkedIds', new Set(['read-order']));
      const host = render([userStep]);

      expect(required<HTMLInputElement>(host, '[data-testid="step-check-read-order"]').checked).toBe(true);
    });

    it('reports a tick rather than keeping it', () => {
      const emitted = toggled();
      const host = render([userStep]);

      check(host, 'read-order');

      expect(emitted).toEqual(['read-order']);
    });

    // A task somebody else holds is shown, not offered: the checkboxes are a preview of their work.
    it('refuses a tick while the task is not the session’s own', () => {
      const emitted = toggled();
      const host = render([userStep], [], true);

      expect(required<HTMLInputElement>(host, '[data-testid="step-check-read-order"]').disabled).toBe(true);
      check(host, 'read-order');
      expect(emitted).toEqual([]);
    });
  });

  describe('a service step’s tool', () => {
    it('is a link when the host mounts the tool catalog', () => {
      fixture.componentRef.setInput(
        'tools',
        new Map([['automated-check-tool', { toolDefinitionId: 'automated-check-tool', name: 'Automated Check Tool', link: '/tool-definition/automated-check-tool/details' }]]),
      );
      const host = render([serviceStep]);

      const tool = required<HTMLAnchorElement>(host, '[data-testid="step-tool-check-items"]');
      expect(tool.tagName).toBe('A');
      expect(tool.getAttribute('href')).toBe('/tool-definition/automated-check-tool/details');
      expect(tool.textContent?.trim()).toBe('Automated Check Tool');
    });

    it('is plain text when nothing is mounted for it', () => {
      fixture.componentRef.setInput('tools', new Map([['automated-check-tool', { toolDefinitionId: 'automated-check-tool', name: 'Automated Check Tool', link: undefined }]]));
      const host = render([serviceStep]);

      expect(required(host, '[data-testid="step-tool-check-items"]').tagName).toBe('SPAN');
    });
  });

  // A task with no steps is a legitimate shape — `OTHER_TASK_DEFINITION_DTO` is exactly that.
  it('says so when the task declares no steps', () => {
    const host = render([]);

    expect(host.querySelector('[data-testid="steps-empty"]')?.textContent?.trim()).toBe('This task declares no steps.');
  });
});
