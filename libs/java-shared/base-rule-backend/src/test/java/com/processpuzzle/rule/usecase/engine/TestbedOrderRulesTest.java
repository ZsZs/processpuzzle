package com.processpuzzle.rule.usecase.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The testbed's sample order rules, read from the seed file itself, against orders whose quantities and prices are the
 * measures the order definitions declare — what a form and the backend both evaluate.
 */
class TestbedOrderRulesTest {

    private JsonNode rules;
    private RuleEngine engine;

    @BeforeEach
    void setUp() throws IOException {
        try (InputStream in = new ClassPathResource("sample-rules/processpuzzle-testbed-rules.yaml").getInputStream()) {
            rules = new ObjectMapper(new YAMLFactory()).readTree(in).get("rules");
        }
        engine = new RuleEngine();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    private boolean holds(String ruleId, Map<String, Object> entity) {
        for (JsonNode rule : rules) {
            if (ruleId.equals(rule.get("id").asText())) {
                return engine.evaluate(RuleKey.of("processpuzzle-testbed", ruleId), rule.get("expression").asText(), entity);
            }
        }
        throw new AssertionError("no sample rule " + ruleId);
    }

    private static Map<String, Object> line(String pieces, String unit, String price) {
        return Map.of("productName", "Widget",
                "quantity", Map.of("amount", pieces, "unit", unit),
                "unitPrice", Map.of("amount", price, "currency", "EUR"));
    }

    private static Map<String, Object> order(String total, Map<String, Object>... lines) {
        return Map.of("lineItems", List.of(lines), "total", Map.of("amount", total, "currency", "EUR"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyLineOfAnOrderNeedsAPositiveQuantity() {
        assertThat(holds("positive-quantities", order("89.88", line("2", "pc", "19.99"), line("1", "dz", "49.90")))).isTrue();
        assertThat(holds("positive-quantities", order("0", line("0", "pc", "0")))).isFalse();
        assertThat(holds("positive-quantities", Map.of())).as("no lines, nothing to refuse").isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSpecialOrderMayCarryAZeroQuantityPlaceholder() {
        assertThat(holds("special-order-allows-zero-quantity", order("1250", line("5", "pc", "250"), line("0", "pc", "0")))).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTotalIsTheSumOfTheLinesInDozensAndPieces() {
        // 2 dozen at 10.00 and 3 pieces at 1.50: 24 × 10.00 + 3 × 1.50
        assertThat(holds("total-matches-line-items", order("244.50", line("2", "dz", "10.00"), line("3", "pc", "1.50")))).isTrue();
        assertThat(holds("total-matches-line-items", order("245.00", line("2", "dz", "10.00"), line("3", "pc", "1.50")))).isFalse();
    }
}
