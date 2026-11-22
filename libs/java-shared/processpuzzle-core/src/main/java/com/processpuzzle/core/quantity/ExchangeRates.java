package com.processpuzzle.core.quantity;

import com.processpuzzle.core.quantity.FundamentalTypeException.Reason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The exchange rates of one organization, immutable. {@link #rate} answers what one currency is worth in another on a date:
 *
 * <ol>
 *   <li>the <b>direct</b> rate of the pair — the one with the latest {@code validFrom} on or before the date, so a rate holds
 *       until the next one starts;</li>
 *   <li>else the <b>inverse</b> of the opposite pair's, to {@link Decimals#DIVISION_PLACES} places;</li>
 *   <li>else a <b>cross</b> rate through one other currency, each leg direct or inverse — the alphabetically first
 *       intermediate currency that has both, so the answer never depends on the order rates were entered in.</li>
 * </ol>
 *
 * Nothing else: a pair that none of these reaches is a NO_EXCHANGE_RATE, never a guess. Without rates ({@link #none()}) every
 * conversion between two currencies is refused, which is how money behaved before rates existed.
 */
public final class ExchangeRates {

    private static final ExchangeRates NONE = new ExchangeRates(Map.of());

    private record Pair(String from, String to) {
    }

    private final Map<Pair, NavigableMap<LocalDate, BigDecimal>> rates;

    private ExchangeRates(Map<Pair, NavigableMap<LocalDate, BigDecimal>> rates) {
        this.rates = rates;
    }

    public static ExchangeRates none() {
        return NONE;
    }

    /** The rates as a table; a pair given twice for the same date is an INVALID_RATE. */
    public static ExchangeRates of(List<ExchangeRate> list) {
        if (list.isEmpty()) {
            return NONE;
        }
        Map<Pair, NavigableMap<LocalDate, BigDecimal>> table = new HashMap<>();
        for (ExchangeRate entry : list) {
            NavigableMap<LocalDate, BigDecimal> history = table.computeIfAbsent(new Pair(entry.from(), entry.to()), key -> new TreeMap<>());
            if (history.put(entry.validFrom(), entry.rate()) != null) {
                throw new FundamentalTypeException(Reason.INVALID_RATE, "%s to %s has two rates valid from %s".formatted(entry.from(), entry.to(), entry.validFrom()));
            }
        }
        return new ExchangeRates(table);
    }

    /** What one {@code from} is worth in {@code to} on {@code on}; one for the same currency. */
    public BigDecimal rate(String from, String to, LocalDate on) {
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }
        return leg(from, to, on).or(() -> cross(from, to, on)).orElseThrow(() -> new FundamentalTypeException(Reason.NO_EXCHANGE_RATE,
            "there is no exchange rate from %s to %s on %s".formatted(from, to, on)));
    }

    /** The money in another currency at the rate of {@code on}, rounded half up to the currency's minor units. */
    public Money convert(Money money, String to, LocalDate on, UnitCatalog catalog) {
        catalog.currency(money.currency());
        int minorUnits = catalog.currency(to).minorUnits();
        if (money.currency().equals(to)) {
            return money;
        }
        BigDecimal converted = money.amount().multiply(rate(money.currency(), to, on)).setScale(minorUnits, RoundingMode.HALF_UP);
        return new Money(converted, to);
    }

    private Optional<BigDecimal> leg(String from, String to, LocalDate on) {
        Optional<BigDecimal> direct = valid(from, to, on);
        if (direct.isPresent()) {
            return direct;
        }
        return valid(to, from, on).map(opposite -> Decimals.divide(BigDecimal.ONE, opposite));
    }

    private Optional<BigDecimal> valid(String from, String to, LocalDate on) {
        NavigableMap<LocalDate, BigDecimal> history = rates.get(new Pair(from, to));
        return history == null ? Optional.empty() : Optional.ofNullable(history.floorEntry(on)).map(Map.Entry::getValue);
    }

    private Optional<BigDecimal> cross(String from, String to, LocalDate on) {
        TreeSet<String> through = new TreeSet<>();
        rates.keySet().forEach(pair -> {
            through.add(pair.from());
            through.add(pair.to());
        });
        through.remove(from);
        through.remove(to);
        for (String via : through) {
            Optional<BigDecimal> first = leg(from, via, on);
            Optional<BigDecimal> second = first.isPresent() ? leg(via, to, on) : Optional.empty();
            if (second.isPresent()) {
                return Optional.of(Decimals.normalize(first.get().multiply(second.get())));
            }
        }
        return Optional.empty();
    }
}
