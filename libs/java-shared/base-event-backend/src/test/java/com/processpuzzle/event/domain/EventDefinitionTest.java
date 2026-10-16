package com.processpuzzle.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.shared.event.EventParameterType;
import com.processpuzzle.shared.event.OccurredParameter;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventDefinitionTest {

    private static final String ORG = "org-1";

    static EventDefinition system(String id, PlatformEventAction action, String state) {
        return EventDefinition.builder()
                .orgKey(ORG).id(id).name(id).kind(EventKind.SYSTEM)
                .subjectType("order").action(action).state(state)
                .build();
    }

    private static PlatformEvent fact(String orgKey, String subjectType, PlatformEventAction action, String state) {
        return new PlatformEvent(orgKey, subjectType, "42", action, state, Map.of(), Instant.now());
    }

    @Test
    void aCompleteSystemDefinitionIsValid() {
        assertThat(system("OrderCreatedEvent", PlatformEventAction.CREATED, null).validate()).isEmpty();
        assertThat(system("OrderConfirmedEvent", PlatformEventAction.STATE_CHANGED, "CONFIRMED").validate()).isEmpty();
    }

    @Test
    void requiresIdNameAndKind() {
        assertThat(new EventDefinition().validate())
                .containsExactly("'id' is required", "'name' is required", "'kind' is required");
    }

    @Test
    void aSystemDefinitionNeedsSubjectTypeAndAction() {
        EventDefinition definition = EventDefinition.builder().id("x").name("x").kind(EventKind.SYSTEM).build();

        assertThat(definition.validate())
                .containsExactly("a SYSTEM event needs a 'subjectType'", "a SYSTEM event needs an 'action'");
    }

    @Test
    void stateIsOnlyMeaningfulWithStateChanged() {
        assertThat(system("x", PlatformEventAction.CREATED, "CONFIRMED").validate())
                .containsExactly("'state' is only meaningful with action STATE_CHANGED");
    }

    @Test
    void messageAndSignalCarryNoBinding() {
        EventDefinition bare = EventDefinition.builder().id("x").name("x").kind(EventKind.MESSAGE).build();
        EventDefinition bound = EventDefinition.builder().id("x").name("x").kind(EventKind.SIGNAL).subjectType("order").build();

        assertThat(bare.validate()).isEmpty();
        assertThat(bound.validate()).singleElement().asString().contains("SIGNAL event carries no binding");
    }

    @Test
    void matchesOnOrganizationSubjectAndAction() {
        EventDefinition created = system("OrderCreatedEvent", PlatformEventAction.CREATED, null);

        assertThat(created.matches(fact(ORG, "order", PlatformEventAction.CREATED, null))).isTrue();
        assertThat(created.matches(fact("org-2", "order", PlatformEventAction.CREATED, null))).isFalse();
        assertThat(created.matches(fact(ORG, "partner", PlatformEventAction.CREATED, null))).isFalse();
        assertThat(created.matches(fact(ORG, "order", PlatformEventAction.UPDATED, null))).isFalse();
        assertThat(created.matches(null)).isFalse();
    }

    @Test
    void aStateFilterMatchesThatStateOnlyAndNoFilterMatchesEveryState() {
        EventDefinition confirmed = system("OrderConfirmedEvent", PlatformEventAction.STATE_CHANGED, "CONFIRMED");
        EventDefinition anyState = system("OrderStateChanged", PlatformEventAction.STATE_CHANGED, null);

        assertThat(confirmed.matches(fact(ORG, "order", PlatformEventAction.STATE_CHANGED, "CONFIRMED"))).isTrue();
        assertThat(confirmed.matches(fact(ORG, "order", PlatformEventAction.STATE_CHANGED, "SHIPPED"))).isFalse();
        assertThat(anyState.matches(fact(ORG, "order", PlatformEventAction.STATE_CHANGED, "SHIPPED"))).isTrue();
    }

    @Test
    void nonSystemDefinitionsMatchNothing() {
        EventDefinition message = EventDefinition.builder().orgKey(ORG).id("x").name("x").kind(EventKind.MESSAGE).build();

        assertThat(message.matches(fact(ORG, "order", PlatformEventAction.CREATED, null))).isFalse();
    }

    @Test
    void replaceWithCopiesTheEditableFieldsOnly() {
        EventDefinition target = system("OrderCreatedEvent", PlatformEventAction.CREATED, null);
        target.setVersion(3L);
        EventDefinition source = EventDefinition.builder()
                .orgKey("other").id("other").name("Renamed").description("d").kind(EventKind.SYSTEM)
                .subjectType("invoice").action(PlatformEventAction.STATE_CHANGED).state("PAID").version(9L)
                .build();

        target.replaceWith(source);

        assertThat(target.getOrgKey()).isEqualTo(ORG);
        assertThat(target.getId()).isEqualTo("OrderCreatedEvent");
        assertThat(target.getVersion()).isEqualTo(3L);
        assertThat(target.getName()).isEqualTo("Renamed");
        assertThat(target.getDescription()).isEqualTo("d");
        assertThat(target.getSubjectType()).isEqualTo("invoice");
        assertThat(target.getAction()).isEqualTo(PlatformEventAction.STATE_CHANGED);
        assertThat(target.getState()).isEqualTo("PAID");
    }

    @Test
    void lifecycleCallbacksStampTheTimestamps() {
        EventDefinition definition = system("x", PlatformEventAction.CREATED, null);

        definition.onCreate();
        assertThat(definition.getCreatedAt()).isNotNull().isEqualTo(definition.getUpdatedAt());

        definition.onUpdate();
        assertThat(definition.getUpdatedAt()).isAfterOrEqualTo(definition.getCreatedAt());
    }

    @Test
    void keyEqualityIsByOrgIdAndChannel() {
        EventDefinitionKey key = new EventDefinitionKey(ORG, "x", Channel.DRAFT);
        EventDefinitionKey same = new EventDefinitionKey();
        same.setOrgKey(ORG);
        same.setId("x");
        same.setChannel(Channel.DRAFT);

        assertThat(key).isEqualTo(same).hasSameHashCodeAs(same)
                .isNotEqualTo(new EventDefinitionKey(ORG, "y", Channel.DRAFT))
                .isNotEqualTo(new EventDefinitionKey(ORG, "x", Channel.RELEASED)).hasToString("org-1/x/DRAFT");
        assertThat(same.getChannel()).isEqualTo(Channel.DRAFT);
        assertThat(same.getOrgKey()).isEqualTo(ORG);
        assertThat(same.getId()).isEqualTo("x");
    }

    @Test
    void acceptsWellFormedParameters() {
        EventDefinition definition = system("OrderCreatedEvent", PlatformEventAction.CREATED, null);
        definition.setParameters(List.of(parameter("orderNumber", EventParameterType.STRING, null),
                parameter("customer", EventParameterType.ENTITY_REF, "customer")));

        assertThat(definition.validate()).isEmpty();
    }

    @Test
    void refusesMalformedParameters() {
        EventDefinition definition = system("OrderCreatedEvent", PlatformEventAction.CREATED, null);
        definition.setParameters(List.of(
                parameter("order number", EventParameterType.STRING, null),
                parameter("total", EventParameterType.NUMBER, null),
                parameter("total", EventParameterType.NUMBER, null),
                parameter("subject", EventParameterType.STRING, null),
                parameter("untyped", null, null),
                parameter("customer", EventParameterType.ENTITY_REF, null),
                parameter("note", EventParameterType.STRING, "order")));

        assertThat(definition.validate()).containsExactly(
                "parameter name 'order number' is not an identifier",
                "parameter 'total' is declared twice",
                "parameter name 'subject' is reserved for the record a SYSTEM event is about",
                "parameter 'untyped' needs a 'type'",
                "parameter 'customer' is an ENTITY_REF and needs an 'entityType'",
                "parameter 'note' is no ENTITY_REF — leave 'entityType' empty");
    }

    // The fact's subject is always there; a declared parameter comes from the payload attribute of its name.
    @Test
    void resolvesTheParametersOfAFact() {
        EventDefinition definition = system("OrderCreatedEvent", PlatformEventAction.CREATED, null);
        definition.setParameters(List.of(parameter("orderNumber", EventParameterType.STRING, null)));

        List<OccurredParameter> parameters = definition.parametersOf(
                PlatformEvent.of(ORG, "order", "42", PlatformEventAction.CREATED, Map.of("orderNumber", "O-1"), Instant.now()));

        assertThat(parameters).containsExactly(
                new OccurredParameter(OccurredParameter.SUBJECT, EventParameterType.ENTITY_REF, "order", "42"),
                new OccurredParameter("orderNumber", EventParameterType.STRING, null, "O-1"));
    }

    // A release copies the parameters, not the list: editing the draft afterwards must not change the released row.
    @Test
    void aReleasedCopyOwnsItsParameters() {
        EventDefinition draft = system("OrderCreatedEvent", PlatformEventAction.CREATED, null);
        draft.setParameters(new java.util.ArrayList<>(List.of(parameter("orderNumber", EventParameterType.STRING, null))));

        EventDefinition released = EventDefinition.releasedCopyOf(draft);
        draft.getParameters().getFirst().setName("renamed");

        assertThat(released.getParameters()).extracting(EventParameter::getName).containsExactly("orderNumber");
    }

    // A row stored before the column existed reads as declaring none.
    @Test
    void readsMissingParametersAsNone() {
        EventDefinition definition = system("OrderCreatedEvent", PlatformEventAction.CREATED, null);
        definition.setParameters(null);

        assertThat(definition.getParameters()).isEmpty();
        assertThat(definition.parametersFrom(null)).isEmpty();
    }

    private static EventParameter parameter(String name, EventParameterType type, String entityType) {
        return EventParameter.builder().name(name).type(type).entityType(entityType).build();
    }
}
