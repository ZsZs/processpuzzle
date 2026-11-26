package com.processpuzzle.rule.usecase.assist;

import com.processpuzzle.rule.port.RuleContext;
import com.processpuzzle.rule.port.RuleContext.ContextAttribute;
import java.util.List;

/** An entity description the assist tests share: an order with a total, dates, a weight, lines and an address. */
public final class Orders {

    private Orders() {
    }

    public static RuleContext context() {
        return new RuleContext("Order", List.of(
            plain("total", "NUMBER", true),
            plain("status", "ENUM", false),
            plain("startDate", "DATE", false),
            plain("endDate", "DATE", false),
            new ContextAttribute("weight", "TEXT", false, false, false, "QUANTITY", List.of("kg"), List.of()),
            new ContextAttribute("lineItems", "REFERENCE", false, true, true, null, List.of(), List.of(
                new ContextAttribute("quantity", "TEXT", true, false, false, "QUANTITY", List.of("pc"), List.of()),
                plain("amount", "NUMBER", false))),
            new ContextAttribute("address", "REFERENCE", false, false, true, null, List.of(), List.of(
                plain("city", "TEXT", false)))));
    }

    private static ContextAttribute plain(String name, String type, boolean required) {
        return new ContextAttribute(name, type, required, false, false, null, List.of(), List.of());
    }
}
