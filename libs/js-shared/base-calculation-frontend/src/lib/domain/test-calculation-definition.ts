/** The seeded order total, as base-calculation-backend answers it. */
export const ORDER_TOTAL_SOURCE = `# The order total
calculation: order-total
name: Order total
parameters:
  order: { entity: order }
  customerTier: { type: string, default: STANDARD }
inputs:
  lines: { from: order, path: lineItems, entity: order-line }

tables:
  # Discount tiers
  volume-discount:
    hitPolicy: FIRST
    inputs: [subtotal, customerTier]
    output: discountRate
    rules:
      - { when: ['>= 10000', '"GOLD"'], then: 0.10 }
      - { when: ['-', '-'], then: 0 }

decisions:
  lineTotal: { for: lines, expr: "quantity * unitPrice" }
  subtotal: { aggregate: { sum: lineTotal } }
  discountRate: { table: volume-discount }
  total: { expr: "subtotal * (1 - discountRate)" }
`;

export const ORDER_TOTAL_DTO = {
  id: 'order-total',
  name: 'Order total',
  description: 'Sums the lines.',
  source: ORDER_TOTAL_SOURCE,
  definitionVersion: 2,
  parameters: [
    { name: 'order', entity: 'order' },
    { name: 'customerTier', type: 'string' },
  ],
  outputEntity: 'order',
  version: 4,
  releaseState: 'RELEASED',
};

export const SERIES_RANKING_DTO = {
  id: 'sail-series-ranking',
  name: null,
  source: 'calculation: sail-series-ranking\ndecisions:\n  x: { expr: "1" }\n',
  definitionVersion: 1,
  parameters: [],
  version: 0,
};

export const SERVICE_ROOT = 'http://localhost:8080/organizations/processpuzzle-testbed';

export const CALCULATION_RUN = {
  id: 'run-1',
  calculationId: 'order-total',
  definitionVersion: 2,
  channel: 'RELEASED',
  triggeredBy: 'ON_CHANGE',
  subjectType: 'order',
  subjectId: 'o-1',
  status: 'COMPLETED',
  result: { values: { total: 89.88 }, order: ['total'] },
  written: 1,
  frozen: false,
  definitionSource: ORDER_TOTAL_SOURCE,
  startedAt: '2026-10-18T10:00:00Z',
} as const;

export const TRACE = [
  { decision: 'lineTotal', kind: 'EXPR', scope: 'row:lines', value: [39.98, 49.9] },
  { decision: 'total', kind: 'EXPR', scope: 'scalar', value: 89.88 },
];
