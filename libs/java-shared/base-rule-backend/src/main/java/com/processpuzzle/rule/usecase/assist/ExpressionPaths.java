package com.processpuzzle.rule.usecase.assist;

import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.rule.port.RuleContext;
import com.processpuzzle.rule.port.RuleContext.ContextAttribute;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks the attribute paths of a rule expression against the entity it is written for. The engine cannot do it: the
 * {@code entity} it hands a rule answers {@code undefined} for a name it does not have, so {@code entity.totl > 0} runs
 * and is quietly false for every object. A misspelt attribute is the commonest fault of a generated rule.
 *
 * <p>Only literal chains from {@code entity} are followed, such as {@code entity.address.city}; what a lambda parameter
 * reaches ({@code li => li.quantity}) is not traced, and a chain ends at a list, whose next member is an array method.
 */
final class ExpressionPaths {

    private static final String NAME = "[A-Za-z_$][\\w$]*";
    private static final String LITERAL = "(?:'[^']*'|\"[^\"]*\")";
    private static final Pattern CHAIN = Pattern.compile(
        "(?<![\\w$.])entity((?:\\s*\\??\\.\\s*" + NAME + "|\\s*\\[\\s*" + LITERAL + "\\s*\\])+)");
    private static final Pattern STEP = Pattern.compile(
        "\\??\\.\\s*(" + NAME + ")|\\[\\s*['\"]([^'\"]*)['\"]\\s*\\]");

    private ExpressionPaths() {
    }

    /** The chains the expression reads from {@code entity}, each as its member names: {@code entity.a.b} is [a, b]. */
    static List<List<String>> chains(String expression) {
        List<List<String>> chains = new ArrayList<>();
        Matcher chain = CHAIN.matcher(expression);
        while (chain.find()) {
            List<String> names = new ArrayList<>();
            Matcher step = STEP.matcher(chain.group(1));
            while (step.find()) {
                names.add(step.group(1) != null ? step.group(1) : step.group(2));
            }
            chains.add(names);
        }
        return chains;
    }

    /** The top-level attributes the expression reads, for telling whether it touches one that could not be sampled. */
    static Set<String> roots(String expression) {
        return chains(expression).stream().map(names -> names.get(0)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** One error for each distinct path that names an attribute the entity does not have. */
    static List<AuthoringIssue> unknownAttributes(String expression, RuleContext context) {
        Set<String> reported = new LinkedHashSet<>();
        List<AuthoringIssue> issues = new ArrayList<>();
        for (List<String> names : chains(expression)) {
            firstUnknown(names, context.attributes()).ifPresent(unknown -> {
                if (reported.add(unknown.path())) {
                    issues.add(AuthoringIssue.error("expression", "'%s' is not an attribute of %s%s.".formatted(
                        unknown.path(), unknown.owner() == null ? context.name() : "the embedded " + unknown.owner(),
                        " (it has: " + unknown.known() + ")")));
                }
            });
        }
        return issues;
    }

    private static Optional<Unknown> firstUnknown(List<String> names, List<ContextAttribute> attributes) {
        List<ContextAttribute> level = attributes;
        String owner = null;
        StringBuilder path = new StringBuilder("entity");
        for (String name : names) {
            path.append('.').append(name);
            Optional<ContextAttribute> found = level.stream().filter(a -> a.name().equals(name)).findFirst();
            if (found.isEmpty()) {
                String known = level.stream().map(ContextAttribute::name).collect(Collectors.joining(", "));
                return Optional.of(new Unknown(path.toString(), owner, known));
            }
            ContextAttribute attribute = found.get();
            if (attribute.multiValued() || attribute.children().isEmpty()) {
                return Optional.empty();
            }
            level = attribute.children();
            owner = attribute.name();
        }
        return Optional.empty();
    }

    private record Unknown(String path, String owner, String known) {
    }
}
