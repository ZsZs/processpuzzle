import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { TaskInstanceStatus } from '../../domain/execution/workflow-instance';
import { TaskStatusBadgeComponent } from './task-status-badge.component';
import { required } from './test-dashboard';

describe('TaskStatusBadgeComponent', () => {
  let fixture: ComponentFixture<TaskStatusBadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [TaskStatusBadgeComponent] }).compileComponents();
    fixture = TestBed.createComponent(TaskStatusBadgeComponent);
  });

  const badge = (status: TaskInstanceStatus | undefined): HTMLElement => {
    fixture.componentRef.setInput('status', status);
    fixture.detectChanges();
    return required<HTMLElement>(fixture.nativeElement as HTMLElement, '.badge');
  };

  /**
   * Untranslated, on purpose: nothing in this workspace translates an enum value, so the generated Task
   * Instance list one route away shows `ACTIVE` as `ACTIVE`. A badge reading "Aktiv" would make the two
   * screens look like they were reading different fields.
   */
  it.each(Object.values(TaskInstanceStatus))('shows %s as the contract spells it', (status) => {
    expect(badge(status).textContent?.trim()).toBe(status);
  });

  // Coloring is keyed off the attribute rather than five classes, and the attribute is what a spec and a
  // stylesheet can both address.
  it('carries the status as an attribute, so the styling has something to key off', () => {
    expect(badge(TaskInstanceStatus.BLOCKED).dataset['status']).toBe('BLOCKED');
  });

  // `TaskInstance.status` is optional in the contract, so a row can arrive without one.
  it('shows a dash for a task with no status', () => {
    expect(badge(undefined).textContent?.trim()).toBe('—');
    expect(badge(undefined).dataset['status']).toBeUndefined();
  });
});
