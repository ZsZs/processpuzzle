import { expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { PlatformApi } from '../support/platform.api';
import { serviceRoots } from '../support/race-sample.api';

/**
 * A workflow that waits for an order in a state must say what happens when the order leaves that state some other
 * way: every transition out of it has to be the task's own output, or be caught by an event attached to the task.
 * A draft that does not is refused at release.
 *
 * Reads the *pending* release, which reports the blocking error without releasing anything. Calling the release
 * itself is deliberately not part of this spec: it releases everything pending in the organization, and a
 * regression here would then publish whatever else the stack holds, stage included.
 */
test.describe('Workflow release check', () => {
  let platform: PlatformApi;
  let ids: { workflow: string; task: string; role: string; artifact: string };

  test.beforeEach(async ({ request, baseURL }) => {
    platform = new PlatformApi(request, await serviceRoots(request, baseURL as string));
    const run = randomUUID().slice(0, 8);
    ids = { workflow: `e2e-wf-${run}`, task: `e2e-task-${run}`, role: `e2e-role-${run}`, artifact: `e2e-order-${run}` };
  });

  test.afterEach(async () => {
    await platform.deleteDefinitions([
      { collection: 'workflows', id: ids.workflow },
      { collection: 'tasks', id: ids.task },
      { collection: 'roles', id: ids.role },
      { collection: 'artifacts', id: ids.artifact },
    ]);
  });

  /** One task that waits for an order in DRAFT and confirms it; the order can also be cancelled from DRAFT. */
  const reviewWorkflow = (catchCancellation: boolean) => `---
role-definitions:
- { id: "${ids.role}", name: "E2E Role", description: "e2e" }
artifact-definitions:
- { id: "${ids.artifact}", name: "E2E Order", description: "the order", artifactType: "ENTITY", artifactTypeId: "order", stateMachineId: "order" }
task-definitions:
- id: "${ids.task}"
  name: "E2E Review"
  description: "review"
  performedByRoles: ["${ids.role}"]
  inputs: ["${ids.artifact}"]
  outputs: ["${ids.artifact}"]
  steps: [ { id: "s1", name: "Step", description: "d", stepType: "USER_STEP" } ]
workflows:
- id: "${ids.workflow}"
  name: "E2E Workflow"
  description: "e2e"
  roles: [ { roleDefinitionId: "${ids.role}" } ]
  artifacts: [ { artifactDefinitionId: "${ids.artifact}", objectName: "the_order" } ]
  tasks:
  - taskDefinitionId: "${ids.task}"
    performedBy: "${ids.role}"
    joinType: "ALL"
    artifactStates: [ { artifactDefinitionId: "${ids.artifact}", inputState: "DRAFT", outputState: "CONFIRMED" } ]
${
  catchCancellation
    ? `  events:
  - { id: "cancelled", name: "Order cancelled", eventDefinitionId: "OrderCancelledEvent", direction: "CATCH", joinType: "ALL", attachedTo: "${ids.task}" }
`
    : ''
}`;

  const problemsOf = async (workflowId: string) => (await platform.pendingProblems()).filter((problem) => problem.key === workflowId);

  test('a workflow that ignores a way its order can leave the state is reported as a blocking error', async () => {
    await platform.importWorkflows(reviewWorkflow(false));

    const problems = await problemsOf(ids.workflow);

    expect(problems).toEqual([expect.objectContaining({ severity: 'error', errorId: 'workflow.release.unhandledStateTransition' })]);
  });

  test('catching the cancellation on the task clears it', async () => {
    await platform.importWorkflows(reviewWorkflow(true));

    expect(await problemsOf(ids.workflow)).toEqual([]);
  });
});
