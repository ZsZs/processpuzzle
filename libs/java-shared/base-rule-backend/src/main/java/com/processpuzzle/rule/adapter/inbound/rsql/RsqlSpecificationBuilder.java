package com.processpuzzle.rule.adapter.inbound.rsql;

import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.RSQLParserException;
import cz.jirutka.rsql.parser.ast.AndNode;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;
import cz.jirutka.rsql.parser.ast.RSQLOperators;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Translates an RSQL query string into a JPA {@link Specification}. Selectors are JPA
 * property paths (dot-separated for nested paths). Arguments are coerced to the path's
 * Java type; unparseable values raise {@link IllegalArgumentException}, which the endpoint
 * layer surfaces as HTTP 400. The literal string {@code null} is treated as
 * {@code IS NULL} / {@code IS NOT NULL}.
 */
public class RsqlSpecificationBuilder<T> {

    private static final ComparisonOperator LIKE = new ComparisonOperator("=like=");

    private final RSQLParser parser;

    public RsqlSpecificationBuilder() {
        Set<ComparisonOperator> operators = RSQLOperators.defaultOperators();
        operators.add(LIKE);
        this.parser = new RSQLParser(operators);
    }

    public Specification<T> build(String rsql) {
        if (rsql == null || rsql.isBlank()) {
            return null;
        }
        Node ast;
        try {
            ast = parser.parse(rsql);
        } catch (RSQLParserException e) {
            throw new IllegalArgumentException("Invalid RSQL: " + e.getMessage(), e);
        }
        return (root, query, cb) -> visit(ast, root, cb);
    }

    private Predicate visit(Node node, Root<T> root, CriteriaBuilder cb) {
        if (node instanceof AndNode and) {
            return cb.and(and.getChildren().stream()
                    .map(child -> visit(child, root, cb))
                    .toArray(Predicate[]::new));
        }
        if (node instanceof OrNode or) {
            return cb.or(or.getChildren().stream()
                    .map(child -> visit(child, root, cb))
                    .toArray(Predicate[]::new));
        }
        if (node instanceof ComparisonNode comparison) {
            return comparison(comparison, root, cb);
        }
        throw new IllegalArgumentException("Unsupported RSQL node: " + node.getClass().getSimpleName());
    }

    private Predicate comparison(ComparisonNode node, Root<T> root, CriteriaBuilder cb) {
        Path<?> path = resolvePath(root, node.getSelector());
        List<String> args = node.getArguments();
        String op = node.getOperator().getSymbol();

        if (args.size() == 1 && "null".equalsIgnoreCase(args.get(0))) {
            return isEquals(op) ? cb.isNull(path) : cb.isNotNull(path);
        }

        Class<?> type = path.getJavaType();

        return switch (op) {
            case "==", "=eq=" -> cb.equal(path, coerce(args.get(0), type));
            case "!=", "=ne=" -> cb.notEqual(path, coerce(args.get(0), type));
            case "=gt=", ">" -> cb.greaterThan(asComparable(path), asComparableValue(args.get(0), type));
            case "=ge=", ">=" -> cb.greaterThanOrEqualTo(asComparable(path), asComparableValue(args.get(0), type));
            case "=lt=", "<" -> cb.lessThan(asComparable(path), asComparableValue(args.get(0), type));
            case "=le=", "<=" -> cb.lessThanOrEqualTo(asComparable(path), asComparableValue(args.get(0), type));
            case "=in=" -> path.in(coerceAll(args, type));
            case "=out=" -> cb.not(path.in(coerceAll(args, type)));
            case "=like=" -> cb.like(path.as(String.class), wildcard(args.get(0)));
            default -> throw new IllegalArgumentException("Unsupported RSQL operator: " + op);
        };
    }

    private static boolean isEquals(String op) {
        return "==".equals(op) || "=eq=".equals(op);
    }

    private static String wildcard(String value) {
        return value.contains("%") ? value : "%" + value + "%";
    }

    private static Path<?> resolvePath(Root<?> root, String selector) {
        Path<?> path = root;
        for (String part : selector.split("\\.")) {
            path = path.get(part);
        }
        return path;
    }

    private static List<Object> coerceAll(List<String> args, Class<?> type) {
        List<Object> out = new ArrayList<>(args.size());
        for (String arg : args) {
            out.add(coerce(arg, type));
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Expression<Comparable> asComparable(Path<?> path) {
        return (Expression<Comparable>) (Expression<?>) path;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Comparable asComparableValue(String value, Class<?> type) {
        Object coerced = coerce(value, type);
        if (!(coerced instanceof Comparable)) {
            throw new IllegalArgumentException("Value is not comparable: " + value);
        }
        return (Comparable) coerced;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object coerce(String value, Class<?> type) {
        try {
            if (type == String.class) return value;
            if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(value);
            if (type == Integer.class || type == int.class) return Integer.parseInt(value);
            if (type == Long.class || type == long.class) return Long.parseLong(value);
            if (type == Double.class || type == double.class) return Double.parseDouble(value);
            if (type == Float.class || type == float.class) return Float.parseFloat(value);
            if (type == BigDecimal.class) return new BigDecimal(value);
            if (type == Instant.class) return Instant.parse(value);
            if (type == OffsetDateTime.class) return OffsetDateTime.parse(value);
            if (type == LocalDate.class) return LocalDate.parse(value);
            if (type == LocalDateTime.class) return LocalDateTime.parse(value);
            if (Enum.class.isAssignableFrom(type)) return Enum.valueOf((Class<Enum>) type, value);
            return value;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Cannot coerce '" + value + "' to " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
