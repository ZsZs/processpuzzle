import { defineEntityListSuite } from '@processpuzzle/e2e-testing';
import { testConfig } from '../../playwright.config';
import { REGISTRY_PATH } from '../support/global-setup';

defineEntityListSuite({ registryPath: REGISTRY_PATH, routePrefix: testConfig.routePrefix });
