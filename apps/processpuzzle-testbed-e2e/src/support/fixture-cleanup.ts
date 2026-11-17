import type { AfterFixtureRemoved } from '@processpuzzle/e2e-testing';
import { PlatformApi } from './platform.api';
import { serviceRoots } from './race-sample.api';

const BASE_URL = process.env['PROCESSPUZZLE_TESTBED_BASE_URL'] || 'http://localhost:4200';

/**
 * What a generated suite's fixture started and its own delete does not end: creating an order starts a run of the
 * order fulfilment workflow, and deleting the order leaves the run — and its tasks — behind, in the team's queue.
 * On stage that was about a hundred runs a day, 738 of them orphaned by the time the task dashboard, which lists
 * every one of them, stopped showing a claim or a completion inside its 5 s budget.
 *
 * Runs are found by the entity they were started for, so this is the same call for every entity type: for one that
 * starts nothing it is a single listing that comes back empty.
 */
export const removeWorkflowInstancesOfFixture: AfterFixtureRemoved = async (fixture, page) => {
  const platform = new PlatformApi(page.request, await serviceRoots(page.request, BASE_URL));
  await platform.deleteWorkflowInstances({ entityId: fixture.id });
};
