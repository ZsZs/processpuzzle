package com.processpuzzle.calculation.engine;

import com.processpuzzle.calculation.domain.definition.AggregateSpec;
import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.DecisionKind;
import com.processpuzzle.calculation.domain.definition.DecisionTable;
import com.processpuzzle.calculation.domain.definition.HitPolicy;
import com.processpuzzle.calculation.domain.definition.RankSpec;
import com.processpuzzle.calculation.domain.definition.TableRule;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Evaluates a {@link CalculationPlan}: functions first, then every decision in dependency order, each in its scope.
 *
 * <p>The FEEL context holds the parameters (an entity parameter as its object's payload), the inputs as lists of
 * rows, the functions and every single-valued decision computed so far. A row decision sees the context plus the
 * row's columns, and adds its value to the row as a column of its own; a group decision sees the context plus the
 * group's row — and its input's name bound to the group's members, so {@code sum(finishes.points)} sums the group.
 *
 * <p>Pure: no I/O, no Spring. Inputs are read and outputs written by the run around it.
 */
public class CalculationEngine {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z_]\\w*$");

    private final ExpressionEnginePort feel;

    public CalculationEngine(ExpressionEnginePort feel) {
        this.feel = feel;
    }

    /**
     * @param parameters the parameter values — an entity parameter's as its object's payload
     * @param inputs     every input's rows
     * @throws CalculationException when a decision cannot be evaluated
     */
    public EvaluationResult evaluate(CalculationPlan plan, Map<String, Object> parameters,
                                     Map<String, List<Map<String, Object>>> inputs) {
        return new Evaluation(plan, parameters, inputs).run();
    }

    /** One evaluation's state. */
    private final class Evaluation {

        private final CalculationPlan plan;
        private final CalculationModel model;
        private final Map<String, Object> context = new LinkedHashMap<>();
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        private final Map<String, GroupTable> groups = new LinkedHashMap<>();
        private final List<TraceEntry> trace = new ArrayList<>();

        Evaluation(CalculationPlan plan, Map<String, Object> parameters, Map<String, List<Map<String, Object>>> inputs) {
            this.plan = plan;
            this.model = plan.model();
            context.putAll(parameters);
            model.parameters().forEach((name, parameter) -> {
                if (context.get(name) == null && parameter != null && parameter.defaultValue() != null) {
                    context.put(name, parameter.defaultValue());
                }
            });
            model.inputs().keySet().forEach(name -> {
                List<Map<String, Object>> copy = new ArrayList<>();
                inputs.getOrDefault(name, List.of()).forEach(row -> copy.add(new LinkedHashMap<>(row)));
                rows.put(name, copy);
                context.put(name, copy);
            });
        }

        EvaluationResult run() {
            model.functions().forEach((name, body) -> context.put(name, evaluate("function '%s'".formatted(name), body, context)));
            for (PlannedDecision decision : plan.order()) {
                try {
                    Object value = switch (decision.scope().type()) {
                        case SCALAR -> scalar(decision);
                        case ROW -> row(decision);
                        case GROUP -> group(decision);
                    };
                    trace.add(new TraceEntry(decision.name(), decision.kind(), decision.scope().toString(), value, null));
                } catch (RuntimeException e) {
                    String message = "decision '%s': %s".formatted(decision.name(), e.getMessage());
                    trace.add(new TraceEntry(decision.name(), decision.kind(), decision.scope().toString(), null, e.getMessage()));
                    throw new CalculationException(message, trace);
                }
            }
            Map<String, List<Map<String, Object>>> groupRows = new LinkedHashMap<>();
            groups.values().forEach(group -> groupRows.put(group.attribute(), group.rows()));
            return new EvaluationResult(values, rows, groupRows, plan.decisionNames(), trace);
        }

        // ---- scopes

        private Object scalar(PlannedDecision decision) {
            Object value = switch (decision.kind()) {
                case EXPR -> evaluate(decision.name(), decision.spec().expr(), context);
                case TABLE -> decide(decision.spec().table(), context);
                case AGGREGATE -> aggregate(decision.spec().aggregate(), sourceRows(decision.source()), context);
                case RANK -> throw new IllegalStateException("a rank needs a row or group scope");
            };
            values.put(decision.name(), value);
            context.put(decision.name(), value);
            return value;
        }

        private List<Object> row(PlannedDecision decision) {
            List<Map<String, Object>> table = rows.get(decision.scope().input());
            switch (decision.kind()) {
                case EXPR -> table.forEach(row -> row.put(decision.name(), evaluate(decision.name(), decision.spec().expr(), with(row))));
                case TABLE -> table.forEach(row -> row.put(decision.name(), decide(decision.spec().table(), with(row))));
                case AGGREGATE -> {
                    AggregateSpec aggregate = decision.spec().aggregate();
                    for (List<Map<String, Object>> partition : partitions(table, aggregate.partitionBy())) {
                        Object value = aggregate(aggregate, partition, with(partition.getFirst()));
                        partition.forEach(row -> row.put(decision.name(), value));
                    }
                }
                case RANK -> {
                    RankSpec rank = decision.spec().rank();
                    partitions(table, rank.partitionBy()).forEach(partition -> rank(decision.name(), rank, partition, this::with));
                }
            }
            return table.stream().map(row -> row.get(decision.name())).toList();
        }

        private List<Map<String, Object>> group(PlannedDecision decision) {
            GroupTable group = groupTable(decision.scope());
            List<Map<String, Object>> groupRows = group.rows();
            for (int index = 0; index < groupRows.size(); index++) {
                Map<String, Object> groupRow = groupRows.get(index);
                List<Map<String, Object>> members = group.members().get(index);
                switch (decision.kind()) {
                    case EXPR -> groupRow.put(decision.name(), evaluate(decision.name(), decision.spec().expr(), within(group, groupRow, members)));
                    case TABLE -> groupRow.put(decision.name(), decide(decision.spec().table(), within(group, groupRow, members)));
                    case AGGREGATE -> groupRow.put(decision.name(), aggregate(decision.spec().aggregate(), members,
                            within(group, groupRow, members)));
                    case RANK -> {
                        // Ranks the groups, not a group's members: done once, below.
                    }
                }
            }
            if (decision.kind() == DecisionKind.RANK) {
                // By identity: ranking adds a column to the rows, which would move their hash.
                Map<Map<String, Object>, List<Map<String, Object>>> membersOf = new IdentityHashMap<>();
                for (int index = 0; index < groupRows.size(); index++) {
                    membersOf.put(groupRows.get(index), group.members().get(index));
                }
                rank(decision.name(), decision.spec().rank(), groupRows,
                        groupRow -> within(group, groupRow, membersOf.getOrDefault(groupRow, List.of())));
            }
            List<Map<String, Object>> values = new ArrayList<>();
            groupRows.forEach(groupRow -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put(group.attribute(), groupRow.get(group.attribute()));
                entry.put("value", groupRow.get(decision.name()));
                values.add(entry);
            });
            return values;
        }

        private List<Map<String, Object>> sourceRows(Scope source) {
            return source.type() == Scope.Type.GROUP ? groupTable(source).rows() : rows.get(source.input());
        }

        /** The groups of {@code scope}: one row per distinct value of its attribute, in order of first appearance. */
        private GroupTable groupTable(Scope scope) {
            return groups.computeIfAbsent(scope.attribute(), attribute -> {
                Map<Object, Integer> indexOf = new LinkedHashMap<>();
                List<Map<String, Object>> groupRows = new ArrayList<>();
                List<List<Map<String, Object>>> members = new ArrayList<>();
                for (Map<String, Object> row : rows.get(scope.input())) {
                    Object value = row.get(attribute);
                    Integer index = indexOf.computeIfAbsent(Values.key(value), key -> {
                        Map<String, Object> groupRow = new LinkedHashMap<>();
                        groupRow.put(attribute, value);
                        groupRows.add(groupRow);
                        members.add(new ArrayList<>());
                        return groupRows.size() - 1;
                    });
                    members.get(index).add(row);
                }
                return new GroupTable(scope.input(), attribute, groupRows, members);
            });
        }

        private Map<String, Object> with(Map<String, Object> row) {
            Map<String, Object> variables = new HashMap<>(context);
            variables.putAll(row);
            return variables;
        }

        private Map<String, Object> within(GroupTable group, Map<String, Object> groupRow, List<Map<String, Object>> members) {
            Map<String, Object> variables = with(groupRow);
            variables.put(group.input(), members);
            return variables;
        }

        // ---- logic

        private Object decide(String tableName, Map<String, Object> variables) {
            DecisionTable table = model.tables().get(tableName);
            List<Object> inputs = new ArrayList<>();
            for (String input : table.inputs()) {
                inputs.add(evaluate("table '%s' input".formatted(tableName), input, variables));
            }
            List<Integer> matched = new ArrayList<>();
            List<Object> outputs = new ArrayList<>();
            for (int index = 0; index < table.rules().size(); index++) {
                TableRule rule = table.rules().get(index);
                if (matches(tableName, rule, inputs, variables)) {
                    matched.add(index + 1);
                    outputs.add(evaluate("table '%s' rule %d".formatted(tableName, index + 1), rule.outputExpression(), variables));
                    if (table.hitPolicy() == HitPolicy.FIRST) {
                        break;
                    }
                }
            }
            return switch (table.hitPolicy()) {
                case FIRST -> outputs.isEmpty() ? null : outputs.getFirst();
                case COLLECT -> outputs;
                case UNIQUE -> {
                    if (outputs.size() > 1) {
                        throw new IllegalStateException("table '%s' is UNIQUE, but rules %s all match".formatted(tableName, matched));
                    }
                    yield outputs.isEmpty() ? null : outputs.getFirst();
                }
                case ANY -> {
                    Set<Object> distinct = new HashSet<>();
                    outputs.forEach(output -> distinct.add(Values.key(output)));
                    if (distinct.size() > 1) {
                        throw new IllegalStateException("table '%s' is ANY, but matching rules %s disagree".formatted(tableName, matched));
                    }
                    yield outputs.isEmpty() ? null : outputs.getFirst();
                }
            };
        }

        private boolean matches(String tableName, TableRule rule, List<Object> inputs, Map<String, Object> variables) {
            List<String> cells = rule.cells();
            for (int index = 0; index < cells.size(); index++) {
                String cell = cells.get(index);
                if (!TableRule.ANY.equals(cell) && !feel.test(cell, inputs.get(index), variables)) {
                    return false;
                }
            }
            return true;
        }

        private Object aggregate(AggregateSpec aggregate, List<Map<String, Object>> source, Map<String, Object> variables) {
            Map.Entry<String, String> function = aggregate.functions().entrySet().iterator().next();
            List<Object> operands = new ArrayList<>();
            for (Map<String, Object> row : source) {
                Object value = NAME.matcher(function.getValue()).matches() && row.containsKey(function.getValue())
                        ? row.get(function.getValue())
                        : evaluate("aggregate", function.getValue(), merge(variables, row));
                if (value != null) {
                    operands.add(value);
                }
            }
            operands = dropped(aggregate, operands, variables);
            return switch (function.getKey()) {
                case "count" -> BigDecimal.valueOf(operands.size());
                case "countDistinct" -> BigDecimal.valueOf(operands.stream().map(Values::key).distinct().count());
                case "sum" -> sum(operands);
                case "avg" -> average(operands);
                case "min" -> operands.stream().min(Values.ORDER).orElse(null);
                case "max" -> operands.stream().max(Values.ORDER).orElse(null);
                default -> throw new IllegalStateException("unknown aggregate '%s'".formatted(function.getKey()));
            };
        }

        /** The operands without the worst and best ones the aggregate drops — the series' discards. */
        private List<Object> dropped(AggregateSpec aggregate, List<Object> operands, Map<String, Object> variables) {
            int worst = count("dropWorst", aggregate.dropWorst(), variables);
            int best = count("dropBest", aggregate.dropBest(), variables);
            if (worst == 0 && best == 0) {
                return operands;
            }
            // Best first.
            Comparator<Object> goodness = aggregate.higherIsBetter() ? Values.ORDER.reversed() : Values.ORDER;
            List<Object> sorted = new ArrayList<>(operands);
            sorted.sort(goodness);
            int from = Math.min(best, sorted.size());
            int to = Math.max(from, sorted.size() - worst);
            return sorted.subList(from, to);
        }

        private int count(String what, Object drop, Map<String, Object> variables) {
            Object value = drop instanceof String expression ? evaluate(what, expression, variables) : drop;
            BigDecimal number = Values.number(value);
            if (drop != null && number == null) {
                throw new IllegalStateException("'%s' is %s, not a number".formatted(what, value));
            }
            return number == null ? 0 : Math.max(0, number.intValue());
        }

        /**
         * Competition ranking of {@code table}'s rows: equal on {@code by} and every tie-break, equal rank; the next
         * rank skips as many. A row the filter refuses, or without a {@code by} value, gets none.
         */
        private void rank(String name, RankSpec rank, List<Map<String, Object>> table,
                          java.util.function.Function<Map<String, Object>, Map<String, Object>> variablesOf) {
            List<Map<String, Object>> ranked = new ArrayList<>();
            for (Map<String, Object> row : table) {
                boolean eligible = row.get(rank.by()) != null && (rank.filter() == null
                        || Boolean.TRUE.equals(evaluate("filter", rank.filter(), variablesOf.apply(row))));
                if (eligible) {
                    ranked.add(row);
                } else {
                    row.put(name, null);
                }
            }
            Comparator<Map<String, Object>> order = comparator(rank.by(), rank.descending());
            for (String tieBreak : rank.tieBreak()) {
                String[] parts = tieBreak.trim().split("\\s+");
                boolean descending = parts.length == 2 ? "desc".equalsIgnoreCase(parts[1]) : rank.descending();
                order = order.thenComparing(comparator(parts[0], descending));
            }
            ranked.sort(order);
            for (int index = 0; index < ranked.size(); index++) {
                Map<String, Object> row = ranked.get(index);
                boolean tied = index > 0 && order.compare(ranked.get(index - 1), row) == 0;
                row.put(name, tied ? ranked.get(index - 1).get(name) : BigDecimal.valueOf(index + 1L));
            }
        }

        private Comparator<Map<String, Object>> comparator(String column, boolean descending) {
            Comparator<Map<String, Object>> comparator = (left, right) -> Values.compare(left.get(column), right.get(column));
            return descending ? comparator.reversed() : comparator;
        }

        private Object evaluate(String owner, String expression, Map<String, Object> variables) {
            try {
                return feel.evaluate(expression, variables);
            } catch (ExpressionEnginePort.ExpressionException e) {
                // A decision's own expression needs no owner: the failure is reported as the decision's.
                throw new IllegalStateException(model.decisions().containsKey(owner) ? e.getMessage() : owner + " " + e.getMessage(), e);
            }
        }
    }

    private static Map<String, Object> merge(Map<String, Object> variables, Map<String, Object> row) {
        Map<String, Object> merged = new HashMap<>(variables);
        merged.putAll(row);
        return merged;
    }

    private static Object sum(List<Object> operands) {
        if (!operands.isEmpty() && operands.stream().allMatch(Duration.class::isInstance)) {
            return operands.stream().map(Duration.class::cast).reduce(Duration.ZERO, Duration::plus);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Object operand : operands) {
            sum = sum.add(numberOf(operand));
        }
        return sum;
    }

    private static Object average(List<Object> operands) {
        if (operands.isEmpty()) {
            return null;
        }
        if (operands.stream().allMatch(Duration.class::isInstance)) {
            return ((Duration) sum(operands)).dividedBy(operands.size());
        }
        return ((BigDecimal) sum(operands)).divide(BigDecimal.valueOf(operands.size()), MathContext.DECIMAL128);
    }

    private static BigDecimal numberOf(Object operand) {
        BigDecimal number = Values.number(operand);
        if (number == null) {
            throw new IllegalStateException("'%s' is no number".formatted(operand));
        }
        return number;
    }

    /** Partitions in order of first appearance; one partition of every row without a column. */
    private static List<List<Map<String, Object>>> partitions(List<Map<String, Object>> table, String column) {
        if (column == null || column.isBlank()) {
            return table.isEmpty() ? List.of() : List.of(table);
        }
        Map<Object, List<Map<String, Object>>> partitions = new LinkedHashMap<>();
        table.forEach(row -> partitions.computeIfAbsent(Values.key(row.get(column)), key -> new ArrayList<>()).add(row));
        return new ArrayList<>(partitions.values());
    }

    /** One grouping: per group its row — the attribute's value and the group decisions — and its member rows. */
    private record GroupTable(String input, String attribute, List<Map<String, Object>> rows,
                              List<List<Map<String, Object>>> members) {
    }
}
