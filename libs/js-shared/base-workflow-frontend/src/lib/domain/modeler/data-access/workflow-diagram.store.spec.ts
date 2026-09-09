import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { DiagramNodeLayout, Point, WorkflowDiagram } from '../models/workflow-diagram';
import { OTHER_WORKFLOW_DIAGRAM_DTO, pageOfWorkflowDiagrams, WORKFLOW_DIAGRAM_DTO } from '../models/test-workflow-diagram';
import { WorkflowDiagramStore } from './workflow-diagram.store';

describe('WorkflowDiagramStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof WorkflowDiagramStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(WorkflowDiagramStore);
    controller = TestBed.inject(HttpTestingController);
    controller.expectOne(`${serviceRoot}/workflow-diagrams`).flush(pageOfWorkflowDiagrams(WORKFLOW_DIAGRAM_DTO, OTHER_WORKFLOW_DIAGRAM_DTO));
  });

  it('loads the layouts of the organization on init', () => {
    expect(store.entities().map((layout) => layout.id)).toEqual(['order-fulfillment-workflow', 'claim-handling-workflow']);
  });

  describe('loadLayout', () => {
    it('makes the opened workflow layout current', async () => {
      const pending = store.loadLayout('order-fulfillment-workflow');
      controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`).flush({ ...WORKFLOW_DIAGRAM_DTO, version: 4 });

      await pending;
      expect(store.currentId()).toBe('order-fulfillment-workflow');
      expect(store.currentEntity()?.version).toBe(4);
      expect(store.error()).toBeUndefined();
      expect(store.isLoading()).toBe(false);
    });

    it('replaces the listed layout with the one just read, rather than appending a second row', async () => {
      const pending = store.loadLayout('order-fulfillment-workflow');
      controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`).flush({ ...WORKFLOW_DIAGRAM_DTO, version: 4 });

      await pending;
      expect(store.entities()).toHaveLength(2);
      expect(store.entities()[0].version).toBe(4);
    });

    /**
     * A workflow with no layout is the normal starting point: the modeler keeps its automatic swimlane
     * layout, so this must not read as a failure.
     */
    it('reports a never-arranged workflow as no current layout and no error', async () => {
      const pending = store.loadLayout('order-fulfillment-workflow');
      controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`).flush(null, { status: 404, statusText: 'Not Found' });

      expect(await pending).toBeUndefined();
      expect(store.currentEntity()).toBeUndefined();
      expect(store.currentId()).toBeUndefined();
      expect(store.error()).toBeUndefined();
      expect(store.entities()).toHaveLength(2);
    });

    // Surfaced through `error` rather than by throwing, keeping the generic store's contract.
    it('reports any other failure through error', async () => {
      const pending = store.loadLayout('order-fulfillment-workflow');
      controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`).flush({ errorId: 'forbidden' }, { status: 403, statusText: 'Forbidden' });

      expect(await pending).toBeUndefined();
      expect(store.error()).toBeDefined();
      expect(store.isLoading()).toBe(false);
    });
  });

  describe('saveLayout', () => {
    const layout = new WorkflowDiagram({
      workflowId: 'order-fulfillment-workflow',
      nodes: [new DiagramNodeLayout({ nodeId: 'task:review-order', position: new Point({ x: 1, y: 2 }) })],
      version: 2,
    });

    /**
     * The server's answer replaces the layout in the store, so the `version` a later save is
     * optimistic-locked on is the one the server just assigned rather than the one this call sent.
     */
    it('makes the saved layout current, at the version the server assigned', async () => {
      const pending = store.saveLayout(layout);
      controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`).flush({ ...WORKFLOW_DIAGRAM_DTO, version: 3 });

      expect((await pending)?.version).toBe(3);
      expect(store.currentEntity()?.version).toBe(3);
      expect(store.entities()).toHaveLength(2);
    });

    it('appends a layout the loaded list does not hold yet', async () => {
      const pending = store.saveLayout(new WorkflowDiagram({ workflowId: 'new-workflow' }));
      controller.expectOne(`${serviceRoot}/workflow-diagrams/new-workflow`).flush({ workflowId: 'new-workflow', nodes: [], edges: [], version: 0 });

      await pending;
      expect(store.entities().map((row) => row.id)).toEqual(['order-fulfillment-workflow', 'claim-handling-workflow', 'new-workflow']);
    });

    // A concurrent edit is a 409; the screen shows the message rather than the save throwing past it.
    it('reports a rejected save through error and leaves the list alone', async () => {
      const pending = store.saveLayout(layout);
      controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`).flush({ errorId: 'workflow.versionConflict' }, { status: 409, statusText: 'Conflict' });

      expect(await pending).toBeUndefined();
      expect(store.error()).toBeDefined();
      expect(store.isLoading()).toBe(false);
      expect(store.entities()[0].version).toBe(2);
    });
  });
});
