import { randomUUID } from 'node:crypto';
import { PlatformApi } from './platform.api';

/**
 * A workflow of a test's own: one role and two tasks, the second after the first, with unique ids. The seeded
 * workflows are shared and some of their behaviour (events starting them, a saved layout that cannot be deleted)
 * is global, so a test that changes or arranges a workflow works on one it created.
 */
export interface OwnWorkflow {
  workflow: string;
  role: string;
  first: string;
  second: string;
  firstName: string;
  secondName: string;
  yaml: string;
}

export function ownWorkflow(run = randomUUID().slice(0, 8)): OwnWorkflow {
  const workflow = `e2e-wf-${run}`;
  const role = `e2e-role-${run}`;
  const first = `e2e-first-${run}`;
  const second = `e2e-second-${run}`;
  const step = '[ { id: "s1", name: "Step", description: "d", stepType: "USER_STEP" } ]';
  const yaml = `---
role-definitions:
- { id: "${role}", name: "E2E Role ${run}", description: "e2e" }
task-definitions:
- id: "${first}"
  name: "First ${run}"
  description: "first"
  performedByRoles: ["${role}"]
  steps: ${step}
- id: "${second}"
  name: "Second ${run}"
  description: "second"
  performedByRoles: ["${role}"]
  steps: ${step}
workflows:
- id: "${workflow}"
  name: "E2E Workflow ${run}"
  description: "e2e"
  roles: [ { roleDefinitionId: "${role}" } ]
  tasks:
  - { taskDefinitionId: "${first}", performedBy: "${role}", joinType: "ALL" }
  - { taskDefinitionId: "${second}", performedBy: "${role}", dependsOn: ["${first}"], joinType: "ALL" }
`;
  return { workflow, role, first, second, firstName: `First ${run}`, secondName: `Second ${run}`, yaml };
}

export async function importOwnWorkflow(platform: PlatformApi): Promise<OwnWorkflow> {
  const own = ownWorkflow();
  await platform.importWorkflows(own.yaml);
  return own;
}

/** Removes what {@link importOwnWorkflow} created, the workflow first so nothing is left referencing a task. */
export async function removeOwnWorkflow(platform: PlatformApi, own: OwnWorkflow): Promise<void> {
  await platform.deleteDefinitions([
    { collection: 'workflows', id: own.workflow },
    { collection: 'tasks', id: own.first },
    { collection: 'tasks', id: own.second },
    { collection: 'roles', id: own.role },
  ]);
}
