package com.processpuzzle.workflow.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.workflow.definition.domain.TimerExpressions.Arming;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class TimerExpressionsTest {

    private static final Instant NOW = Instant.parse("2026-01-31T10:00:00Z");
    private static final Function<String, Object> NO_PATHS = path -> null;

    // ---------------------------------------------------------------- DURATION

    @Test
    void aDurationSplitsIntoItsCalendarAndClockParts() {
        assertThat(TimerExpressions.parseAmount("PT2H")).isEqualTo(new TimerExpressions.Amount(Period.ZERO, Duration.ofHours(2)));
        assertThat(TimerExpressions.parseAmount("P3D")).isEqualTo(new TimerExpressions.Amount(Period.ofDays(3), Duration.ZERO));
        assertThat(TimerExpressions.parseAmount("P1Y2M3DT4H5M"))
                .isEqualTo(new TimerExpressions.Amount(Period.of(1, 2, 3), Duration.ofHours(4).plusMinutes(5)));
    }

    @Test
    void monthsAndYearsAreCalendarAmountsInUtc() {
        assertThat(arm(TimerType.DURATION, "P1M")).isEqualTo(new Arming(Instant.parse("2026-02-28T10:00:00Z"), 0));
        assertThat(arm(TimerType.DURATION, "P1Y")).isEqualTo(new Arming(Instant.parse("2027-01-31T10:00:00Z"), 0));
        assertThat(arm(TimerType.DURATION, "P1DT2H")).isEqualTo(new Arming(Instant.parse("2026-02-01T12:00:00Z"), 0));
    }

    @Test
    void aMalformedOrNegativeDurationIsRefused() {
        assertThatThrownBy(() -> TimerExpressions.parseAmount("2 hours")).hasMessageContaining("not an ISO 8601 duration");
        assertThatThrownBy(() -> TimerExpressions.parseAmount("P")).hasMessageContaining("not an ISO 8601 duration");
        assertThatThrownBy(() -> TimerExpressions.parseAmount("PT")).hasMessageContaining("not an ISO 8601 duration");
        assertThatThrownBy(() -> TimerExpressions.parseAmount("P-1D")).hasMessageContaining("negative");
    }

    // ---------------------------------------------------------------- DATE

    @Test
    void aDateIsAnInstantAnOffsetOrLocalDateTimeOrADay() {
        assertThat(TimerExpressions.parseDate("2026-10-10T08:00:00Z")).isEqualTo(Instant.parse("2026-10-10T08:00:00Z"));
        assertThat(TimerExpressions.parseDate("2026-10-10T10:00:00+02:00")).isEqualTo(Instant.parse("2026-10-10T08:00:00Z"));
        assertThat(TimerExpressions.parseDate("2026-10-10T08:00:00")).isEqualTo(Instant.parse("2026-10-10T08:00:00Z"));
        assertThat(TimerExpressions.parseDate("2026-10-10")).isEqualTo(Instant.parse("2026-10-10T00:00:00Z"));
        assertThatThrownBy(() -> TimerExpressions.parseDate("tomorrow")).hasMessageContaining("not an ISO 8601 date");
    }

    @Test
    void aDateFiresOnceAtItsMoment() {
        assertThat(arm(TimerType.DATE, "2026-12-24")).isEqualTo(new Arming(Instant.parse("2026-12-24T00:00:00Z"), 0));
    }

    // ---------------------------------------------------------------- CYCLE

    @Test
    void aCycleWithoutAnchorFirstFiresOneIntervalOn() {
        assertThat(arm(TimerType.CYCLE, "R3/PT1H")).isEqualTo(new Arming(NOW.plus(Duration.ofHours(1)), 2));
        assertThat(arm(TimerType.CYCLE, "R/PT1H")).isEqualTo(new Arming(NOW.plus(Duration.ofHours(1)), null));
    }

    @Test
    void anAnchoredCycleFiresAtItsAnchor() {
        assertThat(arm(TimerType.CYCLE, "R3/2026-02-01T08:00:00Z/P1D"))
                .isEqualTo(new Arming(Instant.parse("2026-02-01T08:00:00Z"), 2));
    }

    @Test
    void anAnchoredCycleSkipsTheFiringsAlreadyPast() {
        assertThat(arm(TimerType.CYCLE, "R5/2026-01-29T08:00:00Z/P1D"))
                .isEqualTo(new Arming(Instant.parse("2026-02-01T08:00:00Z"), 1));
        assertThat(arm(TimerType.CYCLE, "R/2026-01-01T00:00:00Z/PT1H"))
                .isEqualTo(new Arming(Instant.parse("2026-01-31T10:00:00Z"), null));
        assertThat(TimerExpressions.arm(timer(TimerType.CYCLE, "R2/2026-01-01T00:00:00Z/P1D"), NO_PATHS, NOW)).isEmpty();
    }

    @Test
    void theNextFiringOfACycleIsOneIntervalOnWhileFiringsAreLeft() {
        TimerDefinition cycle = timer(TimerType.CYCLE, "R3/PT1H");

        assertThat(TimerExpressions.next(cycle, NO_PATHS, NOW, 2)).contains(new Arming(NOW.plus(Duration.ofHours(1)), 1));
        assertThat(TimerExpressions.next(cycle, NO_PATHS, NOW, 0)).isEmpty();
        assertThat(TimerExpressions.next(timer(TimerType.CYCLE, "R/PT1H"), NO_PATHS, NOW, null))
                .contains(new Arming(NOW.plus(Duration.ofHours(1)), null));
        assertThat(TimerExpressions.next(timer(TimerType.DURATION, "PT1H"), NO_PATHS, NOW, 0)).isEmpty();
    }

    @Test
    void aMalformedCycleIsRefused() {
        assertThatThrownBy(() -> TimerExpressions.parseCycle("PT1H")).hasMessageContaining("not a cycle");
        assertThatThrownBy(() -> TimerExpressions.parseCycle("Rx/PT1H")).hasMessageContaining("repetition count");
        assertThatThrownBy(() -> TimerExpressions.parseCycle("R0/PT1H")).hasMessageContaining("fewer than once");
        assertThatThrownBy(() -> TimerExpressions.parseCycle("R2/PT0S")).hasMessageContaining("zero interval");
        assertThatThrownBy(() -> TimerExpressions.parseCycle("R2/a/b/c")).hasMessageContaining("not a cycle");
    }

    // ---------------------------------------------------------------- paths

    @Test
    void aPathIsResolvedWhenTheTimerIsArmed() {
        Map<String, Object> context = Map.of("deadline", "2026-03-01", "grace", "P2D");
        Function<String, Object> paths = path -> context.get(path.substring(2));

        assertThat(TimerExpressions.arm(timer(TimerType.DATE, "$.deadline"), paths, NOW))
                .contains(new Arming(Instant.parse("2026-03-01T00:00:00Z"), 0));
        assertThat(TimerExpressions.arm(timer(TimerType.DURATION, "$.grace"), paths, NOW))
                .contains(new Arming(Instant.parse("2026-02-02T10:00:00Z"), 0));
    }

    @Test
    void aPathResolvingToNothingArmsNothingAndToGarbageIsRefused() {
        assertThat(TimerExpressions.arm(timer(TimerType.DATE, "$.missing"), NO_PATHS, NOW)).isEmpty();
        assertThatThrownBy(() -> TimerExpressions.arm(timer(TimerType.DATE, "$.when"), path -> "soon", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- validate

    @Test
    void validateChecksLiteralsAndOnlyTheFormOfAPath() {
        assertThatCode(() -> TimerExpressions.validate(timer(TimerType.CYCLE, "R2/PT5M"))).doesNotThrowAnyException();
        assertThatCode(() -> TimerExpressions.validate(timer(TimerType.DATE, "$.order.deadline"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> TimerExpressions.validate(timer(TimerType.DATE, "$."))).hasMessageContaining("not a context path");
        assertThatThrownBy(() -> TimerExpressions.validate(timer(TimerType.DATE, "$.a..b"))).hasMessageContaining("not a context path");
        assertThatThrownBy(() -> TimerExpressions.validate(timer(TimerType.DATE, " "))).hasMessageContaining("no expression");
        assertThatThrownBy(() -> TimerExpressions.validate(timer(null, "PT1H"))).hasMessageContaining("no type");
        assertThatThrownBy(() -> TimerExpressions.validate(timer(TimerType.DURATION, "R2/PT1H")))
                .hasMessageContaining("not an ISO 8601 duration");
    }

    @Test
    void aTimerDefinitionKnowsWhetherItIsAPath() {
        assertThat(timer(TimerType.DATE, "$.deadline").isPath()).isTrue();
        assertThat(timer(TimerType.DATE, "2026-12-24").isPath()).isFalse();
    }

    private static Arming arm(TimerType type, String expression) {
        return TimerExpressions.arm(timer(type, expression), NO_PATHS, NOW).orElseThrow();
    }

    private static TimerDefinition timer(TimerType type, String expression) {
        return TimerDefinition.builder().type(type).expression(expression).build();
    }
}
