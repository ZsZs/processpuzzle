import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO } from '../definition/test-artifact-definition';
import { OTHER_ROLE_DEFINITION_DTO, ROLE_DEFINITION_DTO } from '../definition/test-role-definition';
import { OTHER_TASK_DEFINITION_DTO, TASK_DEFINITION_DTO, THIRD_TASK_DEFINITION_DTO } from '../definition/test-task-definition';
import { pageOfWorkflows, WORKFLOW_DTO } from '../definition/test-workflow';
import { TaskInstanceStatus } from '../execution/workflow-instance';
import { OTHER_WORKFLOW_INSTANCE_DTO, pageOfWorkflowInstances, WORKFLOW_INSTANCE_DTO } from '../execution/test-workflow-instance';
import { CurrentUserContext, PROCESS_OWNER_ROLE } from './current-user.context';
import { WorkflowDashboardStore } from './workflow-dashboard.store';

/**
 * The dashboard reads five root stores and each one loads on init, so creating the store is what issues
 * five collection GETs. They are answered by URL rather than in order: which store is constructed first is
 * an implementation detail of `withComputed`, and a spec that depended on it would break on a reorder that
 * changed nothing.
 */
describe('WorkflowDashboardStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  const runId = WORKFLOW_INSTANCE_DTO.id;
  const reviewOrderTaskId = WORKFLOW_INSTANCE_DTO.tasks[0].id;
  const approveShipmentTaskId = WORKFLOW_INSTANCE_DTO.tasks[1].id;
  const confirmDeliveryTaskId = WORKFLOW_INSTANCE_DTO.tasks[2].id;

  let store: InstanceType<typeof WorkflowDashboardStore>;
  let session: CurrentUserContext;
  let controller: HttpTestingController;

  const flushCatalogs = (): void => {
    controller.expectOne(`${serviceRoot}/instances`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO, OTHER_WORKFLOW_INSTANCE_DTO));
    controller.expectOne(`${serviceRoot}/tasks`).flush([TASK_DEFINITION_DTO, OTHER_TASK_DEFINITION_DTO, THIRD_TASK_DEFINITION_DTO]);
    controller.expectOne(`${serviceRoot}/artifacts`).flush([ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO]);
    controller.expectOne(`${serviceRoot}/workflows`).flush(pageOfWorkflows(WORKFLOW_DTO));
    controller.expectOne(`${serviceRoot}/roles`).flush([ROLE_DEFINITION_DTO, OTHER_ROLE_DEFINITION_DTO]);
  };

  /**
   * Re-reads the runs with a different answer. `reload()` starts the request and returns — it is an
   * `rxMethod`, not a promise — so the flush has to follow the call rather than precede it.
   */
  const reloadWith = (...runs: object[]): void => {
    store.reload();
    controller.expectOne(`${serviceRoot}/instances`).flush(pageOfWorkflowInstances(...runs));
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } }],
    });
    session = TestBed.inject(CurrentUserContext);
    store = TestBed.inject(WorkflowDashboardStore);
    controller = TestBed.inject(HttpTestingController);
    flushCatalogs();
  });

  describe('the queues', () => {
    /**
     * The whole reason no backend change was needed. `TaskInstance` carries no `workflowInstanceId` and the
     * contract has no cross-instance task query — but `listWorkflowInstances` answers with full instances,
     * tasks nested, so the run each task belongs to is known at the moment the task is read.
     */
    it('flattens every run’s tasks and keeps the run beside each one', () => {
      const rows = store.allTasks();

      expect(rows).toHaveLength(WORKFLOW_INSTANCE_DTO.tasks.length + OTHER_WORKFLOW_INSTANCE_DTO.tasks.length);
      expect(rows.find((row) => row.task.id === reviewOrderTaskId)).toMatchObject({
        instanceId: runId,
        workflowId: 'order-fulfillment-workflow',
        workflowName: 'Order Fulfillment Workflow',
        entityId: '1',
        taskDefinitionId: 'review-order',
      });
    });

    it('shows no rows of its own until somebody is signed in', () => {
      expect(store.myTasks()).toEqual([]);
    });

    it('shows the signed-in user their own tasks, whatever status those are in', () => {
      session.set({ userId: 'clerk-user' });

      expect(store.myTasks().map((row) => row.task.id)).toEqual([reviewOrderTaskId]);
    });

    // Unassigned and ACTIVE: a PENDING task has not been activated by the engine and a BLOCKED one cannot
    // be worked on, so neither is claimable however open it looks.
    it('offers only the unassigned active tasks for claiming', () => {
      expect(store.teamTasks().map((row) => row.task.status)).toEqual([]);

      reloadWith({
        ...WORKFLOW_INSTANCE_DTO,
        tasks: [
          { ...WORKFLOW_INSTANCE_DTO.tasks[1], assignedTo: undefined },
          WORKFLOW_INSTANCE_DTO.tasks[2],
          { ...WORKFLOW_INSTANCE_DTO.tasks[2], id: 'blocked-1', status: 'BLOCKED' },
        ],
      });

      expect(store.teamTasks().map((row) => row.task.id)).toEqual([approveShipmentTaskId]);
    });

    it('scopes the process queue to the run it was pointed at', () => {
      store.selectInstance(runId);

      expect(store.processTasks().map((row) => row.instanceId)).toEqual([runId, runId, runId]);
    });

    it('shows whichever queue the scope names', () => {
      session.set({ userId: 'clerk-user' });

      expect(store.visibleTasks()).toEqual(store.myTasks());
      store.setScope('team');
      expect(store.visibleTasks()).toEqual(store.teamTasks());
      store.setScope('process');
      expect(store.visibleTasks()).toEqual(store.processTasks());
    });
  });

  describe('role matching on the Team queue', () => {
    beforeEach(() => reloadWith({ ...WORKFLOW_INSTANCE_DTO, tasks: [{ ...WORKFLOW_INSTANCE_DTO.tasks[1], assignedTo: undefined }] }));

    // `WorkflowTaskAssignment.performedBy` is the workflow's pick out of the definition's `performedByRoles`
    // — the definition says who is able, the workflow says who does.
    it('reads the performing role from the workflow rather than from the task definition', () => {
      expect(store.allTasks().find((row) => row.task.id === approveShipmentTaskId)?.performedBy).toBe('manager');
    });

    it('offers a claimable task to a session holding the performing role', () => {
      session.set({ userId: 'manager-user', roles: ['manager'] });

      expect(store.teamTasks()).toHaveLength(1);
    });

    it('withholds it from a session holding a different role', () => {
      session.set({ userId: 'clerk-user', roles: ['clerk'] });

      expect(store.teamTasks()).toEqual([]);
    });

    // Unknown is not none — see CurrentUserContext. The backend still refuses a claim by a user without
    // the role, so the generous filter cannot grant anything.
    it('offers everything to a session whose roles the host never wired', () => {
      session.set({ userId: 'somebody' });

      expect(store.teamTasks()).toHaveLength(1);
    });
  });

  describe('the open task', () => {
    beforeEach(() => store.select(runId, reviewOrderTaskId));

    it('resolves the task, its run and its definition together', () => {
      expect(store.selectedTask()?.id).toBe(reviewOrderTaskId);
      expect(store.selectedInstance()?.id).toBe(runId);
      expect(store.selectedDefinition()?.id).toBe('review-order');
    });

    // Keyed by `taskDefinitionId`, never by the instance's own id: those are different ids, and reading a
    // definition by an instance id is a 404 that presents as a task with no steps.
    it('resolves the definition through taskDefinitionId', () => {
      expect(store.selectedDefinition()?.steps.map((step) => step.id)).toEqual(['check-items']);
      expect(store.selectedTask()?.id).not.toBe(store.selectedDefinition()?.id);
    });

    it('resolves a declared input against the run’s artifact instances', () => {
      expect(store.selectedInputs()).toEqual([expect.objectContaining({ artifactDefinitionId: 'order-entity', direction: 'input', name: 'Order Entity' })]);
      expect(store.selectedInputs()[0].instance?.currentState).toBe('CONFIRMED');
    });

    // Kept in the list rather than hidden: a declared output that does not exist is what tells the user
    // what this task is for.
    it('keeps a declared output that nothing has produced yet, named from the catalog', () => {
      reloadWith({ ...WORKFLOW_INSTANCE_DTO, artifacts: [] });

      expect(store.selectedOutputs()).toEqual([expect.objectContaining({ artifactDefinitionId: 'order-entity', direction: 'output', instance: undefined })]);
      expect(store.selectedOutputs()[0].name).toBe(ARTIFACT_DEFINITION_DTO.name);
    });

    it('holds nothing when the selection is cleared', () => {
      store.clearSelection();

      expect(store.selectedRow()).toBeUndefined();
      expect(store.selectedInputs()).toEqual([]);
    });

    it('knows whether the open task is the session’s own', () => {
      expect(store.isMine()).toBe(false);

      session.set({ userId: 'clerk-user' });

      expect(store.isMine()).toBe(true);
    });

    it('never calls an unassigned task the session’s own, even for a session with no id', () => {
      store.select(runId, confirmDeliveryTaskId);

      expect(store.isMine()).toBe(false);
    });
  });

  describe('the process board', () => {
    beforeEach(() => store.selectInstance(runId));

    it('groups the run’s tasks into the four columns', () => {
      expect(store.columns().map((column) => [column.status, column.rows.length])).toEqual([
        [TaskInstanceStatus.PENDING, 1],
        [TaskInstanceStatus.ACTIVE, 1],
        [TaskInstanceStatus.BLOCKED, 0],
        [TaskInstanceStatus.COMPLETED, 1],
      ]);
    });

    // SKIPPED has no column of its own: it is a way of finishing rather than a state to watch.
    it('folds a skipped task into the completed column', () => {
      reloadWith({ ...WORKFLOW_INSTANCE_DTO, tasks: [{ ...WORKFLOW_INSTANCE_DTO.tasks[2], status: 'SKIPPED' }] });

      const completed = store.columns().find((column) => column.status === TaskInstanceStatus.COMPLETED);
      expect(completed?.rows.map((row) => row.task.status)).toEqual([TaskInstanceStatus.SKIPPED]);
    });

    it('offers the runs newest first', () => {
      expect(store.selectableInstances().map((instance) => instance.id)).toEqual([runId, OTHER_WORKFLOW_INSTANCE_DTO.id]);
    });

    // Pointing the board at a different run cannot leave the previous run's task open beside it.
    it('drops a selection belonging to another run', () => {
      store.select(runId, reviewOrderTaskId);
      store.selectInstance(OTHER_WORKFLOW_INSTANCE_DTO.id);

      expect(store.selectedTaskId()).toBeUndefined();
    });

    it('keeps the selection when pointed at the run it already showed', () => {
      store.select(runId, reviewOrderTaskId);
      store.selectInstance(runId);

      expect(store.selectedTaskId()).toBe(reviewOrderTaskId);
    });
  });

  describe('the three verbs', () => {
    // Addressed by `taskDefinitionId` — see `TaskActionService`. `approveShipmentTaskId` is the instance's
    // UUID and would 404, which is what `ActionTarget` exists to make impossible to pass by accident.
    const taskUrl = `${serviceRoot}/instances/${runId}/tasks/approve-shipment`;
    const row = () => ({ instanceId: runId, taskDefinitionId: 'approve-shipment' });

    it('claims for the signed-in user and re-reads the runs afterwards', async () => {
      session.set({ userId: 'manager-user' });
      const claiming = store.claim(row());

      const request = controller.expectOne(`${taskUrl}/assign`);
      expect(request.request.body).toEqual({ userId: 'manager-user' });
      request.flush(WORKFLOW_INSTANCE_DTO.tasks[1]);
      await claiming;

      // The whole list, not the one task the verb answered with: completing or skipping a task activates
      // whatever depended on it.
      controller.expectOne(`${serviceRoot}/instances`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
      expect(store.isActing()).toBe(false);
    });

    it('does not post a claim for nobody', async () => {
      await store.claim(row());

      controller.expectNone(`${taskUrl}/assign`);
    });

    it('reports an accepted completion and leaves no refusal behind', async () => {
      const completing = store.complete(row(), { reviewScore: '7' });

      controller.expectOne(`${taskUrl}/complete`).flush({ accepted: true, task: WORKFLOW_INSTANCE_DTO.tasks[1] });

      expect(await completing).toBe(true);
      expect(store.postconditionDetail()).toBeUndefined();
      controller.expectOne(`${serviceRoot}/instances`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
    });

    /** A refusal is a 200 with the task still ACTIVE — the detail belongs beside the form, not in an error. */
    it('records why a completion was refused, without treating it as an error', async () => {
      const completing = store.complete(row());

      controller.expectOne(`${taskUrl}/complete`).flush({ accepted: false, postconditionDetail: 'shipment-approved: not set' });

      expect(await completing).toBe(false);
      expect(store.postconditionDetail()).toBe('shipment-approved: not set');
      expect(store.actionError()).toBeUndefined();
      controller.expectOne(`${serviceRoot}/instances`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
    });

    // `postconditionDetail` is nullable, and an unchanged screen would read as a submission that vanished.
    it('substitutes a key when the server refuses without saying why', async () => {
      const completing = store.complete(row());

      controller.expectOne(`${taskUrl}/complete`).flush({ accepted: false });
      await completing;

      expect(store.postconditionDetail()).toBe('unstated_refusal');
      controller.expectOne(`${serviceRoot}/instances`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
    });

    it('skips with the reason it was given', async () => {
      const skipping = store.skip(row(), 'customer cancelled');

      const request = controller.expectOne(`${taskUrl}/skip`);
      expect(request.request.body).toEqual({ reason: 'customer cancelled' });
      request.flush(WORKFLOW_INSTANCE_DTO.tasks[1]);

      expect(await skipping).toBe(true);
      controller.expectOne(`${serviceRoot}/instances`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
    });

    it('surfaces a failed verb as a message and stops acting', async () => {
      const completing = store.complete(row());

      controller.expectOne(`${taskUrl}/complete`).flush({ errorId: 'task.not.active' }, { status: 409, statusText: 'Conflict' });

      expect(await completing).toBe(false);
      expect(store.actionError()).toBeTruthy();
      expect(store.isActing()).toBe(false);
      // No reload after a failure: nothing changed, so re-reading the list would only hide the message.
      controller.expectNone(`${serviceRoot}/instances`);
    });

    it('clears a stale refusal when a different task is opened', async () => {
      const completing = store.complete(row());
      controller.expectOne(`${taskUrl}/complete`).flush({ accepted: false, postconditionDetail: 'nope' });
      await completing;
      controller.expectOne(`${serviceRoot}/instances`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));

      store.select(runId, reviewOrderTaskId);

      expect(store.postconditionDetail()).toBeUndefined();
    });
  });

  describe('the skip gate', () => {
    // The strict predicate, not the permissive one: an unstated role is not a granted one, or every host
    // that has not wired roles would show an override button to everybody.
    it('is closed for a session whose roles the host never wired', () => {
      expect(store.canSkip()).toBe(false);
    });

    it('is closed for a signed-in user without the role', () => {
      session.set({ userId: 'clerk-user', roles: ['clerk'] });

      expect(store.canSkip()).toBe(false);
    });

    it('opens for a stated process owner', () => {
      session.set({ userId: 'boss', roles: [PROCESS_OWNER_ROLE] });

      expect(store.canSkip()).toBe(true);
    });
  });

  it('shows the instance list’s own loading and error state rather than a second one', () => {
    expect(store.isLoading()).toBe(false);
    expect(store.loadError()).toBeUndefined();

    store.reload();
    expect(store.isLoading()).toBe(true);

    controller.expectOne(`${serviceRoot}/instances`).flush({ errorId: 'oops' }, { status: 500, statusText: 'Server Error' });
    expect(store.loadError()).toBeTruthy();
  });
});
