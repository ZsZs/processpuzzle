import { expect, Page, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { E2eUser, USER_FILE } from '../support/auth.paths';
import { PlatformApi, TaskInstanceSummary } from '../support/platform.api';
import { EntityApi, ServiceRoots, serviceRoots } from '../support/race-sample.api';

const DASHBOARD = '/workflow-dashboard';

interface OrderPayload {
  orderNumber: string;
  customerName: string;
  status: string;
  total: number;
  lineItems: { productName: string; quantity: number; unitPrice: number }[];
}

/**
 * The task board as a user works from it. Creating an order starts the seeded order fulfillment workflow, so each
 * test has a run of its own whose first task, *Review Order*, is waiting for somebody to claim it.
 *
 * Signed in as the user the setup project registered. The testbed realm has no roles, so every task may be
 * claimed by anyone and nobody may skip one (that is an override for a process owner); the role rules are
 * exercised in processpuzzle-biz.
 */
test.describe('Task dashboard @auth', () => {
  let roots: ServiceRoots;
  let entities: EntityApi;
  let platform: PlatformApi;
  let user: E2eUser;
  let orderId: string;
  let runId: string;
  let tasks: Record<'review' | 'approve' | 'deliver', TaskInstanceSummary>;

  test.beforeEach(async ({ request, baseURL }) => {
    roots = await serviceRoots(request, baseURL as string);
    entities = new EntityApi(request, roots);
    platform = new PlatformApi(request, roots);
    user = JSON.parse(readFileSync(USER_FILE, 'utf-8')) as E2eUser;

    const created = await entities.create<OrderPayload>('order', {
      orderNumber: `E2E-${randomUUID().slice(0, 8)}`,
      customerName: 'Dashboard Customer',
      status: 'DRAFT',
      total: 10,
      lineItems: [{ productName: 'E2E item', quantity: 1, unitPrice: 10 }],
    });
    orderId = created.id;
    await expect.poll(async () => (await platform.workflowInstances({ workflowId: 'order-fulfillment-workflow', entityId: orderId })).length).toBe(1);
    const [instance] = await platform.workflowInstances({ workflowId: 'order-fulfillment-workflow', entityId: orderId });
    runId = instance.id;
    const run = await platform.workflowInstance(runId);
    const byDefinition = (definition: string) => run.tasks.find((task) => task.taskDefinitionId === definition) as TaskInstanceSummary;
    tasks = { review: byDefinition('review-order'), approve: byDefinition('approve-shipment'), deliver: byDefinition('confirm-delivery') };
  });

  test.afterEach(async () => {
    await entities.removeAll();
  });

  const row = (page: Page, task: TaskInstanceSummary) => page.getByTestId(`task-row-${task.id}`);
  const reviewTaskNow = async () => (await platform.workflowInstance(runId)).tasks.find((task) => task.id === tasks.review.id);

  const openTeamQueue = async (page: Page) => {
    await page.goto(DASHBOARD);
    await page.getByTestId('dashboard-scope-team').click();
    await expect(row(page, tasks.review)).toBeVisible({ timeout: 20_000 });
  };

  const claimReview = async (page: Page) => {
    await openTeamQueue(page);
    await page.getByTestId(`claim-task-${tasks.review.id}`).click();
    await expect.poll(async () => (await reviewTaskNow())?.assignedTo).toBeTruthy();
  };

  /** The dashboard reads what it shows when it loads, so a change made elsewhere needs a fresh visit. */
  const openMine = async (page: Page) => {
    await page.goto(DASHBOARD);
    await page.getByTestId('dashboard-scope-mine').click();
    await expect(row(page, tasks.review)).toBeVisible({ timeout: 20_000 });
    await row(page, tasks.review).click();
  };

  test('the first task of a new run waits in the team queue, offered to be claimed', async ({ page }) => {
    await openTeamQueue(page);

    await expect(row(page, tasks.review)).toContainText('Review Order');
    await expect(page.getByTestId(`claim-task-${tasks.review.id}`)).toBeVisible();
    // The later tasks are not offered: they are PENDING until the one before them is done.
    await expect(row(page, tasks.approve)).toHaveCount(0);
  });

  test('claiming a task moves it to my tasks and shows who holds it', async ({ page }) => {
    await claimReview(page);

    await page.getByTestId('dashboard-scope-team').click();
    await expect(row(page, tasks.review)).toHaveCount(0);
    await page.getByTestId('dashboard-scope-mine').click();
    await expect(row(page, tasks.review)).toBeVisible();
    await row(page, tasks.review).click();
    await expect(page.getByTestId('task-detail-assignee')).toContainText(user.displayName);
  });

  test('nobody may skip a task without a role', async ({ page }) => {
    await claimReview(page);
    await openMine(page);

    await expect(page.getByTestId('complete-task')).toBeVisible();
    await expect(page.getByTestId('skip-task')).toHaveCount(0);
    await expect(page.getByTestId('skip-reason')).toHaveCount(0);
  });

  test('a task cannot be completed until its order is in the required state, and completing hands the run on', async ({ page }) => {
    await claimReview(page);
    await openMine(page);
    await expect(page.getByTestId('complete-task')).toBeDisabled();
    await expect(page.getByTestId('complete-blocked')).toBeVisible();

    // The task's output is the order in CONFIRMED. Confirming it is what the Order screens do.
    const order = await entities.get<OrderPayload>('order', orderId);
    expect((await platform.fireTransition('order', orderId, 'confirm', order.version ?? 0)).success).toBe(true);

    await openMine(page);
    await expect(page.getByTestId('complete-task')).toBeEnabled();
    await expect(page.getByTestId('complete-blocked')).toHaveCount(0);

    await page.getByTestId('complete-task').click();

    await expect
      .poll(async () => (await platform.workflowInstance(runId)).tasks.map((task) => `${task.taskDefinitionId}:${task.status}`))
      .toEqual(expect.arrayContaining(['review-order:COMPLETED', 'approve-shipment:ACTIVE']));
    // A finished task stays in my tasks, with its new status.
    await expect(row(page, tasks.review).getByTestId('task-status-COMPLETED')).toBeVisible();
  });

  test('a comment stays on the task', async ({ page }) => {
    const comment = `Checked by the e2e suite ${randomUUID().slice(0, 6)}`;
    await claimReview(page);
    await openMine(page);

    await page.getByTestId('new-comment-content').locator('[contenteditable="true"]').click();
    await page.keyboard.type(comment);
    await page.getByTestId('task-comment-add').click();
    await expect(page.getByTestId('comment-card').filter({ hasText: comment })).toBeVisible();

    await openMine(page);
    await expect(page.getByTestId('comment-card').filter({ hasText: comment })).toBeVisible();
  });

  test('the process view follows one run, its tasks grouped by where they stand', async ({ page }) => {
    await page.goto(DASHBOARD);
    await page.getByTestId('dashboard-scope-process').click();
    await expect(page.getByTestId('dashboard-run').locator(`option[value="${runId}"]`)).toHaveCount(1, { timeout: 20_000 });
    await page.getByTestId('dashboard-run').selectOption(runId);

    await expect(page.getByTestId('board-column-ACTIVE').getByTestId(`board-card-${tasks.review.id}`)).toBeVisible();
    await expect(page.getByTestId('board-column-PENDING').getByTestId(`board-card-${tasks.approve.id}`)).toBeVisible();
    await expect(page.getByTestId('board-column-PENDING').getByTestId(`board-card-${tasks.deliver.id}`)).toBeVisible();
  });
});
