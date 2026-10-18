package com.processpuzzle.calculation.engine;

import com.processpuzzle.calculation.domain.definition.AggregateSpec;
import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.DecisionKind;
import com.processpuzzle.calculation.domain.definition.DecisionSpec;
import com.processpuzzle.calculation.domain.definition.DecisionTable;
import com.processpuzzle.calculation.domain.definition.FreezeSpec;
import com.processpuzzle.calculation.domain.definition.InputSpec;
import com.processpuzzle.calculation.domain.definition.OutputSpec;
import com.processpuzzle.calculation.domain.definition.ParameterSpec;
import com.processpuzzle.calculation.domain.definition.RankSpec;
import com.processpuzzle.calculation.domain.definition.StateRef;
import com.processpuzzle.calculation.domain.definition.TableRule;
import com.processpuzzle.calculation.domain.definition.TriggerSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The static check of a calculation: unknown references, the one-logic-type rule, table arity, scopes, FEEL syntax
 * and cycles — and, for a valid calculation, the evaluation order.
 *
 * <p>A decision depends on the decisions its {@code requires} lists <em>and</em> on every decision its expressions
 * name, so a forgotten {@code requires} cannot evaluate a decision before what it reads. Names inside FEEL string
 * literals do not count.
 */
public class CalculationAnalyzer {

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]*$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z_]\\w*$");
    private static final Pattern IDENTIFIER = Pattern.compile("(?<![\\w.$])[A-Za-z_]\\w*");
    private static final Pattern QUOTED = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    /** {@code ${parameter}} and {@code ${parameter.attribute}} in an input's filter. */
    public static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private final ExpressionEnginePort feel;

    public CalculationAnalyzer(ExpressionEnginePort feel) {
        this.feel = feel;
    }

    public CalculationAnalysis analyze(CalculationModel model) {
        return new Run(model).analyze();
    }

    /** One analysis; holds what the checks share. */
    private final class Run {

        private final CalculationModel model;
        private final List<String> errors = new ArrayList<>();
        private final Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        private final Map<String, Scope> scopes = new HashMap<>();
        private final Set<String> resolving = new HashSet<>();

        Run(CalculationModel model) {
            this.model = model;
        }

        CalculationAnalysis analyze() {
            checkHeader();
            checkParameters();
            checkInputs();
            checkTables();
            checkFunctions();

            model.decisions().forEach((name, spec) -> dependencies.put(name, dependenciesOf(name, spec)));
            List<PlannedDecision> decisions = new ArrayList<>();
            model.decisions().forEach((name, spec) -> {
                Scope scope = scopeOf(name);
                Scope source = spec.kind() == DecisionKind.AGGREGATE && scope != null && scope.isScalar()
                        ? sourceOf(name, spec.aggregate()) : null;
                decisions.add(new PlannedDecision(name, spec, spec.kind(), scope, source, dependencies.get(name)));
            });
            decisions.forEach(this::checkDecision);
            checkGroupAttributes(decisions);
            checkOutput();
            checkTrigger();

            List<CalculationAnalysis.Dependency> edges = new ArrayList<>();
            dependencies.forEach((name, needed) -> needed.forEach(from -> edges.add(new CalculationAnalysis.Dependency(from, name))));
            List<PlannedDecision> order = order(decisions);
            CalculationPlan plan = errors.isEmpty() ? new CalculationPlan(model, order) : null;
            return new CalculationAnalysis(errors, decisions, edges, plan);
        }

        // ---- header, parameters, inputs, tables, functions

        private void checkHeader() {
            if (isBlank(model.id())) {
                errors.add("'calculation' — the id — is required");
            } else if (!ID.matcher(model.id()).matches()) {
                errors.add("calculation id '%s' may contain letters, digits, '-' and '_' only".formatted(model.id()));
            }
            if (model.decisions().isEmpty()) {
                errors.add("a calculation needs at least one decision");
            }
        }

        private void checkParameters() {
            model.parameters().forEach((name, parameter) -> {
                checkName("parameter", name);
                if (parameter == null) {
                    errors.add("parameter '%s' needs an 'entity' or a 'type'".formatted(name));
                    return;
                }
                boolean typed = !isBlank(parameter.type());
                if (parameter.isEntity() == typed) {
                    errors.add("parameter '%s' needs exactly one of 'entity' and 'type'".formatted(name));
                } else if (typed && !ParameterSpec.TYPES.contains(parameter.type())) {
                    errors.add("parameter '%s' has type '%s'; expected one of %s".formatted(name, parameter.type(),
                            String.join(", ", ParameterSpec.TYPES.stream().sorted().toList())));
                }
            });
        }

        private void checkInputs() {
            model.inputs().forEach((name, input) -> {
                checkName("input", name);
                if (input == null) {
                    errors.add("input '%s' needs an 'entity' or a 'from'".formatted(name));
                } else if (input.isEmbedded()) {
                    ParameterSpec parameter = model.parameters().get(input.from());
                    if (parameter == null || !parameter.isEntity()) {
                        errors.add("input '%s' reads from '%s', which is no entity parameter".formatted(name, input.from()));
                    }
                    if (isBlank(input.path())) {
                        errors.add("input '%s' needs the 'path' of the rows inside '%s'".formatted(name, input.from()));
                    }
                    if (!isBlank(input.where())) {
                        errors.add("input '%s' takes no 'where' — it reads embedded rows".formatted(name));
                    }
                } else {
                    if (isBlank(input.entity())) {
                        errors.add("input '%s' needs an 'entity' or a 'from'".formatted(name));
                    }
                    if (!isBlank(input.path())) {
                        errors.add("input '%s' has a 'path' but no 'from'".formatted(name));
                    }
                    checkPlaceholders(name, input);
                }
            });
        }

        private void checkPlaceholders(String name, InputSpec input) {
            if (input.where() == null) {
                return;
            }
            Matcher matcher = PLACEHOLDER.matcher(input.where());
            while (matcher.find()) {
                String parameter = matcher.group(1).trim().split("\\.", 2)[0];
                if (!model.parameters().containsKey(parameter)) {
                    errors.add("input '%s' filters on '${%s}', but there is no parameter '%s'".formatted(name,
                            matcher.group(1), parameter));
                }
            }
        }

        private void checkTables() {
            model.tables().forEach((name, table) -> {
                if (table == null || table.inputs().isEmpty()) {
                    errors.add("table '%s' needs 'inputs'".formatted(name));
                    return;
                }
                table.inputs().forEach(input -> syntax("table '%s' input".formatted(name), input));
                if (table.rules().isEmpty()) {
                    errors.add("table '%s' needs at least one rule".formatted(name));
                }
                for (int index = 0; index < table.rules().size(); index++) {
                    checkRule(name, table, index + 1, table.rules().get(index));
                }
            });
        }

        private void checkRule(String tableName, DecisionTable table, int number, TableRule rule) {
            String where = "table '%s' rule %d".formatted(tableName, number);
            List<String> cells = rule.cells();
            if (cells.size() != table.inputs().size()) {
                errors.add("%s has %d cell(s) for %d input(s)".formatted(where, cells.size(), table.inputs().size()));
            }
            for (String cell : cells) {
                if (!TableRule.ANY.equals(cell)) {
                    feel.checkUnaryTests(cell).ifPresent(error -> errors.add("%s: '%s' is no unary test: %s".formatted(where,
                            cell, error)));
                }
            }
            if (isBlank(rule.outputExpression())) {
                errors.add("%s needs a 'then'".formatted(where));
            } else {
                syntax(where + " output", rule.outputExpression());
            }
        }

        private void checkFunctions() {
            model.functions().forEach((name, body) -> {
                checkName("function", name);
                if (isBlank(body) || !body.trim().startsWith("function")) {
                    errors.add("function '%s' must be a FEEL function definition: function(<parameters>) <body>".formatted(name));
                } else {
                    syntax("function '%s'".formatted(name), body);
                }
            });
        }

        // ---- decisions

        private Set<String> dependenciesOf(String name, DecisionSpec spec) {
            Set<String> needed = new LinkedHashSet<>();
            if (spec == null) {
                return needed;
            }
            spec.requires().stream().filter(model.decisions()::containsKey).forEach(needed::add);
            for (String text : textsOf(spec)) {
                identifiersIn(text).stream().filter(model.decisions()::containsKey).forEach(needed::add);
            }
            needed.remove(name);
            return needed;
        }

        /** Every piece of FEEL or column name a decision evaluates. */
        private List<String> textsOf(DecisionSpec spec) {
            List<String> texts = new ArrayList<>();
            add(texts, spec.expr());
            if (spec.aggregate() != null) {
                texts.addAll(spec.aggregate().functions().values());
                add(texts, spec.aggregate().partitionBy());
                addIfText(texts, spec.aggregate().dropWorst());
                addIfText(texts, spec.aggregate().dropBest());
            }
            if (spec.rank() != null) {
                add(texts, spec.rank().by());
                add(texts, spec.rank().partitionBy());
                add(texts, spec.rank().filter());
                spec.rank().tieBreak().forEach(tieBreak -> add(texts, tieBreak));
            }
            DecisionTable table = spec.table() == null ? null : model.tables().get(spec.table());
            if (table != null) {
                texts.addAll(table.inputs());
                table.rules().forEach(rule -> {
                    texts.addAll(rule.cells());
                    add(texts, rule.outputExpression());
                });
            }
            return texts;
        }

        private Scope scopeOf(String name) {
            if (scopes.containsKey(name)) {
                return scopes.get(name);
            }
            if (!resolving.add(name)) {
                return null;
            }
            Scope scope = resolveScope(name, model.decisions().get(name));
            resolving.remove(name);
            scopes.put(name, scope);
            return scope;
        }

        private Scope resolveScope(String name, DecisionSpec spec) {
            String scope = spec == null ? null : spec.scope();
            if (isBlank(scope)) {
                return Scope.SCALAR;
            }
            if (model.inputs().containsKey(scope)) {
                return Scope.row(scope);
            }
            return groupScope("decision '%s'".formatted(name), scope, dependencies.getOrDefault(name, Set.of()));
        }

        /** {@code input.attribute}, or an attribute whose input the dependencies — or a single input — tell. */
        private Scope groupScope(String owner, String scope, Set<String> needed) {
            int dot = scope.indexOf('.');
            if (dot > 0) {
                String input = scope.substring(0, dot);
                if (!model.inputs().containsKey(input)) {
                    errors.add("%s groups '%s', but there is no input '%s'".formatted(owner, scope, input));
                    return null;
                }
                return Scope.group(input, scope.substring(dot + 1));
            }
            Optional<String> input = inputOf(needed);
            if (input.isEmpty()) {
                errors.add("%s: cannot tell which input's rows '%s' groups — write 'for: <input>.%s'".formatted(owner,
                        scope, scope));
                return null;
            }
            return Scope.group(input.get(), scope);
        }

        private Optional<String> inputOf(Set<String> needed) {
            if (model.inputs().size() == 1) {
                return Optional.of(model.inputs().keySet().iterator().next());
            }
            Set<String> candidates = new LinkedHashSet<>();
            for (String dependency : needed) {
                Scope scope = scopeOf(dependency);
                if (scope != null && !scope.isScalar()) {
                    candidates.add(scope.input());
                }
            }
            return candidates.size() == 1 ? Optional.of(candidates.iterator().next()) : Optional.empty();
        }

        /** The rows a single-valued aggregate runs over. */
        private Scope sourceOf(String name, AggregateSpec aggregate) {
            String owner = "decision '%s'".formatted(name);
            Set<String> needed = dependencies.getOrDefault(name, Set.of());
            if (!isBlank(aggregate.of())) {
                return model.inputs().containsKey(aggregate.of()) ? Scope.row(aggregate.of())
                        : groupScope(owner, aggregate.of(), needed);
            }
            Set<Scope> groups = new LinkedHashSet<>();
            Set<Scope> rows = new LinkedHashSet<>();
            for (String dependency : needed) {
                Scope scope = scopeOf(dependency);
                if (scope != null && scope.type() == Scope.Type.GROUP) {
                    groups.add(scope);
                } else if (scope != null && scope.type() == Scope.Type.ROW) {
                    rows.add(scope);
                }
            }
            if (groups.size() == 1) {
                return groups.iterator().next();
            }
            if (groups.isEmpty() && rows.size() == 1) {
                return rows.iterator().next();
            }
            if (groups.isEmpty() && rows.isEmpty() && model.inputs().size() == 1) {
                return Scope.row(model.inputs().keySet().iterator().next());
            }
            errors.add("%s: cannot tell which rows it aggregates — name them with 'of: <input>'".formatted(owner));
            return null;
        }

        private void checkDecision(PlannedDecision decision) {
            String name = decision.name();
            String owner = "decision '%s'".formatted(name);
            DecisionSpec spec = decision.spec();
            if (model.parameters().containsKey(name) || model.inputs().containsKey(name) || model.functions().containsKey(name)) {
                errors.add("%s has the name of a parameter, input or function".formatted(owner));
            }
            if (spec == null || spec.kinds().size() != 1) {
                errors.add("%s needs exactly one of 'expr', 'table', 'aggregate' and 'rank'".formatted(owner));
                return;
            }
            Scope scope = decision.scope();
            for (String required : spec.requires()) {
                boolean known = model.decisions().containsKey(required) || model.parameters().containsKey(required)
                        || model.inputs().containsKey(required) || model.functions().containsKey(required);
                // In a row or group scope a requirement may be a column of the rows.
                if (!known && (scope == null || scope.isScalar())) {
                    errors.add("%s requires '%s', which is no decision, parameter, input or function".formatted(owner, required));
                }
            }
            switch (decision.kind()) {
                case EXPR -> syntax(owner, spec.expr());
                case TABLE -> {
                    if (!model.tables().containsKey(spec.table())) {
                        errors.add("%s uses table '%s', which is not declared".formatted(owner, spec.table()));
                    }
                }
                case AGGREGATE -> checkAggregate(owner, spec.aggregate(), scope);
                case RANK -> checkRank(owner, spec.rank(), scope);
            }
        }

        private void checkAggregate(String owner, AggregateSpec aggregate, Scope scope) {
            Map<String, String> functions = aggregate.functions();
            if (functions.size() != 1) {
                errors.add("%s: an aggregate needs exactly one of count, countDistinct, sum, avg, min and max".formatted(owner));
            } else {
                String operand = functions.values().iterator().next();
                if (isBlank(operand)) {
                    errors.add("%s: the aggregate needs a column".formatted(owner));
                } else if (!NAME.matcher(operand).matches()) {
                    syntax(owner, operand);
                }
            }
            if (scope != null && !isBlank(aggregate.partitionBy())) {
                if (scope.isScalar()) {
                    errors.add("%s: 'partitionBy' needs a row scope ('for: <input>') or a group scope".formatted(owner));
                } else if (scope.type() == Scope.Type.GROUP && !aggregate.partitionBy().equals(scope.attribute())) {
                    errors.add("%s: in the group scope of '%s' an aggregate partitions by '%s' only".formatted(owner,
                            scope.attribute(), scope.attribute()));
                }
            }
            if (!isBlank(aggregate.of()) && scope != null && !scope.isScalar()) {
                errors.add("%s: 'of' is for a single-valued aggregate; a row or group scope aggregates its own rows".formatted(owner));
            }
            checkDrop(owner, "dropWorst", aggregate.dropWorst());
            checkDrop(owner, "dropBest", aggregate.dropBest());
            if (aggregate.better() != null && !"lower".equalsIgnoreCase(aggregate.better()) && !aggregate.higherIsBetter()) {
                errors.add("%s: 'better' is 'lower' or 'higher', not '%s'".formatted(owner, aggregate.better()));
            }
        }

        private void checkDrop(String owner, String key, Object drop) {
            if (drop == null || drop instanceof Number) {
                return;
            }
            if (drop instanceof String text) {
                syntax(owner + " " + key, text);
            } else {
                errors.add("%s: '%s' is a number or a FEEL expression".formatted(owner, key));
            }
        }

        private void checkRank(String owner, RankSpec rank, Scope scope) {
            if (scope != null && scope.isScalar()) {
                errors.add("%s: a rank needs a row scope ('for: <input>') or a group scope".formatted(owner));
            }
            if (isBlank(rank.by())) {
                errors.add("%s: a rank needs 'by'".formatted(owner));
            }
            if (rank.order() != null && !"asc".equalsIgnoreCase(rank.order()) && !rank.descending()) {
                errors.add("%s: 'order' is 'asc' or 'desc', not '%s'".formatted(owner, rank.order()));
            }
            if (scope != null && scope.type() == Scope.Type.GROUP && !isBlank(rank.partitionBy())) {
                errors.add("%s: a group rank cannot be partitioned".formatted(owner));
            }
            if (!isBlank(rank.filter())) {
                syntax(owner + " filter", rank.filter());
            }
            for (String tieBreak : rank.tieBreak()) {
                String[] parts = tieBreak == null ? new String[0] : tieBreak.trim().split("\\s+");
                if (parts.length == 0 || parts.length > 2 || (parts.length == 2 && !parts[1].matches("(?i)asc|desc"))) {
                    errors.add("%s: tie-break '%s' is '<column>' or '<column> asc|desc'".formatted(owner, tieBreak));
                }
            }
        }

        /** One attribute is grouped over one input only, since a group's results are keyed by its attribute. */
        private void checkGroupAttributes(List<PlannedDecision> decisions) {
            Map<String, String> inputByAttribute = new HashMap<>();
            for (PlannedDecision decision : decisions) {
                for (Scope scope : new Scope[] {decision.scope(), decision.source()}) {
                    if (scope != null && scope.type() == Scope.Type.GROUP) {
                        String other = inputByAttribute.putIfAbsent(scope.attribute(), scope.input());
                        if (other != null && !other.equals(scope.input())) {
                            errors.add("'%s' is grouped over both '%s' and '%s'".formatted(scope.attribute(), other, scope.input()));
                        }
                    }
                }
            }
        }

        // ---- output and trigger

        private void checkOutput() {
            OutputSpec output = model.output();
            if (output == null) {
                return;
            }
            if (isBlank(output.entity())) {
                errors.add("the output needs an 'entity'");
            }
            if (!OutputSpec.STORED.equals(output.materialization()) && !OutputSpec.ON_READ.equals(output.materialization())) {
                errors.add("the output's materialization is 'stored' or 'onRead', not '%s'".formatted(output.materialization()));
            }
            if (output.mapping().isEmpty()) {
                errors.add("the output needs a 'mapping'");
            }
            if (output.isGrouped()) {
                checkGroupedOutput(output);
            } else {
                checkSingleOutput(output);
            }
        }

        private void checkSingleOutput(OutputSpec output) {
            if (!output.key().isEmpty()) {
                errors.add("the output's 'key' is for a grouped output ('for: <attribute>')");
            }
            String target = targetOf(output);
            if (target == null) {
                errors.add("the output writes one '%s', but no entity parameter of that entity names it — set 'target'"
                        .formatted(output.entity()));
            } else {
                ParameterSpec parameter = model.parameters().get(target);
                if (parameter == null || !parameter.isEntity() || !parameter.entity().equals(output.entity())) {
                    errors.add("the output's target '%s' is no parameter of entity '%s'".formatted(target, output.entity()));
                }
            }
            output.mapping().forEach((attribute, source) -> {
                boolean scalarDecision = model.decisions().containsKey(source) && Scope.SCALAR.equals(scopes.get(source));
                if (!scalarDecision && !model.parameters().containsKey(source)) {
                    errors.add("the output maps '%s' from '%s', which is no single-valued decision or parameter".formatted(
                            attribute, source));
                }
            });
        }

        private void checkGroupedOutput(OutputSpec output) {
            String attribute = output.scope().contains(".") ? output.scope().substring(output.scope().indexOf('.') + 1)
                    : output.scope();
            boolean grouped = scopes.values().stream()
                    .anyMatch(scope -> scope != null && scope.type() == Scope.Type.GROUP && scope.attribute().equals(attribute));
            if (!grouped) {
                errors.add("the output is written per '%s', but no decision is evaluated per '%s'".formatted(attribute, attribute));
            }
            if (output.key().isEmpty()) {
                errors.add("a grouped output needs a 'key' — the attributes that find an existing result object");
            }
            output.key().stream().filter(key -> !output.mapping().containsKey(key))
                    .forEach(key -> errors.add("the output's key '%s' is not mapped".formatted(key)));
            output.mapping().forEach((target, source) -> {
                boolean known = source.equals(attribute) || model.decisions().containsKey(source)
                        || model.parameters().containsKey(source);
                if (!known) {
                    errors.add("the output maps '%s' from '%s', which is no decision, parameter or '%s'".formatted(target,
                            source, attribute));
                } else if (model.decisions().containsKey(source)) {
                    Scope scope = scopes.get(source);
                    boolean fits = scope != null && (scope.isScalar()
                            || (scope.type() == Scope.Type.GROUP && scope.attribute().equals(attribute)));
                    if (!fits) {
                        errors.add("the output maps '%s' from '%s', which is not evaluated per '%s'".formatted(target, source,
                                attribute));
                    }
                }
            });
        }

        private void checkTrigger() {
            TriggerSpec trigger = model.trigger();
            if (trigger == null) {
                return;
            }
            if (trigger.onChange() != null) {
                checkTriggerEntity("onChange", trigger.onChange().entity());
            }
            if (trigger.state() != null) {
                checkState("the 'state' trigger", trigger.state());
            }
            FreezeSpec freeze = trigger.freeze();
            if (freeze != null) {
                if (freeze.unknown() != null) {
                    errors.add("'freeze' is 'onCompletion' or { state: { entity, to } }, not '%s'".formatted(freeze.unknown()));
                } else if (freeze.state() != null) {
                    checkState("the 'freeze' state", freeze.state());
                }
            }
        }

        private void checkState(String owner, StateRef state) {
            checkTriggerEntity(owner, state.entity());
            if (isBlank(state.to())) {
                errors.add("%s needs 'to' — the state entered".formatted(owner));
            }
        }

        private void checkTriggerEntity(String owner, String entity) {
            if (isBlank(entity)) {
                errors.add("%s needs an 'entity'".formatted(owner));
            } else if (model.parametersOfEntity(entity).isEmpty()) {
                errors.add("%s names '%s', but no parameter takes an object of it".formatted(owner, entity));
            }
        }

        // ---- order

        /** Declaration order wherever the graph leaves a choice; what is left over is a cycle. */
        private List<PlannedDecision> order(List<PlannedDecision> decisions) {
            List<PlannedDecision> ordered = new ArrayList<>();
            Set<String> placed = new HashSet<>();
            List<PlannedDecision> remaining = new ArrayList<>(decisions);
            boolean progress = true;
            while (!remaining.isEmpty() && progress) {
                progress = false;
                for (PlannedDecision decision : List.copyOf(remaining)) {
                    if (placed.containsAll(decision.dependsOn())) {
                        ordered.add(decision);
                        placed.add(decision.name());
                        remaining.remove(decision);
                        progress = true;
                    }
                }
            }
            if (!remaining.isEmpty()) {
                errors.add("the decisions %s depend on each other in a cycle".formatted(
                        remaining.stream().map(PlannedDecision::name).toList()));
            }
            return ordered;
        }

        // ---- helpers

        private String targetOf(OutputSpec output) {
            if (!isBlank(output.target())) {
                return output.target();
            }
            List<String> candidates = model.parametersOfEntity(output.entity());
            return candidates.size() == 1 ? candidates.getFirst() : null;
        }

        private void checkName(String what, String name) {
            if (name == null || !NAME.matcher(name).matches()) {
                errors.add("%s name '%s' is not an identifier".formatted(what, name));
            }
        }

        private void syntax(String owner, String expression) {
            if (isBlank(expression)) {
                errors.add("%s: the expression is empty".formatted(owner));
                return;
            }
            feel.checkExpression(expression).ifPresent(error -> errors.add("%s: %s".formatted(owner, error)));
        }
    }

    /** The plain names in a FEEL text, string literals left out. */
    static Set<String> identifiersIn(String text) {
        Set<String> identifiers = new LinkedHashSet<>();
        if (text == null) {
            return identifiers;
        }
        Matcher matcher = IDENTIFIER.matcher(QUOTED.matcher(text).replaceAll("\"\""));
        while (matcher.find()) {
            identifiers.add(matcher.group());
        }
        return identifiers;
    }

    private static void add(List<String> texts, String text) {
        if (text != null) {
            texts.add(text);
        }
    }

    private static void addIfText(List<String> texts, Object value) {
        if (value instanceof String text) {
            texts.add(text);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
