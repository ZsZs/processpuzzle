import * as path from 'path';
import { createGlobalSetup } from '@processpuzzle/e2e-testing';

export const REGISTRY_PATH = path.join(__dirname, '../../tmp/entity-registry.json');

export default createGlobalSetup({
  registryPath: REGISTRY_PATH,
  // `/design` is mounted with `loadChildren`, so its branch is not in the router's configuration when the
  // registry page — a page load of its own — serializes the descriptors. `App Definition` therefore arrives
  // without a route, and this is where this application says where it put it.
  routeOverrides: { 'App Definition': '/design/app-definition' },
});
