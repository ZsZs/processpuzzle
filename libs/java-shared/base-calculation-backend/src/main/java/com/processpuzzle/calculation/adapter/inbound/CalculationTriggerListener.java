package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.RunStatus;
import com.processpuzzle.calculation.domain.RunTrigger;
import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.StateRef;
import com.processpuzzle.calculation.domain.definition.TriggerSpec;
import com.processpuzzle.calculation.usecase.FindCalculationDefinition;
import com.processpuzzle.calculation.usecase.FreezeRun;
import com.processpuzzle.calculation.usecase.RunCalculation;
import com.processpuzzle.calculation.usecase.RunEvents;
import com.processpuzzle.calculation.usecase.exception.SubjectFrozenException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the released calculations a platform fact triggers. The fact's object is bound to the calculation's
 * parameter of its entity:
 * <ul>
 *   <li>{@code onChange: { entity }} — the object was created or updated;</li>
 *   <li>{@code state: { entity, to }} — the object entered the state;</li>
 *   <li>{@code freeze: { state: { entity, to } }} — the object entered the freeze state: computed once more, and
 *       frozen.</li>
 * </ul>
 * A subject whose result is frozen is skipped.
 *
 * <p>The output write publishes no change event, so a calculation cannot trigger itself. After commit and in a
 * transaction of its own, the same shape as base-event's catalog listener. A run that fails is recorded as such and
 * not rethrown — the registry would redeliver it forever — and one calculation failing does not keep the others
 * from running.
 */
@Component
class CalculationTriggerListener {

    private static final Logger LOG = LoggerFactory.getLogger(CalculationTriggerListener.class);

    private final FindCalculationDefinition definitions;
    private final RunCalculation runCalculation;
    private final FreezeRun freezeRun;

    CalculationTriggerListener(FindCalculationDefinition definitions, RunCalculation runCalculation, FreezeRun freezeRun) {
        this.definitions = definitions;
        this.runCalculation = runCalculation;
        this.freezeRun = freezeRun;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PlatformEvent fact) {
        if (RunEvents.SUBJECT_TYPE.equals(fact.subjectType()) || fact.action() == PlatformEventAction.DELETED) {
            return;
        }
        for (CalculationDefinition definition : definitions.findAll(fact.orgKey(), Channel.RELEASED)) {
            try {
                trigger(fact, definition);
            } catch (RuntimeException e) {
                LOG.warn("Calculation '{}' in '{}' could not run on {} {}/{}: {}", definition.getId(), fact.orgKey(),
                        fact.action(), fact.subjectType(), fact.subjectId(), e.getMessage());
            }
        }
    }

    private void trigger(PlatformEvent fact, CalculationDefinition definition) {
        CalculationModel model = definition.parsedModel();
        TriggerSpec trigger = model.trigger();
        if (trigger == null) {
            return;
        }
        List<String> bound = model.parametersOfEntity(fact.subjectType());
        RunTrigger kind = kindOf(trigger, fact);
        if (kind == null || bound.isEmpty()) {
            return;
        }
        try {
            CalculationRun run = runCalculation.run(fact.orgKey(), definition.getId(), Channel.RELEASED,
                    Map.of(bound.getFirst(), fact.subjectId()), kind);
            if (kind == RunTrigger.FREEZE_STATE && run.getStatus() == RunStatus.COMPLETED) {
                freezeRun.freeze(fact.orgKey(), run.getId());
            }
        } catch (SubjectFrozenException e) {
            LOG.debug("{}", e.getMessage());
        }
    }

    /** What the fact is to this trigger — the freeze state first, since it also recomputes — or null. */
    static RunTrigger kindOf(TriggerSpec trigger, PlatformEvent fact) {
        if (trigger.freeze() != null && entered(trigger.freeze().state(), fact)) {
            return RunTrigger.FREEZE_STATE;
        }
        if (entered(trigger.state(), fact)) {
            return RunTrigger.STATE;
        }
        boolean changed = fact.action() == PlatformEventAction.CREATED || fact.action() == PlatformEventAction.UPDATED;
        if (changed && trigger.onChange() != null && fact.subjectType().equals(trigger.onChange().entity())) {
            return RunTrigger.ON_CHANGE;
        }
        return null;
    }

    private static boolean entered(StateRef state, PlatformEvent fact) {
        return state != null && fact.action() == PlatformEventAction.STATE_CHANGED
                && fact.subjectType().equals(state.entity()) && state.to() != null && state.to().equals(fact.state());
    }
}
