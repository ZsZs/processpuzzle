import { request as playwrightRequest, type FullConfig } from '@playwright/test';
import { PlatformApi } from './platform.api';
import { EntityApi, serviceRoots } from './race-sample.api';

/**
 * Safety net for what a spec's own cleanup missed: a workflow run whose order is gone.
 *
 * Creating an order starts a run of the order fulfilment workflow, and removing the order leaves the run — and its
 * tasks and artifacts — behind, still ACTIVE. Each spec that creates orders purges its runs itself, but one that
 * forgot, or whose cleanup never ran, would otherwise leave them on the stage for good.
 *
 * Only runs of an `order` are judged, and only by the order being gone (404): an order that exists has a run that
 * may belong to someone, and a run of any other entity type is not this sweep's to read.
 */
export async function sweepOrphanedOrderRuns(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0].use.baseURL ?? 'http://localhost:4200';
  const request = await playwrightRequest.newContext();
  try {
    const roots = await serviceRoots(request, baseURL);
    const platform = new PlatformApi(request, roots);
    const entities = new EntityApi(request, roots);

    const orphaned = new Set<string>();
    for (const run of await platform.workflowInstances()) {
      if (run.entityType !== 'order' || !run.entityId) continue;
      const order = await request.get(entities.url('order', run.entityId));
      if (order.status() === 404) orphaned.add(run.entityId);
    }

    for (const entityId of orphaned) await platform.deleteWorkflowInstances({ entityId });
    if (orphaned.size > 0) console.warn(`[global-teardown] removed the workflow runs of ${orphaned.size} order(s) that no longer exist.`);
  } catch (error) {
    console.warn(`[global-teardown] workflow run sweep stopped — ${error instanceof Error ? error.message.split('\n')[0] : String(error)}`);
  } finally {
    await request.dispose();
  }
}
