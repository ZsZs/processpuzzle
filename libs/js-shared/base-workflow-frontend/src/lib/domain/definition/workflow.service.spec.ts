import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { BaseEntityLoadResponse, PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { Workflow } from './workflow';
import { WorkflowMapper } from './workflow.mapper';
import { WorkflowService } from './workflow.service';
import { pageOfWorkflows, WORKFLOW_DTO } from './test-workflow';

describe('WorkflowService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';

  function configure(baseConfiguration: object) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: baseConfiguration } },
        WorkflowMapper,
        WorkflowService,
      ],
    });
    return { service: TestBed.inject(WorkflowService), controller: TestBed.inject(HttpTestingController) };
  }

  let service: WorkflowService;
  let controller: HttpTestingController;

  beforeEach(() => {
    ({ service, controller } = configure({ WORKFLOW_SERVICE_ROOT: serviceRoot }));
  });

  // The backend answers PageOf_Workflow — a page whose entries are complete workflows rather
  // than headers. The form reads its entity straight out of this list, so anything the list drops the
  // next full-replacement PUT writes back as empty. Complete now means the id lists and the
  // assignments; the catalog records those ids point at come from their own endpoints.
  it('lists complete workflows of the configured organization', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${serviceRoot}/workflows`);
    expect(request.request.method).toBe('GET');
    request.flush(pageOfWorkflows(WORKFLOW_DTO));

    const result = (await pending) as BaseEntityLoadResponse<PersistedEntity<Workflow>>;
    expect(result.totalElements).toBe(1);
    const listed = result.content[0];
    expect(listed.id).toBe('order-fulfillment-workflow');
    expect(listed.roles).toEqual([{ roleDefinitionId: 'clerk' }, { roleDefinitionId: 'manager' }]);
    expect(listed.tasks[0].taskDefinitionId).toBe('review-order');
  });

  it('still reads the bare array the json-server mock returns', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(`${serviceRoot}/workflows`).flush([WORKFLOW_DTO]);

    const result = (await pending) as Workflow[];
    expect(result[0].tasks[1].dependsOn).toEqual(['review-order']);
  });

  it('addresses a single workflow by its author-chosen id', () => {
    service.delete('order-fulfillment-workflow').subscribe();

    const request = controller.expectOne(`${serviceRoot}/workflows/order-fulfillment-workflow`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('sends the Use rows, the start condition and the assignments on update', () => {
    const entity = new WorkflowMapper().fromDto(WORKFLOW_DTO) as PersistedEntity<Workflow>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${serviceRoot}/workflows/order-fulfillment-workflow`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.roles).toEqual([{ roleDefinitionId: 'clerk' }, { roleDefinitionId: 'manager' }]);
    expect(request.request.body.artifacts).toEqual([{ artifactDefinitionId: 'order-entity' }, { artifactDefinitionId: 'fulfillment-invoice' }]);
    // The full-replacement PUT is what makes this load-bearing: a field absent from the body is a field
    // the server clears, and the start condition was absent from every save until this revision.
    expect(request.request.body.startCondition).toMatchObject({ startType: 'INPUT_ARTIFACT' });
    expect(request.request.body.tasks[0]).toMatchObject({ taskDefinitionId: 'review-order', performedBy: 'clerk' });
    request.flush(WORKFLOW_DTO);
  });

  // WORKFLOW_SERVICE_ROOT is optional in BaseConfiguration; `serviceRootOf` falls back to
  // APP_SERVICE_ROOT, which is the only root this workspace's deployments configure today.
  it('falls back to APP_SERVICE_ROOT when no workflow root is configured', () => {
    const { service: fallbackService, controller: fallbackController } = configure({ APP_SERVICE_ROOT: serviceRoot });

    fallbackService.delete('order-fulfillment-workflow').subscribe();

    fallbackController.expectOne(`${serviceRoot}/workflows/order-fulfillment-workflow`).flush(null);
  });
});
