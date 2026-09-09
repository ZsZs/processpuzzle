/**
 * The DTO shape `GET /organizations/{orgKey}/diagrams` and `getDiagramDefinition` actually return.
 *
 * Kept here rather than in a single spec so that the mapper, service and store specs cannot drift onto
 * different mocks — the port fields in particular, which arrive as explicit `null`s from a Spring
 * backend and are what an unmapped pass-through would hand to ng-diagram as an anchor request.
 *
 * Named `test-*` so `tsconfig.lib.json` keeps it out of the published package.
 */
export const DIAGRAM_DEFINITION_DTO = {
  entityName: 'order',
  nodes: [
    { stateKey: 'DRAFT', position: { x: 40, y: 80 }, size: { width: 160, height: 64 } },
    // No size: the node is sized by its content, which is the default.
    { stateKey: 'DELIVERED', position: { x: 320, y: 80 } },
  ],
  edges: [
    {
      transitionKey: 'confirm',
      points: [
        { x: 210, y: 112 },
        { x: 300, y: 112 },
      ],
      sourcePort: 'port-right',
      targetPort: 'port-left',
      routing: 'orthogonal',
    },
  ],
  viewport: { x: -120, y: 0, scale: 1.25 },
  orgKey: 'processpuzzle-testbed',
  version: 3,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-02-01T00:00:00Z',
};

/**
 * A second layout, so list specs can tell entries apart — and, deliberately, one whose optional fields
 * arrive as the explicit `null`s the contract marks them `nullable` for, and whose single edge is
 * routed automatically.
 */
export const OTHER_DIAGRAM_DEFINITION_DTO = {
  entityName: 'dynamic-entity',
  nodes: [{ stateKey: 'DRAFT', position: { x: 0, y: 0 }, size: null }],
  edges: [{ transitionKey: 'archive', points: [], sourcePort: null, targetPort: null, routing: null }],
  viewport: null,
  version: 1,
};

/** Wraps entries in the `PageOf_DiagramDefinition` envelope the Spring backend answers with. */
export function pageOfDiagramDefinitions(...content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}
