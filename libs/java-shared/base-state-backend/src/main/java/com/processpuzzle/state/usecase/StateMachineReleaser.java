package com.processpuzzle.state.usecase;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.state.domain.DiagramDefinition;
import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes a state machine's draft — the machine and the layout drawn for it — what the running application reads.
 *
 * <p>The machine and its layout are released together, as one definition: the live application draws the layout of
 * the machine it runs, and a released machine beside a draft layout would place states that are not there. Each is
 * copied onto its released row, or that row deleted when its draft is gone.
 *
 * <p>Used by the release of an organization, for every machine at once, and by the default importer, for the machines
 * it seeds — and only those, so that a designer's unreleased drafts in the same organization stay drafts.
 */
@Component
public class StateMachineReleaser {

    private final StateMachineDefinitionRepository machines;
    private final DiagramDefinitionRepository diagrams;

    public StateMachineReleaser(StateMachineDefinitionRepository machines, DiagramDefinitionRepository diagrams) {
        this.machines = machines;
        this.diagrams = diagrams;
    }

    /** Releases every machine of the organization, and removes the released ones whose draft is gone. */
    @Transactional
    public void releaseAll(String orgKey) {
        Set<String> entityNames = new TreeSet<>();
        machines.findByOrgKey(orgKey).forEach(machine -> entityNames.add(machine.getEntityName()));
        diagrams.findByOrgKey(orgKey).forEach(diagram -> entityNames.add(diagram.getEntityName()));
        entityNames.forEach(entityName -> releaseOne(orgKey, entityName));
    }

    /** Releases one entity type's machine and layout. */
    @Transactional
    public void release(String orgKey, String entityName) {
        releaseOne(orgKey, entityName);
    }

    private void releaseOne(String orgKey, String entityName) {
        releaseRow(machines.findByOrgKeyAndEntityNameAndChannel(orgKey, entityName, Channel.DRAFT).orElse(null),
                machines.findByOrgKeyAndEntityNameAndChannel(orgKey, entityName, Channel.RELEASED).orElse(null),
                StateMachineDefinition::releasedCopyOf, StateMachineDefinition::releaseFrom, machines);
        releaseRow(diagrams.findByOrgKeyAndEntityNameAndChannel(orgKey, entityName, Channel.DRAFT).orElse(null),
                diagrams.findByOrgKeyAndEntityNameAndChannel(orgKey, entityName, Channel.RELEASED).orElse(null),
                DiagramDefinition::releasedCopyOf, DiagramDefinition::releaseFrom, diagrams);
    }

    /**
     * Copies the draft onto the released row, creating it the first time, or deletes it once the draft is gone.
     * Either may be {@code null}: no draft, or nothing released yet.
     */
    private static <T> void releaseRow(T draft, T released, UnaryOperator<T> copyOf, BiConsumer<T, T> releaseFrom,
                                       CrudRepository<T, ?> repository) {
        if (draft == null) {
            if (released != null) {
                repository.delete(released);
            }
        } else if (released == null) {
            repository.save(copyOf.apply(draft));
        } else {
            releaseFrom.accept(released, draft);
            repository.save(released);
        }
    }
}
