package com.processpuzzle.calculation.domain.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CalculationParserTest {

    @Test
    void readsTheProposalsShape() {
        CalculationParser.Parsed parsed = CalculationParser.parse("""
                calculation: c
                name: C
                parameters: { order: { entity: order } }
                decisions:
                  x: { for: lines, requires: [y], expr: "1" }
                trigger:
                  manual: true
                  freeze: { state: { entity: order, to: CONFIRMED } }
                tables:
                  t: { hitPolicy: unique, inputs: [a], rules: [ { when: [1], then: 0.1 } ] }
                """);

        assertThat(parsed.isParsed()).isTrue();
        CalculationModel model = parsed.model();
        assertThat(model.id()).isEqualTo("c");
        assertThat(model.decisions().get("x").scope()).isEqualTo("lines");
        assertThat(model.decisions().get("x").kind()).isEqualTo(DecisionKind.EXPR);
        assertThat(model.trigger().freeze().state()).isEqualTo(new StateRef("order", "CONFIRMED"));
        assertThat(model.tables().get("t").hitPolicy()).isEqualTo(HitPolicy.UNIQUE);
        assertThat(model.tables().get("t").rules().getFirst().cells()).containsExactly("1");
        assertThat(model.tables().get("t").rules().getFirst().outputExpression()).isEqualTo("0.1");
        assertThat(model.parametersOfEntity("order")).containsExactly("order");
    }

    @Test
    void namesWhereAnUnknownKeyIs() {
        CalculationParser.Parsed parsed = CalculationParser.parse("""
                calculation: c
                decisions:
                  x: { expresion: "1" }
                """);

        assertThat(parsed.isParsed()).isFalse();
        assertThat(parsed.error()).startsWith("'decisions.x.expresion': unknown key \"expresion\"").contains("line 3");
    }

    @Test
    void reportsBrokenAndEmptySources() {
        assertThat(CalculationParser.parse("calculation: [").error()).contains("line");
        assertThat(CalculationParser.parse(" ").error()).isEqualTo("the source is empty");
        assertThat(CalculationParser.parse("# only a comment\n").error()).isEqualTo("the source is empty");
    }

    @Test
    void readsFreezeOnCompletionAndRejectsTheRest() {
        assertThat(FreezeSpec.of("onCompletion").onCompletion()).isTrue();
        assertThat(FreezeSpec.of("later").unknown()).isEqualTo("later");
        assertThat(FreezeSpec.of(java.util.Map.of("x", 1)).unknown()).isNotNull();
    }

    @Test
    void splitsAFileIntoDocumentsKeepingComments() {
        assertThat(CalculationParser.documents("""
                # the first
                calculation: a
                ---
                ---
                # only a comment
                ---
                calculation: b
                """)).containsExactly("# the first\ncalculation: a\n", "calculation: b\n");
    }

    @Test
    void readsTheSourceAsAMap() {
        assertThat(CalculationParser.asMap("calculation: a\n")).containsEntry("calculation", "a");
        assertThatThrownBy(() -> CalculationParser.asMap("a: [")).isInstanceOf(IllegalArgumentException.class);
    }
}
