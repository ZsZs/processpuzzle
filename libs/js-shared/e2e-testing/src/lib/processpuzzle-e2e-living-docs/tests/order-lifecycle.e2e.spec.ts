import { test } from '../support/platform-fixture';
import { orderLifecycleManifest, type ScenarioState } from '../scenarios/order-lifecycle.manifest';

// This spec intentionally contains almost no logic of its own — the manifest
// is the single source of truth for what happens and in what order. That's
// what lets scripts/generate-living-docs.ts reconstruct the same narrative
// without re-parsing test code.
test('Order lifecycle: entity, document, rules, state machine, workflow', async ({ platform }) => {
  const state: ScenarioState = {};

  for (const step of orderLifecycleManifest) {
    await test.step(step.title, async () => {
      await step.run(platform, state);
    });
  }
});
