import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { CurrentUserContext } from '../../domain/dashboard/current-user.context';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';
import { WORKFLOW_INSTANCE_DTO } from '../../domain/execution/test-workflow-instance';
import { TaskListComponent } from './task-list.component';
import { APPROVE_SHIPMENT_TASK_ID, DASHBOARD_SERVICE_ROOT, flushDashboardCatalogs, provideDashboardTesting, required, REVIEW_ORDER_TASK_ID, RUN_ID } from './test-dashboard';

describe('TaskListComponent', () => {
  let fixture: ComponentFixture<TaskListComponent>;
  let store: InstanceType<typeof WorkflowDashboardStore>;
  let session: CurrentUserContext;
  let controller: HttpTestingController;

  const render = (): HTMLElement => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [TaskListComponent], providers: [provideDashboardTesting()] }).compileComponents();
    session = TestBed.inject(CurrentUserContext);
    store = TestBed.inject(WorkflowDashboardStore);
    controller = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TaskListComponent);
    flushDashboardCatalogs(controller);
  });

  it('shows the name, the status and the run of each row of the open queue', () => {
    session.set({ userId: 'clerk-user' });
    const host = render();

    const row = required<HTMLElement>(host, `[data-testid="task-row-${REVIEW_ORDER_TASK_ID}"]`);
    expect(row.querySelector('.row__name')?.textContent?.trim()).toBe('Review Order');
    expect(row.querySelector('[data-testid="task-status-COMPLETED"]')).not.toBeNull();
    expect(row.querySelector('.row__workflow')?.textContent?.trim()).toBe('Order Fulfillment Workflow');
  });

  it('opens a task when its row is activated', () => {
    session.set({ userId: 'clerk-user' });
    const host = render();

    required<HTMLButtonElement>(host, `[data-testid="task-row-${REVIEW_ORDER_TASK_ID}"]`).click();

    expect(store.selectedInstanceId()).toBe(RUN_ID);
    expect(store.selectedTaskId()).toBe(REVIEW_ORDER_TASK_ID);
  });

  it('marks the open row', () => {
    session.set({ userId: 'clerk-user' });
    store.select(RUN_ID, REVIEW_ORDER_TASK_ID);

    expect(render().querySelector(`[data-testid="task-row-${REVIEW_ORDER_TASK_ID}"]`)?.getAttribute('aria-current')).toBe('true');
  });

  /**
   * The cheapest useful field on the screen: `blockedReason` is already on the object, so a blocked task
   * explains itself with no second call. Behind a hover it would hide the only thing the user needs.
   */
  it('shows why a blocked task is blocked, inline', () => {
    store.setScope('process');
    store.selectInstance(RUN_ID);
    store.reload();
    controller
      .expectOne(`${DASHBOARD_SERVICE_ROOT}/instances`)
      .flush({ content: [{ ...WORKFLOW_INSTANCE_DTO, tasks: [{ ...WORKFLOW_INSTANCE_DTO.tasks[2], status: 'BLOCKED', blockedReason: 'positive-quantities: line item 3 has quantity 0' }] }] });

    expect(render().querySelector('[data-testid^="task-blocked-"]')?.textContent?.trim()).toBe('positive-quantities: line item 3 has quantity 0');
  });

  describe('claiming', () => {
    beforeEach(() => {
      store.setScope('team');
      store.reload();
      controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/instances`).flush({ content: [{ ...WORKFLOW_INSTANCE_DTO, tasks: [{ ...WORKFLOW_INSTANCE_DTO.tasks[1], assignedTo: undefined }] }] });
    });

    // On the row, not only in the detail pane: the Team queue is a list of things to pick up, and making
    // the user open each one to take it turns one gesture into three.
    it('offers a claim button on an unassigned active row', () => {
      expect(render().querySelector(`[data-testid="claim-task-${APPROVE_SHIPMENT_TASK_ID}"]`)).not.toBeNull();
    });

    it('claims without also opening the row', () => {
      session.set({ userId: 'manager-user' });
      const host = render();

      required<HTMLButtonElement>(host, `[data-testid="claim-task-${APPROVE_SHIPMENT_TASK_ID}"]`).click();

      expect(controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/instances/${RUN_ID}/tasks/approve-shipment/assign`).request.body).toEqual({ userId: 'manager-user' });
      expect(store.selectedTaskId()).toBeUndefined();
    });

    // Claimable means unassigned *and* ACTIVE. The Team queue only ever holds those, so this is asserted
    // from the Process scope, which shows a run's rows whatever state they are in.
    it('withholds the claim button from a row somebody already holds', () => {
      store.setScope('process');
      store.selectInstance(RUN_ID);
      store.reload();
      controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/instances`).flush({ content: [{ ...WORKFLOW_INSTANCE_DTO, tasks: [WORKFLOW_INSTANCE_DTO.tasks[1]] }] });

      const host = render();
      expect(host.querySelector(`[data-testid="task-row-${APPROVE_SHIPMENT_TASK_ID}"]`)).not.toBeNull();
      expect(host.querySelector(`[data-testid="claim-task-${APPROVE_SHIPMENT_TASK_ID}"]`)).toBeNull();
    });

    // A PENDING task has not been activated by the engine, so it is open but not claimable.
    it('withholds it from an unassigned task that is not yet active', () => {
      store.setScope('process');
      store.selectInstance(RUN_ID);
      store.reload();
      controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/instances`).flush({ content: [{ ...WORKFLOW_INSTANCE_DTO, tasks: [WORKFLOW_INSTANCE_DTO.tasks[2]] }] });

      expect(render().querySelector('[data-testid^="claim-task-"]')).toBeNull();
    });
  });

  describe('the empty and failed states', () => {
    // A different sentence per scope, because "nothing assigned to you" and "nothing to claim" are
    // different facts about the same empty list.
    it.each([
      ['mine', 'Nothing assigned to you.'],
      ['team', 'Nothing to claim right now.'],
      ['process', 'This run has no tasks.'],
    ] as const)('says what an empty %s queue means', (scope, sentence) => {
      store.setScope(scope);

      expect(render().querySelector('[data-testid="task-list-empty"]')?.textContent?.trim()).toBe(sentence);
    });

    it('reports a failed read instead of an empty queue', () => {
      store.reload();
      controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/instances`).flush({ errorId: 'boom' }, { status: 500, statusText: 'Server Error' });

      const host = render();
      expect(host.querySelector('[data-testid="task-list-error"]')?.textContent?.trim()).toBe('The runs could not be loaded.');
      expect(host.querySelector('[data-testid="task-list-empty"]')).toBeNull();
    });
  });
});
