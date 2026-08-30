/**
 * The wire shape of the two seeded artifacts, verbatim from the `artifacts:` section of
 * `processpuzzle-testbed-workflows.yaml`. `/artifacts` answers with a plain array, so no envelope
 * helper is needed.
 */
export const ARTIFACT_DEFINITION_DTO = {
  id: 'order-entity',
  name: 'Order Entity',
  description: 'The order being processed through its lifecycle.',
  type: 'ENTITY',
  entityTypeId: 'order',
  stateMachineId: 'order',
  version: 1,
  createdAt: '2026-08-01T09:00:00Z',
  updatedAt: '2026-08-01T09:00:00Z',
};

/** A second row bound to neither an entity type nor a state machine — a plain deliverable. */
export const OTHER_ARTIFACT_DEFINITION_DTO = {
  id: 'fulfillment-invoice',
  name: 'Fulfillment Invoice',
  description: 'Generated invoice document for a delivered order.',
  type: 'DELIVERABLE',
  version: 1,
};
