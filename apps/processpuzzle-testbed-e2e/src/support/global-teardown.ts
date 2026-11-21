import { createGlobalTeardown } from '@processpuzzle/e2e-testing';
import { testConfig } from '../../playwright.config';
import { REGISTRY_PATH } from './global-setup';

export default createGlobalTeardown({ registryPath: REGISTRY_PATH, routePrefix: testConfig.routePrefix });
