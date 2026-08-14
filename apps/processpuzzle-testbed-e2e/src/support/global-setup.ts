import * as path from 'path';
import { createGlobalSetup } from '@processpuzzle/e2e-testing';

export const REGISTRY_PATH = path.join(__dirname, '../../tmp/entity-registry.json');

export default createGlobalSetup({
  registryPath: REGISTRY_PATH,
  // `/design` is mounted with `loadChildren`, so its branch is not in the router's configuration when the
  // registry page — a page load of its own — serializes the descriptors. An entity mounted only there
  // therefore arrives without a route, and this is where this application says where it put it.
  //
  // `App Definition` is listed for completeness rather than effect: it is *also* mounted eagerly under
  // `/base-app/samples`, so the registry does report a route for it and the override — applied with `??=` —
  // never fires. `Widget Definition` is the opposite case and the reason this option exists: it is mounted
  // *only* under `/design/application`, so without the entry the generated CRUD and list specs would fall
  // back to `/base-entity/samples/widget-definition` and every one of them would fail with NG04002.
  routeOverrides: {
    'App Definition': '/design/application/app-definition',
    'Widget Definition': '/design/application/widget-definition',
  },
});
