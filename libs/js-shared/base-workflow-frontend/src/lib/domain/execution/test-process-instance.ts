/**
 * The wire shape of a running instance of `order-fulfillment-workflow`, verbatim from the
 * `processpuzzle-testbed-instances` fixture in `tools/mock-backend/db.json`.
 *
 * It carries a completed task with a tool response, an active one, and a pending one — so a spec can
 * see every status the screens have to render, not only the happy one.
 */
export const PROCESS_INSTANCE_DTO = {
  id: '8f14e45f-ceea-467a-9c9b-9b0c1f0f5a01',
  processDefinitionId: 'order-fulfillment-workflow',
  processDefinitionName: 'Order Fulfillment Workflow',
  status: 'ACTIVE',
  entityId: '1',
  startedAt: '2026-08-20T08:15:00Z',
  context: { channel: 'web', priority: 'normal' },
  tasks: [
    {
      id: '5a1b2c3d-0001-4a00-8000-000000000001',
      taskDefinitionId: 'review-order',
      name: 'Review Order',
      status: 'COMPLETED',
      assignedTo: 'clerk-user',
      activatedAt: '2026-08-20T08:15:00Z',
      completedAt: '2026-08-20T09:02:00Z',
      stepResults: [{ stepId: 'check-items', completedAt: '2026-08-20T09:01:30Z', toolResponse: { available: 'true', warehouse: 'EU-1' } }],
    },
    {
      id: '5a1b2c3d-0002-4a00-8000-000000000002',
      taskDefinitionId: 'approve-shipment',
      name: 'Approve Shipment',
      status: 'ACTIVE',
      assignedTo: 'manager-user',
      activatedAt: '2026-08-20T09:02:00Z',
      stepResults: [],
    },
    { id: '5a1b2c3d-0003-4a00-8000-000000000003', taskDefinitionId: 'confirm-delivery', name: 'Confirm Delivery', status: 'PENDING', stepResults: [] },
  ],
  artifacts: [
    {
      id: '7c9e6679-0001-4a00-8000-000000000001',
      artifactDefinitionId: 'order-entity',
      name: 'Order Entity',
      type: 'ENTITY',
      entityId: '1',
      stateMachineInstanceId: 'order-1',
      currentState: 'CONFIRMED',
      updatedAt: '2026-08-20T09:02:00Z',
    },
    { id: '7c9e6679-0002-4a00-8000-000000000002', artifactDefinitionId: 'fulfillment-invoice', name: 'Fulfillment Invoice', type: 'DELIVERABLE', updatedAt: '2026-08-20T08:15:00Z' },
  ],
};

/** A finished run whose last task is BLOCKED with a failed tool step — the interesting error path. */
export const OTHER_PROCESS_INSTANCE_DTO = {
  id: '8f14e45f-ceea-467a-9c9b-9b0c1f0f5a02',
  processDefinitionId: 'order-fulfillment-workflow',
  processDefinitionName: 'Order Fulfillment Workflow',
  status: 'COMPLETED',
  entityId: '2',
  startedAt: '2026-08-12T10:00:00Z',
  completedAt: '2026-08-14T16:40:00Z',
  tasks: [
    {
      id: '5a1b2c3d-0006-4a00-8000-000000000006',
      taskDefinitionId: 'confirm-delivery',
      name: 'Confirm Delivery',
      status: 'BLOCKED',
      blockedReason: 'positive-quantities: line item 3 has quantity 0',
      stepResults: [{ stepId: 'generate-invoice', completedAt: '2026-08-14T16:39:00Z', error: 'tool returned 503 after 3 retries' }],
    },
  ],
  artifacts: [],
};

export function pageOfProcessInstances(...content: object[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}
