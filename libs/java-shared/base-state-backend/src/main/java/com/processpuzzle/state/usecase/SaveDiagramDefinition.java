package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.DiagramDefinition;
import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates or replaces the diagram layout of one entity type's state machine — an upsert, unlike
 * the {@link CreateStateMachineDefinition} / {@link UpdateStateMachineDefinition} pair next to it.
 *
 * <p>The asymmetry is deliberate. A second state machine for the same {@code entityName} is a
 * genuine user error worth conflicting on; saving a layout a second time is the normal case, and
 * the modeler's "arrange" gesture cannot reasonably be asked to know whether this machine has ever
 * been arranged before. The client always supplies {@code entityName}, so nothing is
 * server-assigned and an upsert costs no round trip and has no first-save race.
 *
 * <p>A state machine must exist for {@code entityName}: a layout for a machine that does not exist
 * has nothing to lay out, and would be unreachable anyway. Individual {@code stateKey} and
 * {@code transitionKey} values are <em>not</em> validated against it, though —
 * {@link UpdateStateMachineDefinition} is free to drop a state, a layout row naming a key the
 * machine no longer declares is harmless (the modeler ignores what it cannot render, and the next
 * save prunes it), and rejecting it would make two independent, individually-valid saves fail
 * depending only on the order they arrive in.
 *
 * <p>Optimistic locking is Hibernate's own {@code @Version} column on the loaded entity, the same
 * shape as {@link UpdateStateMachineDefinition}.
 */
@Service
@Transactional
public class SaveDiagramDefinition {

    private final DiagramDefinitionRepository repository;
    private final StateMachineDefinitionRepository stateMachineRepository;

    public SaveDiagramDefinition(DiagramDefinitionRepository repository,
                                 StateMachineDefinitionRepository stateMachineRepository) {
        this.repository = repository;
        this.stateMachineRepository = stateMachineRepository;
    }

    /**
     * @param definition the layout to persist; its {@code orgKey}/{@code entityName} come from the
     *                   request path, never from the request body
     * @return the persisted layout, and whether this call created it — which is what lets the
     *         endpoint answer {@code 201} the first time and {@code 200} thereafter
     */
    public Result execute(DiagramDefinition definition) {
        String orgKey = definition.getOrgKey();
        String entityName = definition.getEntityName();
        if (!stateMachineRepository.existsByOrgKeyAndEntityName(orgKey, entityName)) {
            throw new StateMachineNotFoundException(orgKey, entityName);
        }

        Optional<DiagramDefinition> existing = repository.findByOrgKeyAndEntityName(orgKey, entityName);
        if (existing.isEmpty()) {
            return new Result(repository.save(definition), true);
        }

        DiagramDefinition target = existing.get();
        target.replaceLayout(definition.getNodes(), definition.getEdges(), definition.getViewport());
        return new Result(repository.save(target), false);
    }

    /** @param created {@code true} when this save inserted the layout rather than replacing one. */
    public record Result(DiagramDefinition definition, boolean created) {
    }
}
