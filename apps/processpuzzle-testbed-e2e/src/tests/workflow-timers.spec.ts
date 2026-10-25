import { expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { PlatformApi, TaskInstanceSummary } from '../support/platform.api';
import { serviceRoots } from '../support/race-sample.api';

/**
 * Timers: a duration that interrupts a task, a date that has already passed, a cycle that reminds again and again,
 * and the timers that start a workflow on their own.
 *
 * A workflow only runs once it is *released*, and a release publishes everything pending in the organization and
 * cannot be undone — a released definition cannot be deleted while it is referenced. So these specs release once,
 * for a catalog of workflows with unique ids, and only when asked to: `E2E_ALLOW_RELEASE=true`, which the pull
 * request workflow sets for the stack it starts and throws away. They are skipped everywhere else, a developer's
 * stack and every deployed environment included. What they leave behind (released workflows, their runs) stays until
 * the stack is reset.
 *
 * The backend looks for due timers every `base-workflow.timers.interval`; testbed/.env.ci sets two seconds, which
 * is why durations here are seconds and the waits are tens of seconds at most.
 */
test.describe('Workflow timers', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  const run = randomUUID().slice(0, 8);
  const id = (name: string) => `e2e-${name}-${run}`;
  const WORKFLOW = {
    duration: id('duration'),
    past: id('past-date'),
    cycle: id('cycle'),
    startCycle: id('start-cycle'),
    startDate: id('start-date'),
  };
  /** A date long gone, so a timer on it is due the moment it is armed. */
  const PAST = '2020-01-01T00:00:00Z';

  let platform: PlatformApi;

  const step = '[ { id: "s1", name: "Step", description: "d", stepType: "USER_STEP" } ]';
  const task = (name: string) => `- { id: "${id(name)}", name: "${name} ${run}", description: "${name}", performedByRoles: ["${id('role')}"], steps: ${step} }`;
  const usesRole = `roles: [ { roleDefinitionId: "${id('role')}" } ]`;
  const work = `{ taskDefinitionId: "${id('work')}", performedBy: "${id('role')}", joinType: "ALL" }`;
  const catalog = `---
role-definitions:
- { id: "${id('role')}", name: "E2E Role ${run}", description: "e2e" }
task-definitions:
${task('work')}
${task('escalate')}
workflows:
- id: "${WORKFLOW.duration}"
  name: "E2E duration ${run}"
  description: "A duration timer interrupts the task and hands the run to an escalation."
  ${usesRole}
  tasks:
  - ${work}
  - { taskDefinitionId: "${id('escalate')}", performedBy: "${id('role')}", dependsOn: ["overdue"], joinType: "ALL" }
  events:
  - { id: "overdue", name: "Overdue", direction: "CATCH", joinType: "ALL", timer: { type: "DURATION", expression: "PT3S" }, attachedTo: "${id('work')}", interrupting: true }
- id: "${WORKFLOW.past}"
  name: "E2E past date ${run}"
  description: "A date that has passed."
  ${usesRole}
  tasks:
  - ${work}
  events:
  - { id: "deadline", name: "Deadline", direction: "CATCH", joinType: "ALL", timer: { type: "DATE", expression: "${PAST}" }, attachedTo: "${id('work')}", interrupting: true }
- id: "${WORKFLOW.cycle}"
  name: "E2E cycle ${run}"
  description: "A cycle that reminds three times and never interrupts."
  ${usesRole}
  tasks:
  - ${work}
  events:
  - { id: "reminder", name: "Reminder", direction: "CATCH", joinType: "ALL", timer: { type: "CYCLE", expression: "R3/PT3S" }, attachedTo: "${id('work')}", interrupting: false }
- id: "${WORKFLOW.startCycle}"
  name: "E2E start cycle ${run}"
  description: "Starts itself twice, three seconds apart."
  startEvents:
  - { id: "tick", name: "Tick", startType: "TIME_BASED_PRECONDITION", timer: { type: "CYCLE", expression: "R2/PT3S" } }
  ${usesRole}
  tasks:
  - ${work}
- id: "${WORKFLOW.startDate}"
  name: "E2E start date ${run}"
  description: "Starts itself once, at a date that has passed."
  startEvents:
  - { id: "once", name: "Once", startType: "TIME_BASED_PRECONDITION", timer: { type: "DATE", expression: "${PAST}" } }
  ${usesRole}
  tasks:
  - ${work}
`;

  test.beforeAll(async ({ playwright, baseURL }) => {
    test.skip(process.env['E2E_ALLOW_RELEASE'] !== 'true', 'Releases every pending definition of the organization and cannot be undone: set E2E_ALLOW_RELEASE=true on a stack that may be changed.');
    const request = await playwright.request.newContext();
    try {
      platform = new PlatformApi(request, await serviceRoots(request, baseURL as string));
      await platform.importWorkflows(catalog);
      await platform.releaseAll();
    } finally {
      await request.dispose();
    }
  });

  // The suite's own context ends with beforeAll, so each test brings its own.
  test.beforeEach(async ({ request, baseURL }) => {
    platform = new PlatformApi(request, await serviceRoots(request, baseURL as string));
  });

  test.afterEach(async () => {
    for (const workflowId of Object.values(WORKFLOW)) await platform.cancelWorkflowInstances({ workflowId });
  });

  type Run = Awaited<ReturnType<PlatformApi['workflowInstance']>> & { events: { eventUseId: string; status: string; fireCount: number }[] };

  /** Starts the workflow by hand, for the ones that have no start event of their own. */
  const startByHand = async (workflowId: string) => {
    const started = await platform.startWorkflow(workflowId);
    expect(started.status, `starting ${workflowId}: ${JSON.stringify(started.body)}`).toBe(201);
    return started.body['id'] as string;
  };

  const stateOf = (runId: string) => platform.workflowInstance(runId) as Promise<Run>;
  const taskOf = async (runId: string, name: string): Promise<TaskInstanceSummary | undefined> => (await stateOf(runId)).tasks.find((each) => each.taskDefinitionId === id(name));
  const countOf = async (workflowId: string) => (await platform.workflowInstances({ workflowId })).length;
  const settle = (milliseconds: number) => new Promise((resolve) => setTimeout(resolve, milliseconds));

  test('a duration timer interrupts the task it is attached to and hands the run to the escalation', async () => {
    const runId = await startByHand(WORKFLOW.duration);
    expect((await taskOf(runId, 'work'))?.status).toBe('ACTIVE');
    expect((await taskOf(runId, 'escalate'))?.status).toBe('PENDING');

    await expect.poll(async () => (await taskOf(runId, 'work'))?.status, { timeout: 40_000, message: 'the timer is due' }).toBe('CANCELLED');

    expect((await taskOf(runId, 'work'))?.['cancelReason']).toBe('interrupted by overdue');
    expect((await taskOf(runId, 'escalate'))?.status).toBe('ACTIVE');
    expect((await stateOf(runId)).events.find((event) => event.eventUseId === 'overdue')?.status).toBe('OCCURRED');
  });

  test('a date that has already passed fires at once', async () => {
    const runId = await startByHand(WORKFLOW.past);

    await expect.poll(async () => (await taskOf(runId, 'work'))?.status, { timeout: 40_000 }).toBe('CANCELLED');

    expect((await taskOf(runId, 'work'))?.['cancelReason']).toBe('interrupted by deadline');
  });

  test('a cycle timer fires its repetitions and leaves the task running', async () => {
    const runId = await startByHand(WORKFLOW.cycle);
    const fireCount = async () => (await stateOf(runId)).events.find((event) => event.eventUseId === 'reminder')?.fireCount;
    expect(await fireCount()).toBe(0);

    await expect.poll(fireCount, { timeout: 60_000, message: 'three repetitions' }).toBe(3);

    expect((await taskOf(runId, 'work'))?.status, 'a non-interrupting timer leaves the task alone').toBe('ACTIVE');
    // R3 is three repetitions and no more.
    await settle(8_000);
    expect(await fireCount()).toBe(3);
  });

  test('a time-based start event starts the workflow by itself, as often as its cycle allows', async () => {
    await expect.poll(() => countOf(WORKFLOW.startCycle), { timeout: 60_000, message: 'two starts' }).toBe(2);

    await settle(8_000);
    expect(await countOf(WORKFLOW.startCycle)).toBe(2);
    const [first] = await platform.workflowInstances({ workflowId: WORKFLOW.startCycle });
    expect(first.startEventId).toBe('tick');
  });

  test('a start event on a date that has passed starts the workflow once', async () => {
    await expect.poll(() => countOf(WORKFLOW.startDate), { timeout: 60_000 }).toBe(1);

    await settle(8_000);
    expect(await countOf(WORKFLOW.startDate)).toBe(1);
  });
});
