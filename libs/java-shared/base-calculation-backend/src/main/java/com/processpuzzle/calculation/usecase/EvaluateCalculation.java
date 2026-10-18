package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.engine.CalculationEngine;
import com.processpuzzle.calculation.engine.CalculationException;
import com.processpuzzle.calculation.engine.EvaluationResult;
import com.processpuzzle.calculation.usecase.exception.InvalidCalculationException;
import com.processpuzzle.core.definition.Channel;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Computes a calculation and stores nothing — what a designer's preview calls, the unsaved source it is editing
 * included. A decision that fails is part of the answer, with the trace up to it, rather than an error.
 */
@Service
public class EvaluateCalculation {

    private final FindCalculationDefinition definitions;
    private final CalculationValidator validator;
    private final InputResolver resolver;
    private final CalculationEngine engine;
    private final TransactionTemplate reading;

    public EvaluateCalculation(FindCalculationDefinition definitions, CalculationValidator validator, InputResolver resolver,
                               CalculationEngine engine, PlatformTransactionManager transactionManager) {
        this.definitions = definitions;
        this.validator = validator;
        this.resolver = resolver;
        this.engine = engine;
        this.reading = new TransactionTemplate(transactionManager);
        this.reading.setReadOnly(true);
    }

    /** The result as JSON-ready values, and why it stopped when it did. */
    public record Evaluation(EvaluationResult result, String error) {
    }

    /**
     * @param source an unsaved source to evaluate instead of the stored one; null for the stored one
     * @throws InvalidCalculationException when the source does not validate
     */
    public Evaluation evaluate(String orgKey, String calculationId, Channel channel, Map<String, Object> parameters,
                               String source) {
        CalculationValidator.Validation validation = source != null ? validator.validate(source)
                : validator.validate(definitions.find(orgKey, calculationId, channel).parsedModel());
        if (!validation.isValid()) {
            throw new InvalidCalculationException(calculationId, validation.errors());
        }
        CalculationModel model = validation.model();
        return reading.execute(status -> {
            ResolvedInputs inputs = resolver.resolve(orgKey, model, parameters);
            try {
                return new Evaluation(engine.evaluate(validation.analysis().plan(), inputs.parameters(), inputs.inputs()).plain(), null);
            } catch (CalculationException e) {
                EvaluationResult partial = new EvaluationResult(Map.of(), Map.of(), Map.of(),
                        validation.analysis().order(), e.trace());
                return new Evaluation(partial.plain(), e.getMessage());
            }
        });
    }
}
