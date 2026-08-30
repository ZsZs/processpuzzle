/**
 * The wire shape of the two seeded roles, verbatim from the `roles:` section of
 * `processpuzzle-testbed-workflows.yaml`.
 *
 * `/roles` answers with a plain array rather than a page — see `listRoleDefinitions` in the contract —
 * so there is no envelope helper here, unlike the workflow and instance fixtures.
 */
export const ROLE_DEFINITION_DTO = {
  id: 'clerk',
  name: 'Order Clerk',
  description: 'Responsible for initial order entry, verification, and delivery confirmation.',
  responsibleFor: ['order-entity'],
  // Not in the seed, which links no role to base-entity. Kept so a spec covers the field at all.
  entityRoleId: 'clerk-role',
  version: 1,
  createdAt: '2026-08-01T09:00:00Z',
  updatedAt: '2026-08-01T09:00:00Z',
};

/** A second row with no `entityRoleId` — the shape an unlinked role actually arrives in. */
export const OTHER_ROLE_DEFINITION_DTO = {
  id: 'manager',
  name: 'Order Manager',
  description: 'Approves orders for shipment.',
  responsibleFor: ['fulfillment-invoice'],
  version: 1,
};
