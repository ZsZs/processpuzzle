package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.CalculationRunRepository;
import com.processpuzzle.calculation.usecase.exception.RunNotFoundException;
import com.processpuzzle.calculation.usecase.exception.RunNotFreezableException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Makes a completed run's results immutable; its subject is no longer recalculated. Idempotent. */
@Service
@Transactional
public class FreezeRun {

    private final CalculationRunRepository runs;
    private final RunEvents events;

    public FreezeRun(CalculationRunRepository runs, RunEvents events) {
        this.runs = runs;
        this.events = events;
    }

    public CalculationRun freeze(String orgKey, UUID runId) {
        CalculationRun run = runs.findByIdAndOrgKey(runId, orgKey).orElseThrow(() -> new RunNotFoundException(orgKey, runId));
        try {
            if (run.freeze(Instant.now())) {
                events.frozen(run);
            }
        } catch (IllegalStateException e) {
            throw new RunNotFreezableException(e.getMessage());
        }
        return runs.save(run);
    }
}
