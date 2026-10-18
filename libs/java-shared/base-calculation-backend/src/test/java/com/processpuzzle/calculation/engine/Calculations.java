package com.processpuzzle.calculation.engine;

import com.processpuzzle.calculation.adapter.outbound.CamundaFeelExpressionEngine;
import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.CalculationParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The test calculations under {@code src/test/resources/calculations}, and one FEEL engine for all tests. */
public final class Calculations {

    public static final ExpressionEnginePort FEEL = new CamundaFeelExpressionEngine();
    public static final CalculationAnalyzer ANALYZER = new CalculationAnalyzer(FEEL);
    public static final CalculationEngine ENGINE = new CalculationEngine(FEEL);

    private Calculations() {
    }

    public static String source(String name) {
        try (InputStream input = Calculations.class.getResourceAsStream("/calculations/" + name + ".yaml")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static CalculationModel model(String source) {
        CalculationParser.Parsed parsed = CalculationParser.parse(source);
        if (!parsed.isParsed()) {
            throw new IllegalArgumentException(parsed.error());
        }
        return parsed.model();
    }

    public static CalculationPlan plan(String name) {
        CalculationAnalysis analysis = ANALYZER.analyze(model(source(name)));
        if (!analysis.isValid()) {
            throw new IllegalArgumentException(String.join("; ", analysis.errors()));
        }
        return analysis.plan();
    }
}
