package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.CalculationParser;
import com.processpuzzle.calculation.engine.CalculationAnalysis;
import com.processpuzzle.calculation.engine.CalculationAnalyzer;
import com.processpuzzle.calculation.engine.ExpressionEnginePort;
import java.util.List;
import org.springframework.stereotype.Component;

/** Parses and analyzes a source: the one check behind save, import, release and the designer's Validate. */
@Component
public class CalculationValidator {

    private final CalculationAnalyzer analyzer;

    public CalculationValidator(ExpressionEnginePort feel) {
        this.analyzer = new CalculationAnalyzer(feel);
    }

    /**
     * @param model    the parsed source, null when it does not parse
     * @param analysis the analysis of a parsed source, null otherwise
     * @param errors   every problem, a parse error included
     */
    public record Validation(CalculationModel model, CalculationAnalysis analysis, List<String> errors) {

        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    public Validation validate(String source) {
        CalculationParser.Parsed parsed = CalculationParser.parse(source);
        if (!parsed.isParsed()) {
            return new Validation(null, null, List.of(parsed.error()));
        }
        return validate(parsed.model());
    }

    public Validation validate(CalculationModel model) {
        CalculationAnalysis analysis = analyzer.analyze(model);
        return new Validation(model, analysis, analysis.errors());
    }
}
