package com.processpuzzle.event.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.event.domain.EventParameter;
import com.processpuzzle.shared.event.EventParameterType;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Decision 4 of docs/event-parameters.md: a SYSTEM parameter has to name an attribute it can be filled from. */
class EventDefinitionValidatorTest {

    private static final Set<String> ORDER_ATTRIBUTES = Set.of("orderNumber", "customerName");

    private final EventDefinitionValidator validator = new EventDefinitionValidator((orgKey, subjectType, attribute) ->
            "order".equals(subjectType) ? Optional.of(ORDER_ATTRIBUTES.contains(attribute)) : Optional.empty());

    @Test
    void refusesASystemParameterNamingNoAttributeOfTheSubject() {
        assertThat(validator.validate(system("order", "orderNumber", "orderNo")))
                .containsExactly("parameter 'orderNo' names no attribute of 'order', so nothing would fill it");
    }

    // An entity type the catalog cannot read is not a reason to refuse: the check is skipped.
    @Test
    void skipsTheCheckWhenTheSubjectIsUnknown() {
        assertThat(validator.validate(system("invoice", "anything"))).isEmpty();
    }

    // A message's parameters come from whoever throws it, not from an entity.
    @Test
    void doesNotCheckAMessage() {
        EventDefinition message = EventDefinition.builder().orgKey("org-1").id("InvoiceIssued").name("Invoice issued")
                .kind(EventKind.MESSAGE).parameters(List.of(parameter("orderNo"))).build();

        assertThat(validator.validate(message)).isEmpty();
    }

    @Test
    void reportsTheDefinitionsOwnViolationsFirst() {
        EventDefinition unnamed = system("order", "orderNo");
        unnamed.setName(null);

        assertThat(validator.validate(unnamed)).containsExactly("'name' is required");
    }

    private static EventDefinition system(String subjectType, String... parameters) {
        return EventDefinition.builder().orgKey("org-1").id("OrderCreatedEvent").name("Order created").kind(EventKind.SYSTEM)
                .subjectType(subjectType).action(PlatformEventAction.CREATED)
                .parameters(java.util.Arrays.stream(parameters).map(EventDefinitionValidatorTest::parameter).toList())
                .build();
    }

    private static EventParameter parameter(String name) {
        return EventParameter.builder().name(name).type(EventParameterType.STRING).build();
    }
}
