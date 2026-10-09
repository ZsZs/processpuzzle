package com.processpuzzle.workflow.definition.domain;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;

/**
 * Reads and evaluates {@link TimerDefinition} expressions — the one place that knows the literal forms,
 * shared by {@link WorkflowValidator} at save time and the engine at run time.
 *
 * <ul>
 *   <li>DURATION — an ISO 8601 duration. {@code java.time} splits the form in two — {@link Period} takes
 *       years, months, weeks and days, {@link Duration} hours, minutes and seconds — so the expression is
 *       split at {@code T} and both halves are applied, the calendar half in UTC. That is what makes
 *       {@code P1M} and {@code P1DT2H} legal.</li>
 *   <li>DATE — an instant ({@code 2026-10-10T08:00:00Z}), an offset or zoned date-time, a local date-time
 *       or a date; the last two are read in UTC, a date as its midnight.</li>
 *   <li>CYCLE — {@code R<n>/<duration>}, {@code R/<duration>} (unbounded), or
 *       {@code R<n>/<date>/<duration>} anchored at a moment. Without an anchor the first firing is one
 *       interval after the timer is reached. Firings of an anchored cycle that are already past are
 *       skipped, not caught up.</li>
 * </ul>
 *
 * <p>An expression starting with {@code $.} is a path into the instance context instead. It is resolved
 * through the function the caller passes — the engine passes {@code PayloadPath} over the context —
 * when the timer is reached, and the value found there is read as the literal would be. At save time
 * only its form can be checked.
 */
public final class TimerExpressions {

    private TimerExpressions() {
    }

    /** An ISO 8601 duration as its calendar and its clock part. */
    public record Amount(Period period, Duration duration) {

        public Instant addTo(Instant instant) {
            return instant.atOffset(ZoneOffset.UTC).plus(period).toInstant().plus(duration);
        }

        boolean isZero() {
            return period.isZero() && duration.isZero();
        }
    }

    /** A parsed CYCLE: {@code repetitions} is null for an unbounded one, {@code anchor} null when absent. */
    public record Cycle(Integer repetitions, Instant anchor, Amount interval) {
    }

    /**
     * A timer made ready to fire.
     *
     * @param dueAt            when it fires next
     * @param remainingFirings how many firings follow that one: 0 for a DURATION or a DATE, null for an
     *                         unbounded CYCLE
     */
    public record Arming(Instant dueAt, Integer remainingFirings) {
    }

    public static boolean isPath(String expression) {
        return expression != null && expression.startsWith("$.");
    }

    /**
     * Checks a timer as far as it can be checked before it runs: a type and an expression, and — for a
     * literal — that it parses.
     *
     * @throws IllegalArgumentException naming what is wrong
     */
    public static void validate(TimerDefinition timer) {
        if (timer.getType() == null) {
            throw new IllegalArgumentException("the timer has no type");
        }
        String expression = timer.getExpression();
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("the timer has no expression");
        }
        if (isPath(expression)) {
            if (expression.length() == 2 || expression.contains("..") || expression.endsWith(".")) {
                throw new IllegalArgumentException("'%s' is not a context path".formatted(expression));
            }
            return;
        }
        parse(timer.getType(), expression);
    }

    /**
     * Arms {@code timer} at {@code now}.
     *
     * @param paths resolves a {@code $.} path to its value
     * @return empty when a path resolves to nothing, or an anchored cycle has no firing left
     * @throws IllegalArgumentException when the expression, or the value a path resolved to, does not parse
     */
    public static Optional<Arming> arm(TimerDefinition timer, Function<String, Object> paths, Instant now) {
        return literalOf(timer, paths).flatMap(literal -> switch (timer.getType()) {
            case DURATION -> Optional.of(new Arming(parseAmount(literal).addTo(now), 0));
            case DATE -> Optional.of(new Arming(parseDate(literal), 0));
            case CYCLE -> firstOf(parseCycle(literal), now);
        });
    }

    /**
     * The firing after one at {@code lastDue}: one interval later, while a CYCLE has firings left.
     *
     * @return empty for a DURATION or DATE, an exhausted cycle, or a path that no longer resolves
     */
    public static Optional<Arming> next(TimerDefinition timer, Function<String, Object> paths, Instant lastDue,
                                        Integer remainingFirings) {
        if (timer.getType() != TimerType.CYCLE || (remainingFirings != null && remainingFirings <= 0)) {
            return Optional.empty();
        }
        return literalOf(timer, paths).map(literal -> new Arming(
                parseCycle(literal).interval().addTo(lastDue), remainingFirings == null ? null : remainingFirings - 1));
    }

    // ---------------------------------------------------------------- parsing

    public static Amount parseAmount(String expression) {
        String text = expression.trim().toUpperCase();
        if (!text.startsWith("P") || text.length() < 2) {
            throw new IllegalArgumentException("'%s' is not an ISO 8601 duration".formatted(expression));
        }
        int t = text.indexOf('T');
        String datePart = t < 0 ? text : text.substring(0, t);
        String timePart = t < 0 ? null : "PT" + text.substring(t + 1);
        try {
            Period period = datePart.length() > 1 ? Period.parse(datePart) : Period.ZERO;
            Duration duration = timePart == null ? Duration.ZERO : Duration.parse(timePart);
            if (period.isNegative() || duration.isNegative()) {
                throw new IllegalArgumentException("'%s' is a negative duration".formatted(expression));
            }
            return new Amount(period, duration);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("'%s' is not an ISO 8601 duration".formatted(expression), e);
        }
    }

    public static Instant parseDate(String expression) {
        String text = expression.trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeException ignored) {
            // try the next form
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeException ignored) {
            // try the next form
        }
        try {
            return ZonedDateTime.parse(text).toInstant();
        } catch (DateTimeException ignored) {
            // try the next form
        }
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        } catch (DateTimeException ignored) {
            // try the next form
        }
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("'%s' is not an ISO 8601 date or date-time".formatted(expression), e);
        }
    }

    public static Cycle parseCycle(String expression) {
        String[] parts = expression.trim().split("/", -1);
        if (parts.length < 2 || parts.length > 3 || !parts[0].toUpperCase().startsWith("R")) {
            throw new IllegalArgumentException(
                    "'%s' is not a cycle — expected R<n>/<duration> or R<n>/<date>/<duration>".formatted(expression));
        }
        Integer repetitions = null;
        String count = parts[0].substring(1);
        if (!count.isEmpty()) {
            try {
                repetitions = Integer.valueOf(count);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'%s' has no valid repetition count".formatted(expression), e);
            }
            if (repetitions < 1) {
                throw new IllegalArgumentException("'%s' repeats fewer than once".formatted(expression));
            }
        }
        Instant anchor = parts.length == 3 ? parseDate(parts[1]) : null;
        Amount interval = parseAmount(parts[parts.length - 1]);
        if (interval.isZero()) {
            throw new IllegalArgumentException("'%s' has a zero interval".formatted(expression));
        }
        return new Cycle(repetitions, anchor, interval);
    }

    private static void parse(TimerType type, String literal) {
        switch (type) {
            case DURATION -> parseAmount(literal);
            case DATE -> parseDate(literal);
            case CYCLE -> parseCycle(literal);
        }
    }

    // ---------------------------------------------------------------- evaluation

    private static Optional<String> literalOf(TimerDefinition timer, Function<String, Object> paths) {
        String expression = timer.getExpression();
        if (!isPath(expression)) {
            return Optional.ofNullable(expression);
        }
        Object value = paths.apply(expression);
        return value == null || value.toString().isBlank() ? Optional.empty() : Optional.of(value.toString());
    }

    private static Optional<Arming> firstOf(Cycle cycle, Instant now) {
        Integer remaining = cycle.repetitions() == null ? null : cycle.repetitions() - 1;
        if (cycle.anchor() == null) {
            return Optional.of(new Arming(cycle.interval().addTo(now), remaining));
        }
        Instant due = cycle.anchor();
        Amount interval = cycle.interval();
        if (remaining == null && interval.period().isZero() && due.isBefore(now)) {
            // Unbounded, fixed length: jump over the missed firings rather than step through them.
            long missed = Duration.between(due, now).toNanos() / interval.duration().toNanos();
            due = due.plus(interval.duration().multipliedBy(missed));
        }
        while (due.isBefore(now)) {
            if (remaining != null && remaining <= 0) {
                return Optional.empty();
            }
            due = interval.addTo(due);
            remaining = remaining == null ? null : remaining - 1;
        }
        return Optional.of(new Arming(due, remaining));
    }
}
