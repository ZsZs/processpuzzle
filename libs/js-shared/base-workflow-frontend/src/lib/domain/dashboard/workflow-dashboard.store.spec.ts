import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO } from '../definition/test-artifact-definition';
import { OTHER_ROLE_DEFINITION_DTO, ROLE_DEFINITION_DTO } from '../definition/test-role-definition';
import { OTHER_TASK_DEFINITION_DTO, TASK_DEFINITION_DTO, THIRD_TASK_DEFINITION_DTO } from '../definition/test-task-definition';
import { OTHER_TOOL_DEFINITION_DTO, TOOL_DEFINITION_DTO } from '../definition/test-tool-definition';
import { pageOfWorkflows, WORKFLOW_DTO } from '../definition/test-workflow';
import { TaskDefinitionStore } from '../definition/task-definition.store';
import { TaskInstanceStatus } from '../execution/workflow-instance';
import { OTHER_WORKFLOW_INSTANCE_DTO, pageOfWorkflowInstances, WORKFLOW_INSTANCE_DTO } from '../execution/test-workflow-instance';
import { CurrentUserContext, PROCESS_OWNER_ROLE } from './current-user.context';
import { flattenContext, WorkflowDashboardStore } from './workflow-dashboard.store';

/**
 * The dashboard reads six root stores and each one loads on init, so creating the store is what issues
 * six collection GETs. They are answered by URL rather than in order: which store is constructed first is
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
    controller.expectOne(`${serviceRoot}/instances?size=200`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO, OTHER_WORKFLOW_INSTANCE_DTO));
    controller.expectOne(`${serviceRoot}/tasks`).flush([TASK_DEFINITION_DTO, OTHER_TASK_DEFINITION_DTO, THIRD_TASK_DEFINITION_DTO]);
    controller.expectOne(`${serviceRoot}/artifacts`).flush([ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO]);
    controller.expectOne(`${serviceRoot}/workflows`).flush(pageOfWorkflows(WORKFLOW_DTO));
    controller.expectOne(`${serviceRoot}/roles`).flush([ROLE_DEFINITION_DTO, OTHER_ROLE_DEFINITION_DTO]);
    controller.expectOne(`${serviceRoot}/tools`).flush([TOOL_DEFINITION_DTO, OTHER_TOOL_DEFINITION_DTO]);
  };

  /**
   * Re-reads the runs with a different answer. `reload()` starts the request and returns — it is an
   * `rxMethod`, not a promise — so the flush has to follow the call rather than precede it.
   */
  const reloadWith = (...runs: object[]): void => {
    store.reload();
    controller.expectOne(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`).flush(pageOfWorkflowInstances(...runs));
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
        tasks: [{ ...WORKFLOW_INSTANCE_DTO.tasks[1], assignedTo: undefined }, WORKFLOW_INSTANCE_DTO.tasks[2], { ...WORKFLOW_INSTANCE_DTO.tasks[2], id: 'blocked-1', status: 'BLOCKED' }],
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

    // The seeded assignment of review-order: the order is read in DRAFT and left CONFIRMED.
    it('names the states this workflow requires of the task’s artifacts', () => {
      expect(store.selectedInputs()[0]).toMatchObject({ requiredState: 'DRAFT', satisfied: false });
      expect(store.selectedOutputs()[0]).toMatchObject({ requiredState: 'CONFIRMED', satisfied: true });
    });

    it('names the tool a service step calls', () => {
      expect(store.selectedTools().get('automated-check-tool')).toMatchObject({ name: TOOL_DEFINITION_DTO.name });
    });

    // No route in this TestBed mounts an artifact or a tool screen, so nothing is linked.
    it('links nothing the host does not mount', () => {
      expect(store.selectedInputs()[0].link).toBeUndefined();
      expect(store.selectedTools().get('automated-check-tool')?.link).toBeUndefined();
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

    // CANCELLED neither: interrupted or unreachable is how a run went, not a state to watch.
    it('folds a cancelled task into the completed column', () => {
      reloadWith({ ...WORKFLOW_INSTANCE_DTO, tasks: [{ ...WORKFLOW_INSTANCE_DTO.tasks[2], status: 'CANCELLED' }] });

      const completed = store.columns().find((column) => column.status === TaskInstanceStatus.COMPLETED);
      expect(completed?.rows.map((row) => row.task.status)).toEqual([TaskInstanceStatus.CANCELLED]);
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
      controller.expectOne(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
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
      controller.expectOne(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
    });

    /** A refusal is a 200 with the task still ACTIVE — the detail belongs beside the form, not in an error. */
    it('records why a completion was refused, without treating it as an error', async () => {
      const completing = store.complete(row());

      controller.expectOne(`${taskUrl}/complete`).flush({ accepted: false, postconditionDetail: 'shipment-approved: not set' });

      expect(await completing).toBe(false);
      expect(store.postconditionDetail()).toBe('shipment-approved: not set');
      expect(store.actionError()).toBeUndefined();
      controller.expectOne(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
    });

    // `postconditionDetail` is nullable, and an unchanged screen would read as a submission that vanished.
    it('substitutes a key when the server refuses without saying why', async () => {
      const completing = store.complete(row());

      controller.expectOne(`${taskUrl}/complete`).flush({ accepted: false });
      await completing;

      expect(store.postconditionDetail()).toBe('unstated_refusal');
      controller.expectOne(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
    });

    it('skips with the reason it was given', async () => {
      const skipping = store.skip(row(), 'customer cancelled');

      const request = controller.expectOne(`${taskUrl}/skip`);
      expect(request.request.body).toEqual({ reason: 'customer cancelled' });
      request.flush(WORKFLOW_INSTANCE_DTO.tasks[1]);

      expect(await skipping).toBe(true);
      controller.expectOne(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
    });

    it('surfaces a failed verb as a message and stops acting', async () => {
      const completing = store.complete(row());

      controller.expectOne(`${taskUrl}/complete`).flush({ errorId: 'task.not.active' }, { status: 409, statusText: 'Conflict' });

      expect(await completing).toBe(false);
      expect(store.actionError()).toBeTruthy();
      expect(store.isActing()).toBe(false);
      // No reload after a failure: nothing changed, so re-reading the list would only hide the message.
      controller.expectNone(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`);
    });

    it('drops the selection and re-reads the runs when the run was deleted meanwhile', async () => {
      store.select(runId, 'task-1');
      const completing = store.complete(row());

      controller.expectOne(`${taskUrl}/complete`).flush({ errorId: 'instance.not.found' }, { status: 404, statusText: 'Not Found' });

      expect(await completing).toBe(false);
      expect(store.actionError()).toBeTruthy();
      expect(store.selectedInstanceId()).toBeUndefined();
      controller.expectOne(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`).flush(pageOfWorkflowInstances());
    });

    it('clears a stale refusal when a different task is opened', async () => {
      const completing = store.complete(row());
      controller.expectOne(`${taskUrl}/complete`).flush({ accepted: false, postconditionDetail: 'nope' });
      await completing;
      controller.expectOne(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`).flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));

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

    controller.expectOne(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`).flush({ errorId: 'oops' }, { status: 500, statusText: 'Server Error' });
    expect(store.loadError()).toBeTruthy();
  });

  /**
   * Complete waits for every user step to be ticked and every output to be in its required state. Seeded
   * review-order has one service step and leaves the order CONFIRMED, which the run already is.
   */
  describe('readiness to complete', () => {
    const userStep = { id: 'read-note', name: 'Read the customer’s note', stepType: 'USER_STEP' };

    const withUserStep = (): void => {
      TestBed.inject(TaskDefinitionStore).load({});
      controller.expectOne(`${serviceRoot}/tasks`).flush([{ ...TASK_DEFINITION_DTO, steps: [...TASK_DEFINITION_DTO.steps, userStep] }, OTHER_TASK_DEFINITION_DTO, THIRD_TASK_DEFINITION_DTO]);
    };

    beforeEach(() => store.select(runId, reviewOrderTaskId));

    it('is ready when nothing is left to tick and every output is where it should be', () => {
      expect(store.isReadyToComplete()).toBe(true);
    });

    it('waits for an output to reach its required state', () => {
      reloadWith({ ...WORKFLOW_INSTANCE_DTO, artifacts: WORKFLOW_INSTANCE_DTO.artifacts.map((artifact) => ({ ...artifact, currentState: 'DRAFT' })) });

      expect(store.unmetOutputs().map((artifact) => artifact.artifactDefinitionId)).toEqual(['order-entity']);
      expect(store.isReadyToComplete()).toBe(false);
    });

    it('waits for an output nothing has produced yet', () => {
      reloadWith({ ...WORKFLOW_INSTANCE_DTO, artifacts: [] });

      expect(store.isReadyToComplete()).toBe(false);
    });

    it('waits for every user step to be ticked', () => {
      withUserStep();
      expect(store.pendingSteps().map((step) => step.id)).toEqual(['read-note']);
      expect(store.isReadyToComplete()).toBe(false);

      store.toggleStep('read-note');

      expect(store.isReadyToComplete()).toBe(true);
    });

    it('unticks on a second toggle', () => {
      store.toggleStep('read-note');
      store.toggleStep('read-note');

      expect(store.checkedStepIdSet().size).toBe(0);
    });

    // The ticks belong to one task: carried onto the next, they would show progress on work not done.
    it('forgets the ticks when another task is opened, not when the same one is', () => {
      store.toggleStep('read-note');

      store.select(runId, reviewOrderTaskId);
      expect(store.checkedStepIdSet().has('read-note')).toBe(true);

      store.select(runId, approveShipmentTaskId);
      expect(store.checkedStepIdSet().size).toBe(0);
    });
  });

  describe('refresh', () => {
    const instanceReads = () => controller.match(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`);

    it('re-reads the runs when the dashboard is entered again', () => {
      store.refresh();

      const reads = instanceReads();
      expect(reads).toHaveLength(1);
      reads[0].flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
      expect(store.instances().map((instance) => instance.id)).toEqual([runId]);
    });

    // The first visit finds the read the instance store started when it was built (the first page of every run), and
    // the dashboard works on the runs in progress: its own read replaces that one rather than waiting for it.
    it('replaces a read already in flight with its own', () => {
      store.reload();
      store.refresh();

      const reads = instanceReads();
      expect(reads.map((read) => read.cancelled)).toEqual([true, false]);
      reads[1].flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
      expect(store.instances().map((instance) => instance.id)).toEqual([runId]);
    });

    it('asks for the runs in progress, every page of them', () => {
      store.refresh();

      const [read] = instanceReads();
      expect(read.request.urlWithParams).toBe(`${serviceRoot}/instances?page=0&size=200&where=status%3D%3DACTIVE`);
      read.flush(pageOfWorkflowInstances(WORKFLOW_INSTANCE_DTO));
    });

    it('follows the pages when the runs in progress do not fit one', () => {
      store.refresh();
      const [first] = instanceReads();
      first.flush({ content: [WORKFLOW_INSTANCE_DTO], number: 0, size: 200, totalElements: 201, totalPages: 2 });

      const second = controller.expectOne((request) => request.urlWithParams.includes('/instances?page=1&'));
      second.flush({ content: [OTHER_WORKFLOW_INSTANCE_DTO], number: 1, size: 200, totalElements: 201, totalPages: 2 });

      expect(store.instances().map((instance) => instance.id)).toEqual([runId, OTHER_WORKFLOW_INSTANCE_DTO.id]);
    });
  });

  /** The records a run is bound to, named by the backend's `entityLabel` — no client-side lookup. */
  describe('the records behind a run', () => {
    const unlabelled = {
      ...WORKFLOW_INSTANCE_DTO,
      entityLabel: undefined,
      artifacts: WORKFLOW_INSTANCE_DTO.artifacts.map((artifact) => ({ ...artifact, entityLabel: undefined })),
    };

    it('names the open run and its bound artifacts by their labels', () => {
      store.select(runId, reviewOrderTaskId);

      expect(store.selectedSubjectName()).toBe('ORD-1001');
      expect(store.selectedObjectNames().get('order-entity')).toBe('ORD-1001');
      expect(store.selectedInputs()[0].recordName).toBe('ORD-1001');
    });

    it('leaves an unbound artifact unnamed, so it keeps its own name', () => {
      store.select(runId, reviewOrderTaskId);

      expect(store.selectedObjectNames().has('fulfillment-invoice')).toBe(false);
    });

    it('falls back to the record’s id when the backend answered no label', () => {
      reloadWith(unlabelled);
      store.select(runId, reviewOrderTaskId);

      expect(store.selectedSubjectName()).toBe('1');
      expect(store.selectedObjectNames().get('order-entity')).toBe('1');
    });

    it('labels every run in the picker by its workflow and its record', () => {
      expect(store.instanceLabels().get(runId)).toBe('Order Fulfillment Workflow · ORD-1001');
      expect(store.instanceLabels().get(OTHER_WORKFLOW_INSTANCE_DTO.id)).toBe('Order Fulfillment Workflow · 2');
    });
  });

  describe('the run’s context', () => {
    it('lists the open run’s variables, an event’s parameters under dotted keys', () => {
      store.selectInstance(runId);

      expect(store.selectedContext()).toEqual([
        { key: 'channel', value: 'web' },
        { key: 'priority', value: 'normal' },
        { key: 'order-created.subject', value: '1' },
        { key: 'order-created.orderNumber', value: 'ORD-1001' },
        { key: 'new_order', value: '1' },
      ]);
    });

    it('is empty without an open run, or for a run with no context', () => {
      expect(store.selectedContext()).toEqual([]);

      store.selectInstance(OTHER_WORKFLOW_INSTANCE_DTO.id);
      expect(store.selectedContext()).toEqual([]);
    });

    it('keeps lists, empty maps and absent values whole rather than inventing structure', () => {
      expect(flattenContext({ lines: [1, 2], empty: {}, missing: null, deep: { a: { b: true, n: 3 } } })).toEqual([
        { key: 'lines', value: '[1,2]' },
        { key: 'empty', value: '{}' },
        { key: 'missing', value: '—' },
        { key: 'deep.a.b', value: 'true' },
        { key: 'deep.a.n', value: '3' },
      ]);
    });
  });
});
