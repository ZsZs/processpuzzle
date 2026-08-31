import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { DiagramNodeLayout, Point, WorkflowDiagram } from '../models/workflow-diagram';
import { OTHER_WORKFLOW_DIAGRAM_DTO, pageOfWorkflowDiagrams, WORKFLOW_DIAGRAM_DTO } from '../models/test-workflow-diagram';
import { WorkflowDiagramMapper } from './workflow-diagram.mapper';
import { WorkflowDiagramService } from './workflow-diagram.service';

describe('WorkflowDiagramService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: WorkflowDiagramService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } },
        WorkflowDiagramMapper,
        WorkflowDiagramService,
      ],
    });
    service = TestBed.inject(WorkflowDiagramService);
    controller = TestBed.inject(HttpTestingController);
  });

  it('lists the layouts of the configured organization', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${serviceRoot}/workflow-diagrams`);
    expect(request.request.method).toBe('GET');
    request.flush(pageOfWorkflowDiagrams(WORKFLOW_DIAGRAM_DTO, OTHER_WORKFLOW_DIAGRAM_DTO));

    const result = await pending;
    expect((result as { content: WorkflowDiagram[] }).content.map((layout) => layout.id)).toEqual(['order-fulfillment-workflow', 'claim-handling-workflow']);
  });

  it('reads one layout by the id of the workflow it lays out', async () => {
    const pending = firstValueFrom(service.findByWorkflowId('order-fulfillment-workflow'));

    const request = controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`);
    expect(request.request.method).toBe('GET');
    request.flush(WORKFLOW_DIAGRAM_DTO);

    const layout = await pending;
    expect(layout?.nodes.map((node) => node.nodeId)).toEqual(['lane:clerk', 'task:review-order', 'task:approve-order']);
    expect(layout?.edges[0].sourcePort).toBe('port-right');
  });

  // 404 is how the modeler learns to keep its automatic layout, not a failure to report.
  it('reports a workflow that has never been arranged as an absent layout', async () => {
    const pending = firstValueFrom(service.findByWorkflowId('order-fulfillment-workflow'));

    controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`).flush({ errorId: 'workflow.notFound' }, { status: 404, statusText: 'Not Found' });

    expect(await pending).toBeUndefined();
  });

  it('still fails on any other status', async () => {
    const pending = firstValueFrom(service.findByWorkflowId('order-fulfillment-workflow'));

    controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`).flush({ errorId: 'forbidden' }, { status: 403, statusText: 'Forbidden' });

    await expect(pending).rejects.toMatchObject({ status: 403 });
  });

  describe('save', () => {
    const layout = new WorkflowDiagram({
      workflowId: 'order-fulfillment-workflow',
      nodes: [new DiagramNodeLayout({ nodeId: 'task:review-order', position: new Point({ x: 10, y: 20 }) })],
    });

    it('writes the whole layout with one PUT, addressed by the workflow id', async () => {
      const pending = firstValueFrom(service.save(layout));

      const request = controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toMatchObject({ workflowId: 'order-fulfillment-workflow', nodes: [{ nodeId: 'task:review-order', position: { x: 10, y: 20 } }] });
      request.flush({ ...WORKFLOW_DIAGRAM_DTO, version: 3 });

      expect((await pending).version).toBe(3);
    });

    /**
     * The upsert is what removes the create/replace choice, so neither inherited path may take a different
     * route — `add` on a layout that already exists must not POST and get a 409.
     */
    it('routes add through the same upsert', async () => {
      void firstValueFrom(service.add(layout));

      expect(controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`).request.method).toBe('PUT');
      controller.verify();
    });

    it('routes update through the same upsert', async () => {
      void firstValueFrom(service.update(layout as PersistedEntity<WorkflowDiagram>));

      expect(controller.expectOne(`${serviceRoot}/workflow-diagrams/order-fulfillment-workflow`).request.method).toBe('PUT');
      controller.verify();
    });

    // A layout is addressed by the workflow it lays out; without one there is no URL to write to.
    it('refuses to save a layout that names no workflow', () => {
      expect(() => service.save(new WorkflowDiagram())).toThrow(/addressed by the id of its workflow/);
    });
  });
});
