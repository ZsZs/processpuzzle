package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.api.CalculationRunsApi;
import com.processpuzzle.calculation.model.CalculationRun;
import com.processpuzzle.calculation.model.TraceEntry;
import com.processpuzzle.calculation.usecase.FindCalculationRuns;
import com.processpuzzle.calculation.usecase.FreezeRun;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code CalculationRunsApi}: reading runs and their traces, and freezing them. */
@RestController
public class CalculationRunsEndpoint implements CalculationRunsApi {

    private final FreezeRun freeze;
    private final FindCalculationRuns runs;
    private final CalculationMapper mapper;

    public CalculationRunsEndpoint(FreezeRun freeze, FindCalculationRuns runs, CalculationMapper mapper) {
        this.freeze = freeze;
        this.runs = runs;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<CalculationRun>> listCalculationRuns(String orgKey, String calculationId, String subjectId) {
        return ResponseEntity.ok(runs.findAll(orgKey, calculationId, subjectId).stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<CalculationRun> getCalculationRun(String orgKey, UUID runId) {
        return ResponseEntity.ok(mapper.toModel(runs.find(orgKey, runId)));
    }

    @Override
    public ResponseEntity<List<TraceEntry>> getCalculationRunTrace(String orgKey, UUID runId) {
        return ResponseEntity.ok(mapper.toTrace(runs.find(orgKey, runId)));
    }

    @Override
    public ResponseEntity<CalculationRun> freezeCalculationRun(String orgKey, UUID runId) {
        return ResponseEntity.ok(mapper.toModel(freeze.freeze(orgKey, runId)));
    }
}
