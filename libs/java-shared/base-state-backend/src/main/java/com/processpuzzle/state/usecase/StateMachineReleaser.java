package com.processpuzzle.state.usecase;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.state.domain.DiagramDefinition;
import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
        entityNames.forEach(entityName -> release(orgKey, entityName));
    }

    /** Releases one entity type's machine and layout. */
    @Transactional
    public void release(String orgKey, String entityName) {
        releaseMachine(orgKey, entityName);
        releaseDiagram(orgKey, entityName);
    }

    private void releaseMachine(String orgKey, String entityName) {
        Optional<StateMachineDefinition> draft = machines.findByOrgKeyAndEntityNameAndChannel(orgKey, entityName, Channel.DRAFT);
        Optional<StateMachineDefinition> released =
                machines.findByOrgKeyAndEntityNameAndChannel(orgKey, entityName, Channel.RELEASED);
        if (draft.isEmpty()) {
            released.ifPresent(machines::delete);
        } else if (released.isEmpty()) {
            machines.save(StateMachineDefinition.releasedCopyOf(draft.get()));
        } else {
            released.get().releaseFrom(draft.get());
            machines.save(released.get());
        }
    }

    private void releaseDiagram(String orgKey, String entityName) {
        Optional<DiagramDefinition> draft = diagrams.findByOrgKeyAndEntityNameAndChannel(orgKey, entityName, Channel.DRAFT);
        Optional<DiagramDefinition> released = diagrams.findByOrgKeyAndEntityNameAndChannel(orgKey, entityName, Channel.RELEASED);
        if (draft.isEmpty()) {
            released.ifPresent(diagrams::delete);
        } else if (released.isEmpty()) {
            diagrams.save(DiagramDefinition.releasedCopyOf(draft.get()));
        } else {
            released.get().releaseFrom(draft.get());
            diagrams.save(released.get());
        }
    }
}
