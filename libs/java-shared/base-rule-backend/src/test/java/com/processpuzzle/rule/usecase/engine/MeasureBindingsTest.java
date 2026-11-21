package com.processpuzzle.rule.usecase.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs {@code rule-measure-cases.json} — the cases base-rule-frontend's evaluator spec runs too — so a rule says the same
 * in the browser's dry run and in the backend that enforces it, a refusal included.
 */
class MeasureBindingsTest {

    private static final RuleKey KEY = RuleKey.of("demo", "measures");
    private static final JsonNode CASES = read();

    private RuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RuleEngine();
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entity() {
        return new ObjectMapper().convertValue(CASES.get("entity"), Map.class);
    }

    @TestFactory
    Stream<DynamicTest> theSharedCases() {
        return StreamSupport.stream(CASES.get("cases").spliterator(), false).map(c -> DynamicTest.dynamicTest(c.get("expression").asText(), () -> {
            String expression = c.get("expression").asText();
            if (c.has("error")) {
                assertThatThrownBy(() -> engine.evaluate(KEY, expression, entity())).hasMessageContaining(c.get("error").asText());
            } else {
                assertThat(engine.evaluate(KEY, expression, entity())).isEqualTo(c.get("expected").asBoolean());
            }
        }));
    }

    @Test
    void aMeasureObjectIsReadOnly() {
        assertThatThrownBy(() -> engine.evaluate(KEY, "(() => { const q = qty('5 kg'); q.amount = '9'; return true; })()", entity()))
                .hasMessageContaining("TypeError").hasMessageContaining("amount");
    }

    @Test
    void theHelpersAreAvailableToARegisteredRuleToo() {
        RuleKey key = RuleKey.of("demo", "weight-limit");
        engine.registerRule(key, "!entity.weight || qty(entity.weight).le(qty('10 kg'))");

        assertThat(engine.evaluate(key, entity())).isTrue();
        assertThat(engine.evaluate(key, Map.of("weight", Map.of("amount", "11000", "unit", "g")))).isFalse();
        assertThat(engine.evaluate(key, Map.of())).isTrue();
    }

    @Test
    void aMeasureCannotBeBuiltFromAnythingElse() {
        assertThatThrownBy(() -> engine.evaluate(KEY, "qty(5) === null", entity())).hasMessageContaining("not a quantity");
        assertThatThrownBy(() -> engine.evaluate(KEY, "qty('5 kg', '6 kg') === null", entity())).hasMessageContaining("exactly one argument");
        assertThatThrownBy(() -> engine.evaluate(KEY, "qty('1 kg').to(5)", entity())).hasMessageContaining("unit code");
        assertThatThrownBy(() -> engine.evaluate(KEY, "qty('1 kg').gt(5)", entity())).hasMessageContaining("expected a quantity");
        assertThatThrownBy(() -> engine.evaluate(KEY, "money('1 EUR').gt(5)", entity())).hasMessageContaining("expected money");
        assertThatThrownBy(() -> engine.evaluate(KEY, "money('1 EUR').times(NaN)", entity())).hasMessageContaining("expected a number");
        assertThatThrownBy(() -> engine.evaluate(KEY, "qty({ amount: 'x', unit: 'kg' })", entity())).hasMessageContaining("is not a decimal number");
        assertThatThrownBy(() -> engine.evaluate(KEY, "qty({ amount: true, unit: 'kg' })", entity())).hasMessageContaining("amount must be a decimal number");
    }

    private static JsonNode read() {
        try (InputStream source = MeasureBindingsTest.class.getResourceAsStream("/rule-measure-cases.json")) {
            return new ObjectMapper().readTree(source);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
