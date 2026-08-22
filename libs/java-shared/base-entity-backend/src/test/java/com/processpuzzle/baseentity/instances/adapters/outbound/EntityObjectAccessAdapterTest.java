package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityObjectAccessAdapterTest {

    @Mock
    private EntityObjectRepository repository;

    @InjectMocks
    private EntityObjectAccessAdapter adapter;

    private final UUID id = UUID.randomUUID();

    private EntityObject order(long version, Map<String, Object> payload) {
        return EntityObject.builder()
            .id(id)
            .entityDefinitionCode("order")
            .version(version)
            .payload(new LinkedHashMap<>(payload))
            .build();
    }

    @Test
    void find_returnsIdVersionAndPayload() {
        when(repository.findById(id)).thenReturn(Optional.of(order(3L, Map.of("status", "DRAFT"))));

        EntityObjectView view = adapter.find("order", id);

        assertThat(view.id()).isEqualTo(id);
        assertThat(view.version()).isEqualTo(3L);
        assertThat(view.payload()).containsEntry("status", "DRAFT");
    }

    @Test
    void find_unknownId_throwsNotFound() {
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.find("order", id)).isInstanceOf(EntityObjectAccessException.NotFound.class);
    }

    /** A caller holding the right id but naming the wrong type must not see the object. */
    @Test
    void find_wrongEntityType_throwsNotFound() {
        when(repository.findById(id)).thenReturn(Optional.of(order(0L, Map.of())));

        assertThatThrownBy(() -> adapter.find("partner", id))
            .isInstanceOf(EntityObjectAccessException.NotFound.class)
            .hasMessageContaining("No 'partner' instance");
    }

    @Test
    void updateAttribute_writesOnlyThatKeyAndReturnsTheNewVersion() {
        EntityObject existing = order(1L, Map.of("status", "DRAFT", "name", "ACME"));
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            EntityObject saved = invocation.getArgument(0);
            saved.setVersion(saved.getVersion() + 1);
            return saved;
        });

        long newVersion = adapter.updateAttribute("order", id, "status", "CONFIRMED", 1L);

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
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        adapter.updateAttribute("order", id, "status", "CONFIRMED", 0L);

        assertThat(existing.getPayload()).isNotSameAs(loaded);
        assertThat(loaded).containsEntry("status", "DRAFT");
    }

    @Test
    void updateAttribute_versionMismatch_throwsConflictAndWritesNothing() {
        when(repository.findById(id)).thenReturn(Optional.of(order(5L, Map.of("status", "DRAFT"))));

        assertThatThrownBy(() -> adapter.updateAttribute("order", id, "status", "CONFIRMED", 1L))
            .isInstanceOf(EntityObjectAccessException.VersionConflict.class)
            .hasMessageContaining("is at version 5, not the expected 1");

        verifyNoMoreInteractions(repository);
    }
}
