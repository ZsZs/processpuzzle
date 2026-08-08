import { defineEntityRelationshipSuite } from '@processpuzzle/e2e-testing';
import { testConfig } from '../../playwright.config';
import { REGISTRY_PATH } from '../support/global-setup';

defineEntityRelationshipSuite({
  registryPath: REGISTRY_PATH,
  routePrefix: testConfig.routePrefix,
  excludedRelationships: [
    {
      entityName: 'Order',
      attrName: 'lineItems',
      // 'total-matches-line-items' in base-rule-backend/src/main/resources/sample-rules/processpuzzle-testbed-rules.yaml
      // is an ERROR rule: an order's total has to equal the sum of its line item subtotals. The generated
      // fixture gives every numeric field the same value, so attaching one line item makes the two disagree
      // and the rule engine — correctly — refuses the save. RELATED_ENTITIES stays covered by [Test Entity]
      // relatedEntities and by [App Definition] pages / regions, neither of which carries an aggregate rule.
      reason: "the 'total-matches-line-items' ERROR rule rejects an order whose total does not match its line items",
    },
    {
      entityName: 'App Definition',
      attrName: 'pages',
      // The backend answers the PUT with 400 app.validation.orphan-page: a page has to be reachable, and
      // nothing references one until a sidenav nav item points at it — two embedded levels away, in a
      // different region's array, which the single-row flow here cannot reach. EMBEDDED_COMPONENTS stays
      // covered by [App Definition] regions and by [Test Entity] embeddedComponents.
      reason: "the backend's 'app.validation.orphan-page' check rejects a page that no nav item references",
    },
  ],
});
