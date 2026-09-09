/**
 * The wire shape of `automated-check-tool` — the tool the seeded workflow's steps reference, named as
 * `automated-check-tool` in
 * `base-workflow-backend/src/main/resources/default-workflows/processpuzzle-testbed-workflows.yaml`.
 *
 * `auth` arrives nested, as the contract has it; the mapper is what flattens it.
 */
export const TOOL_DEFINITION_DTO = {
  id: 'automated-check-tool',
  name: 'Automated Check Tool',
  description: "Stand-in for the inventory and document service the testbed's order fulfillment steps call.",
  baseUrl: 'https://checks.example.com',
  auth: { type: 'BEARER_TOKEN', secretRef: 'AUTOMATED_CHECK_TOKEN' },
  operations: [
    {
      id: 'inventory-check',
      method: 'POST',
      path: '/v1/inventory/check',
      description: 'Verifies that every line item of an order is available in stock.',
      payloadTemplate: '{ "orderId": "${entityId}" }',
      expectedStatusCodes: [200],
    },
    {
      id: 'generate-doc',
      method: 'POST',
      path: '/v1/documents',
      description: 'Produces the delivery confirmation and final invoice.',
      expectedStatusCodes: [200, 201],
    },
  ],
  version: 1,
  createdAt: '2026-08-01T09:00:00Z',
};

/** A second row with no `auth` block at all — the shape an unauthenticated tool actually arrives in. */
export const OTHER_TOOL_DEFINITION_DTO = {
  id: 'public-rates-tool',
  name: 'Public Rates Tool',
  baseUrl: 'https://rates.example.com',
  operations: [{ id: 'today', method: 'GET', path: '/v1/rates/today' }],
  version: 1,
};

export function pageOfToolDefinitions(...content: object[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}
