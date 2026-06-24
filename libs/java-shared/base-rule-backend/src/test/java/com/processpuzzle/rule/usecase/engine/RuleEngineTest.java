package com.processpuzzle.rule.usecase.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

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
        ruleEngine.registerRule("allQuantitiesPositive",
                "entity.lineItems.every(li => li.quantity > 0)");

        Map<String, Object> order = orderWith(
                lineItem("SKU-1", 2, 10.0),
                lineItem("SKU-2", 1, 25.0)
        );

        assertTrue(ruleEngine.evaluate("allQuantitiesPositive", order));
    }

    @Test
    void allQuantitiesPositive_failsWhenOneIsZero() {
        ruleEngine.registerRule("allQuantitiesPositive",
                "entity.lineItems.every(li => li.quantity > 0)");

        Map<String, Object> order = orderWith(
                lineItem("SKU-1", 0, 10.0)
        );

        assertFalse(ruleEngine.evaluate("allQuantitiesPositive", order));
    }

    @Test
    void totalMatchesLineItems() {
        ruleEngine.registerRule("totalMatchesLineItems",
                "Math.abs(entity.total - "
                        + "entity.lineItems.reduce((sum, li) => sum + li.unitPrice * li.quantity, 0)) < 0.001");

        Map<String, Object> order = orderWith(
                lineItem("SKU-1", 2, 10.0),
                lineItem("SKU-2", 1, 25.0)
        );

        order.put("total", 45.0);
        assertTrue(ruleEngine.evaluate("totalMatchesLineItems", order));

        order.put("total", 50.0);
        assertFalse(ruleEngine.evaluate("totalMatchesLineItems", order));
    }

    @Test
    void shippedRequiresAddress_conditionalImpliesRule() {
        // PPCL "implies" translates naturally to JS: !condition || consequence
        ruleEngine.registerRule("shippedRequiresAddress",
                "entity.status !== 'SHIPPED' || entity.shippingAddress != null");

        Map<String, Object> draft = orderWith(lineItem("SKU-1", 1, 10.0));
        draft.put("status", "DRAFT");
        draft.put("shippingAddress", null);
        assertTrue(ruleEngine.evaluate("shippedRequiresAddress", draft));

        Map<String, Object> shippedNoAddress = orderWith(lineItem("SKU-1", 1, 10.0));
        shippedNoAddress.put("status", "SHIPPED");
        shippedNoAddress.put("shippingAddress", null);
        assertFalse(ruleEngine.evaluate("shippedRequiresAddress", shippedNoAddress));

        Map<String, Object> shippedWithAddress = orderWith(lineItem("SKU-1", 1, 10.0));
        shippedWithAddress.put("status", "SHIPPED");
        shippedWithAddress.put("shippingAddress", "123 Main St");
        assertTrue(ruleEngine.evaluate("shippedRequiresAddress", shippedWithAddress));
    }

    @Test
    void hostAccessIsBlocked() {
        // Regardless of whether a `Java` global is visible at all, attempting to use it for
        // interop must fail, since the Context is built with allowHostAccess(HostAccess.NONE).
        ruleEngine.registerRule("attemptsJavaInterop",
                "(() => { "
                        + "try { Java.type('java.lang.System').exit(1); return false; } "
                        + "catch (e) { return true; } "
                        + "})()");

        Map<String, Object> order = orderWith(lineItem("SKU-1", 1, 10.0));

        assertTrue(ruleEngine.evaluate("attemptsJavaInterop", order),
                "Java interop must be unreachable from within a rule expression");
    }

    @Test
    void rejectsAttemptsToMutateEntityData() {
        ruleEngine.registerRule("attemptsMutation",
                "(() => { "
                        + "try { entity.total = 0; return false; } "
                        + "catch (e) { return true; } "
                        + "})()");

        Map<String, Object> order = orderWith(lineItem("SKU-1", 1, 10.0));
        order.put("total", 10.0);

        assertTrue(ruleEngine.evaluate("attemptsMutation", order),
                "Entity data exposed to a rule must be read-only");
    }

    @Test
    void unregisterRuleRemovesIt() {
        ruleEngine.registerRule("temp-rule", "true");
        assertTrue(ruleEngine.isRegistered("temp-rule"));

        ruleEngine.unregisterRule("temp-rule");

        assertFalse(ruleEngine.isRegistered("temp-rule"));
        assertThrows(IllegalArgumentException.class,
                () -> ruleEngine.evaluate("temp-rule", new HashMap<>()));
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
