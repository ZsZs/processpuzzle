package com.processpuzzle.calculation.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.calculation.engine.ExpressionException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CamundaFeelExpressionEngineTest {

    private final CamundaFeelExpressionEngine feel = new CamundaFeelExpressionEngine();

    @Test
    void computesInDecimalsNotDoubles() {
        assertThat(feel.evaluate("price * 3", Map.of("price", new BigDecimal("19.99")))).isEqualTo(new BigDecimal("59.97"));
        assertThat(feel.evaluate("0.1 + 0.2", Map.of())).isEqualTo(new BigDecimal("0.3"));
    }

    @Test
    void handsBackListsContextsAndTemporals() {
        assertThat(feel.evaluate("[1, 2]", Map.of())).isEqualTo(List.of(BigDecimal.ONE, BigDecimal.valueOf(2)));
        assertThat(feel.evaluate("{a: 1, b: \"x\"}", Map.of())).isEqualTo(Map.of("a", BigDecimal.ONE, "b", "x"));
        assertThat(feel.evaluate("d + duration(\"P1D\")", Map.of("d", LocalDate.of(2026, 1, 1)))).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(feel.evaluate("duration(\"PT1H\")", Map.of())).isEqualTo(Duration.ofHours(1));
        assertThat(feel.evaluate("rows[1].x", Map.of("rows", List.of(Map.of("x", 5))))).isEqualTo(BigDecimal.valueOf(5));
    }

    @Test
    void testsValuesAgainstUnaryTests() {
        assertThat(feel.test(">= 1000", BigDecimal.valueOf(1000), Map.of())).isTrue();
        assertThat(feel.test("\"DNF\", \"DNS\"", "DNS", Map.of())).isTrue();
        assertThat(feel.test("< limit", 5, Map.of("limit", 3))).isFalse();
    }

    @Test
    void reportsSyntaxErrorsAndFailures() {
        assertThat(feel.checkExpression("1 +")).isPresent();
        assertThat(feel.checkExpression("1 + 2")).isEmpty();
        assertThat(feel.checkUnaryTests(">>> 1")).isPresent();
        assertThat(feel.checkUnaryTests("> 1, < 0")).isEmpty();
        assertThatThrownBy(() -> feel.evaluate("1 +", Map.of())).isInstanceOf(ExpressionException.class)
                .hasMessageStartingWith("'1 +': ");
        assertThatThrownBy(() -> feel.test(">>> 1", 1, Map.of())).isInstanceOf(ExpressionException.class);
    }

    @Test
    void passesFunctionValuesAsFunctions() {
        Object twice = feel.evaluate("function(x) x * 2", Map.of());
        assertThat(feel.evaluate("twice(21)", Map.of("twice", twice))).isEqualTo(BigDecimal.valueOf(42));
    }

    @Test
    void refusesThePlatformFunctionsOnOtherValues() {
        assertThat(feel.evaluate("top([\"a\"], 1)", Map.of())).isNull();
        assertThat(feel.evaluate("drop worst(1, 1)", Map.of())).isNull();
        assertThat(feel.evaluate("drop best([3, 1, 2], 5)", Map.of())).isEqualTo(List.of());
    }
}
