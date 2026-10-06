package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityObjectAccessAdapterTest {

    @Mock
    private EntityObjectRepository repository;

    private EntityObjectAccessAdapter adapter;

    private static final String ORG = "acme";

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new EntityObjectAccessAdapter(repository, new EntityObjectScope(repository));
    }

    private EntityObject order(long version, Map<String, Object> payload) {
        return EntityObject.builder()
            .id(id)
            .orgKey(ORG)
            .entityDefinitionCode("order")
            .version(version)
            .payload(new LinkedHashMap<>(payload))
            .build();
    }

    @Test
    void find_returnsIdVersionAndPayload() {
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(order(3L, Map.of("status", "DRAFT"))));

        EntityObjectView view = adapter.find(ORG, "order", id);

        assertThat(view.id()).isEqualTo(id);
        assertThat(view.version()).isEqualTo(3L);
        assertThat(view.payload()).containsEntry("status", "DRAFT");
    }

    @Test
    void find_unknownId_throwsNotFound() {
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.find(ORG, "order", id)).isInstanceOf(EntityObjectAccessException.NotFound.class);
    }

    /** A caller holding the right id but naming the wrong type must not see the object. */
    @Test
    void find_wrongEntityType_throwsNotFound() {
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(order(0L, Map.of())));

        assertThatThrownBy(() -> adapter.find(ORG, "partner", id))
            .isInstanceOf(EntityObjectAccessException.NotFound.class)
            .hasMessageContaining("No 'partner' instance");
    }

    @Test
    void updateAttribute_writesOnlyThatKeyAndReturnsTheNewVersion() {
        EntityObject existing = order(1L, Map.of("status", "DRAFT", "name", "ACME"));
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            EntityObject saved = invocation.getArgument(0);
            saved.setVersion(saved.getVersion() + 1);
            return saved;
        });

        long newVersion = adapter.updateAttribute(ORG, "order", id, "status", "CONFIRMED", 1L);

        assertThat(newVersion).isEqualTo(2L);
        assertThat(existing.getPayload())
            .containsEntry("status", "CONFIRMED")
            .containsEntry("name", "ACME");
    }

    /**
     * The payload has to be swapped for a fresh map: a JSON-converted attribute is only flushed
     * when the reference changes, so mutating in place would leave the write in memory only.
     */
    @Test
    void updateAttribute_replacesThePayloadReference() {
        Map<String, Object> loaded = new LinkedHashMap<>(Map.of("status", "DRAFT"));
        EntityObject existing = EntityObject.builder()
            .id(id).entityDefinitionCode("order").version(0L).payload(loaded).build();
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        adapter.updateAttribute(ORG, "order", id, "status", "CONFIRMED", 0L);

        assertThat(existing.getPayload()).isNotSameAs(loaded);
        assertThat(loaded).containsEntry("status", "DRAFT");
    }

    @Test
    void updateAttribute_versionMismatch_throwsConflictAndWritesNothing() {
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(order(5L, Map.of("status", "DRAFT"))));

        assertThatThrownBy(() -> adapter.updateAttribute(ORG, "order", id, "status", "CONFIRMED", 1L))
            .isInstanceOf(EntityObjectAccessException.VersionConflict.class)
            .hasMessageContaining("is at version 5, not the expected 1");

        verifyNoMoreInteractions(repository);
    }

    @Test
    void findAll_returnsEveryObjectOfThatType() {
        EntityObject other = EntityObject.builder()
            .id(UUID.randomUUID()).entityDefinitionCode("order").version(2L)
            .payload(new LinkedHashMap<>(Map.of("status", "SHIPPED"))).build();
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "order"))
            .thenReturn(List.of(order(1L, Map.of("status", "DRAFT")), other));

        List<EntityObjectView> views = adapter.findAll(ORG, "order");

        assertThat(views).hasSize(2);
        assertThat(views).extracting(EntityObjectView::version).containsExactly(1L, 2L);
        assertThat(views.getFirst().payload()).containsEntry("status", "DRAFT");
    }

    /** An unknown type is not an error here: the caller asked what exists, and nothing does. */
    @Test
    void findAll_unknownType_isEmptyRatherThanNotFound() {
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "nope")).thenReturn(List.of());

        assertThat(adapter.findAll(ORG, "nope")).isEmpty();
    }

    /** Another organization holding the id gets the same answer as a missing id. */
    @Test
    void find_anotherOrganizationsObject_throwsNotFound() {
        when(repository.findByIdAndOrgKey(id, "other-org")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.find("other-org", "order", id))
            .isInstanceOf(EntityObjectAccessException.NotFound.class);
    }

    @Test
    void updateAttribute_anotherOrganizationsObject_throwsNotFoundAndWritesNothing() {
        when(repository.findByIdAndOrgKey(id, "other-org")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.updateAttribute("other-org", "order", id, "status", "CONFIRMED", 0L))
            .isInstanceOf(EntityObjectAccessException.NotFound.class);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void findAll_returnsOnlyTheOrganizationsObjects() {
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "order"))
            .thenReturn(List.of(order(1L, Map.of("status", "DRAFT"))));

        List<EntityObjectView> views = adapter.findAll(ORG, "order");

        assertThat(views).extracting(EntityObjectView::id).containsExactly(id);
        verify(repository).findAllByOrgKeyAndEntityDefinitionCode(ORG, "order");
        verify(repository, never()).findAll();
    }
}
