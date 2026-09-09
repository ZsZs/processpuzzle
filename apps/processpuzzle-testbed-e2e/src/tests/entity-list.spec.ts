import { defineEntityListSuite } from '@processpuzzle/e2e-testing';
import { testConfig } from '../../playwright.config';
import { REGISTRY_PATH } from '../support/global-setup';

defineEntityListSuite({
  registryPath: REGISTRY_PATH,
  routePrefix: testConfig.routePrefix,
  excludedEntities: [
    {
      entityName: 'Workflow Instance',
      // The assertion here is that the list renders *rows*, and nothing in this workspace produces a workflow
      // instance: one exists only after POST /instances with a StartWorkflowRequest, which no seed file and no
      // generated form can send — the descriptor is isAbstract precisely because the runtime side of
      // base-workflow-api.yaml is read-only. The screen itself is covered by base-workflow-frontend's unit
      // tests. Drop this entry once something seeds or starts an instance for the testbed tenant.
      reason: 'nothing starts a workflow instance, so the list is legitimately empty',
    },
  ],
});
