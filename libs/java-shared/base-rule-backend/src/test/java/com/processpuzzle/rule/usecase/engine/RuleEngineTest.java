package com.processpuzzle.rule.usecase.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    private static final String ORG = "demo";

    private RuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new RuleEngine();
    }

    @AfterEach
    void tearDown() {
        ruleEngine.close();
    }

    @Test
    void allQuantitiesPositive_passesWhenAllPositive() {
        RuleKey key = RuleKey.of(ORG, "allQuantitiesPositive");
        ruleEngine.registerRule(key, "entity.lineItems.every(li => li.quantity > 0)");

        Map<String, Object> order = orderWith(
                lineItem("SKU-1", 2, 10.0),
                lineItem("SKU-2", 1, 25.0)
        );

        assertTrue(ruleEngine.evaluate(key, order));
    }

    @Test
    void allQuantitiesPositive_failsWhenOneIsZero() {
        RuleKey key = RuleKey.of(ORG, "allQuantitiesPositive");
        ruleEngine.registerRule(key, "entity.lineItems.every(li => li.quantity > 0)");

        Map<String, Object> order = orderWith(
                lineItem("SKU-1", 0, 10.0)
        );

        assertFalse(ruleEngine.evaluate(key, order));
    }

    @Test
    void totalMatchesLineItems() {
        RuleKey key = RuleKey.of(ORG, "totalMatchesLineItems");
        ruleEngine.registerRule(key,
                "Math.abs(entity.total - "
                        + "entity.lineItems.reduce((sum, li) => sum + li.unitPrice * li.quantity, 0)) < 0.001");

        Map<String, Object> order = orderWith(
                lineItem("SKU-1", 2, 10.0),
                lineItem("SKU-2", 1, 25.0)
        );

        order.put("total", 45.0);
        assertTrue(ruleEngine.evaluate(key, order));

        order.put("total", 50.0);
        assertFalse(ruleEngine.evaluate(key, order));
    }

    @Test
    void shippedRequiresAddress_conditionalImpliesRule() {
        // PPCL "implies" translates naturally to JS: !condition || consequence
        RuleKey key = RuleKey.of(ORG, "shippedRequiresAddress");
        ruleEngine.registerRule(key, "entity.status !== 'SHIPPED' || entity.shippingAddress != null");

        Map<String, Object> draft = orderWith(lineItem("SKU-1", 1, 10.0));
        draft.put("status", "DRAFT");
        draft.put("shippingAddress", null);
        assertTrue(ruleEngine.evaluate(key, draft));

        Map<String, Object> shippedNoAddress = orderWith(lineItem("SKU-1", 1, 10.0));
        shippedNoAddress.put("status", "SHIPPED");
        shippedNoAddress.put("shippingAddress", null);
        assertFalse(ruleEngine.evaluate(key, shippedNoAddress));

        Map<String, Object> shippedWithAddress = orderWith(lineItem("SKU-1", 1, 10.0));
        shippedWithAddress.put("status", "SHIPPED");
        shippedWithAddress.put("shippingAddress", "123 Main St");
        assertTrue(ruleEngine.evaluate(key, shippedWithAddress));
    }

    @Test
    void hostAccessIsBlocked() {
        // Regardless of whether a `Java` global is visible at all, attempting to use it for
        // interop must fail, since the Context is built with allowHostAccess(HostAccess.NONE).
        RuleKey key = RuleKey.of(ORG, "attemptsJavaInterop");
        ruleEngine.registerRule(key,
                "(() => { "
                        + "try { Java.type('java.lang.System').exit(1); return false; } "
                        + "catch (e) { return true; } "
                        + "})()");

        Map<String, Object> order = orderWith(lineItem("SKU-1", 1, 10.0));

        assertTrue(ruleEngine.evaluate(key, order),
                "Java interop must be unreachable from within a rule expression");
    }

    @Test
    void rejectsAttemptsToMutateEntityData() {
        RuleKey key = RuleKey.of(ORG, "attemptsMutation");
        ruleEngine.registerRule(key,
                "(() => { "
                        + "try { entity.total = 0; return false; } "
                        + "catch (e) { return true; } "
                        + "})()");

        Map<String, Object> order = orderWith(lineItem("SKU-1", 1, 10.0));
        order.put("total", 10.0);

        assertTrue(ruleEngine.evaluate(key, order),
                "Entity data exposed to a rule must be read-only");
    }

    @Test
    void anExpressionCanEnumerateTheEntitysOwnFields() {
        // A rule that asserts on the shape of the entity rather than its values, which is what
        // makes the proxy's key enumeration reachable.
        RuleKey key = RuleKey.of(ORG, "hasExpectedFields");
        ruleEngine.registerRule(key,
                "Object.keys(entity).sort().join(',') === 'lineItems,total'");

        assertTrue(ruleEngine.evaluate(key, orderWith(lineItem("SKU-1", 1, 10.0))));
    }

    @Test
    void unregisterRuleRemovesIt() {
        RuleKey key = RuleKey.of(ORG, "temp-rule");
        ruleEngine.registerRule(key, "true");
        assertTrue(ruleEngine.isRegistered(key));

        ruleEngine.unregisterRule(key);

        assertFalse(ruleEngine.isRegistered(key));
        assertThrows(IllegalArgumentException.class,
                () -> ruleEngine.evaluate(key, new HashMap<>()));
    }

    @Test
    void anEditedExpressionReplacesTheFunctionAlreadyCompiledOnThisThread() {
        RuleKey key = RuleKey.of(ORG, "edited-rule");
        ruleEngine.registerRule(key, "true");
        assertTrue(ruleEngine.evaluate(key, Map.of()));

        ruleEngine.registerRule(key, "false");
        assertFalse(ruleEngine.evaluate(key, Map.of()));

        ruleEngine.registerRule(key, "entity.allowed === true");
        assertTrue(ruleEngine.evaluate(key, Map.of("allowed", true)));
        assertFalse(ruleEngine.evaluate(key, Map.of("allowed", false)));
    }

    @Test
    void reRegisteringARemovedRuleDoesNotResurrectItsCompiledFunction() {
        RuleKey key = RuleKey.of(ORG, "recreated-rule");
        ruleEngine.registerRule(key, "true");
        assertTrue(ruleEngine.evaluate(key, Map.of()));

        ruleEngine.unregisterRule(key);
        assertThrows(IllegalArgumentException.class, () -> ruleEngine.evaluate(key, Map.of()));
        ruleEngine.registerRule(key, "false");

        assertFalse(ruleEngine.evaluate(key, Map.of()));
    }

    @Test
    void reRegistrationRefreshesEvenAThreadThatDidNotObserveRemoval() throws Exception {
        RuleKey key = RuleKey.of(ORG, "re-enabled-rule");
        ruleEngine.registerRule(key, "true");
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            assertTrue(evaluateOn(worker, key));
            ruleEngine.unregisterRule(key);
            ruleEngine.registerRule(key, "false");
            assertFalse(evaluateOn(worker, key));
        }
    }

    @Test
    void everyWarmedThreadSeesEditsWithoutCrossingTenantBoundaries() throws Exception {
        RuleKey key = RuleKey.of(ORG, "shared-id");
        RuleKey otherTenant = RuleKey.of("other", "shared-id");
        ruleEngine.registerRule(key, "true");
        ruleEngine.registerRule(otherTenant, "true");
        try (ExecutorService first = Executors.newSingleThreadExecutor();
             ExecutorService second = Executors.newSingleThreadExecutor()) {
            assertTrue(evaluateOn(first, key));
            assertTrue(evaluateOn(second, key));
            assertTrue(evaluateOn(first, otherTenant));
            assertTrue(evaluateOn(second, otherTenant));

            ruleEngine.registerRule(key, "false");

            assertFalse(evaluateOn(first, key));
            assertFalse(evaluateOn(second, key));
            assertTrue(evaluateOn(first, otherTenant));
            assertTrue(evaluateOn(second, otherTenant));
        }
    }

    @Test
    void anInvalidEditFailsInsteadOfExecutingThePreviousRevision() {
        RuleKey key = RuleKey.of(ORG, "invalid-edit");
        ruleEngine.registerRule(key, "true");
        assertTrue(ruleEngine.evaluate(key, Map.of()));
        ruleEngine.registerRule(key, "invalid (");

        assertThrows(org.graalvm.polyglot.PolyglotException.class,
                () -> ruleEngine.evaluate(key, Map.of()));

        ruleEngine.registerRule(key, "false");
        assertFalse(ruleEngine.evaluate(key, Map.of()));
    }

    private boolean evaluateOn(ExecutorService worker, RuleKey key) throws Exception {
        return worker.submit(() -> ruleEngine.evaluate(key, Map.of())).get(30, TimeUnit.SECONDS);
    }

    @Test
    void sameRuleIdInTwoOrganizations_keepsBothExpressions() {
        // The cache is keyed by (orgKey, ruleId): a bare-id key would let whichever organization
        // registered first decide the verdict for the other.
        RuleKey demoKey = RuleKey.of("demo", "max-quantity");
        RuleKey otherKey = RuleKey.of("other", "max-quantity");
        ruleEngine.registerRule(demoKey, "entity.lineItems.every(li => li.quantity <= 1)");
        ruleEngine.registerRule(otherKey, "entity.lineItems.every(li => li.quantity <= 5)");

        Map<String, Object> order = orderWith(lineItem("SKU-1", 3, 10.0));

        assertFalse(ruleEngine.evaluate(demoKey, order));
        assertTrue(ruleEngine.evaluate(otherKey, order));

        // Unregistering one organization's rule leaves the other's alone.
        ruleEngine.unregisterRule(demoKey);
        assertFalse(ruleEngine.isRegistered(demoKey));
        assertTrue(ruleEngine.isRegistered(otherKey));
    }

    @Test
    void unregisteredRule_namesTheOrganizationItBelongsTo() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ruleEngine.evaluate(RuleKey.of("demo", "missing"), new HashMap<>()));

        assertEquals("Rule not registered: demo/missing", thrown.getMessage());
    }

    private Map<String, Object> orderWith(Map<String, Object>... lineItems) {
        Map<String, Object> order = new HashMap<>();
        order.put("lineItems", List.of(lineItems));
        order.put("total", 0.0);
        return order;
    }

    private Map<String, Object> lineItem(String sku, int quantity, double unitPrice) {
        Map<String, Object> item = new HashMap<>();
        item.put("sku", sku);
        item.put("quantity", quantity);
        item.put("unitPrice", unitPrice);
        return item;
    }
}
