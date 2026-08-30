/**
 * The wire shape of `order-fulfillment-workflow`, verbatim from
 * `base-workflow-backend/src/main/resources/default-workflows/processpuzzle-testbed-workflows.yaml` —
 * the same document both backends are provisioned from and json-server serves as-is.
 *
 * Taken from the seed rather than invented, so a spec that passes here is a spec that would pass
 * against a running testbed. Kept as a plain object, not an entity: it is what a mapper is *given*.
 *
 * Note what the workflow holds. `roles`, `artifacts` and `tools` are **not** id lists: each is an array
 * of `*Use` rows wrapping a definition id, which is the extension point for whatever turns out to be
 * true of a shared definition only in this workflow. This fixture claimed to be verbatim from the seed
 * while carrying bare id arrays, and that is exactly why the mapper dropped every role, artifact and
 * tool against a real backend and no spec noticed.
 */
export const WORKFLOW_DTO = {
  id: 'order-fulfillment-workflow',
  name: 'Order Fulfillment Workflow',
  description: 'Tiny end-to-end workflow governing order review, shipment approval, and delivery confirmation in the testbed environment.',
  startCondition: {
    startType: 'INPUT_ARTIFACT',
    requiredArtifacts: [{ artifactDefinitionId: 'order-entity', state: 'DRAFT' }],
  },
  roles: [{ roleDefinitionId: 'clerk' }, { roleDefinitionId: 'manager' }],
  artifacts: [{ artifactDefinitionId: 'order-entity' }, { artifactDefinitionId: 'fulfillment-invoice' }],
  tools: [{ toolDefinitionId: 'automated-check-tool' }],
  tasks: [
    { taskDefinitionId: 'review-order', performedBy: 'clerk', dependsOn: [], parallel: false },
    { taskDefinitionId: 'approve-shipment', performedBy: 'manager', dependsOn: ['review-order'], joinType: 'ALL', parallel: false },
    { taskDefinitionId: 'confirm-delivery', performedBy: 'clerk', dependsOn: ['approve-shipment'], parallel: false },
  ],
  activeInstances: 1,
  version: 3,
  createdAt: '2026-08-01T09:00:00Z',
  updatedAt: '2026-08-20T08:00:00Z',
};

/**
 * A second row, so a list spec can tell selection from "the only entry". Also the shape a workflow with
 * no start condition arrives in — one that can only be started explicitly through `/instances`.
 */
export const OTHER_WORKFLOW_DTO = {
  id: 'claim-handling-workflow',
  name: 'Claim Handling Workflow',
  extends: 'order-fulfillment-workflow',
  roles: [{ roleDefinitionId: 'clerk' }],
  tasks: [{ taskDefinitionId: 'review-order', performedBy: 'clerk', override: true }],
  version: 1,
};

/** The paged envelope `listWorkflows` answers with. */
export function pageOfWorkflows(...content: object[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}
