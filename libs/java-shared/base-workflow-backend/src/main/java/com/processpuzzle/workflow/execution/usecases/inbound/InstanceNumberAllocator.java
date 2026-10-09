package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.execution.domain.WorkflowInstanceCounter;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceCounterRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Draws the next instance number of an organization, inside the transaction that creates the instance —
 * {@code MANDATORY}, so the counter's row lock is held until the instance is committed and a rolled-back
 * start gives its number back.
 *
 * <p>An organization's first counter is seeded from the highest number already in use rather than from
 * zero, so a lost or reset counter row cannot hand out a duplicate. Two <em>first-ever</em> starts of an
 * organization racing each other both insert the row, and the loser fails on its key and is not started;
 * every start after that serializes on the lock.
 */
@Component
public class InstanceNumberAllocator {

    private final WorkflowInstanceCounterRepository counters;
    private final WorkflowInstanceRepository instances;

    public InstanceNumberAllocator(WorkflowInstanceCounterRepository counters, WorkflowInstanceRepository instances) {
        this.counters = counters;
        this.instances = instances;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String orgKey) {
        WorkflowInstanceCounter counter = counters.findByOrgKey(orgKey)
                .orElseGet(() -> counters.saveAndFlush(
                        new WorkflowInstanceCounter(orgKey, instances.findMaxInstanceNumber(orgKey).orElse(0L))));
        counter.setLastNumber(counter.getLastNumber() + 1);
        return counter.getLastNumber();
    }
}
