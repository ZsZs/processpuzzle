import { expect, Page, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { E2eUser, USER_FILE } from '../support/auth.paths';
import { PlatformApi } from '../support/platform.api';
import { EntityApi, ServiceRoots, serviceRoots } from '../support/race-sample.api';

const ORDERS = '/base-workflow/samples/order';
const DOCUMENTS = '/base-document/samples/document';
const ISSUED = '/base-workflow/samples/issued-document';

interface OrderPayload {
  orderNumber: string;
  customerName: string;
  status: string;
  total: number;
  lineItems: { productName: string; quantity: number; unitPrice: number }[];
}

/**
 * Signing a document for an object: the Sign button of a report's signature widget, and the Sign tab of a
 * document. A signature records who signed what, for which object; the widget goes on to render the report to a
 * PDF in the browser (real jsPDF output, not a stand-in) and keep it with the signature, and the issued documents
 * are what that produces.
 *
 * The signer is the signed-in user the setup project registered: signing needs a real token, and the backend
 * refuses a caller who has none.
 */
test.describe('Document signature @auth', () => {
  let roots: ServiceRoots;
  let entities: EntityApi;
  let platform: PlatformApi;
  let user: E2eUser;
  let invoiceId: string;
  let orderId: string;
  let orderNumber: string;

  test.beforeEach(async ({ request, baseURL }) => {
    roots = await serviceRoots(request, baseURL as string);
    entities = new EntityApi(request, roots);
    platform = new PlatformApi(request, roots);
    user = JSON.parse(readFileSync(USER_FILE, 'utf-8')) as E2eUser;
    invoiceId = await platform.documentIdBySlug('order-invoice');

    orderNumber = `E2E-${randomUUID().slice(0, 8)}`;
    const order = await entities.create<OrderPayload>('order', {
      orderNumber,
      customerName: 'Signing Customer',
      status: 'DRAFT',
      total: 10,
      lineItems: [{ productName: 'E2E item', quantity: 1, unitPrice: 10 }],
    });
    orderId = order.id;
  });

  test.afterEach(async () => {
    await entities.removeAll();
  });

  /** The order's report with the seeded Invoice chosen, so a draft template somebody left does not stand in for it. */
  const openInvoice = async (page: Page) => {
    await page.goto(`${ORDERS}/${orderId}/reports`);
    const picker = page.getByTestId('entity-reports-template');
    await picker.waitFor({ state: 'visible', timeout: 3000 }).catch(() => undefined);
    if (await picker.isVisible()) {
      await picker.click();
      await page.getByRole('option', { name: 'Invoice', exact: true }).click();
    }
    await expect(page.getByTestId('report-page')).toContainText(orderNumber, { timeout: 20_000 });
  };

  const signReport = async (page: Page) => {
    await page.getByTestId('signature-accept').check();
    await page.getByTestId('signature-sign').click();
    // While the PDF is rendered the report is cloned for the renderer, so the stamp is briefly in the page twice;
    // the PDF link is the sign that it is done and the clone is gone.
    await expect(page.getByTestId('signature-stamp').first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('signature-pdf-link').first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('signature-stamp')).toHaveCount(1, { timeout: 30_000 });
  };

  const signaturesOfOrder = () => platform.signatures(invoiceId, 'Order', orderId);

  test('Sign is offered only once the reader has accepted what they read', async ({ page }) => {
    await openInvoice(page);

    await expect(page.getByTestId('signature-sign')).toBeDisabled();
    await page.getByTestId('signature-accept').check();
    await expect(page.getByTestId('signature-sign')).toBeEnabled();
    await page.getByTestId('signature-accept').uncheck();
    await expect(page.getByTestId('signature-sign')).toBeDisabled();
    expect(await signaturesOfOrder()).toHaveLength(0);
  });

  test('signing a report stamps it with the signer and keeps a real PDF of it', async ({ page, request }) => {
    await openInvoice(page);

    await signReport(page);

    await expect(page.getByTestId('signature-signer')).toHaveText(user.displayName);
    await expect(page.getByTestId('signature-pdf-link')).toHaveText(`order-invoice-${orderId}.pdf`);
    const [signature] = await signaturesOfOrder();
    expect(signature.signedBy).toBe(user.displayName);
    await expect(page.getByTestId('signature-id')).toHaveText(signature.id.slice(0, 8));

    // What was kept is the rendered report: a PDF, with the order in it, not an empty file.
    expect(signature.pdf?.name).toBe(`order-invoice-${orderId}.pdf`);
    const pdf = await request.get(`${roots.objectStore}/objects/${signature.pdf?.bucket}/${signature.pdf?.objectId}`);
    expect(pdf.status()).toBe(200);
    expect(pdf.headers()['content-type']).toContain('application/pdf');
    const bytes = await pdf.body();
    expect(bytes.subarray(0, 5).toString('latin1')).toBe('%PDF-');
    expect(bytes.length).toBeGreaterThan(5_000);
  });

  test('a signed report opens signed and is not offered for signing again', async ({ page }) => {
    await openInvoice(page);
    await signReport(page);

    await openInvoice(page);

    await expect(page.getByTestId('signature-stamp')).toBeVisible();
    await expect(page.getByTestId('signature-signer')).toHaveText(user.displayName);
    await expect(page.getByTestId('signature-sign')).toHaveCount(0);
    expect(await signaturesOfOrder()).toHaveLength(1);
  });

  test('the signed report is listed under Issued Documents', async ({ page }) => {
    await openInvoice(page);
    await signReport(page);

    await page.goto(`${ISSUED}/list`);
    const filter = page.getByTestId('issuedDocument-filter');
    await filter.fill(orderId);
    await filter.dispatchEvent('keyup');

    const listed = page.locator('tr.mat-mdc-row').filter({ hasText: orderId });
    await expect(listed).toHaveCount(1, { timeout: 20_000 });
    await expect(listed).toContainText('Order Invoice');
    await expect(listed).toContainText(user.displayName);
    // The PDF's name is cut short in its cell, so only its start can be read.
    await expect(listed).toContainText('order-invoice-');
  });

  test('the Sign tab signs a document for an object, and signing again returns the same signature', async ({ page }) => {
    const signTab = `${DOCUMENTS}/${invoiceId}/sign?subjectType=Order&subjectId=${orderId}`;
    const sign = async () => {
      await page.getByTestId('document-sign-accept').locator('input').check();
      await page.getByTestId('document-sign-button').click();
      await expect(page.getByTestId('document-signed')).toBeVisible();
    };
    await page.goto(signTab);
    await expect(page.getByTestId('document-sign-none')).toBeVisible();
    await expect(page.getByTestId('document-sign-button')).toBeDisabled();
    await sign();

    const [signature] = await signaturesOfOrder();
    expect(signature.signedBy).toBe(user.displayName);

    expect(signature.signedById).toBeTruthy();

    // On the next visit the tab lists the signature and knows it is the user's own: it says so and offers no button.
    await page.goto(signTab);
    await expect(page.getByTestId(`document-signature-${signature.id}`)).toBeVisible();
    await expect(page.getByTestId('document-signed')).toBeVisible();
    await expect(page.getByTestId('document-sign-button')).toHaveCount(0);
    expect((await signaturesOfOrder()).map((each) => each.id)).toEqual([signature.id]);
  });

  test('a document with no published content cannot be signed', async ({ page }) => {
    const draftId = await platform.documentIdBySlug('testbed-release-notes');

    await page.goto(`${DOCUMENTS}/${draftId}/sign?subjectType=Order&subjectId=${orderId}`);

    await expect(page.getByTestId('document-sign-not-published')).toBeVisible();
    await expect(page.getByTestId('document-sign-accept')).toHaveCount(0);
    await expect(page.getByTestId('document-sign-button')).toHaveCount(0);
  });
});

test.describe('Document signature, not signed in', () => {
  test('signing is refused to a caller with no signed-in user', async ({ request, baseURL }) => {
    const roots = await serviceRoots(request, baseURL as string);
    const platform = new PlatformApi(request, roots);

    const refused = await platform.signAnonymously(await platform.documentIdBySlug('order-invoice'), 'Order', randomUUID());

    expect(refused).toEqual({ status: 403, errorId: 'document.access-denied' });
  });
});
