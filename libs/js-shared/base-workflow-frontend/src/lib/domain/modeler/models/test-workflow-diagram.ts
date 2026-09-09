/**
 * Wire-shaped diagram layouts, as `GET /workflow-diagrams` and `GET /workflow-diagrams/{workflowId}` answer
 * them — the same role the `test-*.ts` fixtures beside the definition models play, and the same reason: a
 * spec that built a `WorkflowDiagram` by hand would be asserting against the mapper's own output rather than
 * against what the backend sends.
 *
 * Deliberately DTO-shaped, not model-shaped: `nodeId` and `edgeId` are the contract's field names, and the
 * ports arrive as the `nullable` strings the contract declares.
 */

/** The layout of the workflow the modeler specs open — two tasks in two lanes, one flow edge between them. */
export const WORKFLOW_DIAGRAM_DTO = {
  workflowId: 'order-fulfillment-workflow',
  nodes: [
    { nodeId: 'lane:clerk', position: { x: 0, y: 0 }, size: { width: 600, height: 108 } },
    { nodeId: 'task:review-order', position: { x: 156, y: 16 }, size: { width: 170, height: 76 } },
    { nodeId: 'task:approve-order', position: { x: 396, y: 132 }, size: { width: 170, height: 76 } },
  ],
  edges: [{ edgeId: 'task:review-order->task:approve-order', points: [], sourcePort: 'port-right', targetPort: 'port-left', routing: 'orthogonal' }],
  viewport: { x: -20, y: -30, scale: 0.9 },
  orgKey: 'processpuzzle-testbed',
  version: 2,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-02T11:00:00Z',
};

/** A second organization row, so a list assertion has something to distinguish. */
export const OTHER_WORKFLOW_DIAGRAM_DTO = {
  workflowId: 'claim-handling-workflow',
  nodes: [{ nodeId: 'task:review-order', position: { x: 8, y: 9 } }],
  edges: [],
  orgKey: 'processpuzzle-testbed',
  version: 0,
};

/** The page envelope `listWorkflowDiagrams` answers with. */
export function pageOfWorkflowDiagrams(...diagrams: object[]): object {
  return { content: diagrams, totalElements: diagrams.length, totalPages: 1, number: 0, size: 20 };
}
