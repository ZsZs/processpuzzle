import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { provideRouter } from '@angular/router';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO } from '../../domain/definition/test-artifact-definition';
import { OTHER_ROLE_DEFINITION_DTO, ROLE_DEFINITION_DTO } from '../../domain/definition/test-role-definition';
import { OTHER_TASK_DEFINITION_DTO, TASK_DEFINITION_DTO, THIRD_TASK_DEFINITION_DTO } from '../../domain/definition/test-task-definition';
import { OTHER_TOOL_DEFINITION_DTO, TOOL_DEFINITION_DTO } from '../../domain/definition/test-tool-definition';
import { pageOfWorkflows, WORKFLOW_DTO } from '../../domain/definition/test-workflow';
import { OTHER_WORKFLOW_INSTANCE_DTO, pageOfWorkflowInstances, WORKFLOW_INSTANCE_DTO } from '../../domain/execution/test-workflow-instance';
import { provideLogger } from 'ngx-logging-kit';

/**
 * What the dashboard's component specs need in common.
 *
 * Every one of them injects `WorkflowDashboardStore`, which injects six root stores, each of which loads
 * its collection on init — so six requests exist before any component has rendered. Answering them is
 * mechanical and identical in each spec, so it lives here rather than five times over.
 *
 * The English bundle is the real one, keyed under the `base_workflow` alias transloco flattens the scope
 * to. Spelled out rather than loaded from the asset file so that a spec asserts against the sentence it
 * expects, not against whatever the bundle happens to say this week.
 */
export const DASHBOARD_SERVICE_ROOT = 'http://localhost:3000/organizations/processpuzzle-testbed';

const DASHBOARD_TRANSLATIONS: Record<string, string> = {
  'base_workflow.task_instance.dashboard.title': 'My tasks',
  'base_workflow.task_instance.dashboard.scopes.mine': 'My tasks',
  'base_workflow.task_instance.dashboard.scopes.team': 'Team',
  'base_workflow.task_instance.dashboard.scopes.process': 'Process',
  'base_workflow.task_instance.dashboard.run': 'Run',
  'base_workflow.task_instance.dashboard.run_none': 'Pick a run to see its board.',
  'base_workflow.task_instance.dashboard.empty.mine': 'Nothing assigned to you.',
  'base_workflow.task_instance.dashboard.empty.team': 'Nothing to claim right now.',
  'base_workflow.task_instance.dashboard.empty.process': 'This run has no tasks.',
  'base_workflow.task_instance.dashboard.error': 'The runs could not be loaded.',
  'base_workflow.task_instance.dashboard.select_prompt': 'Select a task from the list.',
  'base_workflow.task_instance.dashboard.claim': 'Claim task',
  'base_workflow.task_instance.dashboard.locked': 'Open to anyone holding the required role.',
  'base_workflow.task_instance.dashboard.steps': 'Steps',
  'base_workflow.task_instance.dashboard.steps_none': 'This task declares no steps.',
  'base_workflow.task_instance.dashboard.step_user': 'user step',
  'base_workflow.task_instance.dashboard.step_service': 'service step',
  'base_workflow.task_instance.dashboard.step_tool_ui': 'open tool UI',
  'base_workflow.task_instance.dashboard.artifacts_in': 'Reads',
  'base_workflow.task_instance.dashboard.artifacts_out': 'Writes',
  'base_workflow.task_instance.dashboard.artifacts_none': 'This task declares no artifacts.',
  'base_workflow.task_instance.dashboard.not_created': 'not created yet',
  'base_workflow.task_instance.dashboard.required_state': 'required: {{state}}',
  'base_workflow.task_instance.dashboard.complete_blocked': 'Tick every step and bring each output into its required state to complete the task.',
  'base_workflow.task_instance.dashboard.context_variables': 'Context variables',
  'base_workflow.task_instance.dashboard.complete': 'Complete task',
  'base_workflow.task_instance.dashboard.skip': 'Skip',
  'base_workflow.task_instance.dashboard.skip_reason': 'Reason for skipping',
  'base_workflow.task_instance.dashboard.unstated_refusal': 'Not accepted yet, and the server gave no detail.',
  'base_workflow.task_instance.dashboard.unassigned': 'unassigned',
  'base_workflow.task_instance.dashboard.assigned_to': 'Assigned to',
  'base_workflow.task_instance.dashboard.comments': 'Comments',
  'base_workflow.task_instance.dashboard.comment_add': 'Add comment',
  'base_workflow.task_instance.dashboard.context': 'Context variables',
  'base_workflow.task_instance.dashboard.context_key': 'Key',
  'base_workflow.task_instance.dashboard.context_value': 'Value',
  'base_workflow.task_instance.dashboard.context_none': 'This run holds no context variables.',
};

/** HTTP, the tenant-bearing service root, and the dashboard's own labels. */
export function provideDashboardTesting(): (Provider | EnvironmentProviders)[] {
  return [
    provideHttpClient(),
    provideHttpClientTesting(),
    { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: DASHBOARD_SERVICE_ROOT } } },
    provideTranslocoTesting({ translations: { en: DASHBOARD_TRANSLATIONS } }),
    // The task detail hosts base-entity's COMMENTS control, whose base class logs and navigates.
    provideLogger({ level: 7 }),
    provideRouter([]),
  ];
}

/**
 * Answers the six collection reads the store's construction issues, by URL rather than in order — which
 * store is built first is an implementation detail of `withComputed`.
 *
 * `runs` defaults to both seeded instances: the active `order-fulfillment-workflow` run with a completed,
 * an active and a pending task, and the finished run whose last task is BLOCKED with a failed tool step.
 */
export function flushDashboardCatalogs(controller: HttpTestingController, ...runs: object[]): void {
  const content = runs.length > 0 ? runs : [WORKFLOW_INSTANCE_DTO, OTHER_WORKFLOW_INSTANCE_DTO];
  controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/instances?size=200`).flush(pageOfWorkflowInstances(...content));
  controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/tasks`).flush([TASK_DEFINITION_DTO, OTHER_TASK_DEFINITION_DTO, THIRD_TASK_DEFINITION_DTO]);
  controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/artifacts`).flush([ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO]);
  controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/workflows`).flush(pageOfWorkflows(WORKFLOW_DTO));
  controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/roles`).flush([ROLE_DEFINITION_DTO, OTHER_ROLE_DEFINITION_DTO]);
  controller.expectOne(`${DASHBOARD_SERVICE_ROOT}/tools`).flush([TOOL_DEFINITION_DTO, OTHER_TOOL_DEFINITION_DTO]);
}

/** The ids the specs address, named once so a fixture reorder is one edit rather than five. */
export const RUN_ID = WORKFLOW_INSTANCE_DTO.id;
export const REVIEW_ORDER_TASK_ID = WORKFLOW_INSTANCE_DTO.tasks[0].id;
export const APPROVE_SHIPMENT_TASK_ID = WORKFLOW_INSTANCE_DTO.tasks[1].id;
export const CONFIRM_DELIVERY_TASK_ID = WORKFLOW_INSTANCE_DTO.tasks[2].id;

/**
 * The one element matching `selector`, or a failed test naming what was missing.
 *
 * A helper rather than a `!` on every query: a non-null assertion is a lint warning in this workspace and,
 * worse, a missing element then fails as "cannot read properties of null" at whatever line happened to
 * touch it. This says which selector found nothing.
 */
export function required<T extends Element>(host: HTMLElement, selector: string): T {
  const found = host.querySelector<T>(selector);
  if (!found) throw new Error(`No element matched ${selector}`);
  return found;
}
