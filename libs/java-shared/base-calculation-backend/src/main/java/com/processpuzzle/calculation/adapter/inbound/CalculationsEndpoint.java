package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.api.CalculationsApi;
import com.processpuzzle.calculation.domain.RunTrigger;
import com.processpuzzle.calculation.model.CalculationRun;
import com.processpuzzle.calculation.model.CalculationOutput;
import com.processpuzzle.calculation.model.EvaluationRequest;
import com.processpuzzle.calculation.model.EvaluationResult;
import com.processpuzzle.calculation.model.RunRequest;
import com.processpuzzle.calculation.usecase.EvaluateCalculation;
import com.processpuzzle.calculation.usecase.FindCalculationDefinition;
import com.processpuzzle.calculation.usecase.FindCalculationRuns;
import com.processpuzzle.calculation.usecase.RunCalculation;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code CalculationsApi}: evaluating and running a calculation. */
@RestController
public class CalculationsEndpoint implements CalculationsApi {

    private final EvaluateCalculation evaluate;
    private final RunCalculation run;
    private final FindCalculationRuns runs;
    private final FindCalculationDefinition definitions;
    private final CalculationMapper mapper;

    public CalculationsEndpoint(EvaluateCalculation evaluate, RunCalculation run, FindCalculationRuns runs,
                              FindCalculationDefinition definitions, CalculationMapper mapper) {
        this.evaluate = evaluate;
        this.run = run;
        this.runs = runs;
        this.definitions = definitions;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<EvaluationResult> evaluateCalculation(String orgKey, String calculationId, EvaluationRequest request) {
        Channel channel = Channel.ofRevision(request.getRevision() == null ? null : request.getRevision().getValue());
        return ResponseEntity.ok(mapper.toModel(evaluate.evaluate(orgKey, calculationId, channel, request.getParameters(),
                request.getSource(), request.getUnsaved())));
    }

    @Override
    public ResponseEntity<CalculationRun> runCalculation(String orgKey, String calculationId, RunRequest request) {
        Channel channel = request.getRevision() == null ? Channel.RELEASED : Channel.ofRevision(request.getRevision().getValue());
        var stored = run.run(orgKey, calculationId, channel, request.getParameters(), RunTrigger.MANUAL);
        return new ResponseEntity<>(mapper.toModel(stored), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<List<CalculationRun>> listCalculationRunsOfCalculation(String orgKey, String calculationId) {
        return ResponseEntity.ok(runs.findAll(orgKey, calculationId, null).stream().map(mapper::toModel).toList());
    }
    @Override
    public ResponseEntity<List<CalculationOutput>> listCalculationOutputs(String orgKey, String entity) {
        return ResponseEntity.ok(definitions.storedOutputsOf(orgKey, entity).stream()
                .map(output -> new CalculationOutput().calculationId(output.calculationId()).attribute(output.attribute())
                        .decision(output.decision()).parameter(output.parameter()))
                .toList());
    }
}
