package com.processpuzzle.calculation.usecase.assist;

import com.processpuzzle.calculation.port.EntityCatalogPort;
import com.processpuzzle.calculation.port.EntityCatalogPort.Attribute;
import com.processpuzzle.calculation.port.EntityCatalogPort.EntityType;
import java.util.List;
import java.util.Optional;

/** A small entity catalog the assist tests share: an order with embedded lines, and a customer. */
public final class Shop {

    public static final EntityCatalogPort CATALOG = new EntityCatalogPort() {
        private final List<EntityType> types = List.of(
            new EntityType("customer", "Customer", false, List.of(
                plain("name", "TEXT", true), plain("tier", "TEXT", false))),
            new EntityType("order", "Order", false, List.of(
                plain("reference", "TEXT", true),
                plain("total", "NUMBER", false),
                new Attribute("lineItems", "REFERENCE", false, true, true, "order-line", null))),
            new EntityType("order-line", "Order Line", true, List.of(
                new Attribute("quantity", "TEXT", true, false, false, null, "QUANTITY"),
                new Attribute("unitPrice", "TEXT", false, false, false, null, "MONEY"),
                new Attribute("product", "REFERENCE", false, false, false, "product", null))));

        @Override
        public List<EntityType> all(String orgKey) {
            return types;
        }

        @Override
        public Optional<EntityType> find(String orgKey, String code) {
            return types.stream().filter(type -> type.code().equals(code)).findFirst();
        }
    };

    /** A valid calculation over {@link #CATALOG}. */
    public static final String ORDER_TOTAL = """
        calculation: order-total
        name: Order total
        description: Sum the order lines.
        parameters:
          order: { entity: order }
        inputs:
          lines: { from: order, path: lineItems, entity: order-line }
        decisions:
          lineTotal: { for: lines, expr: "quantity.baseAmount * unitPrice.baseAmount" }
          total: { requires: [lineTotal], aggregate: { sum: lineTotal } }
        output:
          entity: order
          mapping: { total: total }
        trigger:
          onChange: { entity: order }
        """;

    private Shop() {
    }

    private static Attribute plain(String name, String type, boolean required) {
        return new Attribute(name, type, required, false, false, null, null);
    }
}
