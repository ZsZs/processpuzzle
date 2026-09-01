import { ArtifactType } from '../definition/artifact-definition';
import { ArtifactInstance, TaskInstance, TaskInstanceStatus } from '../execution/workflow-instance';

/**
 * The projections the task dashboard renders. None of them is a contract schema: each is a
 * `TaskInstance` or an `ArtifactInstance` with the context the screens need beside it, assembled from
 * what `GET /instances` already returned.
 *
 * That assembly is the whole reason this file exists, and it is what removes the two "backend change
 * needed" notes the design proposal carried. `TaskInstance` has no `workflowInstanceId`, and there is
 * no cross-instance task query — but `listWorkflowInstances` answers with *full* instances, tasks and
 * artifacts nested (see `PageOf_WorkflowInstance`, and `listWorkflowInstances`' own note on why the
 * list is not a lighter projection). So the instance a task belongs to is known at the moment the task
 * is read, and {@link DashboardTask} simply keeps it rather than asking the server to repeat it.
 *
 * The cost is honest and worth stating: the inbox is as wide as the instance list it is derived from.
 * `WorkflowInstanceStore` pages that list like every other base-entity list, so at a volume where one
 * page no longer holds a user's open work, the fix is the convenience endpoint the proposal asked for
 * (`GET /tasks?assignedTo=…`) — not a fan-out here.
 */

/** Which queue the inbox is showing. */
export type InboxScope = 'mine' | 'team' | 'process';

/** Every scope, in the order the toggle shows them. */
export const INBOX_SCOPES: InboxScope[] = ['mine', 'team', 'process'];

/**
 * One task of one run, with the run named beside it.
 *
 * A flat object rather than a task holding a reference to its instance: the list renders the
 * workflow's name in the row and every action needs the instance id in its URL, so both are read on
 * every task and neither is worth a lookup. `performedBy` is the role that performs this task *in this
 * workflow* — `WorkflowTaskAssignment.performedBy`, the workflow's pick out of the task definition's
 * `performedByRoles` — which is what the Team queue filters on.
 */
export interface DashboardTask {
  readonly task: TaskInstance;
  readonly instanceId: string;
  readonly workflowId: string;
  readonly workflowName: string;
  /** The base-entity row this run is about, when it was started for one. */
  readonly entityId: string | undefined;
  /** `TaskDefinition.id` — what resolves the steps, inputs and outputs of this task. */
  readonly taskDefinitionId: string;
  /** `RoleDefinition.id` from the workflow's assignment, absent when the workflow does not resolve. */
  readonly performedBy: string | undefined;
}

/** Reads the status of a dashboard row without every caller repeating the optional chain. */
export function statusOf(row: DashboardTask): TaskInstanceStatus | undefined {
  return row.task.status;
}

/**
 * One declared input or output of a task, resolved against the run's artifact instances.
 *
 * Three states rather than two, which is what screen 3 of the design is about: the instance exists and
 * carries a state, the instance exists with no state machine attached, or the task declares an output
 * nothing has produced yet. The third is kept in the list rather than filtered out — an output that
 * does not exist yet is exactly what tells the user what this task is for.
 */
export interface ResolvedArtifact {
  readonly artifactDefinitionId: string;
  readonly direction: 'input' | 'output';
  /** Name from the artifact catalog, so an unproduced output still has something to call itself. */
  readonly name: string;
  readonly type: ArtifactType | undefined;
  /** Absent when nothing has produced this artifact in this run yet. */
  readonly instance: ArtifactInstance | undefined;
}
