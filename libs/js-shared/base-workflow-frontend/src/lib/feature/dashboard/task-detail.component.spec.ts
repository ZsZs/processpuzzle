import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { CurrentUserContext, PROCESS_OWNER_ROLE } from '../../domain/dashboard/current-user.context';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';
import { TaskDetailComponent } from './task-detail.component';
import {
  APPROVE_SHIPMENT_TASK_ID,
  CONFIRM_DELIVERY_TASK_ID,
  DASHBOARD_SERVICE_ROOT,
  flushDashboardCatalogs,
  provideDashboardTesting,
  required,
  REVIEW_ORDER_TASK_ID,
  RUN_ID,
} from './test-dashboard';

describe('TaskDetailComponent', () => {
  let fixture: ComponentFixture<TaskDetailComponent>;
  let store: InstanceType<typeof WorkflowDashboardStore>;
  let session: CurrentUserContext;
  let controller: HttpTestingController;

  const render = (): HTMLElement => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [TaskDetailComponent], providers: [provideDashboardTesting()] }).compileComponents();
    session = TestBed.inject(CurrentUserContext);
    store = TestBed.inject(WorkflowDashboardStore);
    controller = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TaskDetailComponent);
    flushDashboardCatalogs(controller);
  });

  it('prompts for a selection until a task is open', () => {
    expect(render().querySelector('[data-testid="task-detail-empty"]')?.textContent?.trim()).toBe('Select a task from the list.');
  });

  describe('a task somebody holds', () => {
    beforeEach(() => {
      session.set({ userId: 'manager-user' });
      store.select(RUN_ID, APPROVE_SHIPMENT_TASK_ID);
    });

    it('names the task, its status, its run and its assignee', () => {
      const host = render();

      expect(host.querySelector('[data-testid="task-detail-name"]')?.textContent?.trim()).toBe('Approve Shipment');
      expect(host.querySelector('[data-testid="task-status-ACTIVE"]')).not.toBeNull();
      expect(host.querySelector('[data-testid="task-detail-workflow"]')?.textContent?.trim()).toBe('Order Fulfillment Workflow');
      expect(host.querySelector('[data-testid="task-detail-assignee"]')?.textContent).toContain('manager-user');
    });

    // The three sections of screen 2: how to do it, what it touches, how to finish it.
    it('shows the steps, the artifacts and the completion form', () => {
      const host = render();

      expect(host.querySelector('pp-step-checklist')).not.toBeNull();
      expect(host.querySelector('pp-artifact-panel')).not.toBeNull();
      expect(host.querySelector('pp-completion-form')).not.toBeNull();
    });

    it('completes the task through the run that owns it', () => {
      required<HTMLButtonElement>(render(), '[data-testid="complete-task"]').click();

      controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/instances/${RUN_ID}/tasks/approve-shipment/complete`).flush({ accepted: true });
    });

    // Skip is a manager override, not a peer of Complete.
    it('offers Skip only to a stated process owner', () => {
      expect(render().querySelector('[data-testid="skip-task"]')).toBeNull();

      session.set({ userId: 'manager-user', roles: [PROCESS_OWNER_ROLE] });

      expect(render().querySelector('[data-testid="skip-task"]')).not.toBeNull();
    });

    // Awaited, because the verb is a promise: the flush answers the request synchronously but the store
    // patches `actionError` in the microtask that follows, so a render before that shows the old state.
    it('surfaces a failed verb', async () => {
      required<HTMLButtonElement>(render(), '[data-testid="complete-task"]').click();
      controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/instances/${RUN_ID}/tasks/approve-shipment/complete`).flush({ errorId: 'task.not.active' }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();

      expect(render().querySelector('[data-testid="task-detail-error"]')).not.toBeNull();
    });
  });

  /**
   * The state that precedes all three sections. Offering a form whose submission the backend would refuse
   * is worse than offering nothing, so an unheld task shows its name and a Claim button and nothing else.
   */
  describe('a task nobody holds', () => {
    beforeEach(() => store.select(RUN_ID, CONFIRM_DELIVERY_TASK_ID));

    it('is locked, with a claim button and no sections', () => {
      const host = render();

      expect(host.querySelector('[data-testid="task-detail-locked"]')).not.toBeNull();
      expect(host.querySelector('pp-step-checklist')).toBeNull();
      expect(host.querySelector('pp-completion-form')).toBeNull();
      expect(host.querySelector('[data-testid="task-detail-unassigned"]')?.textContent?.trim()).toBe('unassigned');
    });

    it('claims it for the signed-in user', () => {
      session.set({ userId: 'clerk-user' });

      required<HTMLButtonElement>(render(), '[data-testid="task-detail-claim"]').click();

      expect(controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/instances/${RUN_ID}/tasks/confirm-delivery/assign`).request.body).toEqual({ userId: 'clerk-user' });
    });
  });

  /**
   * Read-only rather than hidden: seeing what a colleague is doing is not the same as being invited to do
   * it. The steps show, the completion form does not.
   */
  describe('a task somebody else holds', () => {
    beforeEach(() => {
      session.set({ userId: 'somebody-else' });
      store.select(RUN_ID, APPROVE_SHIPMENT_TASK_ID);
    });

    it('shows the sections without the completion form', () => {
      const host = render();

      expect(host.querySelector('pp-step-checklist')).not.toBeNull();
      expect(host.querySelector('pp-artifact-panel')).not.toBeNull();
      expect(host.querySelector('pp-completion-form')).toBeNull();
    });

    // The rows themselves still render — that is what "read-only rather than hidden" means. Whether a user
    // step's checkbox is disabled is StepChecklistComponent's own business and is asserted in its spec;
    // asserting it here would only prove that these fixtures declare no user step.
    it('still renders the steps of a task it will not let you finish', () => {
      store.select(RUN_ID, REVIEW_ORDER_TASK_ID);

      expect(render().querySelector('[data-testid="step-check-items"]')).not.toBeNull();
    });
  });

  // A PENDING task has not been activated and a COMPLETED one is finished; `complete` answers 409 for both,
  // so the button is absent rather than present and doomed.
  it('offers no completion form for a task that is not ACTIVE', () => {
    session.set({ userId: 'clerk-user' });
    store.select(RUN_ID, REVIEW_ORDER_TASK_ID);

    const host = render();
    expect(host.querySelector('[data-testid="task-status-COMPLETED"]')).not.toBeNull();
    expect(host.querySelector('pp-completion-form')).toBeNull();
  });
});
