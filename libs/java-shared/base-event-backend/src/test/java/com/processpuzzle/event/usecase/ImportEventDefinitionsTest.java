package com.processpuzzle.event.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportEventDefinitionsTest {

    private static final String ORG = "org-1";

    private EventDefinitionRepository repository;
    private ImportEventDefinitions importer;

    @BeforeEach
    void setUp() {
        repository = mock(EventDefinitionRepository.class);
        when(repository.findByOrgKeyAndId(any(), any())).thenReturn(Optional.empty());
        importer = new ImportEventDefinitions(repository);
    }

    private static InputStream yaml(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void createsNewAndReplacesExistingDefinitions() throws IOException {
        EventDefinition stored = EventDefinition.builder()
                .orgKey(ORG).id("OrderCreatedEvent").name("Old").kind(EventKind.SYSTEM)
                .subjectType("order").action(PlatformEventAction.CREATED).version(4L)
                .build();
        when(repository.findByOrgKeyAndId(ORG, "OrderCreatedEvent")).thenReturn(Optional.of(stored));

        ImportOutcome outcome = importer.execute(ORG, yaml("""
                event-definitions:
                  - id: OrderCreatedEvent
                    name: Order created
                    kind: SYSTEM
                    subjectType: order
                    action: CREATED
                  - id: OrderConfirmedEvent
                    name: Order confirmed
                    kind: SYSTEM
                    subjectType: order
                    action: STATE_CHANGED
                    state: CONFIRMED
                    unknownField: ignored
                """));

        assertThat(outcome).isEqualTo(new ImportOutcome(1, 1, java.util.List.of()));
        assertThat(stored.getName()).isEqualTo("Order created");
        assertThat(stored.getVersion()).isEqualTo(4L);
        verify(repository).save(stored);
    }

    @Test
    void rejectsTheWholeFileWhenAnyEntryIsInvalidOrDuplicated() throws IOException {
        ImportOutcome outcome = importer.execute(ORG, yaml("""
                event-definitions:
                  - id: A
                    name: A
                    kind: SYSTEM
                    subjectType: order
                    action: CREATED
                  - id: A
                    name: A again
                    kind: SIGNAL
                  - id: B
                    name: B
                    kind: SYSTEM
                """));

        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.errors()).hasSize(2)
                .anySatisfy(error -> assertThat(error).contains("Duplicate id").contains("'A'"))
                .anySatisfy(error -> assertThat(error).startsWith("'B'"));
        verify(repository, never()).save(any());
    }

    @Test
    void anEmptyFileOrListImportsNothing() throws IOException {
        assertThat(importer.execute(ORG, yaml(""))).isEqualTo(new ImportOutcome(0, 0, null));
        assertThat(importer.execute(ORG, yaml("event-definitions:\n"))).isEqualTo(new ImportOutcome(0, 0, null));
        assertThat(ImportOutcome.rejected(null).errors()).isEmpty();
    }
}
