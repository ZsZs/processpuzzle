import { expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { PlatformApi } from '../support/platform.api';
import { EntityApi, ServiceRoots, serviceRoots } from '../support/race-sample.api';

interface OrderPayload {
  orderNumber: string;
  customerName: string;
  status: string;
  total: number;
  lineItems: { productName: string; quantity: number; unitPrice: number }[];
}

const FULFILLMENT = 'order-fulfillment-workflow';

/**
 * A platform fact becomes a catalogued event, and a catalogued event starts the workflow that names it. Nothing
 * lists the events that occurred (published ones are deleted once handled), so a fact is observed through what it
 * caused: here, a workflow instance.
 *
 * Runs against the seeded, released catalog: `OrderCreatedEvent` (an order is created) starts the order
 * fulfillment workflow. No definition is released or changed by these specs.
 */
test.describe('Event matching', () => {
  let roots: ServiceRoots;
  let entities: EntityApi;
  let platform: PlatformApi;

  test.beforeEach(async ({ request, baseURL }) => {
    roots = await serviceRoots(request, baseURL as string);
    entities = new EntityApi(request, roots);
    platform = new PlatformApi(request, roots);
  });

  test.afterEach(async () => {
    for (const entityId of entities.createdIds('order')) await platform.cancelWorkflowInstances({ entityId });
    await entities.removeAll();
  });

  const order = (customerName = 'E2E Customer') => {
    const run = randomUUID().slice(0, 8);
    return entities.create<OrderPayload>('order', {
      orderNumber: `E2E-${run}`,
      customerName,
      status: 'DRAFT',
      total: 10,
      lineItems: [{ productName: 'E2E item', quantity: 1, unitPrice: 10 }],
    });
  };

  const instancesOf = (entityId: string) => platform.workflowInstances({ workflowId: FULFILLMENT, entityId });

  test('creating an order starts the order fulfillment workflow with the event\'s parameters', async () => {
    const created = await order('Event Customer');

    await expect.poll(async () => (await instancesOf(created.id)).length, { message: 'an instance for the new order' }).toBe(1);
    const [instance] = await instancesOf(created.id);
    expect(instance.status).toBe('ACTIVE');
    expect(instance.startEventId).toBe('order-created');
    // The start event maps the event's parameters into the run under their own names.
    expect(instance.context).toMatchObject({ orderId: created.id, orderNumber: created.payload.orderNumber, customerName: 'Event Customer' });
  });

  test('a workflow that starts on an event cannot be started by hand', async () => {
    const { status, body } = await platform.startWorkflow(FULFILLMENT);

    expect(status).toBe(409);
    expect(body.errorId).toBe('workflow.startNotManual');
  });

  test('a change that no catalogued event describes starts nothing', async () => {
    const created = await order();
    await expect.poll(async () => (await instancesOf(created.id)).length).toBe(1);

    const current = await entities.get<OrderPayload>('order', created.id);
    await entities.update('order', created.id, current.version ?? 0, { ...current.payload, customerName: 'Renamed' });

    // Nothing to wait *for*: an instance that wrongly started would appear within moments of the update.
    await new Promise((resolve) => setTimeout(resolve, 2000));
    expect(await instancesOf(created.id)).toHaveLength(1);
  });
});
