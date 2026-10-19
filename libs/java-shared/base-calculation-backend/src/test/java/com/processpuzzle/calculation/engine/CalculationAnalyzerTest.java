package com.processpuzzle.calculation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CalculationAnalyzerTest {

    private static CalculationAnalysis analyze(String source) {
        return Calculations.ANALYZER.analyze(Calculations.model(source));
    }

    private static final String HEADER = """
            calculation: test
            parameters:
              order: { entity: order }
            inputs:
              lines: { from: order, path: lineItems }
            """;

    @Test
    void ordersDecisionsByWhatTheyNameEvenWithoutRequires() {
        CalculationAnalysis analysis = analyze(HEADER + """
                decisions:
                  total: { expr: "subtotal * 2" }
                  subtotal: { aggregate: { sum: lineTotal } }
                  lineTotal: { for: lines, expr: "quantity * unitPrice" }
                """);

        assertThat(analysis.errors()).isEmpty();
        assertThat(analysis.order()).containsExactly("lineTotal", "subtotal", "total");
        assertThat(analysis.dependencies()).extracting(edge -> edge.from() + "->" + edge.to())
                .containsExactlyInAnyOrder("subtotal->total", "lineTotal->subtotal");
        assertThat(analysis.decisions()).extracting(decision -> decision.name() + "@" + decision.scope())
                .containsExactly("total@scalar", "subtotal@scalar", "lineTotal@row:lines");
    }

    @Test
    void ignoresNamesInsideStringLiterals() {
        CalculationAnalysis analysis = analyze(HEADER + """
                decisions:
                  label: { expr: '"total"' }
                  total: { expr: "1" }
                """);

        assertThat(analysis.dependencies()).isEmpty();
    }

    @Test
    void reportsACycle() {
        CalculationAnalysis analysis = analyze(HEADER + """
                decisions:
                  a: { expr: "b + 1" }
                  b: { expr: "a + 1" }
                """);

        assertThat(analysis.errors()).singleElement().asString().contains("cycle").contains("a").contains("b");
        assertThat(analysis.plan()).isNull();
        assertThat(analysis.order()).isEmpty();
    }

    @Test
    void requiresExactlyOneLogicType() {
        CalculationAnalysis analysis = analyze(HEADER + """
                decisions:
                  both: { expr: "1", table: t }
                  none: { requires: [both] }
                """);

        assertThat(analysis.errors()).hasSize(2).allMatch(error -> error.contains("exactly one of"));
    }

    @Test
    void checksTablesForArityAndSyntax() {
        CalculationAnalysis analysis = analyze(HEADER + """
                tables:
                  t:
                    inputs: [x]
                    rules:
                      - { when: ['< 1', '-'], then: 1 }
                      - { when: ['>>> 2'], then: "1 +" }
                decisions:
                  x: { expr: "1" }
                  y: { table: t }
                  z: { table: missing }
                """);

        assertThat(analysis.errors()).anyMatch(error -> error.contains("rule 1 has 2 cell(s) for 1 input(s)"))
                .anyMatch(error -> error.contains("rule 2: '>>> 2' is no unary test"))
                .anyMatch(error -> error.contains("rule 2 output"))
                .anyMatch(error -> error.contains("table 'missing'"));
    }

    @Test
    void checksFeelSyntaxAndUnknownRequirements() {
        CalculationAnalysis analysis = analyze(HEADER + """
                decisions:
                  broken: { expr: "1 +* 2", requires: [nothing] }
                """);

        assertThat(analysis.errors()).anyMatch(error -> error.startsWith("decision 'broken': "))
                .anyMatch(error -> error.contains("requires 'nothing'"));
    }

    @Test
    void aRankNeedsARowOrGroupScope() {
        CalculationAnalysis analysis = analyze(HEADER + """
                decisions:
                  place: { rank: { by: total, order: sideways } }
                """);

        assertThat(analysis.errors()).anyMatch(error -> error.contains("a rank needs a row scope"))
                .anyMatch(error -> error.contains("'order' is 'asc' or 'desc'"));
    }

    @Test
    void asksForTheInputWhenAGroupingIsAmbiguous() {
        CalculationAnalysis analysis = analyze("""
                calculation: test
                inputs:
                  a: { entity: x }
                  b: { entity: y }
                decisions:
                  perBoat: { for: boat, aggregate: { count: boat } }
                  explicit: { for: a.boat, aggregate: { count: boat } }
                  total: { aggregate: { sum: points } }
                """);

        assertThat(analysis.errors()).anyMatch(error -> error.contains("write 'for: <input>.boat'"))
                .anyMatch(error -> error.contains("decision 'total': cannot tell which rows it aggregates"))
                .noneMatch(error -> error.contains("'explicit'"));
    }

    @Test
    void checksParametersInputsAndPlaceholders() {
        CalculationAnalysis analysis = analyze("""
                calculation: bad id!
                parameters:
                  both: { entity: order, type: number }
                  odd: { type: money }
                inputs:
                  rows: { entity: finish, where: "series==${series}" }
                  lines: { from: odd }
                decisions:
                  x: { expr: "1" }
                """);

        assertThat(analysis.errors())
                .anyMatch(error -> error.contains("calculation id 'bad id!'"))
                .anyMatch(error -> error.contains("parameter 'both' needs exactly one"))
                .anyMatch(error -> error.contains("has type 'money'"))
                .anyMatch(error -> error.contains("'${series}', but there is no parameter 'series'"))
                .anyMatch(error -> error.contains("reads from 'odd', which is no entity parameter"))
                .anyMatch(error -> error.contains("needs the 'path'"));
    }

    @Test
    void checksTheOutputAndTheTrigger() {
        CalculationAnalysis analysis = analyze(HEADER + """
                decisions:
                  lineTotal: { for: lines, expr: "quantity * unitPrice" }
                output:
                  entity: invoice
                  mapping: { total: lineTotal }
                  materialization: sometimes
                trigger:
                  onChange: { entity: customer }
                  freeze: whenever
                """);

        assertThat(analysis.errors())
                .anyMatch(error -> error.contains("materialization is 'stored' or 'onRead'"))
                .anyMatch(error -> error.contains("no entity parameter of that entity names it"))
                .anyMatch(error -> error.contains("maps 'total' from 'lineTotal', which is no single-valued decision"))
                .anyMatch(error -> error.contains("onChange names 'customer'"))
                .anyMatch(error -> error.contains("not 'whenever'"));
    }

    @Test
    void checksAGroupedOutput() {
        CalculationAnalysis analysis = analyze(HEADER + """
                decisions:
                  perProduct: { for: productName, aggregate: { sum: quantity } }
                  line: { for: lines, expr: "1" }
                output:
                  entity: product-total
                  for: productName
                  key: [product, order]
                  mapping: { product: productName, quantity: perProduct, line: line }
                """);

        assertThat(analysis.errors())
                .anyMatch(error -> error.contains("key 'order' is not mapped"))
                .anyMatch(error -> error.contains("maps 'line' from 'line', which is not evaluated per 'productName'"))
                .hasSize(2);
    }

    @Test
    void acceptsBothProposalExamples() {
        assertThat(Calculations.ANALYZER.analyze(Calculations.model(Calculations.source("sail-series-ranking"))).errors()).isEmpty();
        assertThat(Calculations.ANALYZER.analyze(Calculations.model(Calculations.source("order-total"))).errors()).isEmpty();
    }

    @Test
    void findsIdentifiers() {
        assertThat(CalculationAnalyzer.identifiersIn("a + b.c - \"d e\" * f(g)")).containsExactly("a", "b", "f", "g");
        assertThat(CalculationAnalyzer.identifiersIn(null)).isEmpty();
    }
}
