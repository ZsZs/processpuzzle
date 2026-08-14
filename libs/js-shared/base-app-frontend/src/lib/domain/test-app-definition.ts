/**
 * The DTO shape `GET /organizations/{orgKey}/app-definitions` actually returns: a complete
 * definition graph per entry, the same one `getAppDefinition` serves.
 *
 * It lives here rather than in a single spec because the service and store specs used to mock a
 * header-only object, which is precisely the shape that made the edit form show defaults and the
 * next full-replacement PUT wipe `regions`, `routes` and `modules`. Sharing one full fixture keeps a
 * mock from quietly drifting back to a projection the backend does not send.
 *
 * Named `test-*` so `tsconfig.lib.json` keeps it out of the published package.
 */
export const APP_DEFINITION_DTO = {
  id: 'demo',
  name: 'Demo',
  translocoId: 'demo.app.name',
  description: 'Basic demonstration application',
  theme: { materialTheme: 'azure-blue', colorScheme: 'light', tokenOverrides: { '--pp-surface-sidenav': '#0d1b2a' }, logoUrl: 'logo.png' },
  layout: { preset: 'sidenav-left', sidenavMode: 'side', sidenavCollapsible: true, sidenavOpenByDefault: false, contentMaxWidth: '1280px' },
  regions: [{ type: 'sidenav', navItems: [{ id: 'nav-orders', label: 'Orders', routePath: 'orders' }] }],
  routes: [{ path: 'orders', title: 'Orders', target: { kind: 'WIDGETS', widgets: [{ id: 'order-grid', type: 'entity-grid', props: { entityName: 'Order' } }] } }],
  modules: [{ moduleKey: 'claims', basePath: 'claims' }],
  orgKey: 'processpuzzle-testbed',
  status: 'DRAFT',
  version: 3,
  publishedVersion: 2,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-02-01T00:00:00Z',
};

/** A second definition, so list specs can tell entries apart. */
export const OTHER_APP_DEFINITION_DTO = { ...APP_DEFINITION_DTO, id: 'other', name: 'Other', version: 1, publishedVersion: undefined };

/** Wraps entries in the `PageOf_AppDefinition` envelope the Spring backend answers with. */
export function pageOfAppDefinitions(...content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}
