package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.state.usecase.service.EntityObjectGatewayResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports, once at startup, any object whose state attribute disagrees with the machine that
 * governs it.
 *
 * <p><b>The divergence this exists to catch.</b> base-state stores no copy of an object's state —
 * {@code GetEntityObjectState} reads it out of base-entity's payload every time — so the two halves
 * cannot drift while both are working. They can start out of step, though, and the seed path is
 * where that happens: {@code DefaultEntityLoader} ({@code @Order(10)}) creates instances before
 * {@code DefaultStateImporter} ({@code @Order(20)}) has imported any machine, so the {@code
 * EntityObjectCreatedEvent} those instances publish finds nothing to start and no initial state is
 * written. Every seeded instance today sets its state explicitly, which is why this is currently
 * silent. One that forgot would be worse than silent: the payload would carry no state while
 * {@code GetEntityObjectState} falls back to {@code initialStateKey}, so base-entity would show the
 * field empty and base-state would report {@code DRAFT}, and nothing would say which was right.
 *
 * <p>The ordering cannot simply be swapped — {@code StateMachineTopologyValidator} needs the entity
 * definitions to exist before a machine naming them can be imported — so the check runs third,
 * after both loaders, when the full picture is available.
 *
 * <p><b>Reports, does not repair.</b> Writing an initial state onto whatever it finds would hide the
 * defect in the seed data rather than fix it, and would do so on every boot of every environment.
 * A failed check also does not stop startup: an inconsistent object is a data defect, and refusing
 * to serve the other 99% of the application because of it helps nobody. It logs at ERROR with the
 * offending ids so the seed file can be corrected.
 *
 * <p>Set {@code base-state.verifyGovernedObjectStates=false} to skip it. Worth doing where a
 * governed entity type holds enough rows that reading all of them at boot costs real time — this is
 * a whole-table read per machine.
 */
@Component
@ConditionalOnProperty(prefix = "base-state", name = "verifyGovernedObjectStates",
        havingValue = "true", matchIfMissing = true)
class GovernedStateConsistencyCheck {

    private static final Logger LOG = LoggerFactory.getLogger(GovernedStateConsistencyCheck.class);

    /** Enough to recognize the pattern in a seed file; the count tells the true scale. */
    private static final int MAX_REPORTED_PER_MACHINE = 10;

    private final StateMachineDefinitionRepository repository;
    private final EntityObjectGatewayResolver gatewayResolver;

    GovernedStateConsistencyCheck(StateMachineDefinitionRepository repository,
                                  EntityObjectGatewayResolver gatewayResolver) {
        this.repository = repository;
        this.gatewayResolver = gatewayResolver;
    }

    @Order(30)
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void verify() {
        List<StateMachineDefinition> definitions = repository.findAll();
        if (definitions.isEmpty()) {
            return;
        }

        int checked = 0;
        int inconsistent = 0;
        for (StateMachineDefinition definition : definitions) {
            List<EntityObjectSnapshot> objects = objectsOfOrEmpty(definition);
            checked += objects.size();
            inconsistent += report(definition, objects);
        }

        if (inconsistent == 0) {
            LOG.info("Verified {} object(s) against {} state machine(s); every state attribute holds "
                    + "a declared state.", checked, definitions.size());
        }
    }

    /** @return how many of {@code objects} disagree with the machine governing them */
    private int report(StateMachineDefinition definition, List<EntityObjectSnapshot> objects) {
        String attributeKey = definition.getStateAttributeKey();
        Set<String> declaredStates = definition.getStates().stream()
                .map(State::key)
                .collect(Collectors.toSet());

        List<String> problems = new ArrayList<>();
        for (EntityObjectSnapshot object : objects) {
            Object raw = object.payload() == null ? null : object.payload().get(attributeKey);
            if (raw == null || raw.toString().isBlank()) {
                problems.add("%s has no '%s'".formatted(object.id(), attributeKey));
            } else if (!declaredStates.contains(raw.toString())) {
                problems.add("%s has '%s'='%s'".formatted(object.id(), attributeKey, raw));
            }
        }

        if (!problems.isEmpty()) {
            LOG.error("{} of {} '{}' object(s) in organization '{}' disagree with the state machine "
                            + "governing them: their '{}' is absent or is not one of {}. base-entity will "
                            + "show the attribute as it is while base-state reports the initial state "
                            + "'{}', and nothing downstream can tell which is meant. Offenders: {}{}",
                    problems.size(), objects.size(), definition.getEntityName(), definition.getOrgKey(),
                    attributeKey, declaredStates.stream().sorted().toList(),
                    definition.getInitialStateKey(),
                    problems.stream().limit(MAX_REPORTED_PER_MACHINE).collect(Collectors.joining(", ")),
                    problems.size() > MAX_REPORTED_PER_MACHINE
                            ? " (and %d more)".formatted(problems.size() - MAX_REPORTED_PER_MACHINE) : "");
        }
        return problems.size();
    }

    /** Empty rather than fatal: no gateway wired, or an unreadable entity type, is not this check's
     *  business to resolve, and must not stop the remaining machines from being checked. */
    private List<EntityObjectSnapshot> objectsOfOrEmpty(StateMachineDefinition definition) {
        try {
            return objectsOf(definition);
        } catch (RuntimeException e) {
            LOG.warn("Could not read the objects of '{}' in organization '{}' to verify their state "
                            + "attribute; skipping that machine.",
                    definition.getEntityName(), definition.getOrgKey(), e);
            return List.of();
        }
    }

    private List<EntityObjectSnapshot> objectsOf(StateMachineDefinition definition) {
        return gatewayResolver.gateway().findObjects(definition.getOrgKey(), definition.getEntityName());
    }
}
