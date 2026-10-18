package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.RunStatus;
import com.processpuzzle.calculation.domain.RunTrigger;
import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.StateRef;
import com.processpuzzle.calculation.port.SubjectStatePort;
import com.processpuzzle.calculation.usecase.exception.CalculationNotFoundException;
import com.processpuzzle.core.definition.Channel;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Freezes what a released calculation would have frozen had it been released earlier: every object already in its
 * freeze state without a frozen result is computed once and frozen — an order seeded as confirmed, or confirmed
 * before its calculation existed. The state trigger cannot do it, since the object will not enter that state again.
 *
 * <p>Each subject runs and fails on its own, like a trigger's run, and nothing here fails the release.
 */
@Service
public class CatchUpFreezes {

    private static final Logger LOG = LoggerFactory.getLogger(CatchUpFreezes.class);

    private final FindCalculationDefinition definitions;
    private final RunCalculation runCalculation;
    private final FreezeRun freezeRun;
    private final ObjectProvider<SubjectStatePort> states;

    public CatchUpFreezes(FindCalculationDefinition definitions, RunCalculation runCalculation, FreezeRun freezeRun,
                          ObjectProvider<SubjectStatePort> states) {
        this.definitions = definitions;
        this.runCalculation = runCalculation;
        this.freezeRun = freezeRun;
        this.states = states;
    }

    /** @return how many subjects were frozen */
    public int execute(String orgKey, Collection<String> calculationIds) {
        SubjectStatePort statePort = states.getIfAvailable();
        if (statePort == null) {
            return 0;
        }
        int frozen = 0;
        for (String id : calculationIds) {
            try {
                frozen += catchUp(orgKey, definitions.find(orgKey, id, Channel.RELEASED), statePort);
            } catch (CalculationNotFoundException e) {
                LOG.debug("{}", e.getMessage());
            }
        }
        return frozen;
    }

    private int catchUp(String orgKey, CalculationDefinition definition, SubjectStatePort statePort) {
        CalculationModel model = definition.parsedModel();
        StateRef freezeState = model.trigger() == null || model.trigger().freeze() == null ? null : model.trigger().freeze().state();
        List<String> bound = freezeState == null ? List.of() : model.parametersOfEntity(freezeState.entity());
        if (bound.isEmpty() || freezeState.to() == null) {
            return 0;
        }
        List<String> inState;
        try {
            inState = statePort.objectsInState(orgKey, freezeState.entity(), freezeState.to());
        } catch (RuntimeException e) {
            LOG.warn("Calculation '{}' in '{}' could not find the objects in state {}: {}", definition.getId(), orgKey,
                    freezeState.to(), e.getMessage());
            return 0;
        }
        int frozen = 0;
        for (String subjectId : inState) {
            if (!runCalculation.isFrozen(orgKey, definition.getId(), subjectId) && freeze(orgKey, definition, bound.getFirst(), subjectId)) {
                frozen++;
            }
        }
        return frozen;
    }

    private boolean freeze(String orgKey, CalculationDefinition definition, String parameter, String subjectId) {
        try {
            CalculationRun run = runCalculation.run(orgKey, definition.getId(), Channel.RELEASED, Map.of(parameter, subjectId),
                    RunTrigger.FREEZE_STATE);
            if (run.getStatus() != RunStatus.COMPLETED) {
                return false;
            }
            freezeRun.freeze(orgKey, run.getId());
            return true;
        } catch (RuntimeException e) {
            LOG.warn("Calculation '{}' in '{}' could not catch up the freeze of {}: {}", definition.getId(), orgKey, subjectId,
                    e.getMessage());
            return false;
        }
    }
}
