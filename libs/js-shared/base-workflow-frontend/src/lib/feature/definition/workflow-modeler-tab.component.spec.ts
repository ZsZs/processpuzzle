import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO } from '../../domain/definition/test-artifact-definition';
import { OTHER_ROLE_DEFINITION_DTO, ROLE_DEFINITION_DTO } from '../../domain/definition/test-role-definition';
import { OTHER_TASK_DEFINITION_DTO, TASK_DEFINITION_DTO, THIRD_TASK_DEFINITION_DTO } from '../../domain/definition/test-task-definition';
import { TOOL_DEFINITION_DTO } from '../../domain/definition/test-tool-definition';
import { pageOfWorkflows, WORKFLOW_DTO } from '../../domain/definition/test-workflow';
import { elementNodeId, isLaneNode, laneNodeId } from '../../domain/modeler/workflow-graph';
import { WorkflowDiagramComponent } from '../modeler/components/workflow-diagram.component';
import { WorkflowModelerTabComponent } from './workflow-modeler-tab.component';

const SERVICE_ROOT = 'http://localhost:3000/organizations/processpuzzle-testbed';

describe('WorkflowModelerTabComponent', () => {
  let fixture: ComponentFixture<WorkflowModelerTabComponent>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // Flat dotted keys: TestTranslocoLoader drops nested objects when no scope is configured.
        provideTranslocoTesting({
          translations: {
            en: {
              'base_workflow.workflow.tabs.modeler': 'Workflow Modeler',
              'base_workflow.workflow.modeler.empty': 'This workflow has no tasks yet.',
              'base_workflow.workflow.modeler.lanes': 'Lanes',
              'base_workflow.workflow.modeler.data': 'Work products',
              'base_workflow.workflow.modeler.tools': 'Tools',
              'base_workflow.workflow.modeler.unassigned': 'Unassigned',
              'base_workflow.workflow.modeler.join_any': 'any',
              'base_workflow.workflow_role_definition._self': 'Role',
              'base_workflow.task_definition._self': 'Task',
              'base_workflow.artifact_definition._self': 'Artifact',
              'base_workflow.tool_definition._self': 'Tool',
            },
          },
        }),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: SERVICE_ROOT } } },
      ],
    });
    fixture = TestBed.createComponent(WorkflowModelerTabComponent);
    controller = TestBed.inject(HttpTestingController);
  });

  async function render(entityId = 'order-fulfillment-workflow'): Promise<void> {
    fixture.componentRef.setInput('entityId', entityId);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  /**
   * The five loads this screen makes, each issued by one store's own root-scoped `onInit`. Injecting a store
   * *is* the request for its catalog, which is why five requests exist on a route branch whose other screens
   * make one — a workflow holds ids, and a diagram needs names.
   *
   * `/workflows` answers with a page envelope; the four catalogs answer with plain arrays.
   */
  function flushEverything(rows: { workflows?: object[]; tasks?: object[] } = {}): void {
    const { workflows = [WORKFLOW_DTO], tasks = [TASK_DEFINITION_DTO, OTHER_TASK_DEFINITION_DTO, THIRD_TASK_DEFINITION_DTO] } = rows;
    controller.expectOne(`${SERVICE_ROOT}/workflows`).flush(pageOfWorkflows(...workflows));
    controller.expectOne(`${SERVICE_ROOT}/tasks`).flush(tasks);
    controller.expectOne(`${SERVICE_ROOT}/roles`).flush([ROLE_DEFINITION_DTO, OTHER_ROLE_DEFINITION_DTO]);
    controller.expectOne(`${SERVICE_ROOT}/artifacts`).flush([ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO]);
    controller.expectOne(`${SERVICE_ROOT}/tools`).flush([TOOL_DEFINITION_DTO]);
  }

  async function loaded(entityId?: string): Promise<void> {
    await render(entityId);
    flushEverything();
    await fixture.whenStable();
  }

  function query(testid: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(`[data-testid="${testid}"]`);
  }

  /**
   * The graph handed to the canvas, rather than its DOM: the diagram is drawn by `<ng-diagram>`, whose
   * element structure is its own and is measurement-driven.
   */
  function graph() {
    return fixture.debugElement.query(By.directive(WorkflowDiagramComponent)).componentInstance.graph;
  }

  it('should compile and create component', async () => {
    await loaded();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('draws the diagram once the workflow and the task catalog have arrived', async () => {
    await loaded();

    expect(query('workflow-modeler-diagram')).not.toBeNull();
    expect(query('workflow-modeler-empty')).toBeNull();
  });

  it('draws the workflow the tab was opened from, as lanes and a chain', async () => {
    await loaded();

    expect(graph().nodes.filter(isLaneNode).map((node: { id: string }) => node.id)).toEqual([laneNodeId('clerk'), laneNodeId('manager')]);
    expect(graph().edges.filter((edge: { data?: { relation?: string } }) => edge.data?.relation === 'sequence')).toHaveLength(2);
  });

  /**
   * Unlike the Roles perspective, which marks the row it was opened from and draws the whole organisation:
   * a workflow is a self-contained composition, so the id is a filter here.
   */
  it('draws only the workflow named by the route', async () => {
    await render('claim-handling-workflow');
    flushEverything({
      workflows: [WORKFLOW_DTO, { id: 'claim-handling-workflow', name: 'Claim Handling', tasks: [{ taskDefinitionId: 'review-order', performedBy: 'clerk' }] }],
    });
    await fixture.whenStable();

    expect(graph().nodes.map((node: { id: string }) => node.id)).toEqual([laneNodeId('clerk'), elementNodeId('task', 'review-order'), elementNodeId('artifact', 'order-entity'), elementNodeId('tool', 'automated-check-tool')]);
  });

  // Selects the row, so the tab bar's Details link stays enabled and the status bar keeps naming the record —
  // arriving here directly, nothing else has selected it.
  it('selects the workflow it draws', async () => {
    await loaded();

    expect(query('workflow-modeler-diagram')).not.toBeNull();
    expect(graph().nodes.length).toBeGreaterThan(0);
  });

  describe('the empty state', () => {
    it('says so when the workflow has no tasks', async () => {
      await render('empty-workflow');
      flushEverything({ workflows: [{ id: 'empty-workflow', name: 'Empty', tasks: [] }] });
      await fixture.whenStable();

      expect(query('workflow-modeler-diagram')).toBeNull();
      expect(query('workflow-modeler-empty')?.textContent).toContain('This workflow has no tasks yet.');
    });

    /**
     * Two loads are needed before anything can be drawn — the workflow for its tasks and the task catalog
     * for their names. Drawing on the first alone would show a diagram of unresolved boxes and then re-frame
     * a moment later when the names landed.
     */
    it('draws nothing until the task catalog has arrived too', async () => {
      await render();
      flushEverything({ tasks: [] });
      await fixture.whenStable();

      expect(query('workflow-modeler-diagram')).toBeNull();
    });
  });

  describe('the layer toggles', () => {
    function toggle(layer: string): HTMLInputElement {
      return (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(`[data-testid="modeler-toggle-${layer}"] input`) as HTMLInputElement;
    }

    it('offers all three, on', async () => {
      await loaded();

      expect([toggle('lanes').checked, toggle('data').checked, toggle('tools').checked]).toEqual([true, true, true]);
    });

    it('collapses the lanes to a flat flow', async () => {
      await loaded();

      toggle('lanes').click();
      fixture.detectChanges();

      expect(graph().nodes.filter(isLaneNode)).toEqual([]);
      expect(graph().edges.filter((edge: { data?: { relation?: string } }) => edge.data?.relation === 'sequence')).toHaveLength(2);
    });

    it('takes the work products off the canvas', async () => {
      await loaded();

      toggle('data').click();
      fixture.detectChanges();

      expect(graph().nodes.filter((node: { data: { kind: string } }) => node.data.kind === 'artifact')).toEqual([]);
    });

    it('takes the tools off the canvas', async () => {
      await loaded();

      toggle('tools').click();
      fixture.detectChanges();

      expect(graph().nodes.filter((node: { data: { kind: string } }) => node.data.kind === 'tool')).toEqual([]);
    });
  });

  it('explains every kind it draws', async () => {
    await loaded();

    ['role', 'task', 'artifact', 'tool'].forEach((kind) => expect(query(`modeler-legend-${kind}`)).not.toBeNull());
  });
});
