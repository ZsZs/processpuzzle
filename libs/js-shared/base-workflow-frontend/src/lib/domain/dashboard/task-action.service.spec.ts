import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { TaskInstanceStatus } from '../execution/workflow-instance';
import { TaskActionService } from './task-action.service';

describe('TaskActionService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  const instanceId = '8f14e45f-ceea-467a-9c9b-9b0c1f0f5a01';
  /**
   * The **definition** id, not the instance's UUID. Every task-scoped endpoint resolves the row through
   * `findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId`, so the UUID the reads return answers 404 — see
   * the class comment on `TaskActionService`. The UUID is kept below only to prove the two differ.
   */
  const taskDefinitionId = 'approve-shipment';
  const taskInstanceUuid = '5a1b2c3d-0002-4a00-8000-000000000002';
  const taskUrl = `${serviceRoot}/instances/${instanceId}/tasks/${taskDefinitionId}`;
  const taskDto = { id: taskInstanceUuid, taskDefinitionId, name: 'Approve Shipment', status: 'ACTIVE', assignedTo: 'manager-user', stepResults: [] };

  let service: TaskActionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } }, TaskActionService],
    });
    service = TestBed.inject(TaskActionService);
    controller = TestBed.inject(HttpTestingController);
  });

  // The organization is part of the configured service root, exactly as in every other service of this
  // library — no caller threads an orgKey.
  it('addresses a task by its definition id, through the run that owns it, under the configured service root', async () => {
    const pending = firstValueFrom(service.assign(instanceId, taskDefinitionId, 'manager-user'));
    const request = controller.expectOne(`${taskUrl}/assign`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ userId: 'manager-user' });
    request.flush(taskDto);

    expect((await pending).status).toBe(TaskInstanceStatus.ACTIVE);
    // The two ids are genuinely different — the URL must not carry the one the reads put in `id`.
    expect(taskUrl).not.toContain(taskInstanceUuid);
  });

  // Claim and assign are one call with a different source for the value — no separate backend concept.
  it('claims and assigns through the same endpoint', () => {
    service.assign(instanceId, taskDefinitionId, 'someone-else').subscribe();

    expect(controller.expectOne(`${taskUrl}/assign`).request.body).toEqual({ userId: 'someone-else' });
  });

  it('sends the completion context under the key the contract names', async () => {
    const pending = firstValueFrom(service.complete(instanceId, taskDefinitionId, { reviewScore: '7' }));
    const request = controller.expectOne(`${taskUrl}/complete`);

    expect(request.request.body).toEqual({ context: { reviewScore: '7' } });
    request.flush({ accepted: true, task: taskDto });

    expect(await pending).toMatchObject({ accepted: true, postconditionDetail: undefined });
  });

  // A task whose postcondition needs nothing is completed with no context at all rather than with an empty
  // map, which would be a merge of nothing into the workflow context.
  it.each([[undefined], [{}]])('omits the context entirely when there is none: %s', (context) => {
    service.complete(instanceId, taskDefinitionId, context).subscribe();

    expect(controller.expectOne(`${taskUrl}/complete`).request.body).toEqual({});
  });

  /**
   * The case the whole `CompleteTaskResult` type exists for: 200 with `accepted: false`. It is a returned
   * verdict rather than a thrown error, because the task stays ACTIVE for the user to fix and resubmit.
   */
  it('reports a refused completion as a verdict rather than an error', async () => {
    const pending = firstValueFrom(service.complete(instanceId, taskDefinitionId));

    controller.expectOne(`${taskUrl}/complete`).flush({ accepted: false, task: taskDto, postconditionDetail: 'shipment-approved: not set' });

    expect(await pending).toMatchObject({ accepted: false, postconditionDetail: 'shipment-approved: not set' });
  });

  // A body that does not say it was accepted is not grounds for telling the user their work is done.
  it('treats a missing accepted flag as a refusal', async () => {
    const pending = firstValueFrom(service.complete(instanceId, taskDefinitionId));

    controller.expectOne(`${taskUrl}/complete`).flush({});

    expect(await pending).toEqual({ accepted: false, task: undefined, postconditionDetail: undefined });
  });

  it('sends a skip reason when one was given, and no body when it was not', () => {
    service.skip(instanceId, taskDefinitionId, 'customer cancelled').subscribe();
    expect(controller.expectOne(`${taskUrl}/skip`).request.body).toEqual({ reason: 'customer cancelled' });

    service.skip(instanceId, taskDefinitionId).subscribe();
    expect(controller.expectOne(`${taskUrl}/skip`).request.body).toEqual({});
  });

  // Ids come from the server as UUIDs today, but a task definition id is author-chosen and a run's id is
  // whatever the backend minted — neither is guaranteed to be URL-safe.
  it('encodes ids into the path', () => {
    service.assign('run/1', 'task 2', 'user').subscribe();

    controller.expectOne(`${serviceRoot}/instances/run%2F1/tasks/task%202/assign`).flush({});
  });
});
