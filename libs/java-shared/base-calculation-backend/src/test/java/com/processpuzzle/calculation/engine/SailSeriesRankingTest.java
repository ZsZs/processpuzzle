package com.processpuzzle.calculation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The proposal's sailing example as the engine's acceptance test, on finishes held in memory: four boats, four
 * rounds, a DNF, one discard each, and two boats level on points that the best round separates.
 *
 * <pre>
 *            R1  R2  R3   R4   discard  total  best  rank
 *   Alpha     1   3   3    4      4       7      1     2
 *   Bravo     2   2   DNF  3      5       7      2     3
 *   Charlie   3   1   1    1      3       3      1     1
 *   Delta     4   4   2    2      4       8      2     4
 * </pre>
 */
class SailSeriesRankingTest {

    private static EvaluationResult result;

    @BeforeAll
    static void evaluate() {
        List<Map<String, Object>> finishes = new ArrayList<>();
        round(finishes, 1, Map.of("Alpha", 600, "Bravo", 610, "Charlie", 620, "Delta", 630));
        round(finishes, 2, Map.of("Alpha", 700, "Bravo", 690, "Charlie", 680, "Delta", 720));
        round(finishes, 3, Map.of("Alpha", 900, "Charlie", 800, "Delta", 850));
        finishes.add(finish("Bravo", 3, null, "DNF"));
        round(finishes, 4, Map.of("Alpha", 560, "Bravo", 550, "Charlie", 500, "Delta", 520));

        result = Calculations.ENGINE.evaluate(Calculations.plan("sail-series-ranking"),
                Map.of("series", Map.of("id", "series-1", "name", "Autumn Cup")), Map.of("finishes", finishes));
    }

    @Test
    void derivesElapsedTimesAsDurations() {
        assertThat(rowsOf("Alpha")).extracting(row -> row.get("elapsed"))
                .containsExactly(Duration.ofSeconds(600), Duration.ofSeconds(700), Duration.ofSeconds(900), Duration.ofSeconds(560));
        assertThat(rowOf("Bravo", 3).get("elapsed")).isNull();
    }

    @Test
    void ranksEachRoundAndLeavesTheDnfUnranked() {
        assertThat(rowsOf("Alpha")).extracting(row -> row.get("position")).containsExactly(number(1), number(3), number(3), number(4));
        assertThat(rowOf("Bravo", 3).get("position")).isNull();
        assertThat(rowOf("Charlie", 3)).containsEntry("position", number(1));
    }

    @Test
    void scoresTheDnfAsStartersPlusOne() {
        assertThat(rowOf("Bravo", 3)).containsEntry("starters", number(4)).containsEntry("roundPoints", number(5));
        assertThat(rowsOf("Delta")).extracting(row -> row.get("roundPoints")).containsExactly(number(4), number(4), number(2), number(2));
    }

    @Test
    void discardsTheWorstRoundFromTheFourthOn() {
        assertThat(result.values()).containsEntry("completedRounds", number(4)).containsEntry("discardCount", number(1));
        assertThat(group("Alpha")).containsEntry("seriesTotal", number(7)).containsEntry("bestRound", number(1));
        assertThat(group("Bravo")).containsEntry("seriesTotal", number(7)).containsEntry("bestRound", number(2));
        assertThat(group("Charlie")).containsEntry("seriesTotal", number(3));
        assertThat(group("Delta")).containsEntry("seriesTotal", number(8));
    }

    @Test
    void breaksTheTieOnPointsByTheBestRound() {
        assertThat(result.groups().get("boat")).extracting(row -> row.get("boat") + "=" + row.get("seriesRank"))
                .containsExactly("Alpha=2", "Bravo=3", "Charlie=1", "Delta=4");
    }

    @Test
    void tracesEveryDecisionInDependencyOrder() {
        assertThat(result.order()).containsExactly("elapsed", "position", "starters", "roundPoints", "completedRounds",
                "discardCount", "bestRound", "seriesTotal", "seriesRank");
        assertThat(result.trace()).extracting(TraceEntry::scope)
                .containsExactly("row:finishes", "row:finishes", "row:finishes", "row:finishes", "scalar", "scalar",
                        "group:boat", "group:boat", "group:boat");
        assertThat(result.plain().trace().get(0).value()).asList().first().isEqualTo("PT10M");
    }

    private static Map<String, Object> group(String boat) {
        return result.groups().get("boat").stream().filter(row -> boat.equals(row.get("boat"))).findFirst().orElseThrow();
    }

    private static List<Map<String, Object>> rowsOf(String boat) {
        return result.rows().get("finishes").stream().filter(row -> boat.equals(row.get("boat")))
                .sorted(java.util.Comparator.comparing(row -> (Integer) row.get("round"))).toList();
    }

    private static Map<String, Object> rowOf(String boat, int round) {
        return rowsOf(boat).stream().filter(row -> row.get("round").equals(round)).findFirst().orElseThrow();
    }

    private static void round(List<Map<String, Object>> finishes, int round, Map<String, Integer> seconds) {
        new java.util.TreeMap<>(seconds).forEach((boat, elapsed) -> finishes.add(finish(boat, round, elapsed, "OK")));
    }

    private static Map<String, Object> finish(String boat, int round, Integer elapsed, String status) {
        LocalDateTime start = LocalDateTime.of(2026, 6, round, 10, 0);
        Map<String, Object> finish = new LinkedHashMap<>();
        finish.put("boat", boat);
        finish.put("round", round);
        finish.put("startTime", start);
        finish.put("finishTime", elapsed == null ? null : start.plusSeconds(elapsed));
        finish.put("status", status);
        return finish;
    }

    private static BigDecimal number(int value) {
        return BigDecimal.valueOf(value);
    }
}
