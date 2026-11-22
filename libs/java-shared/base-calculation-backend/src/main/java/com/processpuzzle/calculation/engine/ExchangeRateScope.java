package com.processpuzzle.calculation.engine;

import com.processpuzzle.core.quantity.ExchangeRates;
import java.util.function.Supplier;

/**
 * The exchange rates the calculation being evaluated converts at: those of the organization it runs for. FEEL's functions are
 * registered once for the whole engine and are called with their arguments alone, so what makes {@code convert currency} read
 * one organization's rates and not another's is this scope, entered by the use case that knows the organization and left when
 * the evaluation ends — on the thread that evaluates, which is the one that entered it. Outside any scope there are no rates,
 * so a conversion between two currencies is refused.
 */
public final class ExchangeRateScope {

    private static final ThreadLocal<ExchangeRates> CURRENT = new ThreadLocal<>();

    private ExchangeRateScope() {
    }

    /** Runs {@code body} with {@code rates} as the rates conversions read; the previous ones come back afterwards. */
    public static <T> T with(ExchangeRates rates, Supplier<T> body) {
        ExchangeRates previous = CURRENT.get();
        CURRENT.set(rates);
        try {
            return body.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** The rates of the evaluation in progress; none outside one. */
    public static ExchangeRates current() {
        ExchangeRates rates = CURRENT.get();
        return rates == null ? ExchangeRates.none() : rates;
    }
}
