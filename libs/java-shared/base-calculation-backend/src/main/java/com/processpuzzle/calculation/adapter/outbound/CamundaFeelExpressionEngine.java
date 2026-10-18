package com.processpuzzle.calculation.adapter.outbound;

import com.processpuzzle.calculation.engine.ExpressionEnginePort;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;
import org.camunda.feel.api.ParseResult;
import org.camunda.feel.context.Context;
import org.camunda.feel.syntaxtree.ParsedExpression;
import org.camunda.feel.syntaxtree.ValFunction;
import org.springframework.stereotype.Component;
import scala.jdk.javaapi.CollectionConverters;

/**
 * {@link ExpressionEnginePort} over Camunda's feel-scala (Apache 2.0): FEEL 1.3 with the platform's functions
 * ({@link PlatformFunctions}) and {@link java.math.BigDecimal} numbers ({@link PlatformValueMapper}).
 *
 * <p>Parsed expressions are cached — a row decision evaluates the same expression once per row. The cache is
 * bounded, since a designer's previews evaluate sources nobody saved.
 */
@Component
public class CamundaFeelExpressionEngine implements ExpressionEnginePort {

    private static final int CACHE_SIZE = 2_000;

    private final FeelEngineApi engine = FeelEngineBuilder.create()
            .withFunctionProvider(new PlatformFunctions())
            .withCustomValueMapper(new PlatformValueMapper())
            .build();

    private final Map<String, ParsedExpression> expressions = cache();
    private final Map<String, ParsedExpression> unaryTests = cache();

    @Override
    public Object evaluate(String expression, Map<String, Object> variables) {
        EvaluationResult result = engine.evaluate(parsed(expressions, expression, false), contextOf(variables));
        if (result.isFailure()) {
            throw new ExpressionException(expression, result.failure().message());
        }
        return result.result();
    }

    @Override
    public boolean test(String tests, Object input, Map<String, Object> variables) {
        EvaluationResult result = engine.evaluateWithInput(parsed(unaryTests, tests, true), input, contextOf(variables));
        if (result.isFailure()) {
            throw new ExpressionException(tests, result.failure().message());
        }
        return Boolean.TRUE.equals(result.result());
    }

    @Override
    public Optional<String> checkExpression(String expression) {
        ParseResult result = engine.parseExpression(expression);
        return result.isSuccess() ? Optional.empty() : Optional.of(result.failure().message());
    }

    @Override
    public Optional<String> checkUnaryTests(String tests) {
        ParseResult result = engine.parseUnaryTests(tests);
        return result.isSuccess() ? Optional.empty() : Optional.of(result.failure().message());
    }

    private ParsedExpression parsed(Map<String, ParsedExpression> cache, String text, boolean asUnaryTests) {
        ParsedExpression cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        ParseResult result = asUnaryTests ? engine.parseUnaryTests(text) : engine.parseExpression(text);
        if (result.isFailure()) {
            throw new ExpressionException(text, result.failure().message());
        }
        cache.put(text, result.parsedExpression());
        return result.parsedExpression();
    }

    /**
     * The variables as a FEEL context. A function value — a calculation's {@code functions} — goes into the
     * context's functions rather than its variables: FEEL resolves {@code gross(100)} among functions only.
     */
    private static Context contextOf(Map<String, Object> variables) {
        Map<String, Object> values = new HashMap<>();
        Map<String, scala.collection.immutable.List<ValFunction>> functions = new HashMap<>();
        variables.forEach((name, value) -> {
            if (value instanceof ValFunction function) {
                functions.put(name, CollectionConverters.asScala(List.of(function)).toList());
            } else {
                values.put(name, value);
            }
        });
        return new Context.StaticContext(scala.collection.immutable.Map$.MODULE$.from(CollectionConverters.asScala(values)),
                scala.collection.immutable.Map$.MODULE$.from(CollectionConverters.asScala(functions)));
    }

    private static Map<String, ParsedExpression> cache() {
        return Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ParsedExpression> eldest) {
                return size() > CACHE_SIZE;
            }
        });
    }
}
