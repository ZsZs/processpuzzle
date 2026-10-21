import { expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { PlatformApi, TaskInstanceSummary, WorkflowInstanceSummary } from './platform.api';
import { EntityApi } from './race-sample.api';

interface OrderPayload {
  orderNumber: string;
  customerName: string;
  status: string;
  total: number;
  lineItems: { productName: string; quantity: number; unitPrice: number }[];
}

export type RunState = WorkflowInstanceSummary & {
  tasks: TaskInstanceSummary[];
  events: { eventUseId: string; status: string; direction: string }[];
};

/**
 * One order and the order fulfillment run it starts, driven through the API the way the screens drive it: the
 * order's own transitions, and a task assigned and completed. Which is how the workflow events are reached —
 * a thrown message, a catch that waits for it, a cancellation that interrupts a task — without a browser.
 */
export class OrderRun {
  private constructor(
    private readonly entities: EntityApi,
    private readonly platform: PlatformApi,
    readonly orderId: string,
    readonly orderNumber: string,
    readonly runId: string,
  ) {}

  /** Creates an order, which starts a run, and waits for the run. The order is removed with the entity API's `removeAll`. */
  static async start(entities: EntityApi, platform: PlatformApi): Promise<OrderRun> {
    const orderNumber = `E2E-${randomUUID().slice(0, 8)}`;
    const order = await entities.create<OrderPayload>('order', {
      orderNumber,
      customerName: 'Events Customer',
      status: 'DRAFT',
      total: 10,
      lineItems: [{ productName: 'E2E item', quantity: 1, unitPrice: 10 }],
    });
    await expect.poll(async () => (await platform.workflowInstances({ workflowId: 'order-fulfillment-workflow', entityId: order.id })).length, { message: 'the order starts a run' }).toBe(1);
    const [run] = await platform.workflowInstances({ workflowId: 'order-fulfillment-workflow', entityId: order.id });
    return new OrderRun(entities, platform, order.id, orderNumber, run.id);
  }

  /** Fires one of the order's transitions (`confirm`, `ship`, `deliver`, `cancel`). */
  async transition(trigger: string): Promise<void> {
    const order = await this.entities.get<OrderPayload>('order', this.orderId);
    const result = await this.platform.fireTransition('order', this.orderId, trigger, order.version ?? 0);
    expect(result.success, `${trigger} on the order`).toBe(true);
  }

  /** A person does a task: it is assigned and completed, with the context the completion carries. */
  async work(taskDefinitionId: string, context: Record<string, unknown> = {}, runId = this.runId): Promise<void> {
    await this.platform.assignTask(runId, taskDefinitionId, 'e2e-worker');
    expect(await this.platform.completeTask(runId, taskDefinitionId, context), `${taskDefinitionId} accepted`).toBe(true);
  }

  state(runId = this.runId): Promise<RunState> {
    return this.platform.workflowInstance(runId) as Promise<RunState>;
  }

  async taskStatus(taskDefinitionId: string, runId = this.runId): Promise<string | undefined> {
    return (await this.state(runId)).tasks.find((task) => task.taskDefinitionId === taskDefinitionId)?.status;
  }

  async taskOf(taskDefinitionId: string, runId = this.runId): Promise<TaskInstanceSummary | undefined> {
    return (await this.state(runId)).tasks.find((task) => task.taskDefinitionId === taskDefinitionId);
  }

  /** The run another workflow started for this order, once it exists. */
  async otherRun(workflowId: string): Promise<WorkflowInstanceSummary | undefined> {
    return (await this.platform.workflowInstances({ workflowId, entityId: this.orderId }))[0];
  }

  /** Review and shipment done, as far as the throw of the invoice request. */
  async throughShipmentApproval(): Promise<void> {
    await this.transition('confirm');
    await this.work('review-order');
    await this.transition('ship');
    await this.work('approve-shipment');
  }
}
