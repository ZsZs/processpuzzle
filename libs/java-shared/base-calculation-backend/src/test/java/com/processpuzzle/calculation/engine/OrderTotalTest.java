package com.processpuzzle.calculation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The proposal's sales example as the engine's acceptance test: line totals, a subtotal and a tiered discount. */
class OrderTotalTest {

    private final CalculationPlan plan = Calculations.plan("order-total");

    @Test
    void sumsTheLinesWithoutADiscountBelowTheFirstTier() {
        EvaluationResult result = evaluate(Map.of(), lines(line(2, "19.99"), line(1, "49.90")));

        assertThat(result.rows().get("lines")).extracting(row -> row.get("lineTotal"))
                .containsExactly(new BigDecimal("39.98"), new BigDecimal("49.90"));
        assertThat(result.values()).containsEntry("subtotal", new BigDecimal("89.88"));
        assertThat((BigDecimal) result.values().get("discountRate")).isEqualByComparingTo("0");
        assertThat(result.values()).containsEntry("total", new BigDecimal("89.88"));
    }

    @Test
    void appliesTheTierTheSubtotalReaches() {
        EvaluationResult result = evaluate(Map.of(), lines(line(3, "333.33"), line(1, "100.01")));

        assertThat(result.values()).containsEntry("subtotal", new BigDecimal("1100.00"));
        assertThat((BigDecimal) result.values().get("discountRate")).isEqualByComparingTo("0.03");
        assertThat(result.values()).containsEntry("total", new BigDecimal("1067.00"));
    }

    @Test
    void givesGoldCustomersTheTopTier() {
        assertThat(evaluate(Map.of("customerTier", "GOLD"), lines(line(10, "1000"))).values())
                .containsEntry("total", new BigDecimal("9000.00"));
        assertThat(evaluate(Map.of(), lines(line(10, "1000"))).values()).containsEntry("total", new BigDecimal("9300.00"));
    }

    @Test
    void anOrderWithoutLinesTotalsZero() {
        assertThat((BigDecimal) evaluate(Map.of(), List.of()).values().get("total")).isEqualByComparingTo("0");
    }

    private EvaluationResult evaluate(Map<String, Object> extra, List<Map<String, Object>> lines) {
        Map<String, Object> parameters = new java.util.HashMap<>(extra);
        parameters.put("order", Map.of("id", "order-1", "lineItems", lines));
        return Calculations.ENGINE.evaluate(plan, parameters, Map.of("lines", lines));
    }

    @SafeVarargs
    private static List<Map<String, Object>> lines(Map<String, Object>... lines) {
        return List.of(lines);
    }

    private static Map<String, Object> line(int quantity, String unitPrice) {
        return Map.of("productName", "Widget", "quantity", BigDecimal.valueOf(quantity), "unitPrice", new BigDecimal(unitPrice));
    }
}
