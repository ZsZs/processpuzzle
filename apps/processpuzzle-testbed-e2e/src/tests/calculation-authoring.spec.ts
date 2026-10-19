import { expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { PlatformApi } from '../support/platform.api';
import { EntityApi, ServiceRoots, serviceRoots } from '../support/race-sample.api';

const CALCULATIONS = '/base-calculation/samples/calculation-definition';

/** A one-table calculation of its own: the seeded `order-total` is shared, so a test that edits must not edit that. */
const tableCalculation = (id: string) => `calculation: ${id}
name: E2E tables
parameters:
  amount: { type: number, default: 5 }
tables:
  bracket:
    hitPolicy: FIRST
    inputs: [amount]
    output: tier
    rules:
      - { when: ['>= 100'], then: 2 }
      - { when: ['-'], then: 1 }
decisions:
  tier:
    table: bracket
`;

test.describe('Calculation authoring', () => {
  let roots: ServiceRoots;
  let platform: PlatformApi;
  let id: string;

  test.beforeEach(async ({ request, baseURL }) => {
    roots = await serviceRoots(request, baseURL as string);
    platform = new PlatformApi(request, roots);
    id = await platform.createCalculation(tableCalculation(`e2e-${randomUUID().slice(0, 8)}`));
  });

  test.afterEach(async () => {
    await platform.deleteCalculation(id);
  });

  const sourceEditor = (page: import('@playwright/test').Page) => page.locator('[data-testid="calculation-source-editor"] .cm-content');

  test('validating the seeded order-total lists its decisions in evaluation order', async ({ page }) => {
    await page.goto(`${CALCULATIONS}/order-total/source`);
    await expect(sourceEditor(page)).toContainText('calculation: order-total');

    await page.getByTestId('calculation-validate').click();

    await expect(page.getByTestId('calculation-valid')).toBeVisible();
    const decisions = await page.getByTestId('calculation-decisions').locator('tbody tr td:nth-child(2)').allTextContents();
    expect(decisions).toEqual(expect.arrayContaining(['lineTotal', 'subtotal', 'discountRate', 'total']));
    // `total` needs `subtotal` and `discountRate`, which need `lineTotal`: the order is the dependency order.
    expect(decisions.indexOf('lineTotal')).toBeLessThan(decisions.indexOf('subtotal'));
    expect(decisions.indexOf('subtotal')).toBeLessThan(decisions.indexOf('discountRate'));
    expect(decisions.indexOf('discountRate')).toBeLessThan(decisions.indexOf('total'));
  });

  test('a broken source is reported and never saved', async ({ page }) => {
    const original = await platform.calculationSource(id);
    await page.goto(`${CALCULATIONS}/${id}/source`);
    await expect(sourceEditor(page)).toContainText(`calculation: ${id}`);

    await sourceEditor(page).click();
    await page.keyboard.press('ControlOrMeta+End');
    await page.keyboard.type('\nbogus: [');
    await expect(page.getByTestId('calculation-unsaved')).toBeVisible();

    await page.getByTestId('calculation-save').click();

    await expect(page.getByTestId('calculation-errors')).toBeVisible();
    expect(await platform.calculationSource(id)).toBe(original);
  });

  test('a valid edit is saved', async ({ page }) => {
    await page.goto(`${CALCULATIONS}/${id}/source`);
    await expect(sourceEditor(page)).toContainText(`calculation: ${id}`);

    await sourceEditor(page).click();
    await page.keyboard.press('ControlOrMeta+End');
    await page.keyboard.type('\ndescription: edited by the e2e suite');
    await page.getByTestId('calculation-save').click();

    await expect(page.getByTestId('calculation-unsaved')).toBeHidden();
    await expect.poll(() => platform.calculationSource(id)).toContain('description: edited by the e2e suite');
  });

  test('a decision table is edited, saved and evaluated', async ({ page }) => {
    await page.goto(`${CALCULATIONS}/${id}/tables`);
    await expect(page.getByTestId('calculation-table-rule-1')).toBeVisible();

    // `change` events: the cells commit on blur.
    await page.getByTestId('calculation-table-cell-0-0').fill('>= 10');
    await page.getByTestId('calculation-table-cell-0-0').press('Tab');
    await page.getByTestId('calculation-table-result-0').fill('3');
    await page.getByTestId('calculation-table-result-0').press('Tab');
    await page.getByTestId('calculation-table-add-rule').click();
    await expect(page.getByTestId('calculation-table-rule-2')).toBeVisible();
    await page.getByTestId('calculation-tables-save').click();

    await expect.poll(() => platform.calculationSource(id)).toContain('>= 10');

    // 50 is now caught by the first rule, which the stored table did not have.
    await page.goto(`${CALCULATIONS}/${id}/evaluate`);
    await page.getByTestId('calculation-parameter-amount').fill('50');
    await page.getByTestId('calculation-evaluate').click();
    await expect(page.getByTestId('calculation-values')).toContainText('tier');
    await expect(page.getByTestId('calculation-values').locator('tr', { hasText: 'tier' })).toContainText('3');
    await expect(page.getByTestId('calculation-trace')).toBeVisible();
  });

  test('evaluating an order shows values and a trace, and stores no run', async ({ page, request }) => {
    const order = await new EntityApi(request, roots).findBy<{ orderNumber: string }>('order', 'orderNumber', 'ORD-1001');
    const runsBefore = (await platform.calculationRuns('order-total', order.id)).length;

    await page.goto(`${CALCULATIONS}/order-total/evaluate`);
    // The parameter is a text input until the order choices have loaded, and a select afterwards.
    await page.locator('select[data-testid="calculation-parameter-order"]').selectOption(order.id);
    await page.getByTestId('calculation-evaluate').click();

    // 2 x 19.99 + 1 x 49.90, no volume discount.
    await expect(page.getByTestId('calculation-values').locator('tr', { hasText: 'total' }).last()).toContainText('89.88');
    await expect(page.getByTestId('calculation-trace')).toBeVisible();
    expect((await platform.calculationRuns('order-total', order.id)).length).toBe(runsBefore);
  });
});
