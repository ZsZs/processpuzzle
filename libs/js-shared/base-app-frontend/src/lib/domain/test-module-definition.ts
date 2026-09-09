/**
 * The DTO shape `GET /organizations/{orgKey}/modules` returns: a complete module per entry, the same one
 * `getModuleDefinition` serves — which matters because the shell's lazy mount registers the routes of a
 * *listed* module, and the designer's edit form writes back a full replacement.
 *
 * Mirrors `test-app-definition.ts`, and named `test-*` for the same reason: `tsconfig.lib.json` keeps it
 * out of the published package.
 */
export const MODULE_DEFINITION_DTO = {
  key: 'order-admin',
  name: 'Order administration',
  translocoId: 'order_admin.module.name',
  description: 'Back-office order line screens.',
  translocoScope: 'order_admin',
  routes: [
    { path: 'lines', title: 'All order lines', target: { kind: 'ENTITY', entityName: 'Order Line', entityMode: 'LIST' } },
    { path: 'line/:id', title: 'Order line', target: { kind: 'ENTITY', entityName: 'Order Line', entityMode: 'DETAILS' } },
  ],
  orgKey: 'processpuzzle-testbed',
  version: 2,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-02-01T00:00:00Z',
};

/** A second module, so list specs can tell entries apart. */
export const OTHER_MODULE_DEFINITION_DTO = { ...MODULE_DEFINITION_DTO, key: 'claims', name: 'Claims', translocoScope: undefined, version: 1 };

/** Wraps entries in the `PageOf_ModuleDefinition` envelope the Spring backend answers with. */
export function pageOfModuleDefinitions(...content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}
