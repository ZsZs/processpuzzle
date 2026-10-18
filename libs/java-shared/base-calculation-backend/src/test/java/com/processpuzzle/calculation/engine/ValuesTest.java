package com.processpuzzle.calculation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValuesTest {

    @Test
    void comparesNumbersByValueAndPutsNullsLast() {
        assertThat(Values.compare(1, new BigDecimal("1.0"))).isZero();
        assertThat(Values.compare(2.5, 3L)).isNegative();
        assertThat(Values.compare(null, 1)).isPositive();
        assertThat(Values.compare(1, null)).isNegative();
        assertThat(Values.compare(null, null)).isZero();
        assertThat(Values.compare("a", "b")).isNegative();
        assertThat(Values.compare("a", 1)).isPositive();
    }

    @Test
    void keysEqualNumbersAlike() {
        assertThat(Values.key(1)).isEqualTo(Values.key(new BigDecimal("1.00")));
        assertThat(Values.key(0.0)).isEqualTo(Values.key(0));
        assertThat(Values.key("x")).isEqualTo("x");
    }

    @Test
    void convertsToNumbers() {
        assertThat(Values.number(19.99)).isEqualTo(new BigDecimal("19.99"));
        assertThat(Values.number(1.5f)).isEqualTo(new BigDecimal("1.5"));
        assertThat(Values.number((short) 2)).isEqualTo(BigDecimal.valueOf(2));
        assertThat(Values.number((byte) 3)).isEqualTo(BigDecimal.valueOf(3));
        assertThat(Values.number(java.math.BigInteger.TEN)).isEqualTo(BigDecimal.TEN);
        assertThat(Values.number(Double.NaN)).isNull();
        assertThat(Values.number("1")).isNull();
    }

    @Test
    void makesValuesPlain() {
        assertThat(Values.plain(Map.of("d", Duration.ofMinutes(10), "l", List.of(LocalDate.of(2026, 1, 2), 3))))
                .isEqualTo(Map.of("d", "PT10M", "l", List.of("2026-01-02", BigDecimal.valueOf(3))));
        assertThat(Values.plain(java.time.Period.ofDays(2))).isEqualTo("P2D");
        assertThat(Values.plain(Boolean.TRUE)).isEqualTo(true);
        assertThat(Values.plain(null)).isNull();
        assertThat(Values.plain(new Object() {
            @Override
            public String toString() {
                return "other";
            }
        })).isEqualTo("other");
    }
}
