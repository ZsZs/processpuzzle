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
    {
      entityName: 'App Region',
      attrName: 'navItems',
      // Reached one level below [App Definition] regions. AppDefinitionValidator.validateRegionContents
      // allows navItems only on a 'sidenav' region, and the generated region takes the first Type option,
      // 'header' — so the PUT comes back 400 app.validation.nav-items-not-allowed. Picking 'sidenav' would
      // not help either: a generated nav item has neither a pageId (there are no pages to point at) nor
      // children, which is 'app.validation.dead-nav-item'. Nested EMBEDDED_COMPONENTS stays covered by
      // [Test Entity] embeddedComponents › Embedded Component.embeddedDetails.
      reason: "'app.validation.nav-items-not-allowed': only a sidenav region carries navItems, and a generated nav item would be a dead one",
    },
    {
      entityName: 'App Region',
      attrName: 'widgets',
      // Unlike the entries above, this one is not an invariant the fixture cannot satisfy — it is a framework
      // bug the flow reproduces. `App Widget.children` is self-referential, and an embedded store is a root
      // singleton keyed by entity name, so on a widget's own form the `children` list and the widget itself
      // resolve to the same rows (EmbeddedAggregateAccessor.levelsFor stops at the deepest occurrence of the
      // name). Editing the widget therefore saves it as its own child, and the backend rejects the PUT with
      // 400 app.validation.duplicate-widget-id. Remove this entry once nested self-containment is fixed.
      reason: 'a self-referential EMBEDDED_COMPONENTS attribute makes a row its own child on save (App Widget.children)',
    },
  ],
});
