package com.processpuzzle.baseentity.instances.adapters.outbound.rsql;

import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Turns a resolved attribute path + RSQL comparison into a Postgres SQL/JSON path expression
 * (for jsonb_path_exists) plus its bound variables.
 * <p>
 * Values from the RSQL query are never concatenated into the jsonpath text — they're bound
 * through jsonb_build_object as the {@code vars} argument to jsonb_path_exists, referenced from
 * the jsonpath only as $v0, $v1, ... . Attribute codes DO get concatenated into the jsonpath text
 * (there's no bind-variable form for a path step in SQL/JSON path), so they're checked against a
 * conservative identifier pattern first — cheap insurance since they only ever come from
 * admin-authored attribute metadata, never directly from a caller's RSQL string.
 * <p>
 * DATE / DATE_TIME values are stored as ISO-8601 strings (JSON has no native temporal type), so
 * both sides of the comparison go through jsonpath's {@code .datetime()} item method. NUMBER and
 * BOOLEAN are stored as native JSON types, so jsonpath compares them natively — no cast functions
 * needed for those.
 */
final class JsonPathExpressionBuilder {

    private static final Pattern SAFE_ATTRIBUTE_CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    static String buildPath(ResolvedAttributePath path, ComparisonOperator operator, List<String> arguments) {
        StringBuilder jsonPath = new StringBuilder("$");
        for (PathSegment segment : path.segments()) {
            if (!SAFE_ATTRIBUTE_CODE.matcher(segment.attributeCode()).matches()) {
                throw new IllegalStateException(
                    "Attribute code '%s' failed the jsonpath-safety check".formatted(segment.attributeCode()));
            }
            jsonPath.append('.').append(segment.attributeCode());
            if (segment.array()) {
                jsonPath.append("[*]");
            }
        }

        String leaf = isTemporal(path.valueKind()) ? "@.datetime()" : "@";
        jsonPath.append(" ? (").append(filterExpression(leaf, operator, arguments.size(), path.valueKind())).append(')');
        return jsonPath.toString();
    }

    private static String filterExpression(String leaf, ComparisonOperator operator, int argCount, ValueKindView valueKind) {
        String symbol = operator.getSymbol();
        String pgOperator = switch (symbol) {
            case "==" -> "==";
            case "!=" -> "!=";
            case "=gt=" -> ">";
            case "=ge=" -> ">=";
            case "=lt=" -> "<";
            case "=le=" -> "<=";
            case "=in=", "=out=" -> null;
            default -> throw new IllegalArgumentException("Unsupported RSQL operator: " + symbol);
        };

        if (pgOperator != null) {
            String value = isTemporal(valueKind) ? "$v0.datetime()" : "$v0";
            return leaf + " " + pgOperator + " " + value;
        }

        String equalities = IntStream.range(0, argCount)
            .mapToObj(i -> leaf + " == " + (isTemporal(valueKind) ? "$v" + i + ".datetime()" : "$v" + i))
            .collect(Collectors.joining(" || "));
        return "=out=".equals(symbol) ? "!(" + equalities + ")" : equalities;
    }

    static Expression<Object> buildVars(CriteriaBuilder cb, ValueKindView valueKind, List<String> arguments) {
        List<Expression<?>> keyValuePairs = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            keyValuePairs.add(cb.literal("v" + i));
            keyValuePairs.add(typedLiteral(cb, valueKind, arguments.get(i)));
        }
        return cb.function("jsonb_build_object", Object.class, keyValuePairs.toArray(new Expression[0]));
    }

    private static Expression<?> typedLiteral(CriteriaBuilder cb, ValueKindView valueKind, String raw) {
        return switch (valueKind) {
            case NUMBER -> cb.literal(new BigDecimal(raw));
            case BOOLEAN -> cb.literal(Boolean.parseBoolean(raw));
            case DATE, DATE_TIME, TEXT, ENUM, REFERENCE -> cb.literal(raw);
        };
    }

    private static boolean isTemporal(ValueKindView valueKind) {
        return valueKind == ValueKindView.DATE || valueKind == ValueKindView.DATE_TIME;
    }

    private JsonPathExpressionBuilder() {
    }
}
