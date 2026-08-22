package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.domain.event.EntityObjectStateChangedEvent;
import com.processpuzzle.state.usecase.service.EntityObjectGatewayResolver;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Writes a newly created object's initial state — the one write to a state attribute that is not a
 * transition, and therefore the one thing {@link FireStateTransition} cannot do. Everything after
 * this goes through that use case.
 *
 * <p>Three outcomes, all of them normal:
 * <ol>
 *   <li>no state machine governs the entity type — the overwhelmingly common case, since most
 *       entity types have none. Returns empty, writes nothing, logs nothing.</li>
 *   <li>the creating payload already carried a value at {@code stateAttributeKey} — the caller
 *       stated the state explicitly, e.g. an import restoring objects mid-lifecycle. It is left
 *       alone: overwriting it with the initial state would silently discard the caller's intent,
 *       and rejecting it would make base-state able to fail a creation it is only observing.</li>
 *   <li>otherwise the definition's {@code initialStateKey} is written and {@link
 *       EntityObjectStateChangedEvent} published with a null {@code previousStateKey}, which is
 *       what distinguishes a machine starting from a machine advancing.</li>
 * </ol>
 *
 * <p>No {@code transitionKey} or {@code triggerKey} either: there was no transition. A consumer
 * that keys off those has to handle their absence, and that is the honest shape — the object did
 * not get here by being triggered.
 */
@Service
@Transactional
public class StartStateMachine {

    private final StateMachineDefinitionRepository repository;
    private final EntityObjectGatewayResolver gatewayResolver;
    private final ApplicationEventPublisher eventPublisher;

    public StartStateMachine(StateMachineDefinitionRepository repository,
                             EntityObjectGatewayResolver gatewayResolver,
                             ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.gatewayResolver = gatewayResolver;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param payload the object's payload as created, read only to see whether the state attribute
     *                is already set — not re-read through the gateway, because the creating
     *                transaction has committed and this is the value it committed
     * @return the state written, or empty when nothing was written (cases 1 and 2 above)
     */
    public Optional<String> execute(String orgKey, String entityName, UUID objectId,
                                    Map<String, Object> payload, long version) {
        Optional<StateMachineDefinition> found = repository.findByOrgKeyAndEntityName(orgKey, entityName);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        StateMachineDefinition definition = found.get();

        Object existing = payload == null ? null : payload.get(definition.getStateAttributeKey());
        if (existing != null && !existing.toString().isBlank()) {
            return Optional.empty();
        }

        String initialStateKey = definition.getInitialStateKey();
        long newVersion = gatewayResolver.gateway().updateStateAttribute(
                orgKey, entityName, objectId, definition.getStateAttributeKey(), initialStateKey, version);

        eventPublisher.publishEvent(new EntityObjectStateChangedEvent(
                orgKey, entityName, objectId, null, initialStateKey, null, null, newVersion, Instant.now()));
        return Optional.of(initialStateKey);
    }
}
