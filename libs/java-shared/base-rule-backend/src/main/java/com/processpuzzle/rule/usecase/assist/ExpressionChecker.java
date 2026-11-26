package com.processpuzzle.rule.usecase.assist;

import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.rule.port.RuleContext;
import com.processpuzzle.rule.usecase.assist.SampleEntities.Sample;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.graalvm.polyglot.PolyglotException;
import org.springframework.stereotype.Component;

/**
 * Checks a candidate rule expression the way the engine will meet it, short of saving it: that it compiles, that it names
 * only attributes the entity has, and that it runs and answers a boolean on objects built from the entity's description.
 *
 * <p>A syntax error stops the check, since nothing else can be said. A failure on the object with every attribute filled is
 * an error, because it would fail on real data too; a failure on the sparser objects is a warning, since the rule may be
 * meant for data that always has what it reads. A failure that touches an attribute the samples could not fill is only a
 * warning, because the sample, not the rule, may be at fault.
 */
@Component
public class ExpressionChecker {

    private static final String SOURCE = "expression";
    private static final String CHECK_RULE_ID = "assist-check";

    private final RuleEngine engine;

    public ExpressionChecker(RuleEngine engine) {
        this.engine = engine;
    }

    public List<AuthoringIssue> check(String orgKey, Optional<RuleContext> context, String expression) {
        if (expression == null || expression.isBlank()) {
            return List.of(AuthoringIssue.error(SOURCE, "The expression is empty."));
        }
        RuleKey key = new RuleKey(orgKey, CHECK_RULE_ID);
        List<Sample> samples = context.map(SampleEntities::of)
            .orElseGet(() -> List.of(new Sample(Sample.FILLED, Map.of(), Set.of())));
        List<AuthoringIssue> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Sample sample : samples) {
            Optional<Failure> failure = run(key, expression, sample);
            if (failure.isPresent() && failure.get().syntax()) {
                return List.of(AuthoringIssue.error(SOURCE, "Syntax error: " + failure.get().message()));
            }
            failure.filter(f -> seen.add(f.message())).ifPresent(f -> issues.add(issueOf(f, sample, expression, context)));
        }
        context.ifPresent(known -> issues.addAll(0, ExpressionPaths.unknownAttributes(expression, known)));
        return issues;
    }

    private Optional<Failure> run(RuleKey key, String expression, Sample sample) {
        try {
            engine.evaluate(key, expression, sample.data());
            return Optional.empty();
        } catch (PolyglotException e) {
            return Optional.of(new Failure(e.isSyntaxError(), e.getMessage()));
        } catch (ClassCastException | NullPointerException e) {
            // Value.asBoolean() refuses a result that is not a boolean: with a ClassCastException for another value, and with a
            // NullPointerException for null or undefined, which is what reading an attribute the object lacks gives.
            return Optional.of(new Failure(false, "the expression must resolve to true or false, not to another value"));
        }
    }

    private static AuthoringIssue issueOf(Failure failure, Sample sample, String expression, Optional<RuleContext> context) {
        String text = "It fails on %s: %s".formatted(sample.name(), failure.message());
        return context.isPresent() && isCertain(sample, expression)
            ? AuthoringIssue.error(SOURCE, text) : AuthoringIssue.warning(SOURCE, text);
    }

    /** Whether a failure on {@code sample} is the rule's: on the full object, and not on an attribute it could not fill. */
    private static boolean isCertain(Sample sample, String expression) {
        return Sample.FILLED.equals(sample.name())
            && Collections.disjoint(sample.unsampled(), ExpressionPaths.roots(expression));
    }

    private record Failure(boolean syntax, String message) {
    }
}
