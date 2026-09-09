import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { TaskDefinition } from './task-definition';
import { TaskDefinitionMapper } from './task-definition.mapper';
import { TaskDefinitionService } from './task-definition.service';
import { OTHER_TASK_DEFINITION_DTO, TASK_DEFINITION_DTO } from './test-task-definition';

describe('TaskDefinitionService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: TaskDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } },
        TaskDefinitionMapper,
        TaskDefinitionService,
      ],
    });
    service = TestBed.inject(TaskDefinitionService);
    controller = TestBed.inject(HttpTestingController);
  });

  // A catalog resource of its own, not `/workflows/{id}/tasks`: a task is authored once and picked up
  // by every workflow that references its id.
  it('reads the tasks of the organization from their own collection', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(`${serviceRoot}/tasks`).flush([TASK_DEFINITION_DTO, OTHER_TASK_DEFINITION_DTO]);

    expect(await pending).toHaveLength(2);
  });

  it('addresses a single task by its author-chosen id', () => {
    service.delete('review-order').subscribe();

    controller.expectOne(`${serviceRoot}/tasks/review-order`).flush(null);
  });

  // The task's children have no endpoint of their own, so they have to reach the wire inside this
  // payload — and the roles have to arrive as ids, which is the mapper's work.
  it('sends the steps and the three flat id lists on update', () => {
    const entity = new TaskDefinitionMapper().fromDto(TASK_DEFINITION_DTO) as PersistedEntity<TaskDefinition>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${serviceRoot}/tasks/review-order`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.performedByRoles).toEqual(['clerk', 'manager']);
    expect(request.request.body.inputs).toEqual(['order-entity']);
    expect(request.request.body.outputs).toEqual(['order-entity']);
    expect(request.request.body.steps[0]).toMatchObject({ id: 'check-items', stepType: 'SERVICE_STEP', toolDefinitionId: 'automated-check-tool' });
    request.flush(TASK_DEFINITION_DTO);
  });
});
