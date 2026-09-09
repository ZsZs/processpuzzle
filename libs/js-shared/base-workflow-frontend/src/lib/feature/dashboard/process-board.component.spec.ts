import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';
import { WORKFLOW_INSTANCE_DTO } from '../../domain/execution/test-workflow-instance';
import { ProcessBoardComponent } from './process-board.component';
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

describe('ProcessBoardComponent', () => {
  let fixture: ComponentFixture<ProcessBoardComponent>;
  let store: InstanceType<typeof WorkflowDashboardStore>;
  let controller: HttpTestingController;

  const render = (): HTMLElement => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ProcessBoardComponent], providers: [provideDashboardTesting()] }).compileComponents();
    store = TestBed.inject(WorkflowDashboardStore);
    controller = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProcessBoardComponent);
    flushDashboardCatalogs(controller);
  });

  it('asks for a run until it has one', () => {
    expect(render().querySelector('[data-testid="board-no-run"]')?.textContent?.trim()).toBe('Pick a run to see its board.');
  });

  describe('with a run selected', () => {
    beforeEach(() => store.selectInstance(RUN_ID));

    // One header strip above the board, so the columns are read as belonging to a named run rather than to
    // "the workflow".
    it('names the run, the entity it is about, when it started and where it stands', () => {
      const host = render();

      expect(host.querySelector('[data-testid="board-workflow"]')?.textContent?.trim()).toBe('Order Fulfillment Workflow');
      expect(host.querySelector('[data-testid="board-entity"]')?.textContent?.trim()).toBe('1');
      expect(host.querySelector('[data-testid="board-started"]')?.textContent?.trim()).toBeTruthy();
      expect(host.querySelector('[data-testid="board-status-ACTIVE"]')).not.toBeNull();
    });

    // Exactly the four TaskInstanceStatus columns, in the order a run moves through them.
    it('draws four columns and counts each', () => {
      const host = render();

      expect(Array.from(host.querySelectorAll('[data-testid^="board-column-"]')).map((column) => (column as HTMLElement).dataset['testid'])).toEqual([
        'board-column-PENDING',
        'board-column-ACTIVE',
        'board-column-BLOCKED',
        'board-column-COMPLETED',
      ]);
      expect(host.querySelector('[data-testid="board-column-ACTIVE"] .column__count')?.textContent?.trim()).toBe('1');
    });

    // Per column, the field that matters there — which is why the card is a switch rather than one layout.
    it('shows the assignee on an active card and the completion time on a completed one', () => {
      const host = render();

      expect(host.querySelector(`[data-testid="board-card-${APPROVE_SHIPMENT_TASK_ID}"] .card__detail`)?.textContent?.trim()).toBe('manager-user');
      expect(host.querySelector(`[data-testid="board-card-${REVIEW_ORDER_TASK_ID}"] .card__detail`)?.textContent?.trim()).toBeTruthy();
    });

    it('shows "unassigned" on a pending card nobody holds', () => {
      expect(render().querySelector(`[data-testid="board-card-${CONFIRM_DELIVERY_TASK_ID}"] .card__detail`)?.textContent?.trim()).toBe('unassigned');
    });

    // A way in rather than a read-only report: the owner who spots the stuck task is usually the one acting.
    it('opens a task when its card is activated', () => {
      required<HTMLButtonElement>(render(), `[data-testid="board-card-${APPROVE_SHIPMENT_TASK_ID}"]`).click();

      expect(store.selectedTaskId()).toBe(APPROVE_SHIPMENT_TASK_ID);
    });
  });

  it('shows a blocked card’s reason, which is already on the object', () => {
    store.selectInstance(RUN_ID);
    store.reload();
    controller
      .expectOne(`${DASHBOARD_SERVICE_ROOT}/instances`)
      .flush({ content: [{ ...WORKFLOW_INSTANCE_DTO, tasks: [{ ...WORKFLOW_INSTANCE_DTO.tasks[2], status: 'BLOCKED', blockedReason: 'line item 3 has quantity 0' }] }] });

    expect(render().querySelector('[data-testid="board-column-BLOCKED"] .card__detail--danger')?.textContent?.trim()).toBe('line item 3 has quantity 0');
  });

  /**
   * SKIPPED has no column of its own — it is a way of finishing rather than a state to watch — so the card
   * is what says which of the two it was.
   */
  it('folds a skipped card into the completed column and badges it there', () => {
    store.selectInstance(RUN_ID);
    store.reload();
    controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/instances`).flush({ content: [{ ...WORKFLOW_INSTANCE_DTO, tasks: [{ ...WORKFLOW_INSTANCE_DTO.tasks[2], status: 'SKIPPED' }] }] });

    const host = render();
    expect(host.querySelector('[data-testid="board-column-COMPLETED"] [data-testid="task-status-SKIPPED"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="board-column-SKIPPED"]')).toBeNull();
  });

  it('marks an empty column rather than collapsing it', () => {
    store.selectInstance(RUN_ID);

    expect(render().querySelector('[data-testid="board-column-BLOCKED"] .column__empty')?.textContent?.trim()).toBe('—');
  });
});
