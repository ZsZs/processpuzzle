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
import { pageOfWorkflowDiagrams, WORKFLOW_DIAGRAM_DTO } from '../../domain/modeler/models/test-workflow-diagram';
import { elementNodeId, isLaneNode, laneNodeId } from '../../domain/modeler/workflow-graph';
import { WorkflowDiagramComponent } from '../modeler/components/workflow-diagram.component';
import { WorkflowSelectionService } from '../modeler/services/workflow-selection.service';
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
              'base_workflow.workflow.modeler.save': 'Save layout',
              'base_workflow.workflow.modeler.properties.none': 'Select an element to see its properties.',
              'base_workflow.workflow.modeler.properties.name': 'Name',
              'base_workflow.workflow.modeler.properties.id': 'Identifier',
              'base_workflow.workflow.modeler.properties.description': 'Description',
              'base_workflow.workflow.modeler.properties.unresolved': 'This reference does not resolve to a catalog entry.',
              'base_workflow.workflow.modeler.properties.lane': 'Lane',
              'base_workflow.workflow.modeler.properties.relation_heading': 'Relation',
              'base_workflow.workflow.modeler.properties.relation': 'Kind',
              'base_workflow.workflow.modeler.properties.relations.sequence': 'Depends on',
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
   * The loads this screen makes, each issued by one store's own root-scoped `onInit`. Injecting a store *is*
   * the request for its contents, which is why so many requests exist on a route branch whose other screens
   * make one — a workflow holds ids, a diagram needs names, and an arrangement is a resource of its own.
   *
   * `/workflows` and `/workflow-diagrams` answer with a page envelope; the four catalogs answer with plain
   * arrays. The `/workflow-diagrams` *list* is `BaseEntityStore.onInit`'s doing and is not what this screen
   * reads — `loadLayout` fetches the one layout by id, which is {@link flushLayout}.
   */
  function flushEverything(rows: { workflows?: object[]; tasks?: object[] } = {}): void {
    const { workflows = [WORKFLOW_DTO], tasks = [TASK_DEFINITION_DTO, OTHER_TASK_DEFINITION_DTO, THIRD_TASK_DEFINITION_DTO] } = rows;
    controller.expectOne(`${SERVICE_ROOT}/workflows`).flush(pageOfWorkflows(...workflows));
    controller.expectOne(`${SERVICE_ROOT}/tasks`).flush(tasks);
    controller.expectOne(`${SERVICE_ROOT}/roles`).flush([ROLE_DEFINITION_DTO, OTHER_ROLE_DEFINITION_DTO]);
    controller.expectOne(`${SERVICE_ROOT}/artifacts`).flush([ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO]);
    controller.expectOne(`${SERVICE_ROOT}/tools`).flush([TOOL_DEFINITION_DTO]);
    controller.expectOne(`${SERVICE_ROOT}/workflow-diagrams`).flush(pageOfWorkflowDiagrams());
  }

  /**
   * Answers the arrangement lookup for one workflow. A 404 is the normal answer — it is how the modeler
   * learns to keep its automatic swimlane layout — and the canvas is not rendered until this has settled
   * either way, so every test that expects a diagram has to call it.
   */
  function flushLayout(entityId = 'order-fulfillment-workflow', layout?: object): void {
    const request = controller.expectOne(`${SERVICE_ROOT}/workflow-diagrams/${entityId}`);
    if (layout) request.flush(layout);
    else request.flush({ errorId: 'workflow.notFound' }, { status: 404, statusText: 'Not Found' });
  }

  async function loaded(entityId?: string): Promise<void> {
    await render(entityId);
    flushEverything();
    flushLayout(entityId);
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
    flushLayout('claim-handling-workflow');
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
      flushLayout('empty-workflow');
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
      flushLayout();
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

  describe('the saved arrangement', () => {
    /** What the canvas was handed, which is what decides where the nodes end up. */
    function savedLayout() {
      return fixture.debugElement.query(By.directive(WorkflowDiagramComponent)).componentInstance.savedLayout;
    }

    it('lets the user rearrange what is drawn', async () => {
      await loaded();

      expect(fixture.debugElement.query(By.directive(WorkflowDiagramComponent)).componentInstance.editable).toBe(true);
    });

    it('hands the canvas the layout that was read', async () => {
      await render();
      flushEverything();
      flushLayout('order-fulfillment-workflow', WORKFLOW_DIAGRAM_DTO);
      await fixture.whenStable();

      expect(savedLayout()?.nodes.map((node: { nodeId: string }) => node.nodeId)).toContain('task:review-order');
    });

    // A workflow that has never been arranged is the normal starting point, and keeps the automatic layout.
    it('hands the canvas nothing when the workflow has never been arranged', async () => {
      await loaded();

      expect(savedLayout()).toBeUndefined();
      expect(query('workflow-modeler-error')).toBeNull();
    });

    /**
     * `WorkflowDiagramStore` is root-scoped, so navigating from one workflow's modeler to another's leaves
     * the previous layout current until the new lookup resolves — and applying it would move this workflow's
     * tasks to another one's positions.
     */
    it('does not apply another workflow s layout', async () => {
      await render();
      flushEverything();
      flushLayout('order-fulfillment-workflow', { ...WORKFLOW_DIAGRAM_DTO, workflowId: 'claim-handling-workflow' });
      await fixture.whenStable();

      expect(savedLayout()).toBeUndefined();
    });

    /**
     * The canvas frames itself when its model is created, so one created before the lookup settles would fit
     * the automatic layout and then have to re-frame to the saved viewport.
     */
    it('draws nothing until the arrangement lookup has settled', async () => {
      await render();
      flushEverything();
      await fixture.whenStable();

      expect(query('workflow-modeler-diagram')).toBeNull();
    });
  });

  describe('saving', () => {
    it('writes the arrangement with one PUT, addressed by the workflow id', async () => {
      await loaded();

      query('workflow-modeler-save')?.click();
      await fixture.whenStable();

      const request = controller.expectOne(`${SERVICE_ROOT}/workflow-diagrams/order-fulfillment-workflow`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body.workflowId).toBe('order-fulfillment-workflow');
      // Every node on the canvas has a position by the time it is saved — that is the layout's whole job.
      expect(request.request.body.nodes.length).toBeGreaterThan(0);
      request.flush({ ...WORKFLOW_DIAGRAM_DTO, version: 3 });
    });

    // Nothing to arrange, so nothing to save — and no request to send.
    it('offers no save while there is no diagram', async () => {
      await render('empty-workflow');
      flushEverything({ workflows: [{ id: 'empty-workflow', name: 'Empty', tasks: [] }] });
      flushLayout('empty-workflow');
      await fixture.whenStable();

      expect((query('workflow-modeler-save') as HTMLButtonElement).disabled).toBe(true);
    });

    /**
     * A rejected save has nothing to roll back — the canvas and the server are both exactly as they were —
     * so the only thing owed to the user is the reason.
     */
    it('shows why a save was refused', async () => {
      await loaded();

      query('workflow-modeler-save')?.click();
      await fixture.whenStable();
      controller
        .expectOne(`${SERVICE_ROOT}/workflow-diagrams/order-fulfillment-workflow`)
        .flush({ errorId: 'workflow.versionConflict', errorText: 'Reload and retry.' }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(query('workflow-modeler-error')?.textContent).toContain('Reload and retry.');
    });
  });

  describe('the properties column', () => {
    let selection: WorkflowSelectionService;

    beforeEach(() => {
      selection = TestBed.inject(WorkflowSelectionService);
    });

    it('asks for a selection while there is none', async () => {
      await loaded();

      expect(query('workflow-modeler-no-selection')).not.toBeNull();
    });

    it('shows the selected element', async () => {
      await loaded();

      selection.selectElement({ kind: 'task', elementId: 'review-order', label: 'Review Order' });
      fixture.detectChanges();
      await fixture.whenStable();

      expect(query('element-name')?.textContent).toContain('Review Order');
      expect(query('element-kind')?.textContent).toContain('Task');
      expect(query('workflow-modeler-no-selection')).toBeNull();
    });

    it('shows the selected relation instead', async () => {
      await loaded();

      selection.selectRelation({ relation: 'sequence' });
      fixture.detectChanges();
      await fixture.whenStable();

      expect(query('relation-kind')?.textContent).toContain('Depends on');
      expect(query('element-name')).toBeNull();
    });
  });
});
