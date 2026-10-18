package com.processpuzzle.calculation.engine;

import java.util.Map;
import java.util.Optional;

/**
 * The FEEL engine as the calculation engine needs it: evaluate an expression, test a value against unary tests,
 * and check either for syntax. The engine names no implementation; {@code CamundaFeelExpressionEngine} is the one
 * the module ships.
 *
 * <p>Values cross this port as plain Java: {@link java.math.BigDecimal} numbers, {@code java.time} dates, times
 * and durations, {@link java.util.List}s and {@link java.util.Map}s. A function value a FEEL expression yields is
 * handed back opaque and may be passed in again as a variable.
 */
public interface ExpressionEnginePort {

    /**
     * @throws ExpressionException when the expression does not parse or fails to evaluate
     */
    Object evaluate(String expression, Map<String, Object> variables);

    /**
     * Whether {@code input} satisfies the unary tests — {@code "DNF", "DNS"}, {@code >= 1000}, {@code -}.
     *
     * @throws ExpressionException when the tests do not parse or fail to evaluate
     */
    boolean test(String unaryTests, Object input, Map<String, Object> variables);

    /** The syntax error of an expression, empty when it parses. */
    Optional<String> checkExpression(String expression);

    /** The syntax error of unary tests, empty when they parse. */
    Optional<String> checkUnaryTests(String unaryTests);

    /** A FEEL expression that failed — to parse or to evaluate. */
    class ExpressionException extends RuntimeException {

        public ExpressionException(String expression, String message) {
            super("'%s': %s".formatted(expression, message));
        }
    }
}
