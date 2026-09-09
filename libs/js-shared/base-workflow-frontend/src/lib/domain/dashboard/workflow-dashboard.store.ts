import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { httpErrorMessage } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { ArtifactDefinitionStore } from '../definition/artifact-definition.store';
import { RoleDefinitionStore } from '../definition/role-definition.store';
import { TaskDefinition } from '../definition/task-definition';
import { TaskDefinitionStore } from '../definition/task-definition.store';
import { WorkflowStore } from '../definition/workflow.store';
import { ArtifactInstance, TaskInstance, TaskInstanceStatus, WorkflowInstance } from '../execution/workflow-instance';
import { WorkflowInstanceStore } from '../execution/workflow-instance.store';
import { PropertyMap } from '../property-map';
import { CurrentUserContext, PROCESS_OWNER_ROLE } from './current-user.context';
import { DashboardTask, InboxScope, ResolvedArtifact } from './dashboard-task';
import { TaskActionService } from './task-action.service';

/**
 * The columns of the process board, in the order they are read.
 *
 * `SKIPPED` has no column of its own: a skipped task folds into `COMPLETED`, which is the decision
 * recorded in screen 5 — it is a way of finishing rather than a state to watch, and a column that is
 * empty in every healthy run is worse than a card that says why it is there. The card marks it; the
 * board does not reserve space for it.
 */
export const KANBAN_COLUMNS: TaskInstanceStatus[] = [TaskInstanceStatus.PENDING, TaskInstanceStatus.ACTIVE, TaskInstanceStatus.BLOCKED, TaskInstanceStatus.COMPLETED];

/**
 * What the three verbs need to address a task: the run, and the task's **definition** id.
 *
 * `taskDefinitionId` rather than `TaskInstance.id`, because that is what the endpoints resolve a task
 * through — see `TaskActionService` for the evidence and for why the contract does not say so. A named
 * type rather than an inline shape, so a caller cannot quietly hand over the instance id it happens to
 * have selected: `{ instanceId, taskId }` would have compiled and 404'd at runtime.
 *
 * A `DashboardTask` satisfies it structurally, so every call site passes the row it already has.
 */
export interface ActionTarget {
  readonly instanceId: string;
  readonly taskDefinitionId: string;
}

/** One column of the board: its status and the rows in it. */
export interface KanbanColumn {
  readonly status: TaskInstanceStatus;
  readonly rows: DashboardTask[];
}

/**
 * What a refusal says when the server did not say.
 *
 * `postconditionDetail` is nullable in the contract, so a rejected completion can arrive with no
 * explanation — and the form still has to say *something*, or an unchanged screen reads as a submission
 * that silently vanished. A key rather than a sentence, resolved by the form against the dashboard's own
 * i18n block.
 */
export const UNSTATED_REFUSAL = 'unstated_refusal';

type DashboardState = {
  scope: InboxScope;
  selectedInstanceId: string | undefined;
  selectedTaskId: string | undefined;
  /** True while one of the three verbs is in flight — what disables the action buttons. */
  isActing: boolean;
  /** A failed verb, as a message. Cleared when the next one starts. */
  actionError: string | undefined;
  /**
   * Why the last completion attempt was refused. Separate from {@link DashboardState.actionError}
   * because it is not an error: the call succeeded and the postcondition said no, which the form renders
   * inline beside the fields the user has to change.
   */
  postconditionDetail: string | undefined;
};

const initialState: DashboardState = {
  scope: 'mine',
  selectedInstanceId: undefined,
  selectedTaskId: undefined,
  isActing: false,
  actionError: undefined,
  postconditionDetail: undefined,
};

/**
 * The task dashboard's state: which queue is showing, which task is open, and what the last action did.
 *
 * **It owns no data.** Every row it renders is derived from stores that already exist —
 * `WorkflowInstanceStore` for the runs, and the task, artifact, workflow and role catalogs for the names
 * behind the ids. Injecting one of those stores *is* the request for its contents, since each is
 * root-scoped and loads on init, so this store's construction is what makes the reads; the same
 * arrangement `WorkflowModelerTabComponent` uses, and for the same reason. A dashboard that fetched its
 * own copy would be a second cache of the instance list, disagreeing with the Workflow Instance screens
 * the moment either one reloaded.
 *
 * Five catalogs, each earning its place:
 *
 * | Store | Answers |
 * | --- | --- |
 * | `WorkflowInstanceStore` | the runs, with their tasks and artifacts nested |
 * | `TaskDefinitionStore` | a task's steps, and which artifacts it reads and writes |
 * | `ArtifactDefinitionStore` | what to call an output nothing has produced yet |
 * | `WorkflowStore` | which role performs a task *in this workflow* (`performedBy`) |
 * | `RoleDefinitionStore` | that role's name and its base-entity `entityRoleId`, for role matching |
 *
 * Root-scoped rather than provided per route, so that arriving back at the dashboard shows the task that
 * was open — and so the five catalogs are shared with every other screen reading them rather than loaded
 * again per visit.
 */
export const WorkflowDashboardStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),

  withComputed((store) => {
    const instanceStore = inject(WorkflowInstanceStore);
    const taskDefinitionStore = inject(TaskDefinitionStore);
    const artifactDefinitionStore = inject(ArtifactDefinitionStore);
    const workflowStore = inject(WorkflowStore);
    const roleStore = inject(RoleDefinitionStore);
    const session = inject(CurrentUserContext);

    const instances = computed<WorkflowInstance[]>(() => instanceStore.entities());

    /**
     * Workflow id → (task definition id → the role that performs it).
     *
     * Built from `WorkflowTaskAssignment.performedBy`, which is the workflow's pick out of the task
     * definition's `performedByRoles`: the definition says who is *able*, the workflow says who *does*,
     * and the Team queue is about who does. Falls back to the definition's list where a workflow does not
     * resolve — see {@link isClaimableBySession}.
     */
    const performersByWorkflow = computed(() => {
      const byWorkflow = new Map<string, Map<string, string>>();
      workflowStore.entities().forEach((workflow) => {
        const performers = new Map<string, string>();
        (workflow.tasks ?? []).forEach((assignment) => {
          if (assignment.taskDefinitionId && assignment.performedBy) performers.set(assignment.taskDefinitionId, assignment.performedBy);
        });
        byWorkflow.set(workflow.id, performers);
      });
      return byWorkflow;
    });

    /**
     * Every task of every loaded run, flattened, each carrying the run it belongs to.
     *
     * This is what stands in for the cross-instance "my tasks" query the design proposal asked the
     * backend for: `listWorkflowInstances` already answers with full instances, tasks nested, so the join
     * costs nothing here and needs no new endpoint. See `dashboard-task.ts` for where that stops scaling.
     */
    const allTasks = computed<DashboardTask[]>(() =>
      instances().flatMap((instance) => {
        const performers = performersByWorkflow().get(instance.workflowId);
        return (instance.tasks ?? []).map((task) => ({
          task,
          instanceId: instance.id,
          workflowId: instance.workflowId,
          workflowName: instance.workflowName ?? instance.workflowId,
          entityId: instance.entityId,
          taskDefinitionId: task.taskDefinitionId,
          performedBy: performers?.get(task.taskDefinitionId),
        }));
      }),
    );

    const taskDefinitionsById = computed(() => new Map<string, TaskDefinition>(taskDefinitionStore.entities().map((definition) => [definition.id, definition])));
    const rolesById = computed(() => new Map(roleStore.entities().map((role) => [role.id, role])));

    /**
     * Whether the session may claim this row, matched against the three things a host might call a role —
     * the `RoleDefinition`'s own id, its name, or the `entityRoleId` it points at in base-entity's
     * registry. Which of them a session carries is the host's choice, so all three are offered rather
     * than one imposed; see {@link CurrentUserContext.mayHoldRole}, including why an unknown session matches
     * everything.
     */
    const isClaimableBySession = (row: DashboardTask): boolean => {
      const definition = taskDefinitionsById().get(row.taskDefinitionId);
      const roleIds = row.performedBy ? [row.performedBy] : (definition?.performedByRoles ?? []);
      if (roleIds.length === 0) return session.mayHoldRole();
      return roleIds.some((roleId) => {
        const role = rolesById().get(roleId);
        return session.mayHoldRole(roleId, role?.name, role?.entityRoleId);
      });
    };

    const myTasks = computed<DashboardTask[]>(() => {
      const userId = session.userId();
      // No session, no queue. Matching every task with `assignedTo === ''` would put somebody else's
      // unassigned work in this user's inbox.
      if (!userId) return [];
      return allTasks().filter((row) => row.task.assignedTo === userId);
    });

    const teamTasks = computed<DashboardTask[]>(() => allTasks().filter((row) => row.task.status === TaskInstanceStatus.ACTIVE && !row.task.assignedTo && isClaimableBySession(row)));

    const processTasks = computed<DashboardTask[]>(() => {
      const instanceId = store.selectedInstanceId();
      return instanceId ? allTasks().filter((row) => row.instanceId === instanceId) : [];
    });

    const selectedRow = computed<DashboardTask | undefined>(() => {
      const instanceId = store.selectedInstanceId();
      const taskId = store.selectedTaskId();
      if (!instanceId || !taskId) return undefined;
      return allTasks().find((row) => row.instanceId === instanceId && row.task.id === taskId);
    });

    const selectedInstance = computed<WorkflowInstance | undefined>(() => instances().find((instance) => instance.id === store.selectedInstanceId()));

    const selectedDefinition = computed<TaskDefinition | undefined>(() => {
      const row = selectedRow();
      // Keyed by the definition the *instance* was created from, never by the instance's own id. Those
      // are different ids, and reading a task definition by an instance id is a 404 that presents as a
      // task with no steps.
      return row ? taskDefinitionsById().get(row.taskDefinitionId) : undefined;
    });

    /**
     * The selected task's declared inputs or outputs, each resolved against this run's artifact instances
     * by `artifactDefinitionId` — and kept in the list when nothing matches, which is the "not created
     * yet" case screen 3 is about.
     */
    const resolveArtifacts = (direction: 'input' | 'output'): ResolvedArtifact[] => {
      const definition = selectedDefinition();
      if (!definition) return [];
      const instancesByDefinitionId = new Map<string, ArtifactInstance>((selectedInstance()?.artifacts ?? []).map((artifact) => [artifact.artifactDefinitionId, artifact]));
      const catalog = new Map(artifactDefinitionStore.entities().map((artifact) => [artifact.id, artifact]));
      const declared = direction === 'input' ? definition.inputs : definition.outputs;
      return declared.map((artifactDefinitionId) => {
        const instance = instancesByDefinitionId.get(artifactDefinitionId);
        const catalogEntry = catalog.get(artifactDefinitionId);
        return {
          artifactDefinitionId,
          direction,
          // The instance's own name wins over the catalog's: it is what this run called it.
          name: instance?.name ?? catalogEntry?.name ?? artifactDefinitionId,
          type: instance?.type ?? catalogEntry?.artifactType,
          instance,
        };
      });
    };

    return {
      instances,
      allTasks,
      myTasks,
      teamTasks,
      processTasks,

      /** The rows of the queue currently showing. */
      visibleTasks: computed<DashboardTask[]>(() => {
        switch (store.scope()) {
          case 'mine':
            return myTasks();
          case 'team':
            return teamTasks();
          default:
            return processTasks();
        }
      }),

      selectedRow,
      selectedInstance,
      selectedDefinition,
      selectedTask: computed<TaskInstance | undefined>(() => selectedRow()?.task),
      selectedInputs: computed<ResolvedArtifact[]>(() => resolveArtifacts('input')),
      selectedOutputs: computed<ResolvedArtifact[]>(() => resolveArtifacts('output')),

      /** Whether the open task is the session's to work on — what unlocks the steps and the completion form. */
      isMine: computed<boolean>(() => {
        const assignedTo = selectedRow()?.task.assignedTo;
        return !!assignedTo && assignedTo === session.userId();
      }),

      // The strict predicate, not the permissive one: a host that has not wired roles gets no Skip
      // button rather than one for everybody. See CurrentUserContext for why the two questions differ.
      canSkip: computed<boolean>(() => session.hasRole(PROCESS_OWNER_ROLE)),

      /** The runs the Process scope can be pointed at, newest first — what started last is read first. */
      selectableInstances: computed<WorkflowInstance[]>(() => [...instances()].sort((left, right) => (right.startedAt ?? '').localeCompare(left.startedAt ?? ''))),

      columns: computed<KanbanColumn[]>(() => {
        const rows = processTasks();
        return KANBAN_COLUMNS.map((status) => ({
          status,
          rows: rows.filter((row) => (row.task.status === TaskInstanceStatus.SKIPPED ? status === TaskInstanceStatus.COMPLETED : row.task.status === status)),
        }));
      }),

      /** The instance list's own flags, so the screens show one loading state rather than inventing a second. */
      isLoading: computed<boolean>(() => instanceStore.isLoading()),
      loadError: computed<string | undefined>(() => instanceStore.error()),
    };
  }),

  withMethods((store) => {
    const instanceStore = inject(WorkflowInstanceStore);
    const actions = inject(TaskActionService);
    const session = inject(CurrentUserContext);

    /**
     * Re-reads the instance list after a verb changed something.
     *
     * The whole list rather than the one task the verb answered with, because a verb changes more than its
     * own row: completing a task activates whatever depended on it, and skipping one does the same. The
     * response is discarded for that reason — patching only the acted-on task would leave the rest of the
     * board stale in a way that looks like the engine did nothing.
     */
    const reload = (): void => {
      instanceStore.load({});
    };

    const runAction = async <Result>(action: () => Promise<Result>): Promise<Result | undefined> => {
      patchState(store, { isActing: true, actionError: undefined });
      try {
        const result = await action();
        patchState(store, { isActing: false });
        // Synchronous, and not awaited: `BaseEntityStore.load` is an `rxMethod`, so it starts the request
        // and returns — the rows arrive by signal. The list's own `isLoading` is what the screens show
        // while they do, which is why `isActing` is already cleared here.
        reload();
        return result;
      } catch (error) {
        patchState(store, { isActing: false, actionError: httpErrorMessage(error) });
        return undefined;
      }
    };

    return {
      setScope: (scope: InboxScope): void => patchState(store, { scope }),

      select: (instanceId: string, taskId: string): void =>
        patchState(store, { selectedInstanceId: instanceId, selectedTaskId: taskId, postconditionDetail: undefined, actionError: undefined }),

      /** Points the Process scope at one run, dropping a selection that belonged to a different one. */
      selectInstance: (instanceId: string | undefined): void => {
        const keepTask = store.selectedInstanceId() === instanceId;
        patchState(store, { selectedInstanceId: instanceId, selectedTaskId: keepTask ? store.selectedTaskId() : undefined });
      },

      clearSelection: (): void => patchState(store, { selectedInstanceId: undefined, selectedTaskId: undefined, postconditionDetail: undefined }),

      reload,

      /**
       * Claims a task for the signed-in user. A no-op without a session, since the request would post an
       * empty `userId` for the backend to reject — doing nothing is better than an error the user has no
       * way to act on.
       *
       * Two teammates claiming the same row in the same moment both succeed today: `assignTask` refuses a
       * task that is not `ACTIVE` but not one already assigned to somebody else, so it is last-write-wins.
       * That race is open on the backend (open-questions #2) and cannot be closed here; the reload is what
       * at least shows the loser whose task it now is.
       */
      claim: async (row: ActionTarget): Promise<void> => {
        const userId = session.userId();
        if (!userId) return;
        await runAction(() => firstValueFrom(actions.assign(row.instanceId, row.taskDefinitionId, userId)));
      },

      /**
       * Submits a task. Answers whether it was accepted; a refusal is recorded in `postconditionDetail`
       * and leaves the task open, because that is what the server did with it.
       */
      complete: async (row: ActionTarget, context?: PropertyMap): Promise<boolean> => {
        patchState(store, { postconditionDetail: undefined });
        const result = await runAction(() => firstValueFrom(actions.complete(row.instanceId, row.taskDefinitionId, context)));
        if (!result) return false;
        if (!result.accepted) {
          patchState(store, { postconditionDetail: result.postconditionDetail ?? UNSTATED_REFUSAL });
          return false;
        }
        return true;
      },

      skip: async (row: ActionTarget, reason?: string): Promise<boolean> => {
        const result = await runAction(() => firstValueFrom(actions.skip(row.instanceId, row.taskDefinitionId, reason)));
        return result !== undefined;
      },
    };
  }),

  withDevtools('WorkflowDashboard'),
);
