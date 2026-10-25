import { expect, Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { E2eUser, USER_FILE } from '../support/auth.paths';
import { OrderRun } from '../support/order-run';
import { PlatformApi } from '../support/platform.api';
import { EntityApi, ServiceRoots, serviceRoots } from '../support/race-sample.api';

/**
 * One order through the whole platform, the way the features are meant to work together: the order is created
 * and the calculation computes it while the order fulfillment workflow starts; the first task is claimed and
 * completed on the dashboard once the order is confirmed, which freezes the calculation; the invoice exchange
 * between two workflows goes through; the invoice is signed from the order's report and kept as a PDF; delivering
 * the order finishes the run and starts the feedback workflow; and the signed invoice is listed as issued.
 *
 * Each feature has specs of its own with the detail. This one is for what only shows when they are used together,
 * so it asserts the hand-over from one to the next, not each feature's every case.
 */
test.describe('Order lifecycle @auth', () => {
  test.describe.configure({ timeout: 180_000 });

  let roots: ServiceRoots;
  let entities: EntityApi;
  let platform: PlatformApi;
  let user: E2eUser;

  test.beforeEach(async ({ request, baseURL }) => {
    roots = await serviceRoots(request, baseURL as string);
    entities = new EntityApi(request, roots);
    platform = new PlatformApi(request, roots);
    user = JSON.parse(readFileSync(USER_FILE, 'utf-8')) as E2eUser;
  });

  test.afterEach(async () => {
    for (const entityId of entities.createdIds('order')) await platform.deleteWorkflowInstances({ entityId });
    await entities.removeAll();
  });

  const claimAndCompleteReview = async (page: Page, order: OrderRun) => {
    const review = await order.taskOf('review-order');
    const row = page.getByTestId(`task-row-${review?.id}`);

    await page.goto('/workflow-dashboard');
    await page.getByTestId('dashboard-scope-team').click();
    await expect(row).toBeVisible({ timeout: 20_000 });
    await page.getByTestId(`claim-task-${review?.id}`).click();
    await expect.poll(async () => (await order.taskOf('review-order'))?.assignedTo).toBeTruthy();

    // The task's output is the order, confirmed; the dashboard reads that when it loads.
    await order.transition('confirm');
    await page.goto('/workflow-dashboard');
    await page.getByTestId('dashboard-scope-mine').click();
    await expect(row).toBeVisible({ timeout: 20_000 });
    await row.click();
    await expect(page.getByTestId('complete-task')).toBeEnabled();
    await page.getByTestId('complete-task').click();
    await expect(row.getByTestId('task-status-COMPLETED')).toBeVisible();
  };

  const signInvoice = async (page: Page, order: OrderRun) => {
    await page.goto(`/base-workflow/samples/order/${order.orderId}/reports`);
    const picker = page.getByTestId('entity-reports-template');
    await picker.waitFor({ state: 'visible', timeout: 3000 }).catch(() => undefined);
    if (await picker.isVisible()) {
      await picker.click();
      await page.getByRole('option', { name: 'Invoice', exact: true }).click();
    }
    const report = page.getByTestId('report-page');
    await expect(report).toContainText(order.orderNumber, { timeout: 20_000 });
    await expect(report).toContainText('€10.00');

    await page.getByTestId('signature-accept').check();
    await page.getByTestId('signature-sign').click();
    await expect(page.getByTestId('signature-pdf-link').first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('signature-signer')).toHaveText(user.displayName);
  };

  test('an order is calculated, worked through two workflows, signed, delivered and issued', async ({ page, request }) => {
    const order = await OrderRun.start(entities, platform);
    const runsOfOrder = () => platform.calculationRuns('order-total', order.orderId);

    await test.step('creating the order calculates it and starts the fulfillment run', async () => {
      await expect.poll(async () => (await runsOfOrder()).length, { message: 'a calculation run' }).toBeGreaterThan(0);
      const [run] = await runsOfOrder();
      expect(run.status).toBe('COMPLETED');
      expect(run.frozen).toBe(false);
      expect((run['result'] as { values: Record<string, unknown> }).values).toMatchObject({ subtotal: 10, total: 10 });
      expect(await order.taskStatus('review-order')).toBe('ACTIVE');
    });

    await test.step('claiming and completing the review on the dashboard, once the order is confirmed, freezes the calculation', async () => {
      await claimAndCompleteReview(page, order);

      await expect.poll(async () => (await runsOfOrder()).some((run) => run.frozen), { message: 'the calculation is frozen' }).toBe(true);
      expect((await runsOfOrder()).find((run) => run.frozen)?.triggeredBy).toBe('FREEZE_STATE');
      await expect.poll(() => order.taskStatus('approve-shipment')).toBe('ACTIVE');
    });

    await test.step('shipping and approving throws the invoice request, and issuing the invoice answers it', async () => {
      await order.transition('ship');
      await order.work('approve-shipment');
      await expect.poll(() => order.otherRun('invoicing-workflow'), { message: 'the invoicing run' }).toBeDefined();
      const invoicing = await order.otherRun('invoicing-workflow');
      expect(invoicing?.context).toMatchObject({ orderId: order.orderId, orderNumber: order.orderNumber });

      await order.work('issue-invoice', { invoiceNumber: 'INV-E2E-LIFECYCLE' }, invoicing?.id);

      await expect.poll(() => order.taskStatus('confirm-delivery'), { message: 'the catch is answered' }).toBe('ACTIVE');
    });

    await test.step('the invoice is signed from the order\'s report, which the calculated total fills in', async () => {
      await signInvoice(page, order);
    });

    await test.step('delivering the order finishes the run and starts the feedback workflow', async () => {
      await order.transition('deliver');
      await order.work('confirm-delivery');

      await expect.poll(async () => (await order.state()).status).toBe('COMPLETED');
      await expect.poll(() => order.otherRun('customer-feedback-workflow'), { message: 'the feedback run' }).toBeDefined();
      expect((await order.otherRun('customer-feedback-workflow'))?.startEventId).toBe('order-delivered');
    });

    await test.step('the signed invoice is listed under Issued Documents', async () => {
      await page.goto('/base-workflow/samples/issued-document/list');
      const filter = page.getByTestId('issuedDocument-filter');
      await filter.fill(order.orderId);
      await filter.dispatchEvent('keyup');
      const listed = page.locator('tr.mat-mdc-row').filter({ hasText: order.orderId });
      await expect(listed).toHaveCount(1, { timeout: 20_000 });
      await expect(listed).toContainText(user.displayName);

      // What was kept is the rendered report, as a PDF.
      const issued = await (await request.get(`${roots.backend}/issued-documents`, { params: { size: 200 } })).json();
      const entry = (Array.isArray(issued) ? issued : issued.content).find((each: { subjectId: string }) => each.subjectId === order.orderId);
      const pdf = await request.get(`${roots.objectStore}/objects/${entry.pdf.bucket}/${entry.pdf.objectId}`);
      expect(pdf.status()).toBe(200);
      expect((await pdf.body()).subarray(0, 5).toString('latin1')).toBe('%PDF-');
    });
  });
});
