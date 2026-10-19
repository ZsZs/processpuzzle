package com.processpuzzle.calculation.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The engine's semantics beyond the two acceptance tests. */
class CalculationEngineTest {

    private static EvaluationResult evaluate(String source, Map<String, Object> parameters,
                                             Map<String, List<Map<String, Object>>> inputs) {
        CalculationAnalysis analysis = Calculations.ANALYZER.analyze(Calculations.model(source));
        assertThat(analysis.errors()).isEmpty();
        return Calculations.ENGINE.evaluate(analysis.plan(), parameters, inputs);
    }

    private static List<Map<String, Object>> scores(Object... values) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < values.length; index += 2) {
            rows.add(new java.util.HashMap<>(Map.of("name", values[index], "score", values[index + 1])));
        }
        return rows;
    }

    @Test
    void ranksWithTiesAsACompetition() {
        EvaluationResult result = evaluate("""
                calculation: ranking
                inputs: { players: { entity: player } }
                decisions:
                  place: { for: players, rank: { by: score, order: desc } }
                """, Map.of(), Map.of("players", scores("a", 10, "b", 30, "c", 30, "d", 5)));

        assertThat(result.rows().get("players")).extracting(row -> row.get("name") + "=" + row.get("place"))
                .containsExactly("a=3", "b=1", "c=1", "d=4");
    }

    @Test
    void aggregatesWithDropsAndBroadcasts() {
        EvaluationResult result = evaluate("""
                calculation: aggregates
                inputs: { players: { entity: player } }
                decisions:
                  best: { aggregate: { max: score } }
                  average: { aggregate: { avg: score } }
                  distinct: { aggregate: { countDistinct: score } }
                  withoutExtremes: { aggregate: { sum: score, dropWorst: 1, dropBest: "1" } }
                  higherIsBetter: { aggregate: { sum: score, dropWorst: 1, better: higher } }
                  share: { for: players, aggregate: { sum: score } }
                """, Map.of(), Map.of("players", scores("a", 10, "b", 30, "c", 30, "d", 5)));

        assertThat(result.values()).containsEntry("best", 30).containsEntry("distinct", BigDecimal.valueOf(3))
                .containsEntry("withoutExtremes", new BigDecimal("40")).containsEntry("higherIsBetter", new BigDecimal("70"));
        assertThat((BigDecimal) result.values().get("average")).isEqualByComparingTo("18.75");
        assertThat(result.rows().get("players")).allMatch(row -> new BigDecimal("75").compareTo((BigDecimal) row.get("share")) == 0);
    }

    @Test
    void sumsDurations() {
        EvaluationResult result = evaluate("""
                calculation: durations
                inputs: { laps: { entity: lap } }
                decisions:
                  total: { aggregate: { sum: time } }
                  mean: { aggregate: { avg: time } }
                """, Map.of(), Map.of("laps", List.of(Map.of("time", Duration.ofSeconds(60)), Map.of("time", Duration.ofSeconds(90)))));

        assertThat(result.values()).containsEntry("total", Duration.ofSeconds(150)).containsEntry("mean", Duration.ofSeconds(75));
    }

    private static final String HIT_POLICY = """
            calculation: hit
            parameters: { x: { type: number } }
            tables:
              t:
                hitPolicy: %s
                inputs: [x]
                rules:
                  - { when: ['> 0'], then: '"positive"' }
                  - { when: ['> 10'], then: '"%s"' }
            decisions:
              verdict: { table: t }
            """;

    @Test
    void collectsEveryMatchingOutput() {
        EvaluationResult result = evaluate(HIT_POLICY.formatted("COLLECT", "large"), Map.of("x", 20), Map.of());

        assertThat(result.values()).containsEntry("verdict", List.of("positive", "large"));
    }

    @Test
    void anyAcceptsAgreeingRulesOnly() {
        assertThat(evaluate(HIT_POLICY.formatted("ANY", "positive"), Map.of("x", 20), Map.of()).values())
                .containsEntry("verdict", "positive");
        String disagreeing = HIT_POLICY.formatted("ANY", "large");
        Map<String, Object> twenty = Map.of("x", 20);
        Map<String, List<Map<String, Object>>> noInputs = Map.of();
        assertThatThrownBy(() -> evaluate(disagreeing, twenty, noInputs))
                .isInstanceOf(CalculationException.class).hasMessageContaining("disagree");
    }

    @Test
    void uniqueRefusesTwoMatches() {
        assertThat(evaluate(HIT_POLICY.formatted("UNIQUE", "large"), Map.of("x", 5), Map.of()).values())
                .containsEntry("verdict", "positive");
        String unique = HIT_POLICY.formatted("UNIQUE", "large");
        Map<String, Object> twenty = Map.of("x", 20);
        Map<String, List<Map<String, Object>>> noInputs = Map.of();
        assertThatThrownBy(() -> evaluate(unique, twenty, noInputs))
                .isInstanceOf(CalculationException.class)
                .satisfies(e -> assertThat(((CalculationException) e).trace()).singleElement()
                        .satisfies(entry -> assertThat(entry.error()).contains("rules [1, 2]")));
        assertThat(evaluate(HIT_POLICY.formatted("UNIQUE", "large"), Map.of("x", -1), Map.of()).values()).containsEntry("verdict", null);
    }

    @Test
    void callsFunctionsAndThePlatformLibrary() {
        EvaluationResult result = evaluate("""
                calculation: functions
                parameters: { prices: { type: string } }
                functions:
                  gross: "function(net) net * 1.2"
                decisions:
                  total: { expr: "gross(100)" }
                  kept: { expr: "drop worst([4, 1, 3, 2], 1)" }
                  dropped: { expr: "drop best([4, 1, 3, 2], 2)" }
                  podium: { expr: "top([4, 1, 3, 2], 3)" }
                """, Map.of("prices", "-"), Map.of());

        assertThat((BigDecimal) result.values().get("total")).isEqualByComparingTo("120");
        assertThat(result.values().get("kept")).asList().extracting(Object::toString).containsExactly("1", "2", "3");
        assertThat(result.values().get("dropped")).asList().extracting(Object::toString).containsExactly("3", "4");
        assertThat(result.values().get("podium")).asList().extracting(Object::toString).containsExactly("4", "3", "2");
        assertThat(result.plain().trace()).filteredOn(entry -> entry.decision().equals("total")).isNotEmpty();
    }

    @Test
    void aGroupExpressionSeesItsMembersUnderTheInputsName() {
        EvaluationResult result = evaluate("""
                calculation: groups
                inputs: { finishes: { entity: finish } }
                decisions:
                  rounds: { for: boat, expr: "count(finishes)" }
                """, Map.of(), Map.of("finishes", List.of(Map.of("boat", "A"), Map.of("boat", "B"), Map.of("boat", "A"))));

        assertThat(result.groups().get("boat")).extracting(row -> row.get("boat") + "=" + row.get("rounds"))
                .containsExactly("A=2", "B=1");
    }

    @Test
    void stopsAtTheFailingDecisionWithTheTraceSoFar() {
        String failing = """
                calculation: failing
                parameters: { x: { type: number } }
                tables:
                  t:
                    hitPolicy: UNIQUE
                    inputs: [x]
                    rules:
                      - { when: ['> 0'], then: 1 }
                      - { when: ['> 1'], then: 2 }
                decisions:
                  first: { expr: "x * 2" }
                  second: { table: t, requires: [first] }
                  third: { expr: "second + 1" }
                """;
        Map<String, Object> five = Map.of("x", 5);
        Map<String, List<Map<String, Object>>> noInputs = Map.of();
        assertThatThrownBy(() -> evaluate(failing, five, noInputs))
                .isInstanceOf(CalculationException.class)
                .hasMessageStartingWith("decision 'second': table 't' is UNIQUE")
                .satisfies(e -> assertThat(((CalculationException) e).trace())
                        .extracting(entry -> entry.decision() + "=" + entry.value() + "/" + (entry.error() != null))
                        .containsExactly("first=10/false", "second=null/true"));
    }

    @Test
    void appliesAParametersDefault() {
        EvaluationResult result = evaluate("""
                calculation: defaults
                parameters: { rate: { type: number, default: 0.5 } }
                decisions:
                  half: { expr: "10 * rate" }
                """, Map.of(), Map.of());

        assertThat((BigDecimal) result.values().get("half")).isEqualByComparingTo("5");
    }
}
