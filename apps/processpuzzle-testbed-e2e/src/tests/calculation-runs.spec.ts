import { expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { PlatformApi } from '../support/platform.api';
import { EntityApi, ServiceRoots, serviceRoots } from '../support/race-sample.api';

const RUNS = '/base-calculation/samples/calculation-runs';

interface Line {
  productName: string;
  quantity: number;
  unitPrice: number;
}
interface OrderPayload {
  orderNumber: string;
  customerName: string;
  status: string;
  total: number;
  lineItems: Line[];
}

/**
 * The seeded `order-total` calculation, which recomputes an order whenever it changes and freezes it on CONFIRMED.
 *
 * Each test creates its own order. `total` must equal the line sum on the way in (a seeded rule on Order), and the
 * calculation then writes its own total back — which is how the volume discount shows up.
 */
test.describe('Calculation runs', () => {
  let roots: ServiceRoots;
  let entities: EntityApi;
  let platform: PlatformApi;

  test.beforeEach(async ({ request, baseURL }) => {
    roots = await serviceRoots(request, baseURL as string);
    entities = new EntityApi(request, roots);
    platform = new PlatformApi(request, roots);
  });

  test.afterEach(async () => {
    await entities.removeAll();
  });

  const order = (quantity: number, unitPrice: number) =>
    entities.create<OrderPayload>('order', {
      orderNumber: `E2E-${randomUUID().slice(0, 8)}`,
      customerName: 'E2E Customer',
      status: 'DRAFT',
      total: quantity * unitPrice,
      lineItems: [{ productName: 'E2E item', quantity, unitPrice }],
    });

  const runsOf = (id: string) => platform.calculationRuns('order-total', id);

  test('creating an order runs the calculation, which writes the discounted total back', async () => {
    // 2000 is past the 1000 threshold of the volume table: a 3 % discount.
    const created = await order(1, 2000);

    await expect.poll(async () => (await entities.get<OrderPayload>('order', created.id)).payload.total).toBe(1940);
    const [run] = await runsOf(created.id);
    expect(run.status).toBe('COMPLETED');
  });

  test('the runs screen shows a run with its trace and the source it ran', async ({ page }) => {
    const created = await order(2, 10);
    await expect.poll(async () => (await runsOf(created.id)).length).toBeGreaterThan(0);
    const [run] = await runsOf(created.id);

    await page.goto(RUNS);
    await page.getByTestId('calculation-runs-filter-subject').fill(created.id);
    await page.getByTestId('calculation-runs-filter-subject').press('Tab');
    await page.getByTestId('calculation-runs-refresh').click();
    await page.getByTestId(`calculation-run-${run.id}`).click();

    await expect(page.getByTestId('calculation-run-details')).toBeVisible();
    await expect(page.getByTestId('calculation-trace')).toBeVisible();
    await expect(page.getByTestId('calculation-run-source')).toContainText('calculation: order-total');
  });

  test('a frozen run keeps its order from being recalculated', async ({ page }) => {
    const created = await order(2, 10);
    await expect.poll(async () => (await runsOf(created.id)).length).toBeGreaterThan(0);
    const [run] = await runsOf(created.id);

    await page.goto(RUNS);
    await page.getByTestId('calculation-runs-filter-subject').fill(created.id);
    await page.getByTestId('calculation-runs-filter-subject').press('Tab');
    await page.getByTestId('calculation-runs-refresh').click();
    await page.getByTestId(`calculation-run-${run.id}`).click();
    await page.getByTestId('calculation-run-freeze').click();
    await expect(page.getByTestId(`calculation-run-${run.id}`)).toContainText('✓');
    await expect(page.getByTestId('calculation-run-freeze')).toBeDisabled();

    const runsBefore = (await runsOf(created.id)).length;
    const current = await entities.get<OrderPayload>('order', created.id);
    await entities.update('order', created.id, current.version ?? 0, { ...current.payload, customerName: 'Renamed after freeze' });

    // Nothing to wait *for*: a run that wrongly started would land within moments of the update.
    await page.waitForTimeout(2000);
    expect((await runsOf(created.id)).length).toBe(runsBefore);
  });

  test('confirming an order freezes its calculation', async ({ page }) => {
    const created = await order(2, 10);
    await expect.poll(async () => (await runsOf(created.id)).length).toBeGreaterThan(0);

    const current = await entities.get<OrderPayload>('order', created.id);
    const result = await platform.fireTransition('order', created.id, 'confirm', current.version ?? 0);
    expect(result.success).toBe(true);

    await expect.poll(async () => (await runsOf(created.id)).some((run) => run.frozen)).toBe(true);
    const frozen = (await runsOf(created.id)).find((run) => run.frozen);
    expect(frozen?.triggeredBy).toBe('FREEZE_STATE');

    await page.goto(RUNS);
    await page.getByTestId('calculation-runs-filter-subject').fill(created.id);
    await page.getByTestId('calculation-runs-filter-subject').press('Tab');
    await page.getByTestId('calculation-runs-refresh').click();
    await expect(page.getByTestId(`calculation-run-${frozen?.id}`)).toContainText('✓');
  });
});
