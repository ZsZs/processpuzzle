import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { map, Observable } from 'rxjs';
import { PropertyMap } from '../property-map';
import { TaskInstance } from '../execution/workflow-instance';
import { toTaskInstance, TaskInstanceDto } from '../execution/workflow-instance.mapper';

/** Wire shape of `CompleteTaskResponse` — a verdict, the task as it now stands, and why if it was refused. */
interface CompleteTaskResponseDto {
  accepted?: boolean;
  task?: TaskInstanceDto;
  postconditionDetail?: string;
}

/**
 * The answer to a completion attempt.
 *
 * `accepted: false` is a **success** at the HTTP level — 200, not 4xx — and that is the contract's
 * design rather than an oddity: a failed postcondition leaves the task `ACTIVE` for the user to fix and
 * resubmit, which is a different event from a request that could not be processed. So this is a
 * returned value and not a thrown error, and {@link CompleteTaskResult.postconditionDetail} is rendered
 * beside the form rather than in a snackbar.
 */
export interface CompleteTaskResult {
  readonly accepted: boolean;
  readonly task: TaskInstance | undefined;
  readonly postconditionDetail: string | undefined;
}

/**
 * The three verbs a task moves through: `POST …/assign`, `…/complete` and `…/skip`.
 *
 * **A task is addressed by its `taskDefinitionId`, not by `TaskInstance.id`** — the single most
 * surprising thing about this resource, and it is verified rather than assumed: every task-scoped
 * endpoint resolves the row through
 * `TaskInstanceRepository.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId`, and the backend's own
 * parameter is named `taskDefinitionId` (see `AssignTaskUseCase.assign`). Passing the UUID the reads
 * return answers `404 workflow.notFound`, on `getTaskInstance` as well as on the three verbs.
 *
 * It is defensible — a task appears at most once per workflow, so its definition id is unique within a
 * run — but `base-workflow-api.yaml` declares the parameter as a bare `taskId: string` with no
 * description, so nothing in the contract says which of the two ids it means. That silence is what makes
 * it a trap: `TaskInstance.id` is present, is a UUID, and is the obvious candidate. Naming the parameter
 * `taskDefinitionId` here is the whole defence.
 *
 * **Not a `BaseEntityRestService`, on purpose.** That base class is a repository over one collection —
 * a mapper, a resource segment, and CRUD built from them — and none of the three fits it. They are
 * nested two levels below `instances`, two of them take a body that is not the entity
 * (`AssignTaskRequest`, a skip reason), and `complete` answers with a verdict wrapping the entity
 * rather than the entity. Inheriting to get a URL builder would leave `findAll`, `add` and `update`
 * pointing at `instances` through a `TaskInstance` mapper, which is exactly the kind of surface that
 * gets called by accident.
 *
 * What it does share is where the organization comes from: `serviceRootOf` with the same
 * `WORKFLOW_SERVICE_ROOT` key every other service in this library passes to `super`, so the tenant
 * stays a deployment concern and no caller threads an `orgKey`.
 *
 * Reads are not here either. `WorkflowInstanceStore` already lists instances with their tasks and
 * artifacts nested, so the dashboard reads through the store the instance screens read through — one
 * cache, one loading flag — and this service exists only for what the store cannot express.
 */
@Injectable({ providedIn: 'root' })
export class TaskActionService {
  private readonly httpClient = inject(HttpClient);
  private readonly serviceRoot = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'WORKFLOW_SERVICE_ROOT');
  private readonly headers = new HttpHeaders({ 'Content-Type': 'application/json; charset=utf-8' });

  /**
   * `POST …/assign` — claim or hand over.
   *
   * One call for both, which is the design decision recorded in screen 4: a user claiming a task posts
   * their own id, a manager assigning someone else posts theirs, and the backend has no separate
   * concept to distinguish them. Answers `409` when the task is not `ACTIVE`; it does **not** refuse a
   * task already assigned to somebody else, so two claims in the same moment silently resolve
   * last-write-wins. That race is open on the backend (open-questions #2) and this method cannot close
   * it — {@link WorkflowDashboardStore.claim} reloads afterwards so the loser at least sees the truth.
   */
  assign(instanceId: string, taskDefinitionId: string, userId: string): Observable<TaskInstance> {
    return this.httpClient
      .post(this.taskUrl(instanceId, taskDefinitionId, 'assign'), { userId }, { headers: this.headers })
      .pipe(map((response) => toTaskInstance(response as TaskInstanceDto)));
  }

  /**
   * `POST …/complete` — submit the task, with whatever the completion form contributed to the workflow
   * context. Returns the verdict; see {@link CompleteTaskResult} for why a refusal is not an error.
   */
  complete(instanceId: string, taskDefinitionId: string, context?: PropertyMap): Observable<CompleteTaskResult> {
    const body = context && Object.keys(context).length > 0 ? { context } : {};
    return this.httpClient.post(this.taskUrl(instanceId, taskDefinitionId, 'complete'), body, { headers: this.headers }).pipe(
      map((response) => {
        const dto = (response ?? {}) as CompleteTaskResponseDto;
        return {
          // Absent rather than false is treated as a refusal: a body that does not say it was accepted
          // is not grounds for telling the user their work is done.
          accepted: dto.accepted === true,
          task: dto.task ? toTaskInstance(dto.task) : undefined,
          postconditionDetail: dto.postconditionDetail,
        };
      }),
    );
  }

  /** `POST …/skip` — the manager override, with the reason it was overridden. */
  skip(instanceId: string, taskDefinitionId: string, reason?: string): Observable<TaskInstance> {
    const body = reason ? { reason } : {};
    return this.httpClient
      .post(this.taskUrl(instanceId, taskDefinitionId, 'skip'), body, { headers: this.headers })
      .pipe(map((response) => toTaskInstance(response as TaskInstanceDto)));
  }

  /** `taskDefinitionId`, never `TaskInstance.id` — see the class comment on why that is not a typo. */
  private taskUrl(instanceId: string, taskDefinitionId: string, verb: string): string {
    return `${this.serviceRoot}/instances/${encodeURIComponent(instanceId)}/tasks/${encodeURIComponent(taskDefinitionId)}/${verb}`;
  }
}
