package com.processpuzzle.event.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.event.usecase.exception.EventDefinitionAlreadyExistsException;
import com.processpuzzle.event.usecase.exception.EventDefinitionNotFoundException;
import com.processpuzzle.event.usecase.exception.InvalidEventDefinitionException;
import com.processpuzzle.event.usecase.exception.StaleEventDefinitionException;
import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventDefinitionUseCasesTest {

    private static final String ORG = "org-1";

    private EventDefinitionRepository repository;
    private CreateEventDefinition create;
    private ReplaceEventDefinition replace;
    private DeleteEventDefinition delete;
    private FindEventDefinition find;

    @BeforeEach
    void setUp() {
        repository = mock(EventDefinitionRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        create = new CreateEventDefinition(repository);
        replace = new ReplaceEventDefinition(repository);
        delete = new DeleteEventDefinition(repository);
        find = new FindEventDefinition(repository);
    }

    private static EventDefinition orderCreated() {
        return EventDefinition.builder()
                .id("OrderCreatedEvent").name("Order created").kind(EventKind.SYSTEM)
                .subjectType("order").action(PlatformEventAction.CREATED)
                .build();
    }

    @Test
    void createScopesTheDefinitionToTheOrganizationAndIgnoresAnyVersion() {
        EventDefinition input = orderCreated();
        input.setVersion(7L);

        EventDefinition created = create.create(ORG, input);

        assertThat(created.getOrgKey()).isEqualTo(ORG);
        assertThat(created.getVersion()).isNull();
    }

    @Test
    void createRefusesADuplicateId() {
        when(repository.existsByOrgKeyAndId(ORG, "OrderCreatedEvent")).thenReturn(true);

        assertThatThrownBy(() -> create.create(ORG, orderCreated()))
                .isInstanceOf(EventDefinitionAlreadyExistsException.class);
    }

    @Test
    void createRefusesAnInvalidDefinition() {
        EventDefinition invalid = orderCreated();
        invalid.setAction(null);

        assertThatThrownBy(() -> create.create(ORG, invalid))
                .isInstanceOf(InvalidEventDefinitionException.class)
                .hasMessageContaining("needs an 'action'");
        verify(repository, never()).save(any());
    }

    @Test
    void replaceCopiesOntoTheStoredRow() {
        EventDefinition stored = orderCreated();
        stored.setOrgKey(ORG);
        stored.setVersion(2L);
        when(repository.findByOrgKeyAndId(ORG, "OrderCreatedEvent")).thenReturn(Optional.of(stored));
        EventDefinition desired = orderCreated();
        desired.setName("Renamed");
        desired.setVersion(2L);

        EventDefinition replaced = replace.replace(ORG, "OrderCreatedEvent", desired);

        assertThat(replaced).isSameAs(stored);
        assertThat(replaced.getName()).isEqualTo("Renamed");
    }

    @Test
    void replaceWithoutAVersionOverwritesUnconditionally() {
        EventDefinition stored = orderCreated();
        stored.setVersion(5L);
        when(repository.findByOrgKeyAndId(ORG, "OrderCreatedEvent")).thenReturn(Optional.of(stored));

        assertThat(replace.replace(ORG, "OrderCreatedEvent", orderCreated())).isSameAs(stored);
    }

    @Test
    void replaceRefusesAStaleVersion() {
        EventDefinition stored = orderCreated();
        stored.setVersion(3L);
        when(repository.findByOrgKeyAndId(ORG, "OrderCreatedEvent")).thenReturn(Optional.of(stored));
        EventDefinition desired = orderCreated();
        desired.setVersion(2L);

        assertThatThrownBy(() -> replace.replace(ORG, "OrderCreatedEvent", desired))
                .isInstanceOf(StaleEventDefinitionException.class);
    }

    @Test
    void replaceRefusesAnInvalidResult() {
        when(repository.findByOrgKeyAndId(ORG, "OrderCreatedEvent")).thenReturn(Optional.of(orderCreated()));
        EventDefinition desired = orderCreated();
        desired.setKind(EventKind.MESSAGE);

        assertThatThrownBy(() -> replace.replace(ORG, "OrderCreatedEvent", desired))
                .isInstanceOf(InvalidEventDefinitionException.class);
    }

    @Test
    void replaceAndDeleteOfAnUnknownIdAreNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> replace.replace(ORG, "nope", orderCreated()))
                .isInstanceOf(EventDefinitionNotFoundException.class);
        assertThatThrownBy(() -> delete.delete(ORG, "nope"))
                .isInstanceOf(EventDefinitionNotFoundException.class);
        assertThatThrownBy(() -> find.find(ORG, "nope"))
                .isInstanceOf(EventDefinitionNotFoundException.class)
                .hasMessageContaining("nope").hasMessageContaining(ORG);
    }

    @Test
    void deleteRemovesTheStoredRow() {
        EventDefinition stored = orderCreated();
        when(repository.findByOrgKeyAndId(ORG, "OrderCreatedEvent")).thenReturn(Optional.of(stored));

        delete.delete(ORG, "OrderCreatedEvent");

        verify(repository).delete(stored);
    }

    @Test
    void kindOfNamesTheKindAsTheSharedContractDoes() {
        when(repository.findByOrgKeyAndId(ORG, "InvoiceIssued")).thenReturn(Optional.of(
                EventDefinition.builder().orgKey(ORG).id("InvoiceIssued").name("x").kind(EventKind.MESSAGE).build()));

        FindEventDefinition find = new FindEventDefinition(repository);

        assertThat(find.kindOf(ORG, "InvoiceIssued")).contains(CatalogEventKind.MESSAGE);
        assertThat(find.kindOf(ORG, "Nope")).isEmpty();
        assertThat(find.lookup(ORG, null)).isEmpty();
        for (EventKind kind : EventKind.values()) {
            assertThat(CatalogEventKind.valueOf(kind.name()).name()).isEqualTo(kind.name());
        }
    }

    @Test
    void findAnswersByIdListAndExistence() {
        EventDefinition stored = orderCreated();
        when(repository.findByOrgKeyAndId(ORG, "OrderCreatedEvent")).thenReturn(Optional.of(stored));
        when(repository.findByOrgKeyOrderByIdAsc(ORG)).thenReturn(List.of(stored));
        when(repository.existsByOrgKeyAndId(ORG, "OrderCreatedEvent")).thenReturn(true);

        assertThat(find.find(ORG, "OrderCreatedEvent")).isSameAs(stored);
        assertThat(find.findAll(ORG)).containsExactly(stored);
        assertThat(find.exists(ORG, "OrderCreatedEvent")).isTrue();
        assertThat(find.exists(ORG, null)).isFalse();
    }
}
