package com.processpuzzle.widget.usecase;

import com.processpuzzle.widget.domain.Port;
import com.processpuzzle.widget.domain.WidgetDefinition;
import com.processpuzzle.widget.domain.WidgetDefinitionRepository;
import com.processpuzzle.widget.usecase.WidgetDefinitionCrud.WidgetDefinitionDraft;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionAlreadyExistsException;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionInvalidException;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The widget-definition use cases. What is worth pinning down: that a duplicate create is a 409
 * rather than a silent update (JpaRepository.save merges an assigned id), that the key is genuinely
 * immutable, and that {@code propsSchema} is stored without being validated — the last one is a
 * deliberate contract promise, so a future "helpful" schema check should break this test.
 */
class WidgetDefinitionCrudTest {

    private static final String ORG_KEY = "acme";
    private static final String KEY = "cards-grid";

    private WidgetDefinitionRepository repository;
    private WidgetDefinitionCrud crud;

    @BeforeEach
    void setUp() {
        repository = mock(WidgetDefinitionRepository.class);
        crud = new WidgetDefinitionCrud(repository);
        when(repository.save(any(WidgetDefinition.class))).thenAnswer(call -> call.getArgument(0));
    }

    private WidgetDefinitionDraft draft() {
        return new WidgetDefinitionDraft(KEY, "Cards grid", null, null, null, null, null, null, null);
    }

    @Test
    void createsADraftDefinition() {
        when(repository.existsByOrgKeyAndKey(ORG_KEY, KEY)).thenReturn(false);

        WidgetDefinition created = crud.create(ORG_KEY, draft());

        assertThat(created.getKey()).isEqualTo(KEY);
        assertThat(created.getOrgKey()).isEqualTo(ORG_KEY);
        assertThat(created.getVersion()).isEqualTo(1L);
        verify(repository).save(any(WidgetDefinition.class));
    }

    @Test
    void refusesToCreateADuplicateKey() {
        when(repository.existsByOrgKeyAndKey(ORG_KEY, KEY)).thenReturn(true);

        assertThatThrownBy(() -> crud.create(ORG_KEY, draft()))
                .isInstanceOf(WidgetDefinitionAlreadyExistsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsANonKebabCaseKey() {
        WidgetDefinitionDraft bad = new WidgetDefinitionDraft("Cards Grid", "Cards grid", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> crud.create(ORG_KEY, bad))
                .isInstanceOf(WidgetDefinitionInvalidException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsABlankName() {
        WidgetDefinitionDraft bad = new WidgetDefinitionDraft(KEY, "  ", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> crud.create(ORG_KEY, bad))
                .isInstanceOf(WidgetDefinitionInvalidException.class);
    }

    /** Two ports of one name make any binding to that name ambiguous and unresolvable at render time. */
    @Test
    void rejectsDuplicatePortNames() {
        List<Port> ports = List.of(Port.of("rows", Port.PortType.ARRAY), Port.of("rows", Port.PortType.ARRAY));
        WidgetDefinitionDraft bad = new WidgetDefinitionDraft(KEY, "Cards grid", null, null, null, null, null, ports, null);

        assertThatThrownBy(() -> crud.create(ORG_KEY, bad))
                .isInstanceOf(WidgetDefinitionInvalidException.class)
                .hasMessageContaining("rows");
    }

    @Test
    void bumpsTheVersionOnUpdate() {
        WidgetDefinition existing = new WidgetDefinition(ORG_KEY, KEY, "Cards grid");
        when(repository.findByOrgKeyAndKey(ORG_KEY, KEY)).thenReturn(Optional.of(existing));

        WidgetDefinition updated = crud.update(ORG_KEY, KEY, new WidgetDefinitionDraft(KEY, "Renamed", null, null, null, null, null, null, null));

        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getVersion()).isEqualTo(2L);
    }

    /** The key is what every stored WidgetInstance.type references; renaming it would orphan them all. */
    @Test
    void refusesToRenameTheKeyThroughUpdate() {
        WidgetDefinition existing = new WidgetDefinition(ORG_KEY, KEY, "Cards grid");
        when(repository.findByOrgKeyAndKey(ORG_KEY, KEY)).thenReturn(Optional.of(existing));

        WidgetDefinitionDraft renaming = new WidgetDefinitionDraft("card-grid", "Cards grid", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> crud.update(ORG_KEY, KEY, renaming))
                .isInstanceOf(WidgetDefinitionInvalidException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void reportsAMissingDefinitionAsNotFound() {
        when(repository.findByOrgKeyAndKey(ORG_KEY, KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crud.find(ORG_KEY, KEY))
                .isInstanceOf(WidgetDefinitionNotFoundException.class);
    }

    /**
     * The contract promises the backend stores propsSchema verbatim and never interprets it. This
     * pins that promise: a structurally nonsensical schema is accepted, because validating it is the
     * job of whatever edits the props.
     */
    @Test
    void storesPropsSchemaWithoutValidatingIt() {
        when(repository.existsByOrgKeyAndKey(ORG_KEY, KEY)).thenReturn(false);
        Map<String, Object> nonsense = Map.of("type", "not-a-real-json-schema-type");

        WidgetDefinition created = crud.create(ORG_KEY,
                new WidgetDefinitionDraft(KEY, "Cards grid", null, null, null, null, nonsense, null, null));

        assertThat(created.getPropsSchema()).isEqualTo(nonsense);
    }

    /** Null propsSchema means "unconstrained", distinct from an empty schema meaning "no props". */
    @Test
    void keepsANullPropsSchemaNull() {
        when(repository.existsByOrgKeyAndKey(ORG_KEY, KEY)).thenReturn(false);

        WidgetDefinition created = crud.create(ORG_KEY, draft());

        assertThat(created.getPropsSchema()).isNull();
    }

    @Test
    void publishesWithoutBumpingTheVersion() {
        WidgetDefinition existing = new WidgetDefinition(ORG_KEY, KEY, "Cards grid");
        when(repository.findByOrgKeyAndKey(ORG_KEY, KEY)).thenReturn(Optional.of(existing));

        WidgetDefinition published = crud.publish(ORG_KEY, KEY);

        assertThat(published.getVersion()).isEqualTo(1L);
        assertThat(published.getPublishedVersion()).isEqualTo(1L);
    }

    @Test
    void deletesAnExistingDefinition() {
        WidgetDefinition existing = new WidgetDefinition(ORG_KEY, KEY, "Cards grid");
        when(repository.findByOrgKeyAndKey(ORG_KEY, KEY)).thenReturn(Optional.of(existing));

        crud.delete(ORG_KEY, KEY);

        verify(repository).delete(existing);
    }
}
