package com.processpuzzle.event.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.event.domain.EventParameter;
import com.processpuzzle.shared.event.DefinedEventOccurred;
import com.processpuzzle.shared.event.EventParameterType;
import com.processpuzzle.shared.event.OccurredParameter;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class PlatformEventCatalogListenerTest {

    private static final String ORG = "org-1";
    private static final Instant WHEN = Instant.parse("2026-10-08T10:00:00Z");

    private EventDefinitionRepository repository;
    private ApplicationEventPublisher publisher;
    private PlatformEventCatalogListener listener;

    @BeforeEach
    void setUp() {
        repository = mock(EventDefinitionRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        listener = new PlatformEventCatalogListener(repository, publisher);
    }

    private static EventDefinition definition(String id, PlatformEventAction action, String state) {
        return EventDefinition.builder()
                .orgKey(ORG).id(id).name(id).kind(EventKind.SYSTEM)
                .subjectType("order").action(action).state(state)
                .build();
    }

    private List<DefinedEventOccurred> published() {
        ArgumentCaptor<DefinedEventOccurred> captor = ArgumentCaptor.forClass(DefinedEventOccurred.class);
        verify(publisher, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void republishesAMatchingDefinitionWithTheFactsSubjectAndPayload() {
        when(repository.findByOrgKeyAndChannelAndKindAndSubjectTypeAndAction(ORG, Channel.RELEASED, EventKind.SYSTEM, "order", PlatformEventAction.CREATED))
                .thenReturn(List.of(definition("OrderCreatedEvent", PlatformEventAction.CREATED, null)));

        listener.on(PlatformEvent.of(ORG, "order", "42", PlatformEventAction.CREATED, Map.of("customer", "ACME"), WHEN));

        assertThat(published()).containsExactly(new DefinedEventOccurred(
                ORG, "OrderCreatedEvent", "order", "42", Map.of("customer", "ACME"), WHEN,
                List.of(new OccurredParameter(OccurredParameter.SUBJECT, EventParameterType.ENTITY_REF, "order", "42"))));
    }

    // The declared parameters come from the payload attribute of the same name; one the fact lacks is null.
    @Test
    void carriesTheDeclaredParametersFromThePayload() {
        EventDefinition created = definition("OrderCreatedEvent", PlatformEventAction.CREATED, null);
        created.setParameters(List.of(
                EventParameter.builder().name("orderNumber").type(EventParameterType.STRING).build(),
                EventParameter.builder().name("customerName").type(EventParameterType.STRING).build()));
        when(repository.findByOrgKeyAndChannelAndKindAndSubjectTypeAndAction(ORG, Channel.RELEASED, EventKind.SYSTEM, "order", PlatformEventAction.CREATED))
                .thenReturn(List.of(created));

        listener.on(PlatformEvent.of(ORG, "order", "42", PlatformEventAction.CREATED, Map.of("orderNumber", "O-1"), WHEN));

        assertThat(published().getFirst().parameters()).extracting(OccurredParameter::name, OccurredParameter::value)
                .containsExactly(tuple(OccurredParameter.SUBJECT, "42"), tuple("orderNumber", "O-1"), tuple("customerName", null));
    }

    @Test
    void appliesTheStateFilterAndPublishesOncePerMatchingDefinition() {
        when(repository.findByOrgKeyAndChannelAndKindAndSubjectTypeAndAction(ORG, Channel.RELEASED, EventKind.SYSTEM, "order", PlatformEventAction.STATE_CHANGED))
                .thenReturn(List.of(
                        definition("OrderConfirmedEvent", PlatformEventAction.STATE_CHANGED, "CONFIRMED"),
                        definition("OrderDeliveredEvent", PlatformEventAction.STATE_CHANGED, "DELIVERED"),
                        definition("OrderStateChanged", PlatformEventAction.STATE_CHANGED, null)));

        listener.on(PlatformEvent.stateChanged(ORG, "order", "42", "CONFIRMED", Map.of(), WHEN));

        assertThat(published()).extracting(DefinedEventOccurred::eventDefinitionId)
                .containsExactly("OrderConfirmedEvent", "OrderStateChanged");
    }

    @Test
    void aFactNoDefinitionNamesIsDropped() {
        when(repository.findByOrgKeyAndChannelAndKindAndSubjectTypeAndAction(ORG, Channel.RELEASED, EventKind.SYSTEM, "partner", PlatformEventAction.DELETED))
                .thenReturn(List.of());

        listener.on(PlatformEvent.of(ORG, "partner", "7", PlatformEventAction.DELETED, null, WHEN));

        verifyNoInteractions(publisher);
    }

    @Test
    void anotherOrganizationsDefinitionDoesNotMatch() {
        EventDefinition foreign = definition("OrderCreatedEvent", PlatformEventAction.CREATED, null);
        foreign.setOrgKey("org-2");
        when(repository.findByOrgKeyAndChannelAndKindAndSubjectTypeAndAction(ORG, Channel.RELEASED, EventKind.SYSTEM, "order", PlatformEventAction.CREATED))
                .thenReturn(List.of(foreign));

        listener.on(PlatformEvent.of(ORG, "order", "42", PlatformEventAction.CREATED, Map.of(), WHEN));

        verifyNoInteractions(publisher);
    }
}
