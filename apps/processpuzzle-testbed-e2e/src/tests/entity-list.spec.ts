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
    {
      entityName: 'Organization Unit',
      // The list is the units this organization added to the standard catalog, and the testbed seed adds none: one exists only
      // after a designer creates it. Drop this entry once something seeds a unit for the testbed tenant.
      reason: 'nothing seeds an organization unit, so the list is legitimately empty',
    },
    {
      entityName: 'Exchange Rate',
      // The list is the rates this organization entered, and the testbed seed enters none: one exists only after a designer
      // creates it. Drop this entry once something seeds a rate for the testbed tenant.
      reason: 'nothing seeds an exchange rate, so the list is legitimately empty',
    },
    {
      entityName: 'IssuedDocument',
      // The list is the signatures of the organization, and nothing in the testbed seed signs a report: one
      // exists only after a user signs. Drop this entry once something seeds a signature for the testbed tenant.
      reason: 'nothing signs a report in the seed, so the list is legitimately empty',
    },
  ],
});
