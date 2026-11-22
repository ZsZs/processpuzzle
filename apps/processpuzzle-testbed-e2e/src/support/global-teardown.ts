import type { FullConfig } from '@playwright/test';
import { createGlobalTeardown } from '@processpuzzle/e2e-testing';
import { testConfig } from '../../playwright.config';
import { REGISTRY_PATH } from './global-setup';
import { sweepOrphanedOrderRuns } from './orphan-sweep';

const sweepEntities = createGlobalTeardown({ registryPath: REGISTRY_PATH, routePrefix: testConfig.routePrefix });

export default async function globalTeardown(config: FullConfig): Promise<void> {
  await sweepEntities(config);
  await sweepOrphanedOrderRuns(config);
}
