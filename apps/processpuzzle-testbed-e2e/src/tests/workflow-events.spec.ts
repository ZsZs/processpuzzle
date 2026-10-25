import { expect, test } from '@playwright/test';
import { OrderRun } from '../support/order-run';
import { PlatformApi } from '../support/platform.api';
import { EntityApi, serviceRoots } from '../support/race-sample.api';

/**
 * What the seeded workflows do with events, end to end, on a run of the test's own. An order starts the order
 * fulfillment workflow; its shipment approval throws `InvoiceRequested`, which starts the invoicing workflow;
 * issuing the invoice throws `InvoiceIssued`, which the fulfillment run is waiting for; and a cancelled order
 * interrupts whichever task it is in. The events themselves are not listed anywhere, so each is observed in the
 * state of the runs it moved.
 */
test.describe('Workflow events', () => {
  let entities: EntityApi;
  let platform: PlatformApi;
  let order: OrderRun;

  test.beforeEach(async ({ request, baseURL }) => {
    const roots = await serviceRoots(request, baseURL as string);
    entities = new EntityApi(request, roots);
    platform = new PlatformApi(request, roots);
    order = await OrderRun.start(entities, platform);
  });

  test.afterEach(async () => {
    for (const entityId of entities.createdIds('order')) await platform.cancelWorkflowInstances({ entityId });
    await entities.removeAll();
  });

  const invoicingRun = async () => {
    await expect.poll(() => order.otherRun('invoicing-workflow'), { message: 'the invoicing run' }).toBeDefined();
    return (await order.otherRun('invoicing-workflow')) as NonNullable<Awaited<ReturnType<OrderRun['otherRun']>>>;
  };

  const eventStatus = async (eventUseId: string, runId = order.runId) => (await order.state(runId)).events.find((event) => event.eventUseId === eventUseId)?.status;

  test('approving the shipment throws the invoice request, which starts an invoicing run from the order', async () => {
    await order.throughShipmentApproval();

    const invoicing = await invoicingRun();
    expect(invoicing.status).toBe('ACTIVE');
    // The thrown message carries the order into the new run under its own names.
    expect(invoicing.context).toMatchObject({ orderId: order.orderId, orderNumber: order.orderNumber, customerName: 'Events Customer' });
    expect(await eventStatus('request-invoice')).toBe('THROWN');
    expect(await order.taskStatus('issue-invoice', invoicing.id)).toBe('ACTIVE');
  });

  test('issuing the invoice answers the fulfillment run, which goes on to delivery', async () => {
    await order.throughShipmentApproval();
    const invoicing = await invoicingRun();
    expect(await order.taskStatus('confirm-delivery')).toBe('PENDING');
    expect(await eventStatus('invoice-issued')).toBe('WAITING');

    await order.work('issue-invoice', { invoiceNumber: 'INV-E2E-1' }, invoicing.id);

    await expect.poll(() => order.taskStatus('confirm-delivery'), { message: 'the catch is answered' }).toBe('ACTIVE');
    expect((await order.state(invoicing.id)).status).toBe('COMPLETED');
  });

  test('a throw whose required parameter is missing is held, and the run keeps waiting', async ({ page }) => {
    await order.throughShipmentApproval();
    const invoicing = await invoicingRun();

    // `InvoiceIssued` requires an invoice number, which the task's completion did not supply.
    await order.work('issue-invoice', {}, invoicing.id);

    await expect.poll(() => order.taskStatus('issue-invoice', invoicing.id)).toBe('COMPLETED');
    await expect.poll(() => eventStatus('invoice-issued', invoicing.id)).toBe('PENDING');
    // Nothing to wait *for*: an answer that wrongly went out would arrive within moments.
    // eslint-disable-next-line sonarjs/no-fixed-wait-in-tests -- a negative: there is no condition to poll for
    await page.waitForTimeout(2500);
    expect(await order.taskStatus('confirm-delivery')).toBe('PENDING');
    expect(await eventStatus('invoice-issued')).toBe('WAITING');
    expect((await order.state(invoicing.id)).status).toBe('ACTIVE');
  });

  test('delivering the order finishes the run and starts the customer feedback workflow', async () => {
    await order.throughShipmentApproval();
    await order.work('issue-invoice', { invoiceNumber: 'INV-E2E-2' }, (await invoicingRun()).id);
    await expect.poll(() => order.taskStatus('confirm-delivery')).toBe('ACTIVE');

    await order.transition('deliver');
    await order.work('confirm-delivery');

    await expect.poll(async () => (await order.state()).status).toBe('COMPLETED');
    // The order reaching DELIVERED is what starts the feedback run, not anything the fulfillment run does.
    await expect.poll(() => order.otherRun('customer-feedback-workflow'), { message: 'the feedback run' }).toBeDefined();
    expect((await order.otherRun('customer-feedback-workflow'))?.startEventId).toBe('order-delivered');
  });

  test('cancelling the order while it is being reviewed interrupts the review and everything behind it', async () => {
    expect(await order.taskStatus('review-order')).toBe('ACTIVE');

    await order.transition('cancel');

    await expect.poll(() => order.taskStatus('review-order')).toBe('CANCELLED');
    const run = await order.state();
    expect(run.status).toBe('COMPLETED');
    expect((await order.taskOf('review-order'))?.['cancelReason']).toBe('interrupted by order-cancelled-in-review');
    // Everything behind the interrupted task can no longer be reached, and says so.
    expect((await order.taskOf('approve-shipment'))?.['cancelReason']).toBe('unreachable');
    expect((await order.taskOf('confirm-delivery'))?.['cancelReason']).toBe('unreachable');
    expect(await eventStatus('order-cancelled-in-review')).toBe('OCCURRED');
  });

  test('cancelling the order after the review interrupts the shipment approval instead', async () => {
    await order.transition('confirm');
    await order.work('review-order');
    await expect.poll(() => order.taskStatus('approve-shipment')).toBe('ACTIVE');

    await order.transition('cancel');

    await expect.poll(() => order.taskStatus('approve-shipment')).toBe('CANCELLED');
    expect(await order.taskStatus('review-order')).toBe('COMPLETED');
    expect(await order.taskStatus('confirm-delivery')).toBe('CANCELLED');
    expect(await eventStatus('order-cancelled-before-shipment')).toBe('OCCURRED');
    // The waiting reminder and the throw that would have asked for an invoice are called off with it.
    expect(await eventStatus('approval-reminder')).toBe('CANCELLED');
    expect(await eventStatus('request-invoice')).toBe('CANCELLED');
  });
});
