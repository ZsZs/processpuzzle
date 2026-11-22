package com.processpuzzle.core.quantity;

import com.processpuzzle.core.quantity.FundamentalTypeException.Reason;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TIME quantities as the ISO 8601 durations workflow timers use: {@code 1.5 h} is {@code PT1H30M}. Years and months
 * have no fixed length, so no quantity measures them — a duration naming them is refused.
 */
public final class IsoDurations {

    private static final String SECONDS = "s";
    private static final BigDecimal DAY = BigDecimal.valueOf(86_400);
    private static final BigDecimal HOUR = BigDecimal.valueOf(3_600);
    private static final BigDecimal MINUTE = BigDecimal.valueOf(60);
    private static final Map<String, BigDecimal> DATE_SECONDS = Map.of("W", BigDecimal.valueOf(604_800), "D", DAY);
    private static final Map<String, BigDecimal> TIME_SECONDS = Map.of("H", HOUR, "M", MINUTE, "S", BigDecimal.ONE);
    private static final List<String> DATE_ORDER = List.of("Y", "M", "W", "D");
    private static final List<String> TIME_ORDER = List.of("H", "M", "S");
    private static final Pattern TOKEN = Pattern.compile("(\\d+(?:\\.\\d+)?)([A-Z])");

    private IsoDurations() {
    }

    /** Days are written as days, never weeks; zero is {@code PT0S}. */
    public static String toIsoDuration(Quantity quantity, UnitCatalog catalog) {
        BigDecimal rest = quantity.convertTo(SECONDS, catalog).amount();
        if (rest.signum() < 0) {
            throw new FundamentalTypeException(Reason.INVALID_DURATION, "a duration cannot be negative");
        }
        BigDecimal days = rest.divideToIntegralValue(DAY);
        rest = rest.subtract(days.multiply(DAY));
        BigDecimal hours = rest.divideToIntegralValue(HOUR);
        rest = rest.subtract(hours.multiply(HOUR));
        BigDecimal minutes = rest.divideToIntegralValue(MINUTE);
        rest = rest.subtract(minutes.multiply(MINUTE));
        String date = part(days, "D");
        String time = part(hours, "H") + part(minutes, "M") + part(rest, "S");
        if (date.isEmpty() && time.isEmpty()) {
            return "PT0S";
        }
        return "P" + date + (time.isEmpty() ? "" : "T" + time);
    }

    public static Quantity toQuantity(String duration, String unit, UnitCatalog catalog) {
        String text = duration == null ? "" : duration.trim().toUpperCase(Locale.ROOT);
        if (!text.startsWith("P")) {
            throw invalid(duration);
        }
        String[] parts = text.substring(1).split("T", -1);
        if (parts.length > 2 || (parts.length == 2 && parts[1].isEmpty())) {
            throw invalid(duration);
        }
        List<String[]> date = designators(parts[0], DATE_ORDER, duration);
        List<String[]> time = parts.length == 2 ? designators(parts[1], TIME_ORDER, duration) : List.of();
        if (date.isEmpty() && time.isEmpty()) {
            throw invalid(duration);
        }
        if (date.stream().anyMatch(pair -> "Y".equals(pair[1]) || "M".equals(pair[1]))) {
            throw new FundamentalTypeException(Reason.CALENDAR_DURATION, "%s names years or months, which have no fixed length".formatted(duration));
        }
        BigDecimal seconds = sum(date, DATE_SECONDS).add(sum(time, TIME_SECONDS));
        return new Quantity(seconds, SECONDS).convertTo(unit, catalog);
    }

    /** The {@code <number><designator>} pairs of one part, each designator at most once and in ISO order. */
    private static List<String[]> designators(String part, List<String> order, String duration) {
        List<String[]> found = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(part);
        int position = 0;
        int last = -1;
        while (position < part.length()) {
            int rank = matcher.find(position) && matcher.start() == position ? order.indexOf(matcher.group(2)) : -1;
            if (rank <= last) {
                throw invalid(duration);
            }
            found.add(new String[] {matcher.group(1), matcher.group(2)});
            last = rank;
            position = matcher.end();
        }
        return found;
    }

    private static BigDecimal sum(List<String[]> pairs, Map<String, BigDecimal> secondsOf) {
        return pairs.stream().map(pair -> new BigDecimal(pair[0]).multiply(secondsOf.get(pair[1]))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String part(BigDecimal amount, String designator) {
        return amount.signum() > 0 ? Decimals.text(amount) + designator : "";
    }

    private static FundamentalTypeException invalid(String duration) {
        return new FundamentalTypeException(Reason.INVALID_DURATION, "'%s' is not an ISO 8601 duration".formatted(duration));
    }
}
