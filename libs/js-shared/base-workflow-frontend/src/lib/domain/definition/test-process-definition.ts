/**
 * The wire shape of `order-fulfillment-workflow`, verbatim from
 * `base-workflow-backend/src/main/resources/default-workflows/processpuzzle-testbed-workflows.yaml` —
 * the same document both backends are provisioned from and json-server serves as-is.
 *
 * Taken from the seed rather than invented, so a spec that passes here is a spec that would pass
 * against a running testbed. Kept as a plain object, not an entity: it is what a mapper is *given*.
 *
 * Note what the process holds: `roles`, `artifacts` and `tools` are **id lists** into the tenant's
 * catalog, and the only nested rows are the task assignments — the one thing that has no meaning
 * outside this process.
 */
export const PROCESS_DEFINITION_DTO = {
  id: 'order-fulfillment-workflow',
  name: 'Order Fulfillment Workflow',
  description: 'Tiny end-to-end workflow governing order review, shipment approval, and delivery confirmation in the testbed environment.',
  roles: ['clerk', 'manager'],
  artifacts: ['order-entity', 'fulfillment-invoice'],
  tools: ['automated-check-tool'],
  tasks: [
    { taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [], parallel: false },
    { taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: ['review-order'], parallel: false },
    { taskDefinitionId: 'confirm-delivery', performedBy: 'clerk', dependsOn: ['approve-shipment'], parallel: false },
  ],
  activeInstances: 1,
  version: 3,
  createdAt: '2026-08-01T09:00:00Z',
  updatedAt: '2026-08-20T08:00:00Z',
};

/** A second row, so a list spec can tell selection from "the only entry". */
export const OTHER_PROCESS_DEFINITION_DTO = {
  id: 'claim-handling-workflow',
  name: 'Claim Handling Workflow',
  extends: 'order-fulfillment-workflow',
  roles: ['clerk'],
  tasks: [{ taskDefinitionId: 'review-order', performedBy: 'clerk', override: true }],
  version: 1,
};

/** The paged envelope `listProcessDefinitions` answers with. */
export function pageOfProcessDefinitions(...content: object[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}
