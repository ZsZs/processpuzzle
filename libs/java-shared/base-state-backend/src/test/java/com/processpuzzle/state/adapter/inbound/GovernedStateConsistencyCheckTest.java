package com.processpuzzle.state.adapter.inbound;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.port.EntityObjectGateway;
import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.state.usecase.service.EntityObjectGatewayResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What the check reports is a log line, so that is what these assert. The alternative — having it
 * return a result object nobody reads — would test a shape invented for the test.
 */
@ExtendWith(MockitoExtension.class)
class GovernedStateConsistencyCheckTest {

    private static final String ORG = "acme";
    private static final String ENTITY = "order";

    @Mock
    private StateMachineDefinitionRepository repository;
    @Mock
    private EntityObjectGatewayResolver gatewayResolver;
    @Mock
    private EntityObjectGateway gateway;

    private GovernedStateConsistencyCheck check;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        check = new GovernedStateConsistencyCheck(repository, gatewayResolver);
        logger = (Logger) LoggerFactory.getLogger(GovernedStateConsistencyCheck.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLogging() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private StateMachineDefinition machine() {
        return StateMachineDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .name("Order")
                .stateAttributeKey("status")
                .initialStateKey("DRAFT")
                .states(List.of(
                        new State("DRAFT", "Draft", null, false, false, null),
                        new State("SHIPPED", "Shipped", null, true, false, null)))
                .transitions(List.of())
                .build();
    }

    private void governedObjectsAre(List<EntityObjectSnapshot> objects) {
        when(repository.findAll()).thenReturn(List.of(machine()));
        when(gatewayResolver.gateway()).thenReturn(gateway);
        when(gateway.findObjects(ORG, ENTITY)).thenReturn(objects);
    }

    private EntityObjectSnapshot object(Map<String, Object> payload) {
        return new EntityObjectSnapshot(UUID.randomUUID(), 1L, payload);
    }

    private List<ILoggingEvent> loggedAt(Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level).toList();
    }

    @Test
    void saysNothingIsWrongWhenEveryObjectHoldsADeclaredState() {
        governedObjectsAre(List.of(
                object(Map.of("status", "DRAFT")),
                object(Map.of("status", "SHIPPED"))));

        check.verify();

        assertThat(loggedAt(Level.ERROR)).isEmpty();
        assertThat(loggedAt(Level.INFO)).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("Verified 2 object(s)"));
    }

    /**
     * The case the check exists for: seeding creates instances before any machine is imported, so an
     * instance that omits its state gets none — and {@code GetEntityObjectState} then answers with
     * the initial state, hiding the omission behind a plausible value.
     */
    @Test
    void reportsAnObjectWhoseStateAttributeIsAbsent() {
        UUID id = UUID.randomUUID();
        governedObjectsAre(List.of(new EntityObjectSnapshot(id, 1L, Map.of("total", 12))));

        check.verify();

        assertThat(loggedAt(Level.ERROR)).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains(id.toString());
            assertThat(event.getFormattedMessage()).contains("has no 'status'");
            assertThat(event.getFormattedMessage()).contains("DRAFT");
        });
        assertThat(loggedAt(Level.INFO)).isEmpty();
    }

    /** A blank string is an absent state wearing a value's clothes. */
    @Test
    void treatsABlankStateAttributeAsAbsent() {
        governedObjectsAre(List.of(object(Map.of("status", "   "))));

        check.verify();

        assertThat(loggedAt(Level.ERROR)).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("has no 'status'"));
    }

    @Test
    void reportsAnObjectHoldingAStateTheMachineDoesNotDeclare() {
        governedObjectsAre(List.of(object(Map.of("status", "TELEPORTED"))));

        check.verify();

        assertThat(loggedAt(Level.ERROR)).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("'status'='TELEPORTED'"));
    }

    /** A payload can be null over the port; a null-check bug here would turn a report into a crash. */
    @Test
    void survivesAnObjectWithNoPayloadAtAll() {
        governedObjectsAre(List.of(new EntityObjectSnapshot(UUID.randomUUID(), 1L, null)));

        assertThatCode(() -> check.verify()).doesNotThrowAnyException();
        assertThat(loggedAt(Level.ERROR)).hasSize(1);
    }

    /**
     * One line per machine, however many objects are wrong. Naming all of them would bury the log on
     * a real dataset, and naming none would leave nothing to act on.
     */
    @Test
    void namesTheFirstOffendersAndCountsTheRest() {
        List<EntityObjectSnapshot> objects = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            objects.add(object(new HashMap<>()));
        }
        governedObjectsAre(objects);

        check.verify();

        assertThat(loggedAt(Level.ERROR)).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).startsWith("13 of 13 'order' object(s)");
            assertThat(event.getFormattedMessage()).contains("(and 3 more)");
        });
    }

    /**
     * A missing gateway is base-state running without base-entity, which is a supported deployment.
     * It must cost a warning, not a failed startup, and must not stop the other machines.
     */
    @Test
    void warnsAndContinuesWhenTheObjectsOfAMachineCannotBeRead() {
        when(repository.findAll()).thenReturn(List.of(machine()));
        when(gatewayResolver.gateway()).thenReturn(gateway);
        when(gateway.findObjects(ORG, ENTITY))
                .thenThrow(new UnsupportedOperationException("no EntityObjectGateway is configured"));

        assertThatCode(() -> check.verify()).doesNotThrowAnyException();

        assertThat(loggedAt(Level.WARN)).hasSize(1);
        assertThat(loggedAt(Level.ERROR)).isEmpty();
    }

    /** The overwhelmingly common case — no machines at all — must not touch base-entity. */
    @Test
    void readsNoObjectsWhenNoStateMachineIsDefined() {
        lenient().when(repository.findAll()).thenReturn(List.of());

        check.verify();

        verifyNoInteractions(gatewayResolver);
        assertThat(appender.list).isEmpty();
    }
}
