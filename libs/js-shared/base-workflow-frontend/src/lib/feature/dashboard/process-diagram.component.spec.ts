import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';
import { elementNodeId, WorkflowGraph } from '../../domain/modeler/workflow-graph';
import { WorkflowSelectionService } from '../modeler/services/workflow-selection.service';
import { ProcessDiagramComponent } from './process-diagram.component';
import { APPROVE_SHIPMENT_TASK_ID, DASHBOARD_SERVICE_ROOT, flushDashboardCatalogs, provideDashboardTesting, REVIEW_ORDER_TASK_ID, RUN_ID } from './test-dashboard';

describe('ProcessDiagramComponent', () => {
  const layoutUrl = `${DASHBOARD_SERVICE_ROOT}/workflow-diagrams/order-fulfillment-workflow`;

  let fixture: ComponentFixture<ProcessDiagramComponent>;
  let store: InstanceType<typeof WorkflowDashboardStore>;
  let controller: HttpTestingController;

  /** The graph handed to the canvas — what the diagram draws, without depending on ng-diagram's rendering. */
  const graph = (): WorkflowGraph => (fixture.componentInstance as unknown as { graph: () => WorkflowGraph }).graph();
  const nodeData = (id: string) => graph().nodes.find((node) => node.id === id)?.data;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ProcessDiagramComponent], providers: [provideDashboardTesting()] }).compileComponents();
    store = TestBed.inject(WorkflowDashboardStore);
    controller = TestBed.inject(HttpTestingController);
    flushDashboardCatalogs(controller);
    store.selectInstance(RUN_ID);
    store.select(RUN_ID, APPROVE_SHIPMENT_TASK_ID);
    fixture = TestBed.createComponent(ProcessDiagramComponent);
    fixture.detectChanges();
  });

  const answerLayout = async (): Promise<HTMLElement> => {
    controller.expectOne(layoutUrl).flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  // The canvas is created once, already knowing whether there is a viewport to restore.
  it('waits for the saved arrangement before drawing', async () => {
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="process-diagram"]')).toBeNull();

    expect((await answerLayout()).querySelector('[data-testid="process-diagram"]')).not.toBeNull();
  });

  it('marks every task and event with where the run stands on it', async () => {
    await answerLayout();

    expect(nodeData(elementNodeId('task', 'review-order'))?.runStatus).toBe('COMPLETED');
    expect(nodeData(elementNodeId('task', 'approve-shipment'))?.runStatus).toBe('ACTIVE');
    expect(nodeData(elementNodeId('task', 'confirm-delivery'))?.runStatus).toBe('PENDING');
    expect(nodeData(elementNodeId('event', 'invoice-issued'))?.runStatus).toBe('PENDING');
  });

  it('marks the flow edges the run has travelled', async () => {
    await answerLayout();

    const passed = (from: string, to: string) => graph().edges.find((edge) => edge.source === elementNodeId('task', from) && edge.target === elementNodeId('task', to))?.data?.passed;
    expect(passed('review-order', 'approve-shipment')).toBe(true);
    expect(passed('approve-shipment', 'confirm-delivery')).toBeUndefined();
  });

  it('highlights the open task', async () => {
    await answerLayout();

    expect(nodeData(elementNodeId('task', 'approve-shipment'))?.highlighted).toBe(true);
    expect(nodeData(elementNodeId('task', 'review-order'))?.highlighted).toBe(false);
  });

  // The diagram is a way in, like the board: a click opens that task of this run below.
  it('opens the task clicked on', async () => {
    await answerLayout();

    fixture.debugElement.injector.get(WorkflowSelectionService).selectElement({ kind: 'task', elementId: 'review-order', label: 'Review Order' });
    TestBed.tick();

    expect(store.selectedTaskId()).toBe(REVIEW_ORDER_TASK_ID);
  });

  it('looks the arrangement up once per workflow', async () => {
    await answerLayout();

    store.select(RUN_ID, REVIEW_ORDER_TASK_ID);
    fixture.detectChanges();

    expect(controller.match(layoutUrl)).toHaveLength(0);
  });
});
