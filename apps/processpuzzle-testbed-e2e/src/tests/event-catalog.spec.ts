import { expect, Page, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { chooseOption, fillText } from '../support/embedded-form';
import { PlatformApi } from '../support/platform.api';
import { serviceRoots } from '../support/race-sample.api';

const CATALOG = '/base-event/samples/event-definition';

/**
 * The event catalog screens, on top of what the generated CRUD suite already covers (create, read, update and
 * delete of an Event Definition, and its parameters): that the seeded catalog is what the screens show, and that a
 * definition the backend refuses is refused to the user and not stored.
 */
test.describe('Event catalog', () => {
  let platform: PlatformApi;
  const created: string[] = [];

  test.beforeEach(async ({ request, baseURL }) => {
    platform = new PlatformApi(request, await serviceRoots(request, baseURL as string));
  });

  test.afterEach(async () => {
    for (const id of created.splice(0)) await platform.deleteEventDefinition(id);
  });

  const row = (page: Page, id: string) => page.locator('tr.mat-mdc-row').filter({ has: page.locator('a').filter({ hasText: new RegExp(`^${id}$`) }) });

  const openDefinition = async (page: Page, id: string) => {
    await page.goto(`${CATALOG}/list`);
    await page.locator('tr.mat-mdc-row a').filter({ hasText: new RegExp(`^${id}$`) }).first().click();
    await expect(page.getByTestId('eventDefinition-kind')).toBeVisible();
  };

  test('the list shows the seeded catalog with what each event binds to', async ({ page }) => {
    await page.goto(`${CATALOG}/list`);
    await expect(row(page, 'OrderCreatedEvent')).toBeVisible({ timeout: 20_000 });

    await expect(row(page, 'OrderCreatedEvent')).toContainText('SYSTEM');
    await expect(row(page, 'OrderCreatedEvent')).toContainText('order');
    await expect(row(page, 'OrderCreatedEvent')).toContainText('CREATED');
    await expect(row(page, 'OrderConfirmedEvent')).toContainText('STATE_CHANGED');
    await expect(row(page, 'OrderConfirmedEvent')).toContainText('CONFIRMED');
    // A message is thrown by a workflow, so it binds to no entity.
    await expect(row(page, 'InvoiceRequested')).toContainText('MESSAGE');
    await expect(row(page, 'InvoiceRequested')).not.toContainText('order');
    await expect(row(page, 'CalculationFrozenEvent')).toContainText('calculation-run');
  });

  test('a definition opens with its kind, subject, action and state', async ({ page }) => {
    await openDefinition(page, 'OrderConfirmedEvent');

    await expect(page.getByTestId('eventDefinition-kind')).toContainText('SYSTEM');
    await expect(page.getByTestId('eventDefinition-subjectType')).toContainText('order');
    await expect(page.getByTestId('eventDefinition-action')).toContainText('STATE_CHANGED');
    await expect(page.getByTestId('eventDefinition-state').locator('input')).toHaveValue('CONFIRMED');
  });

  test('an event lists the parameters a workflow can map from it', async ({ page }) => {
    await openDefinition(page, 'OrderCreatedEvent');

    const parameters = page.getByTestId('eventDefinition-parameters');
    await expect(parameters).toContainText('orderNumber');
    await expect(parameters).toContainText('customerName');
  });

  test('a message is created without a subject and is stored as a draft', async ({ page }) => {
    const id = `E2EMessage${randomUUID().slice(0, 8)}`;
    created.push(id);
    await page.goto(`${CATALOG}/new/details`);

    await fillText(page, 'eventDefinition-id', id);
    await fillText(page, 'eventDefinition-name', 'E2E message');
    await chooseOption(page, 'eventDefinition-kind', 'MESSAGE');
    await page.getByTestId('eventDefinition-save').click();

    await expect.poll(() => platform.eventDefinition(id)).toMatchObject({ kind: 'MESSAGE', releaseState: 'NEW' });
  });

  test('a system event without a subject is refused and not stored', async ({ page }) => {
    const id = `E2ESystem${randomUUID().slice(0, 8)}`;
    created.push(id);
    await page.goto(`${CATALOG}/new/details`);

    await fillText(page, 'eventDefinition-id', id);
    await fillText(page, 'eventDefinition-name', 'E2E system event');
    await chooseOption(page, 'eventDefinition-kind', 'SYSTEM');
    await page.getByTestId('eventDefinition-save').click();

    await expect(page.locator('simple-snack-bar, .mat-mdc-snack-bar-label').first()).toBeVisible();
    expect(await platform.eventDefinition(id)).toBeUndefined();
  });
});
