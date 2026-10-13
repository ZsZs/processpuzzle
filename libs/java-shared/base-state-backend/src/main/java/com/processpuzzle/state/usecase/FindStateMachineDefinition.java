package com.processpuzzle.state.usecase;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindStateMachineDefinition {

    private final StateMachineDefinitionRepository repository;

    public FindStateMachineDefinition(StateMachineDefinitionRepository repository) {
        this.repository = repository;
    }

    /** @throws StateMachineNotFoundException when the entity type has no machine in that channel */
    public StateMachineDefinition execute(String orgKey, String entityName, Channel channel) {
        return find(orgKey, entityName, channel).orElseThrow(() -> new StateMachineNotFoundException(orgKey, entityName));
    }

    /**
     * The machine, or nothing when the entity type has none — for a caller to whom that is an answer, not an error.
     *
     * <p>Such a caller must ask this rather than catch {@link #execute}'s exception: this class is transactional, and
     * an exception leaving it marks the caller's transaction rollback-only even when the caller catches it. Saving an
     * object of a type no state machine governs then fails at commit with an {@code UnexpectedRollbackException}.
     */
    public Optional<StateMachineDefinition> find(String orgKey, String entityName, Channel channel) {
        return repository.findByOrgKeyAndEntityNameAndChannel(orgKey, entityName, channel);
    }
}
