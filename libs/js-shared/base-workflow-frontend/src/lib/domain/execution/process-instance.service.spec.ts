import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { BaseEntityLoadResponse, PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ProcessInstance } from './process-instance';
import { ProcessInstanceMapper } from './process-instance.mapper';
import { ProcessInstanceService } from './process-instance.service';
import { pageOfProcessInstances, PROCESS_INSTANCE_DTO } from './test-process-instance';

describe('ProcessInstanceService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: ProcessInstanceService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } },
        ProcessInstanceMapper,
        ProcessInstanceService,
      ],
    });
    service = TestBed.inject(ProcessInstanceService);
    controller = TestBed.inject(HttpTestingController);
  });

  // `listProcessInstances` returns whole instances, tasks and artifacts included — the endpoint
  // assembles each row through the same path the single GET takes, so the form can read its record out
  // of the list the store already loaded.
  it('lists complete instances, tasks and artifacts included', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${serviceRoot}/instances`);
    expect(request.request.method).toBe('GET');
    request.flush(pageOfProcessInstances(PROCESS_INSTANCE_DTO));

    const result = (await pending) as BaseEntityLoadResponse<PersistedEntity<ProcessInstance>>;
    const listed = result.content[0];
    expect(listed.tasks).toHaveLength(3);
    expect(listed.tasks[0].stepResults[0].stepId).toBe('check-items');
    expect(listed.artifacts).toHaveLength(2);
  });

  it('addresses a single instance by its server-minted id', () => {
    // Which happens to be `cancelProcessInstance` on the wire — the one runtime mutation the generic
    // service can express. The screens never offer it: every descriptor here is isAbstract.
    service.delete('8f14e45f-ceea-467a-9c9b-9b0c1f0f5a01').subscribe();

    const request = controller.expectOne(`${serviceRoot}/instances/8f14e45f-ceea-467a-9c9b-9b0c1f0f5a01`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
