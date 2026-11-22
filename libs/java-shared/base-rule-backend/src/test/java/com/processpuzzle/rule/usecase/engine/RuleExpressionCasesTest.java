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
import org.junit.jupiter.api.TestFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs {@code rule-expression-cases.json} — the cases base-rule-frontend's interpreter spec runs too. The browser no longer
 * hands a rule's text to {@code new Function}: it interprets the subset of JavaScript rules are written in. These cases pin
 * that subset to what this engine, which runs all of JavaScript, answers for the same text.
 */
class RuleExpressionCasesTest {

    private static final RuleKey KEY = RuleKey.of("demo", "expressions");
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

    private static JsonNode read() {
        try (InputStream source = RuleExpressionCasesTest.class.getResourceAsStream("/rule-expression-cases.json")) {
            return new ObjectMapper().readTree(source);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
