import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UNSTATED_REFUSAL } from '../../domain/dashboard/workflow-dashboard.store';
import { PropertyMap } from '../../domain/property-map';
import { CompletionFormComponent } from './completion-form.component';
import { required } from './test-dashboard';

describe('CompletionFormComponent', () => {
  let fixture: ComponentFixture<CompletionFormComponent>;
  let component: CompletionFormComponent;
  let completed: PropertyMap | undefined | 'never';
  let skipped: string | undefined | 'never';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CompletionFormComponent],
      providers: [
        provideTranslocoTesting({
          translations: {
            en: {
              'base_workflow.task_instance.dashboard.note': 'Notes',
              'base_workflow.task_instance.dashboard.complete': 'Complete task',
              'base_workflow.task_instance.dashboard.skip': 'Skip',
              'base_workflow.task_instance.dashboard.skip_reason': 'Reason for skipping',
              'base_workflow.task_instance.dashboard.unstated_refusal': 'Not accepted yet, and the server gave no detail.',
            },
          },
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CompletionFormComponent);
    component = fixture.componentInstance;
    completed = 'never';
    skipped = 'never';
    component.completeRequested.subscribe((context) => (completed = context));
    component.skipRequested.subscribe((reason) => (skipped = reason));
    fixture.detectChanges();
  });

  const host = () => fixture.nativeElement as HTMLElement;
  const click = (testId: string): void => {
    required<HTMLButtonElement>(host(), `[data-testid="${testId}"]`).click();
    fixture.detectChanges();
  };
  const type = (testId: string, value: string): void => {
    const field = required<HTMLInputElement>(host(), `[data-testid="${testId}"]`);
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  describe('the context editor', () => {
    it('starts as one blank pair', () => {
      expect(host().querySelectorAll('.form__row')).toHaveLength(1);
    });

    // A trailing blank rather than an Add button, so a second pair costs no click.
    it('grows a row as soon as one is filled in', () => {
      type('context-key-0', 'reviewScore');

      expect(host().querySelectorAll('.form__row')).toHaveLength(2);
    });

    /**
     * The correction of the original sketch: `CompleteTaskRequest.context` is merged into the workflow
     * context before the postcondition runs, and a rule reads *named* variables. A single free-text note
     * would produce one key no rule ever references.
     */
    it('submits the pairs by the names the user gave them', () => {
      type('context-key-0', 'reviewScore');
      type('context-value-0', '7');

      click('complete-task');

      expect(completed).toEqual({ reviewScore: '7' });
    });

    it('drops a row whose key was left empty', () => {
      type('context-value-0', 'orphaned');

      click('complete-task');

      expect(completed).toBeUndefined();
    });

    // Nothing filled in means no body at all, not an empty map — merging nothing is not a merge.
    it('submits nothing when nothing was filled in', () => {
      click('complete-task');

      expect(completed).toBeUndefined();
    });

    it('trims the key but keeps the value verbatim', () => {
      type('context-key-0', '  reviewScore  ');
      type('context-value-0', '  7  ');

      click('complete-task');

      expect(completed).toEqual({ reviewScore: '  7  ' });
    });
  });

  describe('the skip override', () => {
    // Not a peer of Complete: the contract calls it a manager override, so it is absent rather than
    // disabled for everybody else.
    it('is absent unless the session may override', () => {
      expect(host().querySelector('[data-testid="skip-task"]')).toBeNull();

      fixture.componentRef.setInput('canSkip', true);
      fixture.detectChanges();

      expect(host().querySelector('[data-testid="skip-task"]')).not.toBeNull();
    });

    it('sends the reason it was given', () => {
      fixture.componentRef.setInput('canSkip', true);
      fixture.detectChanges();

      type('skip-reason', 'customer cancelled');
      click('skip-task');

      expect(skipped).toBe('customer cancelled');
    });

    // An override with no record of why is the kind of thing only ever discovered later — but the contract
    // makes the reason optional, so a blank one is `undefined` rather than an empty string.
    it('sends no reason rather than an empty one', () => {
      fixture.componentRef.setInput('canSkip', true);
      fixture.detectChanges();

      type('skip-reason', '   ');
      click('skip-task');

      expect(skipped).toBeUndefined();
    });
  });

  describe('a refused completion', () => {
    /** Inline, not a toast: the task stays ACTIVE and the user has to read the reason while fixing it. */
    it('is shown beside the fields, with the server’s own words', () => {
      fixture.componentRef.setInput('postconditionDetail', 'shipment-approved: not set');
      fixture.detectChanges();

      expect(host().querySelector('[data-testid="completion-refusal"]')?.textContent?.trim()).toBe('shipment-approved: not set');
    });

    // `postconditionDetail` is nullable, and an unchanged screen reads as a submission that vanished.
    it('falls back to a translated sentence when the server refused without saying why', () => {
      fixture.componentRef.setInput('postconditionDetail', UNSTATED_REFUSAL);
      fixture.detectChanges();

      expect(host().querySelector('[data-testid="completion-refusal"]')?.textContent?.trim()).toBe('Not accepted yet, and the server gave no detail.');
    });

    it('is absent until something was refused', () => {
      expect(host().querySelector('[data-testid="completion-refusal"]')).toBeNull();
    });
  });

  it('locks every control while a verb is in flight', () => {
    fixture.componentRef.setInput('canSkip', true);
    fixture.componentRef.setInput('isBusy', true);
    fixture.detectChanges();

    const disabled = Array.from(host().querySelectorAll<HTMLInputElement | HTMLButtonElement>('input, button')).map((control) => control.disabled);

    expect(disabled.every(Boolean)).toBe(true);
  });

  it('does not emit on its own', () => {
    const emit = vi.fn();
    component.completeRequested.subscribe(emit);
    fixture.detectChanges();

    expect(emit).not.toHaveBeenCalled();
  });
});
