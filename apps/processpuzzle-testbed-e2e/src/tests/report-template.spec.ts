import { expect, Page, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { PlatformApi } from '../support/platform.api';
import { printCalls, stubPrint } from '../support/print-stub';
import { EntityApi, ServiceRoots, serviceRoots } from '../support/race-sample.api';

const DOCUMENTS = '/base-document/samples/document';
const ORDERS = '/base-workflow/samples/order';

interface OrderPayload {
  orderNumber: string;
  customerName: string;
  status: string;
  total: number;
  lineItems: { productName: string; quantity: number; unitPrice: number }[];
}

/**
 * The seeded `order-invoice` report template: an order port, data fields bound to `order.*`, an entity table over
 * `order.lineItems` and a signature widget. Signing is covered by the document-signature spec; this one is about
 * the data reaching the page and the page reaching paper.
 */
test.describe('Report template', () => {
  let roots: ServiceRoots;
  let entities: EntityApi;
  let templateId: string;
  let platform: PlatformApi;
  const createdDocuments: string[] = [];

  test.beforeEach(async ({ page, request, baseURL }) => {
    roots = await serviceRoots(request, baseURL as string);
    entities = new EntityApi(request, roots);
    platform = new PlatformApi(request, roots);
    const documents = await (await request.get(`${roots.backend}/documents`, { params: { size: 100 } })).json();
    templateId = (documents.content ?? documents).find((document: { slug: string }) => document.slug === 'order-invoice').id;
    await stubPrint(page);
  });

  test.afterEach(async () => {
    await entities.removeAll();
    for (const id of createdDocuments.splice(0)) await platform.deleteDocument(id);
  });

  const reportPage = (page: Page) => page.getByTestId('report-page');

  /** The order choices load after the picker renders, so an early open shows an empty panel: close it and retry. */
  async function pickOrder(page: Page, orderNumber: string): Promise<void> {
    const option = page.getByRole('option', { name: orderNumber });
    await expect(async () => {
      await page.getByTestId('report-parameter-order').focus();
      await page.keyboard.press('Enter');
      try {
        await expect(option).toBeVisible({ timeout: 1500 });
      } catch (error) {
        await page.keyboard.press('Escape');
        throw error;
      }
    }).toPass();
    await option.click();
  }

  /** An order's Reports tab, which shows the seeded Invoice: the only published report template of the Order type. */
  async function openInvoice(page: Page, orderId: string): Promise<void> {
    await page.goto(`${ORDERS}/${orderId}/reports`);
  }

  async function openPreview(page: Page): Promise<void> {
    await page.goto(`${DOCUMENTS}/${templateId}/details`);
    await page.getByTestId('document-show-preview').click();
  }

  test('previewing the template asks for an order, then fills the invoice from it', async ({ page }) => {
    const order = await entities.findBy<OrderPayload>('order', 'orderNumber', 'ORD-1001');
    await openPreview(page);

    await expect(page.getByTestId('report-missing-parameters')).toBeVisible();
    await pickOrder(page, 'ORD-1001');

    await expect(page.getByTestId('report-missing-parameters')).toBeHidden();
    await expect(reportPage(page)).toContainText('ORD-1001');
    await expect(reportPage(page)).toContainText('Alice Johnson');
    await expect(reportPage(page)).toContainText('Widget A');
    await expect(reportPage(page)).toContainText('Widget B');
    await expect(reportPage(page)).toContainText('€89.88');
    // The parameter bar keeps the choice in the URL, so a preview of one invoice is a link.
    await expect(page).toHaveURL(new RegExp(`[?&]order=${order.id}`));
  });

  // The Preview tab documents "a preview of one invoice is a link": opened directly, it shows that invoice.
  test('a preview opened from a shared link shows the invoice', async ({ page }) => {
    const order = await entities.findBy<OrderPayload>('order', 'orderNumber', 'ORD-1001');
    await page.goto(`${DOCUMENTS}/${templateId}/preview?order=${order.id}`);
    // The page loads the document, its draft and then the order, one after the other.
    await expect(reportPage(page)).toContainText('Alice Johnson', { timeout: 20_000 });
  });

  test('an order\'s Reports tab runs the template for that order', async ({ page }) => {
    const run = randomUUID().slice(0, 8);
    const order = await entities.create<OrderPayload>('order', {
      orderNumber: `E2E-${run}`,
      customerName: `Customer ${run}`,
      status: 'DRAFT',
      total: 30,
      lineItems: [{ productName: `Item ${run}`, quantity: 3, unitPrice: 10 }],
    });

    await openInvoice(page, order.id);

    await expect(reportPage(page)).toContainText(`E2E-${run}`);
    await expect(reportPage(page)).toContainText(`Customer ${run}`);
    await expect(reportPage(page)).toContainText(`Item ${run}`);
    await expect(reportPage(page)).toContainText('30.00');
  });

  // A template nobody has published is its author's: offering it on every order's Reports tab made a draft somebody
  // left the report an order opened with, saying "not published yet" in place of the invoice.
  test('a draft template is not offered on the Reports tab of an order', async ({ page }) => {
    const draft = await platform.createReportTemplate(`e2e-${randomUUID().slice(0, 8)}`, 'E2E draft invoice', { name: 'order', entityType: 'Order' });
    createdDocuments.push(draft);
    const run = randomUUID().slice(0, 8);
    const order = await entities.create<OrderPayload>('order', {
      orderNumber: `E2E-${run}`,
      customerName: `Customer ${run}`,
      status: 'DRAFT',
      total: 10,
      lineItems: [{ productName: 'Item', quantity: 1, unitPrice: 10 }],
    });

    await openInvoice(page, order.id);

    await expect(reportPage(page)).toContainText(`E2E-${run}`, { timeout: 20_000 });
    await expect(page.getByTestId('entity-reports-template'), 'one published template: nothing to choose between').toHaveCount(0);
    await expect(page.getByTestId('entity-reports-not-published')).toHaveCount(0);
  });

  test('a field and a table added to a template appear in its preview', async ({ page }) => {
    const id = await platform.createReportTemplate(`e2e-${randomUUID().slice(0, 8)}`, 'E2E invoice', { name: 'order', entityType: 'Order' });
    createdDocuments.push(id);
    // Opened by its link, as a template is when it is shared or the page is reloaded.
    await page.goto(`${DOCUMENTS}/${id}/content`);
    await page.getByTestId('document-add-text-block').click();
    const editor = page.locator('.pp-document-text-block [contenteditable="true"]');
    await editor.click();
    await page.keyboard.type('Dear ');

    await page.getByTestId('document-text-block-insert-field').click();
    await page.getByTestId('data-field-path').click();
    await page.getByRole('option', { name: /customer/i }).first().click();
    await page.getByTestId('data-field-insert').click();
    await expect(page.getByTestId('data-field-order.customerName')).toBeVisible();

    await page.getByTestId('document-add-widget-block').click();
    await page.getByRole('menuitem', { name: 'Entity table' }).click();
    // A new table block is empty until its settings are opened from the block.
    await page.locator('[data-testid^="document-table-settings-"]').click();
    await page.getByTestId('table-settings-source').click();
    await page.getByRole('option', { name: /line/i }).first().click();
    await page.getByTestId('table-settings-save').click();
    await expect(page.getByTestId('entity-table')).toBeVisible();

    // Autosave has an 800 ms debounce, and Preview reads the stored draft.
    await page.waitForTimeout(2500);
    await page.getByTestId('document-show-preview').click();
    await pickOrder(page, 'ORD-1001');

    await expect(reportPage(page)).toContainText('Dear Alice Johnson');
    await expect(reportPage(page)).toContainText('Widget A');
  });

  test('printing hands a copy of the report to the browser', async ({ page }) => {
    const order = await entities.findBy<OrderPayload>('order', 'orderNumber', 'ORD-1002');
    await openInvoice(page, order.id);
    await expect(reportPage(page)).toContainText('Bob Meier');

    await page.getByTestId('report-print').click();

    // The copy is printed from a hidden iframe after a short delay for its style sheets.
    await expect.poll(() => printCalls(page)).toBeGreaterThan(0);
  });
});
