package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.CalculationRunRepository;
import com.processpuzzle.calculation.usecase.exception.RunNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindCalculationRuns {

    private final CalculationRunRepository runs;

    public FindCalculationRuns(CalculationRunRepository runs) {
        this.runs = runs;
    }

    public CalculationRun find(String orgKey, UUID runId) {
        return runs.findByIdAndOrgKey(runId, orgKey).orElseThrow(() -> new RunNotFoundException(orgKey, runId));
    }

    /** Newest first; either filter may be null. */
    public List<CalculationRun> findAll(String orgKey, String calculationId, String subjectId) {
        if (calculationId != null && subjectId != null) {
            return runs.findByOrgKeyAndCalculationIdAndSubjectIdOrderByStartedAtDesc(orgKey, calculationId, subjectId);
        }
        if (calculationId != null) {
            return runs.findByOrgKeyAndCalculationIdOrderByStartedAtDesc(orgKey, calculationId);
        }
        if (subjectId != null) {
            return runs.findByOrgKeyAndSubjectIdOrderByStartedAtDesc(orgKey, subjectId);
        }
        return runs.findByOrgKeyOrderByStartedAtDesc(orgKey);
    }
}
