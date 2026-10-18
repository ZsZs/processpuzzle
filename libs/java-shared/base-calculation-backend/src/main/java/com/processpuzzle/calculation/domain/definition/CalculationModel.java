package com.processpuzzle.calculation.domain.definition;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A calculation as its YAML source declares it — the proposal's shape: parameters, named inputs, decision
 * tables, FEEL functions, the decision graph, the output and the trigger. A plain value read by
 * {@code CalculationParser}; nothing here says whether it is valid, which is {@code CalculationAnalyzer}'s job.
 *
 * <p>Every map keeps the source's order, so a trace or a validation message lists things as the author wrote them.
 *
 * @param version informational only — the stored definition's {@code definitionVersion} is what a run records
 */
public record CalculationModel(
        @JsonProperty("calculation") String id,
        String name,
        String description,
        Integer version,
        Map<String, ParameterSpec> parameters,
        Map<String, InputSpec> inputs,
        Map<String, DecisionTable> tables,
        Map<String, String> functions,
        Map<String, DecisionSpec> decisions,
        OutputSpec output,
        TriggerSpec trigger) {

    public CalculationModel {
        parameters = orEmpty(parameters);
        inputs = orEmpty(inputs);
        tables = orEmpty(tables);
        functions = orEmpty(functions);
        decisions = orEmpty(decisions);
    }

    /** The entity parameters whose entity is {@code entity}, in declaration order. */
    public java.util.List<String> parametersOfEntity(String entity) {
        return parameters.entrySet().stream()
                .filter(parameter -> entity != null && entity.equals(parameter.getValue().entity()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static <V> Map<String, V> orEmpty(Map<String, V> map) {
        return map == null ? new LinkedHashMap<>() : map;
    }
}
