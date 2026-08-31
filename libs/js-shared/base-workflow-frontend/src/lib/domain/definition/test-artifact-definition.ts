/**
 * The wire shape of the two seeded artifacts, verbatim from the `artifacts:` section of
 * `processpuzzle-testbed-workflows.yaml`. `/artifacts` answers with a plain array, so no envelope
 * helper is needed.
 *
 * The kind and the backing type are `artifactType` and `artifactTypeId`, as the schema and the backend
 * column both spell them. This fixture said `type` and `entityTypeId`, which is why the mapper's
 * matching misspelling round-tripped cleanly here and dropped both fields against a real backend.
 */
export const ARTIFACT_DEFINITION_DTO = {
  id: 'order-entity',
  name: 'Order Entity',
  description: 'The order being processed through its lifecycle.',
  artifactType: 'ENTITY',
  artifactTypeId: 'order',
  stateMachineId: 'order',
  version: 1,
  createdAt: '2026-08-01T09:00:00Z',
  updatedAt: '2026-08-01T09:00:00Z',
};

/** A second row bound to no state machine — the shape an artifact without a lifecycle arrives in. */
export const OTHER_ARTIFACT_DEFINITION_DTO = {
  id: 'fulfillment-invoice',
  name: 'Fulfillment Invoice',
  description: 'Generated invoice document for a delivered order.',
  artifactType: 'DOCUMENT',
  artifactTypeId: 'fulfillment-invoice',
  version: 1,
};
