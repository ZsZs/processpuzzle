import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { BaseEntityLoadResponse, PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ProcessDefinition } from './process-definition';
import { ProcessDefinitionMapper } from './process-definition.mapper';
import { ProcessDefinitionService } from './process-definition.service';
import { pageOfProcessDefinitions, PROCESS_DEFINITION_DTO } from './test-process-definition';

describe('ProcessDefinitionService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';

  function configure(baseConfiguration: object) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: baseConfiguration } },
        ProcessDefinitionMapper,
        ProcessDefinitionService,
      ],
    });
    return { service: TestBed.inject(ProcessDefinitionService), controller: TestBed.inject(HttpTestingController) };
  }

  let service: ProcessDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    ({ service, controller } = configure({ WORKFLOW_SERVICE_ROOT: serviceRoot }));
  });

  // The backend answers PageOf_ProcessDefinition — a page whose entries are complete processes rather
  // than headers. The form reads its entity straight out of this list, so anything the list drops the
  // next full-replacement PUT writes back as empty. Complete now means the id lists and the
  // assignments; the catalog records those ids point at come from their own endpoints.
  it('lists complete processes of the configured organization', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${serviceRoot}/processes`);
    expect(request.request.method).toBe('GET');
    request.flush(pageOfProcessDefinitions(PROCESS_DEFINITION_DTO));

    const result = (await pending) as BaseEntityLoadResponse<PersistedEntity<ProcessDefinition>>;
    expect(result.totalElements).toBe(1);
    const listed = result.content[0];
    expect(listed.id).toBe('order-fulfillment-workflow');
    expect(listed.roles).toEqual(['clerk', 'manager']);
    expect(listed.tasks[0].taskDefinitionId).toBe('review-order');
  });

  it('still reads the bare array the json-server mock returns', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(`${serviceRoot}/processes`).flush([PROCESS_DEFINITION_DTO]);

    const result = (await pending) as ProcessDefinition[];
    expect(result[0].tasks[1].dependsOn).toEqual(['review-order']);
  });

  it('addresses a single process by its author-chosen id', () => {
    service.delete('order-fulfillment-workflow').subscribe();

    const request = controller.expectOne(`${serviceRoot}/processes/order-fulfillment-workflow`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('sends the id lists and the assignments on update', () => {
    const entity = new ProcessDefinitionMapper().fromDto(PROCESS_DEFINITION_DTO) as PersistedEntity<ProcessDefinition>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${serviceRoot}/processes/order-fulfillment-workflow`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.roles).toEqual(['clerk', 'manager']);
    expect(request.request.body.artifacts).toEqual(['order-entity', 'fulfillment-invoice']);
    expect(request.request.body.tasks[0]).toMatchObject({ taskDefinitionId: 'review-order', performedBy: 'clerk' });
    request.flush(PROCESS_DEFINITION_DTO);
  });

  // WORKFLOW_SERVICE_ROOT is optional in BaseConfiguration; `serviceRootOf` falls back to
  // APP_SERVICE_ROOT, which is the only root this workspace's deployments configure today.
  it('falls back to APP_SERVICE_ROOT when no workflow root is configured', () => {
    const { service: fallbackService, controller: fallbackController } = configure({ APP_SERVICE_ROOT: serviceRoot });

    fallbackService.delete('order-fulfillment-workflow').subscribe();

    fallbackController.expectOne(`${serviceRoot}/processes/order-fulfillment-workflow`).flush(null);
  });
});
