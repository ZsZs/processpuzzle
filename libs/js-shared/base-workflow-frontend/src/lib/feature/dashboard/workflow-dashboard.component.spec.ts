import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { CurrentUserContext } from '../../domain/dashboard/current-user.context';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';
import { WorkflowDashboardComponent } from './workflow-dashboard.component';
import { flushDashboardCatalogs, provideDashboardTesting, required, REVIEW_ORDER_TASK_ID, RUN_ID } from './test-dashboard';

describe('WorkflowDashboardComponent', () => {
  let fixture: ComponentFixture<WorkflowDashboardComponent>;
  let store: InstanceType<typeof WorkflowDashboardStore>;
  let session: CurrentUserContext;
  let controller: HttpTestingController;

  const render = (): HTMLElement => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };
  const click = (testId: string): void => {
    required<HTMLButtonElement>(render(), `[data-testid="${testId}"]`).click();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [WorkflowDashboardComponent], providers: [provideDashboardTesting()] }).compileComponents();
    session = TestBed.inject(CurrentUserContext);
    store = TestBed.inject(WorkflowDashboardStore);
    controller = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WorkflowDashboardComponent);
    flushDashboardCatalogs(controller);
  });

  it('offers the three queues, My tasks first', () => {
    const scopes = Array.from(render().querySelectorAll<HTMLElement>('[data-testid^="dashboard-scope-"]'));

    expect(scopes.map((scope) => scope.dataset['testid'])).toEqual(['dashboard-scope-mine', 'dashboard-scope-team', 'dashboard-scope-process']);
    expect(scopes[0].getAttribute('aria-selected')).toBe('true');
  });

  it('switches the queue', () => {
    click('dashboard-scope-team');

    expect(store.scope()).toBe('team');
    expect(render().querySelector('[data-testid="dashboard-scope-team"]')?.getAttribute('aria-selected')).toBe('true');
  });

  /**
   * Counted for every scope rather than only the open one, which is the point: a user glances at "Team 3"
   * and decides whether to look, instead of switching to find out.
   */
  it('counts every queue, not only the open one', () => {
    session.set({ userId: 'clerk-user' });

    expect(render().querySelector('[data-testid="dashboard-scope-mine"] .scope__count')?.textContent?.trim()).toBe('1');
  });

  describe('layout', () => {
    it('shows the queue beside the detail for a personal scope', () => {
      const host = render();

      expect(host.querySelector('pp-task-list')).not.toBeNull();
      expect(host.querySelector('pp-process-board')).toBeNull();
      expect(host.querySelector('.dashboard__body')?.classList.contains('dashboard__body--stacked')).toBe(false);
    });

    // A four-column board squeezed into a third of the width is four unreadable columns, so it takes the
    // row and the detail moves below it.
    it('replaces the queue with the board and stacks the detail for the process scope', () => {
      click('dashboard-scope-process');
      const host = render();

      expect(host.querySelector('pp-process-board')).not.toBeNull();
      expect(host.querySelector('pp-task-list')).toBeNull();
      expect(host.querySelector('.dashboard__body')?.classList.contains('dashboard__body--stacked')).toBe(true);
    });

    it('keeps the detail pane in both layouts', () => {
      expect(render().querySelector('pp-task-detail')).not.toBeNull();

      click('dashboard-scope-process');

      expect(render().querySelector('pp-task-detail')).not.toBeNull();
    });
  });

  describe('the run picker', () => {
    it('appears only for the process scope', () => {
      expect(render().querySelector('[data-testid="dashboard-run"]')).toBeNull();

      click('dashboard-scope-process');

      expect(render().querySelector('[data-testid="dashboard-run"]')).not.toBeNull();
    });

    it('points the board at the run that was picked', () => {
      click('dashboard-scope-process');
      const picker = required<HTMLSelectElement>(render(), '[data-testid="dashboard-run"]');

      picker.value = RUN_ID;
      picker.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(store.selectedInstanceId()).toBe(RUN_ID);
    });

    it('clears the run when the blank option is picked', () => {
      store.selectInstance(RUN_ID);
      click('dashboard-scope-process');
      const picker = required<HTMLSelectElement>(render(), '[data-testid="dashboard-run"]');

      picker.value = '';
      picker.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(store.selectedInstanceId()).toBeUndefined();
    });
  });

  // A user who was working on something and glanced at the team queue has not stopped working on it.
  it('keeps the open task across a scope switch', () => {
    session.set({ userId: 'clerk-user' });
    store.select(RUN_ID, REVIEW_ORDER_TASK_ID);

    click('dashboard-scope-team');

    expect(store.selectedTaskId()).toBe(REVIEW_ORDER_TASK_ID);
  });
});
