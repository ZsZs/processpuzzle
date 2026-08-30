/**
 * The wire shape of the seeded `review-order` task, verbatim from the `tasks:` section of
 * `processpuzzle-testbed-workflows.yaml`.
 *
 * `performedByRoles` names both roles able to perform it; which one actually does is the referencing
 * workflow's business — see the `tasks` of `WORKFLOW_DTO`. Its inputs and outputs are
 * `ARTIFACT` references, the type the catalog model added.
 */
export const TASK_DEFINITION_DTO = {
  id: 'review-order',
  name: 'Review Order',
  description: 'Review order details and validate line items before confirming it for fulfillment.',
  performedByRoles: ['clerk', 'manager'],
  inputs: [{ type: 'ARTIFACT', refId: 'order-entity', label: 'New Order' }],
  outputs: [{ type: 'ARTIFACT', refId: 'order-entity', label: 'Reviewed Order' }],
  preconditionRuleId: 'positive-quantities',
  steps: [
    {
      id: 'check-items',
      name: 'Check Line Items',
      description: 'Verify all line items are available in inventory.',
      toolId: 'automated-check-tool',
      toolOperation: 'inventory-check',
    },
  ],
  version: 1,
  createdAt: '2026-08-01T09:00:00Z',
  updatedAt: '2026-08-01T09:00:00Z',
};

/** A second row with a single role and no rules or steps — the smallest task the contract accepts. */
export const OTHER_TASK_DEFINITION_DTO = {
  id: 'approve-shipment',
  name: 'Approve Shipment',
  performedByRoles: ['manager'],
  version: 1,
};
